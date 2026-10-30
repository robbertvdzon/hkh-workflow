package nl.vdzon.hkhworkflow.settings.api

import jakarta.servlet.http.HttpServletRequest
import nl.vdzon.hkhworkflow.settings.AnswerRule
import nl.vdzon.hkhworkflow.settings.SettingsService
import nl.vdzon.hkhworkflow.settings.WorkflowSettings
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestParam

/** Eén rij in het antwoordenformulier. Wachtwoorden worden nooit naar de browser gestuurd. */
data class AnswerRow(val index: Int, val pattern: String, val answer: String, val secret: Boolean, val hasSecretValue: Boolean)

@Controller
class SettingsController(private val settingsService: SettingsService) {

    @GetMapping("/instellingen")
    fun show(model: Model, @RequestParam("saved", required = false) saved: Boolean?): String {
        val settings = settingsService.get()
        model.addAttribute("active", "instellingen")
        model.addAttribute("settings", settings)
        model.addAttribute("problems", settingsService.check(settings) + settingsService.checkExtras(settings))
        model.addAttribute("hasFtpPassword", settings.ftpPassword.isNotEmpty())
        model.addAttribute("settingsFile", settingsService.settingsFile().toString())
        model.addAttribute("saved", saved == true)
        model.addAttribute("pdf2zcbsRows", rows(settings.pdf2zcbsAnswers))
        model.addAttribute("imagesRows", rows(settings.imagesAnswers))
        return "settings"
    }

    @PostMapping("/instellingen")
    fun save(request: HttpServletRequest): String {
        val current = settingsService.get()
        fun param(name: String) = request.getParameter(name).orEmpty()
        settingsService.save(
            current.copy(
                testFolder = param("testFolder"),
                imagesExe = param("imagesExe"),
                pdf2zcbsExe = param("pdf2zcbsExe"),
                perlioUnbuffered = request.getParameter("perlioUnbuffered") != null,
                collection = param("collection"),
                backup100pctFolder = param("backup100pctFolder"),
                backupPdfFolder = param("backupPdfFolder"),
                leftoversFolder = param("leftoversFolder"),
                zcbsBaseUrl = param("zcbsBaseUrl"),
                sourceFolders = param("sourceFolders"),
                ftpHost = param("ftpHost"),
                ftpPort = param("ftpPort").trim().toIntOrNull() ?: 21,
                ftpUser = param("ftpUser"),
                // Een leeg wachtwoordveld betekent: het opgeslagen wachtwoord houden (tenzij "wissen" is aangevinkt).
                ftpPassword = when {
                    request.getParameter("ftpPasswordClear") != null -> ""
                    param("ftpPassword").isEmpty() -> current.ftpPassword
                    else -> param("ftpPassword")
                },
                ftpBaseDir = param("ftpBaseDir"),
                ftpOcrDir = param("ftpOcrDir"),
                ftpPdfDir = param("ftpPdfDir"),
                pdf2zcbsAnswers = readRules(request, "pdf2zcbs", current.pdf2zcbsAnswers),
                imagesAnswers = readRules(request, "images", current.imagesAnswers),
            ),
        )
        return "redirect:/instellingen?saved=true"
    }

    /** Bestaande regels plus twee lege rijen om nieuwe toe te voegen. */
    private fun rows(rules: List<AnswerRule>): List<AnswerRow> =
        rules.mapIndexed { i, r -> AnswerRow(i, r.pattern, if (r.secret) "" else r.answer, r.secret, r.secret && r.answer.isNotEmpty()) } +
            (0 until 2).map { AnswerRow(rules.size + it, "", "", false, false) }

    /**
     * Leest de rijen `<prefix>Pattern-<i>`, `<prefix>Answer-<i>`, `<prefix>Secret-<i>` en `<prefix>Clear-<i>`.
     * Een leeg wachtwoordveld betekent: het opgeslagen wachtwoord houden (tenzij "wissen" is aangevinkt).
     */
    private fun readRules(request: HttpServletRequest, prefix: String, existing: List<AnswerRule>): List<AnswerRule> {
        val count = request.getParameter("${prefix}Count")?.toIntOrNull() ?: 0
        return (0 until count).mapNotNull { i ->
            val pattern = request.getParameter("${prefix}Pattern-$i").orEmpty().trim()
            if (pattern.isEmpty()) return@mapNotNull null
            val secret = request.getParameter("${prefix}Secret-$i") != null
            var answer = request.getParameter("${prefix}Answer-$i").orEmpty()
            if (secret && answer.isEmpty() && request.getParameter("${prefix}Clear-$i") == null) {
                answer = existing.firstOrNull { it.secret && it.pattern == pattern }?.answer
                    ?: existing.getOrNull(i)?.takeIf { it.secret }?.answer
                    ?: ""
            }
            AnswerRule(pattern, answer, secret)
        }
    }
}
