package nl.vdzon.hkhworkflow.testrun

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

/** Eén bestand dat op het archief is gezet: uit welke map het kwam, onder welk recordnummer, wanneer en door wie. */
data class ProcessedFile(
    /** Hoort bij één keer verwerken: alle bestanden van die keer hebben dezelfde waarde. */
    val runId: String,
    val at: LocalDateTime,
    /** De ZCBS-naam van wie het deed. */
    val user: String,
    val collection: String,
    val number: String,
    /** Wie het gescand heeft: de naam van de bronmap (Jelly of Ria). */
    val by: String,
    /** De map waar het bestand vandaan kwam, zoals die op de schijf heet. */
    val folder: String,
    val name: String,
) {
    val dateText: String get() = at.format(DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm", java.util.Locale.forLanguageTag("nl")))
}

/** Eén keer verwerken, zoals Carla het ziet: de bestanden van één doorloop van de flow. */
data class ProcessedRun(val runId: String, val at: LocalDateTime, val user: String, val collection: String, val files: List<ProcessedFile>) {
    val first: String get() = files.minOf { it.number }
    val last: String get() = files.maxOf { it.number }
}

/**
 * Wat er al verwerkt is, in een tekstbestand naast het instellingenbestand (een regel per bestand, gescheiden door tabs). Zo blijft
 * zichtbaar welke scans al op het archief staan, ook al blijven de bestanden op K: staan, en ook na een herstart van de app.
 */
@Service
class ProcessedLog(private val settingsService: SettingsService, private val properties: WorkflowProperties) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val stamp = DateTimeFormatter.ISO_LOCAL_DATE_TIME

    fun file(): Path {
        val configured = Path.of(properties.processedLog)
        return if (configured.isAbsolute) configured else (settingsService.settingsFile().parent ?: Path.of(".")).resolve(configured)
    }

    private fun clean(s: String) = s.replace('\t', ' ').replace('\n', ' ').replace('\r', ' ')

    @Synchronized
    fun add(entries: List<ProcessedFile>) {
        if (entries.isEmpty()) return
        val path = file()
        path.parent?.let { Files.createDirectories(it) }
        val lines = entries.joinToString("") {
            listOf(it.runId, it.at.format(stamp), it.user, it.collection, it.number, it.by, it.folder, it.name).joinToString("\t") { f -> clean(f) } + "\n"
        }
        Files.writeString(path, lines, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND)
        log.info("{} bestanden toegevoegd aan het verwerkt-log {}", entries.size, path)
    }

    @Synchronized
    fun all(): List<ProcessedFile> {
        val path = file()
        if (!Files.isRegularFile(path)) return emptyList()
        return runCatching {
            Files.readAllLines(path, StandardCharsets.UTF_8).mapNotNull { line ->
                val p = line.split('\t')
                if (p.size < 8) null
                else runCatching { ProcessedFile(p[0], LocalDateTime.parse(p[1], stamp), p[2], p[3], p[4], p[5], p[6], p[7]) }.getOrNull()
            }
        }.getOrElse { log.warn("Kan het verwerkt-log {} niet lezen: {}", path, it.message); emptyList() }
    }

    /** Per keer verwerken, de nieuwste eerst. */
    fun runs(): List<ProcessedRun> =
        all().groupBy { it.runId }.values
            .map { files -> ProcessedRun(files.first().runId, files.first().at, files.first().user, files.first().collection, files.sortedBy { it.number }) }
            .sortedByDescending { it.at }

    /** De bestanden die al verwerkt zijn, per map en naam, om in de lijst met scans aan te geven. */
    fun index(): Map<Pair<String, String>, ProcessedFile> = all().associateBy { it.folder to it.name }
}
