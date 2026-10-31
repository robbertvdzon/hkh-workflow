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

/**
 * Een pdf of afbeelding (jpg, png) in een map met scans. [processed] staat gevuld als het bestand al op het archief is gezet. Alle drie
 * kunnen worden gekozen; een png wordt bij het kiezen een jpg.
 */
data class SourceFile(val name: String, val sizeKb: Long, val modified: String, val processed: ProcessedFile?, val kind: String = "pdf") {
    val kindLabel: String get() = name.substringAfterLast('.', "").uppercase().replace("JPEG", "JPG")
}

/** Een submap, met zijn pad onder de bronmap (met `/` als scheiding). */
data class SourceDir(val name: String, val relPath: String)

/** Eén stap in het pad naar de map waar Carla nu is, om terug omhoog te kunnen. */
data class Crumb(val name: String, val relPath: String)

/** De inhoud van één map met scans: submappen, pdf's en afbeeldingen, beide op natuurlijke volgorde (scan 2 voor scan 10). */
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
class SourceFilesService(private val settingsService: SettingsService, private val processed: ProcessedLog, private val input: WorkFolders) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val dateFormat = DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm").withZone(ZoneId.systemDefault())

    fun roots(): List<SourceRoot> = settingsService.get().sourceRoots()

    private fun originsFile(): Path? = input.invoer()?.resolve("herkomst.tsv")

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
        val files = entries.filter { it.isRegularFile() && it.extension.lowercase() in VIEWABLE }.sortedWith(compareBy(naturalOrder) { it.name })
            .map {
                SourceFile(
                    it.name, (runCatching { Files.size(it) }.getOrDefault(0) + 1023) / 1024,
                    runCatching { dateFormat.format(Files.getLastModifiedTime(it).toInstant()) }.getOrDefault(""),
                    done[dir.toString() to invoerName(it.name)],
                    if (it.extension.equals("pdf", ignoreCase = true)) "pdf" else "image",
                )
            }
        val crumbs = rel.split('/').filter { it.isNotEmpty() }.runningFold(null as Crumb?) { acc, part -> Crumb(part, listOfNotNull(acc?.relPath, part).joinToString("/")) }.filterNotNull()
        return Listing(root, rel, crumbs, dirs, files)
    }

    /** Het bestand [name] in de map [relPath] van bronmap [rootName], om te bekijken of te downloaden. Weigert alles wat geen pdf of afbeelding in die map is. */
    fun fileToView(rootName: String, relPath: String, name: String): Path {
        val dir = resolve(root(rootName), relPath)
        require(name == Path.of(name).fileName.toString() && name.substringAfterLast('.', "").lowercase() in VIEWABLE) { "Dit bestand kan niet worden getoond: $name" }
        val file = dir.resolve(name)
        require(file.isRegularFile()) { "Dit bestand staat niet in de map: $name" }
        return file
    }

    private val details = java.util.concurrent.ConcurrentHashMap<String, String>()

    /**
     * Per bestand in de map: het aantal pagina's van een pdf of de afmeting van een afbeelding. Een pdf openen kost tijd op een netwerkschijf, dus
     * dit komt los van de lijst en wordt onthouden zolang het bestand niet verandert.
     */
    fun details(rootName: String, relPath: String): Map<String, String> {
        val dir = resolve(root(rootName), relPath)
        require(dir.isDirectory()) { "De map bestaat niet of is niet bereikbaar: $dir." }
        val files = runCatching { Files.list(dir).use { s -> s.filter { it.isRegularFile() && it.extension.lowercase() in VIEWABLE }.toList() } }.getOrDefault(emptyList())
        return files.associate { file ->
            val key = "$file|${runCatching { Files.size(file) }.getOrDefault(0)}|${runCatching { Files.getLastModifiedTime(file).toMillis() }.getOrDefault(0)}"
            file.name to details.computeIfAbsent(key) { describe(file) }
        }
    }

    private fun describe(file: Path): String = runCatching {
        if (file.extension.equals("pdf", ignoreCase = true)) {
            org.apache.pdfbox.Loader.loadPDF(org.apache.pdfbox.io.RandomAccessReadBufferedFile(file.toFile())).use { doc ->
                val pages = doc.numberOfPages
                if (pages == 1) "1 pagina" else "$pages pagina's"
            }
        } else {
            javax.imageio.ImageIO.createImageInputStream(file.toFile()).use { stream ->
                val reader = javax.imageio.ImageIO.getImageReaders(stream).takeIf { it.hasNext() }?.next() ?: return@runCatching "geen geldige afbeelding"
                try {
                    reader.input = stream
                    "${reader.getWidth(0)} × ${reader.getHeight(0)} px"
                } finally { reader.dispose() }
            }
        }
    }.getOrElse { "kan niet worden gelezen" }

    /** Kopieert de gekozen bestanden (pdf, jpg of png, op naam) uit één map naar de invoermap; een png wordt daarbij een jpg. Al aanwezige bestanden worden overgeslagen. */
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
            require(name == Path.of(name).fileName.toString() && name.substringAfterLast('.', "").lowercase() in VIEWABLE) { "Dit is geen pdf of afbeelding in de map: $name" }
            val source = dir.resolve(name)
            require(source.isRegularFile()) { "Dit bestand staat niet in de map: $name" }
            val destName = invoerName(name)
            val dest = target.resolve(destName)
            if (Files.exists(dest)) { skipped += "$name (staat al in de invoermap)"; continue }
            val already = done[dir.toString() to destName]
            if (already != null) { skipped += "$name (al verwerkt als ${already.number})"; continue }
            val tmp = target.resolve("$destName.kopie")
            if (name.endsWith(".png", ignoreCase = true)) {
                convertToJpg(source, tmp)
            } else {
                Files.copy(source, tmp, StandardCopyOption.REPLACE_EXISTING)
                check(Files.size(tmp) == Files.size(source)) { "Kopie van $name is niet even groot als het origineel." }
            }
            Files.move(tmp, dest)
            origins[destName] = Origin(root.name, dir.toString())
            copied += name
        }
        writeOrigins(origins)
        log.info("{} bestanden uit {} gekopieerd naar {}", copied.size, dir, target)
        return CopyResult(copied, skipped)
    }

    /** Zet een png om in een jpg (op een witte achtergrond, want een jpg heeft geen doorzichtigheid). */
    private fun convertToJpg(source: Path, target: Path) {
        val image = javax.imageio.ImageIO.read(source.toFile()) ?: throw IllegalArgumentException("Kan de png niet lezen: ${source.name}")
        val rgb = java.awt.image.BufferedImage(image.width, image.height, java.awt.image.BufferedImage.TYPE_INT_RGB)
        rgb.createGraphics().apply { color = java.awt.Color.WHITE; fillRect(0, 0, image.width, image.height); drawImage(image, 0, 0, null); dispose() }
        val writer = javax.imageio.ImageIO.getImageWritersByFormatName("jpg").next()
        try {
            val params = writer.defaultWriteParam.apply { compressionMode = javax.imageio.ImageWriteParam.MODE_EXPLICIT; compressionQuality = 0.95f }
            javax.imageio.ImageIO.createImageOutputStream(target.toFile()).use { out ->
                writer.output = out
                writer.write(null, javax.imageio.IIOImage(rgb, null, null), params)
            }
        } finally { writer.dispose() }
    }

    /** Haalt alle kopieën weg die de app zelf in de invoermap heeft gezet (wat Carla zelf neerzette, blijft staan). @return hoeveel. */
    @Synchronized
    fun removeAllCopies(): Int {
        val folder = input.invoer() ?: return 0
        val names = readOrigins().keys.toList()
        val removed = names.count { Files.deleteIfExists(folder.resolve(it)) }
        forget(names)
        return removed
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
        /** De soorten bestanden die in de lijst met scans staan: allemaal te bekijken en te kiezen. */
        val VIEWABLE = setOf("pdf", "jpg", "jpeg", "png")

        /** De naam van een gekozen bestand in de invoermap: een png wordt een jpg, de rest blijft zoals het is. */
        fun invoerName(name: String): String = if (name.endsWith(".png", ignoreCase = true)) name.dropLast(4) + ".jpg" else name

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
