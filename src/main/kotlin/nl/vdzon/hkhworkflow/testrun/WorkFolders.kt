package nl.vdzon.hkhworkflow.testrun

import java.nio.file.Files
import java.nio.file.Path
import nl.vdzon.hkhworkflow.settings.SettingsService

/**
 * De mappen van één gebruiker in één collectie: `<werkmap>/<gebruiker>/<collectie>/` met daarin `invoer` (de gekozen bestanden), `werk`
 * (de kopieën met recordnummer), `pdf2zcbs` en `images` (een kopie van het programma met alleen de bestanden voor die twee programma's, zodat
 * de gebruiker kan meekijken) en `resten`. De werkmap is de instelling "werkmap" (voorheen de invoermap) en kan op het netwerk staan. Zonder
 * ingelogde gebruiker (de technische pagina van de beheerder) heet de gebruikersmap `gebruiker`.
 */
class WorkFolders(private val settingsService: SettingsService, val user: String, val collection: String) {
    /** De werkmap uit de instellingen, of null als die niet is ingesteld. */
    fun base(): Path? = settingsService.get().testFolder.trim().takeIf { it.isNotEmpty() }?.let { Path.of(it) }

    /** De map van deze gebruiker in deze collectie (die er nog niet hoeft te zijn). */
    fun root(): Path? = base()?.resolve(folderName(user))?.resolve(collection)

    /** De gekozen bestanden, zoals ze zijn gekopieerd uit de mappen met scans. */
    fun invoer(): Path? = root()?.resolve("invoer")

    /** De kopieën met het recordnummer als naam. */
    fun werk(): Path? = root()?.resolve("werk")

    /** De map van pdf2zcbs.exe: het programma, zijn ini en de pdf's die hij verwerkt, met `output`. */
    fun pdf2zcbs(): Path? = root()?.resolve("pdf2zcbs")

    /** De map van images.exe: het programma, zijn ini, i_view32 en de afbeeldingen (`images`, `small`, `large`, `100pct`). */
    fun images(): Path? = root()?.resolve("images")

    /** Hierheen gaan resten van een eerdere keer: de instelling (met een submap per gebruiker en collectie) of de map `resten` van de gebruiker. */
    fun resten(): Path? {
        settingsService.get().leftoversFolder.trim().takeIf { it.isNotEmpty() }?.let { return Path.of(it).resolve(folderName(user)).resolve(collection) }
        return root()?.resolve("resten")
    }

    /** Maakt de map van de gebruiker met `invoer` aan en geeft `invoer` terug. De werkmap zelf moet er al zijn. */
    fun ensure(): Path {
        val base = requireNotNull(base()) { "Stel eerst de werkmap in bij Instellingen." }
        require(Files.isDirectory(base)) { "De werkmap bestaat niet: $base" }
        return invoer()!!.also { Files.createDirectories(it) }
    }

    /** Maakt ook de mappen voor de programma's aan. */
    fun ensureAll() {
        ensure()
        Files.createDirectories(pdf2zcbs()!!)
        Files.createDirectories(images()!!)
    }

    companion object {
        /** Een bruikbare mapnaam van een ZCBS-naam: tekens die op schijf niet mogen of lastig zijn worden `_`. */
        fun folderName(user: String): String {
            val clean = user.trim().replace(Regex("[^\\p{L}\\p{N}._ -]"), "_").trim('.', ' ')
            return clean.ifEmpty { "gebruiker" }
        }
    }
}
