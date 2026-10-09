package nl.vdzon.hkhworkflow.testrun

import java.nio.file.Path
import java.time.Instant

/**
 * Soorten stappen. Hernoemen en archiveren zijn per bestand; de twee programma's draaien **één keer per batch**,
 * omdat ze alles verwerken wat in hun map staat (en pdf2zcbs zijn `output/` bij elke start leegt).
 */
enum class StepType(val label: String, val perFile: Boolean) {
    RENAME("Hernoemen", true),
    PDF2ZCBS("pdf2zcbs.exe", false),
    IMAGES("images.exe", false),
    ARCHIVE("Archiveren", true),
}

enum class StepStatus(val label: String) {
    PENDING("Wacht"),
    RUNNING("Bezig"),
    DONE("Klaar"),
    SKIPPED("Overgeslagen"),
    FAILED("Mislukt"),
}

data class TestFile(val index: Int, val original: Path, val number: String) {
    val originalName: String get() = original.fileName.toString()
    val newName: String get() = "$number.pdf"
}

/** Eén stap: voor een stap per bestand bevat [files] precies één bestand, voor een batchstap alle bestanden. */
class Step(val id: Int, val type: StepType, val files: List<TestFile>) {
    @Volatile
    var status: StepStatus = StepStatus.PENDING

    @Volatile
    var message: String? = null

    val finished: Boolean get() = status == StepStatus.DONE || status == StepStatus.SKIPPED

    /** Het bestand van een stap per bestand. */
    val file: TestFile get() = files.single()

    val question: String
        get() = when (type) {
            StepType.RENAME -> "Hernoem ${file.originalName} naar ${file.newName}?"
            StepType.ARCHIVE -> "Verplaats origineel ${file.originalName} naar de archived-map?"
            StepType.PDF2ZCBS, StepType.IMAGES ->
                "Run ${type.label} één keer voor ${files.size} ${if (files.size == 1) "bestand" else "bestanden"} (${files.joinToString { it.newName }})?"
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
)

data class FileRowView(
    val originalName: String,
    val newName: String,
    val cells: List<StepView>,
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
