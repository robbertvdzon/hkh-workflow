package nl.vdzon.hkhworkflow.testrun

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.io.path.extension
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile
import kotlin.io.path.name
import nl.vdzon.hkhworkflow.settings.SettingsService
import nl.vdzon.hkhworkflow.settings.SourceRoot
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

/** Een pdf in een map met scans. [processed] staat gevuld als het bestand al op het archief is gezet. */
data class SourceFile(val name: String, val sizeKb: Long, val modified: String, val processed: ProcessedFile?)

/** Een submap, met zijn pad onder de bronmap (met `/` als scheiding). */
data class SourceDir(val name: String, val relPath: String)

/** Eén stap in het pad naar de map waar Carla nu is, om terug omhoog te kunnen. */
data class Crumb(val name: String, val relPath: String)

/** De inhoud van één map met scans: submappen en pdf's, beide op natuurlijke volgorde (scan 2 voor scan 10). */
data class Listing(val root: SourceRoot, val relPath: String, val crumbs: List<Crumb>, val dirs: List<SourceDir>, val files: List<SourceFile>) {
    val folderName: String get() = crumbs.lastOrNull()?.name ?: root.name
    val parent: String? get() = if (relPath.isEmpty()) null else relPath.substringBeforeLast('/', "")
}

/** Waar een bestand in de invoermap vandaan komt: de naam van de bronmap (wie het scande) en de map zelf. */
data class Origin(val by: String, val folder: String)

data class CopyResult(val copied: List<String>, val skipped: List<String>)

/**
 * De eerste stap van de flow: Jelly of Ria vragen Carla om bestanden op het archief te zetten ("bestand X t/m Y uit map Z").
 * Ze staan op K: in een van de bronmappen. Hier bladert Carla door die mappen en kopieert ze de aangevinkte pdf's naar de
 * invoermap. De originelen blijven staan en er wordt nooit iets overschreven. Wie het gescand heeft, is niet in de pdf te
 * vinden maar blijkt uit de bronmap: die wordt per bestand onthouden.
 */
