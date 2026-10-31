package nl.vdzon.hkhworkflow.ux.api

import java.time.LocalTime
import java.time.ZoneId
import nl.vdzon.hkhworkflow.items.ItemEditService
import nl.vdzon.hkhworkflow.testrun.TestRunService
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.servlet.mvc.support.RedirectAttributes

/** Voor de beheerder: wie is er ingelogd en welke collecties zijn in gebruik, met de mogelijkheid om een collectie vrij te geven. */
@Controller
class SessionsController(private val testRunService: TestRunService, private val items: ItemEditService) {
    @GetMapping("/instellingen/sessies")
    fun page(model: Model): String {
        val locks = testRunService.locks()
        fun time(instant: java.time.Instant) = LocalTime.ofInstant(instant, ZoneId.systemDefault()).toString().take(5)
        model.addAttribute("active", "sessies")
        model.addAttribute("locks", locks)
        model.addAttribute("times", locks.flatMap { listOf("${it.collection}/since" to time(it.since), "${it.collection}/active" to time(it.lastActive)) }.toMap())
        model.addAttribute("users", items.users())
        return "sessies"
    }

    @PostMapping("/instellingen/sessies/{collection}/vrijgeven")
    fun release(@PathVariable("collection") collection: String, redirect: RedirectAttributes): String {
        val released = testRunService.release(collection)
        redirect.addFlashAttribute(if (released) "info" else "error", if (released) "De collectie is vrijgegeven." else "Deze collectie is al vrij.")
        return "redirect:/instellingen/sessies"
    }
}
