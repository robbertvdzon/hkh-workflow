package nl.vdzon.hkhworkflow.ux.api

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpSession
import nl.vdzon.hkhworkflow.items.Current
import nl.vdzon.hkhworkflow.items.ItemEditService
import nl.vdzon.hkhworkflow.settings.SettingsService
import nl.vdzon.hkhworkflow.settings.WorkflowSettings
import nl.vdzon.hkhworkflow.testrun.TestRunService
import nl.vdzon.hkhworkflow.ux.AdminAccess
import nl.vdzon.hkhworkflow.ux.CurrentContextFilter
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

    @ModelAttribute("zcbsRights")
    fun rights(): List<String> = items.rights()

    @ModelAttribute("collectionName")
    fun collectionName(): String = Current.collection()

    @ModelAttribute("zcbsAddress")
    fun zcbsAddress(): String = settingsService.get().zcbsBaseUrl
}

/** Een collectie op de keuzepagina. Alleen de collecties waar de app mee werkt, zijn te kiezen. */
data class CollectionChoice(val name: String, val enabled: Boolean, val hint: String)

/**
 * Het begin van de app: inloggen bij ZCBS, daarna de collectie kiezen en dan een van twee dingen doen: nieuwe bestanden in ZCBS
 * zetten, of ZCBS records aanpassen. Ook het inloggen van de beheerder staat hier.
 */
@Controller
class ShellController(
    private val items: ItemEditService,
    private val admin: AdminAccess,
    private val settingsService: SettingsService,
    private val testRunService: TestRunService,
) {

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
        val rights = items.rights()
        model.addAttribute(
            "collections",
            WorkflowSettings.ALL_COLLECTIONS.map {
                when {
                    it !in rights -> CollectionChoice(it, false, "Geen rechten")
                    it !in WorkflowSettings.IMPLEMENTED_COLLECTIONS -> CollectionChoice(it, false, "Nog niet beschikbaar")
                    else -> CollectionChoice(it, true, "Collectie in ZCBS")
                }
            },
        )
        return "collectie"
    }

    @GetMapping("/collectie/{name}")
    fun collection(@org.springframework.web.bind.annotation.PathVariable("name") name: String, session: HttpSession, model: Model): String {
        if (!items.canUse(name)) return "redirect:/collectie"
        // Vanaf hier werkt deze browsersessie in deze collectie.
        session.setAttribute(CurrentContextFilter.COLLECTION, name)
        model.addAttribute("lock", testRunService.lockedBy(name))
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
