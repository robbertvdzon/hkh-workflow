package nl.vdzon.hkhworkflow.ftp.api

import nl.vdzon.hkhworkflow.ftp.FtpService
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RestController

data class FtpTestResponse(val ok: Boolean, val message: String, val folders: List<FtpFolderResponse>)
data class FtpFolderResponse(val path: String, val exists: Boolean, val message: String?)

@RestController
class FtpApiController(private val ftpService: FtpService) {
    /** Test de opgeslagen instellingen. Een server die niet antwoordt is geen fout van de API, maar de uitkomst van de test. */
    @PostMapping("/api/settings/ftp-test")
    fun test(): FtpTestResponse {
        val result = ftpService.testConnection()
        return FtpTestResponse(result.ok, result.message, result.dirs.map { FtpFolderResponse(it.path, it.exists, it.message) })
    }
}
