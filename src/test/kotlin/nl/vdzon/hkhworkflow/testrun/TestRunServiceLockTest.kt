package nl.vdzon.hkhworkflow.testrun

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import nl.vdzon.hkhworkflow.ftp.FtpService
import nl.vdzon.hkhworkflow.items.Current
import nl.vdzon.hkhworkflow.items.ItemEditService
import nl.vdzon.hkhworkflow.settings.SettingsService
import nl.vdzon.hkhworkflow.settings.WorkflowProperties
import nl.vdzon.hkhworkflow.settings.WorkflowSettings
import org.junit.jupiter.api.io.TempDir
import tools.jackson.databind.ObjectMapper

/** Per collectie hooguit één doorloop; twee gebruikers in twee collecties tegelijk werken elk in hun eigen mappen. */
class TestRunServiceLockTest {
    @TempDir
    lateinit var dir: Path

    private lateinit var settings: SettingsService
    private lateinit var work: Path

    @BeforeTest
    fun setUp() {
        Current.set(null)
        work = Files.createDirectories(dir.resolve("werkmap"))
        val properties = WorkflowProperties(settingsFile = dir.resolve("s.json").toString(), processedLog = dir.resolve("verwerkt.tsv").toString())
        settings = SettingsService(ObjectMapper(), properties)
        settings.save(WorkflowSettings(testFolder = work.toString(), collection = "archief"))
    }

    @AfterTest
    fun tearDown() = Current.set(null)

    private fun service(timeoutMinutes: Long = 120): TestRunService {
        val mapper = ObjectMapper()
        val processed = ProcessedLog(settings, WorkflowProperties(settingsFile = dir.resolve("s.json").toString(), processedLog = dir.resolve("verwerkt.tsv").toString()))
        return TestRunService(settings, FtpService(settings), ItemEditService(settings), processed, "UTF-8", dir.resolve("logs").toString(), timeoutMinutes, mapper)
    }

    /** Zet een pdf in de invoermap van [user] in [collection], zoals het kiezen van bestanden dat doet. */
    private fun choose(user: String, collection: String, name: String = "scan.pdf") {
        val invoer = WorkFolders(settings, user, collection).also { it.ensureAll() }.invoer()!!
        Files.writeString(invoer.resolve(name), "pdf van $user")
    }

    private fun <T> asUser(user: String, collection: String, block: () -> T): T = Current.runAs(user, collection, block)

    @Test
    fun `een tweede gebruiker kan de collectie niet gebruiken zolang de eerste bezig is`() {
        val svc = service()
        choose("Jelly", "archief")
        asUser("Jelly", "archief") { svc.start("100") }
        assertEquals("Jelly", asUser("Ria", "archief") { svc.lockedBy() }?.owner)
        val locked = assertFailsWith<FlowLockedException> { asUser("Ria", "archief") { svc.view() } }
        assertTrue(locked.message!!.contains("In archief werkt Jelly al"), locked.message)
        // Jelly zelf merkt er niets van.
        assertEquals(1, asUser("Jelly", "archief") { svc.view() }!!.fileCount)
        assertNull(asUser("Jelly", "archief") { svc.lockedBy() })
    }

    @Test
    fun `twee gebruikers in twee collecties werken tegelijk, elk in hun eigen mappen`() {
        val svc = service()
        choose("Jelly", "archief")
        choose("Ria", "beeldbank", "foto.pdf")
        asUser("Jelly", "archief") { svc.start("100") }
        asUser("Ria", "beeldbank") { svc.start("500") }
        assertEquals(listOf("scan.pdf"), asUser("Jelly", "archief") { svc.view() }!!.rows.map { it.originalName })
        assertEquals(listOf("foto.pdf"), asUser("Ria", "beeldbank") { svc.view() }!!.rows.map { it.originalName })
        assertEquals("100", asUser("Jelly", "archief") { svc.view() }!!.startNumber)
        assertEquals("500", asUser("Ria", "beeldbank") { svc.view() }!!.startNumber)
        assertNull(asUser("Jelly", "archief") { svc.lockedBy() })
        assertNull(asUser("Ria", "beeldbank") { svc.lockedBy() })
        assertEquals(setOf("archief", "beeldbank"), svc.locks().map { it.collection }.toSet())
        assertTrue(Files.isDirectory(work.resolve("Jelly/archief/pdf2zcbs")))
        assertTrue(Files.isDirectory(work.resolve("Ria/beeldbank/pdf2zcbs")))
    }

