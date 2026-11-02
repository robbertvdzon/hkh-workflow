package nl.vdzon.hkhworkflow.items

import java.nio.file.Files
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import nl.vdzon.hkhworkflow.settings.SettingsService
import nl.vdzon.hkhworkflow.settings.WorkflowProperties
import tools.jackson.databind.json.JsonMapper

class RecentRecordsTest {
    private val day = LocalDateTime.of(2026, 10, 31, 8, 0)
    private var clock = 0L
    private val titles = mutableMapOf<String, String?>()
    private val lookups = mutableListOf<String>()
    private val busy = mutableMapOf<String, String>()
    private val added = mutableListOf<NewRecord>()
    private val edited = mutableListOf<EditEvent>()

    private fun recent() = RecentRecords({ edited }, { added }, { n -> lookups += n; titles[n] }, { busy[it] }, { clock })

    private fun new(number: String, user: String, at: LocalDateTime, collection: String = "archief") = NewRecord(at, user, collection, number, "scan_$number.pdf")

    @Test
    fun `toont de laatste records, nieuwste eerst, en bij elk record de laatste keer dat iemand eraan werkte`() {
        added += new("99090", "carla", day)
        added += new("99091", "carla", day.plusHours(1))
        edited += EditEvent(day.plusHours(3), "ria", "archief", "99090")
        titles += mapOf("99090" to "Markt", "99091" to "")

        val page = recent().page("carla", "archief", RecentScope.ALL, 20)

        assertEquals(listOf("99090", "99091"), page.records.map { it.number })
        val first = page.records[0]
        assertEquals(RecentKind.AANGEPAST, first.kind)
        assertEquals("ria", first.by, "wie het record als laatste aanpaste")
        assertTrue(first.mine, "carla heeft het zelf toegevoegd, dus het is ook van haar")
        assertEquals(RecentKind.NIEUW, page.records[1].kind)
        assertTrue(page.records[1].toDescribe)
        assertFalse(page.more)
    }

    @Test
    fun `standaard alleen eigen records, en records van een andere collectie tellen niet mee`() {
        added += new("99090", "carla", day)
        added += new("99091", "ria", day.plusHours(1))
        added += new("55555", "carla", day.plusHours(2), collection = "objecten")
        titles += mapOf("99090" to "a", "99091" to "b")

        assertEquals(listOf("99090"), recent().page("Carla", "archief", RecentScope.MINE, 20).records.map { it.number })
        assertEquals(listOf("99091", "99090"), recent().page("Carla", "archief", RecentScope.ALL, 20).records.map { it.number })
    }

    @Test
    fun `nog te beschrijven toont alleen records zonder titel, van iedereen`() {
        added += new("99090", "carla", day)
        added += new("99091", "ria", day.plusHours(1))
        added += new("99092", "ria", day.plusHours(2))
        titles += mapOf("99090" to "", "99091" to "Gedaan", "99092" to "")

        val page = recent().page("carla", "archief", RecentScope.TO_DESCRIBE, 20)

        assertEquals(listOf("99092", "99090"), page.records.map { it.number })
    }

    @Test
    fun `een titel die ZCBS niet gaf is onbekend, geen nog te beschrijven, en wordt opnieuw geprobeerd`() {
        added += new("99090", "carla", day)
        titles["99090"] = null
        val r = recent()

        assertNull(r.page("carla", "archief", RecentScope.ALL, 20).records.single().title)
        assertTrue(r.page("carla", "archief", RecentScope.TO_DESCRIBE, 20).records.isEmpty())
        assertEquals(2, lookups.size, "elke keer opnieuw, want een mislukte opvraag wordt niet onthouden")
    }

    @Test
    fun `titels worden even onthouden en na het opslaan vergeten`() {
        added += new("99090", "carla", day)
        titles["99090"] = ""
        val r = recent()

        r.page("carla", "archief", RecentScope.ALL, 20)
        r.page("carla", "archief", RecentScope.ALL, 20)
        assertEquals(1, lookups.size)

        titles["99090"] = "Nu beschreven"
        r.forget("99090")
        assertEquals("Nu beschreven", r.page("carla", "archief", RecentScope.ALL, 20).records.single().title)

        clock += 31_000
        titles["99090"] = "Weer anders"
        assertEquals("Weer anders", r.page("carla", "archief", RecentScope.ALL, 20).records.single().title)
    }

    @Test
    fun `de lijst heeft een maximum en meldt dat er meer is`() {
        (1..25).forEach { added += new("990%02d".format(it), "carla", day.plusMinutes(it.toLong())) }
        (1..25).forEach { titles["990%02d".format(it)] = "t" }

        val page = recent().page("carla", "archief", RecentScope.MINE, 20)

        assertEquals(20, page.records.size)
        assertTrue(page.more)
        assertEquals("99025", page.records.first().number)
        assertFalse(recent().page("carla", "archief", RecentScope.MINE, 40).more)
    }

    @Test
    fun `wie een record nu open heeft staat erbij`() {
        added += new("99090", "carla", day)
        titles["99090"] = ""
        busy["99090"] = "Ria"

        assertEquals("Ria", recent().page("carla", "archief", RecentScope.ALL, 20).records.single().busyBy)
    }

    @Test
    fun `het volgende te beschrijven record is het eerstvolgende hogere eigen nummer, anders het laagste`() {
        listOf("99090", "99091", "99092", "99093").forEachIndexed { i, n -> added += new(n, "carla", day.plusMinutes(i.toLong())) }
        added += new("99095", "ria", day.plusHours(1))
        titles += mapOf("99090" to "", "99091" to "klaar", "99092" to "", "99093" to "", "99095" to "")
        val r = recent()

        assertEquals("99092", r.nextToDescribe("carla", "archief", "99090"))
        assertEquals("99093", r.nextToDescribe("carla", "archief", "99092"))
        assertEquals("99090", r.nextToDescribe("carla", "archief", "99093"), "daarna weer vooraan beginnen")
        assertNull(r.nextToDescribe("ria", "archief", "99095"), "ria heeft verder niets dat nog beschreven moet worden")
    }

    @Test
    fun `het bewerkt-log bewaart wie welk record wanneer opsloeg en leest het weer`() {
        val dir = Files.createTempDirectory("edit-history")
        val props = WorkflowProperties(settingsFile = dir.resolve("settings.json").toString(), editLog = "bewerkt.tsv")
        val history = EditHistory(SettingsService(JsonMapper.builder().build(), props), props)
        assertTrue(history.all().isEmpty())

        history.add(EditEvent(day, "carla", "archief", "99090"))
        history.add(EditEvent(day.plusMinutes(5), "ria\tx", "archief", "99091"))

        val all = history.all()
        assertEquals(listOf("99090", "99091"), all.map { it.number })
        assertEquals("ria x", all[1].user, "een tab in een naam breekt het bestand niet")
        assertEquals(dir.resolve("bewerkt.tsv"), history.file())
    }

    @Test
    fun `de standaardtitel van een nieuw record telt als nog niet beschreven`() {
        assertTrue(isUndescribed(""))
        assertTrue(isUndescribed("Een nog niet beschreven record (261031)"))
        assertFalse(isUndescribed("Dorpsfeest 1975, optocht"))
        added += new("99090", "carla", day)
        titles["99090"] = "Een nog niet beschreven record (261031)"

        assertTrue(recent().page("carla", "archief", RecentScope.ALL, 20).records.single().toDescribe)
        assertEquals("99090", recent().page("carla", "archief", RecentScope.TO_DESCRIBE, 20).records.single().number)
    }
}
