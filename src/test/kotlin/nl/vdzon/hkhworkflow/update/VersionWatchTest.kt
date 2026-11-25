package nl.vdzon.hkhworkflow.update

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertEquals
import nl.vdzon.hkhworkflow.adminlog.AdminLog
import nl.vdzon.hkhworkflow.adminlog.SessionReport
import nl.vdzon.hkhworkflow.settings.WorkflowProperties
import nl.vdzon.hkhworkflow.settings.internal.DefaultSettingsService
import nl.vdzon.hkhworkflow.update.internal.VersionWatch
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import tools.jackson.databind.ObjectMapper

class VersionWatchTest {
    @TempDir
    lateinit var dir: Path

    private val reported = mutableListOf<String>()
    private val adminLog = object : AdminLog {
        override fun session(report: SessionReport) {}
        override fun login(user: String?, success: Boolean, method: String, detail: String?) {}
        override fun versionChanged(previous: String, current: String, reason: String) { reported += "$reason: $previous -> $current" }
    }

    private fun watch() = VersionWatch(DefaultSettingsService(ObjectMapper(), WorkflowProperties(settingsFile = dir.resolve("s.json").toString())), adminLog)

    @Test
    fun `de eerste start onthoudt de versie en meldt niets`() {
        val watch = watch()
        watch.check("build 18 (0.0.1+20261124.1000)", watch.file())
        assertEquals(emptyList(), reported)
        assertEquals("build 18 (0.0.1+20261124.1000)", Files.readString(watch.file()).trim())
        assertEquals(dir.resolve(VersionWatch.FILE_NAME), watch.file())
    }

    @Test
    fun `dezelfde versie opnieuw starten meldt niets`() {
        val watch = watch()
        watch.check("build 18 (a)", watch.file())
        watch.check("build 18 (a)", watch.file())
        assertEquals(emptyList(), reported)
    }

    @Test
    fun `een nieuwere build wordt gemeld als nieuwe versie`() {
        val watch = watch()
        watch.check("build 18 (a)", watch.file())
        watch.check("build 19 (b)", watch.file())
        assertEquals(listOf("Nieuwe versie geïnstalleerd: build 18 (a) -> build 19 (b)"), reported)
        assertEquals("build 19 (b)", Files.readString(watch.file()).trim())
    }

    @Test
    fun `een lagere build is de vorige versie die is teruggezet`() {
        val watch = watch()
        watch.check("build 19 (b)", watch.file())
        watch.check("build 18 (a)", watch.file())
        assertEquals(listOf("Vorige versie teruggezet: build 19 (b) -> build 18 (a)"), reported)
    }
}
