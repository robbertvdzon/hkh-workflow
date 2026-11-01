package nl.vdzon.hkhworkflow.ux.api

import java.time.LocalTime
import java.time.ZoneId
import nl.vdzon.hkhworkflow.items.ItemEditService
import nl.vdzon.hkhworkflow.testrun.TestRunService
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.servlet.mvc.support.RedirectAttributes

/** Voor de beheerder: wie is er ingelogd en wie werkt er aan nieuwe bestanden, met de mogelijkheid om een doorloop te stoppen. */
@Controller
class SessionsController(private val testRunService: TestRunService, private val items: ItemEditService) {
    @GetMapping("/instellingen/sessies")
    fun page(model: Model): String {
        val flows = testRunService.active()
        fun time(instant: java.time.Instant) = LocalTime.ofInstant(instant, ZoneId.systemDefault()).toString().take(5)
        model.addAttribute("active", "sessies")
        model.addAttribute("flows", flows)
        model.addAttribute("times", flows.flatMap { listOf("${it.owner}/${it.collection}/since" to time(it.since), "${it.owner}/${it.collection}/active" to time(it.lastActive)) }.toMap())
        model.addAttribute("users", items.users())
        return "sessies"
    }

    @PostMapping("/instellingen/sessies/stoppen")
    fun stop(@RequestParam("gebruiker", defaultValue = "") user: String, @RequestParam("collectie") collection: String, redirect: RedirectAttributes): String {
        val stopped = testRunService.stop(user, collection)
        redirect.addFlashAttribute(if (stopped) "info" else "error", if (stopped) "De doorloop is gestopt." else "Deze doorloop was er al niet meer.")
        return "redirect:/instellingen/sessies"
    }
}
