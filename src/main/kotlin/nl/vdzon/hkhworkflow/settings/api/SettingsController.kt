package nl.vdzon.hkhworkflow.settings.api

import jakarta.servlet.http.HttpServletRequest
import nl.vdzon.hkhworkflow.settings.CollectionSettings
import nl.vdzon.hkhworkflow.settings.SettingsService
import nl.vdzon.hkhworkflow.settings.WorkflowProperties
import nl.vdzon.hkhworkflow.settings.WorkflowSettings
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestParam

/** De instellingen van één collectie in het formulier. [implemented] = de app werkt er al mee. */
data class CollectionRow(val name: String, val implemented: Boolean, val settings: CollectionSettings)

@Controller
class SettingsController(private val settingsService: SettingsService, private val properties: WorkflowProperties) {

    @GetMapping("/instellingen")
    fun show(model: Model, @RequestParam("saved", required = false) saved: Boolean?): String {
        val settings = settingsService.get()
        model.addAttribute("active", "instellingen")
        model.addAttribute("settings", settings)
        model.addAttribute("testZcbsUrl", properties.testZcbsUrl)
        model.addAttribute("problems", settingsService.check(settings) + settingsService.checkExtras(settings))
        model.addAttribute("settingsFile", settingsService.settingsFile().toString())
        model.addAttribute("saved", saved == true)
        model.addAttribute("hasFtpPassword", settings.ftpPassword.isNotEmpty())
        model.addAttribute("resolutions", RESOLUTION_LABELS)
        model.addAttribute(
            "collectionRows",
            WorkflowSettings.ALL_COLLECTIONS.map { CollectionRow(it, it in WorkflowSettings.IMPLEMENTED_COLLECTIONS, settings.collectionSettings(it)) },
        )
        return "settings"
    }

    @PostMapping("/instellingen")
    fun save(request: HttpServletRequest): String {
        val current = settingsService.get()
        fun param(name: String) = request.getParameter(name).orEmpty()
        settingsService.save(
            by = request.session.getAttribute("zcbsUser") as? String, // de sleutel van de ZCBS-gebruiker in de sessie (de module settings kent de module ux niet)
            address = request.remoteAddr,
            settings = settingsService.withEnvironment(current.copy(
                testFolder = param("testFolder"),
                imagesExe = param("imagesExe"),
                pdf2zcbsExe = param("pdf2zcbsExe"),
                perlioUnbuffered = request.getParameter("perlioUnbuffered") != null,
                collections = WorkflowSettings.ALL_COLLECTIONS.associateWith { c ->
                    CollectionSettings(
                        imagesResolution = param("resolution-$c").trim().ifEmpty { CollectionSettings.DEFAULT_RESOLUTION },
                        backup100pctFolder = param("backup100pctFolder-$c"),
                        backupPdfFolder = param("backupPdfFolder-$c"),
                    )
                },
                leftoversFolder = param("leftoversFolder"),
                logSource = param("logSource"),
                logFtpPassword = request.getParameter("logFtpPassword") != null,
                useTestZcbs = request.getParameter("useTestZcbs") != null,
                sourceFolders = param("sourceFolders"),
                // Een leeg wachtwoordveld betekent: het opgeslagen wachtwoord houden (tenzij "wissen" is aangevinkt).
                ftpPassword = when {
                    request.getParameter("ftpPasswordClear") != null -> ""
                    param("ftpPassword").isEmpty() -> current.ftpPassword
                    else -> param("ftpPassword")
                },
            )),
        )
        return "redirect:/instellingen?saved=true"
    }

    private companion object {
        val RESOLUTION_LABELS = listOf(
            "1" to "1: small 100×100, 100pct ongewijzigd",
            "2" to "2: small 100×100, 100pct maximaal 3000×2000",
            "3" to "3: small 100×100, 100pct maximaal 4000×3000",
            "6" to "6: small 250×250, 100pct ongewijzigd",
            "7" to "7: small 250×250, 100pct maximaal 3000×2000",
            "8" to "8: small 250×250, 100pct maximaal 4000×3000",
        )
    }
}
