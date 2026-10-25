package nl.vdzon.hkhworkflow.settings

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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

    @Test
    fun `saves and reloads the collection, backup and sftp settings`() {
        val file = dir.resolve("settings.json")
        val service = SettingsService(ObjectMapper(), WorkflowProperties(settingsFile = file.toString()))
        val settings = WorkflowSettings(
            collection = " archieftest ", imagesFileType = "a", imagesResolution = "6", imagesInstitution = "4",
            backup100pctFolder = "C:\\zcbs\\backup_100pct", sftpHost = "sftp.example.nl", sftpPort = 2222,
            sftpUser = "hkh", sftpPassword = "geheim", sftpHostKey = "SHA256:abc", sftpBaseDir = "/domains/x/public_html",
        )
        service.save(settings)

        val reloaded = SettingsService(ObjectMapper(), WorkflowProperties(settingsFile = file.toString())).get()
        assertEquals(settings.copy(collection = "archieftest"), reloaded)
    }

    @Test
    fun `an old settings file without the new fields still loads with safe defaults`() {
        val file = dir.resolve("old.json")
        Files.writeString(file, """{"testFolder": "C:\\test", "archivedFolder": "C:\\archived"}""")
        val loaded = SettingsService(ObjectMapper(), WorkflowProperties(settingsFile = file.toString())).get()
        assertEquals("archief", loaded.collection)
        assertEquals("a", loaded.imagesFileType)
        assertEquals("", loaded.sftpHostKey)
        assertEquals(22, loaded.sftpPort)
    }

    @Test
    fun `remote folders default to the collection under the base folder and can be overridden`() {
        val s = WorkflowSettings(collection = "archief", sftpBaseDir = "/domains/historischekringheemskerk.nl/public_html/")
        assertEquals("/domains/historischekringheemskerk.nl/public_html/archief/ocr", s.remoteOcrDir())
        assertEquals("/domains/historischekringheemskerk.nl/public_html/archief/pdf", s.remotePdfDir())

        val custom = s.copy(sftpOcrDir = "documenten/ocr", sftpPdfDir = "/andere/wortel/pdf/")
        assertEquals("/domains/historischekringheemskerk.nl/public_html/documenten/ocr", custom.remoteOcrDir())
        assertEquals("/andere/wortel/pdf", custom.remotePdfDir())
    }

    @Test
    fun `the password never shows up in toString`() {
        val text = WorkflowSettings(sftpPassword = "geheimwoord").toString()
        assertFalse(text.contains("geheimwoord"), text)
    }

    @Test
    fun `extras warn about missing backup folder and sftp settings`() {
        val service = SettingsService(ObjectMapper(), WorkflowProperties(settingsFile = dir.resolve("x.json").toString()))
        val problems = service.checkExtras(WorkflowSettings())
        assertEquals(setOf("backup100pctFolder", "zcbsBaseUrl", "sftpHost", "sftpUser", "sftpPassword", "sftpHostKey"), problems.keys)
    }
}
