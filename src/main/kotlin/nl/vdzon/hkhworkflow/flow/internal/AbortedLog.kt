package nl.vdzon.hkhworkflow.flow.internal

import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import nl.vdzon.hkhworkflow.settings.SettingsService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

/**
 * Een doorloop die is afgebroken nadat images.exe al was gestart: de records [numbers] kunnen dan al in ZCBS staan en de afbeeldingen op de
 * server, terwijl de upload van tekst en pdf of de back-up niet is gedaan. [by] is wie het afbrak: de gebruiker zelf of de beheerder.
 */
data class AbortedFlow(val at: LocalDateTime, val user: String, val collection: String, val numbers: String, val fileCount: Int, val steps: String, val by: String)

/**
 * De doorlopen die niet zijn afgemaakt nadat images.exe was gestart, in een tekstbestand naast het instellingenbestand (een regel per doorloop,
 * gescheiden door tabs). De beheerder ziet ze op de pagina Sessies en loopt in ZCBS na wat er van die records is geworden.
 */
@Service
class AbortedLog(private val settingsService: SettingsService) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val stamp = DateTimeFormatter.ISO_LOCAL_DATE_TIME

    fun file(): Path = (settingsService.settingsFile().parent ?: Path.of(".")).resolve(FILE_NAME)

    private fun clean(s: String) = s.replace('\t', ' ').replace('\n', ' ').replace('\r', ' ')

    @Synchronized
    fun add(entry: AbortedFlow) {
        val path = file()
        path.parent?.let { Files.createDirectories(it) }
        val line = listOf(entry.at.format(stamp), entry.user, entry.collection, entry.numbers, entry.fileCount.toString(), entry.steps, entry.by).joinToString("\t") { clean(it) }
        Files.writeString(path, line + "\n", StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND)
    }

    /** Nieuwste eerst. */
    @Synchronized
    fun all(): List<AbortedFlow> {
        val path = file()
        if (!Files.isRegularFile(path)) return emptyList()
        return runCatching {
            Files.readAllLines(path, StandardCharsets.UTF_8).mapNotNull { line ->
                val p = line.split('\t')
                if (p.size < 7) null else runCatching { AbortedFlow(LocalDateTime.parse(p[0], stamp), p[1], p[2], p[3], p[4].toInt(), p[5], p[6]) }.getOrNull()
            }.reversed()
        }.getOrElse { log.warn("Kan het log met afgebroken doorlopen {} niet lezen: {}", path, it.message); emptyList() }
    }

    companion object {
        const val FILE_NAME = "hkh-workflow-afgebroken.tsv"
    }
}
