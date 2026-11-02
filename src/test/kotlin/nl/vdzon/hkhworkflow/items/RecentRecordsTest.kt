package nl.vdzon.hkhworkflow.items

import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RecentRecordsTest {
    private val day = LocalDateTime.of(2026, 10, 31, 8, 0)
    private var clock = 0L
    private val titles = mutableMapOf<String, String?>()
    private val lookups = mutableListOf<String>()
    private val busy = mutableMapOf<String, String>()
    private val added = mutableListOf<NewRecord>()

    private fun recent() = RecentRecords({ added }, { n -> lookups += n; titles[n] }, { busy[it] }, { clock })

    private fun new(number: String, user: String, at: LocalDateTime, collection: String = "archief") = NewRecord(at, user, collection, number, "scan_$number.pdf")

    @Test
    fun `toont de laatste toegevoegde records, nieuwste eerst, met wie en wanneer ze zijn toegevoegd`() {
        added += new("99090", "carla", day)
        added += new("99091", "ria", day.plusHours(1))
        titles += mapOf("99090" to "Markt", "99091" to "")

        val page = recent().page("carla", "archief", RecentScope.ALL, 20)

        assertEquals(listOf("99091", "99090"), page.records.map { it.number })
        assertEquals("ria", page.records[0].by)
        assertEquals(day.plusHours(1), page.records[0].at)
        assertTrue(page.records[0].toDescribe)
        assertTrue(page.records[1].mine, "carla heeft dit zelf toegevoegd")
        assertFalse(page.records[0].mine)
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

        clock += 301_000
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
    fun `de standaardtitel van een nieuw record telt als nog niet beschreven`() {
        assertTrue(isUndescribed(""))
        assertTrue(isUndescribed("Een nog niet beschreven record (261031)"))
        assertFalse(isUndescribed("Dorpsfeest 1975, optocht"))
        added += new("99090", "carla", day)
        titles["99090"] = "Een nog niet beschreven record (261031)"

        assertTrue(recent().page("carla", "archief", RecentScope.ALL, 20).records.single().toDescribe)
        assertEquals("99090", recent().page("carla", "archief", RecentScope.TO_DESCRIBE, 20).records.single().number)
    }

    @Test
    fun `meer laden gaat verder waar de vorige keer ophield`() {
        (1..45).forEach { added += new("990%02d".format(it), "carla", day.plusMinutes(it.toLong())); titles["990%02d".format(it)] = "t" }
        val r = recent()

        val first = r.page("carla", "archief", RecentScope.MINE, 20)
        val second = r.page("carla", "archief", RecentScope.MINE, 20, offset = 20)
        val third = r.page("carla", "archief", RecentScope.MINE, 20, offset = 40)

        assertEquals("99045", first.records.first().number)
        assertEquals("99025", second.records.first().number)
        assertTrue(first.more && second.more)
        assertEquals(listOf("99005", "99004", "99003", "99002", "99001"), third.records.map { it.number })
        assertFalse(third.more)
        assertEquals(45, (first.records + second.records + third.records).map { it.number }.distinct().size)
    }

    @Test
    fun `de scan van een record komt bij de rij en het bronbestand bepaalt het type`() {
        added += NewRecord(day, "carla", "archief", "99090", "raadhuis_001.pdf")
        added += NewRecord(day.plusMinutes(1), "carla", "archief", "99091", "dorp_003.jpg")
        titles += mapOf("99090" to "a", "99091" to "b")
        val asked = mutableListOf<Pair<String, String?>>()
        val r = RecentRecords({ added }, { titles[it] }, { null }, { clock }, { n, hint -> asked += n to hint; Media(if (hint!!.endsWith("pdf")) "pdf" else "image", "http://zcbs/$n") })

        val rows = r.page("carla", "archief", RecentScope.ALL, 20).records.associateBy { it.number }

        assertEquals("pdf", rows.getValue("99090").media?.kind)
        assertEquals("image", rows.getValue("99091").media?.kind)
        r.page("carla", "archief", RecentScope.ALL, 20)
        assertEquals(2, asked.size, "wat gevonden is, wordt onthouden")
    }
}
