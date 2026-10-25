package nl.vdzon.hkhworkflow.testrun

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import nl.vdzon.hkhworkflow.exerunner.ExeRunner
import nl.vdzon.hkhworkflow.exerunner.RunMode
import nl.vdzon.hkhworkflow.items.EditStatus
import nl.vdzon.hkhworkflow.items.ItemEditService
import nl.vdzon.hkhworkflow.settings.AnswerRule
import nl.vdzon.hkhworkflow.settings.SettingsService
import nl.vdzon.hkhworkflow.settings.WorkflowProperties
import nl.vdzon.hkhworkflow.settings.WorkflowSettings
import nl.vdzon.hkhworkflow.sftp.SftpService
import nl.vdzon.hkhworkflow.sftp.TestSftpServer
import nl.vdzon.hkhworkflow.zcbs.FakeZcbs
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.condition.EnabledOnOs
import org.junit.jupiter.api.condition.OS
import org.junit.jupiter.api.io.TempDir
import tools.jackson.databind.ObjectMapper

/** De volledige stappenketen, met nep-programma's (shellscripts uit de testresources) en een sftp-server in het geheugen. */
@EnabledOnOs(OS.MAC, OS.LINUX)
class TestRunServiceTest {
    @TempDir
    lateinit var dir: Path

    private lateinit var inbox: Path
    private lateinit var archived: Path
    private lateinit var backup: Path
    private lateinit var p2zDir: Path
    private lateinit var imgDir: Path
    private lateinit var serverRoot: Path
    private lateinit var server: TestSftpServer
    private lateinit var settings: SettingsService
    private lateinit var service: TestRunService
    private lateinit var items: ItemEditService
    private lateinit var zcbs: FakeZcbs

    private fun fake(name: String, into: Path, asName: String): Path {
        Files.createDirectories(into)
        val target = into.resolve(asName)
        javaClass.getResourceAsStream("/fake-exes/$name")!!.use { Files.copy(it, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING) }
        target.toFile().setExecutable(true)
        return target
    }

    @BeforeEach
    fun setUp() {
        zcbs = FakeZcbs()
        inbox = Files.createDirectories(dir.resolve("invoer"))
        archived = dir.resolve("backup_invoer_org")
        backup = Files.createDirectories(dir.resolve("backup_100pct"))
        p2zDir = dir.resolve("pdf2zcbs")
        imgDir = dir.resolve("images")
        Files.writeString(inbox.resolve("xx02.pdf"), "b")
        Files.writeString(inbox.resolve("xx01.pdf"), "a")
        Files.writeString(inbox.resolve("notes.txt"), "genegeerd")
        fake("pdf2zcbs.sh", p2zDir, "pdf2zcbs.sh")
        fake("images.sh", imgDir, "images.sh")

        serverRoot = Files.createDirectories(dir.resolve("server"))
        Files.createDirectories(serverRoot.resolve("public_html/archief/ocr"))
        Files.createDirectories(serverRoot.resolve("public_html/archief/pdf"))
        server = TestSftpServer(serverRoot, dir.resolve("hostkey.ser"))

        settings = SettingsService(ObjectMapper(), WorkflowProperties(settingsFile = dir.resolve("s.json").toString()))
        save { it }
        items = ItemEditService(settings)
        service = TestRunService(settings, ExeRunner("UTF-8", dir.resolve("logs").toString()), SftpService(settings), items, SourceFilesService(settings))
    }

    @AfterEach
    fun tearDown() {
        service.reset()
        server.close()
        zcbs.close()
    }

    private fun base() = WorkflowSettings(
        testFolder = inbox.toString(), archivedFolder = archived.toString(),
        imagesExe = imgDir.resolve("images.sh").toString(), pdf2zcbsExe = p2zDir.resolve("pdf2zcbs.sh").toString(),
        collection = "archief", backup100pctFolder = backup.toString(),
        sftpHost = "localhost", sftpPort = server.port, sftpUser = TestSftpServer.USER, sftpPassword = TestSftpServer.PASSWORD,
        sftpHostKey = server.fingerprint, sftpBaseDir = "/public_html", zcbsBaseUrl = zcbs.baseUrl,
    )

    private fun save(change: (WorkflowSettings) -> WorkflowSettings) {
        settings.save(change(if (this::service.isInitialized) settings.get() else base()))
    }

    // ---- hulpjes ----

    private fun next(): StepView = service.view()!!.next!!

    private fun stepView(id: Int): StepView = service.view()!!.rows.flatMap { it.cells }.first { it.id == id }

    /** Voert de volgende stap uit en wacht tot een programma klaar is. */
    private fun runNext(auto: Boolean = false, options: StepOptions = StepOptions()): StepView {
        val step = next()
        service.execute(step.id, RunMode.IN_APP, auto, options)
        val deadline = System.currentTimeMillis() + 15_000
        while (stepView(step.id).status == "RUNNING" && System.currentTimeMillis() < deadline) Thread.sleep(50)
        return stepView(step.id)
    }

