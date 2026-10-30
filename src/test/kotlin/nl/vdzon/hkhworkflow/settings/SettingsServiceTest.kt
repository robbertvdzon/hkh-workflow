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
    fun `saves and reloads the collection, backup and ftp settings`() {
        val file = dir.resolve("settings.json")
        val service = SettingsService(ObjectMapper(), WorkflowProperties(settingsFile = file.toString()))
        val settings = WorkflowSettings(
            collection = " archieftest ", sourceFolders = " Jelly=K:\\scans jelly \n\nRia=K:\\scans ria\n",
            backup100pctFolder = "K:\\backup_100pct", backupPdfFolder = "K:\\backup_invoer_org", ftpHost = "ftp.example.nl", ftpPort = 2121,
            ftpUser = "hkh", ftpPassword = "geheim", ftpBaseDir = "/domains/x/public_html",
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
        assertEquals(21, loaded.ftpPort)
        assertEquals("/public_html", loaded.ftpBaseDir)
    }

    @Test
    fun `een instellingenbestand uit de tijd van sftp gebruikt dezelfde gegevens voor ftp`() {
        val file = dir.resolve("sftp.json")
        Files.writeString(
            file,
            """{"sftpHost": "ftp.example.nl", "sftpPort": 22, "sftpUser": "hkh", "sftpPassword": "geheim", "sftpHostKey": "SHA256:abc",
               "sftpBaseDir": "/domains/x/public_html", "sftpOcrDir": "o", "sftpPdfDir": "p"}""",
        )
        val loaded = SettingsService(ObjectMapper(), WorkflowProperties(settingsFile = file.toString())).get()
        assertEquals("ftp.example.nl", loaded.ftpHost)
        assertEquals(21, loaded.ftpPort, "de sftp-poort 22 hoort niet bij ftp")
        assertEquals("hkh", loaded.ftpUser)
        assertEquals("geheim", loaded.ftpPassword)
        assertEquals("/domains/x/public_html", loaded.ftpBaseDir)
        assertEquals("o", loaded.ftpOcrDir)
        assertEquals("p", loaded.ftpPdfDir)

        // een nieuw bestand gaat voor
        val both = dir.resolve("beide.json")
        Files.writeString(both, """{"ftpHost": "nieuw.example.nl", "sftpHost": "oud.example.nl"}""")
        assertEquals("nieuw.example.nl", SettingsService(ObjectMapper(), WorkflowProperties(settingsFile = both.toString())).get().ftpHost)
    }

    @Test
    fun `een oude lijst met antwoorden voor images krijgt de nieuwe standaardvragen erbij`() {
        val file = dir.resolve("oud-antwoorden.json")
        Files.writeString(file, """{"imagesAnswers": [{"pattern": "Kies 1,2,3", "answer": "7", "secret": false}]}""")
        val rules = SettingsService(ObjectMapper(), WorkflowProperties(settingsFile = file.toString())).get().imagesAnswers
        assertEquals("7", rules.first { it.pattern == "Kies 1,2,3" }.answer, "wat er stond blijft zoals het was")
        assertEquals("", rules.first { it.pattern == "Geef een ENTER/RETURN" }.answer, "de vraag om een submap is er nieuw bij")
        assertEquals(1, rules.count { it.pattern == "Kies 1,2,3" })
    }

    @Test
    fun `remote folders default to the collection under the base folder and can be overridden`() {
        val s = WorkflowSettings(collection = "archief", ftpBaseDir = "/domains/historischekringheemskerk.nl/public_html/")
        assertEquals("/domains/historischekringheemskerk.nl/public_html/archief/ocr", s.remoteOcrDir())
        assertEquals("/domains/historischekringheemskerk.nl/public_html/archief/pdf", s.remotePdfDir())

        val custom = s.copy(ftpOcrDir = "documenten/ocr", ftpPdfDir = "/andere/wortel/pdf/")
        assertEquals("/domains/historischekringheemskerk.nl/public_html/documenten/ocr", custom.remoteOcrDir())
        assertEquals("/andere/wortel/pdf", custom.remotePdfDir())
    }

    @Test
    fun `the password never shows up in toString`() {
        val text = WorkflowSettings(ftpPassword = "geheimwoord").toString()
        assertFalse(text.contains("geheimwoord"), text)
        assertFalse(WorkflowProperties(adminPassword = "beheerwoord").toString().contains("beheerwoord"))
    }

    @Test
    fun `extras warn about missing backup folder and ftp settings`() {
        val service = SettingsService(ObjectMapper(), WorkflowProperties(settingsFile = dir.resolve("x.json").toString()))
        val problems = service.checkExtras(WorkflowSettings())
        assertEquals(setOf("backup100pctFolder", "backupPdfFolder", "sourceFolders", "zcbsBaseUrl", "ftpHost", "ftpUser", "ftpPassword"), problems.keys)
    }
}
