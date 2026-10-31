package nl.vdzon.hkhworkflow.items

import java.nio.file.Path
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import nl.vdzon.hkhworkflow.settings.SettingsService
import nl.vdzon.hkhworkflow.settings.WorkflowProperties
import nl.vdzon.hkhworkflow.settings.WorkflowSettings
import nl.vdzon.hkhworkflow.zcbs.FakeZcbs
import nl.vdzon.hkhworkflow.zcbs.NumberRange
import org.junit.jupiter.api.io.TempDir
import tools.jackson.databind.ObjectMapper

class ItemEditServiceTest {
    @TempDir
    lateinit var dir: Path

    private lateinit var zcbs: FakeZcbs
    private lateinit var settings: SettingsService
    private lateinit var service: ItemEditService

    @BeforeTest
    fun setUp() {
        zcbs = FakeZcbs()
        zcbs.addRecord("edit-leeg.html", "11905")
        zcbs.addRecord("edit-gevuld.html", "11902")
        settings = SettingsService(ObjectMapper(), WorkflowProperties(settingsFile = dir.resolve("s.json").toString()))
        settings.save(WorkflowSettings(zcbsBaseUrl = zcbs.baseUrl, collection = "archief"))
        Current.set(null)
        service = ItemEditService(settings)
        assertNull(service.login("Test Beheerder", "Beheer1"))
        service.startBatch(
            listOf(BatchRecord("11905", "scan_0412.pdf", "Ria", "K:\\scabslelly\\Ria\\map57"), BatchRecord("11902", "scan_0413.pdf", "Ria", "K:\\scabslelly\\Ria\\map57"), BatchRecord("11999", "scan_0414.pdf")),
        )
    }

    @AfterTest
    fun tearDown() {
        Current.set(null)
        zcbs.close()
    }

    private fun opened(number: String) = assertIs<OpenOutcome.Ready>(service.open(number))

    @Test
    fun `twee gebruikers hebben elk hun eigen ZCBS-sessie en gekozen records`() {
        assertEquals("Test Beheerder", service.user)
        assertEquals(3, service.records().size)

        // Zonder gebruiker aan het woord is er niemand ingelogd.
        Current.set(null)
        assertNull(service.user)
        assertFalse(service.loggedIn())
        assertEquals(emptyList(), service.records())

        // Een tweede gebruiker logt in: dat logt de eerste niet uit en ze delen geen records.
        assertNull(service.login("Test Bewerker", "Bewerk1"))
        assertEquals("Test Bewerker", service.user)
        assertEquals(emptyList(), service.records())
        service.startBatch(listOf(BatchRecord("11905")))
        assertEquals(listOf("11905"), service.records().map { it.number })
        Current.runAs("Test Beheerder", "archief") {
            assertTrue(service.loggedIn())
            assertEquals(3, service.records().size)
        }
        assertEquals(listOf("Test Beheerder", "Test Bewerker"), service.users())

        // Uitloggen geldt alleen voor wie aan het woord is.
        service.logout()
        assertFalse(service.loggedIn())
        Current.runAs("Test Beheerder", "archief") { assertTrue(service.loggedIn()) }
        assertFalse(service.hasSession("Test Bewerker"))
        assertTrue(service.hasSession("Test Beheerder"))
    }

    @Test
    fun `inloggen controleert naam en wachtwoord en het adres van ZCBS`() {
        val other = ItemEditService(settings)
        assertContains(other.login("Test Beheerder", "fout")!!, "niet herkend")
        assertFalse(other.loggedIn())
        assertNull(other.login("Test Beheerder", "Beheer1"))
        assertTrue(other.loggedIn())
        assertEquals("Test Beheerder", other.user)
        other.logout()
        assertFalse(other.loggedIn())
        settings.save(settings.get().copy(zcbsBaseUrl = ""))
        assertContains(ItemEditService(settings).login("a", "b")!!, "Instellingen")
        settings.save(settings.get().copy(zcbsBaseUrl = "http://127.0.0.1:1"))
        assertContains(ItemEditService(settings).login("a", "b")!!, "Geen verbinding")
    }

