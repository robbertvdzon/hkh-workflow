package nl.vdzon.hkhworkflow.items

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import nl.vdzon.hkhworkflow.settings.SettingsService
import nl.vdzon.hkhworkflow.settings.WorkflowProperties
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

/** Een record dat iemand met de app heeft opgeslagen. */
data class EditEvent(val at: LocalDateTime, val user: String, val collection: String, val number: String)

/**
 * Wie welk record wanneer heeft opgeslagen met Records aanpassen, in een tekstbestand naast het instellingenbestand (een regel per keer,
 * gescheiden door tabs). Daarmee laat het scherm zien wat er recent is aangepast, ook door een andere gebruiker en na een herstart van de app.
 */
@Service
class EditHistory(private val settingsService: SettingsService, private val properties: WorkflowProperties) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val stamp = DateTimeFormatter.ISO_LOCAL_DATE_TIME

    fun file(): Path {
        val configured = Path.of(properties.editLog)
        return if (configured.isAbsolute) configured else (settingsService.settingsFile().parent ?: Path.of(".")).resolve(configured)
    }

    private fun clean(s: String) = s.replace('\t', ' ').replace('\n', ' ').replace('\r', ' ')

    @Synchronized
    fun add(event: EditEvent) {
        runCatching {
            val path = file()
            path.parent?.let { Files.createDirectories(it) }
            val line = listOf(event.at.format(stamp), event.user, event.collection, event.number).joinToString("\t") { clean(it) } + "\n"
            Files.writeString(path, line, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND)
        }.onFailure { log.warn("Bewaren van het bewerkt-log mislukt: {}", it.message) }
    }

    @Synchronized
    fun all(): List<EditEvent> {
        val path = file()
        if (!Files.isRegularFile(path)) return emptyList()
        return runCatching {
            Files.readAllLines(path, StandardCharsets.UTF_8).mapNotNull { line ->
                val p = line.split('\t')
                if (p.size < 4) null else runCatching { EditEvent(LocalDateTime.parse(p[0], stamp), p[1], p[2], p[3]) }.getOrNull()
            }
        }.getOrElse { log.warn("Kan het bewerkt-log {} niet lezen: {}", path, it.message); emptyList() }
    }
}
