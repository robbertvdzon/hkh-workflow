package nl.vdzon.hkhworkflow.ux

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FlowDemoStateTest {
    private fun runPhase(s: FlowDemoState, rows: Int = 12) {
        s.start()
        Thread.sleep(rows * s.stepMillis + 100L)
    }

    private fun loggedInAndPrepared(): FlowDemoState {
        val s = FlowDemoState()
        s.stepMillis = 10
        assertNull(s.login("Carla van Noort", "geheim"))
        s.confirmFiles()
        assertNull(s.prepare("11934"))
        return s
    }

    @Test
    fun `login vraagt naam en wachtwoord en telt foute pogingen`() {
        val s = FlowDemoState()
        assertNotNull(s.login("", "x"))
        repeat(3) { assertNotNull(s.login("Carla", "fout")) }
        assertTrue(s.login("Carla", "goed")!!.contains("Wacht"))
        assertNull(s.user)
    }

    @Test
    fun `bestaande recordnummers worden geweigerd`() {
        val s = FlowDemoState()
        s.login("Carla", "x")
        s.confirmFiles()
        assertTrue(s.prepare("11930")!!.contains("bestaat al"))
        assertNotNull(s.prepare("abc"))
        assertNull(s.prepare("11934"))
        assertEquals(12, s.records.size)
        assertEquals(11945, s.records.last().nr)
    }

    @Test
    fun `een stap gaat pas verder als de fase klaar is`() {
        val s = loggedInAndPrepared()
        assertEquals(4, s.current)
        s.next()
        assertEquals(4, s.current)
        runPhase(s)
        assertTrue(s.phaseDone(4))
        s.next()
        assertEquals(5, s.current)
    }

    @Test
    fun `een mislukte upload stopt de fase en opnieuw proberen lukt`() {
        val s = loggedInAndPrepared()
        runPhase(s)
        s.next()
        assertEquals(5, s.current)
        s.failPhase5 = true
        s.start()
        Thread.sleep(8 * s.stepMillis + 200L)
        assertTrue(s.failed())
        assertFalse(s.phaseDone(5))
        s.retry()
        Thread.sleep(12 * s.stepMillis + 150L)
        assertTrue(s.phaseDone(5))
    }

    @Test
    fun `afsluiten kan pas als elk record is opgeslagen of op later staat`() {
        val s = loggedInAndPrepared()
        repeat(3) { runPhase(s); s.next() }
        assertEquals(7, s.current)
        s.next()
        assertEquals(7, s.current)
        s.records.forEachIndexed { i, r -> r.status = if (i == 3) RecStatus.LATER else RecStatus.OPGESLAGEN }
        assertTrue(s.allHandled())
        s.next()
        assertEquals(8, s.current)
    }

    @Test
    fun `het laatste record is niet veilig te bewerken`() {
        val s = loggedInAndPrepared()
        s.open(s.records.last())
        assertEquals(RecStatus.ONVEILIG, s.records.last().status)
        s.open(s.records.first())
        assertEquals(RecStatus.BEZIG, s.records.first().status)
    }
}