@Service
class SourceFilesService(private val settingsService: SettingsService, private val processed: ProcessedLog, private val input: InputFolder) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val dateFormat = DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm").withZone(ZoneId.systemDefault())

    fun roots(): List<SourceRoot> = settingsService.get().sourceRoots()

    private fun originsFile(): Path? = input.path()?.resolve("herkomst.tsv")

    /** De map onder de bronmap [rootName], op [relPath] (met `/`). Weigert paden die buiten de bronmap uitkomen. */
    private fun resolve(root: SourceRoot, relPath: String): Path {
        val base = Path.of(root.path).toAbsolutePath().normalize()
        val dir = base.resolve(relPath.trim('/')).normalize()
        require(dir.startsWith(base)) { "Dit pad valt buiten de map ${root.name}." }
        return dir
    }

    private fun root(name: String): SourceRoot = roots().firstOrNull { it.name == name } ?: throw IllegalArgumentException("Onbekende map met scans: $name")

    /** Laat zien wat er in de map staat. Gooit een melding als de map niet bestaat of niet bereikbaar is (is K: gekoppeld?). */
    fun browse(rootName: String, relPath: String): Listing {
        val root = root(rootName)
        val dir = resolve(root, relPath)
        require(dir.isDirectory()) { "De map bestaat niet of is niet bereikbaar: $dir. Is de schijf gekoppeld?" }
        val done = processed.index()
        val entries = runCatching { Files.list(dir).use { it.toList() } }.getOrElse { throw IllegalArgumentException("Kan de map niet lezen: $dir (${it.message})") }
        val rel = dir.let { Path.of(root.path).toAbsolutePath().normalize().relativize(it).toString().replace('\\', '/').replace("//", "/") }.trim('/')
        val dirs = entries.filter { it.isDirectory() && !it.name.startsWith(".") }.sortedWith(compareBy(naturalOrder) { it.name })
            .map { SourceDir(it.name, if (rel.isEmpty()) it.name else "$rel/${it.name}") }
        val files = entries.filter { it.isRegularFile() && it.extension.equals("pdf", ignoreCase = true) }.sortedWith(compareBy(naturalOrder) { it.name })
            .map {
                SourceFile(
                    it.name, (runCatching { Files.size(it) }.getOrDefault(0) + 1023) / 1024,
                    runCatching { dateFormat.format(Files.getLastModifiedTime(it).toInstant()) }.getOrDefault(""),
                    done[dir.toString() to it.name],
                )
            }
        val crumbs = rel.split('/').filter { it.isNotEmpty() }.runningFold(null as Crumb?) { acc, part -> Crumb(part, listOfNotNull(acc?.relPath, part).joinToString("/")) }.filterNotNull()
        return Listing(root, rel, crumbs, dirs, files)
    }

    /** Kopieert de gekozen pdf's (op naam) uit één map naar de invoermap. Al aanwezige bestanden worden overgeslagen. */
    @Synchronized
    fun copy(rootName: String, relPath: String, names: List<String>): CopyResult {
        val target = input.ensure()
        require(names.isNotEmpty()) { "Kies minstens één bestand." }
        val root = root(rootName)
        val dir = resolve(root, relPath)
        val done = processed.index()
        val copied = mutableListOf<String>()
        val skipped = mutableListOf<String>()
        val origins = readOrigins().toMutableMap()
        for (name in names) {
            require(name == Path.of(name).fileName.toString() && name.lowercase().endsWith(".pdf")) { "Dit is geen pdf-bestand in de map: $name" }
            val source = dir.resolve(name)
            require(source.isRegularFile()) { "Dit bestand staat niet in de map: $name" }
            val dest = target.resolve(name)
            if (Files.exists(dest)) { skipped += "$name (staat al in de invoermap)"; continue }
            val already = done[dir.toString() to name]
            if (already != null) { skipped += "$name (al verwerkt als ${already.number})"; continue }
            val tmp = target.resolve("$name.kopie")
            Files.copy(source, tmp, StandardCopyOption.REPLACE_EXISTING)
            check(Files.size(tmp) == Files.size(source)) { "Kopie van $name is niet even groot als het origineel." }
            Files.move(tmp, dest)
            origins[name] = Origin(root.name, dir.toString())
            copied += name
        }
        writeOrigins(origins)
        log.info("{} bestanden uit {} gekopieerd naar {}", copied.size, dir, target)
        return CopyResult(copied, skipped)
    }

    /** Haalt een bestand dat de app zelf in de invoermap heeft gezet weer weg (de kopie; het origineel blijft staan). */
    @Synchronized
    fun removeCopy(name: String) {
        val folder = requireNotNull(input.path()) { "Stel eerst de invoermap in bij Instellingen." }
        require(name == Path.of(name).fileName.toString()) { "Ongeldige bestandsnaam." }
        Files.deleteIfExists(folder.resolve(name))
        forget(listOf(name))
    }

    fun origin(fileName: String): Origin? = readOrigins()[fileName]

    /** Vergeet waar deze bestanden vandaan kwamen (na het opruimen van de invoermap). */
    @Synchronized
    fun forget(names: Collection<String>) {
        val origins = readOrigins().toMutableMap()
        if (names.none { it in origins }) return
        names.forEach { origins.remove(it) }
        if (origins.isEmpty()) originsFile()?.let { Files.deleteIfExists(it) } else writeOrigins(origins)
    }

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
        if (origins.isEmpty()) return
        Files.writeString(file, origins.entries.joinToString("\n") { "${it.key}\t${it.value.by}\t${it.value.folder}" } + "\n")
    }

    companion object {
        /** Natuurlijke volgorde: cijfers als getal, zodat "scan 2" voor "scan 10" komt, en zonder onderscheid tussen hoofd- en kleine letters. */
        val naturalOrder: Comparator<String> = Comparator { a, b ->
            val x = Regex("\\d+|\\D+").findAll(a.lowercase()).map { it.value }.toList()
            val y = Regex("\\d+|\\D+").findAll(b.lowercase()).map { it.value }.toList()
            var result = 0
            for (i in 0 until minOf(x.size, y.size)) {
                val p = x[i]
                val q = y[i]
                result = if (p[0].isDigit() && q[0].isDigit()) {
                    val c = p.toBigInteger().compareTo(q.toBigInteger())
                    if (c != 0) c else p.length.compareTo(q.length)
                } else p.compareTo(q)
                if (result != 0) break
            }
            if (result != 0) result else x.size.compareTo(y.size)
        }
    }
}
