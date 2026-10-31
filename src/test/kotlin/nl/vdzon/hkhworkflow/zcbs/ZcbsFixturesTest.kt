package nl.vdzon.hkhworkflow.zcbs

import java.nio.charset.StandardCharsets
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ZcbsFixturesTest {
    @Test
    fun `een gevuld formulier wordt volledig gelezen`() {
        val f = Fixtures.form("edit-gevuld.html")
        assertEquals("11902", f.number)
        assertEquals(35, f.a0Fields.size)
        assertEquals("Testrecord \"één\" & Zn", f.control("a5")?.value)
        assertEquals(ControlKind.MULTI, f.control("a24")?.kind)
        assertEquals(listOf("Kerken en Religie", "Markt"), f.control("a24")?.values)
        assertTrue((f.control("a24")?.options?.size ?: 0) > 20)
        assertEquals("== Anders ==", f.control("a3")?.value)
        assertEquals("brandweerrapport", f.control("a3b")?.value)
        assertEquals("kaft beschadigd", f.control("a10x")?.value)
        assertEquals("Regel 1 met ë en \"dubbel\" & 'enkel'\nRegel 2 na een nieuwe regel", f.control("a6")?.value)
        assertEquals(ControlKind.RADIO, f.control("a21")?.kind)
        assertEquals("9972", f.control("a21")?.value)
        assertEquals("Conditie (extra)", f.control("a10x")?.label)
        assertEquals("Titel", f.control("a5")?.label)
    }

    @Test
    fun `de helptekst van ZCBS komt bij het veld`() {
        val html = """
            <html><body><script>
              info = new Array();
              info[1] = "<strong>Verplicht veld!</strong><br>De volledige titel \"en\" ondertitel";
              info[2] = "Geen help-informatie beschikbaar.";
              info[3] = "";
            </script>
            <form name="zcbsform"><input type="hidden" name="ident" value="1"><input type="hidden" name="a0" value="1|x">
              <table><tr><th>Titel</th><th><img onMouseOver="popLayer(1)" onMouseOut="hideLayer()"></th><td><input type="text" name="a5" value="t"></td></tr>
              <tr><th>Datum</th><th><img onMouseOver="popLayer(2)"></th><td><input type="text" name="a8" value=""></td></tr>
              <tr><th>Plaats</th><th><img onMouseOver="popLayer(3)"></th><td><input type="text" name="a9" value=""></td></tr>
              <tr><th>Zonder</th><td><input type="text" name="a11" value=""></td></tr></table></form></body></html>
        """.trimIndent()
        val f = FormParser.parse(html)!!
        assertEquals("Verplicht veld!\nDe volledige titel \"en\" ondertitel", f.control("a5")?.help)
        assertEquals("", f.control("a8")?.help)
        assertEquals("", f.control("a9")?.help)
        assertEquals("", f.control("a11")?.help)
    }

    @Test
    fun `verborgen velden en a0 worden meegestuurd, bijlagevelden niet als zichtbaar veld`() {
        val f = Fixtures.form("edit-gevuld.html")
        listOf("ident", "upd_form", "write", "a0", "a32", "a33", "a34").forEach { assertTrue(f.posts.any { p -> p.name == it }, it) }
        assertEquals("edt", f.values("upd_form").single())
        assertEquals("1", f.values("write").single())
        assertTrue(f.controls.none { it.name.startsWith("add_") })
        assertTrue(f.controls.none { it.name == "a0" })
        // het zoekformulier onderaan de pagina hoort er niet bij
        assertTrue(f.posts.none { it.name == "search2" })
    }

    @Test
    fun `het opnieuw opgebouwde record is gelijk aan a0`() {
        listOf("edit-gevuld.html", "edit-leeg.html", "edit-a17.html").forEach {
            assertEquals(emptyList(), RecordRebuilder.differences(Fixtures.form(it).withMissingFields()), it)
        }
    }

    @Test
    fun `veld 17 staat als verborgen veld in het formulier als het gevuld is`() {
        val f = Fixtures.form("edit-a17.html")
        assertNull(f.control("a17"))
        assertEquals(listOf("|Test opmerking"), f.values("a17"))
        assertTrue(Fixtures.form("edit-leeg.html").values("a17").isEmpty())
    }

    @Test
    fun `veld 28 heeft geen invoerveld en gaat verloren zonder withMissingFields`() {
        val f = Fixtures.form("edit-a28.html")
        assertNull(f.control("a28"))
        assertEquals("1", f.a0Fields[28])
        assertEquals(listOf(28), RecordRebuilder.differences(f))
        val fixed = f.withMissingFields()
        assertEquals(listOf("1"), fixed.values("a28"))
        assertEquals(emptyList(), RecordRebuilder.differences(fixed))
    }

    @Test
    fun `een leeg record toont neen terwijl veld 21 leeg is`() {
        val f = Fixtures.form("edit-leeg.html")
        assertEquals("0", f.control("a21")?.value)
        assertEquals("", f.a0Fields[21])
        assertEquals(emptyList(), RecordRebuilder.differences(f))
    }

    @Test
    fun `wijzigingen vervangen alleen de opgegeven velden en bewaren de volgorde`() {
        val f = Fixtures.form("edit-gevuld.html")
        val changed = f.withChanges(mapOf("a5" to listOf("Nieuw"), "a24" to listOf("Markt", "Recreatie", "Kerken en Religie"), "a6" to listOf("een\ntwee")))
        assertEquals(listOf("Nieuw"), changed.values("a5"))
        assertEquals(listOf("Markt", "Recreatie", "Kerken en Religie"), changed.values("a24"))
        assertEquals("een\r\ntwee", changed.values("a6").single())
        assertEquals(f.values("a3b"), changed.values("a3b"))
        assertEquals(f.posts.indexOfFirst { it.name == "a5" }, changed.posts.indexOfFirst { it.name == "a5" })
        assertEquals(f.a0, changed.a0)
        // alle meerkeuzes weghalen laat geen post over
        assertEquals(emptyList(), f.withChanges(mapOf("a24" to emptyList())).values("a24"))
    }

    @Test
    fun `een gewijzigd formulier is na opbouwen gelijk aan wat de gebruiker bedoelde`() {
        val f = Fixtures.form("edit-gevuld.html").withChanges(mapOf("a5" to listOf("Heemskrink"), "a24" to listOf("Markt")))
        val built = RecordRebuilder.rebuild(f)
        assertEquals("Heemskrink", built[5])
        assertEquals("Markt", built[24])
        assertEquals("brandweerrapport", built[3])
        assertEquals("goed|kaft beschadigd", built[10])
        assertEquals("Regel 1 met ë en \"dubbel\" & 'enkel'<br>Regel 2 na een nieuwe regel", built[6].replace(" <br>", "<br>"))
    }

    @Test
    fun `pagina's worden herkend`() {
        assertIs<Page.Form>(ZcbsPages.classify(Fixtures.page("edit-gevuld.html")))
        assertIs<Page.NotAvailable>(ZcbsPages.classify(Fixtures.page("edit-niet-bestaand.html")))
        assertIs<Page.NotAvailable>(ZcbsPages.classify(Fixtures.page("weergave-niet-bestaand.html")))
        assertIs<Page.Other>(ZcbsPages.classify(Fixtures.page("weergave-bestaand.html")))
        assertIs<Page.SessionExpired>(ZcbsPages.classify(Fixtures.page("sessie-verlopen.html")))
        assertIs<Page.LoginOk>(ZcbsPages.classify(Fixtures.page("login-ok.html")))
        assertIs<Page.LoginFailed>(ZcbsPages.classify(Fixtures.page("login-fout.html")))
        assertIs<Page.RouterQuestion>(ZcbsPages.classify(Fixtures.page("router-vraag.html")))
        val locked = assertIs<Page.Locked>(ZcbsPages.classify(Fixtures.page("vergrendeld.html")))
        assertEquals(10, locked.minutes)
        assertEquals("Test Bewerker", locked.by)
        assertEquals(4, assertIs<Page.Refused>(ZcbsPages.classify("<html><body>==Sorry:4==</body></html>")).code)
        assertIs<Page.RateLimited>(ZcbsPages.classify("<p>Access ZCBS denied: using too many requests</p>"))
    }

    @Test
    fun `een niet bestaand record heeft geen formulier met a0`() {
        assertNull(FormParser.parse(Fixtures.page("edit-niet-bestaand.html")))
        assertNull(FormParser.parse(Fixtures.page("weergave-bestaand.html")))
    }

    @Test
    fun `de titel en de plaatsing op internet komen uit de gewone weergave`() {
        assertEquals(RecordSummary("Testrecord & meer", false), ZcbsPages.recordSummary(Fixtures.page("weergave-bestaand.html")))
        val online = """<table><tr><th align="left" nowrap>Titel</th>
            <th align="center"> : </th>
            <td align="left">Dorpsfeest <b>1962</b></td></tr>
            <tr><th nowrap>Plaatsing op internet</th><th> : </th><td>ja, sinds 27-10-2026</td></tr></table>"""
        assertEquals(RecordSummary("Dorpsfeest 1962", true), ZcbsPages.recordSummary(online))
        assertNull(ZcbsPages.recordSummary(Fixtures.page("weergave-niet-bestaand.html")))
    }

    @Test
    fun `het hoogste nummer komt uit de LAST-pagina`() {
        assertEquals(11933, ZcbsPages.lastNumber(Fixtures.page("last.html")))
    }

    @Test
    fun `tekens uit Word worden vervangen en andere tekens buiten latin-1 geweigerd`() {
        assertEquals("\"hallo\" - 'x'...", ZcbsText.clean("“hallo” – ‘x’…"))
        assertEquals(emptyList(), ZcbsText.unencodable("café ë ñ"))
        assertEquals(listOf('€', '日'), ZcbsText.unencodable("5 € 日"))
        val posts = listOf(Post("a5", "café ë"))
        val (type, body) = ZcbsClient.multipart(posts)
        assertTrue(type.startsWith("multipart/form-data; boundary="))
        assertTrue(String(body, StandardCharsets.ISO_8859_1).contains("café ë"))
        assertTrue(body.contains(0xE9.toByte()))
        val e = assertFailsWith<UnencodableException> { ZcbsClient.multipart(listOf(Post("a6", "prijs € 5"))) }
        assertEquals("a6", e.field)
    }

    @Test
    fun `woorden die ZCBS weigert worden herkend`() {
        listOf("SELECT", "x FROM y", "NULL", "UNION", "onload", "ONCLICK").forEach { assertNotNull(ZcbsText.refusedWord(it), it) }
        listOf("select", "from", "null", "manuscript", "scriptie", "style", "curl", "Selecteren", "Heemskerk").forEach { assertNull(ZcbsText.refusedWord(it), it) }
        assertNotNull(ZcbsText.refusedWord("zie permit.lst"))
    }

    // ---- de vrije nummers (pagina voor een nieuw record) ----

    @Test
    fun `de vrije nummers worden gelezen en aansluitende nummers vormen een reeks`() {
        val free = ZcbsPages.freeNumbers(Fixtures.page("vrije-nummers.html"), last = 11933)!!
        assertEquals(5, free.width)
        assertEquals(NumberRange(1, 9999), free.ranges.first())
        assertEquals(NumberRange(10001, 10002), free.ranges[1], "10001 en 10002 sluiten aan")
        assertEquals(NumberRange(10006, 10006), free.ranges[2])
        assertEquals(NumberRange(11935, 99000), free.ranges[free.ranges.size - 2])
        assertEquals(NumberRange(99003, 99999, open = true), free.ranges.last())
        assertEquals("00001 t/m 09999", free.format(free.ranges.first()))
        assertEquals("99003 en hoger", free.format(free.ranges.last()))
        assertEquals("10006", free.format(free.ranges[2]))
    }

    @Test
    fun `een reeks scans moet aaneengesloten in een vrije reeks passen`() {
        val free = ZcbsPages.freeNumbers(Fixtures.page("vrije-nummers.html"), last = 11933)!!
        assertNull(free.check(10001, 2))
        assertNull(free.check(11935, 12))
        assertNull(free.check(99003, 500))
        val tooMany = free.check(10001, 3)!!
        assertTrue(tooMany.contains("maar 2 aaneengesloten"), tooMany)
        assertTrue(tooMany.contains("10002"), tooMany)
        assertTrue(free.check(11934, 1)!!.contains("niet vrij"), "11934 bestaat al")
        assertTrue(free.check(10005, 1)!!.contains("niet vrij"))
        assertTrue(free.check(99990, 20)!!.contains("passen niet"), "voorbij het hoogste nummer van de collectie")
    }

    @Test
    fun `het voorstel is het nummer na het laatste record, of anders de eerste reeks met ruimte`() {
        val free = ZcbsPages.freeNumbers(Fixtures.page("vrije-nummers.html"), last = 11934)!!
        assertEquals(11935L, free.suggest(12))
        assertEquals(1L, ZcbsPages.freeNumbers(Fixtures.page("vrije-nummers.html"), last = 99999)!!.suggest(12), "na het laatste record is geen ruimte meer")
        assertEquals(listOf(NumberRange(1, 9999), NumberRange(11935, 99000), NumberRange(99003, 99999, open = true)), free.withRoom(3))
    }

    @Test
    fun `zonder bestanden is er geen voorstel`() {
        assertNull(ZcbsPages.freeNumbers(Fixtures.page("vrije-nummers.html"), last = 11934)!!.suggest(0))
    }

    @Test
    fun `een pagina zonder de lijst met vrije nummers geeft null`() {
        assertNull(ZcbsPages.freeNumbers(Fixtures.page("login-ok.html")))
    }
}
