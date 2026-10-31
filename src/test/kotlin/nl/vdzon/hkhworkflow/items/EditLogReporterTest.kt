package nl.vdzon.hkhworkflow.items

import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.nio.file.Path
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import nl.vdzon.hkhworkflow.adminlog.AdminLogClient
import nl.vdzon.hkhworkflow.settings.SettingsService
import nl.vdzon.hkhworkflow.settings.WorkflowProperties
import org.junit.jupiter.api.io.TempDir
import tools.jackson.databind.json.JsonMapper

class EditLogReporterTest {
    @TempDir
    lateinit var dir: Path

    private val mapper = JsonMapper.builder().build()
    private val bodies = CopyOnWriteArrayList<String>()
    private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
        createContext("/") { ex ->
            bodies += ex.requestBody.readAllBytes().decodeToString()
            ex.sendResponseHeaders(204, -1)
            ex.close()
        }
        start()
    }
    private lateinit var items: ItemEditService
    private lateinit var reporter: EditLogReporter
    private lateinit var zcbs: nl.vdzon.hkhworkflow.zcbs.FakeZcbs

    @BeforeTest
    fun setUp() {
        Current.set(null)
        zcbs = nl.vdzon.hkhworkflow.zcbs.FakeZcbs()
        val settings = SettingsService(mapper, WorkflowProperties(settingsFile = dir.resolve("s.json").toString()))
        settings.save(nl.vdzon.hkhworkflow.settings.WorkflowSettings(zcbsBaseUrl = zcbs.baseUrl, collection = "archief"))
        items = ItemEditService(settings)
        assertNull(items.login("Test Beheerder", "Beheer1"))
        reporter = EditLogReporter(items, AdminLogClient(mapper, "http://127.0.0.1:${server.address.port}"), mapper)
    }

    @AfterTest
    fun tearDown() {
        Current.set(null)
        server.stop(0)
        zcbs.close()
    }

    private fun waitFor(count: Int) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
        while (bodies.size < count && System.nanoTime() < deadline) Thread.sleep(20)
    }

    @Test
    fun `een sessie begint bij het kiezen van records en meldt wat er per record gebeurt`() {
        reporter.tick()
        Thread.sleep(150)
        assertTrue(bodies.isEmpty(), "zonder records is er geen sessie")

        items.startBatch(listOf(BatchRecord("11905", "scan_1.pdf", "Ria"), BatchRecord("11902", "scan_2.pdf", "Ria")))
        reporter.tick()
        waitFor(1)
        val first = mapper.readTree(bodies[0])
        assertEquals("EDIT", first.path("kind").asString())
        assertEquals(2, first.path("fileCount").asInt())
        assertEquals("RUNNING", first.path("status").asString())
        assertTrue("Gestart: 2 records gekozen" in first.path("event").asString())
        assertEquals("11905", first.path("snapshot").path("records").path(0).path("nummer").asString())

        // Een record wordt opgeslagen: de wijzigingen gaan mee in de melding.
        reporter.willSave("11905", listOf(Change("a5", "Titel", "oud", "Nieuwe titel")))
        items.record("11905")!!.status = EditStatus.OPGESLAGEN
        Thread.sleep(2_600)
        reporter.tick()
        waitFor(2)
        val second = mapper.readTree(bodies[1])
        assertEquals(1, second.path("phase").asInt())
        assertTrue("Record 11905 opgeslagen (gewijzigd: Titel)" in second.path("event").asString(), second.path("event").asString())
        assertEquals("Nieuwe titel", second.path("snapshot").path("wijzigingen").path("11905").path(0).path("nieuw").asString())
        assertEquals("oud", second.path("snapshot").path("wijzigingen").path("11905").path(0).path("oud").asString())
        // Het geheim en de sessie blijven dezelfde, een nieuwe keuze van records is een nieuwe sessie.
        assertEquals(first.path("secret").asString(), second.path("secret").asString())

        items.record("11902")!!.status = EditStatus.LATER
        Thread.sleep(2_600)
        reporter.tick()
        waitFor(3)
        val third = mapper.readTree(bodies[2])
        assertEquals("FINISHED", third.path("status").asString())

        items.startBatch(listOf(BatchRecord("11950")))
        Thread.sleep(2_600)
        reporter.tick()
        waitFor(4)
        val fourth = mapper.readTree(bodies[3])
        assertTrue(fourth.path("secret").asString() != first.path("secret").asString())
        assertEquals("RUNNING", fourth.path("status").asString())
    }
}
