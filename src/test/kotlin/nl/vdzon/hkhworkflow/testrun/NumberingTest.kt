package nl.vdzon.hkhworkflow.testrun

import kotlin.test.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class NumberingTest {
    @Test
    fun `keeps leading zeros`() {
        assertEquals(listOf("01650", "01651", "01652"), sequenceNumbers("01650", 3))
    }

    @Test
    fun `grows past the original width`() {
        assertEquals(listOf("98", "99", "100"), sequenceNumbers("98", 3))
    }

    @Test
    fun `rejects non digits`() {
        assertThrows<IllegalArgumentException> { sequenceNumbers("12a", 1) }
    }
}
