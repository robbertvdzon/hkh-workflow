package nl.vdzon.hkhworkflow.testrun.api

import jakarta.servlet.http.HttpServletResponse
import nl.vdzon.hkhworkflow.exerunner.ExeRunner
import nl.vdzon.hkhworkflow.exerunner.RunMode
import nl.vdzon.hkhworkflow.settings.SettingsService
import nl.vdzon.hkhworkflow.testrun.StepOptions
import nl.vdzon.hkhworkflow.testrun.TestRunService
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseBody
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.servlet.mvc.support.RedirectAttributes

@Controller
class TestController(
    private val testRunService: TestRunService,
    private val exeRunner: ExeRunner,
    private val settingsService: SettingsService,
) {

    @GetMapping("/test")
    fun page(model: Model): String {
        val settings = settingsService.get()
        model.addAttribute("active", "test")
        model.addAttribute("settings", settings)
        model.addAttribute("problems", settingsService.check(settings) + settingsService.checkExtras(settings))
        model.addAttribute("pdfs", testRunService.pdfsInTestFolder().map { it.fileName.toString() })
        model.addAttribute("run", testRunService.view())
        model.addAttribute("console", exeRunner.snapshot())
        model.addAttribute("runs", exeRunner.history())
        return "test"
    }

    /** Het logbestand van een eerdere run, als fragment onder de runlijst op de testpagina. */
    @GetMapping("/test/runs/log")
    fun runLog(@RequestParam("file") file: String, model: Model): String {
        model.addAttribute("logFile", file)
        model.addAttribute("logText", exeRunner.logText(file) ?: "Logbestand niet gevonden.")
        return "test :: runlog"
    }

    @PostMapping("/test/runs/delete")
    fun deleteRun(@RequestParam("file") file: String, redirect: RedirectAttributes): String = attempt(redirect) {
        check(exeRunner.deleteLog(file)) { "Dit log kan niet verwijderd worden (bestaat het niet, of draait het programma nog?)." }
    }

    @PostMapping("/test/runs/delete-all")
    fun deleteAllRuns(redirect: RedirectAttributes): String = attempt(redirect) {
        val n = exeRunner.deleteAllLogs()
        redirect.addFlashAttribute("info", "$n logbestanden verwijderd.")
    }


    /** Fragment dat elke seconde wordt opgehaald zolang er een programma draait. */
    @GetMapping("/test/console")
    fun console(model: Model, response: HttpServletResponse): String {
        val snapshot = exeRunner.snapshot()
        if (snapshot == null || !snapshot.running) {
            // Programma is klaar: laat de hele pagina verversen zodat de stappen bijgewerkt worden.
            response.setHeader("HX-Refresh", "true")
        }
        model.addAttribute("console", snapshot)
        return "test :: console"
    }

    /** Het volledige logbestand van de laatste run, als platte tekst. */
    @GetMapping("/test/log", produces = ["text/plain;charset=UTF-8"])
    @ResponseBody
    fun logFile(): String = exeRunner.logText() ?: "Nog geen run."

    @PostMapping("/test/console/input")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun input(@RequestParam("line", defaultValue = "") line: String) {
        exeRunner.sendInput(line)
    }

    @PostMapping("/test/console/stop")
    fun stop(): String {
        exeRunner.kill()
        return "redirect:/test"
    }

    @PostMapping("/test/start")
    fun start(@RequestParam("startNumber") startNumber: String, redirect: RedirectAttributes): String =
        attempt(redirect) { testRunService.start(startNumber) }

    @PostMapping("/test/reset")
    fun reset(redirect: RedirectAttributes): String = attempt(redirect) { testRunService.reset() }

    @PostMapping("/test/steps/{id}/run")
    fun run(
        @PathVariable("id") id: Int,
        @RequestParam("mode", defaultValue = "IN_APP") mode: RunMode,
        @RequestParam("auto", defaultValue = "false") auto: Boolean,
        @RequestParam("upload", defaultValue = "false") upload: Boolean,
        @RequestParam("records", defaultValue = "false") records: Boolean,
        redirect: RedirectAttributes,
    ): String = attempt(redirect) { testRunService.execute(id, mode, auto, StepOptions(upload, records)) }

    @PostMapping("/test/steps/{id}/skip")
    fun skip(@PathVariable("id") id: Int, redirect: RedirectAttributes): String =
        attempt(redirect) { testRunService.skip(id) }

    @PostMapping("/test/steps/{id}/done")
    fun done(@PathVariable("id") id: Int, redirect: RedirectAttributes): String =
        attempt(redirect) { testRunService.markDone(id) }

    @PostMapping("/test/steps/{id}/retry")
    fun retry(@PathVariable("id") id: Int, redirect: RedirectAttributes): String =
        attempt(redirect) { testRunService.retry(id) }

    private fun attempt(redirect: RedirectAttributes, action: () -> Unit): String {
        try {
            action()
        } catch (e: Exception) {
            redirect.addFlashAttribute("error", e.message ?: e.javaClass.simpleName)
        }
        return "redirect:/test"
    }
}
