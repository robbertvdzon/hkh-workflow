package nl.vdzon.hkhworkflow.zcbs

import org.jsoup.Jsoup
import org.jsoup.nodes.Element

/** Eén naam-waardepaar zoals een browser het zou posten. Bij een meerkeuzelijst staat er één per gekozen optie. */
data class Post(val name: String, val value: String)

enum class ControlKind { TEXT, TEXTAREA, SELECT, MULTI, RADIO }

/** Een veld dat de gebruiker ziet en kan wijzigen, met de keuzes die ZCBS zelf in het formulier zet. */
data class Control(
    val name: String,
    val label: String,
    val kind: ControlKind,
    val options: List<String> = emptyList(),
    val values: List<String> = emptyList(),
    /** De helptekst die ZCBS bij het veld toont (het vraagteken), als platte tekst; leeg als er geen is. */
    val help: String = "",
) {
    val value: String get() = values.firstOrNull().orEmpty()
}

/**
 * Het bewerkformulier van één record zoals ZCBS het levert: alle velden die een browser zou posten ([posts], met de
 * verborgen velden en `a0`) en de velden die de gebruiker ziet ([controls]).
 */
class ItemForm(val number: String, val posts: List<Post>, val controls: List<Control>) {
    /** Het hele oude record, tab-gescheiden (veld 0 t/m 34). ZCBS vergelijkt hiermee of er iets is gewijzigd. */
    val a0: String get() = posts.firstOrNull { it.name == "a0" }?.value.orEmpty()
    val a0Fields: List<String> get() = a0.split("\t")

    fun control(name: String): Control? = controls.firstOrNull { it.name == name }

    fun values(name: String): List<String> = posts.filter { it.name == name }.map { it.value }

    /** De huidige waarden van alle zichtbare velden, bijvoorbeeld om met een nieuwe invoer te vergelijken. */
    fun currentValues(): Map<String, List<String>> = controls.associate { it.name to it.values }

    /**
     * Zet de waarden van de opgegeven velden en laat al het andere ongemoeid. Een regeleinde in een tekstvak gaat als
     * CR+LF, zoals een browser dat doet.
     */
    fun withChanges(changes: Map<String, List<String>>): ItemForm {
        if (changes.isEmpty()) return this
        val newPosts = mutableListOf<Post>()
        val placed = mutableSetOf<String>()
        posts.forEach { post ->
            val replacement = changes[post.name]
            if (replacement == null) {
                newPosts += post
            } else if (placed.add(post.name)) {
                replacement.forEach { newPosts += Post(post.name, encodeValue(post.name, it)) }
            }
        }
        changes.forEach { (name, values) ->
            if (placed.add(name)) values.forEach { newPosts += Post(name, encodeValue(name, it)) }
        }
        val newControls = controls.map { c ->
            changes[c.name]?.let { c.copy(values = it) } ?: c
        }
        return ItemForm(number, newPosts, newControls)
    }

    private fun encodeValue(name: String, value: String): String =
        if (control(name)?.kind == ControlKind.TEXTAREA) value.replace("\r\n", "\n").replace("\r", "\n").replace("\n", "\r\n") else value

    /**
     * ZCBS bouwt het nieuwe record alleen uit de velden die het ontvangt. Velden die het formulier niet toont (bijvoorbeeld
     * veld 28) zouden dus leeg worden opgeslagen. Hier komen ze uit `a0` terug mee.
     */
    fun withMissingFields(): ItemForm {
        val known = (posts.map { it.name } + controls.map { it.name }).toSet()
        val old = a0Fields
        val extra = (3..31).filter { "a$it" !in known && it < old.size }.map { Post("a$it", old[it]) }
        return if (extra.isEmpty()) this else ItemForm(number, posts + extra, controls)
    }
}

/** Leest het formulier uit de HTML van ZCBS. */
object FormParser {
    private val companion = Regex("""a(\d+)([bx])""")
    private val skipTypes = setOf("submit", "button", "image", "reset")

