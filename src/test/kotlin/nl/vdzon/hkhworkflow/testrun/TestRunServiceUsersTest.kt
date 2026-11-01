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

/** Elke gebruiker heeft per collectie een eigen doorloop in eigen mappen, zonder slot: ook twee gebruikers in dezelfde collectie werken tegelijk. */
class TestRunServiceUsersTest {
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

    private fun service(): TestRunService {
        val mapper = ObjectMapper()
        val processed = ProcessedLog(settings, WorkflowProperties(settingsFile = dir.resolve("s.json").toString(), processedLog = dir.resolve("verwerkt.tsv").toString()))
        return TestRunService(settings, FtpService(settings), ItemEditService(settings), processed, "UTF-8", dir.resolve("logs").toString(), mapper)
    }

    /** Zet een pdf in de invoermap van [user] in [collection], zoals het kiezen van bestanden dat doet. */
    private fun choose(user: String, collection: String, name: String = "scan.pdf") {
        val invoer = WorkFolders(settings, user, collection).also { it.ensureAll() }.invoer()!!
        Files.writeString(invoer.resolve(name), "pdf van $user")
    }

    private fun <T> asUser(user: String, collection: String, block: () -> T): T = Current.runAs(user, collection, block)

    @Test
    fun `twee gebruikers in dezelfde collectie werken tegelijk, elk met eigen bestanden en een eigen stand`() {
        val svc = service()
        choose("Jelly", "archief", "jelly.pdf")
        choose("Ria", "archief", "ria.pdf")
        asUser("Jelly", "archief") { svc.start("100") }
        asUser("Ria", "archief") { svc.start("500") }
        assertEquals(listOf("jelly.pdf"), asUser("Jelly", "archief") { svc.view() }!!.rows.map { it.originalName })
        assertEquals(listOf("ria.pdf"), asUser("Ria", "archief") { svc.view() }!!.rows.map { it.originalName })
        assertEquals("100", asUser("Jelly", "archief") { svc.view() }!!.startNumber)
        assertEquals("500", asUser("Ria", "archief") { svc.view() }!!.startNumber)
        assertEquals(setOf("Jelly", "Ria"), svc.active().map { it.owner }.toSet())
        assertTrue(Files.isDirectory(work.resolve("Jelly/archief/pdf2zcbs")))
        assertTrue(Files.isDirectory(work.resolve("Ria/archief/pdf2zcbs")))
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
        assertEquals(setOf("archief", "beeldbank"), svc.active().map { it.collection }.toSet())
        assertTrue(Files.isDirectory(work.resolve("Jelly/archief/pdf2zcbs")))
        assertTrue(Files.isDirectory(work.resolve("Ria/beeldbank/pdf2zcbs")))
    }

    @Test
    fun `iemand die alleen kijkt telt niet als bezig`() {
        val svc = service()
        asUser("Jelly", "archief") { svc.flowView() }
        asUser("Ria", "archief") { svc.flowView() }
        assertEquals(emptyList(), svc.active())
    }

    @Test
    fun `de naam met andere hoofdletters is dezelfde gebruiker`() {
        val svc = service()
        choose("Jelly", "archief")
        asUser("Jelly", "archief") { svc.start("100") }
        assertEquals("100", asUser("jelly", "archief") { svc.view() }!!.startNumber)
        assertEquals(1, svc.sessions().size)
    }

    @Test
    fun `na reset heeft de gebruiker weer een lege doorloop, en die van een ander blijft staan`() {
        val svc = service()
        choose("Jelly", "archief", "jelly.pdf")
        choose("Ria", "archief", "ria.pdf")
        asUser("Jelly", "archief") { svc.start("100") }
        asUser("Ria", "archief") { svc.start("500") }
        asUser("Jelly", "archief") { svc.reset() }
        assertEquals(listOf("Ria"), svc.active().map { it.owner })
        assertEquals("500", asUser("Ria", "archief") { svc.view() }!!.startNumber)
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
        assertEquals(listOf("Jelly"), after.active().map { it.owner })
        val view = asUser("Jelly", "archief") { after.view() }!!
        assertEquals("100", view.startNumber)
        assertEquals(listOf("scan.pdf"), view.rows.map { it.originalName })
        assertEquals("DONE", view.rows[0].cells[0].status, "de hernoem-stap was klaar")
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
    fun `de beheerder kan de doorloop van een gebruiker stoppen, zonder die van een ander`() {
        val svc = service()
        choose("Jelly", "archief", "jelly.pdf")
        choose("Ria", "archief", "ria.pdf")
        asUser("Jelly", "archief") { svc.start("100") }
        asUser("Ria", "archief") { svc.start("500") }
        assertTrue(svc.stop("jelly", "archief"))
        assertEquals(false, svc.stop("Jelly", "archief"), "al gestopt")
        assertEquals(listOf("Ria"), svc.active().map { it.owner })
    }
}
