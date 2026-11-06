package nl.vdzon.hkhworkflow.update.api

import jakarta.servlet.http.HttpServletRequest
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import nl.vdzon.hkhworkflow.settings.SettingsService
import nl.vdzon.hkhworkflow.update.internal.UpdatePhase
import nl.vdzon.hkhworkflow.update.internal.UpdateService
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.servlet.mvc.support.RedirectAttributes

/** Voor de beheerder: welke build draait, welke staat er klaar op GitHub, en bijwerken (nu, of elke nacht om 00:00). */
@Controller
class UpdateController(private val updates: UpdateService, private val settingsService: SettingsService) {
    private val moment = DateTimeFormatter.ofPattern("d MMM yyyy HH:mm", Locale.of("nl", "NL")).withZone(ZoneId.of("Europe/Amsterdam"))

    @GetMapping("/instellingen/versie")
    fun page(model: Model): String {
        val status = updates.status()
        model.addAttribute("active", "versie")
        model.addAttribute("status", status)
        model.addAttribute("checkedAt", status.checkedAt?.let { moment.format(it) })
        model.addAttribute("publishedAt", status.latest?.publishedAt?.let { moment.format(it) })
        model.addAttribute("downloading", status.phase == UpdatePhase.DOWNLOADING)
        model.addAttribute("restartRequested", status.restarting)
        model.addAttribute("megabytes", mapOf("done" to status.downloaded / 1_048_576, "total" to status.total / 1_048_576))
        return "versie"
    }

    @PostMapping("/instellingen/versie/controleren")
    fun check(): String {
        updates.check() // het resultaat (of de fout) staat daarna in het blok "Op GitHub"
        return "redirect:/instellingen/versie"
    }

    @PostMapping("/instellingen/versie/bijwerken")
    fun update(request: HttpServletRequest, redirect: RedirectAttributes): String {
        val by = request.session.getAttribute("zcbsUser") as? String ?: "de beheerder"
        val problem = updates.updateNow(by)
        if (problem != null) redirect.addFlashAttribute("error", problem)
        return "redirect:/instellingen/versie" // het downloaden loopt op de achtergrond; de pagina toont de voortgang
    }

    @PostMapping("/instellingen/versie/nacht")
    fun nightly(@RequestParam("aan", required = false) on: String?, request: HttpServletRequest, redirect: RedirectAttributes): String {
        val enabled = on != null
        settingsService.save(
            settingsService.get().copy(nightlyUpdate = enabled),
            reason = if (enabled) "Nachtelijke update aangezet" else "Nachtelijke update uitgezet",
            by = request.session.getAttribute("zcbsUser") as? String,
            address = request.remoteAddr,
        )
        redirect.addFlashAttribute("info", if (enabled) "De app werkt zich elke nacht om 00:00 bij als er een nieuwere build is." else "De nachtelijke update staat uit.")
        return "redirect:/instellingen/versie"
    }
}
