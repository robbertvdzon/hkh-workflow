package nl.vdzon.hkhworkflow.settings.internal

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import nl.vdzon.hkhworkflow.settings.CollectionSettings
import nl.vdzon.hkhworkflow.settings.SettingsChanged
import nl.vdzon.hkhworkflow.settings.SettingsService
import nl.vdzon.hkhworkflow.settings.WorkflowProperties
import nl.vdzon.hkhworkflow.settings.WorkflowSettings
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import tools.jackson.databind.ObjectMapper

/**
 * Leest en schrijft de instellingen in een JSON-bestand (standaard naast de jar).
 */
@Service
class DefaultSettingsService(
    private val objectMapper: ObjectMapper,
    private val properties: WorkflowProperties,
    /** Meldt een wijziging van de instellingen aan wie dat wil weten (het workflow-logboek); zonder is er niemand die luistert. */
    private val events: ApplicationEventPublisher? = null,
) : SettingsService {
    private val log = LoggerFactory.getLogger(javaClass)
    private val file: Path = Path.of(properties.settingsFile).toAbsolutePath()

    @Volatile
    private var cached: WorkflowSettings? = null

    override fun settingsFile(): Path = file

    @Synchronized
    override fun get(): WorkflowSettings {
        cached?.let { return it }
        val loaded = withEnvironment(
            if (Files.isRegularFile(file)) {
                try {
                    SettingsFile.read(objectMapper.readTree(Files.readString(file))).toSettings()
                } catch (e: Exception) {
                    log.warn("Kan instellingen niet lezen uit {}: {}", file, e.message)
                    WorkflowSettings()
                }
            } else {
                WorkflowSettings()
            },
        )
        cached = loaded
        return loaded
    }

    /**
     * Vult in wat uit de keuze voor de test-ZCBS volgt: het adres van ZCBS en de ftp-server. De test-ZCBS staat in de
     * eigenschappen van de app (standaard op deze computer), het echte archief staat vast. Gebruiker, basismap en collectie
     * zijn altijd dezelfde.
     */
    override fun withEnvironment(settings: WorkflowSettings): WorkflowSettings = settings.copy(
        zcbsBaseUrl = if (settings.useTestZcbs) properties.testZcbsUrl.trim().trimEnd('/') else WorkflowSettings.PRODUCTION_ZCBS_URL,
        ftpHost = if (settings.useTestZcbs) properties.testFtpHost.trim() else WorkflowSettings.PRODUCTION_FTP_HOST,
        ftpPort = if (settings.useTestZcbs) properties.testFtpPort else 21,
        ftpUser = WorkflowSettings.FTP_USER,
        ftpBaseDir = WorkflowSettings.DEFAULT_FTP_BASE_DIR,
        collection = WorkflowSettings.COLLECTION,
    )

    @Synchronized
    override fun save(settings: WorkflowSettings, reason: String, by: String?, address: String?): WorkflowSettings {
        val before = get()
        val cleaned = settings.copy(
            testFolder = settings.testFolder.trim(),
            imagesExe = settings.imagesExe.trim(),
            pdf2zcbsExe = settings.pdf2zcbsExe.trim(),
            collections = settings.collections.mapValues { (_, c) ->
                c.copy(
                    imagesResolution = c.imagesResolution.trim().takeIf { it in CollectionSettings.RESOLUTIONS } ?: CollectionSettings.DEFAULT_RESOLUTION,
                    backup100pctFolder = c.backup100pctFolder.trim(),
                    backupPdfFolder = c.backupPdfFolder.trim(),
                )
            },
            leftoversFolder = settings.leftoversFolder.trim(),
            sourceFolders = settings.sourceFolders.lines().map { it.trim() }.filter { it.isNotEmpty() }.joinToString("\n"),
        )
        file.parent?.let { Files.createDirectories(it) }
        val tmp = file.resolveSibling(file.fileName.toString() + ".tmp")
        Files.writeString(tmp, objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(SettingsFile.of(cleaned)))
        Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING)
        cached = cleaned
        // Een melding die mislukt mag het opslaan niet tegenhouden.
        if (cleaned != before) runCatching { events?.publishEvent(SettingsChanged(before, cleaned, reason, by, address)) }
            .onFailure { log.warn("Melden van de gewijzigde instellingen mislukt: {}", it.message) }
        return cleaned
    }

    /** Waarschuwingen per veld (bestaat de map/het bestand?). Blokkeert het opslaan niet. */
    override fun check(settings: WorkflowSettings): Map<String, String> {
        val problems = linkedMapOf<String, String>()
        fun dir(field: String, value: String) {
            if (value.isBlank()) problems[field] = "Nog niet ingevuld."
            else if (!Files.isDirectory(Path.of(value))) problems[field] = "Map bestaat niet."
        }
        fun exe(field: String, value: String) {
            if (value.isBlank()) problems[field] = "Nog niet ingevuld."
            else if (!Files.isRegularFile(Path.of(value))) problems[field] = "Bestand bestaat niet."
            else if (!Files.isExecutable(Path.of(value))) problems[field] = "Bestand is niet uitvoerbaar (op Mac/Linux: chmod +x)."
        }
        dir("testFolder", settings.testFolder)
        exe("imagesExe", settings.imagesExe)
        exe("pdf2zcbsExe", settings.pdf2zcbsExe)
        return problems
    }

    /** Waarschuwingen over de instellingen die alleen de latere stappen (upload, back-up) nodig hebben. */
    override fun checkExtras(settings: WorkflowSettings): Map<String, String> {
        val problems = linkedMapOf<String, String>()
        // Alleen de collecties waar de app mee werkt hebben de back-upmappen nodig; de sleutel is "<collectie>.<veld>".
        for (c in WorkflowSettings.IMPLEMENTED_COLLECTIONS) {
            val own = settings.collectionSettings(c)
            if (own.backup100pctFolder.isBlank()) problems["$c.backup100pctFolder"] = "Nog niet ingevuld."
            else if (!Files.isDirectory(Path.of(own.backup100pctFolder))) problems["$c.backup100pctFolder"] = "Map bestaat niet."
            if (own.backupPdfFolder.isBlank()) problems["$c.backupPdfFolder"] = "Nog niet ingevuld."
            else if (!Files.isDirectory(Path.of(own.backupPdfFolder))) problems["$c.backupPdfFolder"] = "Map bestaat niet."
        }
        if (settings.sourceRoots().isEmpty()) problems["sourceFolders"] = "Nog niet ingevuld: zonder deze mappen kan niemand scans kiezen."
        if (settings.ftpPassword.isEmpty()) problems["ftpPassword"] = "Nog niet ingevuld. Vul het hier in, of typ het één keer in bij Handmatig uitvoeren van images.exe: dan neemt de app het over."
        return problems
    }
}
