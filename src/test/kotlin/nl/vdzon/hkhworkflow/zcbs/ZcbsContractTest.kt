package nl.vdzon.hkhworkflow.zcbs

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import nl.vdzon.hkhworkflow.integration.ZcbsServer
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeEach

/**
 * Het contract tussen de app en ZCBS: draait de client van de app tegen de nagebouwde ZCBS in Docker, en met `-Dzcbs.real=true` precies dezelfde
 * tests tegen de echte ZCBS-scripts. Slaagt het allebei, dan gedraagt de nabouw zich op deze punten als de echte. Zonder Docker wordt dit overgeslagen.
 */
class ZcbsContractTest {
    private val zcbs: ZcbsServer by lazy { ZcbsServer.shared }

    @BeforeEach
    fun clean() {
        assumeTrue(ZcbsServer.dockerAvailable(), "Docker is niet beschikbaar.")
        zcbs.reset()
    }

    private fun client() = ZcbsClient(zcbs.baseUrl, ZcbsServer.SCRIPT)
    private fun loggedIn(user: String = ZcbsServer.EDITOR, password: String = ZcbsServer.EDITOR_PASSWORD) =
        client().also { assertEquals(LoginOutcome.Ok, it.login(user, password)) }

    @Test
    fun `inloggen lukt alleen met de goede combinatie`() {
        loggedIn()
        loggedIn(ZcbsServer.ADMIN, ZcbsServer.ADMIN_PASSWORD)
        assertIs<LoginOutcome.Failed>(client().login(ZcbsServer.EDITOR, "fout"))
        assertIs<LoginOutcome.Failed>(client().login("Niemand", "fout"))
    }

    @Test
    fun `een toegevoegde gebruiker kan inloggen`() {
        zcbs.addUser("Nieuwe Gebruiker", "Nieuw1")
        loggedIn("Nieuwe Gebruiker", "Nieuw1")
    }

    @Test
    fun `het archief bevat precies de 2 testitems`() {
        val c = loggedIn()
        assertEquals(99067, c.lastNumber())
        assertEquals(true, c.exists("99003"))
        assertEquals(true, c.exists("99067"))
        assertEquals(false, c.exists("12345"))
        assertEquals(RecordSummary("Kerkstraat 1960, de kerk van Heemskerk", false), (c.lookup("99003") as RecordLookup.Found).summary)
        assertEquals(RecordSummary("Schoolfoto klas 1, 1955", false), (c.lookup("99067") as RecordLookup.Found).summary)
        assertEquals(RecordLookup.Missing, c.lookup("12345"))
    }

    @Test
    fun `de bestanden van een item staan op de site`() {
        val c = loggedIn()
        listOf("archief/pdf/99003.pdf", "archief/small/99067.jpg", "archief/large/99067.jpg", "archief/ocr/99003.txt").forEach { assertTrue(c.fileExists(it), it) }
        assertEquals(false, c.fileExists("archief/pdf/12345.pdf"))
        assertTrue(c.fetchText("archief/ocr/99003.txt").orEmpty().contains("Kerkstraat scan 1"))
    }

    @Test
    fun `de vrije nummers liggen rond de 2 testitems`() {
        val free = assertNotNull(loggedIn().freeNumbers())
        assertEquals(5, free.width)
        assertEquals(99067L, free.last)
        assertEquals(listOf(NumberRange(1, 99002), NumberRange(99004, 99066), NumberRange(99068, 99999, open = true)), free.ranges)
    }

    @Test
    fun `iemand die alleen mag lezen krijgt geen formulier en geen vrije nummers`() {
        val c = loggedIn(ZcbsServer.READER, ZcbsServer.READER_PASSWORD)
        assertNull(c.freeNumbers())
        assertTrue(c.openForm("99067") !is Page.Form)
    }

    @Test
    fun `het formulier is voor de app volledig te begrijpen`() {
        val form = assertIs<Page.Form>(loggedIn().openForm("99003")).form.withMissingFields()
        assertEquals("99003", form.number)
        assertEquals(emptyList(), RecordRebuilder.differences(form))
        assertEquals("Kerkstraat 1960, de kerk van Heemskerk", form.control("a5")?.value)
        assertEquals(ControlKind.TEXTAREA, form.control("a6")?.kind)
        assertEquals(ControlKind.MULTI, form.control("a24")?.kind)
        assertEquals(ControlKind.SELECT, form.control("a3")?.kind)
        assertEquals(ControlKind.RADIO, form.control("a21")?.kind)
        assertEquals("Titel", form.control("a5")?.label)
        assertEquals(35, form.a0Fields.size)
        assertIs<Page.NotAvailable>(loggedIn().openForm("12345"))
    }

