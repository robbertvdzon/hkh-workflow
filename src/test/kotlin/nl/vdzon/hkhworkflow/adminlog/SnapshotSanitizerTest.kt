package nl.vdzon.hkhworkflow.adminlog

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import nl.vdzon.hkhworkflow.settings.AnswerRule
import nl.vdzon.hkhworkflow.settings.WorkflowSettings
import tools.jackson.databind.json.JsonMapper
import tools.jackson.module.kotlin.KotlinModule

class SnapshotSanitizerTest {
    private val mapper = JsonMapper.builder().addModule(KotlinModule.Builder().build()).build()
    private val sanitizer = SnapshotSanitizer(mapper)

    @Test
    fun `de instellingen gaan mee, maar zonder wachtwoorden`() {
        val settings = WorkflowSettings(
            ftpHost = "ftp.example.org", ftpUser = "hkh", ftpPassword = "ftp-geheim",
            imagesAnswers = listOf(AnswerRule("Kies 1,2,3", "6"), AnswerRule("FTP wachtwoord", "antwoord-geheim", secret = true)),
        )
        val cleaned = sanitizer.clean(settings)
        val json = cleaned.tree.toString()
        assertEquals("ftp.example.org", cleaned.tree.path("ftpHost").asString())
        assertEquals(SnapshotSanitizer.REDACTED, cleaned.tree.path("ftpPassword").asString())
        assertFalse("ftp-geheim" in json)
        assertFalse("antwoord-geheim" in json)
        assertEquals("6", cleaned.tree.path("imagesAnswers").path(0).path("answer").asString())
    }

    @Test
    fun `een geheim dat in de uitvoer van een programma staat, wordt eruit gehaald`() {
        val cleaned = sanitizer.clean(WorkflowSettings(ftpPassword = "ftp-geheim"))
        assertEquals("FTP wachtwoord: *** gevraagd", cleaned.scrub("FTP wachtwoord: ftp-geheim gevraagd"))
        // Een leeg of heel kort geheim vervangt niets (anders verdwijnt elke letter uit de tekst).
        assertEquals("abc", sanitizer.clean(WorkflowSettings(ftpPassword = "")).scrub("abc"))
        assertEquals("abc", sanitizer.clean(WorkflowSettings(ftpPassword = "a")).scrub("abc"))
    }
}
