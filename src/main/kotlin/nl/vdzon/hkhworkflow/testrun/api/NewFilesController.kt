package nl.vdzon.hkhworkflow.testrun.api

import java.nio.file.Files
import nl.vdzon.hkhworkflow.exerunner.ExeRunner
import nl.vdzon.hkhworkflow.items.ItemEditService
import nl.vdzon.hkhworkflow.settings.SettingsService
import nl.vdzon.hkhworkflow.testrun.ProcessedLog
import nl.vdzon.hkhworkflow.testrun.CopyResult
import nl.vdzon.hkhworkflow.testrun.SourceFilesService
import nl.vdzon.hkhworkflow.testrun.StepType
import nl.vdzon.hkhworkflow.testrun.TestRunService
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.servlet.mvc.support.RedirectAttributes

/**
 * "Nieuwe bestanden in ZCBS zetten": de flow die Carla doorloopt. Ze kiest scans uit de mappen op K:, geeft ze recordnummers uit
 * de vrije nummers van ZCBS, draait pdf2zcbs.exe en images.exe (met een controle na elk), uploadt tekst en pdf en eindigt met de
 * back-up. Het invullen van de gegevens in ZCBS hoort er niet bij.
 */
@Controller
class NewFilesController(
    private val testRunService: TestRunService,
    private val exeRunner: ExeRunner,
    private val settingsService: SettingsService,
    private val sourceFiles: SourceFilesService,
    private val itemEditService: ItemEditService,
    private val processedLog: ProcessedLog,
) {
    /** Een pdf in de invoermap, met de bronmap waar hij vandaan kwam. */
    data class PdfRow(val name: String, val by: String, val folder: String)

    /** Een vrije reeks uit ZCBS voor de tabel: [fits] is waar als alle bestanden erin passen. */
    data class RangeRow(val from: String, val label: String, val size: String, val fits: Boolean)

    @GetMapping("/nieuw")
    fun page(
        @RequestParam("bron", required = false) rootName: String?,
        @RequestParam("pad", defaultValue = "") path: String,
        model: Model,
    ): String {
        val settings = settingsService.get()
        val flow = testRunService.flowView()
        // Standaard de eerste map met scans uit de instellingen, zodat Carla meteen bestanden ziet.
        val rootName = rootName?.takeIf { it.isNotBlank() } ?: sourceFiles.roots().firstOrNull()?.name.takeIf { flow.current == 2 }
        model.addAttribute("active", "nieuw")
        model.addAttribute("returnTo", "/nieuw")
        model.addAttribute("settings", settings)
        model.addAttribute("flow", flow)
        model.addAttribute("run", flow.run)
        model.addAttribute("console", exeRunner.snapshot())
        when (flow.current) {
            1 -> {
                val checks = testRunService.environment()
                val leftovers = testRunService.leftovers()
                model.addAttribute("checks", checks)
                model.addAttribute("allOk", checks.all { it.ok })
                model.addAttribute("leftovers", leftovers)
                model.addAttribute("leftoversTarget", testRunService.leftoversTarget()?.toString().orEmpty())
            }

            2 -> {
                val pdfs = testRunService.pdfsInTestFolder().map {
                    val origin = sourceFiles.origin(it.fileName.toString())
                    PdfRow(it.fileName.toString(), origin?.by.orEmpty(), origin?.folder.orEmpty())
                }
                model.addAttribute("roots", sourceFiles.roots())
                model.addAttribute("pdfs", pdfs)
                if (rootName != null) {
                    try {
                        model.addAttribute("listing", sourceFiles.browse(rootName, path))
                    } catch (e: IllegalArgumentException) {
                        model.addAttribute("browseError", e.message)
                    }
                }
            }

            3 -> {
                val count = testRunService.pdfsInTestFolder().size
                val free = itemEditService.freeNumbers()
                model.addAttribute("count", count)
                model.addAttribute("free", free)
                if (free != null) {
                    model.addAttribute("ranges", free.ranges.map { RangeRow(free.format(it.from), free.format(it), if (it.open) "onbeperkt" else it.size.toString(), it.size >= count) })
                    model.addAttribute("lastNumber", free.last?.let { free.format(it) })
                    model.addAttribute("suggestion", free.suggest(count)?.let { free.format(it) })
                    model.addAttribute("width", free.width)
                }
            }

            4, 5 -> if (flow.review && flow.run != null) {
                val type = if (flow.current == 4) StepType.PDF2ZCBS else StepType.IMAGES
                model.addAttribute("reviewStepId", flow.run.rows.first().cells[StepType.entries.indexOf(type)].id)
                model.addAttribute("reviewType", type.name)
                model.addAttribute("produced", testRunService.producedFiles(type))
                model.addAttribute("names", flow.run.rows.associate { it.number to it.originalName })
            }
        }
        return "nieuw"
    }

    /** Alles is in orde en Carla gaat verder met het kiezen van bestanden. */
    @PostMapping("/nieuw/start")
    fun begin(redirect: RedirectAttributes): String = attempt(redirect) { testRunService.startFlow() }

    private fun attempt(redirect: RedirectAttributes, vararg query: Pair<String, String>, action: () -> String?): String {
        try {
            action()?.let { redirect.addFlashAttribute("error", it) }
        } catch (e: Exception) {
            redirect.addFlashAttribute("error", e.message ?: e.javaClass.simpleName)
        }
        query.forEach { (k, v) -> redirect.addAttribute(k, v) }
        return "redirect:/nieuw"
    }

    /**
     * "Verder" bij het kiezen: de aangevinkte pdf's uit de map waar Carla nu is worden gekozen (naar de invoermap gekopieerd) en de flow gaat door naar de nummers.
     * Lukt dat niet (niets aangevinkt, alles al verwerkt), dan blijft ze in de map met een melding.
     */
    @PostMapping("/nieuw/kopieer")
    fun copy(
        @RequestParam("bron") rootName: String,
        @RequestParam("pad", defaultValue = "") path: String,
        @RequestParam("files", required = false) files: List<String>?,
        redirect: RedirectAttributes,
    ): String {
        fun stay(message: String): String {
            redirect.addFlashAttribute("error", message)
            redirect.addAttribute("bron", rootName)
            redirect.addAttribute("pad", path)
            return "redirect:/nieuw"
        }
        return try {
            val result = if (files.isNullOrEmpty()) CopyResult(emptyList(), emptyList()) else sourceFiles.copy(rootName, path, files)
            testRunService.acceptInput()
            val skipped = if (result.skipped.isEmpty()) "" else " Overgeslagen: ${result.skipped.joinToString()}."
            if (testRunService.pdfsInTestFolder().isEmpty()) return stay("Vink minstens één bestand aan.$skipped")
            testRunService.confirmFiles()?.let { return stay(it) }
            if (skipped.isNotEmpty()) redirect.addFlashAttribute("info", skipped.trim())
            "redirect:/nieuw"
        } catch (e: Exception) {
            stay(e.message ?: e.javaClass.simpleName)
        }
    }

    /** Haalt de gekozen bestanden weer weg (alleen de kopieën; de originelen blijven staan). */
    @PostMapping("/nieuw/bestanden/leeg")
    fun clearChosen(redirect: RedirectAttributes): String = attempt(redirect) { sourceFiles.removeAllCopies(); null }

    /** Resten van een eerdere run uit de mappen van de programma's halen: verplaatsen of verwijderen. */
    @PostMapping("/nieuw/resten")
    fun leftovers(@RequestParam("actie") action: String, redirect: RedirectAttributes): String = attempt(redirect) {
        if (action == "accepteer") {
            testRunService.acceptInput()
            redirect.addFlashAttribute("info", "Je gekozen bestanden horen erbij.")
        } else redirect.addFlashAttribute("info", testRunService.clearLeftovers(move = action == "verplaats"))
        null
    }

    /** Geeft de bestanden hun recordnummers en maakt kopieën met het nummer als naam. */
    @PostMapping("/nieuw/voorbereiden")
    fun prepare(@RequestParam("start", defaultValue = "") start: String, redirect: RedirectAttributes): String =
        attempt(redirect) { testRunService.prepare(start) }

    /** Carla heeft de uitvoer van pdf2zcbs.exe of images.exe gecontroleerd. */
    @PostMapping("/nieuw/gecontroleerd")
    fun reviewed(@RequestParam("stap") type: StepType, redirect: RedirectAttributes): String =
        attempt(redirect) { testRunService.confirmReview(type) }

    @PostMapping("/nieuw/opnieuw")
    fun reset(redirect: RedirectAttributes): String = attempt(redirect) { testRunService.reset(); null }

    /** Een bestand uit de uitvoer van de programma's, om te controleren: de afbeelding, de pdf of de tekst. */
    @GetMapping("/nieuw/resultaat/{kind}/{number}")
    fun result(@PathVariable("kind") kind: String, @PathVariable("number") number: String): ResponseEntity<ByteArray> {
        val file = testRunService.resultFile(kind, number) ?: return ResponseEntity.notFound().build()
        val type = when (kind) {
            "pdf" -> "application/pdf"
            "tekst" -> "text/plain;charset=UTF-8"
            else -> "image/jpeg"
        }
        return ResponseEntity.ok().header("Content-Type", type).header("Cache-Control", "no-store").body(Files.readAllBytes(file))
    }

    /** Wat er al op het archief is gezet, per keer verwerken. */
    @GetMapping("/verwerkt")
    fun processed(model: Model): String {
        model.addAttribute("active", "verwerkt")
        model.addAttribute("runs", processedLog.runs())
        model.addAttribute("logFile", processedLog.file().toString())
        model.addAttribute("settings", settingsService.get())
        return "verwerkt"
    }
}
