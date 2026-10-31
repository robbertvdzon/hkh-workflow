package nl.vdzon.hkhworkflow.adminlog

import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import nl.vdzon.hkhworkflow.settings.CollectionSettings
import nl.vdzon.hkhworkflow.settings.SettingsChanged
import nl.vdzon.hkhworkflow.settings.WorkflowSettings
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper

class SettingsChangeReporterTest {
    private class Received(val method: String, val path: String, val body: JsonNode)

    private val mapper = JsonMapper.builder().build()
    private val received = CopyOnWriteArrayList<Received>()
    private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
        createContext("/") { ex ->
            received += Received(ex.requestMethod, ex.requestURI.path, mapper.readTree(ex.requestBody.readAllBytes()))
            ex.sendResponseHeaders(204, -1)
            ex.close()
        }
        start()
    }
    private val client = AdminLogClient(mapper, "http://127.0.0.1:${server.address.port}") { "windows-laptop" }
    private val reporter = SettingsChangeReporter(mapper, client)

    @AfterTest
    fun stop() = server.stop(0)

    private fun waitFor(count: Int) {
        val end = System.currentTimeMillis() + 5_000
        while (received.size < count && System.currentTimeMillis() < end) TimeUnit.MILLISECONDS.sleep(20)
    }

    private fun event(before: WorkflowSettings, after: WorkflowSettings) = SettingsChanged(before, after, "Instellingen opgeslagen", "carla", "10.0.0.5")

    @Test
    fun `meldt per veld de oude en de nieuwe waarde, wie het deed en de volledige nieuwe instellingen`() {
        val before = WorkflowSettings(testFolder = "C:\\oud", sourceFolders = "Ria=K:\\ria")
        val after = before.copy(testFolder = "C:\\nieuw").withCollection("beeldbank") { it.copy(imagesResolution = "2") }

        reporter.changed(event(before, after))
        waitFor(1)

        val r = received.single()
        assertEquals("POST", r.method)
        assertEquals("/api/workflow-log/settings-changes", r.path)
        assertEquals("carla", r.body.path("user").asString())
        assertEquals("10.0.0.5", r.body.path("address").asString())
        assertEquals("Instellingen opgeslagen", r.body.path("reason").asString())
        assertEquals("windows-laptop", r.body.path("source").asString())
        assertTrue(r.body.path("appVersion").asString().isNotEmpty())
        val changes = (0 until r.body.path("changes").size()).associate {
            val c = r.body.path("changes").get(it)
            c.path("field").asString() to (c.path("old").asString() to c.path("new").asString())
        }
        assertEquals("C:\\oud" to "C:\\nieuw", changes["testFolder"])
        assertEquals("6" to "2", changes["collections.beeldbank.imagesResolution"]?.let { (if (it.first.isEmpty()) "6" else it.first) to it.second })
        assertEquals("C:\\nieuw", r.body.path("settings").path("testFolder").asString())
        assertEquals("Ria=K:\\ria", r.body.path("settings").path("sourceFolders").asString())
    }

    @Test
    fun `het ftp-wachtwoord staat alleen leesbaar in het bericht als de beheerder dat wil, en dat het veranderde staat er altijd`() {
        val before = WorkflowSettings(ftpPassword = "oud-geheim", logFtpPassword = false)
        val after = before.copy(ftpPassword = "nieuw-geheim")

        reporter.changed(event(before, after))
        waitFor(1)

        val text = mapper.writeValueAsString(received.single().body)
        assertFalse(text.contains("oud-geheim") || text.contains("nieuw-geheim"), text)
        val change = received.single().body.path("changes").get(0)
        assertEquals("ftpPassword", change.path("field").asString())
        assertEquals(SnapshotSanitizer.REDACTED, change.path("new").asString())

        received.clear()
        reporter.changed(event(before.copy(logFtpPassword = true), after.copy(logFtpPassword = true)))
        waitFor(1)
        assertEquals("nieuw-geheim", received.single().body.path("settings").path("ftpPassword").asString())
    }

    @Test
    fun `wat uit de keuze voor de test-zcbs volgt telt niet als eigen wijziging`() {
        val before = WorkflowSettings(zcbsBaseUrl = "http://localhost:8081", ftpHost = "localhost")
        val after = before.copy(useTestZcbs = false, zcbsBaseUrl = WorkflowSettings.PRODUCTION_ZCBS_URL, ftpHost = WorkflowSettings.PRODUCTION_FTP_HOST)

        reporter.changed(event(before, after))
        waitFor(1)

        val fields = (0 until received.single().body.path("changes").size()).map { received.single().body.path("changes").get(it).path("field").asString() }
        assertEquals(listOf("useTestZcbs"), fields)
    }

    @Test
    fun `zonder wijziging of zonder logboek wordt niets gemeld`() {
        reporter.changed(event(WorkflowSettings(), WorkflowSettings()))
        SettingsChangeReporter(mapper, AdminLogClient(mapper, " ")).changed(event(WorkflowSettings(), WorkflowSettings(testFolder = "C:\\x")))
        TimeUnit.MILLISECONDS.sleep(200)
        assertTrue(received.isEmpty())
        assertEquals(CollectionSettings(), WorkflowSettings().collectionSettings("archief"))
    }
}
