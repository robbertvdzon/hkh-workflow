package nl.vdzon.hkhworkflow.testrun

import java.nio.file.Files
import java.nio.file.Path
import nl.vdzon.hkhworkflow.items.ItemEditService
import nl.vdzon.hkhworkflow.settings.SettingsService
import org.springframework.stereotype.Component

/**
 * De invoermap van wie nu is ingelogd. De instelling is de gemeenschappelijke map (`c:\zcbs\invoer`); elke gebruiker werkt in een eigen
 * submap met zijn of haar ZCBS-naam, zodat de bestanden van de een niet door de ander worden meegenomen. Zonder inlog (de technische
 * pagina van de beheerder) is het de gemeenschappelijke map zelf.
 */
@Component
class InputFolder(private val settingsService: SettingsService, private val items: ItemEditService) {
    /** De gemeenschappelijke invoermap uit de instellingen, of null als die niet is ingesteld. */
    fun base(): Path? = settingsService.get().testFolder.trim().takeIf { it.isNotEmpty() }?.let { Path.of(it) }

    /** De map van de ingelogde gebruiker (die er nog niet hoeft te zijn). */
    fun path(): Path? {
        val base = base() ?: return null
        val user = items.user ?: return base
        return base.resolve(folderName(user))
    }

    /** De naam van de ingelogde gebruiker, of leeg zonder inlog. */
    fun user(): String = items.user.orEmpty()

    /** Maakt de map van de gebruiker aan als die er nog niet is. */
    fun ensure(): Path {
        val base = requireNotNull(base()) { "Stel eerst de invoermap in bij Instellingen." }
        require(Files.isDirectory(base)) { "De invoermap bestaat niet: $base" }
        return path()!!.also { Files.createDirectories(it) }
    }

    companion object {
        /** De submap van de gemeenschappelijke invoermap waar resten van eerdere runs naartoe gaan; geen gebruiker kan zo heten. */
        const val LEFTOVERS_DIR = "resten"

        /** Een bruikbare mapnaam van een ZCBS-naam: tekens die op schijf niet mogen of lastig zijn worden `_`. */
        fun folderName(user: String): String {
            val clean = user.trim().replace(Regex("[^\\p{L}\\p{N}._ -]"), "_").trim('.', ' ')
            return clean.ifEmpty { "gebruiker" }.let { if (it.equals(LEFTOVERS_DIR, ignoreCase = true)) "${it}_" else it }
        }
    }
}
