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
                    return array.map {
                        AnswerRule(it.path("pattern").asText(""), it.path("answer").asText(""), it.path("secret").asBoolean(false))
                    }
                }
                WorkflowSettings(
                    testFolder = node.path("testFolder").asText(""),
                    archivedFolder = node.path("archivedFolder").asText(""),
                    imagesExe = node.path("imagesExe").asText(""),
                    pdf2zcbsExe = node.path("pdf2zcbsExe").asText(""),
                    perlioUnbuffered = node.path("perlioUnbuffered").asBoolean(true),
                    pdf2zcbsAnswers = rules("pdf2zcbsAnswers", WorkflowSettings.DEFAULT_PDF2ZCBS_ANSWERS),
                    imagesAnswers = rules("imagesAnswers", WorkflowSettings.DEFAULT_IMAGES_ANSWERS),
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
            archivedFolder = settings.archivedFolder.trim(),
            imagesExe = settings.imagesExe.trim(),
            pdf2zcbsExe = settings.pdf2zcbsExe.trim(),
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
        dir("archivedFolder", settings.archivedFolder)
        exe("imagesExe", settings.imagesExe)
        exe("pdf2zcbsExe", settings.pdf2zcbsExe)
        return problems
    }
}