    @Test
    fun `inloggen probeert alle collecties en onthoudt waar je rechten hebt`() {
        zcbs.grant("beeldbank", "Test Beheerder")
        zcbs.grant("bidprent", "Test Bewerker")
        val other = ItemEditService(settings)
        assertNull(other.login("Test Beheerder", "Beheer1"))
        assertEquals(listOf("archief", "beeldbank"), other.rights(), "de rest geeft geen recht of bestaat niet op de server")
        assertTrue(other.canUse("archief"))
        assertFalse(other.canUse("beeldbank"), "rechten, maar de app werkt er nog niet mee")
        assertFalse(other.canUse("bidprent"))
        assertContains(zcbs.requests, "POST /cgi-bin/beeldbank.pl")
        assertContains(zcbs.requests, "POST /cgi-bin/bidprent.pl", "ook waar hij geen recht heeft wordt gevraagd")
        other.logout()
        assertEquals(emptyList(), other.rights())
    }

    @Test
    fun `iemand met alleen rechten bij een andere collectie kan inloggen, maar niet met het archief werken`() {
        zcbs.grant("beeldbank", "Alleen Beeldbank", "Beeld1")
        val other = ItemEditService(settings)
        // het archief kent hem niet, de beeldbank wel: ZCBS beslist per collectie
        assertNull(other.login("Alleen Beeldbank", "Beeld1"))
        assertTrue(other.loggedIn())
        assertEquals(listOf("beeldbank"), other.rights())
        assertFalse(other.canUse("archief"))
        other.startBatch(listOf(BatchRecord("11905", "scan.pdf")))
        assertEquals(OpenOutcome.NeedsLogin, other.open("11905"), "geen rechten in het archief: opnieuw inloggen helpt niet, maar er wordt niets gedaan")
    }

    @Test
    fun `een naam zonder rechten bij welke collectie dan ook kan niet inloggen`() {
        val other = ItemEditService(settings)
        assertContains(other.login("Onbekend", "fout")!!, "nergens rechten")
        assertFalse(other.loggedIn())
        assertEquals(emptyList(), other.rights())
    }

    @Test
    fun `openen haalt het formulier op en zet de status op bezig`() {
        val ready = opened("11905")
        assertEquals(EditStatus.BEZIG, ready.record.status)
        assertEquals("11905", ready.form.number)
        assertNotNull(service.record("11905")?.baseline)
        assertContains(zcbs.requests, "GET /cgi-bin/archief.pl?ident=11905&upd_form=edt")
    }

    @Test
    fun `een record dat niet bestaat of niet begrepen wordt, wordt niet bewerkt`() {
        assertContains(assertIs<OpenOutcome.Blocked>(service.open("11999")).message, "bestaat niet")
        assertEquals(EditStatus.FOUT, service.record("11999")?.status)
        zcbs.corruptA0("11902")
        assertContains(assertIs<OpenOutcome.Blocked>(service.open("11902")).message, "niet goed genoeg")
        assertEquals(EditStatus.ONVEILIG, service.record("11902")?.status)
    }

    @Test
    fun `een vergrendeld record wordt gemeld`() {
        zcbs.lock("11905", "Test Bewerker")
        val blocked = assertIs<OpenOutcome.Blocked>(service.open("11905"))
        assertContains(blocked.message, "Test Bewerker")
        assertEquals(EditStatus.GEBLOKKEERD, service.record("11905")?.status)
    }

    @Test
    fun `opnieuw inloggen gebeurt vanzelf als de sessie bij ZCBS verlopen is`() {
        zcbs.killSessions()
        opened("11905")
    }

    @Test
    fun `opslaan stuurt het hele formulier en leest het terug`() {
        opened("11905")
        val result = service.save("11905", mapOf("a5" to listOf("Heemskrink, \u201Cjuni\u201D 1981 \u00EB"), "a24" to listOf("Markt", "Recreatie")))
        assertEquals(SaveOutcome.Saved, result)
        val sent = zcbs.posts.single()
        assertEquals(listOf("Heemskrink, \"juni\" 1981 \u00EB"), sent.filter { it.name == "a5" }.map { it.value })
        assertEquals(listOf("Markt", "Recreatie"), sent.filter { it.name == "a24" }.map { it.value })
        listOf("a0", "ident", "upd_form", "write", "a34").forEach { assertTrue(sent.any { p -> p.name == it }, it) }
        assertTrue(zcbs.rawBodies.single().contains(0xEB.toByte()), "ë moet als latin-1 byte gaan")
        assertEquals(EditStatus.OPGESLAGEN, service.record("11905")?.status)
        assertEquals("Heemskrink, \"juni\" 1981 \u00EB", service.record("11905")?.title)
        assertEquals("Heemskrink, \"juni\" 1981 \u00EB", zcbs.records.getValue("11905").form.control("a5")?.value)
        assertTrue(service.handledCount() == 1)
    }

