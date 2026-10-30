package nl.vdzon.hkhworkflow.settings

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import tools.jackson.databind.ObjectMapper

/**
 * Leest en schrijft de instellingen in een JSON-bestand (standaard naast de jar).
 */
@Service
class SettingsService(
    private val objectMapper: ObjectMapper,
    properties: WorkflowProperties,
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
                fun rules(name: String, defaults: List<AnswerRule>): List<AnswerRule> {
                    val array = node.path(name)
                    if (!array.isArray) return defaults
                    // Expliciete lus: JsonNode in Jackson 3 heeft een eigen map(), dus geen Kotlin-map gebruiken.
                    val result = mutableListOf<AnswerRule>()
                    for (i in 0 until array.size()) {
                        val item = array.get(i)
                        result.add(AnswerRule(item.path("pattern").asText(""), item.path("answer").asText(""), item.path("secret").asBoolean(false)))
                    }
                    return result
                }
                WorkflowSettings(
                    testFolder = node.path("testFolder").asText(""),
                    imagesExe = node.path("imagesExe").asText(""),
                    pdf2zcbsExe = node.path("pdf2zcbsExe").asText(""),
                    perlioUnbuffered = node.path("perlioUnbuffered").asBoolean(true),
                    pdf2zcbsAnswers = rules("pdf2zcbsAnswers", WorkflowSettings.DEFAULT_PDF2ZCBS_ANSWERS),
                    imagesAnswers = rules("imagesAnswers", WorkflowSettings.DEFAULT_IMAGES_ANSWERS),
                    collection = node.path("collection").asText("archief"),
                    backup100pctFolder = node.path("backup100pctFolder").asText(""),
                    backupPdfFolder = node.path("backupPdfFolder").asText(""),
                    zcbsBaseUrl = node.path("zcbsBaseUrl").asText(""),
                    sourceFolders = node.path("sourceFolders").asText(""),
                    sftpHost = node.path("sftpHost").asText(""),
                    sftpPort = node.path("sftpPort").asInt(22),
                    sftpUser = node.path("sftpUser").asText(""),
                    sftpPassword = node.path("sftpPassword").asText(""),
                    sftpHostKey = node.path("sftpHostKey").asText(""),
                    sftpBaseDir = node.path("sftpBaseDir").asText(WorkflowSettings.DEFAULT_SFTP_BASE_DIR),
                    sftpOcrDir = node.path("sftpOcrDir").asText(""),
                    sftpPdfDir = node.path("sftpPdfDir").asText(""),
                )
            } catch (e: Exception) {
                log.warn("Kan instellingen niet lezen uit {}: {}", file, e.message)
                WorkflowSettings()
            }
        } else {
            WorkflowSettings()
        }
        cached = loaded
        return loaded
    }

    @Synchronized
    fun save(settings: WorkflowSettings): WorkflowSettings {
        val cleaned = settings.copy(
            testFolder = settings.testFolder.trim(),
            imagesExe = settings.imagesExe.trim(),
            pdf2zcbsExe = settings.pdf2zcbsExe.trim(),
            collection = settings.collection.trim(),
            backup100pctFolder = settings.backup100pctFolder.trim(),
            backupPdfFolder = settings.backupPdfFolder.trim(),
            zcbsBaseUrl = settings.zcbsBaseUrl.trim().trimEnd('/'),
            sourceFolders = settings.sourceFolders.lines().map { it.trim() }.filter { it.isNotEmpty() }.joinToString("\n"),
            sftpHost = settings.sftpHost.trim(),
            sftpUser = settings.sftpUser.trim(),
            sftpHostKey = settings.sftpHostKey.trim(),
            sftpBaseDir = settings.sftpBaseDir.trim(),
            sftpOcrDir = settings.sftpOcrDir.trim(),
            sftpPdfDir = settings.sftpPdfDir.trim(),
            pdf2zcbsAnswers = settings.pdf2zcbsAnswers.filter { it.pattern.isNotBlank() },
            imagesAnswers = settings.imagesAnswers.filter { it.pattern.isNotBlank() },
        )
        file.parent?.let { Files.createDirectories(it) }
        val tmp = file.resolveSibling(file.fileName.toString() + ".tmp")
        Files.writeString(tmp, objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(cleaned))
        Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING)
        cached = cleaned
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
        if (settings.collection.isBlank()) problems["collection"] = "Nog niet ingevuld."
        if (settings.backup100pctFolder.isBlank()) problems["backup100pctFolder"] = "Nog niet ingevuld."
        else if (!Files.isDirectory(Path.of(settings.backup100pctFolder))) problems["backup100pctFolder"] = "Map bestaat niet."
        if (settings.backupPdfFolder.isBlank()) problems["backupPdfFolder"] = "Nog niet ingevuld."
        else if (!Files.isDirectory(Path.of(settings.backupPdfFolder))) problems["backupPdfFolder"] = "Map bestaat niet."
        if (settings.sourceRoots().isEmpty()) problems["sourceFolders"] = "Nog niet ingevuld: zonder deze mappen kan Carla geen scans kiezen."
        if (settings.zcbsBaseUrl.isBlank()) problems["zcbsBaseUrl"] = "Nog niet ingevuld."
        if (settings.sftpHost.isBlank()) problems["sftpHost"] = "Nog niet ingevuld."
        if (settings.sftpUser.isBlank()) problems["sftpUser"] = "Nog niet ingevuld."
        if (settings.sftpPassword.isEmpty()) problems["sftpPassword"] = "Nog niet ingevuld."
        if (settings.sftpHostKey.isBlank()) problems["sftpHostKey"] = "Nog niet vastgezet: gebruik Test verbinding."
        return problems
    }
}
