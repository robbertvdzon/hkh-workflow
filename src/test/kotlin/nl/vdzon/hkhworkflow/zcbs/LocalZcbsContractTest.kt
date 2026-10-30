package nl.vdzon.hkhworkflow.zcbs

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue

/**
 * Contracttest tegen de lokale ZCBS in Docker (tools/docker/zcbs, http://localhost:8081, verzonnen testgebruiker user1 / abc,
 * aan te passen met -Dzcbs.user en -Dzcbs.password). Draait alleen met `mvn test -Dzcbs.live=true`, omdat de opslagtest in de
 * lokale kopie schrijft. Er gaat niets naar de echte HKH-server.
 */
class LocalZcbsContractTest {
    private val base = System.getProperty("zcbs.url", "http://localhost:8081")
    private val user = System.getProperty("zcbs.user", "user1")
    private val password = System.getProperty("zcbs.password", "abc")

    private fun client(): ZcbsClient {
        assumeTrue(System.getProperty("zcbs.live") == "true", "Zet -Dzcbs.live=true om tegen de lokale ZCBS te testen.")
        val c = ZcbsClient(base, "/cgi-bin/archief.pl")
        assumeTrue(runCatching { c.login(user, password) == LoginOutcome.Ok }.getOrDefault(false), "De lokale ZCBS draait niet op $base.")
        return c
    }

    @Test
    fun `inloggen met een goed en een fout wachtwoord`() {
        client()
        assertIs<LoginOutcome.Failed>(ZcbsClient(base, "/cgi-bin/archief.pl").login(user, "fout"))
    }

    @Test
    fun `het hoogste nummer en het bestaan van records`() {
        val c = client()
        assertTrue((c.lastNumber() ?: 0) >= 11933)
        assertEquals(true, c.exists("11901"))
        assertEquals(false, c.exists("12999"))
    }

    @Test
    fun `de vrije nummers van een nieuw record`() {
        val free = client().freeNumbers()
        assertNotNull(free, "ZCBS geeft de lijst met vrije nummers voor een gebruiker met recht om records aan te maken")
        assertEquals(5, free.width)
        assertTrue(free.ranges.isNotEmpty())
        assertTrue(free.ranges.last().open, "na het laatste record loopt de reeks door")
        val last = free.last!!
        assertNotNull(free.check(last, 1), "het laatste record bestaat al, dus is niet vrij")
        assertNull(free.check(last + 1, 12), "na het laatste record is ruimte genoeg")
    }

    @Test
    fun `bestaande records zijn te lezen en te begrijpen`() {
        val c = client()
        listOf("11901", "11886", "10016", "10147", "11933", "10064", "11242", "11387").forEach { nr ->
            val page = c.openForm(nr)
            if (page is Page.NotAvailable) return@forEach
            val form = assertIs<Page.Form>(page, nr).form.withMissingFields()
            assertEquals(emptyList(), RecordRebuilder.differences(form), "record $nr")
            assertTrue(form.controls.any { it.name == "a5" }, nr)
        }
        assertIs<Page.NotAvailable>(c.openForm("12999"))
    }

    @Test
    fun `opslaan en teruglezen`() {
        val c = client()
        val nr = "11910"
        val form = (c.openForm(nr) as? Page.Form)?.form?.withMissingFields()
        assumeTrue(form != null, "Record $nr bestaat niet in de lokale kopie.")
        val title = "Contracttest ${System.currentTimeMillis()} ë \"x\" & y"
        val saved = form!!.withChanges(mapOf("a5" to listOf(title), "a24" to listOf("Markt", "Recreatie"), "a6" to listOf("Regel 1\nRegel 2")))
        c.save(saved)
        val after = assertIs<Page.Form>(c.openForm(nr)).form.withMissingFields()
        assertEquals(title, after.control("a5")?.value)
        assertEquals(listOf("Markt", "Recreatie"), after.control("a24")?.values)
        // ZCBS maakt van een regeleinde " <br>": bij teruglezen staat er een spatie voor het regeleinde
        assertEquals("Regel 1\nRegel 2", after.control("a6")?.value?.replace("\r\n", "\n")?.replace(" \n", "\n"))
        assertEquals(emptyList(), RecordRebuilder.differences(after))
        assertNotNull(after.a0Fields[34])
    }
}
