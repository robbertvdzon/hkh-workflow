package nl.vdzon.hkhworkflow.testrun.api

import nl.vdzon.hkhworkflow.exerunner.ExeRunner
import nl.vdzon.hkhworkflow.items.ItemEditService
import nl.vdzon.hkhworkflow.settings.SettingsService
import nl.vdzon.hkhworkflow.testrun.SourceFilesService
import nl.vdzon.hkhworkflow.testrun.TestRunService
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.servlet.mvc.support.RedirectAttributes

/**
 * De batch zoals Carla hem doorloopt: acht fasen van inloggen tot afsluiten, met per fase een goedkeuring. Het gebruikt
 * dezelfde stappen en controles als de pagina Test, maar dan als één doorlopend scherm.
 */
@Controller
class BatchController(
    private val testRunService: TestRunService,
    private val exeRunner: ExeRunner,
    private val settingsService: SettingsService,
    private val sourceFiles: SourceFilesService,
    private val itemEditService: ItemEditService,
) {
    data class PdfRow(val name: String, val by: String, val folder: String)

    @GetMapping("/batch")
    fun page(model: Model): String {
        val settings = settingsService.get()
        val batch = testRunService.batchView()
        model.addAttribute("active", "batch")
        model.addAttribute("returnTo", "/batch")
        model.addAttribute("settings", settings)
        model.addAttribute("batch", batch)
        model.addAttribute("run", batch.run)
        model.addAttribute("console", exeRunner.snapshot())
        model.addAttribute("zcbsLoggedIn", itemEditService.loggedIn())
        model.addAttribute("zcbsUser", itemEditService.user)
        model.addAttribute("zcbsLocal", settings.zcbsIsLocal())
        model.addAttribute("sourceConfigured", settings.sourceFolder.isNotBlank())
        model.addAttribute("settingsProblems", settingsService.check(settings) + settingsService.checkExtras(settings))
        if (batch.current == 2) {
            model.addAttribute("sourceGroups", sourceFiles.candidates().groupBy { it.folder })
            model.addAttribute("pdfs", testRunService.pdfsInTestFolder().map {
                val origin = sourceFiles.origin(it.fileName.toString())
                PdfRow(it.fileName.toString(), origin?.by.orEmpty(), origin?.folder.orEmpty())
            })
        }
        if (batch.current == 3) {
            model.addAttribute("pdfCount", testRunService.pdfsInTestFolder().size)
            val last = runCatching { itemEditService.nextFreeNumber() }.getOrNull()
            model.addAttribute("suggestion", last?.toString())
        }
        if (batch.current == 7) {
            model.addAttribute("items", itemEditService)
        }
        return "batch"
    }

    private fun attempt(redirect: RedirectAttributes, action: () -> String?): String {
        try {
            action()?.let { redirect.addFlashAttribute("error", it) }
        } catch (e: Exception) {
            redirect.addFlashAttribute("error", e.message ?: e.javaClass.simpleName)
        }
        return "redirect:/batch"
    }

    /** De aangevinkte pdf's van de server naar de invoermap (testmap) kopiëren. */
    @PostMapping("/batch/bestanden/kopieer")
    fun copy(@RequestParam("files", required = false) files: List<String>?, redirect: RedirectAttributes): String = attempt(redirect) {
        val result = sourceFiles.copy(files.orEmpty())
        val skipped = if (result.skipped.isEmpty()) "" else " Overgeslagen: ${result.skipped.joinToString()}."
        redirect.addFlashAttribute("info", "${result.copied.size} bestanden gekopieerd naar de invoermap. De originelen op de server blijven staan.$skipped")
        null
    }

    @PostMapping("/batch/bestanden/bevestig")
    fun confirm(redirect: RedirectAttributes): String = attempt(redirect) { testRunService.confirmFiles(); null }

    @PostMapping("/batch/bestanden/andere")
    fun reselect(redirect: RedirectAttributes): String = attempt(redirect) { testRunService.reopenFiles(); null }

    @PostMapping("/batch/voorbereiden")
    fun prepare(@RequestParam("start", defaultValue = "") start: String, redirect: RedirectAttributes): String =
        attempt(redirect) { testRunService.prepare(start) }

    @PostMapping("/batch/afsluiten")
    fun finish(redirect: RedirectAttributes): String = attempt(redirect) { testRunService.finish() }

    @PostMapping("/batch/opnieuw")
    fun reset(redirect: RedirectAttributes): String = attempt(redirect) { testRunService.reset(); null }
}
