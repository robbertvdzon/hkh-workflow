package nl.vdzon.hkhworkflow.testrun

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import nl.vdzon.hkhworkflow.exerunner.ExeRunner
import nl.vdzon.hkhworkflow.exerunner.RunMode
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

/** De volledige stappenketen, met nep-programma's (shellscripts uit de testresources), een nep-ZCBS en een sftp-server in het geheugen. */
@EnabledOnOs(OS.MAC, OS.LINUX)
class TestRunServiceTest {
    @TempDir
    lateinit var dir: Path

    private lateinit var inbox: Path
    private lateinit var backupPdf: Path
    private lateinit var backup: Path
    private lateinit var p2zDir: Path
    private lateinit var imgDir: Path
    private lateinit var serverRoot: Path
    private lateinit var server: TestSftpServer
    private lateinit var settings: SettingsService
    private lateinit var service: TestRunService
    private lateinit var items: ItemEditService
    private lateinit var zcbs: FakeZcbs
    private lateinit var runner: ExeRunner
    private lateinit var processed: ProcessedLog
    private lateinit var input: InputFolder

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
        zcbs.numberWidth = 8
        inbox = Files.createDirectories(dir.resolve("invoer"))
        backupPdf = Files.createDirectories(dir.resolve("backup_invoer_org"))
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

