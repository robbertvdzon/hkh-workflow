package nl.vdzon.hkhworkflow.exerunner

import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.io.Writer
import java.nio.charset.Charset
import java.nio.file.Path
import java.time.Instant
import java.util.concurrent.atomic.AtomicLong
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service

/** Hoe een programma wordt gestart. */
enum class RunMode(val label: String) {
    /** De app start het proces zelf, toont de uitvoer in de browser en stuurt invoer door. */
    IN_APP("in de app"),

    /** Windows: het programma opent in een eigen cmd-venster; de vragen beantwoord je daar. */
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
)

/**
 * Start externe programma's (images.exe, pdf2zcbs.exe) één tegelijk.
 *
 * In [RunMode.IN_APP] wordt stdout/stderr teken voor teken gelezen (prompts eindigen niet op een
 * nieuwe regel) en kan invoer via [sendInput] worden doorgestuurd.
 */
@Service
class ExeRunner(
    @Value("\${hkh.workflow.console-charset:IBM850}") charsetName: String,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val charset: Charset = runCatching { Charset.forName(charsetName) }.getOrElse { Charsets.UTF_8 }
    private val ids = AtomicLong()

    @Volatile
    private var current: Run? = null

    private class Run(
        val id: Long,
        val label: String,
        val mode: RunMode,
        val process: Process,
        val writer: Writer?,
        val startedAt: Instant,
    ) {
        val output = StringBuilder()

        @Volatile
        var exitCode: Int? = null

        @Volatile
        var finishedAt: Instant? = null

        val running: Boolean get() = finishedAt == null
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
        )
    }

    /**
     * Start [exe] met [workingDir] als werkmap. [onExit] wordt aangeroepen met de exitcode zodra het
     * proces stopt (of met null als starten mislukt na het aanmaken van het proces).
     */
    @Synchronized
    fun start(exe: Path, workingDir: Path, label: String, mode: RunMode, onExit: (Int?) -> Unit): RunSnapshot {
        check(!isRunning()) { "Er draait al een programma: ${current?.label}" }
        val command = when (mode) {
            RunMode.IN_APP -> listOf(exe.toString())
            RunMode.WINDOW -> {
                check(isWindows()) { "Een eigen venster openen kan alleen op Windows." }
                // 'start' opent een nieuw consolevenster; het eerste argument tussen quotes is de titel.
                listOf("cmd.exe", "/c", "start", "\"$label\"", "/wait", exe.toString())
            }
        }
        val process = ProcessBuilder(command)
            .directory(workingDir.toFile())
            .redirectErrorStream(true)
            .start()
        val writer = if (mode == RunMode.IN_APP) OutputStreamWriter(process.outputStream, charset) else null
        val run = Run(ids.incrementAndGet(), label, mode, process, writer, Instant.now())
        current = run
        log.info("Gestart: {} ({}) in {}", label, mode, workingDir)

        Thread({ pump(run) }, "exe-output-${run.id}").apply { isDaemon = true }.start()
        Thread({
            val code = try {
                process.waitFor()
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                null
            }
            // Even wachten zodat de laatste uitvoer nog binnenkomt.
            Thread.sleep(200)
            run.exitCode = code
            run.finishedAt = Instant.now()
            log.info("Gestopt: {} met exitcode {}", label, code)
            runCatching { onExit(code) }.onFailure { log.error("Fout in afronding van {}", label, it) }
        }, "exe-wait-${run.id}").apply { isDaemon = true }.start()
        return snapshot()!!
    }

    /** Stuurt een regel (plus Enter) naar het lopende programma. */
    fun sendInput(line: String) {
        val run = current ?: error("Er draait geen programma.")
        check(run.running) { "Het programma is al gestopt." }
        val writer = run.writer ?: error("Dit programma draait in een eigen venster; typ daar je antwoord.")
        synchronized(run.output) { run.output.append(line).append("\n") }
        writer.write(line + "\r\n")
        writer.flush()
    }

    fun kill() {
        current?.takeIf { it.running }?.process?.let { process ->
            process.descendants().forEach { it.destroyForcibly() }
            process.destroyForcibly()
        }
    }

    private fun pump(run: Run) {
        try {
            InputStreamReader(run.process.inputStream, charset).use { reader ->
                val buffer = CharArray(1024)
                while (true) {
                    val n = reader.read(buffer)
                    if (n < 0) break
                    synchronized(run.output) {
                        run.output.append(buffer, 0, n)
                        if (run.output.length > MAX_OUTPUT) run.output.delete(0, run.output.length - MAX_OUTPUT)
                    }
                }
            }
        } catch (e: Exception) {
            log.debug("Uitvoer lezen gestopt: {}", e.message)
        }
    }

    private fun isWindows() = System.getProperty("os.name").lowercase().contains("win")

    companion object {
        private const val MAX_OUTPUT = 200_000
    }
}
