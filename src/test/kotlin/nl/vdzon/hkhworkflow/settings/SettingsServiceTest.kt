package nl.vdzon.hkhworkflow.settings

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
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
        assertEquals(service.withEnvironment(WorkflowSettings(testFolder = "C:\\test", imagesExe = "C:\\images.exe", pdf2zcbsExe = "C:\\pdf2zcbs.exe")), reloaded)
    }

    @Test
    fun `de keuze voor het ftp-wachtwoord in het logboek blijft bewaard`() {
        val file = dir.resolve("settings.json")
        val service = SettingsService(ObjectMapper(), WorkflowProperties(settingsFile = file.toString()))
        assertEquals(true, service.get().logFtpPassword)
        service.save(WorkflowSettings(logFtpPassword = false))
        val reloaded = SettingsService(ObjectMapper(), WorkflowProperties(settingsFile = file.toString())).get()
        assertEquals(false, reloaded.logFtpPassword)
    }

    @Test
    fun `missing file gives empty settings and warnings`() {
        val service = SettingsService(ObjectMapper(), WorkflowProperties(settingsFile = dir.resolve("none.json").toString()))
        val settings = service.get()
        assertEquals(service.withEnvironment(WorkflowSettings()), settings)
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
    fun `saves and reloads the backup, source and ftp settings`() {
        val file = dir.resolve("settings.json")
        val service = SettingsService(ObjectMapper(), WorkflowProperties(settingsFile = file.toString()))
        val settings = service.withEnvironment(
            WorkflowSettings(
                sourceFolders = " Jelly=K:\\scans jelly \n\nRia=K:\\scans ria\n",
            ).withBackups(pct = "K:\\backup_100pct", pdf = "K:\\backup_invoer_org").withFtpPassword("geheim"),
        )
        service.save(settings)

        val reloaded = SettingsService(ObjectMapper(), WorkflowProperties(settingsFile = file.toString())).get()
        assertEquals(settings.copy(sourceFolders = "Jelly=K:\\scans jelly\nRia=K:\\scans ria"), reloaded)
        assertEquals(listOf(SourceRoot("Jelly", "K:\\scans jelly"), SourceRoot("Ria", "K:\\scans ria")), reloaded.sourceRoots())
    }

    @Test
    fun `de keuze voor de test-ZCBS bepaalt het adres van ZCBS en de ftp-server, de rest staat vast`() {
        val props = WorkflowProperties(settingsFile = dir.resolve("env.json").toString(), testZcbsUrl = "http://192.168.178.144:8081/", testFtpHost = "192.168.178.144", testFtpPort = 2121)
        val service = SettingsService(ObjectMapper(), props)

        val test = service.withEnvironment(WorkflowSettings(useTestZcbs = true, ftpUser = "iemand", ftpBaseDir = "/elders", collection = "beeldbank"))
        assertEquals("http://192.168.178.144:8081", test.zcbsBaseUrl)
        assertEquals("192.168.178.144", test.ftpHost)
        assertEquals(2121, test.ftpPort)
        assertEquals("ZCBS@historischekringheemskerk.nl", test.ftpUser)
        assertEquals("/public_html", test.ftpBaseDir)
        assertEquals("archief", test.collection)
        assertTrue(test.zcbsIsLocal())

        val real = service.withEnvironment(WorkflowSettings(useTestZcbs = false))
        assertEquals("https://www.historischekringheemskerk.nl", real.zcbsBaseUrl)
        assertEquals("ftp.historischekringheemskerk.nl", real.ftpHost)
        assertEquals(21, real.ftpPort)
        assertFalse(real.zcbsIsLocal())
    }

    @Test
    fun `een nieuwe installatie werkt met de test-ZCBS en een oude instelling wijst nooit ineens naar het echte archief`() {
        fun load(name: String, json: String): WorkflowSettings {
            val file = dir.resolve(name)
            Files.writeString(file, json)
            return SettingsService(ObjectMapper(), WorkflowProperties(settingsFile = file.toString())).get()
        }
        assertTrue(SettingsService(ObjectMapper(), WorkflowProperties(settingsFile = dir.resolve("bestaat-niet.json").toString())).get().useTestZcbs)
        assertTrue(load("a.json", """{"zcbsBaseUrl": "http://localhost:8081"}""").useTestZcbs)
        assertTrue(load("b.json", """{"zcbsBaseUrl": "http://192.168.178.144:8081"}""").useTestZcbs)
        assertTrue(load("c.json", """{}""").useTestZcbs)
        val real = load("d.json", """{"zcbsBaseUrl": "https://www.historischekringheemskerk.nl"}""")
        assertFalse(real.useTestZcbs)
        assertEquals("ftp.historischekringheemskerk.nl", real.ftpHost)
        assertFalse(load("e.json", """{"useTestZcbs": false}""").useTestZcbs)
        assertTrue(load("f.json", """{"useTestZcbs": true, "zcbsBaseUrl": "https://www.historischekringheemskerk.nl"}""").useTestZcbs, "de keuze gaat voor")
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
    fun `een instellingenbestand uit de tijd van sftp neemt het wachtwoord over, de server volgt de keuze`() {
        val file = dir.resolve("sftp.json")
        Files.writeString(
            file,
            """{"sftpHost": "ftp.example.nl", "sftpPort": 22, "sftpUser": "hkh", "sftpPassword": "geheim", "sftpHostKey": "SHA256:abc",
               "sftpBaseDir": "/domains/x/public_html", "sftpOcrDir": "o", "sftpPdfDir": "p"}""",
        )
        val loaded = SettingsService(ObjectMapper(), WorkflowProperties(settingsFile = file.toString())).get()
        assertEquals("localhost", loaded.ftpHost, "de server komt uit de keuze, niet uit het oude bestand")
        assertEquals(21, loaded.ftpPort)
        assertEquals("ZCBS@historischekringheemskerk.nl", loaded.ftpUser)
        assertEquals("geheim", loaded.ftpPassword)
        assertEquals("/public_html", loaded.ftpBaseDir)
    }

    @Test
    fun `een oud bestand met antwoorden voor images geeft de resolutie, het wachtwoord en de back-upmappen aan het archief`() {
        val file = dir.resolve("oud-antwoorden.json")
        Files.writeString(
            file,
            """{"backup100pctFolder": "K:\\a", "backupPdfFolder": "K:\\b",
               "pdf2zcbsAnswers": [{"pattern": "Deze PDF splitsen", "answer": "n", "secret": false}],
               "imagesAnswers": [{"pattern": "Kies 1,2,3", "answer": "7", "secret": false}, {"pattern": "FTP wachtwoord", "answer": "pw", "secret": true}]}""",
        )
        val loaded = SettingsService(ObjectMapper(), WorkflowProperties(settingsFile = file.toString())).get()
        assertEquals(CollectionSettings("7", "K:\\a", "K:\\b"), loaded.collectionSettings("archief"))
        assertEquals("pw", loaded.ftpPassword)
        assertEquals(CollectionSettings(), loaded.collectionSettings("beeldbank"), "een collectie zonder eigen instellingen krijgt de standaardwaarden")
        assertEquals("6", loaded.collectionSettings("beeldbank").imagesResolution)
    }

    @Test
    fun `de instellingen per collectie worden opgeslagen en een ongeldige resolutie wordt de standaard`() {
        val file = dir.resolve("per-collectie.json")
        val service = SettingsService(ObjectMapper(), WorkflowProperties(settingsFile = file.toString()))
        service.save(
            service.get()
                .withCollection("archief") { it.copy(imagesResolution = "6", backup100pctFolder = " K:\\arch100 ", backupPdfFolder = "K:\\archpdf") }
                .withCollection("beeldbank") { it.copy(imagesResolution = "2", backup100pctFolder = "K:\\beeld100") }
                .withCollection("objecten") { it.copy(imagesResolution = "9") },
        )
        val reloaded = SettingsService(ObjectMapper(), WorkflowProperties(settingsFile = file.toString())).get()
        assertEquals(CollectionSettings("6", "K:\\arch100", "K:\\archpdf"), reloaded.collectionSettings("archief"))
        assertEquals(CollectionSettings("2", "K:\\beeld100", ""), reloaded.collectionSettings("beeldbank"))
        assertEquals("6", reloaded.collectionSettings("objecten").imagesResolution, "9 bestaat niet")
        assertEquals("K:\\arch100", reloaded.backup100pctFolder(), "de collectie waar de app mee werkt")
        assertEquals("6", reloaded.imagesResolution())
    }

    @Test
    fun `remote folders are the collection under the base folder`() {
        val s = WorkflowSettings(collection = "archief", ftpBaseDir = "/domains/historischekringheemskerk.nl/public_html/")
        assertEquals("/domains/historischekringheemskerk.nl/public_html/archief/ocr", s.remoteOcrDir())
        assertEquals("/domains/historischekringheemskerk.nl/public_html/archief/pdf", s.remotePdfDir())
        assertEquals("/domains/historischekringheemskerk.nl/public_html/cgi-bin/archief/100pct", s.remoteImagesDir("100pct"))
    }

    @Test
    fun `het ftp-wachtwoord is een eigen instelling, ook uit een oud bestand`() {
        val file = dir.resolve("wachtwoord.json")
        val service = SettingsService(ObjectMapper(), WorkflowProperties(settingsFile = file.toString()))
        assertEquals("", service.get().ftpPassword)
        service.save(service.get().withFtpPassword("geheim"))
        assertEquals("geheim", SettingsService(ObjectMapper(), WorkflowProperties(settingsFile = file.toString())).get().ftpPassword)
        fun load(name: String, json: String): String {
            val f = dir.resolve(name)
            Files.writeString(f, json)
            return SettingsService(ObjectMapper(), WorkflowProperties(settingsFile = f.toString())).get().ftpPassword
        }
        assertEquals("oud", load("a.json", """{"ftpPassword": "oud"}"""))
        assertEquals("sftp", load("b.json", """{"sftpPassword": "sftp"}"""))
        assertEquals("antwoord", load("c.json", """{"imagesAnswers": [{"pattern": "FTP wachtwoord", "answer": "antwoord", "secret": true}]}"""))
        assertEquals("eigen", load("d.json", """{"ftpPassword": "eigen", "imagesAnswers": [{"pattern": "FTP wachtwoord", "answer": "antwoord", "secret": true}]}"""), "de eigen instelling gaat voor")
    }

    @Test
    fun `the password never shows up in toString`() {
        val text = WorkflowSettings().withFtpPassword("geheimwoord").toString()
        assertFalse(text.contains("geheimwoord"), text)
        assertFalse(WorkflowProperties(adminPassword = "beheerwoord").toString().contains("beheerwoord"))
    }

    @Test
    fun `extras warn about missing backup folder and ftp settings`() {
        val service = SettingsService(ObjectMapper(), WorkflowProperties(settingsFile = dir.resolve("x.json").toString()))
        val problems = service.checkExtras(WorkflowSettings())
        assertEquals(setOf("archief.backup100pctFolder", "archief.backupPdfFolder", "sourceFolders", "ftpPassword"), problems.keys)
    }

    @Test
    fun `opslaan meldt een wijziging, maar een gelijke opslag en een fout bij het melden niet`() {
        val events = mutableListOf<SettingsChanged>()
        val publisher = org.springframework.context.ApplicationEventPublisher { e -> if (e is SettingsChanged) events += e }
        val service = SettingsService(ObjectMapper(), WorkflowProperties(settingsFile = dir.resolve("s.json").toString()), publisher)

        service.save(service.get().copy(testFolder = "C:\\invoer"), reason = "Test", by = "carla", address = "10.0.0.5")
        service.save(service.get()) // niets veranderd

        val event = events.single()
        assertEquals("", event.before.testFolder)
        assertEquals("C:\\invoer", event.after.testFolder)
        assertEquals(Triple("Test", "carla", "10.0.0.5"), Triple(event.reason, event.by, event.address))

        val failing = SettingsService(ObjectMapper(), WorkflowProperties(settingsFile = dir.resolve("t.json").toString())) { error("stuk") }
        assertEquals("C:\\x", failing.save(failing.get().copy(testFolder = "C:\\x")).testFolder, "een mislukte melding houdt het opslaan niet tegen")
    }
}
