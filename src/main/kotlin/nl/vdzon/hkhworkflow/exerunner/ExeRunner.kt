package nl.vdzon.hkhworkflow.exerunner

import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.io.Writer
import java.nio.charset.Charset
import java.nio.file.Files
import java.nio.file.Path
import java.time.Instant
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.atomic.AtomicLong
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service

/** Hoe een programma wordt gestart. */
enum class RunMode(val label: String) {
    /** De app start het proces zelf, toont de uitvoer in de browser en stuurt invoer door. */
    IN_APP("in de app"),

    /** Windows: het programma opent in een eigen cmd-venster op de computer waar de jar draait. */
    WINDOW("in eigen venster"),
}

/** Momentopname van de laatste (of lopende) run, voor de UI. */
data class RunSnapshot(
    val id: Long,
    val label: String,
    val mode: RunMode,
    val running: Boolean,
    val exitCode: Int?,
    val output: String,
    val startedAt: Instant,
    val finishedAt: Instant?,
    /** Aantal ontvangen tekens uitvoer. */
    val charsReceived: Long,
    /** Aantal keer dat er uitvoer binnenkwam (brokken). */
    val chunksReceived: Long,
    /** Seconden sinds de laatste uitvoer (of sinds de start als er nog niets kwam). */
    val secondsSinceOutput: Long,
    val inputsSent: Int,
    val logFile: String,
    val command: String,
)

/**
 * Start externe programma's (images.exe, pdf2zcbs.exe of op een Mac een vervangend script) één tegelijk.
 *
 * In [RunMode.IN_APP] wordt de uitvoer teken voor teken gelezen (prompts eindigen niet op een nieuwe
 * regel) en kan invoer via [sendInput] worden doorgestuurd.
 *
 * Elke run krijgt een eigen logbestand met tijdstempels per binnengekomen brok uitvoer en per
 * verstuurde invoer. Daarmee is te zien óf en wanneer een programma zijn vragen laat zien
 * (bijvoorbeeld door buffering pas na een antwoord).
 */
