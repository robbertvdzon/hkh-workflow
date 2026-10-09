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
 * hernoemen → pdf2zcbs.exe → images.exe → archiveren. Elke stap start de gebruiker zelf.
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
        check(!exeRunner.isRunning()) { "Er draait nog een programma." }
        val settings = settingsService.get()
        require(settings.testFolder.isNotBlank()) { "Stel eerst de testmap in bij Instellingen." }
        val testFolder = Path.of(settings.testFolder)
        require(Files.isDirectory(testFolder)) { "De testmap bestaat niet: $testFolder" }
        val pdfs = pdfsInTestFolder()
        require(pdfs.isNotEmpty()) { "Er staan geen pdf's in de testmap." }

        val numbers = sequenceNumbers(startNumber, pdfs.size)
        val files = pdfs.mapIndexed { i, path -> TestFile(i, path, numbers[i]) }
        var id = 0
        val steps = StepType.entries.flatMap { type -> files.map { Step(++id, type, it) } }
        run = TestRun(startNumber.trim(), testFolder, testFolder.resolve("werk"), files, steps)
        log.info("Testrun gestart: {} bestanden vanaf {}", files.size, startNumber)
    }

    @Synchronized
    fun reset() {
        check(!exeRunner.isRunning()) { "Er draait nog een programma; stop dat eerst." }
        run = null
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
        val source = current.workDir.resolve(step.file.newName)
        check(Files.exists(source)) { "Werkbestand ontbreekt: $source (is de hernoem-stap gedaan?)" }

        // pdf2zcbs.exe verwerkt de pdf's in zijn eigen map; images.exe die in de submap 'images'.
        val inputDir = if (imagesSubfolder) exeDir.resolve("images") else exeDir
        Files.createDirectories(inputDir)
        val target = inputDir.resolve(step.file.newName)
        // Bij opnieuw proberen staat het bestand er vaak nog van de vorige poging: overschrijven.
        val replaced = Files.exists(target)
        Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING)

        step.status = StepStatus.RUNNING
        step.message = (if (replaced) "Overschreven in " else "Gekopieerd naar ") + inputDir
        val settings = settingsService.get()
        val env = if (settings.perlioUnbuffered) mapOf("PERLIO" to ":unix") else emptyMap()
        val rules = if (imagesSubfolder) settings.imagesAnswers else settings.pdf2zcbsAnswers
        val autoAnswers = if (auto) rules.map { AutoAnswer(it.pattern, it.answer, it.secret) } else null
        val notes = listOf(
            "origineel ${step.file.original}",
            "${if (replaced) "overschreven" else "gekopieerd"}: $source -> $target",
        )
        exeRunner.start(exe, exeDir, "${step.type.label} – ${step.file.newName}", mode, env, notes, autoAnswers) { code ->
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
            originalName = step.file.originalName,
            newName = step.file.newName,
            status = step.status.name,
            statusLabel = step.status.label,
            message = step.message,
            question = step.type.question.format(step.file.originalName, step.file.newName),
            current = step.id == next?.id,
            usesExe = step.type == StepType.PDF2ZCBS || step.type == StepType.IMAGES,
        )
        val rows = current.files.map { file ->
            FileRowView(
                originalName = file.originalName,
                newName = file.newName,
                cells = current.steps.filter { it.file.index == file.index }.map(::toView),
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
