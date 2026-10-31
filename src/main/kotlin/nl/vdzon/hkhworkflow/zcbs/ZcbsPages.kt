package nl.vdzon.hkhworkflow.zcbs

/** Wat een antwoord van ZCBS is. ZCBS geeft altijd HTTP 200; alleen de tekst verraadt wat er gebeurd is. */
sealed interface Page {
    data class Form(val form: ItemForm) : Page
    data class Locked(val minutes: Int?, val by: String?) : Page
    data object NotAvailable : Page
    data object SessionExpired : Page
    data object NotLoggedIn : Page
    data object LoginOk : Page
    data object LoginFailed : Page
    data object RouterQuestion : Page
    data class Refused(val code: Int) : Page
    data object RateLimited : Page
    data class Other(val text: String) : Page
}

/** Wat de gewone weergave van een record laat zien: de titel en of het record op internet staat (null als de pagina dat niet zegt). */
data class RecordSummary(val title: String, val online: Boolean?)

object ZcbsPages {
    private val sorry = Regex("""==Sorry:(\d)==""")
    private val minutes = Regex("""nog voor\s+(\d+)\s+minuten""")
    private val lockedBy = Regex("""\(\s*([^)]+?)\s*\)\s*voor bewerking""")

    private val lastList = Regex("""zcbs_archief\s*=\s*"([^"]*)"""")

    /** Het hoogste nummer uit het antwoord op `search=LAST` (de regel `zcbs_archief = "0000 11933 0000"`). */
    fun lastNumber(html: String): Int? = lastList.find(html)?.groupValues?.get(1)?.split(' ')?.mapNotNull { it.toIntOrNull() }?.maxOrNull()

    private fun cell(html: String, label: String): String? =
        Regex("""<th[^>]*>\s*${Regex.escape(label)}\s*</th>\s*(?:<th[^>]*>.*?</th>\s*)?<td[^>]*>(.*?)</td>""", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
            .find(html)?.groupValues?.get(1)?.let { plain(it) }

    /**
     * De titel en de plaatsing op internet uit de gewone weergave van een record (`ident=NNNNN`, alleen lezen, zet geen vergrendeling).
     * "Plaatsing op internet" staat er als "nee" of als ja met een datum. Null als dit de weergave van een record niet is.
     */
    fun recordSummary(html: String): RecordSummary? {
        val title = cell(html, "Titel") ?: return null
        val internet = cell(html, "Plaatsing op internet")?.lowercase()
        return RecordSummary(decode(title), internet?.let { it.isNotEmpty() && !it.startsWith("nee") && !it.startsWith("neen") })
    }

    private fun decode(text: String): String =
        text.replace("&quot;", "\"").replace("&lt;", "<").replace("&gt;", ">").replace("&#39;", "'").replace("&amp;", "&")

    private val freeCell = Regex("""<td>&nbsp;(\d+)(?:-(\d*))?&nbsp;</td>""")

    /**
     * De vrije nummers uit de pagina voor een nieuw record (`ident=0&upd_form=new`): een tabel met losse nummers ("10006"),
     * reeksen ("11935-99000") en een open reeks ("99003-"). Null als dit de pagina niet is, bijvoorbeeld omdat je niet bent ingelogd
     * of geen recht hebt om records aan te maken.
     */
    fun freeNumbers(html: String, last: Long? = null): FreeNumbers? {
        val start = html.indexOf("Niet gebruikte (dus vrije)")
        if (start < 0) return null
        val table = html.substring(start).substringBefore("</table>")
        val cells = freeCell.findAll(table).map { it.groupValues[1] to it.groups[2]?.value }.toList()
        if (cells.isEmpty()) return null
        val width = cells.flatMap { listOfNotNull(it.first, it.second?.takeIf { s -> s.isNotEmpty() }) }.maxOf { it.length }
        val limit = "9".repeat(width).toLong()
        val ranges = cells.map { (from, to) ->
            when {
                to == null -> NumberRange(from.toLong(), from.toLong())
                to.isEmpty() -> NumberRange(from.toLong(), limit, open = true)
                else -> NumberRange(from.toLong(), to.toLong())
            }
        }
        return FreeNumbers(ranges, width, last)
    }

    private fun plain(html: String): String =
        html.replace(Regex("<script.*?</script>", RegexOption.DOT_MATCHES_ALL), " ").replace(Regex("<[^>]+>"), " ").replace("&nbsp;", " ").replace(Regex("\\s+"), " ").trim()

    fun classify(html: String): Page {
        sorry.find(html)?.let { return Page.Refused(it.groupValues[1].toInt()) }
        val text = plain(html)
        if (text.contains("Access ZCBS denied")) return Page.RateLimited
        if (text.contains("Tijdelijk geblokkeerd")) {
            return Page.Locked(minutes.find(text)?.groupValues?.get(1)?.toIntOrNull(), lockedBy.find(text)?.groupValues?.get(1))
        }
        if (text.contains("sessie is verlopen")) return Page.SessionExpired
        if (Regex("combinatie\\s+niet\\s+herkend").containsMatchIn(text) || text.contains("Onjuist wachtwoord ingevuld")) return Page.LoginFailed
        if (html.contains("name=\"veld\"") && html.contains("value=\"access\"") && text.contains("Wie bent u")) return Page.RouterQuestion
        FormParser.parse(html)?.let { return Page.Form(it) }
        if (Regex("Database record nr\\. \\d+ is niet beschikbaar").containsMatchIn(text)) return Page.NotAvailable
        if (Regex("combinatie\\s+herkend").containsMatchIn(text)) return Page.LoginOk
        if (text.contains("Autorisatie interne gebruiker") || text.contains("Uw naam:")) return Page.NotLoggedIn
        return Page.Other(text.take(200))
    }
}
