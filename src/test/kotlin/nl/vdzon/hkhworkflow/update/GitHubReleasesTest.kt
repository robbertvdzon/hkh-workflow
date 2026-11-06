package nl.vdzon.hkhworkflow.update

import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import kotlin.io.path.readBytes
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import nl.vdzon.hkhworkflow.update.internal.GitHubReleases
import org.junit.jupiter.api.io.TempDir
import tools.jackson.databind.ObjectMapper

class GitHubReleasesTest {
    @TempDir lateinit var dir: Path

    private lateinit var server: HttpServer
    private lateinit var base: String
    private var releasesJson = "[]"
    private val payload = ByteArray(200_000) { (it % 251).toByte() }

    @BeforeTest
    fun start() {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/repos/o/r/releases") { ex -> ex.respond(200, releasesJson.toByteArray()) }
        server.createContext("/jar") { ex -> ex.respond(200, payload) }
        server.createContext("/redirect") { ex -> ex.responseHeaders.add("Location", "/jar"); ex.sendResponseHeaders(302, -1); ex.close() }
        server.createContext("/sha") { ex -> ex.respond(200, "abc123  hkh-workflow.jar\n".toByteArray()) }
        server.createContext("/weg") { ex -> ex.respond(404, ByteArray(0)) }
        server.start()
        base = "http://127.0.0.1:${server.address.port}"
    }

    @AfterTest
    fun stop() = server.stop(0)

    private fun com.sun.net.httpserver.HttpExchange.respond(code: Int, body: ByteArray) {
        sendResponseHeaders(code, if (body.isEmpty()) -1 else body.size.toLong())
        if (body.isNotEmpty()) responseBody.use { it.write(body) } else close()
    }

    private fun source() = GitHubReleases(ObjectMapper(), "o/r", base)

    private fun release(tag: String, draft: Boolean = false, pre: Boolean = false, jar: Boolean = true, sha: Boolean = true) = """
        {"tag_name":"$tag","draft":$draft,"prerelease":$pre,"published_at":"2026-11-02T20:00:00Z","assets":[
          ${listOfNotNull(if (jar) """{"name":"hkh-workflow.jar","browser_download_url":"$base/jar"}""" else null,
            if (sha) """{"name":"hkh-workflow.jar.sha256","browser_download_url":"$base/sha"}""" else null).joinToString(",")}
        ]}""".trimIndent()

    @Test
    fun `de hoogste build wint, ook als die niet bovenaan staat`() {
        releasesJson = "[${release("build-9")}, ${release("build-12")}, ${release("build-10")}]"
        val latest = source().latest()
        assertEquals(12, latest?.number)
        assertEquals("$base/jar", latest?.jarUrl)
        assertEquals("$base/sha", latest?.shaUrl)
    }

    @Test
    fun `concepten, voorlopige releases, andere tags en releases zonder jar tellen niet`() {
        releasesJson = "[${release("build-30", draft = true)}, ${release("build-31", pre = true)}, ${release("v2")}, ${release("build-32", jar = false)}, ${release("build-5")}]"
        assertEquals(5, source().latest()?.number)
    }

    @Test
    fun `zonder bruikbare release is er niets`() {
        releasesJson = "[]"
        assertNull(source().latest())
    }

    @Test
    fun `een release zonder sha256 heeft geen shaUrl`() {
        releasesJson = "[${release("build-3", sha = false)}]"
        assertNull(source().latest()?.shaUrl)
    }

    @Test
    fun `downloaden volgt een redirect en geeft de sha256 van wat is binnengekomen`() {
        val target = dir.resolve("x.jar.part")
        val reports = mutableListOf<Pair<Long, Long>>()
        val sha = source().download("$base/redirect", target) { done, total -> reports += done to total }
        assertEquals(payload.size.toLong(), reports.last().first)
        assertEquals(payload.size.toLong(), reports.last().second)
        assertEquals(MessageDigest.getInstance("SHA-256").digest(payload).joinToString("") { "%02x".format(it) }, sha)
        assertEquals(payload.size, target.readBytes().size)
    }

    @Test
    fun `een fout van de server stopt het downloaden`() {
        val result = runCatching { source().download("$base/weg", dir.resolve("y.part")) }
        assertEquals(true, result.isFailure)
        assertEquals(false, Files.exists(dir.resolve("y.part")))
    }

    @Test
    fun `de tekst van het sha256-bestand wordt opgehaald`() {
        assertEquals("abc123  hkh-workflow.jar\n", source().text("$base/sha"))
    }
}
