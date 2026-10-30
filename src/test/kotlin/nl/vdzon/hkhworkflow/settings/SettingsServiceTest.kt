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
        service.save(WorkflowSettings(testFolder = " C:\\test ", imagesExe = "C:\\images.exe", pdf2zcbsExe = "C:\\pdf2zcbs.exe"))

        val reloaded = SettingsService(ObjectMapper(), WorkflowProperties(settingsFile = file.toString())).get()
        assertEquals(WorkflowSettings(testFolder = "C:\\test", imagesExe = "C:\\images.exe", pdf2zcbsExe = "C:\\pdf2zcbs.exe"), reloaded)
    }

    @Test
    fun `missing file gives empty settings and warnings`() {
        val service = SettingsService(ObjectMapper(), WorkflowProperties(settingsFile = dir.resolve("none.json").toString()))
        val settings = service.get()
        assertEquals(WorkflowSettings(), settings)
        assertEquals(3, service.check(settings).size)
    }

    @Test
    fun `check accepts existing folders and files`() {
        val exe = Files.createFile(dir.resolve("images.exe"))
        exe.toFile().setExecutable(true)
        val service = SettingsService(ObjectMapper(), WorkflowProperties(settingsFile = dir.resolve("s.json").toString()))
        val problems = service.check(WorkflowSettings(testFolder = dir.toString(), imagesExe = exe.toString(), pdf2zcbsExe = exe.toString()))
        assertEquals(emptyMap(), problems)
    }

    @Test
    fun `saves and reloads the collection, backup and sftp settings`() {
        val file = dir.resolve("settings.json")
        val service = SettingsService(ObjectMapper(), WorkflowProperties(settingsFile = file.toString()))
        val settings = WorkflowSettings(
            collection = " archieftest ", sourceFolders = " Jelly=K:\\scans jelly \n\nRia=K:\\scans ria\n",
            backup100pctFolder = "K:\\backup_100pct", backupPdfFolder = "K:\\backup_invoer_org", sftpHost = "sftp.example.nl", sftpPort = 2222,
            sftpUser = "hkh", sftpPassword = "geheim", sftpHostKey = "SHA256:abc", sftpBaseDir = "/domains/x/public_html",
        )
        service.save(settings)

        val reloaded = SettingsService(ObjectMapper(), WorkflowProperties(settingsFile = file.toString())).get()
        assertEquals(settings.copy(collection = "archieftest", sourceFolders = "Jelly=K:\\scans jelly\nRia=K:\\scans ria"), reloaded)
        assertEquals(listOf(SourceRoot("Jelly", "K:\\scans jelly"), SourceRoot("Ria", "K:\\scans ria")), reloaded.sourceRoots())
    }

    @Test
    fun `regels zonder naam of pad tellen niet mee als map met scans`() {
        assertEquals(listOf(SourceRoot("Ria", "K:\\scans ria")), WorkflowSettings(sourceFolders = "los pad\n=K:\\x\nleeg=\nRia = K:\\scans ria").sourceRoots())
    }

    @Test
    fun `an old settings file without the new fields still loads with safe defaults`() {
        val file = dir.resolve("old.json")
        Files.writeString(file, """{"testFolder": "C:\\test", "archivedFolder": "C:\\archived", "imagesFileType": "a", "sourceFolder": "K:\\oud"}""")
        val loaded = SettingsService(ObjectMapper(), WorkflowProperties(settingsFile = file.toString())).get()
        assertEquals("archief", loaded.collection)
        assertEquals("C:\\test", loaded.testFolder)
        assertEquals("", loaded.sourceFolders)
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
        assertFalse(WorkflowProperties(adminPassword = "beheerwoord").toString().contains("beheerwoord"))
    }

    @Test
    fun `extras warn about missing backup folder and sftp settings`() {
        val service = SettingsService(ObjectMapper(), WorkflowProperties(settingsFile = dir.resolve("x.json").toString()))
        val problems = service.checkExtras(WorkflowSettings())
        assertEquals(setOf("backup100pctFolder", "backupPdfFolder", "sourceFolders", "zcbsBaseUrl", "sftpHost", "sftpUser", "sftpPassword", "sftpHostKey"), problems.keys)
    }
}
