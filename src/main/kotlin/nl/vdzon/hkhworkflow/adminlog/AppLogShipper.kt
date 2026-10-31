package nl.vdzon.hkhworkflow.adminlog

import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper
import tools.jackson.databind.node.ObjectNode

/**
 * Stuurt de logregels van de app (INFO en hoger, zie [AppLogAppender]) naar het workflow-logboek van hkh-admin
 * (`POST /api/workflow-log/app-logs`), **hooguit eens per kwartier en alleen als er nieuwe regels zijn**. De regels staan intussen in de
 * wachtrij op schijf ([AppLogSpool]); een bestand verdwijnt pas na een geslaagde verzending, en elk pakket heeft een vaste naam
 * (`chunkId`) zodat een herhaling na een time-out aan de andere kant niet dubbel telt. Het tijdstip van de laatste poging staat op schijf,
 * zodat ook een herstart niet vaker laat versturen. Een fout, een trage of een weigerende server hindert de app nooit.
 */
@Component
class AppLogShipper internal constructor(
    private val mapper: ObjectMapper,
    private val client: AdminLogClient,
    private val spoolSource: () -> AppLogSpool?,
    private val clock: () -> Long,
    private val interval: Duration,
) {
    @Autowired
    constructor(mapper: ObjectMapper, client: AdminLogClient) : this(mapper, client, { AppLogSpool.active }, System::currentTimeMillis, INTERVAL)

    private val log = LoggerFactory.getLogger(javaClass)
    private val http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build()

    @Volatile private var blockedUntil = 0L

    /** Een eigen draad, zodat een trage of hangende server de andere geplande taken van de app (die één draad delen) niet ophoudt. */
    private val executor = Executors.newSingleThreadExecutor { r -> Thread(r, "app-log-shipper").apply { isDaemon = true } }
    private val running = AtomicBoolean(false)

    @Scheduled(fixedDelay = 60_000, initialDelay = 60_000)
    fun tick() {
        if (!running.compareAndSet(false, true)) return
        executor.execute {
            try {
                sendPending()
            } catch (e: Exception) {
                log.debug("Versturen van de logregels mislukt: {}", e.message)
            } finally {
                running.set(false)
            }
        }
    }

    /** Verstuurt wat er in de wachtrij staat als er een kwartier voorbij is sinds de laatste poging. Geeft het aantal verstuurde pakketten terug. */
    internal fun sendPending(): Int {
        if (!client.enabled) return 0
        val spool = spoolSource() ?: return 0
        val now = clock()
        if (now < blockedUntil || now - spool.lastAttempt() < interval.toMillis()) return 0
        val files = spool.rotate()
        if (files.isEmpty()) return 0
        spool.markAttempt(now)
        if (spool.trimmed > 0) log.warn("De logregels konden lang niet verstuurd worden; {} oudere bestanden zijn weggegooid", spool.trimmed)

        var sent = 0
        for (file in files.take(MAX_FILES_PER_RUN)) {
            when (val result = sendFile(file)) {
                is Result.Done -> { sent += result.chunks; runCatching { Files.deleteIfExists(file) } }
                is Result.Rejected -> { log.warn("Het workflow-logboek weigerde de logregels in {}: {}", file.fileName, result.reason); runCatching { Files.deleteIfExists(file) } }
                is Result.Retry -> return sent
            }
        }
        return sent
    }

    private sealed interface Result {
        class Done(val chunks: Int) : Result
        class Rejected(val reason: String) : Result
        data object Retry : Result
    }

    /** Verstuurt één bestand in pakketten van een beperkte grootte; een nieuwe appversie begint een nieuw pakket. */
    private fun sendFile(file: Path): Result {
        val batches = batches(file)
        for ((index, batch) in batches.withIndex()) {
            val body = mapper.createObjectNode().apply {
                put("source", client.source())
                put("computer", client.computer)
                put("appVersion", batch.version)
                put("chunkId", "${file.fileName.toString().removeSuffix(".jsonl")}#$index".take(120))
                set("lines", mapper.createArrayNode().apply { batch.lines.forEach { add(it) } })
            }
            when (val code = post(body)) {
                in 200..299 -> {}
                400, 413 -> return Result.Rejected("status $code")
                else -> return Result.Retry.also { log.debug("Het workflow-logboek nam de logregels niet aan (status {})", code) }
            }
        }
        return Result.Done(batches.size)
    }

    private class Batch(val version: String) {
        val lines = ArrayList<ObjectNode>()
        var bytes = 0
    }

    /** De regels van [file], op volgorde, in pakketten per appversie, elk met hooguit [MAX_LINES] regels en [MAX_BYTES] bytes. Onleesbare regels vallen weg. */
    private fun batches(file: Path): List<Batch> {
        val batches = ArrayList<Batch>()
        Files.newBufferedReader(file).useLines { sequence ->
            sequence.forEach { text ->
                val node = runCatching { mapper.readTree(text) }.getOrNull() as? ObjectNode ?: return@forEach
                val version = node.get("v")?.asString("")?.takeIf { it.isNotBlank() } ?: "onbekend"
                node.remove("v")
                var batch = batches.lastOrNull()
                if (batch == null || batch.version != version || batch.lines.size >= MAX_LINES || batch.bytes + text.length > MAX_BYTES) {
                    batch = Batch(version).also { batches += it }
                }
                batch.lines += node
                batch.bytes += text.length
            }
        }
        return batches
    }

    /** Geeft de statuscode terug (429 zet de verzender op pauze); een verbindingsfout is 0. */
    private fun post(body: ObjectNode): Int {
        return try {
            val request = HttpRequest.newBuilder(URI.create(client.baseUrl + "/api/workflow-log/app-logs"))
                .timeout(Duration.ofSeconds(20))
                .header("Content-Type", "application/json")
                .header(AdminLogClient.KEY_HEADER, AdminLogClient.INGEST_KEY)
                .header("User-Agent", "hkh-workflow/${AppVersion.current}")
                .POST(HttpRequest.BodyPublishers.ofByteArray(mapper.writeValueAsBytes(body)))
                .build()
            val response = http.send(request, HttpResponse.BodyHandlers.ofString())
            if (response.statusCode() == 429) {
                val seconds = response.headers().firstValue("Retry-After").map { it.toLongOrNull() ?: 60L }.orElse(60L)
                blockedUntil = clock() + seconds.coerceIn(5, 3600) * 1000
            }
            response.statusCode()
        } catch (e: Exception) {
            log.debug("Het workflow-logboek is niet bereikbaar: {}", e.message)
            0
        }
    }

    companion object {
        val INTERVAL: Duration = Duration.ofMinutes(15)
        private const val MAX_LINES = 2_000
        private const val MAX_BYTES = 400_000

        /** Zoveel bestanden per poging; na een lange storing haalt de verzender de rest in bij de volgende pogingen. */
        private const val MAX_FILES_PER_RUN = 4
    }
}
