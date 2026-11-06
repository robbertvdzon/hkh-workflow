package nl.vdzon.hkhworkflow

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.extension
import kotlin.io.path.isDirectory
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * De afspraak over modules (zie docs/modules.md): de root van een module is zijn kleine, openbare kant. Daar staan alleen interfaces en de domeinklassen die
 * daarbij horen. Services, clients, configuratie en controllers staan in een subpackage (`internal`, `api`), en die zijn voor andere modules onzichtbaar
 * (dat bewaakt `ModulithArchitectureTest`). Modulith zelf vindt alles in de root prima, daarom staat de regel hier.
 */
class ModuleApiConventionTest {
    private val root = Path.of("src/main/kotlin/nl/vdzon/hkhworkflow")

    /** Geen interface of dataklasse, maar wel bewust openbaar: domeinklassen met gedrag en pure regels zonder afhankelijkheden. */
    private val allowedPlainTypes = setOf(
        "Current", // wie is aan het woord
        "AppVersion", // het versienummer van de app
        "SnapshotSanitizer", // haalt wachtwoorden uit een melding
        "ItemForm", // het formulier van een record
        "FreeNumbers", // de vrije recordnummers
        "ZcbsText", // welke tekens en woorden ZCBS weigert
        "UnencodableException",
    )
    private val stereotype = Regex("""(?m)^@(Service|Component|Controller|RestController|ControllerAdvice|Configuration|Repository)\b""")
    private val plainType = Regex("""(?m)^(?:(?:public|open|abstract|final)\s+)*(class|object)\s+(\w+)""")

    private fun moduleRootFiles(): List<Path> =
        root.listDirectoryEntries().filter { it.isDirectory() }.flatMap { module -> module.listDirectoryEntries().filter { it.extension == "kt" } }

    @Test
    fun `in de root van een module staan geen Spring-beans`() {
        val found = moduleRootFiles().filter { stereotype.containsMatchIn(it.readText()) }.map { root.relativize(it).toString() }
        assertEquals(emptyList(), found, "Een service, client, configuratie of controller hoort in een subpackage van de module.")
    }

    @Test
    fun `in de root van een module staan alleen interfaces en domeinklassen`() {
        val found = moduleRootFiles().flatMap { file ->
            plainType.findAll(file.readText()).map { it.groupValues[2] }.filter { it !in allowedPlainTypes }.map { "${root.relativize(file)}: $it" }
        }
        assertEquals(emptyList(), found, "Implementatie hoort in internal. Maak er een interface in de root van, of zet het in de lijst als het echt domein is.")
    }

    @Test
    fun `andere modules gebruiken alleen de root van een module`() {
        val moduleNames = root.listDirectoryEntries().filter { it.isDirectory() }.map { it.name }
        val found = Files.walk(root).use { files ->
            files.filter { it.extension == "kt" }.toList().flatMap { file ->
                val own = root.relativize(file).first().toString()
                file.readText().lines().filter { line ->
                    moduleNames.any { m -> m != own && Regex("""^import nl\.vdzon\.hkhworkflow\.$m\.[a-z]""").containsMatchIn(line) }
                }.map { "${root.relativize(file)}: $it" }
            }
        }
        assertEquals(emptyList(), found)
    }
}
