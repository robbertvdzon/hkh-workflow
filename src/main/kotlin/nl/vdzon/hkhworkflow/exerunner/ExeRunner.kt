package nl.vdzon.hkhworkflow.exerunner

import java.nio.file.Path
import java.time.Instant
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Hoe een programma wordt gestart. */
enum class RunMode(val label: String) {
    /** De app start het proces zelf, toont de uitvoer in de browser en stuurt invoer door. */
    IN_APP("in de app"),
}

/** Automatisch antwoord: bevat de uitvoer [pattern] (hoofdletterongevoelig), dan wordt [answer] + Enter gestuurd. */
data class AutoAnswer(val pattern: String, val answer: String, val secret: Boolean = false, val once: Boolean = false)

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
    /**
     * Verdacht stil: er draait iets, maar er kwam nog nooit uitvoer, of er kwam niets meer na de laatste
     * invoer. Wacht het programma gewoon op een antwoord na een zichtbare vraag, dan is dit false.
     */
    val suspiciousSilence: Boolean,
    val logFile: String,
    val command: String,
    /** Draait met automatische antwoorden. */
    val auto: Boolean,
    /** Aantal automatisch gegeven antwoorden. */
    val autoAnswered: Int,
    /** Automatisch: er staat een vraag die door geen enkele regel herkend wordt. */
    val autoStuck: Boolean,
    /** De bekende vraag waar het programma nu op wacht (de tekst waarmee hij herkend is), of null. */
    val question: String? = null,
    /** Het onthouden antwoord op [question], om vooraf in te vullen. Nooit een wachtwoord. */
    val suggestion: String? = null,
)

/** Eén eerdere of lopende run, afgeleid van het logbestand in de logmap. Blijft dus ook na een herstart zichtbaar. */
data class RunLogEntry(
    /** Bestandsnaam van het log (zonder map). */
    val file: String,
    val startedAt: LocalDateTime?,
    val label: String,
    val running: Boolean,
    val exitCode: Int?,
    /** Door de gebruiker gestopt. */
    val stopped: Boolean,
    /** Looptijd volgens de laatste logregel, bijv. "3,4 s". */
    val duration: String?,
) {
    val startedText: String get() = startedAt?.format(STARTED_FORMAT) ?: file

    companion object {
        private val STARTED_FORMAT = DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm:ss", Locale.forLanguageTag("nl"))
    }
}


/** Start één extern programma tegelijk, leest zijn uitvoer, geeft invoer en houdt per run een logbestand bij. */
interface ExeRunner {
    fun isRunning(): Boolean
    fun snapshot(): RunSnapshot?
    fun givenAnswers(): Map<String, String>
    fun typedSecretAnswers(): Map<String, String>
    fun typedAnswers(): Map<String, String>
    fun logText(): String?
    fun logText(file: String): String?
    fun history(limit: Int = 50): List<RunLogEntry>
    fun deleteLog(file: String): Boolean
    fun deleteAllLogs(): Int
    fun stopAndWait(timeoutSeconds: Long = 5): Boolean
    fun clear()
    fun start(
        exe: Path,
        workingDir: Path,
        label: String,
        mode: RunMode,
        extraEnv: Map<String, String> = emptyMap(),
        notes: List<String> = emptyList(),
        autoAnswers: List<AutoAnswer>? = null,
        knownQuestions: List<AutoAnswer> = emptyList(),
        forcedAnswers: List<AutoAnswer> = emptyList(),
        onExit: (Int?) -> Unit,
    ): RunSnapshot
    fun sendInput(line: String)
    fun kill()
}

/** Maakt een eigen [ExeRunner], met zijn logs in een eigen submap van de logmap. */
fun interface ExeRunnerFactory {
    fun create(logSubfolder: String): ExeRunner
}
