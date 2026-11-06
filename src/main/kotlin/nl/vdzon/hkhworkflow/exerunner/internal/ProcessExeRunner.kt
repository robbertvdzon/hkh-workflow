package nl.vdzon.hkhworkflow.exerunner.internal

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
import java.util.Locale
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import nl.vdzon.hkhworkflow.exerunner.AutoAnswer
import nl.vdzon.hkhworkflow.exerunner.ExeRunner
import nl.vdzon.hkhworkflow.exerunner.ExeRunnerFactory
import nl.vdzon.hkhworkflow.exerunner.RunLogEntry
import nl.vdzon.hkhworkflow.exerunner.RunMode
import nl.vdzon.hkhworkflow.exerunner.RunSnapshot
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service

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

class ProcessExeRunner(charsetName: String, logDir: String) : ExeRunner {
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
        val auto: List<AutoAnswer>?,
        /** Vragen die de app ook bij handmatig uitvoeren zelf beantwoordt (bijvoorbeeld "verwijderen? nee"), zodat een verkeerde J ze niet kan veroorzaken. */
        val forced: List<AutoAnswer>,
        /** De vragen die we kennen, met het onthouden antwoord: voor een voorstel in de handmatige stand en om antwoorden te onthouden. */
        val known: List<AutoAnswer>,
    ) {
        val output = StringBuilder()

        /** De uitvoer sinds het laatste antwoord: daarin staat de vraag waar het programma nu op wacht. */
        val sinceInput = StringBuilder()

        /** Alle antwoorden die op een bekende vraag zijn gegeven, handmatig en automatisch. */
        val given = java.util.concurrent.ConcurrentHashMap<String, String>()

        /** Alleen wat de gebruiker zelf heeft ingetypt op een bekende vraag (zonder wachtwoorden). */
        val typed = java.util.concurrent.ConcurrentHashMap<String, String>()

        /** Wat de gebruiker zelf heeft ingetypt op een vraag waarvan het antwoord geheim is (een wachtwoord). */
        val typedSecret = java.util.concurrent.ConcurrentHashMap<String, String>()

        /** Vragen met een antwoord dat maar één keer per run wordt gegeven ([AutoAnswer.once]) en dat al is gegeven. */
        val onceDone: MutableSet<String> = java.util.concurrent.ConcurrentHashMap.newKeySet()

        @Volatile var exitCode: Int? = null
        @Volatile var finishedAt: Instant? = null
        @Volatile var charsReceived = 0L
        @Volatile var chunksReceived = 0L
        @Volatile var lastOutputNanos = startNanos
        @Volatile var inputsSent = 0
        @Volatile var lastInputNanos = 0L
        @Volatile var autoAnswered = 0
        @Volatile var autoEnabled = auto != null
        @Volatile var autoStuckLoggedAt = 0L
        val autoBuffer = StringBuilder()

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

    override fun isRunning(): Boolean = current?.running == true

    override fun snapshot(): RunSnapshot? = current?.let { run ->
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
            suspiciousSilence = run.running &&
                (System.nanoTime() - maxOf(run.lastOutputNanos, run.lastInputNanos)) / 1_000_000_000 >= 5 &&
                (run.charsReceived == 0L || run.lastInputNanos > run.lastOutputNanos),
            logFile = run.logFile.toString(),
            command = run.command,
            auto = run.auto != null,
            autoAnswered = run.autoAnswered,
            autoStuck = isAutoStuck(run),
            question = currentQuestion(run)?.pattern,
            suggestion = currentQuestion(run)?.takeIf { !it.secret && run.running }?.answer,
        )
    }

    /** Op welke bekende vraag wacht het programma? De laatst getoonde vraag sinds het laatste antwoord. */
    private fun currentQuestion(run: Run): AutoAnswer? {
        val text = synchronized(run.output) { run.sinceInput.toString() }
        return run.known.filter { it.pattern.isNotBlank() }
            .map { it to text.lastIndexOf(squeeze(it.pattern), ignoreCase = true) }
            .filter { it.second >= 0 }
            .maxByOrNull { it.second }?.first
    }

    /** De antwoorden die in de laatste run op bekende vragen zijn gegeven, per vraag. */
    override fun givenAnswers(): Map<String, String> = current?.given?.toMap().orEmpty()

    /** Wachtwoorden en andere geheime antwoorden die de gebruiker zelf heeft ingetypt in de laatste run, per vraag; alleen om te onthouden, nooit om te tonen. */
    override fun typedSecretAnswers(): Map<String, String> = current?.typedSecret?.toMap().orEmpty()

    /** De antwoorden die de gebruiker zelf heeft ingetypt in de laatste run, per vraag; die onthoudt de app voor de volgende keer. */
    override fun typedAnswers(): Map<String, String> = current?.typed?.toMap().orEmpty()

    /** Inhoud van het logbestand van de laatste run. */
    override fun logText(): String? = current?.let { run ->
        synchronized(run.logWriter) { runCatching { Files.readString(run.logFile) }.getOrNull() }
    }

    /** Inhoud van een logbestand uit de logmap, op naam (alleen namen zoals deze klasse ze zelf maakt). */
    override fun logText(file: String): String? {
        if (!LOG_NAME.matches(file)) return null
        val path = logDir.resolve(file)
        if (!Files.isRegularFile(path)) return null
        val run = current
        return if (run != null && run.logFile == path) logText() else runCatching { Files.readString(path) }.getOrNull()
    }

    /** De laatste [limit] runs volgens de logbestanden, nieuwste boven. */
    override fun history(limit: Int): List<RunLogEntry> {
        if (!Files.isDirectory(logDir)) return emptyList()
        val files = Files.list(logDir).use { stream ->
            stream.filter { Files.isRegularFile(it) && LOG_NAME.matches(it.fileName.toString()) }
                .map { it.fileName.toString() }
                .sorted(Comparator.reverseOrder())
                .limit(limit.toLong())
                .toList()
        }
        return files.map { readEntry(it) }
    }

    private fun readEntry(file: String): RunLogEntry {
        val match = LOG_NAME.matchEntire(file)
        val startedAt = match?.let { runCatching { LocalDateTime.parse(it.groupValues[1], STAMP_FORMAT) }.getOrNull() }
        var label = match?.groupValues?.get(3) ?: file
        var exitCode: Int? = null
        var stopped = false
        var lastElapsed: String? = null
        runCatching {
            Files.lines(logDir.resolve(file), Charsets.UTF_8).use { lines ->
                var first = true
                lines.forEach { line ->
                    if (first) {
                        first = false
                        line.substringAfter("] START ", "").takeIf { it.isNotBlank() }?.let { label = it }
                    }
                    if (line.contains("] STOP  door gebruiker")) stopped = true
                    if (line.contains("] EXIT code=")) {
                        exitCode = line.substringAfter("EXIT code=").substringBefore(",").trim().toIntOrNull()
                        lastElapsed = line.substringAfter("[").substringBefore("s]").trim()
                    }
                }
            }
        }
        val run = current
        val running = run != null && run.running && run.logFile.fileName.toString() == file
        val duration = lastElapsed?.toDoubleOrNull()?.let { String.format(Locale.forLanguageTag("nl"), "%.1f s", it) }
        return RunLogEntry(file, startedAt, label, running, exitCode, stopped, duration)
    }

    /** Verwijdert één logbestand. Het log van een lopend programma blijft staan. */
    override fun deleteLog(file: String): Boolean {
        if (!LOG_NAME.matches(file)) return false
        val path = logDir.resolve(file)
        val run = current
        if (run != null && run.running && run.logFile == path) return false
        if (run != null && run.logFile == path) current = null
        return Files.deleteIfExists(path)
    }

    /** Verwijdert alle logbestanden behalve dat van een lopend programma. @return aantal verwijderd. */
    override fun deleteAllLogs(): Int = history(Int.MAX_VALUE).count { deleteLog(it.file) }

    /**
     * Stopt een eventueel lopend programma en wacht tot het echt gestopt is (max. [timeoutSeconds]).
     * @return true als er niets (meer) draait.
     */
    override fun stopAndWait(timeoutSeconds: Long): Boolean {
        val run = current?.takeIf { it.running } ?: return true
        kill()
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(timeoutSeconds)
        while (run.running && System.nanoTime() < deadline) Thread.sleep(50)
        return !run.running
    }

    /** Vergeet de laatste run, zodat de console leeg is. Alleen als er niets draait. */
    @Synchronized
    override fun clear() {
        check(current?.running != true) { "Er draait nog een programma: ${current?.label}" }
        current = null
    }

    /**
     * Start [exe] met [workingDir] als werkmap. [onExit] wordt aangeroepen met de exitcode zodra het
     * proces stopt.
     */
    @Synchronized
    override fun start(
        exe: Path,
        workingDir: Path,
        label: String,
        mode: RunMode,
        extraEnv: Map<String, String>,
        notes: List<String>,
        autoAnswers: List<AutoAnswer>?,
        knownQuestions: List<AutoAnswer>,
        forcedAnswers: List<AutoAnswer>,
        onExit: (Int?) -> Unit,
    ): RunSnapshot {
        check(!isRunning()) { "Er draait al een programma: ${current?.label}" }
        val command = listOf(exe.toString())
        val id = ids.incrementAndGet()
        Files.createDirectories(logDir)
        val stamp = LocalDateTime.now().format(STAMP_FORMAT)
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
        val run = Run(id, label, mode, command.joinToString(" "), process, writer, logWriter, logFile, Instant.now(), startNanos, newline,
            autoAnswers?.takeIf { mode == RunMode.IN_APP }, forcedAnswers.takeIf { mode == RunMode.IN_APP && autoAnswers == null }.orEmpty(),
            (autoAnswers.orEmpty() + forcedAnswers + knownQuestions).distinctBy { it.pattern })
        current = run

        run.logLine("START $label")
        run.logLine("  commando    : ${run.command}")
        run.logLine("  werkmap     : $workingDir")
        run.logLine("  modus       : $mode")
        run.logLine("  charset     : $charset (invoer eindigt op ${if (run.newline == "\r\n") "CRLF" else "LF"})")
        run.logLine("  extra env   : ${if (extraEnv.isEmpty()) "(geen)" else extraEnv}")
        run.logLine("  argumenten  : ${if (command.size > 1 && mode == RunMode.IN_APP) command.drop(1) else "(geen; het programma werkt op de bestanden in zijn werkmap en stelt zijn vragen via de console)"}")
        run.logLine("  automatisch : ${run.auto?.let { "ja, ${it.size} regels: " + it.joinToString { a -> quote(a.pattern) } } ?: "nee"}")
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
                if (isAutoStuck(run) && run.autoStuckLoggedAt != run.lastOutputNanos) {
                    run.autoStuckLoggedAt = run.lastOutputNanos
                    val tail = synchronized(run.output) { run.output.takeLast(160).toString() }
                    run.logLine("AUTO  geen regel herkent de laatste vraag; wacht op handmatig antwoord. Laatste uitvoer: ${quote(tail)}")
                }
            }
        }, "exe-watch-${run.id}").apply { isDaemon = true }.start()
        return snapshot()!!
    }

    /** Stuurt een regel (plus Enter) naar het lopende programma. */
    override fun sendInput(line: String) {
        val run = current ?: error("Er draait geen programma.")
        check(run.running) { "Het programma is al gestopt." }
        val question = currentQuestion(run)
        // Een wachtwoord dat iemand intypt, komt niet leesbaar in de console of het log.
        send(run, line, secret = question?.secret == true, auto = false)
        if (question != null) {
            if (question.secret) run.typedSecret[question.pattern] = line
            else {
                run.given[question.pattern] = line
                run.typed[question.pattern] = line
            }
        }
    }

    private fun send(run: Run, line: String, secret: Boolean, auto: Boolean) {
        val writer = run.writer ?: error("Dit programma draait in een eigen venster; typ daar je antwoord.")
        val shown = if (secret) "*".repeat(8) else line
        synchronized(run.output) {
            run.output.append(if (auto) "⟶ (auto) " else "⟶ ").append(shown).append("\n")
            run.sinceInput.setLength(0)
        }
        run.inputsSent++
        run.lastInputNanos = System.nanoTime()
        val logged = if (secret) "(wachtwoord, ${line.length} tekens) + ${quote(run.newline)}" else quote(line + run.newline)
        run.logLine((if (auto) "IN    (auto) " else "IN    ") + logged)
        try {
            writer.write(line + run.newline)
            writer.flush()
        } catch (e: Exception) {
            run.logLine("IN-FOUT ${e.message}")
            throw IllegalStateException("Invoer versturen mislukt: ${e.message}", e)
        }
    }

    override fun kill() {
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
                        appendSqueezed(run.sinceInput, chunk)
                        if (run.sinceInput.length > MAX_OUTPUT) run.sinceInput.delete(0, run.sinceInput.length - MAX_OUTPUT)
                        if (run.output.length > MAX_OUTPUT) run.output.delete(0, run.output.length - MAX_OUTPUT)
                    }
                    if (run.autoEnabled || run.forced.isNotEmpty()) autoRespond(run, chunk)
                }
            }
            run.logLine("EOF   uitvoerstroom gesloten")
        } catch (e: Exception) {
            run.logLine("OUT-FOUT ${e.message}")
        }
    }

    /**
     * Vragen worden herkend zonder te letten op witruimte: het echte images.exe schrijft "Kies  1,2,3" met twee spaties, de handleiding en de instellingen
     * met één. Daarom staan de uitvoer (hier) en de teksten (in [squeeze]) beide met één spatie voor elke reeks witruimte, ook over brokken heen.
     */
    private fun appendSqueezed(buffer: StringBuilder, chunk: String) {
        var space = buffer.isNotEmpty() && buffer.last() == ' '
        for (c in chunk) {
            if (c.isWhitespace()) { if (!space) buffer.append(' '); space = true } else { buffer.append(c); space = false }
        }
    }

    private fun squeeze(text: String): String = text.replace(Regex("\\s+"), " ")

    /** Zoekt in de nieuwe uitvoer naar herkende vragen en beantwoordt ze, in volgorde van verschijnen. */
    private fun autoRespond(run: Run, chunk: String) {
        val rules = run.auto ?: run.forced.takeIf { it.isNotEmpty() } ?: return
        appendSqueezed(run.autoBuffer, chunk)
        while (run.autoEnabled || run.auto == null) {
            val text = run.autoBuffer.toString()
            var best: AutoAnswer? = null
            var bestIndex = Int.MAX_VALUE
            for (rule in rules) {
                if (rule.pattern.isBlank()) continue
                val i = text.indexOf(squeeze(rule.pattern), ignoreCase = true)
                if (i in 0 until bestIndex) {
                    best = rule
                    bestIndex = i
                }
            }
            val rule = best ?: return
            run.autoBuffer.delete(0, bestIndex + squeeze(rule.pattern).length)
            // Een antwoord dat maar één keer wordt gegeven (een onthouden wachtwoord): vraagt het programma het opnieuw, dan klopte het niet en typt de gebruiker het zelf.
            if (rule.once && !run.onceDone.add(rule.pattern)) continue
            if (run.autoAnswered >= MAX_AUTO_ANSWERS) {
                run.autoEnabled = false
                run.logLine("AUTO  uitgezet: al $MAX_AUTO_ANSWERS antwoorden gegeven (loopt het programma in een kringetje?)")
                return
            }
            run.autoAnswered++
            if (!rule.secret) run.given[rule.pattern] = rule.answer
            run.logLine("AUTO  vraag herkend: ${quote(rule.pattern)}")
            try {
                send(run, rule.answer, rule.secret, auto = true)
            } catch (e: Exception) {
                run.autoEnabled = false
                return
            }
        }
    }

    /** Automatisch, er kwam uitvoer na het laatste antwoord, maar al 5 s geen regel die past. */
    private fun isAutoStuck(run: Run): Boolean =
        run.running && run.autoEnabled && run.charsReceived > 0 &&
            run.lastOutputNanos > run.lastInputNanos &&
            (System.nanoTime() - run.lastOutputNanos) / 1_000_000_000 >= 5

    private fun quote(s: String): String =
        "\"" + s.replace("\\", "\\\\").replace("\r", "\\r").replace("\n", "\\n").replace("\t", "\\t") + "\""

    private fun isWindows() = System.getProperty("os.name").lowercase().contains("win")

    companion object {
        private const val MAX_OUTPUT = 200_000
        private const val MAX_AUTO_ANSWERS = 100
        private val STAMP_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")

        /** Logbestandsnaam zoals [start] die maakt: run-<datum-tijd>-<id>-<label>.log. */
        private val LOG_NAME = Regex("run-(\\d{8}-\\d{6})-(\\d+)-([A-Za-z0-9._-]*)\\.log")
    }
}

/** Maakt de runners: elke gebruiker en collectie heeft een eigen runner met een eigen map voor de logs. */
@org.springframework.stereotype.Component
class ProcessExeRunnerFactory(
    @Value("\${hkh.workflow.console-charset:IBM850}") private val charsetName: String,
    @Value("\${hkh.workflow.log-dir:logs}") private val logDir: String,
) : ExeRunnerFactory {
    override fun create(logSubfolder: String): ExeRunner = ProcessExeRunner(charsetName, Path.of(logDir, logSubfolder).toString())
}
