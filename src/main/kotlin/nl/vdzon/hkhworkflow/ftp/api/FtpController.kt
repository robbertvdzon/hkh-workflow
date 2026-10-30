package nl.vdzon.hkhworkflow.ftp.api

import nl.vdzon.hkhworkflow.ftp.FtpService
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.servlet.mvc.support.RedirectAttributes

@Controller
class FtpController(private val ftpService: FtpService) {
    /** Test de opgeslagen instellingen; het resultaat verschijnt onder de instellingen. */
    @PostMapping("/instellingen/ftp-test")
    fun test(redirect: RedirectAttributes): String {
        redirect.addFlashAttribute("ftpTest", ftpService.testConnection())
        return "redirect:/instellingen"
    }
}
