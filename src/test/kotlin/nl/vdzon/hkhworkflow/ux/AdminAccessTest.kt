package nl.vdzon.hkhworkflow.ux

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import nl.vdzon.hkhworkflow.settings.WorkflowProperties

class AdminAccessTest {
    @Test
    fun `zonder ingesteld wachtwoord kan niemand beheerder worden`() {
        val admin = AdminAccess(WorkflowProperties(adminPassword = ""))
        assertFalse(admin.configured())
        assertFalse(admin.check(""), "ook een leeg wachtwoord werkt niet")
        assertFalse(admin.check("iets"))
    }

    @Test
    fun `alleen het juiste wachtwoord werkt`() {
        val admin = AdminAccess(WorkflowProperties(adminPassword = "testbeheer"))
        assertTrue(admin.configured())
        assertTrue(admin.check("testbeheer"))
        assertFalse(admin.check("testbeheer "))
        assertFalse(admin.check("TESTBEHEER"))
        assertFalse(admin.check(""))
    }
}