    @Test
    fun `opslaan en teruglezen`() {
        val c = loggedIn()
        val form = assertIs<Page.Form>(c.openForm("99003")).form.withMissingFields()
        val title = "Contract ë \"x\" & y"
        c.save(
            form.withChanges(
                mapOf(
                    "a5" to listOf(title), "a24" to listOf("Markt", "Recreatie"), "a6" to listOf("Regel 1\nRegel 2"),
                    "a3" to listOf("== Anders =="), "a3b" to listOf("brandweerrapport"), "a10" to listOf("goed"), "a10x" to listOf("kaft beschadigd"),
                ),
            ),
        )
        val after = assertIs<Page.Form>(c.openForm("99003")).form.withMissingFields()
        assertEquals(title, after.control("a5")?.value)
        assertEquals(listOf("Markt", "Recreatie"), after.control("a24")?.values)
        assertEquals("Regel 1\nRegel 2", after.control("a6")?.value?.replace("\r\n", "\n")?.replace(" \n", "\n"))
        assertEquals(listOf("== Anders =="), after.control("a3")?.values)
        assertEquals("brandweerrapport", after.control("a3b")?.value)
        assertEquals("goed", after.control("a10")?.value)
        assertEquals("kaft beschadigd", after.control("a10x")?.value)
        assertEquals(emptyList(), RecordRebuilder.differences(after))
        assertTrue(after.a0Fields[32].endsWith("|system"), after.a0Fields[32])
        assertTrue(after.a0Fields[34].endsWith("|${ZcbsServer.EDITOR}"), after.a0Fields[34])
        assertEquals(title, (c.lookup("99003") as RecordLookup.Found).summary.title)
        assertTrue(zcbs.readFile("cgi-bin/archief/updates.txt").lines().last { it.isNotEmpty() }.split("\t")[5].startsWith("Contract"))
    }

    @Test
    fun `een ongewijzigd formulier opslaan voegt niets toe aan de database`() {
        val c = loggedIn()
        val before = zcbs.readFile("cgi-bin/archief/updates.txt")
        c.save(assertIs<Page.Form>(c.openForm("99003")).form.withMissingFields())
        assertEquals(before, zcbs.readFile("cgi-bin/archief/updates.txt"))
    }

    @Test
    fun `een geopend record is vergrendeld voor een ander, niet voor jezelf`() {
        assertIs<Page.Form>(loggedIn().openForm("99003"))
        assertIs<Page.Form>(loggedIn().openForm("99003"))
        val locked = assertIs<Page.Locked>(loggedIn(ZcbsServer.ADMIN, ZcbsServer.ADMIN_PASSWORD).openForm("99003"))
        assertEquals(ZcbsServer.EDITOR, locked.by)
        assertEquals(10, locked.minutes)
        assertIs<Page.Form>(loggedIn(ZcbsServer.ADMIN, ZcbsServer.ADMIN_PASSWORD).openForm("99067"))
    }

    @Test
    fun `bekijken vergrendelt niet`() {
        loggedIn().lookup("99003")
        assertIs<Page.Form>(loggedIn(ZcbsServer.ADMIN, ZcbsServer.ADMIN_PASSWORD).openForm("99003"))
    }

    @Test
    fun `na het opslaan blijft het record nog vergrendeld voor een ander`() {
        val c = loggedIn()
        c.save(assertIs<Page.Form>(c.openForm("99003")).form.withMissingFields().withChanges(mapOf("a5" to listOf("Nieuwe titel"))))
        assertEquals(ZcbsServer.EDITOR, assertIs<Page.Locked>(loggedIn(ZcbsServer.ADMIN, ZcbsServer.ADMIN_PASSWORD).openForm("99003")).by)
    }

    @Test
    fun `zonder inlog geen formulier`() {
        assertTrue(client().openForm("99003") !is Page.Form)
        assertNull(client().freeNumbers())
    }

    @Test
    fun `een woord dat ZCBS weigert geeft een lege pagina en wijzigt niets`() {
        val c = loggedIn()
        val before = zcbs.readFile("cgi-bin/archief/updates.txt")
        val form = assertIs<Page.Form>(c.openForm("99003")).form.withMissingFields()
        assertIs<Page.Refused>(c.save(form.withChanges(mapOf("a5" to listOf("UNION van werknemers")))))
        assertEquals(before, zcbs.readFile("cgi-bin/archief/updates.txt"))
    }

    @Test
    fun `de beginstand komt terug na een reset`() {
        val c = loggedIn()
        c.save(assertIs<Page.Form>(c.openForm("99003")).form.withMissingFields().withChanges(mapOf("a5" to listOf("Gewijzigd"))))
        zcbs.reset()
        assertEquals("Kerkstraat 1960, de kerk van Heemskerk", (loggedIn().lookup("99003") as RecordLookup.Found).summary.title)
        assertIs<Page.Form>(loggedIn(ZcbsServer.ADMIN, ZcbsServer.ADMIN_PASSWORD).openForm("99003"))
    }
}