    /** Voert stappen uit tot de volgende stap van het gegeven type aan de beurt is (die wordt zelf niet uitgevoerd). */
    private fun runUntil(type: String) {
        while (next().type != type) {
            // Records bewerken heeft een eigen test (met inloggen en een nep-ZCBS); hier slaan we de stap over.
            if (next().type == "EDIT") { service.skip(next().id); continue }
            val result = runNext()
            assertEquals("DONE", result.status, "${result.type}: ${result.message}")
        }
    }

    private fun filesIn(path: Path): List<String> =
        if (Files.isDirectory(path)) path.listDirectoryEntries().map { it.name }.sorted() else emptyList()

    private fun remote(path: String): Path = serverRoot.resolve(path.trimStart('/'))

    private fun states(row: Int, group: String): List<String> =
        service.view()!!.rows[row].checks.first { it.label == group }.items.map { it.state }

    // ---- opzet van de stappen ----

    @Test
    fun `creates one step per file for renaming and archiving and one per batch for the rest`() {
        service.start("0100")
        val view = service.view()!!
        assertEquals(listOf("0100.pdf", "0101.pdf"), view.rows.map { it.newName })
        assertEquals(listOf("xx01.pdf", "xx02.pdf"), view.rows.map { it.originalName })
        assertEquals(9, view.totalCount, "2 x hernoemen, pdf2zcbs, images, upload, records bewerken, opruimen, 2 x archiveren")
        assertEquals(listOf("Hernoemen", "pdf2zcbs.exe", "images.exe", "Upload (sftp)", "Records bewerken", "Opruimen", "Archiveren"), view.stepTypes)
        assertEquals("Hernoem xx01.pdf naar 0100.pdf?", view.next!!.question)
    }

    @Test
    fun `only the next step may run`() {
        service.start("1")
        val later = service.view()!!.rows[1].cells[0]
        assertThrows<IllegalStateException> { service.execute(later.id, RunMode.IN_APP) }
    }

    @Test
    fun `a new test replaces the previous one`() {
        service.start("1")
        runNext()
        service.start("5")
        val view = service.view()!!
        assertEquals("5", view.startNumber)
        assertEquals(0, view.doneCount)
    }

    // ---- de volledige keten ----

