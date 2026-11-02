package nl.vdzon.hkhworkflow.adminlog

import java.util.Properties
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AppVersionTest {
    private fun props(vararg pairs: Pair<String, String>) = Properties().apply { pairs.forEach { (k, v) -> setProperty(k, v) } }

    @Test
    fun `zonder build-info is er niets bekend`() {
        val info = AppVersion.parse(null)
        assertEquals("ontwikkelversie", info.label)
        assertEquals("ontwikkel", info.text)
        assertEquals("", info.details)
    }

    @Test
    fun `een lokale build heeft geen buildnummer`() {
        val info = AppVersion.parse(props("build.version" to "0.0.1-SNAPSHOT", "build.time" to "2026-11-02T19:34:28Z", "build.number" to "0", "build.commit" to "-"))
        assertNull(info.number)
        assertNull(info.commit)
        assertEquals("0.0.1-SNAPSHOT+20261102.1934", info.text)
        assertEquals("lokale build · 2 nov 2026 20:34", info.label)
    }

    @Test
    fun `een build van de GitHub Action heeft nummer, commit en bouwtijd in Nederlandse tijd`() {
        val info = AppVersion.parse(
            props("build.version" to "0.0.1-SNAPSHOT", "build.time" to "2026-07-01T10:05:00Z", "build.number" to "57", "build.commit" to "9eedbee0123456789"),
        )
        assertEquals(57, info.number)
        assertEquals("9eedbee", info.commit)
        assertEquals("build 57 (0.0.1-SNAPSHOT+20260701.1005)", info.text)
        assertEquals("build 57 · 1 jul 2026 12:05", info.label) // zomertijd: UTC+2
        assertEquals("0.0.1-SNAPSHOT, commit 9eedbee", info.details)
    }

    @Test
    fun `een buildnummer van nul telt niet`() {
        assertNull(AppVersion.parse(props("build.version" to "1", "build.number" to "0")).number)
        assertNull(AppVersion.parse(props("build.version" to "1", "build.number" to "abc")).number)
    }
}