    @Test
    fun `iemand die niets gestart heeft houdt de collectie niet vast`() {
        val svc = service()
        asUser("Jelly", "archief") { svc.flowView() }
        assertNull(asUser("Ria", "archief") { svc.lockedBy() }, "Jelly keek alleen, ze begon niets")
        asUser("Ria", "archief") { svc.flowView() }
        assertEquals(emptyList(), svc.locks())
    }

    @Test
    fun `na reset of klaar komt de collectie vrij`() {
        val svc = service()
        choose("Jelly", "archief")
        asUser("Jelly", "archief") { svc.start("100") }
        assertNotNull(asUser("Ria", "archief") { svc.lockedBy() })
        asUser("Jelly", "archief") { svc.reset() }
        assertNull(asUser("Ria", "archief") { svc.lockedBy() })
        assertEquals(emptyList(), asUser("Ria", "archief") { svc.filesInTestFolder() })
    }

    @Test
    fun `een doorloop die te lang stil ligt komt vrij voor een ander`() {
        val svc = service(timeoutMinutes = 0)
        choose("Jelly", "archief")
        asUser("Jelly", "archief") { svc.start("100") }
        Thread.sleep(20)
        assertNull(asUser("Ria", "archief") { svc.lockedBy() })
        assertEquals("Ria", asUser("Ria", "archief") { svc.current() }.owner)
    }

    @Test
    fun `de stand van een doorloop overleeft een herstart van de app`() {
        val svc = service()
        choose("Jelly", "archief")
        choose("Ria", "beeldbank", "foto.pdf")
        asUser("Jelly", "archief") { svc.start("100") }
        asUser("Jelly", "archief") { svc.execute(svc.view()!!.next!!.id, nl.vdzon.hkhworkflow.exerunner.RunMode.IN_APP) }
        asUser("Ria", "beeldbank") { svc.flowView() }
        svc.saveStates()
        assertTrue(Files.isRegularFile(work.resolve("Jelly/archief/sessie.json")))
        assertFalse(Files.exists(work.resolve("Ria/beeldbank/sessie.json")), "Ria heeft niets gestart")

        // Een nieuwe app leest de bewaarde doorloop terug.
        val after = service()
        after.restore()
        assertEquals(setOf("archief"), after.locks().map { it.collection }.toSet())
        val view = asUser("Jelly", "archief") { after.view() }!!
        assertEquals("100", view.startNumber)
        assertEquals(listOf("scan.pdf"), view.rows.map { it.originalName })
        assertEquals("DONE", view.rows[0].cells[0].status, "de hernoem-stap was klaar")
        assertEquals("Jelly", asUser("Ria", "archief") { after.lockedBy() }?.owner)
        // Ook als de ZCBS-naam met andere hoofdletters wordt ingelogd.
        assertEquals("100", asUser("jelly", "archief") { after.view() }!!.startNumber)
    }

    @Test
    fun `een stap die liep tijdens de herstart staat daarna op mislukt, en zonder doorloop verdwijnt het bestand`() {
        val svc = service()
        choose("Jelly", "archief")
        asUser("Jelly", "archief") { svc.start("100") }
        val flow = svc.sessions().single()
        val file = flow.stateFile()!!
        svc.saveStates()
        val json = Files.readString(file).replace(Regex("\"status\" : \"PENDING\""), "\"status\" : \"RUNNING\"")
        Files.writeString(file, json)
        val after = service()
        after.restore()
        val cells = asUser("Jelly", "archief") { after.view() }!!.rows[0].cells
        assertTrue(cells.all { it.status == "FAILED" }, cells.map { it.status }.toString())
        assertTrue(cells[0].message!!.contains("herstart"))

        asUser("Jelly", "archief") { after.reset() }
        after.saveStates()
        assertFalse(Files.exists(file), "een afgeronde of gewiste doorloop heeft geen bewaarde stand meer")
    }

    @Test
    fun `de beheerder kan een collectie vrijgeven`() {
        val svc = service()
        choose("Jelly", "archief")
        asUser("Jelly", "archief") { svc.start("100") }
        assertTrue(svc.release("archief"))
        assertEquals(false, svc.release("archief"), "al vrij")
        assertNull(asUser("Ria", "archief") { svc.lockedBy() })
    }
}
