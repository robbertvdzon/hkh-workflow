package nl.vdzon.hkhworkflow.testrun

import java.nio.file.Path
import java.time.Instant

enum class StepType(val label: String, val question: String) {
    RENAME("Hernoemen", "Hernoem %s naar %s?"),
    PDF2ZCBS("pdf2zcbs.exe", "Run pdf2zcbs.exe voor %2\$s?"),
    IMAGES("images.exe", "Run images.exe voor %2\$s?"),
    ARCHIVE("Archiveren", "Verplaats origineel %s naar de archived-map?"),
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

class Step(val id: Int, val type: StepType, val file: TestFile) {
    @Volatile
    var status: StepStatus = StepStatus.PENDING

    @Volatile
    var message: String? = null

    val finished: Boolean get() = status == StepStatus.DONE || status == StepStatus.SKIPPED
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
    val originalName: String,
    val newName: String,
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
