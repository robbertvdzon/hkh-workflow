package nl.vdzon.hkhworkflow.flow.internal

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.io.path.extension
import kotlin.io.path.isRegularFile
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import nl.vdzon.hkhworkflow.exerunner.AutoAnswer
import nl.vdzon.hkhworkflow.exerunner.ExeRunner
import nl.vdzon.hkhworkflow.exerunner.RunMode
import nl.vdzon.hkhworkflow.ftp.FtpService
import nl.vdzon.hkhworkflow.ftp.UploadItem
import nl.vdzon.hkhworkflow.items.Current
import nl.vdzon.hkhworkflow.items.ZcbsSession
import nl.vdzon.hkhworkflow.settings.SettingsService
import nl.vdzon.hkhworkflow.settings.WorkflowSettings
import org.slf4j.LoggerFactory

/** Wat er van het opruimen is gekomen; [inputCleared] zegt dat de gekozen bestanden in de invoermap er ook bij zaten. */
data class ClearOutcome(val message: String, val inputCleared: Boolean)

/** De resten van een eerdere run in de mappen van de gebruiker: vinden en opruimen. pdf2zcbs.exe wist zijn uitvoer bij de start en images.exe zou oude afbeeldingen opnieuw uploaden. */
class LeftoverFolders(private val input: WorkFolders) {
    private val log = LoggerFactory.getLogger(javaClass)

    /**
     * Wat er nog in de mappen van pdf2zcbs.exe en images.exe staat van een eerdere run (of van handwerk): pdf's naast pdf2zcbs, de uitvoer in
     * `output` (behalve `log` en `updates.txt`), de bestanden in `images` en de resultaten in `small`, `large` en `100pct`.
     */
    fun find(s: WorkflowSettings, runActive: Boolean, inputAccepted: Boolean): List<Leftover> {
        val l = FlowLayout(s, input)
        fun regular(dir: Path, deep: Boolean, skip: (Path) -> Boolean = { false }): List<Path> {
            if (!Files.isDirectory(dir)) return emptyList()
            return runCatching {
                if (deep) Files.walk(dir).use { w -> w.filter { Files.isRegularFile(it) && !skip(it) }.sorted().toList() }
                else dir.listDirectoryEntries().filter { it.isRegularFile() && !skip(it) }.sortedBy { it.name }
            }.getOrDefault(emptyList())
        }
        return buildList {
            // Wat al in de invoermap staat zonder dat Carla het in deze doorloop koos, is van een eerdere keer.
            input.invoer()?.takeIf { !runActive && !inputAccepted }?.let { dir ->
                regular(dir, true) { it.name == "herkomst.tsv" }.takeIf { it.isNotEmpty() }
                    ?.let { add(Leftover("invoer", "je gekozen bestanden", dir, it)) }
            }
            // De kopieën met het recordnummer van een doorloop die niet is afgemaakt (in een doorloop horen ze er juist te staan).
            input.werk()?.takeIf { !runActive }?.let { dir ->
                regular(dir, true).takeIf { it.isNotEmpty() }?.let { add(Leftover("werk", "de kopieën met recordnummer", dir, it)) }
            }
            l.p2z?.let { dir ->
                regular(dir, false) { !it.extension.equals("pdf", ignoreCase = true) }.takeIf { it.isNotEmpty() }?.let { add(Leftover("pdf2zcbs", "pdf2zcbs: pdf's in de map van het programma", dir, it)) }
                val out = dir.resolve("output")
                regular(out, true) { out.relativize(it).getName(0).toString() == "log" || it.name == "updates.txt" }.takeIf { it.isNotEmpty() }
                    ?.let { add(Leftover("pdf2zcbs", "pdf2zcbs: de map output", dir, it)) }
            }
            l.img?.let { dir ->
                regular(dir.resolve("images"), false).takeIf { it.isNotEmpty() }?.let { add(Leftover("images", "images.exe: bronbestanden in de map images", dir, it)) }
                listOf("small", "large", "100pct").forEach { sub ->
                    regular(dir.resolve(sub), true).takeIf { it.isNotEmpty() }?.let { add(Leftover("images", "images.exe: de map $sub", dir, it)) }
                }
            }
        }
    }

    /** Alleen de bestanden uit [items]; lege mappen daaronder verdwijnen. */
    fun clear(items: List<Leftover>, move: Boolean, s: WorkflowSettings): ClearOutcome {
        var inputCleared = false
        val total = items.sumOf { it.files.size }
        val target = if (move) {
            val root = input.resten() ?: return ClearOutcome("Stel eerst de invoermap of de map voor resten in bij Instellingen.", false)
            root.resolve(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")))
        } else null
        try {
            items.forEach { item ->
                val area = item.area
                item.files.forEach { file ->
                    check(file.toAbsolutePath().normalize().startsWith(item.base.toAbsolutePath().normalize())) { "Weigert iets buiten de map van het programma aan te raken: $file" }
                    if (target != null) {
                        val dest = target.resolve(area).resolve(item.base.relativize(file).toString())
                        Files.createDirectories(dest.parent)
                        Files.move(file, dest)
                    } else Files.delete(file)
                }
            }
            val l = FlowLayout(s, input)
            listOfNotNull(l.p2zOutput(), l.img?.resolve("small"), l.img?.resolve("large"), l.img?.resolve("100pct"), input.werk()).forEach { pruneEmptyDirs(it) }
            if (items.any { it.area == "invoer" }) {
                inputCleared = true
                input.invoer()?.resolve("herkomst.tsv")?.let { Files.deleteIfExists(it) }
            }
        } catch (e: Exception) {
            log.warn("Resten opruimen mislukt: {}", e.message)
            return ClearOutcome("Opruimen is niet helemaal gelukt: ${e.message ?: e.javaClass.simpleName}. Controleer de mappen van de programma's.", inputCleared)
        }
        log.info("{} bestanden van een eerdere run {}", total, if (target != null) "verplaatst naar $target" else "verwijderd")
        return ClearOutcome(if (target != null) "$total bestanden verplaatst naar $target." else "$total bestanden verwijderd.", inputCleared)
    }

    /** Verwijdert lege (sub)mappen onder [dir], en [dir] zelf als die leeg is. */
    private fun pruneEmptyDirs(dir: Path) {
        if (!Files.isDirectory(dir)) return
        Files.walk(dir).use { w -> w.sorted(Comparator.reverseOrder()).filter { Files.isDirectory(it) }.toList() }.forEach { runCatching { Files.deleteIfExists(it) } }
    }
}
