package nl.vdzon.hkhworkflow.integration

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * De integratietests mogen niets weten van de code van de app: ze praten via http, ftp en bestanden. Daarom mag code in dit package alleen het
 * startpunt van de app importeren. Dan blijven de tests gelijk als de code eronder wordt omgebouwd.
 */
class BlackBoxRuleTest {
    @Test
    fun `de integratietests importeren niets uit de app, behalve het startpunt`() {
        val root = Path.of("src/test/kotlin/nl/vdzon/hkhworkflow/integration")
        val allowed = Regex("""import nl\.vdzon\.hkhworkflow\.(HkhWorkflowApplication|integration\..*)""")
        val violations = Files.walk(root).use { files ->
            files.filter { it.toString().endsWith(".kt") }.toList().flatMap { file ->
                Files.readAllLines(file).filter { it.startsWith("import nl.vdzon.hkhworkflow.") && !allowed.matches(it.trim()) }.map { "${file.fileName}: $it" }
            }
        }
        assertEquals(emptyList(), violations)
    }
}