    @Test
    fun `zonder wijziging wordt er niets gepost`() {
        opened("11902")
        assertEquals(SaveOutcome.NoChanges, service.save("11902", emptyMap()))
        assertTrue(zcbs.posts.isEmpty())
    }

    @Test
    fun `tekens en woorden die ZCBS weigert worden vooraf geblokkeerd zonder iets te sturen`() {
        opened("11905")
        val euro = assertIs<SaveOutcome.Blocked>(service.save("11905", mapOf("a5" to listOf("prijs \u20AC 5"))))
        assertContains(euro.messages.single(), "'\u20AC'")
        val word = assertIs<SaveOutcome.Blocked>(service.save("11905", mapOf("a6" to listOf("SELECT alles"))))
        assertContains(word.messages.single(), "SELECT")
        assertTrue(zcbs.posts.isEmpty())
        val preview = assertIs<PreviewOutcome.Ok>(service.preview("11905", mapOf("a5" to listOf("prijs \u20AC 5"))))
        assertEquals(1, preview.blockers.size)
        assertEquals(1, preview.changes.size)
    }

    @Test
    fun `een voorbeeld toont wat er verandert`() {
        opened("11902")
        val preview = assertIs<PreviewOutcome.Ok>(service.preview("11902", mapOf("a5" to listOf("Nieuw"), "a24" to listOf("Markt"))))
        assertEquals(listOf("Titel", "Thema"), preview.changes.map { it.label })
        assertEquals("Kerken en Religie; Markt", preview.changes[1].old)
        assertEquals("Markt", preview.changes[1].new)
    }

    @Test
    fun `een wijziging van een ander in een ander veld blijft behouden en geeft geen conflict`() {
        opened("11902")
        zcbs.editElsewhere("11902", mapOf("a7" to listOf("Auteur van een collega")))
        assertEquals(SaveOutcome.Saved, service.save("11902", mapOf("a5" to listOf("Mijn titel"))))
        val stored = zcbs.records.getValue("11902").form
        assertEquals("Mijn titel", stored.control("a5")?.value)
        assertEquals("Auteur van een collega", stored.control("a7")?.value)
    }

    @Test
    fun `dezelfde veldwijziging door een ander geeft een conflict dat je kunt oplossen`() {
        opened("11902")
        zcbs.editElsewhere("11902", mapOf("a5" to listOf("Titel van een collega")))
        val conflict = assertIs<SaveOutcome.Conflict>(service.save("11902", mapOf("a5" to listOf("Mijn titel"))))
        assertEquals("Mijn titel", conflict.rows.single().mine)
        assertEquals("Titel van een collega", conflict.rows.single().theirs)
        assertTrue(zcbs.posts.isEmpty())
        assertEquals(SaveOutcome.Saved, service.resolveConflict("11902", true, mapOf("a5" to listOf("Mijn titel"))))
        assertEquals("Mijn titel", zcbs.records.getValue("11902").form.control("a5")?.value)
    }

    @Test
    fun `bij een conflict kun je de versie van ZCBS kiezen`() {
        opened("11902")
        zcbs.editElsewhere("11902", mapOf("a5" to listOf("Titel van een collega")))
        assertIs<SaveOutcome.Conflict>(service.save("11902", mapOf("a5" to listOf("Mijn titel"), "a7" to listOf("Mijn auteur"))))
        service.resolveConflict("11902", false, mapOf("a5" to listOf("Mijn titel"), "a7" to listOf("Mijn auteur")))
        val shown = service.controlsFor("11902").associate { it.name to it.value }
        assertEquals("Titel van een collega", shown["a5"])
        assertEquals("Mijn auteur", shown["a7"])
        assertEquals(SaveOutcome.Saved, service.save("11902", service.record("11902")!!.draft!!))
        assertEquals("Mijn auteur", zcbs.records.getValue("11902").form.control("a7")?.value)
        assertEquals("Titel van een collega", zcbs.records.getValue("11902").form.control("a5")?.value)
    }

