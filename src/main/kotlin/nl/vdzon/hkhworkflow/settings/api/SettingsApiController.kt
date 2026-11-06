package nl.vdzon.hkhworkflow.settings.api

import jakarta.servlet.http.HttpServletRequest
import nl.vdzon.hkhworkflow.settings.CollectionSettings
import nl.vdzon.hkhworkflow.settings.SettingsService
import nl.vdzon.hkhworkflow.settings.WorkflowProperties
import nl.vdzon.hkhworkflow.settings.WorkflowSettings
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/** De instellingen zoals de beheerder ze ziet. Het ftp-wachtwoord zit er nooit in, alleen of het is ingesteld. */
data class SettingsResponse(
    val workFolder: String,
    val imagesExe: String,
    val pdf2zcbsExe: String,
    val perlioUnbuffered: Boolean,
    val sourceFolders: String,
    val useTestZcbs: Boolean,
    val testZcbsUrl: String,
    val leftoversFolder: String,
    val logFtpPassword: Boolean,
    val ftpUser: String,
    val ftpPasswordSet: Boolean,
    val collections: List<CollectionSettingsResponse>,
    val resolutions: List<ResolutionResponse>,
    /** Waarschuwingen per veld. */
    val problems: Map<String, String>,
    val settingsFile: String,
)

data class CollectionSettingsResponse(val name: String, val implemented: Boolean, val imagesResolution: String, val backup100pctFolder: String, val backupPdfFolder: String)
data class ResolutionResponse(val value: String, val label: String)

data class SettingsRequest(
    val workFolder: String,
    val imagesExe: String,
    val pdf2zcbsExe: String,
    val perlioUnbuffered: Boolean,
    val sourceFolders: String,
    val useTestZcbs: Boolean,
    val leftoversFolder: String,
    val logFtpPassword: Boolean,
    /** Weggelaten is het opgeslagen wachtwoord houden, leeg is wissen. */
    val ftpPassword: String? = null,
    val collections: List<CollectionSettingsRequest>,
)

data class CollectionSettingsRequest(val name: String, val imagesResolution: String, val backup100pctFolder: String, val backupPdfFolder: String)

@RestController
@RequestMapping("/api/settings")
class SettingsApiController(private val settingsService: SettingsService, private val properties: WorkflowProperties) {

    @GetMapping
    fun get(): SettingsResponse {
        val s = settingsService.get()
        return SettingsResponse(
            workFolder = s.testFolder,
            imagesExe = s.imagesExe,
            pdf2zcbsExe = s.pdf2zcbsExe,
            perlioUnbuffered = s.perlioUnbuffered,
            sourceFolders = s.sourceFolders,
            useTestZcbs = s.useTestZcbs,
            testZcbsUrl = properties.testZcbsUrl,
            leftoversFolder = s.leftoversFolder,
            logFtpPassword = s.logFtpPassword,
            ftpUser = s.ftpUser,
            ftpPasswordSet = s.ftpPassword.isNotEmpty(),
            collections = WorkflowSettings.ALL_COLLECTIONS.map {
                val c = s.collectionSettings(it)
                CollectionSettingsResponse(it, it in WorkflowSettings.IMPLEMENTED_COLLECTIONS, c.imagesResolution, c.backup100pctFolder, c.backupPdfFolder)
            },
            resolutions = RESOLUTIONS,
            problems = settingsService.check(s) + settingsService.checkExtras(s),
            settingsFile = settingsService.settingsFile().toString(),
        )
    }

    @PutMapping
    fun save(@RequestBody body: SettingsRequest, request: HttpServletRequest): SettingsResponse {
        val current = settingsService.get()
        val given = body.collections.associateBy { it.name }
        settingsService.save(
            by = request.session.getAttribute("zcbsUser") as? String, // de sleutel van de ZCBS-gebruiker in de sessie (de module settings kent de module ux niet)
            address = request.remoteAddr,
            settings = settingsService.withEnvironment(current.copy(
                testFolder = body.workFolder,
                imagesExe = body.imagesExe,
                pdf2zcbsExe = body.pdf2zcbsExe,
                perlioUnbuffered = body.perlioUnbuffered,
                sourceFolders = body.sourceFolders,
                useTestZcbs = body.useTestZcbs,
                leftoversFolder = body.leftoversFolder,
                logFtpPassword = body.logFtpPassword,
                ftpPassword = body.ftpPassword ?: current.ftpPassword,
                collections = WorkflowSettings.ALL_COLLECTIONS.associateWith { name ->
                    given[name]?.let { CollectionSettings(it.imagesResolution.trim().ifEmpty { CollectionSettings.DEFAULT_RESOLUTION }, it.backup100pctFolder, it.backupPdfFolder) }
                        ?: current.collectionSettings(name)
                },
            )),
        )
        return get()
    }

    private companion object {
        val RESOLUTIONS = listOf(
            ResolutionResponse("1", "1: small 100×100, 100pct ongewijzigd"),
            ResolutionResponse("2", "2: small 100×100, 100pct maximaal 3000×2000"),
            ResolutionResponse("3", "3: small 100×100, 100pct maximaal 4000×3000"),
            ResolutionResponse("6", "6: small 250×250, 100pct ongewijzigd"),
            ResolutionResponse("7", "7: small 250×250, 100pct maximaal 3000×2000"),
            ResolutionResponse("8", "8: small 250×250, 100pct maximaal 4000×3000"),
        )
    }
}
