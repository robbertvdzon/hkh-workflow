package nl.vdzon.hkhworkflow.sftp.api

import nl.vdzon.hkhworkflow.settings.SettingsService
import nl.vdzon.hkhworkflow.sftp.SftpService
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.servlet.mvc.support.RedirectAttributes

@Controller
class SftpController(
    private val sftpService: SftpService,
    private val settingsService: SettingsService,
) {
    /** Test de opgeslagen instellingen; het resultaat verschijnt onder de instellingen. */
    @PostMapping("/instellingen/sftp-test")
    fun test(redirect: RedirectAttributes): String {
        redirect.addFlashAttribute("sftpTest", sftpService.testConnection())
        return "redirect:/instellingen"
    }

    /** Zet de getoonde vingerafdruk vast. De gebruiker heeft hem zelf vergeleken. */
    @PostMapping("/instellingen/sftp-vingerafdruk")
    fun pin(@RequestParam("fingerprint") fingerprint: String): String {
        require(Regex("SHA256:[A-Za-z0-9+/]{43}").matches(fingerprint)) { "Ongeldige vingerafdruk." }
        settingsService.save(settingsService.get().copy(sftpHostKey = fingerprint))
        return "redirect:/instellingen?saved=true"
    }
}
