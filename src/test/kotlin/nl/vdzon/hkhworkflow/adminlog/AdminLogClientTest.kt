package nl.vdzon.hkhworkflow.adminlog

import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.util.UUID
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import tools.jackson.databind.json.JsonMapper

class AdminLogClientTest {
    private class Received(val method: String, val path: String, val key: String?, val body: String)

    private val mapper = JsonMapper.builder().build()
    private val received = CopyOnWriteArrayList<Received>()
    private var status = 204
    private val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
        createContext("/") { ex ->
            received += Received(ex.requestMethod, ex.requestURI.path, ex.requestHeaders.getFirst("X-HKH-Workflow-Key"), ex.requestBody.readAllBytes().decodeToString())
            if (status == 429) ex.responseHeaders.add("Retry-After", "60")
            ex.sendResponseHeaders(status, -1)
            ex.close()
        }
        start()
    }
    private val url = "http://127.0.0.1:${server.address.port}"

    @AfterTest
    fun stop() = server.stop(0)

    private fun report(id: UUID = UUID.randomUUID(), event: String? = null, snapshot: String = """{"a":1}""") = SessionReport(
        id, "NEW_FILES", "geheim-0123456789abcdef", "carla", "archief", 2, 7, "Bestanden kiezen", "RUNNING", 3, event,
        mapper.readTree(snapshot) as tools.jackson.databind.node.ObjectNode,
    )

    private fun waitFor(count: Int) {
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
        while (received.size < count && System.nanoTime() < deadline) Thread.sleep(20)
    }

    @Test
    fun `een sessie gaat met de sleutel en de volledige stand naar het logboek`() {
        val client = AdminLogClient(mapper, "$url/")
        val id = UUID.randomUUID()
        client.session(report(id, event = "Gestart"))
        waitFor(1)
        val r = received.single()
        assertEquals("PUT", r.method)
        assertEquals("/api/workflow-log/sessions/$id", r.path)
        assertEquals(AdminLogClient.INGEST_KEY, r.key)
        val body = mapper.readTree(r.body)
        assertEquals("NEW_FILES", body.path("kind").asString())
        assertEquals("carla", body.path("user").asString())
        assertEquals(2, body.path("phase").asInt())
        assertEquals("Gestart", body.path("event").asString())
        assertEquals(1, body.path("snapshot").path("a").asInt())
        assertTrue(body.path("appVersion").asString().isNotEmpty())
    }

    @Test
    fun `de bron is de computernaam`() {
        val client = AdminLogClient(mapper, url)
        client.login("carla", true, "PASSWORD")
        waitFor(1)
        val body = mapper.readTree(received.single().body)
        assertTrue(body.path("source").asString().isNotEmpty())
        assertEquals(body.path("computer").asString(), body.path("source").asString())
    }

    @Test
    fun `een inlogpoging wordt gemeld, dezelfde mislukte poging maar eens in de vijf minuten`() {
        val client = AdminLogClient(mapper, url)
        client.login("carla", true, "PASSWORD")
        client.login("onbekend", false, "COOKIE", "Naam of wachtwoord klopt niet")
        client.login("onbekend", false, "COOKIE", "Naam of wachtwoord klopt niet")
        waitFor(2)
        Thread.sleep(200)
        assertEquals(2, received.size)
        assertEquals("/api/workflow-log/logins", received[0].path)
        val failed = mapper.readTree(received[1].body)
        assertFalse(failed.path("success").asBoolean())
        assertEquals("COOKIE", failed.path("method").asString())
        assertFalse("password" in received[1].body.lowercase())
    }

    @Test
    fun `zonder adres wordt er niets gemeld`() {
        val client = AdminLogClient(mapper, " ")
        assertFalse(client.enabled)
        client.session(report())
        client.login("carla", true, "PASSWORD")
        Thread.sleep(200)
        assertTrue(received.isEmpty())
    }

    @Test
    fun `bij te veel meldingen wacht de client en zet hij niets door`() {
        status = 429
        val client = AdminLogClient(mapper, url)
        client.session(report())
        waitFor(1)
        client.session(report())
        client.login("carla", true, "PASSWORD")
        Thread.sleep(300)
        assertEquals(1, received.size)
    }

    @Test
    fun `een server die niet bereikbaar is, geeft nooit een fout`() {
        server.stop(0)
        val client = AdminLogClient(mapper, url)
        client.session(report())
        client.login("carla", true, "PASSWORD")
        Thread.sleep(300)
    }

    @Test
    fun `een te groot bericht wordt ingekort tot onder de grens van het logboek`() {
        val client = AdminLogClient(mapper, url)
        val big = "x".repeat(700_000)
        client.session(report(snapshot = """{"a":"$big","b":"$big"}"""))
        waitFor(1)
        val body = received.single().body
        assertTrue(body.length < 950_000, "lengte ${body.length}")
        assertTrue("ingekort" in body)
    }

    @Test
    fun `een hangend logboek houdt de aanroeper niet op`() {
        val hanging = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).apply {
            createContext("/") { Thread.sleep(5_000) }
            start()
        }
        try {
            val client = AdminLogClient(mapper, "http://127.0.0.1:${hanging.address.port}")
            client.login("carla", true, "PASSWORD") // wacht op de computernaam (hooguit een halve seconde) maar niet op de server
            val start = System.nanoTime()
            repeat(20) { client.login("carla$it", false, "PASSWORD", "fout $it"); client.session(report()) }
            val millis = (System.nanoTime() - start) / 1_000_000
            assertTrue(millis < 500, "de aanroepen duurden $millis ms")
        } finally {
            hanging.stop(0)
        }
    }

    @Test
    fun `een onbereikbaar logboek geeft nooit een fout en houdt de wachtrij klein`() {
        val closed = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).also { it.stop(0) }
        val client = AdminLogClient(mapper, "http://127.0.0.1:${closed.address.port}")
        repeat(500) { client.session(report()) }
        repeat(100) { client.login("carla", false, "PASSWORD", "fout $it") }
        Thread.sleep(300)
        assertTrue(client.enabled)
    }
}
