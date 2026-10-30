package nl.vdzon.hkhworkflow.ux.api

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpSession
import nl.vdzon.hkhworkflow.items.ItemEditService
import nl.vdzon.hkhworkflow.settings.SettingsService
import nl.vdzon.hkhworkflow.ux.AdminAccess
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.ModelAttribute
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.servlet.mvc.support.RedirectAttributes

/** Wat elke pagina in het menu nodig heeft: wie er is ingelogd, en of dat de beheerder is. */
@ControllerAdvice
class ShellAdvice(private val items: ItemEditService, private val admin: AdminAccess, private val settingsService: SettingsService) {
    @ModelAttribute("zcbsUser")
    fun zcbsUser(): String? = items.user

    @ModelAttribute("isAdmin")
    fun isAdmin(session: HttpSession): Boolean = admin.isAdmin(session)

    @ModelAttribute("zcbsRealArchive")
    fun realArchive(): Boolean = items.loggedIn() && !settingsService.get().zcbsIsLocal()

    @ModelAttribute("collectionName")
    fun collectionName(): String = settingsService.get().collection.ifBlank { "archief" }

    @ModelAttribute("zcbsAddress")
    fun zcbsAddress(): String = settingsService.get().zcbsBaseUrl
}

/**
 * Het begin van de app: inloggen bij ZCBS, daarna de collectie kiezen en dan een van twee dingen doen: nieuwe bestanden in ZCBS
 * zetten, of ZCBS records aanpassen. Ook het inloggen van de beheerder staat hier.
 */
@Controller
class ShellController(private val items: ItemEditService, private val admin: AdminAccess, private val settingsService: SettingsService) {

    @GetMapping("/")
    fun start(@RequestParam("inloggen", required = false) mustLogin: String?, model: Model): String {
        if (items.loggedIn()) return "redirect:/collectie"
        model.addAttribute("active", "start")
        model.addAttribute("needsLogin", mustLogin != null)
        model.addAttribute("zcbsConfigured", settingsService.get().zcbsBaseUrl.isNotBlank())
        return "start"
    }

    @GetMapping("/collectie")
    fun collections(model: Model): String {
        model.addAttribute("active", "start")
        model.addAttribute("collections", listOf(settingsService.get().collection.ifBlank { "archief" }))
        return "collectie"
    }

    @GetMapping("/collectie/{name}")
    fun collection(@org.springframework.web.bind.annotation.PathVariable("name") name: String, model: Model): String {
        val known = settingsService.get().collection.ifBlank { "archief" }
        if (name != known) return "redirect:/collectie"
        model.addAttribute("active", "start")
        model.addAttribute("collection", name)
        return "collectie-acties"
    }

    // ---- beheerder ----

    @GetMapping("/beheer")
    fun adminPage(@RequestParam("terug", defaultValue = "/instellingen") back: String, session: HttpSession, model: Model): String {
        model.addAttribute("active", "beheer")
        model.addAttribute("back", safeBack(back))
        model.addAttribute("configured", admin.configured())
        model.addAttribute("loggedIn", admin.isAdmin(session))
        return "beheer"
    }

    @PostMapping("/beheer/inloggen")
    fun adminLogin(
        @RequestParam("wachtwoord", defaultValue = "") password: String,
        @RequestParam("terug", defaultValue = "/instellingen") back: String,
        request: HttpServletRequest,
        redirect: RedirectAttributes,
    ): String {
        if (admin.check(password)) {
            if (request.getSession(false) != null) request.changeSessionId()
            admin.login(request.session)
            return "redirect:" + safeBack(back)
        }
        // Een foute poging kost een seconde: remt het raden van het wachtwoord.
        Thread.sleep(1000)
        redirect.addFlashAttribute("error", if (admin.configured()) "Dat wachtwoord klopt niet." else "Er is geen beheerderswachtwoord ingesteld op de server.")
        redirect.addAttribute("terug", safeBack(back))
        return "redirect:/beheer"
    }

    @PostMapping("/beheer/uitloggen")
    fun adminLogout(session: HttpSession): String {
        admin.logout(session)
        return "redirect:/"
    }

    /** Alleen pagina's van deze app als doel na het inloggen. */
    private fun safeBack(back: String): String = if (back.startsWith("/") && !back.startsWith("//") && !back.contains("://")) back else "/instellingen"
}
