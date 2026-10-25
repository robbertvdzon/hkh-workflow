package nl.vdzon.hkhworkflow.testrun

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import nl.vdzon.hkhworkflow.settings.SettingsService
import nl.vdzon.hkhworkflow.settings.WorkflowProperties
import nl.vdzon.hkhworkflow.settings.WorkflowSettings
import org.junit.jupiter.api.io.TempDir
import tools.jackson.databind.ObjectMapper

class SourceFilesServiceTest {
    @TempDir
    lateinit var dir: Path

    private fun service(): SourceFilesService {
        val source = Files.createDirectories(dir.resolve("scabslelly"))
        Files.createDirectories(source.resolve("Ria/map57"))
        Files.createDirectories(source.resolve("Kees/raadhuis"))
        Files.writeString(source.resolve("Ria/map57/scan_0412.pdf"), "ria 1")
        Files.writeString(source.resolve("Ria/map57/scan_0413.pdf"), "ria 2")
        Files.writeString(source.resolve("Ria/map57/notitie.txt"), "geen pdf")
        Files.writeString(source.resolve("Kees/raadhuis/voorgevel.pdf"), "kees")
        Files.writeString(source.resolve("los.pdf"), "zonder persoon")
        val inbox = Files.createDirectories(dir.resolve("invoer"))
        val settings = SettingsService(ObjectMapper(), WorkflowProperties(settingsFile = dir.resolve("s.json").toString()))
        settings.save(WorkflowSettings(testFolder = inbox.toString(), sourceFolder = source.toString()))
        return SourceFilesService(settings)
    }

    @Test
    fun `de pdfs van de server staan per persoon en map in de lijst`() {
        val list = service().candidates()
        assertEquals(listOf("los.pdf", "voorgevel.pdf", "scan_0412.pdf", "scan_0413.pdf"), list.map { it.name })
        assertEquals(listOf("", "Kees", "Ria", "Ria"), list.map { it.by })
        assertTrue(list.first { it.name == "voorgevel.pdf" }.folder.endsWith("Kees/raadhuis") || list.first { it.name == "voorgevel.pdf" }.folder.endsWith("Kees\\raadhuis"))
    }

    @Test
    fun `kopieren zet de bestanden in de testmap, onthoudt wie het scande en laat de originelen staan`() {
        val s = service()
        val wanted = s.candidates().filter { it.name.startsWith("scan_") }.map { it.relPath }
        val result = s.copy(wanted)
        assertEquals(listOf("scan_0412.pdf", "scan_0413.pdf"), result.copied)
        assertEquals(emptyList(), result.skipped)
        assertEquals(listOf("herkomst.tsv", "scan_0412.pdf", "scan_0413.pdf"), Files.list(dir.resolve("invoer")).use { it.map { p -> p.fileName.toString() }.sorted().toList() })
        assertEquals("Ria", s.origin("scan_0412.pdf")?.by)
        assertTrue(Files.exists(dir.resolve("scabslelly/Ria/map57/scan_0412.pdf")))
        assertNull(s.origin("onbekend.pdf"))
    }

    @Test
    fun `een bestand dat al in de testmap staat wordt nooit overschreven`() {
        val s = service()
        Files.writeString(dir.resolve("invoer/scan_0412.pdf"), "EIGEN VERSIE")
        val result = s.copy(s.candidates().filter { it.name == "scan_0412.pdf" }.map { it.relPath })
        assertEquals(emptyList(), result.copied)
        assertEquals(1, result.skipped.size)
        assertEquals("EIGEN VERSIE", Files.readString(dir.resolve("invoer/scan_0412.pdf")))
    }

    @Test
    fun `alleen bestanden uit de lijst mogen gekopieerd worden`() {
        val s = service()
        assertFailsWith<IllegalArgumentException> { s.copy(listOf("../s.json")) }
        assertFailsWith<IllegalArgumentException> { s.copy(emptyList()) }
        assertFalse(Files.exists(dir.resolve("invoer/s.json")))
    }
}