    @Test
    fun `the whole chain runs step by step and leaves everything in its place`() {
        service.start("90000000")

        runUntil("PDF2ZCBS")
        assertEquals(listOf("90000000.pdf", "90000001.pdf"), filesIn(inbox.resolve("werk")))
        assertEquals(listOf("notes.txt", "werk", "xx01.pdf", "xx02.pdf"), filesIn(inbox), "originelen blijven staan")
        assertTrue(next().plan.any { it.contains("één keer") }, "de stap toont vooraf wat hij gaat doen")

        // pdf2zcbs: één run, vier bestanden per pdf, en de vinkjes zeggen dat ze er zijn.
        assertEquals(listOf("PENDING", "PENDING", "PENDING", "PENDING"), states(0, "pdf2zcbs"))
        assertEquals("DONE", runNext().status)
        assertEquals(listOf("90000000.txt", "90000001.txt"), filesIn(p2zDir.resolve("output/ocr")))
        assertEquals(listOf("90000000.pdf", "90000001.pdf"), filesIn(p2zDir.resolve("output/pdf")))
        assertEquals(listOf("OK", "OK", "OK", "OK"), states(0, "pdf2zcbs"))

        // images: krijgt de large-jpg's van pdf2zcbs en geen pdf's.
        assertEquals("DONE", runNext().status)
        assertEquals(listOf("90000000.jpg", "90000001.jpg"), filesIn(imgDir.resolve("images")))
        assertEquals("large 90000000", Files.readString(imgDir.resolve("images/90000000.jpg")).trim())
        assertEquals(listOf("90000000.jpg", "90000001.jpg"), filesIn(imgDir.resolve("100pct")))
        assertEquals(listOf("OK", "OK", "OK"), states(1, "images"))

        // upload: tekst en pdf staan op de server, en de pagina weet dat.
        assertEquals("DONE", runNext().status)
        assertEquals("tekst van 90000000", Files.readString(remote("/public_html/archief/ocr/90000000.txt")).trim())
        assertTrue(Files.exists(remote("/public_html/archief/pdf/90000001.pdf")))
        assertEquals(listOf("OK", "OK"), states(0, "server"))

        // records bewerken: de stap kan pas als je bent ingelogd en de records bestaan; hij is klaar als alles is opgeslagen of op later staat.
        assertTrue(next().problems.any { it.contains("Log eerst in") }, next().problems.toString())
        zcbs.addRecord("edit-leeg.html", "90000000")
        zcbs.addRecord("edit-leeg.html", "90000001")
        assertEquals(null, items.login("Test Beheerder", "Beheer1"))
        val edit = next()
        assertEquals("EDIT", edit.type)
        service.execute(edit.id, RunMode.IN_APP)
        assertEquals("RUNNING", stepView(edit.id).status)
        assertEquals(listOf("PENDING"), states(0, "ZCBS"))
        assertTrue(items.open("90000000") is nl.vdzon.hkhworkflow.items.OpenOutcome.Ready)
        assertEquals(nl.vdzon.hkhworkflow.items.SaveOutcome.Saved, items.save("90000000", mapOf("a5" to listOf("Eerste titel"))))
        assertEquals("RUNNING", stepView(edit.id).status, "nog niet alle records zijn klaar")
        items.later("90000001")
        assertEquals("DONE", stepView(edit.id).status)
        assertEquals(listOf("OK"), states(0, "ZCBS"))
        assertEquals(EditStatus.LATER, items.statusOf("90000001"))

        // opruimen: eerst back-up, dan weg; de originelen staan er nog.
        assertEquals("DONE", runNext().status)
        assertEquals(listOf("90000000.jpg", "90000001.jpg"), filesIn(backup))
        assertFalse(Files.exists(p2zDir.resolve("output")))
        assertFalse(Files.exists(imgDir.resolve("small")))
        assertFalse(Files.exists(imgDir.resolve("100pct")))
        assertEquals(emptyList(), filesIn(imgDir.resolve("images")))
        assertEquals(listOf("images", "images.sh"), filesIn(imgDir), "het logbestand is weg; het programma en de lege invoermap blijven")
        assertTrue(Files.exists(inbox.resolve("xx01.pdf")))
        assertEquals(listOf("server", "ZCBS", "back-up"), service.view()!!.rows[0].checks.map { it.label })
        assertEquals(listOf("OK"), states(0, "back-up"))

        // archiveren: de originelen verhuizen naar de archived-map en alles is klaar.
        repeat(2) { assertEquals("DONE", runNext().status) }
        assertEquals(listOf("xx01.pdf", "xx02.pdf"), filesIn(archived))
        assertEquals(listOf("notes.txt", "werk"), filesIn(inbox))
        assertNull(service.view()!!.next)
    }

    // ---- een stap is pas klaar als de bestanden er zijn ----

    @Test
    fun `a program that exits 0 without making the files is a failure`() {
        fake("pdf2zcbs-broken.sh", p2zDir, "pdf2zcbs.sh")
        service.start("10")
        runUntil("PDF2ZCBS")

        val result = runNext()

        assertEquals("FAILED", result.status)
        assertTrue(result.message!!.contains("er ontbreken bestanden"), result.message)
        assertTrue(result.message!!.contains("small"), result.message)
        assertEquals(listOf("OK", "OK", "MISSING", "MISSING"), states(0, "pdf2zcbs"))
    }

    // ---- voorzichtigheid: niets starten of verwijderen als er iets niet klopt ----

    @Test
    fun `pdf2zcbs does not start while output holds results of another run`() {
        Files.createDirectories(p2zDir.resolve("output/ocr"))
        Files.writeString(p2zDir.resolve("output/ocr/555.txt"), "oud")
        service.start("10")
        runUntil("PDF2ZCBS")

        assertTrue(next().problems.any { it.contains("vorige run") && it.contains("555.txt") }, next().problems.toString())
        assertEquals("FAILED", runNext().status)
        assertEquals(listOf("555.txt"), filesIn(p2zDir.resolve("output/ocr")), "niets is gewist of toegevoegd")
        assertFalse(Files.exists(p2zDir.resolve("10.pdf")), "er is niets naar de map van het programma gekopieerd")
    }

    @Test
    fun `pdf2zcbs does not start while other pdfs wait in its folder`() {
        Files.writeString(p2zDir.resolve("777.pdf"), "van iemand anders")
        service.start("10")
        runUntil("PDF2ZCBS")
        assertTrue(next().problems.any { it.contains("andere pdf's") && it.contains("777.pdf") })
    }

    @Test
    fun `images does not start while old derived files wait`() {
        service.start("10")
        runUntil("PDF2ZCBS")
        assertEquals("DONE", runNext().status)
        Files.createDirectories(imgDir.resolve("small"))
        Files.writeString(imgDir.resolve("small/555.jpg"), "oud")

        assertTrue(next().problems.any { it.contains("vorige run") && it.contains("555.jpg") }, next().problems.toString())
        assertEquals("FAILED", runNext().status)
        assertEquals(emptyList(), filesIn(imgDir.resolve("images")), "er is niets naar images gekopieerd")
    }