    @Test
    fun `een record dat intussen vergrendeld is, houdt de invoer vast`() {
        opened("11905")
        zcbs.lock("11905", "Test Bewerker")
        val locked = assertIs<SaveOutcome.Locked>(service.save("11905", mapOf("a5" to listOf("Mijn titel"))))
        assertContains(locked.message, "Test Bewerker")
        assertEquals(EditStatus.GEBLOKKEERD, service.record("11905")?.status)
        assertEquals(listOf("Mijn titel"), service.record("11905")?.draft?.get("a5"))
        zcbs.unlock("11905")
        assertEquals(SaveOutcome.Saved, service.save("11905", service.record("11905")!!.draft!!))
    }

    @Test
    fun `een sessie die verloopt tussen openen en opslaan is geen probleem`() {
        opened("11905")
        zcbs.killSessions()
        assertEquals(SaveOutcome.Saved, service.save("11905", mapOf("a5" to listOf("Titel"))))
    }

    @Test
    fun `als ZCBS een veld niet bewaart, geldt het niet als opgeslagen`() {
        opened("11905")
        zcbs.ignoreFields = setOf("a5")
        val failed = assertIs<SaveOutcome.Failed>(service.save("11905", mapOf("a5" to listOf("Titel"))))
        assertContains(failed.message, "Titel")
        assertEquals(EditStatus.FOUT, service.record("11905")?.status)
    }

    @Test
    fun `een geweigerde opslag wordt gemeld`() {
        opened("11905")
        zcbs.refuseSaves = true
        assertContains(assertIs<SaveOutcome.Failed>(service.save("11905", mapOf("a5" to listOf("Titel")))).message, "weigerde")
        assertNotEquals(EditStatus.OPGESLAGEN, service.record("11905")?.status)
    }

    private fun assertNotEquals(a: Any?, b: Any?) = assertTrue(a != b)

    @Test
    fun `later zetten, en afsluiten kan pas als alles opgeslagen is of op later staat`() {
        service.startBatch(listOf(BatchRecord("11905", "a.pdf"), BatchRecord("11902", "b.pdf")))
        assertFalse(service.allHandled())
        opened("11905")
        service.save("11905", mapOf("a5" to listOf("Titel")))
        assertFalse(service.allHandled())
        service.later("11902")
        assertTrue(service.allHandled())
        assertEquals(1, service.laterCount())
    }

    @Test
    fun `welke nummers bestaan nog niet`() {
        assertEquals(listOf("11999"), service.missingInZcbs(listOf("11905", "11999", "11902")))
    }

    @Test
    fun `de vrije nummers komen uit ZCBS, met het laatste record`() {
        val free = service.freeNumbers()!!
        assertEquals(5, free.width)
        assertEquals(11905L, free.last)
        assertEquals(listOf(NumberRange(1, 11901), NumberRange(11903, 11904), NumberRange(11906, 99999, open = true)), free.ranges)
        assertNull(free.check(11906, 12), "na het laatste record is ruimte genoeg")
        assertTrue(free.check(11903, 3)!!.contains("maar 2 aaneengesloten"))
    }

    @Test
    fun `zonder inlog zijn er geen vrije nummers`() {
        service.logout()
        assertNull(service.freeNumbers())
    }

    @Test
    fun `een nummer, een reeks of meer daarvan kiezen en alleen bestaande records bewaren`() {
        val result = service.chooseRecords("11902-11903, 11905 11999")
        assertNull(result.error)
        assertEquals(2, result.count)
        assertEquals(listOf("11903", "11999"), result.missing)
        assertEquals(listOf("11902", "11905"), service.records().map { it.number })
    }

    @Test
    fun `een ongeldige keuze geeft een duidelijke melding`() {
        assertTrue(service.chooseRecords("abc").error!!.contains("geen recordnummer"))
        assertTrue(service.chooseRecords("11905-11902").error!!.contains("eerste nummer hoger"))
        assertTrue(service.chooseRecords("").error!!.contains("Vul een recordnummer"))
        assertTrue(service.chooseRecords("1-2000").error!!.contains("maximaal 500"))
        assertTrue(service.chooseRecords("00099998").error!!.contains("bestaan niet"))
        assertEquals(listOf("11905", "11902", "11999"), service.records().map { it.number }, "een mislukte keuze laat de lijst met rust")
    }
}
