package nl.vdzon.hkhworkflow.testrun

import java.nio.file.Path
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap

/**
 * Soorten stappen, in de volgorde waarin ze draaien. Hernoemen is per bestand. De rest draait één keer voor alle
 * bestanden samen, omdat de programma's alles verwerken wat in hun map staat en pdf2zcbs zijn `output/` bij elke
 * start leegt. Het invullen van de gegevens in ZCBS hoort er niet bij: dat gebeurt op een andere dag.
 */
enum class StepType(val label: String, val perFile: Boolean, val usesExe: Boolean = false) {
    RENAME("Hernoemen", true),
    PDF2ZCBS("pdf2zcbs.exe", false, usesExe = true),
    IMAGES("images.exe", false, usesExe = true),
    UPLOAD("Upload (sftp)", false),
    CLEANUP("Back-up en opruimen", false),
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

/** Eén stap: voor een stap per bestand bevat [files] precies één bestand, voor een stap voor alles samen alle bestanden. */
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
            StepType.PDF2ZCBS, StepType.IMAGES ->
                "Run ${type.label} één keer voor ${files.size} ${if (files.size == 1) "bestand" else "bestanden"} ($fileNames)?"
            StepType.UPLOAD -> "Upload tekst en pdf van ${files.size} ${if (files.size == 1) "bestand" else "bestanden"} naar de server?"
            StepType.CLEANUP -> "Maak een back-up van de 100pct en de pdf's en ruim de mappen op?"
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
    /** Bij de flow van Carla moet ze de uitvoer van pdf2zcbs en images.exe eerst controleren voordat de volgende stap kan. */
    @Volatile
    var gated: Boolean = false

    /** De stappen waarvan de uitvoer is gecontroleerd. */
    val reviewed: MutableSet<StepType> = ConcurrentHashMap.newKeySet()

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
    /** Het recordnummer van dit bestand. */
    val number: String,
    /** Wie het gescand heeft: de naam van de bronmap waarin het bestand stond (leeg als dat niet bekend is). */
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


/**
 * Wat een eerdere run in de map van een programma (of in de invoermap van de gebruiker) heeft laten staan: [files] staan onder [base]; [area] is
 * `pdf2zcbs`, `images` of `invoer`. pdf2zcbs wist zijn `output` bij de start
 * en images.exe zou oude small, large en 100pct opnieuw uploaden, dus dit moet weg of opzij voordat een nieuwe doorloop begint.
 */
data class Leftover(val area: String, val label: String, val base: Path, val files: List<Path>) {
    val names: List<String> get() = files.map { base.relativize(it).toString() }
}

/** Eén controle van de omgeving aan het begin van de flow: [ok] of niet, en bij niet ok in [detail] wat er mis is en wat Carla kan doen. */
data class EnvCheck(val label: String, val ok: Boolean, val detail: String = "")

/** Een bestand dat pdf2zcbs.exe of images.exe heeft gemaakt, om te controleren: wat het is, hoe groot en (bij een afbeelding) in welke afmeting. */
data class ProducedFile(val number: String, val kind: String, val label: String, val size: String, val resolution: String, val note: String = "")

/** Eén fase in de balk bovenaan de pagina Nieuwe bestanden. [state] is done, now of todo. */
data class PhaseView(val nr: Int, val title: String, val state: String, val note: String?)

/**
 * De flow zoals Carla hem ziet: zes fasen (bestanden kiezen, nummers, pdf2zcbs, images.exe, upload, back-up). [current] is de
 * fase die aan de beurt is, of 7 als alles klaar is. [review] is gezet als de uitvoer van de huidige fase klaar is en Carla
 * hem moet controleren voordat ze verder kan.
 */
data class FlowView(val phases: List<PhaseView>, val current: Int, val finished: Boolean, val review: Boolean, val run: TestRunView?)
