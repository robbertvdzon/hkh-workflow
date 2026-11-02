package nl.vdzon.hkhworkflow.update

import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import nl.vdzon.hkhworkflow.settings.SettingsService
import nl.vdzon.hkhworkflow.settings.WorkflowProperties
import nl.vdzon.hkhworkflow.settings.WorkflowSettings
import org.junit.jupiter.api.io.TempDir
import tools.jackson.databind.ObjectMapper

class UpdateServiceTest {
    @TempDir lateinit var dir: Path

    private lateinit var updates: Path
    private lateinit var settings: SettingsService
    private val restarts = mutableListOf<Int>()
    private var busyNow: () -> List<String> = { emptyList() }
    private var clock = TestClock(Instant.parse("2026-11-03T23:00:00Z")) // 00:00 Nederlandse tijd

    private class TestClock(var now: Instant) : Clock() {
        override fun getZone(): ZoneId = ZoneId.of("UTC")
        override fun withZone(zone: ZoneId?): Clock = this
        override fun instant(): Instant = now
    }

    /** Een nep-GitHub: bouwt een jar (zip met build-info) voor het gevraagde nummer. */
    private inner class FakeSource(var release: Release? = Release(58, "build-58", Instant.parse("2026-11-03T20:00:00Z"), "jar-url", "sha-url")) : ReleaseSource {
        var jarBuild = 58
        var corruptDownload = false
        var downloads = 0
        var failLatest = false
        private fun jar(): ByteArray {
            val out = ByteArrayOutputStream()
            ZipOutputStream(out).use { zip ->
                zip.putNextEntry(ZipEntry("BOOT-INF/classes/META-INF/build-info.properties"))
                zip.write("build.number=$jarBuild\nbuild.version=0.0.1-SNAPSHOT\n".toByteArray())
                zip.closeEntry()
            }
            return out.toByteArray()
        }
        private fun sha(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
        override fun latest(): Release? = if (failLatest) error("geen verbinding") else release
        override fun text(url: String) = sha(jar()) + "  hkh-workflow.jar\n"
        override fun download(url: String, target: Path): String {
            downloads++
            val bytes = jar().let { if (corruptDownload) it + byteArrayOf(1) else it }
            Files.write(target, bytes)
            return sha(bytes)
        }
    }

    @BeforeTest
    fun setUp() {
        updates = dir.resolve("updates")
        settings = SettingsService(ObjectMapper(), WorkflowProperties(settingsFile = dir.resolve("s.json").toString()))
        settings.save(WorkflowSettings())
        restarts.clear()
    }

    private fun service(source: ReleaseSource, service: Boolean = true) = UpdateService(
        source, settings, object : Activity { override fun running(): List<String> = busyNow() },
        object : AppRestarter { override fun restart() { restarts += EXIT_UPDATE } }, clock, updates.toString(), service,
    )

    @Test
    fun `een nieuwere build wordt klaargezet met sha256 en de app start opnieuw`() {
        val source = FakeSource()
        assertNull(service(source).updateNow("Robbert"))
        assertTrue(updates.resolve("hkh-workflow.jar").exists())
        assertTrue(updates.resolve("hkh-workflow.jar.sha256").readText().endsWith("  hkh-workflow.jar\n"))
        assertFalse(updates.resolve("hkh-workflow.jar.part").exists())
        assertEquals(listOf(EXIT_UPDATE), restarts)
    }

    @Test
    fun `buiten de service wordt er niets gedownload en niet herstart`() {
        val source = FakeSource()
        val problem = service(source, service = false).updateNow("Robbert")
        assertNotNull(problem)
        assertTrue(problem.contains("Windows-service"))
        assertEquals(0, source.downloads)
        assertTrue(restarts.isEmpty())
    }

    @Test
    fun `zolang een programma loopt wordt er niet bijgewerkt`() {
        busyNow = { listOf("Jelly in archief") }
        val source = FakeSource()
        val problem = service(source).updateNow("Robbert")
        assertNotNull(problem)
        assertTrue(problem.contains("Jelly in archief"))
        assertEquals(0, source.downloads)
        assertTrue(restarts.isEmpty())
    }

    @Test
    fun `wordt er tijdens het downloaden een programma gestart, dan blijft de app draaien`() {
        var calls = 0
        busyNow = { if (++calls >= 2) listOf("Ria in archief") else emptyList() }
        val problem = service(FakeSource()).updateNow("Robbert")
        assertNotNull(problem)
        assertTrue(restarts.isEmpty())
    }

    @Test
    fun `een bestand met een verkeerde sha256 wordt geweigerd`() {
        val source = FakeSource().apply { corruptDownload = true }
        val problem = service(source).updateNow("Robbert")
        assertNotNull(problem)
        assertTrue(problem.contains("sha256"))
        assertFalse(updates.resolve("hkh-workflow.jar").exists())
        assertFalse(updates.resolve("hkh-workflow.jar.part").exists())
        assertTrue(restarts.isEmpty())
    }

    @Test
    fun `een jar met een ander buildnummer dan de release wordt geweigerd`() {
        val source = FakeSource().apply { jarBuild = 57 }
        val problem = service(source).updateNow("Robbert")
        assertNotNull(problem)
        assertTrue(problem.contains("build 57"))
        assertFalse(updates.resolve("hkh-workflow.jar").exists())
        assertTrue(restarts.isEmpty())
    }

    @Test
    fun `is er geen nieuwere build, dan gebeurt er niets`() {
        val source = FakeSource(Release(0, "build-0", null, "j", "s"))
        val problem = service(source).updateNow("Robbert")
        assertNotNull(problem)
        assertEquals(0, source.downloads)
        assertTrue(restarts.isEmpty())
    }

    @Test
    fun `is GitHub niet bereikbaar, dan staat de reden in de status`() {
        val svc = service(FakeSource().apply { failLatest = true })
        assertNull(svc.check())
        val status = svc.status()
        assertTrue(status.checkError!!.contains("niet bereikbaar"))
        assertFalse(status.newer)
        assertNotNull(svc.updateNow("Robbert"))
    }

    @Test
    fun `de nachtelijke update werkt bij als de schakelaar aan staat`() {
        val svc = service(FakeSource())
        svc.nightly()
        assertEquals(listOf(EXIT_UPDATE), restarts)
        assertFalse(svc.status().nightlyPending)
    }

    @Test
    fun `de nachtelijke update doet niets als de schakelaar uit staat of de app geen service is`() {
        settings.save(settings.get().copy(nightlyUpdate = false))
        service(FakeSource()).nightly()
        assertTrue(restarts.isEmpty())
        settings.save(settings.get().copy(nightlyUpdate = true))
        service(FakeSource(), service = false).nightly()
        assertTrue(restarts.isEmpty())
    }

    @Test
    fun `is er 's nachts nog iemand bezig, dan probeert de app het later opnieuw`() {
        busyNow = { listOf("Jelly in archief") }
        val svc = service(FakeSource())
        svc.nightly()
        assertTrue(restarts.isEmpty())
        assertTrue(svc.status().nightlyPending)

        clock.now = clock.now.plus(Duration.ofMinutes(10))
        svc.retryNightly()
        assertTrue(restarts.isEmpty())

        busyNow = { emptyList() }
        clock.now = clock.now.plus(Duration.ofMinutes(10))
        svc.retryNightly()
        assertEquals(listOf(EXIT_UPDATE), restarts)
        assertFalse(svc.status().nightlyPending)
    }

    @Test
    fun `na het nachtelijke venster geeft de app het op tot de volgende nacht`() {
        busyNow = { listOf("Jelly in archief") }
        val svc = service(FakeSource())
        svc.nightly()
        clock.now = clock.now.plus(Duration.ofHours(3)).plusSeconds(1)
        svc.retryNightly()
        assertFalse(svc.status().nightlyPending)
        assertTrue(restarts.isEmpty())
        assertTrue(svc.status().events.first().contains("overgeslagen"))
        busyNow = { emptyList() }
        svc.retryNightly() // geen poging meer tot de volgende nacht
        assertTrue(restarts.isEmpty())
    }
}
