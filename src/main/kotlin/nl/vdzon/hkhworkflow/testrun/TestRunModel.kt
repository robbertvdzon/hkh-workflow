package nl.vdzon.hkhworkflow.testrun

import java.nio.file.Path
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/**
 * Soorten stappen, in de volgorde waarin ze draaien. Hernoemen en archiveren zijn per bestand. De rest draait één
 * keer per batch, omdat de programma's alles verwerken wat in hun map staat en pdf2zcbs zijn `output/` bij elke
 * start leegt.
 */
enum class StepType(val label: String, val perFile: Boolean, val usesExe: Boolean = false) {
    RENAME("Hernoemen", true),
    PDF2ZCBS("pdf2zcbs.exe", false, usesExe = true),
    IMAGES("images.exe", false, usesExe = true),
    UPLOAD("Upload (sftp)", false),
    EDIT("Records bewerken", false),
    CLEANUP("Opruimen", false),
    ARCHIVE("Archiveren", true),
}

enum class StepStatus(val label: String) {
    PENDING("Wacht"),
    RUNNING("Bezig"),
    DONE("Klaar"),
    SKIPPED("Overgeslagen"),
    FAILED("Mislukt"),
}

data class TestFile(val index: Int, val original: Path, val number: String, val by: String = "", val folder: String = "") {
    val originalName: String get() = original.fileName.toString()
    val newName: String get() = "$number.pdf"
}

/** Keuzes die de gebruiker bij het starten van images.exe maakt. Standaard schrijft het niets naar de server. */
data class StepOptions(
    /** Antwoord j op "UPLOADEN aangemaakte images ... naar webserver?" (schrijft in de echte collectie). */
    val upload: Boolean = false,
    /** Antwoord j op "AANMAKEN en UPLOADEN van de bijbehorende ... lege DATABASE RECORDS?" (schrijft in de echte collectie). */
    val records: Boolean = false,
)

/** Eén stap: voor een stap per bestand bevat [files] precies één bestand, voor een batchstap alle bestanden. */
class Step(val id: Int, val type: StepType, val files: List<TestFile>) {
    @Volatile
    var status: StepStatus = StepStatus.PENDING

    @Volatile
    var message: String? = null

    /** Paden op de server die in deze run zijn geüpload en gecontroleerd (alleen bij de uploadstap). */
    val uploaded: MutableSet<String> = ConcurrentHashMap.newKeySet()

    val finished: Boolean get() = status == StepStatus.DONE || status == StepStatus.SKIPPED

    /** Het bestand van een stap per bestand. */
    val file: TestFile get() = files.single()

    /** De bestandsnamen die de stap verwerkt: images.exe krijgt jpg's, de rest werkt met de genummerde pdf's. */
    val fileNames: String
        get() = files.joinToString { if (type == StepType.IMAGES) "${it.number}.jpg" else it.newName }

    val question: String
        get() = when (type) {
            StepType.RENAME -> "Hernoem ${file.originalName} naar ${file.newName}?"
            StepType.ARCHIVE -> "Verplaats origineel ${file.originalName} naar de archived-map?"
            StepType.PDF2ZCBS, StepType.IMAGES ->
                "Run ${type.label} één keer voor ${files.size} ${if (files.size == 1) "bestand" else "bestanden"} ($fileNames)?"
            StepType.UPLOAD -> "Upload tekst en pdf van ${files.size} ${if (files.size == 1) "bestand" else "bestanden"} naar de server?"
            StepType.EDIT -> "Bewerk ${files.size} ${if (files.size == 1) "record" else "records"} in ZCBS?"
            StepType.CLEANUP -> "Maak een back-up van de 100pct en ruim de mappen van de programma's op?"
        }
}

class TestRun(
    val startNumber: String,
    val testFolder: Path,
    val workDir: Path,
    val files: List<TestFile>,
    val steps: List<Step>,
    val createdAt: Instant = Instant.now(),
) {
    /** De eerstvolgende stap die nog niet klaar of overgeslagen is. */
    fun nextStep(): Step? = steps.firstOrNull { !it.finished }
}

// ---- Views voor de templates (onveranderlijke momentopnames) ----

data class StepView(
    val id: Int,
    val type: String,
    val typeLabel: String,
    /** Aantal bestanden in de stap (1 bij een stap per bestand). */
    val fileCount: Int,
    val status: String,
    val statusLabel: String,
    val message: String?,
    val question: String,
    val current: Boolean,
    val usesExe: Boolean,
    /** Wat deze stap gaat doen, ter goedkeuring (alleen gevuld voor de eerstvolgende stap). */
    val plan: List<String> = emptyList(),
    /** Reden waarom de stap nu niet kan draaien (alleen voor de eerstvolgende stap). */
    val problems: List<String> = emptyList(),
    /** Aandachtspunten die de stap niet blokkeren. */
    val warnings: List<String> = emptyList(),
)

/** Eén controle op een bestand. [state] is OK, MISSING of PENDING (stap nog niet aan de beurt). */
data class CheckView(val label: String, val state: String, val path: String)

data class CheckGroupView(val label: String, val items: List<CheckView>)

data class FileRowView(
    val originalName: String,
    val newName: String,
    /** Wie het gescand heeft: de persoon van de map waarin het bestand op de server stond (leeg als dat niet bekend is). */
    val by: String,
    val cells: List<StepView>,
    val checks: List<CheckGroupView>,
)

data class TestRunView(
    val startNumber: String,
    val testFolder: String,
    val workDir: String,
    val fileCount: Int,
    val doneCount: Int,
    val totalCount: Int,
    val rows: List<FileRowView>,
    val stepTypes: List<String>,
    val next: StepView?,
)
