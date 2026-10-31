package nl.vdzon.hkhworkflow.adminlog

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.KotlinModule

private data class Rule(val pattern: String, val answer: String, val secret: Boolean = false)

/** Een stand zoals de instellingen: gewone velden, een wachtwoord en antwoordregels waarvan sommige geheim zijn. */
private data class Settings(val ftpHost: String = "", val ftpPassword: String = "", val answers: List<Rule> = emptyList())

class SnapshotSanitizerTest {
    private val mapper = JsonMapper.builder().addModule(KotlinModule.Builder().build()).build()
    private val sanitizer = SnapshotSanitizer(mapper)

    @Test
    fun `de instellingen gaan mee, maar zonder wachtwoorden`() {
        val settings = Settings(
            ftpHost = "ftp.example.org", ftpPassword = "ftp-geheim",
            answers = listOf(Rule("Kies 1,2,3", "6"), Rule("FTP wachtwoord", "antwoord-geheim", secret = true)),
        )
        val cleaned = sanitizer.clean(settings)
        val json = cleaned.tree.toString()
        assertEquals("ftp.example.org", cleaned.tree.path("ftpHost").asString())
        assertEquals(SnapshotSanitizer.REDACTED, cleaned.tree.path("ftpPassword").asString())
        // De vlag zelf blijft een gewone waarde.
        assertEquals(true, cleaned.tree.path("answers").path(1).path("secret").asBoolean())
        assertFalse("ftp-geheim" in json)
        assertFalse("antwoord-geheim" in json)
        assertEquals("6", cleaned.tree.path("answers").path(0).path("answer").asString())
    }

    @Test
    fun `een geheim dat in de uitvoer van een programma staat, wordt eruit gehaald`() {
        val cleaned = sanitizer.clean(Settings(ftpPassword = "ftp-geheim"))
        assertEquals("FTP wachtwoord: *** gevraagd", cleaned.scrub("FTP wachtwoord: ftp-geheim gevraagd"))
        // Een leeg of heel kort geheim vervangt niets (anders verdwijnt elke letter uit de tekst).
        assertEquals("abc", sanitizer.clean(Settings(ftpPassword = "")).scrub("abc"))
        assertEquals("abc", sanitizer.clean(Settings(ftpPassword = "a")).scrub("abc"))
    }
}
