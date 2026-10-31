package nl.vdzon.hkhworkflow.adminlog

import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.LinkedHashMap
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import org.slf4j.LoggerFactory
import nl.vdzon.hkhworkflow.settings.SettingsService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper
import tools.jackson.databind.node.ObjectNode

/** Zet het uitvoeren van geplande taken aan (de meldingen aan het workflow-logboek lopen daarop). */
@Configuration
@EnableScheduling
class AdminLogScheduling

/** Eén melding over een doorloop (sessie), zoals het workflow-logboek van hkh-admin die verwacht. [snapshot] is alles wat de app weet. */
data class SessionReport(
    val id: UUID,
    /** NEW_FILES (nieuwe bestanden in ZCBS zetten) of EDIT (records aanpassen). */
    val kind: String,
    /** Willekeurig geheim van deze sessie; alleen wie het kent mag de sessie bijwerken. */
    val secret: String,
    val user: String?,
    val collection: String?,
    val phase: Int,
    val phaseCount: Int,
    val phaseLabel: String?,
    /** RUNNING, FINISHED of FAILED. */
    val status: String,
    val fileCount: Int,
    val event: String?,
    val snapshot: ObjectNode,
)

/**
 * Meldt inlogpogingen en de stand van elke doorloop aan het workflow-logboek van hkh-admin (`PUT /api/workflow-log/sessions/{uuid}`
 * en `POST /api/workflow-log/logins`). Het meldpunt vraagt geen inlog, wel een vaste sleutel. Alles gebeurt op een eigen draad en
 * een fout, een trage of een weigerende server mag de app nooit hinderen: elke melding bevat de volledige stand, dus een gemiste
 * melding herstelt zich bij de volgende. Zonder adres (`hkh.workflow.admin-log-url` leeg) doet deze klasse niets.
 */