    @Test
    fun `cleanup deletes nothing without a backup folder`() {
        service.start("10")
        runUntil("PDF2ZCBS")
        repeat(3) { assertEquals("DONE", runNext().status) }
        service.skip(next().id)
        save { it.copy(backup100pctFolder = "") }

        assertTrue(next().problems.any { it.contains("back-upmap") })
        assertEquals("FAILED", runNext().status)
        assertTrue(Files.exists(p2zDir.resolve("output/ocr/10.txt")), "de tekst is er nog")
        assertTrue(Files.exists(imgDir.resolve("100pct/10.jpg")))
    }

    @Test
    fun `cleanup warns when the upload was skipped`() {
        service.start("10")
        runUntil("PDF2ZCBS")
        repeat(2) { assertEquals("DONE", runNext().status) }
        service.skip(next().id)
        service.skip(next().id)
        assertTrue(next().warnings.any { it.contains("upload is overgeslagen") })
    }

    @Test
    fun `an upload never overwrites and can be retried after the clash is gone`() {
        Files.writeString(remote("/public_html/archief/pdf/11.pdf"), "ORIGINEEL")
        service.start("10")
        runUntil("PDF2ZCBS")
        repeat(2) { assertEquals("DONE", runNext().status) }

        val failed = runNext()
        assertEquals("FAILED", failed.status)
        assertTrue(failed.message!!.contains("Bestaat al op de server"), failed.message)
        assertEquals("ORIGINEEL", Files.readString(remote("/public_html/archief/pdf/11.pdf")))
        assertEquals(emptyList(), filesIn(remote("/public_html/archief/ocr")), "ook de rest is niet geüpload")
        assertTrue(Files.exists(p2zDir.resolve("output/ocr/10.txt")), "lokaal staat alles nog")

        Files.delete(remote("/public_html/archief/pdf/11.pdf"))
        service.retry(failed.id)
        assertEquals("DONE", runNext().status)
        assertEquals(listOf("10.txt", "11.txt"), filesIn(remote("/public_html/archief/ocr")))
    }

    // ---- de antwoorden die images.exe krijgt ----

    private fun useQuestionsImages() {
        fake("images-questions.sh", imgDir, "images.sh")
    }

    private fun answers(): Map<String, String> =
        Files.readAllLines(imgDir.resolve("answers.txt")).associate { it.substringBefore("=") to it.substringAfter("=") }

    @Test
    fun `automatic images answers are safe by default and follow the settings`() {
        useQuestionsImages()
        save { it.copy(collection = "archief", imagesFileType = "a", imagesResolution = "6") }
        service.start("10")
        runUntil("PDF2ZCBS")
        assertEquals("DONE", runNext().status)

        assertEquals("DONE", runNext(auto = true).status)

        assertEquals(
            mapOf("type" to "a", "resolutie" to "6", "upload" to "n", "records" to "n", "verwijdermappen" to "n", "verwijderbron" to "n"),
            answers(),
        )
    }

    @Test
    fun `upload and records are only answered yes when the user ticked them, and the collection comes from the settings`() {
        useQuestionsImages()
        save { it.copy(imagesAnswers = WorkflowSettings.DEFAULT_IMAGES_ANSWERS.map { r -> if (r.pattern == "FTP wachtwoord") AnswerRule("FTP wachtwoord", "geheimpje", true) else r }) }
        service.start("10")
        runUntil("PDF2ZCBS")
        assertEquals("DONE", runNext().status)

        assertEquals("DONE", runNext(auto = true, options = StepOptions(upload = true, records = true)).status)

        val given = answers()
        assertEquals("j", given["upload"])
        assertEquals("j", given["records"])
        assertEquals("4", given["instelling"])
        assertEquals("archief", given["collectie"], "de collectie uit de instellingen gaat voor de standaardregel")
        assertEquals("n", given["overschrijven"], "nooit overschrijven")
        assertEquals("n", given["verwijdermappen"])
        assertEquals("n", given["verwijderbron"])
    }

    @Test
    fun `records without upload and automatic upload without a password are refused`() {
        useQuestionsImages()
        service.start("10")
        runUntil("PDF2ZCBS")
        assertEquals("DONE", runNext().status)

        val noUpload = runNext(auto = true, options = StepOptions(upload = false, records = true))
        assertEquals("FAILED", noUpload.status)
        assertTrue(noUpload.message!!.contains("alleen samen met uploaden"), noUpload.message)

        service.retry(noUpload.id)
        val noPassword = runNext(auto = true, options = StepOptions(upload = true, records = false))
        assertEquals("FAILED", noPassword.status)
        assertTrue(noPassword.message!!.contains("FTP-wachtwoord"), noPassword.message)
        assertFalse(Files.exists(imgDir.resolve("answers.txt")), "het programma is niet gestart")
    }
}
