package nl.vdzon.hkhworkflow.items

import java.time.LocalDateTime
import java.util.concurrent.ConcurrentHashMap
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service

/** Een record dat via de app in het archief is gezet (de flow Nieuwe bestanden). Een andere module levert ze aan via [NewRecordsSource]. */
data class NewRecord(val at: LocalDateTime, val user: String, val collection: String, val number: String, val fileName: String)

/** Levert de records die met de app zijn toegevoegd; de module `testrun` kent ze van het verwerkt-log. */
fun interface NewRecordsSource {
    fun newRecords(): List<NewRecord>
}

enum class RecentKind(val label: String) { NIEUW("nieuw"), AANGEPAST("aangepast") }

/** Welke records de lijst toont: de eigen, alle, of alle die nog geen titel hebben (nog te beschrijven). */
enum class RecentScope { MINE, ALL, TO_DESCRIBE }

/**
 * Heeft het record nog geen eigen titel? images.exe maakt nieuwe records aan met de titel "Een nog niet beschreven record (jjmmdd)"; een lege titel
 * telt ook. Zodra iemand een titel invult, is het record beschreven.
 */
fun isUndescribed(title: String): Boolean = title.isBlank() || title.trim().startsWith("Een nog niet beschreven record", ignoreCase = true)

/**
 * Een record in de lijst met recente records. [title] is de titel uit ZCBS (nog niet beschreven: zie [isUndescribed]) en null als ZCBS het niet liet zien.
 * [by] en [at] zijn van de laatste keer dat iemand het met de app toevoegde of aanpaste; [busyBy] is wie het nu open heeft.
 */
data class RecentRecord(
    val number: String,
    val kind: RecentKind,
    val by: String,
    val at: LocalDateTime,
    val title: String?,
    val mine: Boolean,
    val busyBy: String?,
) {
    val toDescribe: Boolean get() = title != null && isUndescribed(title)
}

data class RecentPage(val records: List<RecentRecord>, val more: Boolean)

/**
 * De recent toegevoegde en aangepaste records van een collectie, nieuwste eerst: wat er met de app is toegevoegd (verwerkt-log) en wat er met
 * Records aanpassen is opgeslagen ([EditHistory]), door alle gebruikers van deze app. Wat iemand rechtstreeks in ZCBS deed, staat er niet bij.
 * De titels komen uit ZCBS zelf (gewone weergave, zet geen vergrendeling) en worden even onthouden.
 */
@Service
class RecentRecords internal constructor(
    private val edits: () -> List<EditEvent>,
    private val added: () -> List<NewRecord>,
    private val titleOf: (String) -> String?,
    private val busyOf: (String) -> String?,
    private val now: () -> Long,
) {
    @Autowired
    constructor(items: ItemEditService, history: EditHistory, sources: List<NewRecordsSource>) :
        this({ history.all() }, { sources.flatMap { it.newRecords() } }, { items.summaryOf(it)?.title }, items::openedByOther, System::currentTimeMillis)

    private class Candidate(val number: String, val kind: RecentKind, val by: String, val at: LocalDateTime, val mine: Boolean)

    private val titles = ConcurrentHashMap<String, Pair<String?, Long>>()

    /** Vergeet de onthouden titel van [number], bijvoorbeeld omdat het net is opgeslagen. */
    fun forget(number: String) { titles.keys.removeIf { it.endsWith("#$number") } }

    private fun title(collection: String, number: String): String? {
        val key = "$collection#$number"
        val cached = titles[key]
        if (cached != null && now() - cached.second < TITLE_TTL_MILLIS) return cached.first
        val fresh = runCatching { titleOf(number) }.getOrNull()
        // Een mislukte opvraag (null) wordt niet onthouden: de volgende keer wordt het opnieuw geprobeerd.
        if (fresh != null) titles[key] = fresh to now()
        return fresh
    }

    private fun candidates(user: String, collection: String): List<Candidate> {
        val me = user.trim().lowercase()
        class Event(val at: LocalDateTime, val by: String, val kind: RecentKind)
        val byNumber = linkedMapOf<String, MutableList<Event>>()
        added().filter { it.collection == collection }.forEach { byNumber.getOrPut(it.number) { mutableListOf() } += Event(it.at, it.user, RecentKind.NIEUW) }
        edits().filter { it.collection == collection }.forEach { byNumber.getOrPut(it.number) { mutableListOf() } += Event(it.at, it.user, RecentKind.AANGEPAST) }
        return byNumber.map { (number, events) ->
            val last = events.maxBy { it.at }
            Candidate(number, last.kind, last.by, last.at, events.any { it.by.trim().lowercase() == me })
        }.sortedWith(compareByDescending<Candidate> { it.at }.thenByDescending { it.number })
    }

    /** De eerste [limit] records van [scope], nieuwste eerst; [RecentPage.more] zegt of er meer zijn. */
    fun page(user: String, collection: String, scope: RecentScope, limit: Int): RecentPage {
        val found = mutableListOf<RecentRecord>()
        var scanned = 0
        for (c in candidates(user, collection)) {
            if (scope == RecentScope.MINE && !c.mine) continue
            if (++scanned > MAX_SCAN) break
            val title = title(collection, c.number)
            if (scope == RecentScope.TO_DESCRIBE && !(title != null && isUndescribed(title))) continue
            found += RecentRecord(c.number, c.kind, c.by, c.at, title, c.mine, busyOf(c.number))
            if (found.size > limit) break
        }
        return RecentPage(found.take(limit), found.size > limit)
    }

    /**
     * Het volgende eigen record dat nog niet is beschreven, na [except]: het eerstvolgende hogere nummer, anders het laagste. Zo werkt iemand
     * de nieuwe records op volgorde af.
     */
    fun nextToDescribe(user: String, collection: String, except: String): String? {
        val open = candidates(user, collection).filter { it.mine && it.number != except }
            .take(MAX_SCAN)
            .filter { title(collection, it.number)?.let(::isUndescribed) == true }
            .map { it.number }
            .sorted()
        return open.firstOrNull { it > except } ?: open.firstOrNull()
    }

    private companion object {
        const val TITLE_TTL_MILLIS = 30_000L
        const val MAX_SCAN = 200
    }
}
