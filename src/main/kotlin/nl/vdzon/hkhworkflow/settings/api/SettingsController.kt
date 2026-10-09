package nl.vdzon.hkhworkflow.settings.api

import nl.vdzon.hkhworkflow.settings.SettingsService
import nl.vdzon.hkhworkflow.settings.WorkflowSettings
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestParam

@Controller
class SettingsController(private val settingsService: SettingsService) {

    @GetMapping("/instellingen")
    fun show(model: Model, @RequestParam("saved", required = false) saved: Boolean?): String {
        val settings = settingsService.get()
        model.addAttribute("active", "instellingen")
        model.addAttribute("settings", settings)
        model.addAttribute("problems", settingsService.check(settings))
        model.addAttribute("settingsFile", settingsService.settingsFile().toString())
        model.addAttribute("saved", saved == true)
        return "settings"
    }

    @PostMapping("/instellingen")
    fun save(
        @RequestParam("testFolder") testFolder: String,
        @RequestParam("archivedFolder") archivedFolder: String,
        @RequestParam("imagesExe") imagesExe: String,
        @RequestParam("pdf2zcbsExe") pdf2zcbsExe: String,
    ): String {
        settingsService.save(WorkflowSettings(testFolder, archivedFolder, imagesExe, pdf2zcbsExe))
        return "redirect:/instellingen?saved=true"
    }
}