        val properties = WorkflowProperties(settingsFile = dir.resolve("s.json").toString(), processedLog = dir.resolve("verwerkt.tsv").toString())
        settings = SettingsService(ObjectMapper(), properties)
        save { it }
        items = ItemEditService(settings)
        runner = ExeRunner("UTF-8", dir.resolve("logs").toString())
        processed = ProcessedLog(settings, properties)
        input = InputFolder(settings, items)
        service = TestRunService(settings, runner, SftpService(settings), items, SourceFilesService(settings, processed, input), processed, input)
    }

    @AfterEach
    fun tearDown() {
        service.reset()
        server.close()
        zcbs.close()
    }

    private fun base() = WorkflowSettings(
        testFolder = inbox.toString(), backupPdfFolder = backupPdf.toString(),
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
    private fun runNext(auto: Boolean = false): StepView {
        val step = next()
        service.execute(step.id, RunMode.IN_APP, auto)
        val deadline = System.currentTimeMillis() + 15_000
        while (stepView(step.id).status == "RUNNING" && System.currentTimeMillis() < deadline) Thread.sleep(50)
        return stepView(step.id)
    }

    /** Voert stappen uit tot de volgende stap van het gegeven type aan de beurt is (die wordt zelf niet uitgevoerd). */
    private fun runUntil(type: String) {
        while (next().type != type) {
            val result = runNext()
            assertEquals("DONE", result.status, "${result.type}: ${result.message}")
        }
    }

    /** Zet de pdf's uit de gemeenschappelijke invoermap in de map van de ingelogde gebruiker, zoals de app ze daar zou neerzetten. */
    private fun useUserFolder(): Path {
        val mine = input.ensure()
        listOf("xx01.pdf", "xx02.pdf", "notes.txt").forEach { Files.move(inbox.resolve(it), mine.resolve(it)) }
        return mine
    }

    private fun filesIn(path: Path): List<String> =
        if (Files.isDirectory(path)) path.listDirectoryEntries().map { it.name }.sorted() else emptyList()

    private fun remote(path: String): Path = serverRoot.resolve(path.trimStart('/'))

    private fun states(row: Int, group: String): List<String> =
        service.view()!!.rows[row].checks.first { it.label == group }.items.map { it.state }

    // ---- opzet van de stappen ----

    @Test
    fun `creates one step per file for renaming and one per run for the rest`() {
        service.start("0100")
        val view = service.view()!!
        assertEquals(listOf("0100.pdf", "0101.pdf"), view.rows.map { it.newName })
        assertEquals(listOf("xx01.pdf", "xx02.pdf"), view.rows.map { it.originalName })
        assertEquals(6, view.totalCount, "2 x hernoemen, pdf2zcbs, images, upload, back-up en opruimen")
        assertEquals(listOf("Hernoemen", "pdf2zcbs.exe", "images.exe", "Upload (sftp)", "Back-up en opruimen"), view.stepTypes)
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

        // back-up en opruimen: eerst de back-up van 100pct en pdf, dan weg; de originelen op de bron blijven staan.
        assertTrue(next().plan.any { it.contains("verwerkt-log") })
        assertEquals("DONE", runNext().status)
        assertEquals(listOf("90000000.jpg", "90000001.jpg"), filesIn(backup))
        assertEquals(listOf("90000000.pdf", "90000001.pdf"), filesIn(backupPdf))
        assertFalse(Files.exists(p2zDir.resolve("output")))
        assertFalse(Files.exists(imgDir.resolve("small")))
        assertFalse(Files.exists(imgDir.resolve("100pct")))
        assertEquals(emptyList(), filesIn(imgDir.resolve("images")))
        assertEquals(listOf("images", "images.sh"), filesIn(imgDir), "het logbestand is weg; het programma en de lege invoermap blijven")
        assertEquals(listOf("pdf2zcbs.sh"), filesIn(p2zDir), "ook de pdf's die pdf2zcbs kreeg zijn weg")
        assertEquals(listOf("notes.txt"), filesIn(inbox), "de kopieën in de invoermap en de werkmap zijn opgeruimd")
        assertEquals(listOf("server", "back-up"), service.view()!!.rows[0].checks.map { it.label })
        assertEquals(listOf("OK", "OK"), states(0, "back-up"))
        assertNull(service.view()!!.next)
    }

    @Test
    fun `de chain zet de bestanden in het verwerkt-log`() {
        service.start("90000000")
        runUntil("CLEANUP")
        assertEquals(emptyList(), processed.all())
        assertEquals("DONE", runNext().status)
        val logged = processed.all()
        assertEquals(listOf("90000000", "90000001"), logged.map { it.number })
        assertEquals(listOf("xx01.pdf", "xx02.pdf"), logged.map { it.name })
        assertEquals(1, processed.runs().size)
        assertEquals("archief", logged[0].collection)
    }

    // ---- de flow van Carla ----

    @Test
    fun `de flow loopt in zes fasen met een controle na pdf2zcbs en na images`() {
        assertEquals(null, items.login("Test Beheerder", "Beheer1"))
        val mine = useUserFolder()
        assertEquals(1, service.flowView().current, "eerst de bestanden kiezen")
        service.acceptInput()
        assertNull(service.confirmFiles())
        assertEquals(2, service.flowView().current, "dan de nummers")

        // een reeks die niet aaneengesloten past (11 bestaat al) wordt geweigerd; er is dan niets gekopieerd
        zcbs.addRecord("edit-leeg.html", "90000011")
        assertTrue(service.prepare("90000010")!!.contains("passen niet"))
        assertTrue(service.prepare("abc")!!.contains("alleen cijfers"))
        assertEquals(2, service.flowView().current)
        assertFalse(Files.exists(mine.resolve("werk")))

        // 12 en 13 zijn vrij en sluiten aan
        assertEquals(null, service.prepare("90000012"))
        assertEquals(listOf("90000012.pdf", "90000013.pdf"), filesIn(mine.resolve("werk")))
        assertEquals(3, service.flowView().current)
        assertEquals(listOf("done", "done", "now", "todo", "todo", "todo"), service.flowView().phases.map { it.state })
        assertEquals("90000012 t/m 90000013", service.flowView().phases[1].note)

        // pdf2zcbs draait; daarna moet Carla controleren voordat images mag
        assertEquals("DONE", runNext().status)
        var flow = service.flowView()
        assertEquals(3, flow.current)
        assertTrue(flow.review, "de uitvoer van pdf2zcbs wacht op controle")
        assertTrue(next().problems.any { it.contains("Controleer eerst") }, next().problems.toString())
        assertEquals("FAILED", runNext().status, "images start niet voor de controle")
        assertFalse(Files.exists(imgDir.resolve("images/90000012.jpg")))
        service.retry(next().id)
        assertTrue(service.confirmReview(StepType.IMAGES)!!.contains("nog niet klaar"))
        assertEquals(null, service.confirmReview(StepType.PDF2ZCBS))
        flow = service.flowView()
        assertEquals(4, flow.current)
        assertFalse(flow.review)

        assertEquals("DONE", runNext().status)
        flow = service.flowView()
        assertEquals(4, flow.current)
        assertTrue(flow.review, "de uitvoer van images wacht op controle")
        assertTrue(next().problems.any { it.contains("Controleer eerst") })
        assertEquals(null, service.confirmReview(StepType.IMAGES))
        assertEquals(5, service.flowView().current)

        assertEquals("DONE", runNext().status)
        assertEquals(6, service.flowView().current)
        assertEquals("DONE", runNext().status)
        val done = service.flowView()
        assertTrue(done.finished)
        assertEquals(7, done.current)
        assertEquals(listOf("90000012.jpg", "90000013.jpg"), filesIn(backup))
        assertEquals(listOf("90000012.pdf", "90000013.pdf"), filesIn(backupPdf))
        assertEquals(listOf("notes.txt"), filesIn(mine), "de kopieën en de werkmap zijn opgeruimd")
        assertEquals(listOf("Test Beheerder", "Test Beheerder"), processed.all().map { it.user })
    }

    @Test
    fun `de nummers moeten allemaal in een vrije reeks passen`() {
        assertEquals(null, items.login("Test Beheerder", "Beheer1"))
        val mine = useUserFolder()
        service.acceptInput()
        zcbs.addRecord("edit-leeg.html", "00000003")
        // de vrije nummers zijn 1-2 en 4 en hoger: twee bestanden passen op 1 en 2, maar niet op 2 en 3
        assertTrue(service.prepare("00000002")!!.contains("passen niet"))
        assertEquals(null, service.prepare("00000001"))
        assertEquals(listOf("00000001.pdf", "00000002.pdf"), filesIn(mine.resolve("werk")), "voorloopnullen blijven staan")
    }

    @Test
    fun `zonder lijst met vrije nummers uit ZCBS start er niets`() {
        service.acceptInput()
        assertTrue(service.prepare("90000000")!!.contains("vrije nummers"), "niet ingelogd")
        assertFalse(Files.exists(inbox.resolve("werk")))
    }

    @Test
    fun `de uitvoer die Carla controleert wordt alleen getoond voor nummers van deze run`() {
        service.start("90000000")
        runUntil("IMAGES")
        assertNotNull(service.resultFile("pdf", "90000000"))
        assertNotNull(service.resultFile("p2z-small", "90000001"))
        assertEquals("tekst van 90000000", service.ocrExcerpt("90000000"))
        assertNull(service.resultFile("pdf", "12345"), "geen nummer van deze run")
        assertNull(service.resultFile("../../etc/passwd", "90000000"))
        assertNull(service.resultFile("100pct", "90000000"), "images draaide nog niet")
    }

    @Test
    fun `de back-up kan pas als de stappen ervoor klaar zijn`() {
        service.start("90000020")
        assertTrue(next().type != "CLEANUP")
        assertThrows<IllegalStateException> { service.execute(service.view()!!.rows[0].cells[4].id, RunMode.IN_APP) }
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

    // ---- resten van een eerdere run ----

    private fun makeLeftovers() {
        Files.createDirectories(p2zDir.resolve("output/ocr"))
        Files.writeString(p2zDir.resolve("output/ocr/555.txt"), "oud")
        Files.createDirectories(p2zDir.resolve("output/log"))
        Files.writeString(p2zDir.resolve("output/log/pdf2zcbs.log"), "blijft")
        Files.writeString(p2zDir.resolve("output/updates.txt"), "blijft")
        Files.writeString(p2zDir.resolve("777.pdf"), "pdf van iemand anders")
        Files.createDirectories(imgDir.resolve("images"))
        Files.writeString(imgDir.resolve("images/555.jpg"), "bron")
        Files.createDirectories(imgDir.resolve("small"))
        Files.writeString(imgDir.resolve("small/555.jpg"), "klein")
    }

    @Test
    fun `een schone werkplek heeft geen resten, en logs en het programma zelf tellen niet`() {
        service.acceptInput()
        Files.writeString(imgDir.resolve("log-261019-2000.txt"), "log")
        assertEquals(emptyList(), service.leftovers())
    }

    @Test
    fun `resten van een eerdere run worden gevonden, behalve de logs`() {
        service.acceptInput()
        makeLeftovers()
        val found = service.leftovers()
        assertEquals(
            listOf("pdf2zcbs: pdf's in de map van het programma", "pdf2zcbs: de map output", "images.exe: bronbestanden in de map images", "images.exe: de map small"),
            found.map { it.label },
        )
        assertEquals(listOf("output/ocr/555.txt"), found[1].names, "output/log en updates.txt blijven buiten beschouwing")
    }

    @Test
    fun `zolang er resten zijn gaat de flow niet verder`() {
        service.acceptInput()
        makeLeftovers()
        assertTrue(service.confirmFiles()!!.contains("eerdere run"))
        assertEquals(1, service.flowView().current)
        assertTrue(service.prepare("10")!!.contains("eerdere run"))
        assertFalse(Files.exists(inbox.resolve("werk")))
    }

    @Test
    fun `resten verplaatsen zet ze in een submap met dezelfde indeling en laat de rest met rust`() {
        service.acceptInput()
        makeLeftovers()
        val message = service.clearLeftovers(move = true)
        assertTrue(message.contains("4 bestanden") || message.contains("bestanden verplaatst"), message)
        assertEquals(emptyList(), service.leftovers())
        val target = inbox.resolve("resten").listDirectoryEntries().single()
        assertEquals("oud", Files.readString(target.resolve("pdf2zcbs/output/ocr/555.txt")))
        assertEquals("pdf van iemand anders", Files.readString(target.resolve("pdf2zcbs/777.pdf")))
        assertEquals("bron", Files.readString(target.resolve("images/images/555.jpg")))
        assertEquals("klein", Files.readString(target.resolve("images/small/555.jpg")))
        assertTrue(Files.exists(p2zDir.resolve("output/log/pdf2zcbs.log")), "het log van pdf2zcbs blijft staan")
        assertTrue(Files.exists(p2zDir.resolve("output/updates.txt")))
        assertTrue(Files.exists(p2zDir.resolve("pdf2zcbs.sh")), "het programma zelf blijft staan")
        assertFalse(Files.exists(imgDir.resolve("small")), "lege mappen verdwijnen")
        assertTrue(Files.isDirectory(imgDir.resolve("images")), "de invoermap van images.exe blijft")
        assertNull(service.confirmFiles())
        assertEquals(2, service.flowView().current)
    }

    @Test
    fun `resten verplaatsen gaat naar de ingestelde map voor resten`() {
        service.acceptInput()
        makeLeftovers()
        val custom = dir.resolve("k-resten")
        save { it.copy(leftoversFolder = custom.toString()) }
        service.clearLeftovers(move = true)
        assertEquals(1, custom.listDirectoryEntries().size)
        assertFalse(Files.exists(inbox.resolve("resten")))
    }

    @Test
    fun `resten verwijderen wist alleen die bestanden`() {
        service.acceptInput()
        makeLeftovers()
        assertTrue(service.clearLeftovers(move = false).contains("verwijderd"))
        assertEquals(emptyList(), service.leftovers())
        assertFalse(Files.exists(inbox.resolve("resten")))
        assertTrue(Files.exists(p2zDir.resolve("pdf2zcbs.sh")))
        assertTrue(Files.exists(p2zDir.resolve("output/updates.txt")))
    }

    @Test
    fun `bestanden die al in de invoermap staan zijn ook een rest, tot ze erbij horen`() {
        val found = service.leftovers().single()
        assertEquals("invoer", found.area)
        assertEquals(listOf("notes.txt", "xx01.pdf", "xx02.pdf"), found.names)
        assertTrue(service.confirmFiles()!!.contains("eerdere run"))
        assertTrue(service.prepare("10")!!.contains("eerdere run"))

        service.acceptInput()
        assertEquals(emptyList(), service.leftovers())
        assertNull(service.confirmFiles())
    }

    @Test
    fun `bevestigen zonder bestanden in de invoermap kan niet`() {
        Files.list(inbox).use { it.toList() }.forEach { Files.delete(it) }
        service.acceptInput()
        assertTrue(service.confirmFiles()!!.contains("geen pdf"))
        assertEquals(1, service.flowView().current)
    }

    @Test
    fun `de invoermap opruimen verplaatst alles uit je map, ook de werkmap, en vraagt daarna niets meer`() {
        Files.createDirectories(inbox.resolve("werk"))
        Files.writeString(inbox.resolve("werk/1.pdf"), "kopie")
        service.clearLeftovers(move = true)
        assertEquals(emptyList(), service.leftovers())
        assertEquals(listOf("resten"), filesIn(inbox), "alleen de map voor resten blijft over")
        val target = inbox.resolve("resten").listDirectoryEntries().single()
        assertEquals(listOf("notes.txt", "werk", "xx01.pdf", "xx02.pdf"), filesIn(target.resolve("invoer")))
        assertEquals("kopie", Files.readString(target.resolve("invoer/werk/1.pdf")))
    }

    @Test
    fun `elke gebruiker heeft een eigen invoermap`() {
        assertNull(items.login("Test Beheerder", "Beheer1"))
        val first = input.ensure()
        assertEquals(inbox.resolve("Test Beheerder"), first)
        Files.writeString(first.resolve("a.pdf"), "van de eerste")
        assertEquals(listOf("a.pdf"), service.pdfsInTestFolder().map { it.name })
        assertEquals(inbox.resolve("resten").resolve("Test Beheerder"), service.leftoversTarget())
        service.acceptInput()
        assertEquals(emptyList(), service.leftovers())

        items.logout()
        assertNull(items.login("Test Bewerker", "Bewerk1"))
        assertEquals(inbox.resolve("Test Bewerker"), input.path())
        assertEquals(emptyList(), service.pdfsInTestFolder(), "de bestanden van de ander zijn niet de mijne")
        assertTrue(Files.exists(first.resolve("a.pdf")))
    }

    @Test
    fun `opruimen weigert tijdens een doorloop`() {
        service.start("10")
        makeLeftovers()
        assertTrue(service.clearLeftovers(move = true).contains("al een doorloop"))
        assertEquals(4, service.leftovers().size)
    }

    // ---- de antwoorden die images.exe krijgt ----

    private fun useQuestionsImages() {
        fake("images-questions.sh", imgDir, "images.sh")
    }

    private fun answers(): Map<String, String> =
        Files.readAllLines(imgDir.resolve("answers.txt")).associate { it.substringBefore("=") to it.substringAfter("=") }

    private fun rememberedAnswer(pattern: String): String? = settings.get().imagesAnswers.firstOrNull { it.pattern == pattern }?.answer

    /** Typt de antwoorden voor een handmatige run: wacht tot het programma een bekende vraag stelt en antwoordt op die vraag. */
    private fun answerManually(answers: Map<String, String>, seen: MutableList<String> = mutableListOf()) {
        var sent = 0
        val deadline = System.currentTimeMillis() + 15_000
        while (System.currentTimeMillis() < deadline) {
            val snap = runner.snapshot() ?: break
            if (!snap.running) break
            val question = snap.question
            if (question != null && snap.inputsSent == sent) {
                seen += "$question => ${snap.suggestion}"
                runner.sendInput(answers[question] ?: error("Onverwachte vraag: $question"))
                sent++
            } else Thread.sleep(30)
        }
    }

    private fun waitFor(step: StepView): StepView {
        val deadline = System.currentTimeMillis() + 15_000
        while (stepView(step.id).status == "RUNNING" && System.currentTimeMillis() < deadline) Thread.sleep(50)
        return stepView(step.id)
    }

    @Test
    fun `automatische antwoorden van images zijn standaard veilig`() {
        useQuestionsImages()
        service.start("10")
        runUntil("PDF2ZCBS")
        assertEquals("DONE", runNext().status)

        assertEquals("DONE", runNext(auto = true).status)

        assertEquals(
            mapOf("type" to "a", "resolutie" to "6", "upload" to "n", "records" to "n", "verwijdermappen" to "n", "verwijderbron" to "n"),
            answers(),
            "tot er handmatig is geantwoord wordt er niets geüpload en worden er geen records aangemaakt",
        )
        assertTrue(stepView(service.view()!!.rows[0].cells[2].id).message!!.contains("niet naar de server geüpload"))
    }

    @Test
    fun `handmatig typen onthoudt de antwoorden en automatisch gebruikt ze`() {
        useQuestionsImages()
        save { it.copy(imagesAnswers = WorkflowSettings.DEFAULT_IMAGES_ANSWERS.map { r -> if (r.pattern == "FTP wachtwoord") AnswerRule("FTP wachtwoord", "geheimpje", true) else r }) }
        service.start("10")
        runUntil("PDF2ZCBS")
        assertEquals("DONE", runNext().status)
        val images = next()
        assertEquals("n", rememberedAnswer("UPLOADEN aangemaakte images"))

        // handmatig: Carla typt, de app stelt het onthouden antwoord voor
        service.execute(images.id, RunMode.IN_APP, auto = false)
        val seen = mutableListOf<String>()
        answerManually(
            mapOf(
                "Type ENTER/RETURN indien OK" to "a", "Kies 1,2,3" to "7", "UPLOADEN aangemaakte images" to "j",
                "Nummer, naam of plaats instelling" to "4", "FTP wachtwoord" to "ander-wachtwoord", "Kies de ZCBS-toepassing" to "archief",
                "OVERSCHRIJVEN" to "n", "AANMAKEN en UPLOADEN van de bijbehorende" to "j",
                "VERWIJDEREN (tijdelijk) aangemaakte mappen" to "n", "VERWIJDEREN inhoud van de map [images]" to "n",
            ),
            seen,
        )
        assertEquals("DONE", waitFor(images).status)
        assertTrue(seen.contains("Kies 1,2,3 => 6"), seen.toString())
        assertTrue(seen.contains("UPLOADEN aangemaakte images => n"), "het voorstel is het onthouden antwoord: $seen")
        assertTrue(seen.contains("FTP wachtwoord => null"), "een wachtwoord wordt nooit voorgesteld: $seen")
        assertEquals("ander-wachtwoord", answers()["wachtwoord"])

        // onthouden: wat ze typte, behalve het wachtwoord
        assertEquals("7", rememberedAnswer("Kies 1,2,3"))
        assertEquals("j", rememberedAnswer("UPLOADEN aangemaakte images"))
        assertEquals("j", rememberedAnswer("AANMAKEN en UPLOADEN van de bijbehorende"))
        assertEquals("geheimpje", rememberedAnswer("FTP wachtwoord"), "het wachtwoord uit de instellingen blijft zoals het was")
        assertFalse(stepView(images.id).message!!.contains("niet naar de server geüpload"), stepView(images.id).message)

        // automatisch: de onthouden antwoorden, met de vaste regels erbovenop
        service.retry(images.id)
        assertTrue(next().warnings.isEmpty(), "het onthouden antwoord is nu ja: ${next().warnings}")
        assertEquals("DONE", runNext(auto = true).status)
        val given = answers()
        assertEquals("7", given["resolutie"])
        assertEquals("j", given["upload"])
        assertEquals("j", given["records"])
        assertEquals("geheimpje", given["wachtwoord"])
        assertEquals("archief", given["collectie"], "de collectie uit de instellingen gaat voor")
        assertEquals("n", given["overschrijven"], "nooit overschrijven")
        assertEquals("n", given["verwijdermappen"])
        assertEquals("n", given["verwijderbron"])
    }

    @Test
    fun `automatisch uitvoeren waarschuwt als de onthouden antwoorden niet uploaden`() {
        service.start("10")
        runUntil("PDF2ZCBS")
        assertEquals("DONE", runNext().status)
        assertTrue(next().warnings.any { it.contains("onthouden antwoorden") }, next().warnings.toString())
        assertTrue(next().plan.any { it.contains("uploaden naar de server nee") })
    }

    @Test
    fun `automatisch uploaden zonder wachtwoord wordt geweigerd`() {
        useQuestionsImages()
        save { it.copy(imagesAnswers = WorkflowSettings.DEFAULT_IMAGES_ANSWERS.map { r -> if (r.pattern.startsWith("UPLOADEN")) AnswerRule(r.pattern, "j") else r }) }
        service.start("10")
        runUntil("PDF2ZCBS")
        assertEquals("DONE", runNext().status)

        val noPassword = runNext(auto = true)
        assertEquals("FAILED", noPassword.status)
        assertTrue(noPassword.message!!.contains("FTP-wachtwoord"), noPassword.message)
        assertFalse(Files.exists(imgDir.resolve("answers.txt")), "het programma is niet gestart")
    }
}
