package nl.vdzon.hkhworkflow.testrun

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlin.io.path.extension
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile
import kotlin.io.path.name

/**
 * Zet de bestanden van een programma (pdf2zcbs.exe of images.exe: de exe, zijn ini, i_view32, plugins...) uit de hoofdmap in de map van de
 * gebruiker, zodat elke gebruiker zijn eigen kopie draait en de uitvoer niet door elkaar loopt. Alleen wat ontbreekt of verandert wordt
 * gekopieerd; er wordt nooit iets in de map van de gebruiker verwijderd. De gegevensmappen (`output`, `images`, `small`, ...) en de bestanden
 * die het programma zelf maakt (logs, pdf's, afbeeldingen) blijven in de hoofdmap.
 */
object ProgramFiles {
    private val DATA_DIRS = setOf("output", "images", "small", "large", "100pct", "log", "werk", "invoer")
    private val DATA_EXTENSIONS = setOf("pdf", "jpg", "jpeg", "png", "tif", "tiff")

    /** @return hoeveel bestanden zijn gekopieerd; 0 als [masterExe] niet bestaat of dezelfde map is als [target]. */
    fun sync(masterExe: String, target: Path): Int {
        if (masterExe.isBlank()) return 0
        val master = Path.of(masterExe).toAbsolutePath().parent ?: return 0
        if (!master.isDirectory() || master.normalize() == target.toAbsolutePath().normalize()) return 0
        Files.createDirectories(target)
        return copyDir(master, target)
    }

    private fun copyDir(from: Path, to: Path): Int {
        var copied = 0
        Files.list(from).use { it.toList() }.forEach { entry ->
            val name = entry.name
            when {
                entry.isDirectory() -> if (name.lowercase() !in DATA_DIRS) {
                    Files.createDirectories(to.resolve(name))
                    copied += copyDir(entry, to.resolve(name))
                }
                entry.isRegularFile() -> if (!isData(entry)) {
                    val dest = to.resolve(name)
                    if (!Files.exists(dest) || Files.size(dest) != Files.size(entry) || Files.getLastModifiedTime(dest) < Files.getLastModifiedTime(entry)) {
                        Files.copy(entry, dest, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES)
                        copied++
                    }
                }
            }
        }
        return copied
    }

    private fun isData(file: Path): Boolean =
        file.extension.lowercase() in DATA_EXTENSIONS || (file.name.startsWith("log-") && file.extension.equals("txt", true))
}