@Component
class AdminLogClient internal constructor(
    private val mapper: ObjectMapper,
    baseUrl: String,
    /** De bron van deze installatie volgens de instellingen (macbook, windows-laptop, hkh, ...); leeg = de computernaam. */
    private val sourceSetting: () -> String = { "" },
) {
    @Autowired
    constructor(
        mapper: ObjectMapper,
        @Value("\${hkh.workflow.admin-log-url:}") baseUrl: String,
        settings: SettingsService,
    ) : this(mapper, baseUrl, { settings.get().logSource })

    private val log = LoggerFactory.getLogger(javaClass)
    private val base = baseUrl.trim().trimEnd('/')
    val enabled: Boolean = base.isNotEmpty()

    /** Het adres van het workflow-logboek zonder slash aan het eind; leeg als het uit staat. */
    val baseUrl: String get() = base

    private val http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build()
    private val executor = Executors.newSingleThreadExecutor { r -> Thread(r, "admin-log").apply { isDaemon = true } }
    private val pending = LinkedHashMap<UUID, SessionReport>()
    private val logins = ArrayDeque<ObjectNode>()
    private val draining = AtomicBoolean(false)

    /** Tot dit tijdstip (ms) wordt er niets verstuurd, omdat de server te veel meldingen kreeg of niet reageerde. */
    private val blockedUntil = AtomicLong(0)
    private val lastFailure = HashMap<String, Long>()

    val appVersion: String = AppVersion.current
    /** De computernaam. Het opzoeken kan op een computer met een trage dns lang duren, dus dat gebeurt één keer op een eigen draad; wie niet kan wachten krijgt "onbekend". */
    private val computerLookup = java.util.concurrent.CompletableFuture.supplyAsync {
        System.getenv("COMPUTERNAME")?.takeIf { it.isNotBlank() }
            ?: runCatching { java.net.InetAddress.getLocalHost().hostName }.getOrDefault("onbekend")
    }
    val computer: String get() = runCatching { computerLookup.get(500, java.util.concurrent.TimeUnit.MILLISECONDS) }.getOrDefault("onbekend")

    /** Waar deze melding vandaan komt: de ingestelde bron, anders de computernaam. */
    fun source(): String = runCatching { sourceSetting().trim() }.getOrDefault("").ifEmpty { computer }.take(80)

    /** Meldt de stand van een doorloop. Een oudere, nog niet verstuurde melding van dezelfde sessie vervalt. */
    fun session(report: SessionReport) {
        if (!enabled) return
        try {
            synchronized(pending) {
                pending[report.id] = report
                // Bij een logboek dat dagen onbereikbaar is, groeit dit niet door: de oudste sessies vallen dan af.
                while (pending.size > MAX_PENDING_SESSIONS) pending.remove(pending.keys.first())
            }
            schedule()
        } catch (e: Exception) {
            log.debug("Melding aan het workflow-logboek overgeslagen: {}", e.message)
        }
    }

    /** Meldt een inlogpoging. Dezelfde mislukte poging binnen vijf minuten wordt maar één keer gemeld. */
    fun login(user: String?, success: Boolean, method: String, detail: String? = null) {
        if (!enabled) return
        try {
            enqueueLogin(user, success, method, detail)
        } catch (e: Exception) {
            // Een inlogpoging mag nooit mislukken omdat het logboek niet bereikbaar of stuk is.
            log.debug("Melding aan het workflow-logboek overgeslagen: {}", e.message)
        }
    }

    private fun enqueueLogin(user: String?, success: Boolean, method: String, detail: String?) {
        val key = "$user|$success|$method|$detail"
        val now = System.currentTimeMillis()
        if (!success) synchronized(lastFailure) {
            if (now - (lastFailure[key] ?: 0) < DUPLICATE_FAILURE_MILLIS) return
            lastFailure[key] = now
            if (lastFailure.size > 200) lastFailure.entries.removeIf { now - it.value > DUPLICATE_FAILURE_MILLIS }
        }
        val body = mapper.createObjectNode().apply {
            put("user", user.orEmpty())
            put("success", success)
            put("method", method)
            detail?.let { put("detail", it.take(300)) }
            put("appVersion", appVersion)
            put("computer", computer)
            put("source", source())
        }
        synchronized(logins) { if (logins.size < MAX_QUEUED_LOGINS) logins.addLast(body) }
        schedule()
    }

    private fun schedule() {
        if (draining.compareAndSet(false, true)) executor.execute(::drain)
    }

    private fun drain() {
        try {
            while (System.currentTimeMillis() >= blockedUntil.get()) {
                val login = synchronized(logins) { logins.removeFirstOrNull() }
                if (login != null) { send("POST", "/api/workflow-log/logins", login); continue }
                val report = synchronized(pending) { pending.entries.firstOrNull()?.also { pending.remove(it.key) }?.value } ?: break
                send("PUT", "/api/workflow-log/sessions/${report.id}", body(report))
            }
        } finally {
            draining.set(false)
            // Er kan intussen iets bij zijn gekomen.
            if (System.currentTimeMillis() >= blockedUntil.get() && (synchronized(pending) { pending.isNotEmpty() } || synchronized(logins) { logins.isNotEmpty() })) schedule()
        }
    }

    private fun body(report: SessionReport): ObjectNode {
        fun build(snapshot: ObjectNode) = mapper.createObjectNode().apply {
            put("kind", report.kind)
            put("secret", report.secret)
            put("user", report.user.orEmpty())
            put("collection", report.collection.orEmpty())
            put("appVersion", appVersion)
            put("computer", computer)
            put("source", source())
            put("phase", report.phase)
            put("phaseCount", report.phaseCount)
            put("phaseLabel", report.phaseLabel.orEmpty())
            put("status", report.status)
            put("fileCount", report.fileCount)
            report.event?.let { put("event", it.take(500)) }
            set("snapshot", snapshot)
        }
        var node = build(report.snapshot)
        // Het logboek weigert berichten boven 1 MB: lange teksten (de uitvoer van de programma's) worden dan ingekort.
        var limit = 150_000
        while (mapper.writeValueAsBytes(node).size > MAX_BODY_BYTES && limit >= 2_000) {
            limit /= 3
            node = build(truncated(report.snapshot, limit) as ObjectNode)
        }
        return node
    }

    private fun truncated(node: tools.jackson.databind.JsonNode, limit: Int): tools.jackson.databind.JsonNode = when {
        node.isObject -> mapper.createObjectNode().also { out -> node.properties().forEach { (k, v) -> out.set(k, truncated(v, limit)) } }
        node.isArray -> mapper.createArrayNode().also { out -> node.forEach { out.add(truncated(it, limit)) } }
        node.isString && node.stringValue().length > limit ->
            mapper.nodeFactory.stringNode("(ingekort, de eerste ${limit / 2} en de laatste ${limit / 2} tekens)\n" + node.stringValue().take(limit / 2) + "\n…\n" + node.stringValue().takeLast(limit / 2))
        else -> node
    }

    private fun send(method: String, path: String, body: ObjectNode) {
        try {
            val request = HttpRequest.newBuilder(URI.create(base + path))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json")
                .header(KEY_HEADER, INGEST_KEY)
                .header("User-Agent", "hkh-workflow/$appVersion")
                .method(method, HttpRequest.BodyPublishers.ofByteArray(mapper.writeValueAsBytes(body)))
                .build()
            val response = http.send(request, HttpResponse.BodyHandlers.ofString())
            when (val code = response.statusCode()) {
                in 200..299 -> {}
                429 -> {
                    val seconds = response.headers().firstValue("Retry-After").map { it.toLongOrNull() ?: 60L }.orElse(60L)
                    blockedUntil.set(System.currentTimeMillis() + seconds.coerceIn(5, 600) * 1000)
                    log.debug("Het workflow-logboek vraagt om te wachten ({} s)", seconds)
                }
                else -> {
                    // Een weigering verandert niet door het opnieuw te proberen; de volgende melding probeert het weer.
                    blockedUntil.set(System.currentTimeMillis() + FAILURE_PAUSE_MILLIS)
                    log.warn("Het workflow-logboek weigerde de melding ({}): {}", code, response.body().take(200))
                }
            }
        } catch (e: Exception) {
            blockedUntil.set(System.currentTimeMillis() + FAILURE_PAUSE_MILLIS)
            log.debug("Het workflow-logboek is niet bereikbaar: {}", e.message)
        }
    }

    companion object {
        const val KEY_HEADER = "X-HKH-Workflow-Key"

        /** Bewust vast en openbaar: dezelfde sleutel staat in de backend van hkh (zie docs/workflow-logboek.md daar). */
        const val INGEST_KEY = "hkh-workflow-logboek-3f9c2a7e51b84d06"
        private const val MAX_BODY_BYTES = 950_000
        private const val FAILURE_PAUSE_MILLIS = 30_000L
        private const val DUPLICATE_FAILURE_MILLIS = 5 * 60_000L
        private const val MAX_QUEUED_LOGINS = 50
        private const val MAX_PENDING_SESSIONS = 200
    }
}
