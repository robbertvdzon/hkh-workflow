package nl.vdzon.hkhworkflow.testrun

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlin.io.path.extension
import kotlin.io.path.isRegularFile
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import nl.vdzon.hkhworkflow.exerunner.AutoAnswer
import nl.vdzon.hkhworkflow.exerunner.ExeRunner
import nl.vdzon.hkhworkflow.exerunner.RunMode
import nl.vdzon.hkhworkflow.settings.SettingsService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

/**
 * De testworkflow: alle pdf's in de testmap krijgen een volgnummer en gaan stap voor stap door
 * hernoemen (per bestand) → pdf2zcbs.exe (één keer) → images.exe (één keer) → archiveren (per bestand).
 * Elke stap start de gebruiker zelf.
 *
 * Het origineel blijft tot het archiveren onaangeroerd in de testmap; "hernoemen" maakt een kopie
 * met het nieuwe nummer in de submap `werk`. Zo kan het origineel met zijn eigen naam worden
 * gearchiveerd.
 */
@Service
class TestRunService(
    private val settingsService: SettingsService,
    private val exeRunner: ExeRunner,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Volatile
    private var run: TestRun? = null

    /** Pdf's die nu in de testmap staan (alleen de map zelf, geen submappen). */
    fun pdfsInTestFolder(): List<Path> {
        val folder = settingsService.get().testFolder
        if (folder.isBlank()) return emptyList()
        val dir = Path.of(folder)
        if (!Files.isDirectory(dir)) return emptyList()
        return dir.listDirectoryEntries()
            .filter { it.isRegularFile() && it.extension.equals("pdf", ignoreCase = true) }
            .sortedBy { it.name.lowercase() }
    }

    @Synchronized
    fun start(startNumber: String) {
        stopPrevious()
        val settings = settingsService.get()
        require(settings.testFolder.isNotBlank()) { "Stel eerst de testmap in bij Instellingen." }
        val testFolder = Path.of(settings.testFolder)
        require(Files.isDirectory(testFolder)) { "De testmap bestaat niet: $testFolder" }
        val pdfs = pdfsInTestFolder()
        require(pdfs.isNotEmpty()) { "Er staan geen pdf's in de testmap." }

        val numbers = sequenceNumbers(startNumber, pdfs.size)
        val files = pdfs.mapIndexed { i, path -> TestFile(i, path, numbers[i]) }
        var id = 0
        val steps = StepType.entries.flatMap { type ->
            if (type.perFile) files.map { Step(++id, type, listOf(it)) } else listOf(Step(++id, type, files))
        }
        run = TestRun(startNumber.trim(), testFolder, testFolder.resolve("werk"), files, steps)
        log.info("Testrun gestart: {} bestanden vanaf {}", files.size, startNumber)
    }

    @Synchronized
    fun reset() {
        stopPrevious()
        run = null
    }

    /** Een nieuwe test begint schoon: een nog lopend programma wordt gestopt en de console leeggemaakt. */
    private fun stopPrevious() {
        check(exeRunner.stopAndWait()) { "Het lopende programma wil niet stoppen; probeer het nog eens." }
        exeRunner.clear()
        log.info("Vorige testrun opgeruimd")
    }

    @Synchronized
    fun execute(stepId: Int, mode: RunMode, auto: Boolean = false) {
        val current = run ?: error("Er is geen testrun gestart.")
        val step = current.steps.firstOrNull { it.id == stepId } ?: error("Onbekende stap $stepId.")
        check(current.nextStep()?.id == step.id) { "Deze stap is nog niet aan de beurt." }
        check(step.status != StepStatus.RUNNING) { "Deze stap is al bezig." }
        check(!exeRunner.isRunning()) { "Er draait nog een programma." }
        step.message = null
        try {
            when (step.type) {
                StepType.RENAME -> rename(current, step)
                StepType.PDF2ZCBS -> runExe(current, step, settingsService.get().pdf2zcbsExe, mode, auto, imagesSubfolder = false)
                StepType.IMAGES -> runExe(current, step, settingsService.get().imagesExe, mode, auto, imagesSubfolder = true)
                StepType.ARCHIVE -> archive(step)
            }
        } catch (e: Exception) {
            step.status = StepStatus.FAILED
            step.message = e.message ?: e.javaClass.simpleName
            log.warn("Stap {} ({}) mislukt: {}", step.id, step.type, step.message)
        }
    }

    @Synchronized
    fun skip(stepId: Int) = update(stepId) { it.status = StepStatus.SKIPPED }

    @Synchronized
    fun markDone(stepId: Int) = update(stepId) { it.status = StepStatus.DONE; it.message = "Handmatig op klaar gezet." }

    @Synchronized
    fun retry(stepId: Int) = update(stepId) { it.status = StepStatus.PENDING; it.message = null }

    private fun update(stepId: Int, change: (Step) -> Unit) {
        val step = run?.steps?.firstOrNull { it.id == stepId } ?: error("Onbekende stap $stepId.")
        check(step.status != StepStatus.RUNNING) { "Deze stap is nog bezig." }
        change(step)
    }

    private fun rename(current: TestRun, step: Step) {
        Files.createDirectories(current.workDir)
        val target = current.workDir.resolve(step.file.newName)
        check(!Files.exists(target)) { "${target.fileName} bestaat al in ${current.workDir}." }
        check(Files.exists(step.file.original)) { "Origineel niet gevonden: ${step.file.original}" }
        Files.copy(step.file.original, target)
        step.status = StepStatus.DONE
        step.message = "Kopie gemaakt: werk\\${step.file.newName}"
    }

    private fun runExe(
        current: TestRun,
        step: Step,
        exeSetting: String,
        mode: RunMode,
        auto: Boolean,
        imagesSubfolder: Boolean,
    ) {
        require(exeSetting.isNotBlank()) { "Stel eerst het pad naar ${step.type.label} in." }
        val exe = Path.of(exeSetting)
        require(Files.isRegularFile(exe)) { "${step.type.label} niet gevonden: $exe" }
        val exeDir = exe.toAbsolutePath().parent
        val sources = step.files.map { it to current.workDir.resolve(it.newName) }
        sources.forEach { (_, source) -> check(Files.exists(source)) { "Werkbestand ontbreekt: $source (is de hernoem-stap gedaan?)" } }

        // pdf2zcbs.exe verwerkt alle pdf's in zijn eigen map; images.exe alles in de submap 'images'.
        // Daarom draaien ze één keer per batch, en mag er niets anders in die map staan.
        val inputDir = if (imagesSubfolder) exeDir.resolve("images") else exeDir
        Files.createDirectories(inputDir)
        val expected = step.files.map { it.newName.lowercase() }.toSet()
        val strangers = Files.list(inputDir).use { list ->
            list.filter { Files.isRegularFile(it) && it.extension.equals("pdf", ignoreCase = true) && it.name.lowercase() !in expected }
                .map { it.name }.sorted().toList()
        }
        check(strangers.isEmpty()) {
            "In $inputDir staan nog andere pdf's die ${step.type.label} ook zou verwerken: ${strangers.joinToString()}. Verwijder die eerst."
        }
        val notes = mutableListOf<String>()
        var replaced = 0
        sources.forEach { (file, source) ->
            val target = inputDir.resolve(file.newName)
            // Bij opnieuw proberen staan de bestanden er vaak nog van de vorige poging: overschrijven.
            val existed = Files.exists(target)
            if (existed) replaced++
            Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING)
            notes += "origineel ${file.original}"
            notes += "${if (existed) "overschreven" else "gekopieerd"}: $source -> $target"
        }

        step.status = StepStatus.RUNNING
        step.message = "${sources.size} bestanden in $inputDir gezet" + (if (replaced > 0) " ($replaced overschreven)" else "")
        val settings = settingsService.get()
        val env = if (settings.perlioUnbuffered) mapOf("PERLIO" to ":unix") else emptyMap()
        val rules = if (imagesSubfolder) settings.imagesAnswers else settings.pdf2zcbsAnswers
        val autoAnswers = if (auto) rules.map { AutoAnswer(it.pattern, it.answer, it.secret) } else null
        val label = "${step.type.label} – ${sources.size} ${if (sources.size == 1) "bestand" else "bestanden"} (${step.files.joinToString { it.newName }})"
        exeRunner.start(exe, exeDir, label, mode, env, notes, autoAnswers) { code ->
            synchronized(this) {
                if (code == 0) {
                    step.status = StepStatus.DONE
                    step.message = "Klaar (exitcode 0)."
                } else {
                    step.status = StepStatus.FAILED
                    step.message = "Gestopt met exitcode ${code ?: "onbekend"}. Controleer de uitvoer; " +
                        "zet de stap handmatig op klaar als het toch goed ging."
                }
            }
        }
    }

    private fun archive(step: Step) {
        val folder = settingsService.get().archivedFolder
        require(folder.isNotBlank()) { "Stel eerst de archived-map in bij Instellingen." }
        val archived = Path.of(folder)
        Files.createDirectories(archived)
        check(Files.exists(step.file.original)) { "Origineel niet gevonden: ${step.file.original}" }
        val target = uniqueTarget(archived, step.file.originalName)
        Files.move(step.file.original, target)
        step.status = StepStatus.DONE
        step.message = "Verplaatst naar $target"
    }

    private fun uniqueTarget(dir: Path, name: String): Path {
        val base = name.substringBeforeLast('.')
        val ext = name.substringAfterLast('.', "")
        var candidate = dir.resolve(name)
        var i = 1
        while (Files.exists(candidate)) {
            candidate = dir.resolve(if (ext.isEmpty()) "$base-$i" else "$base-$i.$ext")
            i++
        }
        return candidate
    }

    @Synchronized
    fun view(): TestRunView? {
        val current = run ?: return null
        val next = current.nextStep()
        fun toView(step: Step) = StepView(
            id = step.id,
            type = step.type.name,
            typeLabel = step.type.label,
            fileCount = step.files.size,
            status = step.status.name,
            statusLabel = step.status.label,
            message = step.message,
            question = step.question,
            current = step.id == next?.id,
            usesExe = !step.type.perFile,
        )
        // Per bestand een rij; een batchstap staat (met dezelfde status) in elke rij.
        val rows = current.files.map { file ->
            FileRowView(
                originalName = file.originalName,
                newName = file.newName,
                cells = StepType.entries.map { type -> toView(current.steps.first { it.type == type && it.files.contains(file) }) },
            )
        }
        return TestRunView(
            startNumber = current.startNumber,
            testFolder = current.testFolder.toString(),
            workDir = current.workDir.toString(),
            fileCount = current.files.size,
            doneCount = current.steps.count { it.finished },
            totalCount = current.steps.size,
            rows = rows,
            stepTypes = StepType.entries.map { it.label },
            next = next?.let(::toView),
        )
    }
}