    /** @return het formulier, of null als de pagina geen bewerkformulier van een bestaand record bevat (er is dan geen `a0`). */
    fun parse(html: String): ItemForm? {
        val doc = Jsoup.parse(html)
        val form = doc.selectFirst("form[name=zcbsform]") ?: return null
        val helpTexts = helpTexts(html)
        val posts = mutableListOf<Post>()
        val controls = mutableListOf<Control>()
        val radioGroups = linkedMapOf<String, MutableList<Element>>()

        for (el in form.select("input, select, textarea")) {
            val name = el.attr("name")
            if (name.isEmpty()) continue
            when (el.tagName()) {
                "input" -> {
                    val type = el.attr("type").lowercase().ifEmpty { "text" }
                    when {
                        type in skipTypes -> {}
                        type == "radio" -> radioGroups.getOrPut(name) { mutableListOf() }.add(el)
                        type == "checkbox" -> if (el.hasAttr("checked")) posts += Post(name, el.attr("value").ifEmpty { "on" })
                        type == "file" -> posts += Post(name, "")
                        type == "hidden" -> posts += Post(name, el.attr("value"))
                        else -> {
                            val value = el.attr("value")
                            posts += Post(name, value)
                            addControl(controls, helpTexts, el, name, ControlKind.TEXT, emptyList(), listOf(value))
                        }
                    }
                }
                "textarea" -> {
                    val value = el.wholeText()
                    posts += Post(name, value)
                    addControl(controls, helpTexts, el, name, ControlKind.TEXTAREA, emptyList(), listOf(value))
                }
                "select" -> {
                    val options = el.select("option")
                    val values = options.map { o -> if (o.hasAttr("value")) o.attr("value") else o.text().trim() }
                    val multiple = el.hasAttr("multiple")
                    val selected = options.indices.filter { options[it].hasAttr("selected") }.map { values[it] }
                    val chosen = if (multiple || selected.isNotEmpty()) selected else values.take(1)
                    chosen.forEach { posts += Post(name, it) }
                    addControl(controls, helpTexts, el, name, if (multiple) ControlKind.MULTI else ControlKind.SELECT, values, chosen)
                }
            }
        }
        // Keuzerondjes: alleen het aangevinkte rondje wordt gepost, maar het veld is één keuze met alle waarden.
        radioGroups.forEach { (name, list) ->
            val checked = list.firstOrNull { it.hasAttr("checked") }
            if (checked != null) posts += Post(name, checked.attr("value"))
            addControl(controls, helpTexts, list.first(), name, ControlKind.RADIO, list.map { it.attr("value") }, listOfNotNull(checked?.attr("value")))
        }
        val result = ItemForm(posts.firstOrNull { it.name == "ident" }?.value.orEmpty(), posts, controls)
        return if (posts.any { it.name == "a0" } && result.a0.isNotEmpty()) result else null
    }

    private fun addControl(list: MutableList<Control>, helpTexts: Map<Int, String>, el: Element, name: String, kind: ControlKind, options: List<String>, values: List<String>) {
        if (name.startsWith("add_") || !name.matches(Regex("a\\d+[bx]?"))) return
        list += Control(name, label(el, name), kind, options, values, help(el, helpTexts))
    }

    private val infoLine = Regex("""info\[(\d+)]\s*=\s*"((?:[^"\\]|\\.)*)"\s*;""")
    private val popLayer = Regex("""popLayer\((\d+)\)""")

    /** De helpteksten uit het script van het formulier (`info[n] = "..."`), als platte tekst. */
    private fun helpTexts(html: String): Map<Int, String> = infoLine.findAll(html).associate { m ->
        val raw = m.groupValues[2].replace("\\\"", "\"").replace("\\\\", "\\")
        val text = Jsoup.parse(raw.replace(Regex("(?i)<br\\s*/?>"), "\n")).wholeText().trim()
        m.groupValues[1].toInt() to text
    }.filterValues { it.isNotEmpty() && !it.startsWith("Geen help-informatie") }

    /** Het vraagteken in de rij van het veld verwijst met `popLayer(n)` naar de tekst. */
    private fun help(el: Element, helpTexts: Map<Int, String>): String {
        val row = el.closest("tr") ?: return ""
        val n = row.select("[onmouseover]").firstNotNullOfOrNull { popLayer.find(it.attr("onmouseover"))?.groupValues?.get(1)?.toInt() } ?: return ""
        return helpTexts[n].orEmpty()
    }

    private fun label(el: Element, name: String): String {
        val th = el.closest("tr")?.selectFirst("th")?.text()?.trim()?.trimEnd(':', ' ').orEmpty()
        val m = companion.matchEntire(name)
        val base = th.ifEmpty { name }
        return when (m?.groupValues?.get(2)) {
            "b" -> "$base (namelijk)"
            "x" -> "$base (extra)"
            else -> base
        }
    }
}
