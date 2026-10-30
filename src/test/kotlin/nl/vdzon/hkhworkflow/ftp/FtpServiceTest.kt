package nl.vdzon.hkhworkflow.ftp

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import nl.vdzon.hkhworkflow.settings.SettingsService
import nl.vdzon.hkhworkflow.settings.WorkflowProperties
import nl.vdzon.hkhworkflow.settings.WorkflowSettings
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import tools.jackson.databind.ObjectMapper

class FtpServiceTest {
    @TempDir
    lateinit var dir: Path

    private lateinit var server: TestFtpServer
    private lateinit var settings: SettingsService
    private lateinit var service: FtpService
    private lateinit var serverRoot: Path

    @BeforeEach
    fun setUp() {
        serverRoot = Files.createDirectories(dir.resolve("server"))
        Files.createDirectories(serverRoot.resolve("public_html/archief/ocr"))
        Files.createDirectories(serverRoot.resolve("public_html/archief/pdf"))
        server = TestFtpServer(serverRoot)
        settings = SettingsService(ObjectMapper(), WorkflowProperties(settingsFile = dir.resolve("s.json").toString()))
        settings.save(
            WorkflowSettings(
                collection = "archief",
                ftpHost = "localhost", ftpPort = server.port,
                ftpUser = TestFtpServer.USER, ftpPassword = TestFtpServer.PASSWORD,
                ftpBaseDir = "/public_html",
            ),
        )
        service = FtpService(settings)
    }

    @AfterEach
    fun tearDown() = server.close()

    private fun local(name: String, content: String = "inhoud van $name"): Path =
        Files.writeString(dir.resolve(name), content)

    private fun item(name: String, sub: String = "ocr") =
        UploadItem(local(name), "/public_html/archief/$sub", name)

    @Test
    fun `uploads files and verifies the size`() {
        val results = service.upload(listOf(item("90000000.txt"), item("90000000.pdf", "pdf")))

        assertTrue(results.all { it.ok }, results.toString())
        assertEquals("inhoud van 90000000.txt", Files.readString(serverRoot.resolve("public_html/archief/ocr/90000000.txt")))
        assertTrue(Files.exists(serverRoot.resolve("public_html/archief/pdf/90000000.pdf")))
        // Geen tijdelijke bestanden achtergelaten.
        assertEquals(listOf("90000000.txt"), Files.list(serverRoot.resolve("public_html/archief/ocr")).use { l -> l.map { it.fileName.toString() }.toList() })
    }

    @Test
    fun `never overwrites an existing file and uploads nothing when the check fails`() {
        Files.writeString(serverRoot.resolve("public_html/archief/pdf/2.pdf"), "ORIGINEEL")

        val e = assertThrows<IllegalStateException> {
            service.upload(listOf(item("1.txt"), item("2.pdf", "pdf")))
        }

        assertTrue(e.message!!.contains("Bestaat al op de server: /public_html/archief/pdf/2.pdf"), e.message)
        assertEquals("ORIGINEEL", Files.readString(serverRoot.resolve("public_html/archief/pdf/2.pdf")))
        assertFalse(Files.exists(serverRoot.resolve("public_html/archief/ocr/1.txt")), "ook de andere bestanden blijven weg")
    }

    @Test
    fun `refuses a leftover temporary file of an earlier attempt`() {
        Files.writeString(serverRoot.resolve("public_html/archief/ocr/.1.txt.uploading"), "half")
        val e = assertThrows<IllegalStateException> { service.upload(listOf(item("1.txt"))) }
        assertTrue(e.message!!.contains("Tijdelijk bestand"), e.message)
    }

    @Test
    fun `refuses to create missing folders`() {
        val e = assertThrows<IllegalStateException> {
            service.upload(listOf(UploadItem(local("1.txt"), "/public_html/archieftest/ocr", "1.txt")))
        }
        assertTrue(e.message!!.contains("Map bestaat niet op de server"), e.message)
        assertFalse(Files.exists(serverRoot.resolve("public_html/archieftest")))
    }

    @Test
    fun `refuses an empty local file and two files for the same place`() {
        val empty = UploadItem(Files.createFile(dir.resolve("leeg.txt")), "/public_html/archief/ocr", "leeg.txt")
        assertTrue(assertThrows<IllegalStateException> { service.upload(listOf(empty)) }.message!!.contains("ontbreekt of is leeg"))
        val a = item("1.txt")
        assertTrue(assertThrows<IllegalStateException> { service.upload(listOf(a, a)) }.message!!.contains("dezelfde plek"))
    }

    @Test
    fun `refuses to upload without the connection details`() {
        settings.save(settings.get().copy(ftpHost = ""))
        val e = assertThrows<IllegalStateException> { service.upload(listOf(item("1.txt"))) }
        assertTrue(e.message!!.contains("Ftp-host"), e.message)
    }

    @Test
    fun `wrong password fails without uploading`() {
        settings.save(settings.get().copy(ftpPassword = "fout"))
        val e = assertThrows<Exception> { service.upload(listOf(item("1.txt"))) }
        assertTrue(e.message!!.contains("inloggen is mislukt"), e.message)
        assertFalse(Files.exists(serverRoot.resolve("public_html/archief/ocr/1.txt")))
    }

    @Test
    fun `test connection logs in and checks the folders`() {
        val result = service.testConnection()
        assertTrue(result.ok, result.message)
        assertEquals(listOf("/public_html/archief/ocr", "/public_html/archief/pdf"), result.dirs.map { it.path })
        assertTrue(result.dirs.all { it.exists })
    }

    @Test
    fun `test connection reports missing folders`() {
        settings.save(settings.get().copy(collection = "bestaatniet"))
        val result = service.testConnection()
        assertTrue(result.ok)
        assertTrue(result.dirs.none { it.exists })
    }

    @Test
    fun `test connection reports a failing login and an unreachable server`() {
        settings.save(settings.get().copy(ftpPassword = "fout"))
        val login = service.testConnection()
        assertFalse(login.ok)
        assertTrue(login.message.startsWith("Verbinden mislukt"), login.message)

        server.close()
        settings.save(settings.get().copy(ftpPassword = TestFtpServer.PASSWORD))
        assertFalse(service.testConnection(2_000).ok)
    }
}
