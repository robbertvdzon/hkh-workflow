package nl.vdzon.hkhworkflow.testrun

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.io.path.extension
import kotlin.io.path.isRegularFile
import kotlin.io.path.name
import nl.vdzon.hkhworkflow.settings.SettingsService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

/** Een pdf op de server die Ria (of iemand anders) klaargezet heeft. [by] is de persoon, de eerste map onder de bronmap. */
data class SourceFile(val idx: Int, val relPath: String, val name: String, val by: String, val folder: String, val sizeKb: Long, val modified: String)

/** Waar een bestand in de testmap vandaan komt. */
data class Origin(val by: String, val folder: String)

data class CopyResult(val copied: List<String>, val skipped: List<String>)

/**
 * De eerste stap van de flow: de bestanden die Ria heeft aangegeven staan op de server (`K:\scabslelly\<persoon>\<map>`).
 * Hier worden ze naar de testmap (de invoermap) gekopieerd. De originelen blijven staan en er wordt nooit iets overschreven.
 * Wie het gescand heeft, is niet in de pdf te vinden maar blijkt uit de map: die wordt per bestand onthouden.
 */
@Service
class SourceFilesService(private val settingsService: SettingsService) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val dateFormat = DateTimeFormatter.ofPattern("d MMM, HH:mm").withZone(ZoneId.systemDefault())

    fun sourceRoot(): Path? = settingsService.get().sourceFolder.trim().takeIf { it.isNotEmpty() }?.let { Path.of(it) }

    private fun originsFile(): Path? = settingsService.get().testFolder.trim().takeIf { it.isNotEmpty() }?.let { Path.of(it).resolve("herkomst.tsv") }

    /** De pdf's onder de bronmap, tot drie mappen diep, gesorteerd per persoon en map. */
    fun candidates(): List<SourceFile> {
        val root = sourceRoot()?.takeIf { Files.isDirectory(it) } ?: return emptyList()
        val found = runCatching {
            Files.walk(root, 4).use { walk ->
                walk.filter { it.isRegularFile() && it.extension.equals("pdf", ignoreCase = true) }
                    .sorted(compareBy({ root.relativize(it).parent?.toString().orEmpty().lowercase() }, { it.name.lowercase() }))
                    .toList()
            }
        }.getOrDefault(emptyList())
        return found.mapIndexed { i, path ->
            val rel = root.relativize(path)
            val by = if (rel.nameCount > 1) rel.getName(0).toString() else ""
            SourceFile(
                i, rel.toString(), path.name, by, path.parent.toString(),
                (runCatching { Files.size(path) }.getOrDefault(0) + 1023) / 1024,
                runCatching { dateFormat.format(Files.getLastModifiedTime(path).toInstant()) }.getOrDefault(""),
            )
        }
    }

    /** Kopieert de gekozen bestanden (door hun pad onder de bronmap) naar de testmap. */
    @Synchronized
    fun copy(relPaths: List<String>): CopyResult {
        val s = settingsService.get()
        require(s.testFolder.isNotBlank()) { "Stel eerst de testmap in bij Instellingen." }
        val target = Path.of(s.testFolder)
        require(Files.isDirectory(target)) { "De testmap bestaat niet: $target" }
        require(relPaths.isNotEmpty()) { "Kies minstens één bestand." }
        val root = requireNotNull(sourceRoot()) { "Stel eerst de bronmap op de server in bij Instellingen." }
        val available = candidates().associateBy { it.relPath }
        val copied = mutableListOf<String>()
        val skipped = mutableListOf<String>()
        val origins = readOrigins().toMutableMap()
        for (rel in relPaths) {
            val file = available[rel] ?: throw IllegalArgumentException("Dit bestand staat niet in de bronmap: $rel")
            val dest = target.resolve(file.name)
            if (Files.exists(dest)) { skipped += "${file.name} (staat al in de testmap)"; continue }
            val tmp = target.resolve(file.name + ".kopie")
            Files.copy(root.resolve(rel), tmp, StandardCopyOption.REPLACE_EXISTING)
            check(Files.size(tmp) == Files.size(root.resolve(rel))) { "Kopie van ${file.name} is niet even groot als het origineel." }
            Files.move(tmp, dest)
            origins[file.name] = Origin(file.by, file.folder)
            copied += file.name
        }
        writeOrigins(origins)
        log.info("{} bestanden van {} gekopieerd naar {}", copied.size, root, target)
        return CopyResult(copied, skipped)
    }

    fun origin(fileName: String): Origin? = readOrigins()[fileName]

    private fun readOrigins(): Map<String, Origin> {
        val file = originsFile()?.takeIf { Files.isRegularFile(it) } ?: return emptyMap()
        return runCatching {
            Files.readAllLines(file).mapNotNull { line ->
                val parts = line.split('\t')
                if (parts.size >= 3) parts[0] to Origin(parts[1], parts[2]) else null
            }.toMap()
        }.getOrDefault(emptyMap())
    }

    private fun writeOrigins(origins: Map<String, Origin>) {
        val file = originsFile() ?: return
        Files.writeString(file, origins.entries.joinToString("\n") { "${it.key}\t${it.value.by}\t${it.value.folder}" } + "\n")
    }
}
