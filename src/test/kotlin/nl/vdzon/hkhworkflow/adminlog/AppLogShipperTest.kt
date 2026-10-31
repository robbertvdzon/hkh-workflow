package nl.vdzon.hkhworkflow.adminlog

import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper

class AppLogShipperTest {
    private class Received(val path: String, val key: String?, val body: JsonNode)

    private val mapper = JsonMapper.builder().build()
    private val received = CopyOnWriteArrayList<Received>()
    private var status = 204
    private var retryAfter = 60
    private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
        createContext("/") { ex ->
            received += Received(ex.requestURI.path, ex.requestHeaders.getFirst("X-HKH-Workflow-Key"), mapper.readTree(ex.requestBody.readAllBytes()))
            if (status == 429) ex.responseHeaders.add("Retry-After", retryAfter.toString())
            ex.sendResponseHeaders(status, -1)
            ex.close()
        }
        start()
    }
    private val url = "http://127.0.0.1:${server.address.port}"
    private val dir: Path = Files.createTempDirectory("app-log-spool")
    private val spool = AppLogSpool(dir)
    private var now = 1_000_000_000L

    @AfterTest
    fun stop() = server.stop(0)

    private fun shipper(address: String = url) =
        AppLogShipper(mapper, AdminLogClient(mapper, address) { "windows-laptop" }, { spool }, { now }, Duration.ofMinutes(15))

    private fun messages(body: JsonNode): List<String> = (0 until body.path("lines").size()).map { body.path("lines").get(it).path("message").asString() }

    private fun line(message: String, version: String = "1.0+a", level: String = "INFO") =
        """{"time":"2026-10-31T10:00:00Z","level":"$level","logger":"nl.test.X","message":"$message","v":"$version"}"""

    @Test
    fun `verstuurt nieuwe regels met bron, computer, versie en een vast pakketnummer`() {
        spool.add(line("eerste"))
        spool.add(line("tweede", level = "WARN"))

        assertEquals(1, shipper().sendPending())

        val r = received.single()
        assertEquals("/api/workflow-log/app-logs", r.path)
        assertEquals(AdminLogClient.INGEST_KEY, r.key)
        assertEquals("windows-laptop", r.body.path("source").asString())
        assertEquals("1.0+a", r.body.path("appVersion").asString())
        assertTrue(r.body.path("chunkId").asString().startsWith("pending-"))
        assertEquals(listOf("eerste", "tweede"), messages(r.body))
        assertEquals("WARN", r.body.path("lines").get(1).path("level").asString())
        assertFalse(r.body.path("lines").get(0).has("v"), "de versie hoort bij het pakket, niet bij elke regel")
        assertTrue(spool.pending().isEmpty(), "na een geslaagde verzending is het bestand weg")
    }

    @Test
    fun `stuurt niets als er niets nieuws is`() {
        assertEquals(0, shipper().sendPending())
        assertTrue(received.isEmpty())
    }

    @Test
    fun `hooguit eens per kwartier, ook na een herstart`() {
        spool.add(line("a"))
        assertEquals(1, shipper().sendPending())

        spool.add(line("b"))
        now += Duration.ofMinutes(14).toMillis()
        assertEquals(0, shipper().sendPending(), "een nieuwe verzender (herstart) wacht ook tot het kwartier om is")
        assertEquals(1, received.size)

        now += Duration.ofMinutes(2).toMillis()
        assertEquals(1, shipper().sendPending())
        assertEquals(listOf("b"), messages(received.last().body))
    }

    @Test
    fun `bij een storing blijven de regels staan en gaan ze bij de volgende poging mee, met hetzelfde pakketnummer`() {
        spool.add(line("a"))
        status = 500
        assertEquals(0, shipper().sendPending())
        val first = received.single().body.path("chunkId").asString()
        assertEquals(1, spool.pending().size)

        status = 204
        spool.add(line("b"))
        now += Duration.ofMinutes(16).toMillis()
        shipper().sendPending()

        // Het oude bestand (a) gaat opnieuw met hetzelfde pakketnummer, zodat de ontvanger een herhaling herkent; b volgt in een eigen bestand.
        assertEquals(first, received[1].body.path("chunkId").asString())
        assertEquals(listOf("a"), messages(received[1].body))
        assertEquals(listOf("b"), messages(received[2].body))
        assertNotEquals(first, received[2].body.path("chunkId").asString())
        assertTrue(spool.pending().isEmpty())
    }

    @Test
    fun `een nieuwe appversie begint een nieuw pakket`() {
        spool.add(line("oud", version = "1.0+a"))
        spool.add(line("nieuw", version = "1.1+b"))

        assertEquals(2, shipper().sendPending())

        assertEquals(listOf("1.0+a", "1.1+b"), received.map { it.body.path("appVersion").asString() })
        assertEquals(2, received.map { it.body.path("chunkId").asString() }.distinct().size)
    }

    @Test
    fun `een weigering met 400 gooit het bestand weg en een 429 wacht af`() {
        spool.add(line("a"))
        status = 400
        shipper().sendPending()
        assertTrue(spool.pending().isEmpty(), "een bericht dat de ontvanger nooit aanneemt blijft niet eeuwig in de wachtrij")

        status = 429
        retryAfter = 1_800
        spool.add(line("b"))
        now += Duration.ofMinutes(16).toMillis()
        val shipper = shipper()
        shipper.sendPending()
        assertEquals(1, spool.pending().size)

        status = 204
        now += Duration.ofMinutes(16).toMillis()
        assertEquals(0, shipper.sendPending(), "een kwartier is om, maar de ontvanger vroeg om een half uur te wachten")
        now += Duration.ofMinutes(15).toMillis()
        assertEquals(1, shipper.sendPending())
        assertTrue(spool.pending().isEmpty())
    }

    @Test
    fun `zonder adres van het logboek gebeurt er niets`() {
        spool.add(line("a"))
        assertEquals(0, shipper(address = " ").sendPending())
        assertTrue(received.isEmpty())
    }

    @Test
    fun `een groot bestand gaat in meerdere pakketten`() {
        repeat(2_500) { spool.add(line("regel $it")) }
        assertEquals(2, shipper().sendPending())
        assertEquals(2_000, received[0].body.path("lines").size())
        assertEquals(500, received[1].body.path("lines").size())
    }
}
