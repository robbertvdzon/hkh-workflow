package nl.vdzon.hkhworkflow.testrun

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import nl.vdzon.hkhworkflow.exerunner.ExeRunner
import nl.vdzon.hkhworkflow.exerunner.RunMode
import nl.vdzon.hkhworkflow.settings.SettingsService
import nl.vdzon.hkhworkflow.settings.WorkflowProperties
import nl.vdzon.hkhworkflow.settings.WorkflowSettings
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import tools.jackson.databind.ObjectMapper

class TestRunServiceTest {
    @TempDir
    lateinit var dir: Path

    private fun service(): TestRunService {
        val test = Files.createDirectories(dir.resolve("test"))
        val archived = dir.resolve("archived")
        Files.writeString(test.resolve("xx02.pdf"), "b")
        Files.writeString(test.resolve("xx01.pdf"), "a")
        Files.writeString(test.resolve("notes.txt"), "ignored")
        val settings = SettingsService(ObjectMapper(), WorkflowProperties(settingsFile = dir.resolve("s.json").toString()))
        settings.save(WorkflowSettings(test.toString(), archived.toString(), "", ""))
        return TestRunService(settings, ExeRunner("UTF-8", dir.resolve("logs").toString()))
    }

    @Test
    fun `creates steps per type and file in order`() {
        val service = service()
        service.start("0100")
        val view = service.view()!!
        assertEquals(listOf("0100.pdf", "0101.pdf"), view.rows.map { it.newName })
        assertEquals(listOf("xx01.pdf", "xx02.pdf"), view.rows.map { it.originalName })
        assertEquals(8, view.totalCount)
        assertEquals("RENAME", view.next!!.type)
        assertEquals("Hernoem xx01.pdf naar 0100.pdf?", view.next!!.question)
    }

    @Test
    fun `rename copies to work folder and archive moves the original`() {
        val service = service()
        service.start("7")
        val first = service.view()!!.next!!
        service.execute(first.id, RunMode.IN_APP)
        assertTrue(Files.exists(dir.resolve("test/werk/7.pdf")))
        assertTrue(Files.exists(dir.resolve("test/xx01.pdf")), "origineel blijft staan")

        // Sla de rest over tot de archiveerstappen.
        while (service.view()!!.next!!.type != "ARCHIVE") service.skip(service.view()!!.next!!.id)
        service.execute(service.view()!!.next!!.id, RunMode.IN_APP)
        assertFalse(Files.exists(dir.resolve("test/xx01.pdf")))
        assertTrue(Files.exists(dir.resolve("archived/xx01.pdf")))
    }

    @Test
    fun `only the next step may run`() {
        val service = service()
        service.start("1")
        val later = service.view()!!.rows[1].cells[0]
        assertThrows<IllegalStateException> { service.execute(later.id, RunMode.IN_APP) }
    }

    @Test
    fun `a new test replaces the previous one without reset`() {
        val service = service()
        service.start("1")
        service.execute(service.view()!!.next!!.id, RunMode.IN_APP)
        service.start("5")
        val view = service.view()!!
        assertEquals("5", view.startNumber)
        assertEquals(0, view.doneCount)
    }
}
