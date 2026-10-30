package nl.vdzon.hkhworkflow.zcbs

/**
 * Een aaneengesloten reeks vrije recordnummers, van [from] tot en met [to]. [open] is waar als ZCBS de reeks open liet
 * ("99003-"): hij loopt dan door tot het hoogste nummer dat bij de lengte van de nummers past.
 */
data class NumberRange(val from: Long, val to: Long, val open: Boolean = false) {
    val size: Long get() = to - from + 1
}

/**
 * De vrije recordnummers van een collectie, zoals ZCBS ze toont als je een nieuw record aanmaakt ("Niet gebruikte (dus
 * vrije) record/databasenummers"). [last] is het hoogste bestaande nummer, als dat bekend is. Een collectie kan nummers
 * per cluster uitgeven (bijvoorbeeld 1000-1999 en 2000-2999); ZCBS laat alleen zien wat in het cluster van de gebruiker
 * past. Een reeks scans moet in één vrije reeks passen: aaneengesloten nummers.
 */
class FreeNumbers(ranges: List<NumberRange>, val width: Int, val last: Long? = null) {
    val ranges: List<NumberRange> = merge(ranges)

    fun format(number: Long): String = number.toString().padStart(width, '0')

    fun format(range: NumberRange): String =
        if (range.open) "${format(range.from)} en hoger" else if (range.size == 1L) format(range.from) else "${format(range.from)} t/m ${format(range.to)}"

    /** Reeksen waarin [count] aaneengesloten nummers passen. */
    fun withRoom(count: Int): List<NumberRange> = ranges.filter { it.size >= count }

    /** Een bruikbaar beginnummer: direct na het laatste record als daar ruimte is, anders het begin van de eerste reeks die groot genoeg is. */
    fun suggest(count: Int): Long? {
        if (count <= 0) return null
        last?.let { if (check(it + 1, count) == null) return it + 1 }
        return withRoom(count).firstOrNull()?.from
    }

    /** Past een reeks van [count] nummers vanaf [start] helemaal in één vrije reeks? @return een melding als het niet past, anders null. */
    fun check(start: Long, count: Int): String? {
        require(count > 0)
        val range = ranges.firstOrNull { start in it.from..it.to }
            ?: return "Recordnummer ${format(start)} is niet vrij in ZCBS (of valt buiten wat jij mag gebruiken). Kies een beginnummer uit de lijst met vrije nummers."
        val end = start + count - 1
        if (end > range.to) {
            val room = range.to - start + 1
            val free = if (room == 1L) "maar 1 nummer vrij" else "maar $room aaneengesloten nummers vrij"
            return "De $count bestanden passen niet vanaf ${format(start)}: daar is $free (t/m ${format(range.to)}). " +
                "Kies een reeks waar alle $count bestanden in passen, of kies minder bestanden."
        }
        return null
    }

    companion object {
        /** Voegt reeksen die aansluiten of overlappen samen: "10001" en "10002" zijn samen één reeks. */
        private fun merge(input: List<NumberRange>): List<NumberRange> {
            val result = mutableListOf<NumberRange>()
            for (r in input.sortedBy { it.from }) {
                val prev = result.lastOrNull()
                if (prev != null && r.from <= prev.to + 1) result[result.lastIndex] = NumberRange(prev.from, maxOf(prev.to, r.to), prev.open || r.open)
                else result += r
            }
            return result
        }
    }
}
