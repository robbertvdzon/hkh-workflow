package nl.vdzon.hkhworkflow.settings

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationEventPublisher
import org.springframework.stereotype.Service
import tools.jackson.databind.ObjectMapper

/**
 * Leest en schrijft de instellingen in een JSON-bestand (standaard naast de jar).
 */
@Service
class SettingsService(
    private val objectMapper: ObjectMapper,
    private val properties: WorkflowProperties,
    /** Meldt een wijziging van de instellingen aan wie dat wil weten (het workflow-logboek); zonder is er niemand die luistert. */
    private val events: ApplicationEventPublisher? = null,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val file: Path = Path.of(properties.settingsFile).toAbsolutePath()

    @Volatile
    private var cached: WorkflowSettings? = null

    fun settingsFile(): Path = file

    @Synchronized
    fun get(): WorkflowSettings {
        cached?.let { return it }
        val loaded = if (Files.isRegularFile(file)) {
            try {
                val node = objectMapper.readTree(Files.readString(file))
                fun text(name: String, oldName: String, default: String): String =
                    node.path(name).takeIf { it.isString }?.asText() ?: node.path(oldName).takeIf { it.isString }?.asText() ?: default
                val oldBaseUrl = node.path("zcbsBaseUrl").asText("")
                // De antwoorden voor images.exe stonden vroeger in dit bestand (een lijst {pattern, answer, secret}); nu zijn ze vast, op de
                // resolutie en het ftp-wachtwoord na. Die twee nemen we over uit zo'n oud bestand.
                fun oldAnswer(pattern: String): String? {
                    val array = node.path("imagesAnswers")
                    if (!array.isArray) return null
                    for (i in 0 until array.size()) {
                        val item = array.get(i)
                        if (item.path("pattern").asText("").equals(pattern, ignoreCase = true)) return item.path("answer").asText("")
                    }
                    return null
                }
                val oldResolution = oldAnswer("Kies 1,2,3")?.takeIf { it in CollectionSettings.RESOLUTIONS }
                val loadedCollections = linkedMapOf<String, CollectionSettings>()
                val collectionsNode = node.path("collections")
                if (collectionsNode.isObject) {
                    for (name in collectionsNode.propertyNames()) {
                        val c = collectionsNode.path(name)
                        loadedCollections[name] = CollectionSettings(
                            imagesResolution = c.path("imagesResolution").asText("").takeIf { it in CollectionSettings.RESOLUTIONS } ?: CollectionSettings.DEFAULT_RESOLUTION,
                            backup100pctFolder = c.path("backup100pctFolder").asText(""),
                            backupPdfFolder = c.path("backupPdfFolder").asText(""),
                        )
                    }
                } else {
                    // Een bestand van voor de instellingen per collectie: alles hoorde bij het archief.
                    loadedCollections[WorkflowSettings.COLLECTION] = CollectionSettings(
                        imagesResolution = oldResolution ?: CollectionSettings.DEFAULT_RESOLUTION,
                        backup100pctFolder = node.path("backup100pctFolder").asText(""),
                        backupPdfFolder = node.path("backupPdfFolder").asText(""),
                    )
                }
                withEnvironment(
                    WorkflowSettings(
                        testFolder = node.path("testFolder").asText(""),
                        imagesExe = node.path("imagesExe").asText(""),
                        pdf2zcbsExe = node.path("pdf2zcbsExe").asText(""),
                        perlioUnbuffered = node.path("perlioUnbuffered").asBoolean(true),
                        collections = loadedCollections,
                        leftoversFolder = node.path("leftoversFolder").asText(""),
                    logSource = node.path("logSource").asText(""),
                    logFtpPassword = node.path("logFtpPassword").asBoolean(true),
                        // Een bestand van voor deze keuze: alles wat niet het echte archief is, geldt als test. Zo wijst een oude instelling nooit ineens naar het echte archief.
                        useTestZcbs = if (node.path("useTestZcbs").isBoolean) node.path("useTestZcbs").asBoolean(true) else !oldBaseUrl.contains("historischekringheemskerk.nl"),
                        sourceFolders = node.path("sourceFolders").asText(""),
                        // Het wachtwoord: de eigen instelling, anders het oude antwoord op "FTP wachtwoord" (ook uit de tijd van sftp).
                        ftpPassword = text("ftpPassword", "sftpPassword", "").ifEmpty { oldAnswer("FTP wachtwoord").orEmpty() },
                    ),
                )
            } catch (e: Exception) {
                log.warn("Kan instellingen niet lezen uit {}: {}", file, e.message)
                withEnvironment(WorkflowSettings())
            }
        } else {
            withEnvironment(WorkflowSettings())
        }
        cached = loaded
        return loaded
    }

    /**
     * Vult in wat uit de keuze voor de test-ZCBS volgt: het adres van ZCBS en de ftp-server. De test-ZCBS staat in de
     * eigenschappen van de app (standaard op deze computer), het echte archief staat vast. Gebruiker, basismap en collectie
     * zijn altijd dezelfde.
     */
    fun withEnvironment(settings: WorkflowSettings): WorkflowSettings = settings.copy(
        zcbsBaseUrl = if (settings.useTestZcbs) properties.testZcbsUrl.trim().trimEnd('/') else WorkflowSettings.PRODUCTION_ZCBS_URL,
        ftpHost = if (settings.useTestZcbs) properties.testFtpHost.trim() else WorkflowSettings.PRODUCTION_FTP_HOST,
        ftpPort = if (settings.useTestZcbs) properties.testFtpPort else 21,
        ftpUser = WorkflowSettings.FTP_USER,
        ftpBaseDir = WorkflowSettings.DEFAULT_FTP_BASE_DIR,
        collection = WorkflowSettings.COLLECTION,
    )

    @Synchronized
    fun save(settings: WorkflowSettings, reason: String = "Instellingen opgeslagen", by: String? = null, address: String? = null): WorkflowSettings {
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
            logSource = settings.logSource.trim(),
            sourceFolders = settings.sourceFolders.lines().map { it.trim() }.filter { it.isNotEmpty() }.joinToString("\n"),
        )
        file.parent?.let { Files.createDirectories(it) }
        val tmp = file.resolveSibling(file.fileName.toString() + ".tmp")
        Files.writeString(tmp, objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(cleaned))
        Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING)
        cached = cleaned
        // Een melding die mislukt mag het opslaan niet tegenhouden.
        if (cleaned != before) runCatching { events?.publishEvent(SettingsChanged(before, cleaned, reason, by, address)) }
            .onFailure { log.warn("Melden van de gewijzigde instellingen mislukt: {}", it.message) }
        return cleaned
    }

    /** Waarschuwingen per veld (bestaat de map/het bestand?). Blokkeert het opslaan niet. */
    fun check(settings: WorkflowSettings): Map<String, String> {
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
    fun checkExtras(settings: WorkflowSettings): Map<String, String> {
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
