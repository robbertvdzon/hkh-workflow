package nl.vdzon.hkhworkflow.sftp

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

class SftpServiceTest {
    @TempDir
    lateinit var dir: Path

    private lateinit var server: TestSftpServer
    private lateinit var settings: SettingsService
    private lateinit var service: SftpService
    private lateinit var serverRoot: Path

    @BeforeEach
    fun setUp() {
        serverRoot = Files.createDirectories(dir.resolve("server"))
        Files.createDirectories(serverRoot.resolve("public_html/archief/ocr"))
        Files.createDirectories(serverRoot.resolve("public_html/archief/pdf"))
        server = TestSftpServer(serverRoot, dir.resolve("hostkey.ser"))
        settings = SettingsService(ObjectMapper(), WorkflowProperties(settingsFile = dir.resolve("s.json").toString()))
        settings.save(
            WorkflowSettings(
                collection = "archief",
                sftpHost = "localhost", sftpPort = server.port,
                sftpUser = TestSftpServer.USER, sftpPassword = TestSftpServer.PASSWORD,
                sftpHostKey = server.fingerprint,
                sftpBaseDir = "/public_html",
            ),
        )
        service = SftpService(settings)
    }

    @AfterEach
    fun tearDown() = server.close()

    private fun local(name: String, content: String = "inhoud van $name"): Path =
        Files.writeString(dir.resolve(name), content)

    private fun item(name: String, sub: String = "ocr") =
        UploadItem(local(name), "/public_html/archief/$sub", name)

    @Test
    fun `uploads files, sets them readable and verifies the size`() {
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
    fun `refuses to create missing folders`() {
        val e = assertThrows<IllegalStateException> {
            service.upload(listOf(UploadItem(local("1.txt"), "/public_html/archieftest/ocr", "1.txt")))
        }
        assertTrue(e.message!!.contains("Map bestaat niet op de server"), e.message)
        assertFalse(Files.exists(serverRoot.resolve("public_html/archieftest")))
    }

    @Test
    fun `refuses a server whose fingerprint differs from the pinned one`() {
        settings.save(settings.get().copy(sftpHostKey = "SHA256:" + "A".repeat(43)))
        assertThrows<Exception> { service.upload(listOf(item("1.txt"))) }
        assertFalse(Files.exists(serverRoot.resolve("public_html/archief/ocr/1.txt")))
    }

    @Test
    fun `refuses to upload without a pinned fingerprint`() {
        settings.save(settings.get().copy(sftpHostKey = ""))
        val e = assertThrows<IllegalStateException> { service.upload(listOf(item("1.txt"))) }
        assertTrue(e.message!!.contains("vingerafdruk"), e.message)
    }

    @Test
    fun `wrong password fails without uploading`() {
        settings.save(settings.get().copy(sftpPassword = "fout"))
        assertThrows<Exception> { service.upload(listOf(item("1.txt"))) }
    }

    @Test
    fun `test connection shows our fingerprint, which matches the server's own calculation`() {
        val result = service.testConnection()
        assertTrue(result.ok, result.message)
        assertEquals(server.fingerprint, result.fingerprint)
        assertEquals("MATCH", result.pinned)
        assertEquals(listOf("/public_html/archief/ocr", "/public_html/archief/pdf"), result.dirs.map { it.path })
        assertTrue(result.dirs.all { it.exists })
    }

    @Test
    fun `test connection reports missing folders and a mismatching pinned fingerprint`() {
        settings.save(settings.get().copy(collection = "bestaatniet", sftpHostKey = "SHA256:" + "B".repeat(43)))
        val result = service.testConnection()
        assertTrue(result.ok)
        assertEquals("MISMATCH", result.pinned)
        assertTrue(result.dirs.none { it.exists })
    }

    @Test
    fun `test connection reports a failing login`() {
        settings.save(settings.get().copy(sftpPassword = "fout"))
        val result = service.testConnection()
        assertFalse(result.ok)
        assertTrue(result.message.startsWith("Verbinden mislukt"), result.message)
    }
}
