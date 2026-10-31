package nl.vdzon.hkhworkflow.adminlog

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.util.UUID

/**
 * De wachtrij op schijf voor de loglijnen die naar het workflow-logboek van hkh-admin gaan. De appender zet elke regel (één json-object per
 * regel) in een geheugenbuffer; een eigen draad schrijft die om de paar seconden naar `current.jsonl`. De verzender ([AppLogShipper])
 * draait dat bestand om naar `pending-….jsonl` en verstuurt het; pas na een geslaagde verzending verdwijnt het. Zo gaat er bij een herstart of
 * crash niets verloren, en wat nog niet is verstuurd gaat bij de volgende start alsnog mee.
 *
 * Het bestand wordt alleen geopend om te schrijven of te hernoemen en meteen weer gesloten, want Windows laat een geopend bestand niet
 * hernoemen. Deze klasse logt zelf nooit (dan zou hij zijn eigen appender voeden); fouten worden stil genegeerd.
 */
class AppLogSpool(private val dir: Path) {
    private val lock = Any()
    private val buffer = ArrayDeque<String>()
    private val current: Path = dir.resolve(CURRENT)

    init {
        runCatching { Files.createDirectories(dir) }
        Thread({
            while (true) {
                try { Thread.sleep(FLUSH_MILLIS) } catch (_: InterruptedException) { return@Thread }
                flush()
            }
        }, "app-log-spool").apply { isDaemon = true }.start()
        Runtime.getRuntime().addShutdownHook(Thread(::flush, "app-log-spool-flush"))
    }

    /** Voegt één regel (een json-object zonder newline) toe. Bij een vastlopende schijf vallen de oudste regels weg. */
    fun add(jsonLine: String) {
        synchronized(lock) {
            if (buffer.size >= MAX_BUFFERED_LINES) buffer.removeFirst()
            buffer.addLast(jsonLine)
        }
    }

    fun flush() {
        synchronized(lock) { flushLocked() }
    }

    private fun flushLocked() {
        if (buffer.isEmpty()) return
        val text = buffer.joinToString(separator = "\n", postfix = "\n")
        buffer.clear()
        runCatching {
            Files.createDirectories(dir)
            Files.writeString(current, text, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND)
        }
    }

    /**
     * Maakt van wat er nu in de wachtrij staat een eigen bestand om te versturen, en geeft alle bestanden die nog moeten (oudste eerst).
     * Wat boven [MAX_SPOOL_BYTES] uitkomt, wordt weggegooid, oudste eerst; dat aantal bestanden staat in [trimmed].
     */
    fun rotate(): List<Path> = synchronized(lock) {
        flushLocked()
        runCatching {
            if (Files.isRegularFile(current) && Files.size(current) > 0) {
                Files.move(current, dir.resolve("pending-${System.currentTimeMillis()}-${UUID.randomUUID().toString().take(8)}.jsonl"), StandardCopyOption.ATOMIC_MOVE)
            }
        }
        val files = pending().toMutableList()
        var total = files.sumOf { runCatching { Files.size(it) }.getOrDefault(0L) }
        while (total > MAX_SPOOL_BYTES && files.size > 1) {
            val oldest = files.removeFirst()
            total -= runCatching { Files.size(oldest) }.getOrDefault(0L)
            runCatching { Files.deleteIfExists(oldest) }
            trimmed++
        }
        files
    }

    /** Hoeveel bestanden er sinds de start zijn weggegooid omdat de verzending te lang niet lukte. */
    @Volatile var trimmed: Int = 0
        private set

    fun pending(): List<Path> = runCatching {
        Files.list(dir).use { stream -> stream.filter { it.fileName.toString().startsWith("pending-") }.sorted().toList() }
    }.getOrDefault(emptyList())

    /** Het tijdstip van de laatste verzendpoging, bewaard op schijf zodat een herstart niet vaker laat versturen dan elk kwartier. */
    fun lastAttempt(): Long = runCatching { Files.readString(dir.resolve(STATE)).trim().toLong() }.getOrDefault(0L)

    fun markAttempt(millis: Long) {
        runCatching { Files.writeString(dir.resolve(STATE), millis.toString()) }
    }

    companion object {
        const val CURRENT = "current.jsonl"
        const val STATE = "last-attempt"
        private const val FLUSH_MILLIS = 5_000L
        private const val MAX_BUFFERED_LINES = 10_000
        const val MAX_SPOOL_BYTES = 20L * 1024 * 1024

        /** De wachtrij die de appender gebruikt; `null` zolang de appender niet draait (geen adres ingesteld). */
        @Volatile var active: AppLogSpool? = null
            private set
        private var activeDir: Path? = null

        /** Start de wachtrij in [dir], of geeft de bestaande terug als Logback zich opnieuw configureert. */
        @Synchronized fun start(dir: Path): AppLogSpool {
            active?.takeIf { activeDir == dir }?.let { return it }
            return AppLogSpool(dir).also { active = it; activeDir = dir }
        }
    }
}
