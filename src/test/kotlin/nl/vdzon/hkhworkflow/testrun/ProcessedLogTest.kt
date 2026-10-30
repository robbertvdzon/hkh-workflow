package nl.vdzon.hkhworkflow.testrun

import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import nl.vdzon.hkhworkflow.settings.SettingsService
import nl.vdzon.hkhworkflow.settings.WorkflowProperties
import org.junit.jupiter.api.io.TempDir
import tools.jackson.databind.ObjectMapper

class ProcessedLogTest {
    @TempDir
    lateinit var dir: Path

    private fun log(processedLog: String = "verwerkt.tsv"): ProcessedLog {
        val props = WorkflowProperties(settingsFile = dir.resolve("config/s.json").toString(), processedLog = processedLog)
        return ProcessedLog(SettingsService(ObjectMapper(), props), props)
    }

    private fun entry(run: String, number: String, name: String, at: LocalDateTime = LocalDateTime.of(2026, 10, 30, 9, 0)) =
        ProcessedFile(run, at, "Carla", "archief", number, "Ria", "K:\\scans ria\\map 57", name)

    @Test
    fun `een nieuw log is leeg en bestanden worden achter elkaar toegevoegd`() {
        val log = log()
        assertEquals(emptyList(), log.all())
        log.add(listOf(entry("r1", "11935", "a.pdf"), entry("r1", "11936", "b.pdf")))
        log.add(listOf(entry("r2", "11937", "c.pdf", LocalDateTime.of(2026, 11, 2, 14, 30))))

        val all = log.all()
        assertEquals(listOf("11935", "11936", "11937"), all.map { it.number })
        assertEquals("K:\\scans ria\\map 57", all[0].folder)
        assertEquals(listOf("r2", "r1"), log.runs().map { it.runId }, "nieuwste eerst")
        assertEquals("11935", log.runs()[1].first)
        assertEquals("11936", log.runs()[1].last)
    }

    @Test
    fun `zonder pad staat het log naast het instellingenbestand`() {
        val log = log()
        log.add(listOf(entry("r1", "1", "a.pdf")))
        assertTrue(Files.exists(dir.resolve("config/verwerkt.tsv")))
    }

    @Test
    fun `tabs en regeleinden in een naam breken het log niet`() {
        val log = log()
        log.add(listOf(entry("r1", "1", "rare\tnaam\nmet tab.pdf")))
        assertEquals("rare naam met tab.pdf", log.all().single().name)
    }

    @Test
    fun `een kapotte regel wordt overgeslagen`() {
        val log = log()
        log.add(listOf(entry("r1", "1", "a.pdf")))
        Files.writeString(log.file(), "rommel zonder tabs\n", java.nio.file.StandardOpenOption.APPEND)
        log.add(listOf(entry("r2", "2", "b.pdf")))
        assertEquals(listOf("1", "2"), log.all().map { it.number })
    }

    @Test
    fun `de index vindt een bestand op map en naam`() {
        val log = log()
        log.add(listOf(entry("r1", "11935", "a.pdf")))
        assertEquals("11935", log.index()["K:\\scans ria\\map 57" to "a.pdf"]?.number)
        assertEquals(null, log.index()["K:\\scans ria\\map 58" to "a.pdf"])
    }
}
