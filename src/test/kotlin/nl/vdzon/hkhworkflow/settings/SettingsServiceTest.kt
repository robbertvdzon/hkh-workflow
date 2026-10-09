package nl.vdzon.hkhworkflow.settings

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import tools.jackson.databind.ObjectMapper

class SettingsServiceTest {
    @TempDir
    lateinit var dir: Path

    @Test
    fun `saves and reloads settings from the json file`() {
        val file = dir.resolve("settings.json")
        val service = SettingsService(ObjectMapper(), WorkflowProperties(settingsFile = file.toString()))
        service.save(WorkflowSettings(" C:\\test ", "C:\\archived", "C:\\images.exe", "C:\\pdf2zcbs.exe"))

        val reloaded = SettingsService(ObjectMapper(), WorkflowProperties(settingsFile = file.toString())).get()
        assertEquals(WorkflowSettings("C:\\test", "C:\\archived", "C:\\images.exe", "C:\\pdf2zcbs.exe"), reloaded)
    }

    @Test
    fun `missing file gives empty settings and warnings`() {
        val service = SettingsService(ObjectMapper(), WorkflowProperties(settingsFile = dir.resolve("none.json").toString()))
        val settings = service.get()
        assertEquals(WorkflowSettings(), settings)
        assertEquals(4, service.check(settings).size)
    }

    @Test
    fun `check accepts existing folders and files`() {
        val exe = Files.createFile(dir.resolve("images.exe"))
        exe.toFile().setExecutable(true)
        val service = SettingsService(ObjectMapper(), WorkflowProperties(settingsFile = dir.resolve("s.json").toString()))
        val problems = service.check(WorkflowSettings(dir.toString(), dir.toString(), exe.toString(), exe.toString()))
        assertEquals(emptyMap(), problems)
    }
}