@Service
class ExeRunner(
    @Value("\${hkh.workflow.console-charset:IBM850}") charsetName: String,
    @Value("\${hkh.workflow.log-dir:logs}") logDir: String,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val charset: Charset = runCatching { Charset.forName(charsetName) }.getOrElse { Charsets.UTF_8 }
    private val logDir: Path = Path.of(logDir).toAbsolutePath()
    private val ids = AtomicLong()

    @Volatile
    private var current: Run? = null

    private class Run(
        val id: Long,
        val label: String,
        val mode: RunMode,
        val command: String,
        val process: Process,
        val writer: Writer?,
        val logWriter: BufferedWriter,
        val logFile: Path,
        val startedAt: Instant,
        val startNanos: Long,
        val newline: String,
    ) {
        val output = StringBuilder()

        @Volatile var exitCode: Int? = null
        @Volatile var finishedAt: Instant? = null
        @Volatile var charsReceived = 0L
        @Volatile var chunksReceived = 0L
        @Volatile var lastOutputNanos = startNanos
        @Volatile var inputsSent = 0

        val running: Boolean get() = finishedAt == null

        fun elapsed(): String = "%8.3fs".format((System.nanoTime() - startNanos) / 1_000_000_000.0)

        fun logLine(text: String) = synchronized(logWriter) {
            runCatching {
                logWriter.write("[${elapsed()}] $text")
                logWriter.newLine()
                logWriter.flush()
            }
        }
    }

    fun isRunning(): Boolean = current?.running == true

    fun snapshot(): RunSnapshot? = current?.let { run ->
        val text = synchronized(run.output) { run.output.toString() }
        RunSnapshot(
            id = run.id,
            label = run.label,
            mode = run.mode,
            running = run.running,
            exitCode = run.exitCode,
            output = text.replace("\r\n", "\n").replace("\r", ""),
            startedAt = run.startedAt,
            finishedAt = run.finishedAt,
            charsReceived = run.charsReceived,
            chunksReceived = run.chunksReceived,
            secondsSinceOutput = (System.nanoTime() - run.lastOutputNanos) / 1_000_000_000,
            inputsSent = run.inputsSent,
            logFile = run.logFile.toString(),
            command = run.command,
        )
    }

    /** Inhoud van het logbestand van de laatste run. */
    fun logText(): String? = current?.let { run ->
        synchronized(run.logWriter) { runCatching { Files.readString(run.logFile) }.getOrNull() }
    }

    /**
     * Start [exe] met [workingDir] als werkmap. [onExit] wordt aangeroepen met de exitcode zodra het
     * proces stopt.
     */
    @Synchronized
    fun start(
        exe: Path,
        workingDir: Path,
        label: String,
        mode: RunMode,
        extraEnv: Map<String, String> = emptyMap(),
        notes: List<String> = emptyList(),
        onExit: (Int?) -> Unit,
    ): RunSnapshot {
        check(!isRunning()) { "Er draait al een programma: ${current?.label}" }
        val command = when (mode) {
            RunMode.IN_APP -> listOf(exe.toString())
            RunMode.WINDOW -> {
                check(isWindows()) { "Een eigen venster openen kan alleen als de app op Windows draait." }
                // 'start' opent een nieuw consolevenster; het eerste argument tussen quotes is de titel.
                listOf("cmd.exe", "/c", "start", "\"$label\"", "/wait", exe.toString())
            }
        }
        val id = ids.incrementAndGet()
        Files.createDirectories(logDir)
        val stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))
        val safeLabel = label.replace(Regex("[^A-Za-z0-9._-]+"), "_").trim('_')
        val logFile = logDir.resolve("run-$stamp-$id-$safeLabel.log")
        val logWriter = Files.newBufferedWriter(logFile, Charsets.UTF_8)

        val builder = ProcessBuilder(command).directory(workingDir.toFile()).redirectErrorStream(true)
        builder.environment().putAll(extraEnv)
        val startNanos = System.nanoTime()
        val process = try {
            builder.start()
        } catch (e: Exception) {
            logWriter.use { it.write("Starten mislukt: $command\n${e}\n") }
            throw IllegalStateException("Starten mislukt: ${e.message} (log: $logFile)", e)
        }
        val writer = if (mode == RunMode.IN_APP) OutputStreamWriter(process.outputStream, charset) else null
        // Met PERLIO=:unix valt Perl's CRLF-vertaling weg; stuur dan alleen LF, anders komt er een \r in het antwoord.
        val newline = if (isWindows() && extraEnv["PERLIO"] == null) "\r\n" else "\n"
        val run = Run(id, label, mode, command.joinToString(" "), process, writer, logWriter, logFile, Instant.now(), startNanos, newline)
        current = run

        run.logLine("START $label")
        run.logLine("  commando    : ${run.command}")
        run.logLine("  werkmap     : $workingDir")
        run.logLine("  modus       : $mode")
        run.logLine("  charset     : $charset (invoer eindigt op ${if (run.newline == "\r\n") "CRLF" else "LF"})")
        run.logLine("  extra env   : ${if (extraEnv.isEmpty()) "(geen)" else extraEnv}")
        run.logLine("  argumenten  : ${if (command.size > 1 && mode == RunMode.IN_APP) command.drop(1) else "(geen; het programma werkt op de bestanden in zijn werkmap en stelt zijn vragen via de console)"}")
        run.logLine("  pid         : ${runCatching { process.pid() }.getOrNull()}")
        notes.forEach { run.logLine("  voorbereid  : $it") }
        runCatching {
            Files.list(workingDir).use { files ->
                files.sorted().forEach { f -> run.logLine("  werkmap bevat: ${f.fileName}${if (Files.isDirectory(f)) "/" else ""}") }
            }
        }
        log.info("Gestart: {} ({}) in {}, log {}", label, mode, workingDir, logFile)

        Thread({ pump(run) }, "exe-output-${run.id}").apply { isDaemon = true }.start()
        Thread({
            val code = try {
                process.waitFor()
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                null
            }
            // Even wachten zodat de laatste uitvoer nog binnenkomt.
            Thread.sleep(300)
            run.exitCode = code
            run.finishedAt = Instant.now()
            run.logLine("EXIT code=$code, ${run.charsReceived} tekens in ${run.chunksReceived} brokken, ${run.inputsSent} invoerregels")
            synchronized(run.logWriter) { runCatching { run.logWriter.close() } }
            log.info("Gestopt: {} met exitcode {}", label, code)
            runCatching { onExit(code) }.onFailure { log.error("Fout in afronding van {}", label, it) }
        }, "exe-wait-${run.id}").apply { isDaemon = true }.start()

        // Waakhond: meldt in het log als er lang geen uitvoer komt terwijl het programma draait.
        Thread({
            var warned = 0L
            while (run.running) {
                Thread.sleep(1000)
                val silent = (System.nanoTime() - run.lastOutputNanos) / 1_000_000_000
                if (run.running && silent >= 5 && silent / 5 > warned) {
                    warned = silent / 5
                    run.logLine("STIL  al ${silent}s geen uitvoer (programma draait nog; wacht het op invoer of houdt het uitvoer vast?)")
                }
                if (silent < 5) warned = 0
            }
        }, "exe-watch-${run.id}").apply { isDaemon = true }.start()
        return snapshot()!!
    }

    /** Stuurt een regel (plus Enter) naar het lopende programma. */
    fun sendInput(line: String) {
        val run = current ?: error("Er draait geen programma.")
        check(run.running) { "Het programma is al gestopt." }
        val writer = run.writer ?: error("Dit programma draait in een eigen venster; typ daar je antwoord.")
        synchronized(run.output) { run.output.append("⟶ ").append(line).append("\n") }
        run.inputsSent++
        run.logLine("IN    ${quote(line + run.newline)}")
        try {
            writer.write(line + run.newline)
            writer.flush()
        } catch (e: Exception) {
            run.logLine("IN-FOUT ${e.message}")
            throw IllegalStateException("Invoer versturen mislukt: ${e.message}", e)
        }
    }

    fun kill() {
        current?.takeIf { it.running }?.let { run ->
            run.logLine("STOP  door gebruiker")
            run.process.descendants().forEach { it.destroyForcibly() }
            run.process.destroyForcibly()
        }
    }

    private fun pump(run: Run) {
        try {
            InputStreamReader(run.process.inputStream, charset).use { reader ->
                val buffer = CharArray(4096)
                while (true) {
                    val n = reader.read(buffer)
                    if (n < 0) break
                    val chunk = String(buffer, 0, n)
                    run.charsReceived += n
                    run.chunksReceived++
                    run.lastOutputNanos = System.nanoTime()
                    run.logLine("OUT   $n tekens: ${quote(chunk)}")
                    synchronized(run.output) {
                        run.output.append(chunk)
                        if (run.output.length > MAX_OUTPUT) run.output.delete(0, run.output.length - MAX_OUTPUT)
                    }
                }
            }
            run.logLine("EOF   uitvoerstroom gesloten")
        } catch (e: Exception) {
            run.logLine("OUT-FOUT ${e.message}")
        }
    }

    private fun quote(s: String): String =
        "\"" + s.replace("\\", "\\\\").replace("\r", "\\r").replace("\n", "\\n").replace("\t", "\\t") + "\""

    private fun isWindows() = System.getProperty("os.name").lowercase().contains("win")

    companion object {
        private const val MAX_OUTPUT = 200_000
    }
}
