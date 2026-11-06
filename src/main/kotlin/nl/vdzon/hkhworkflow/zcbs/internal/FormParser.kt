package nl.vdzon.hkhworkflow.zcbs.internal

import nl.vdzon.hkhworkflow.zcbs.Control
import nl.vdzon.hkhworkflow.zcbs.ControlKind
import nl.vdzon.hkhworkflow.zcbs.ItemForm
import nl.vdzon.hkhworkflow.zcbs.Post
import org.jsoup.Jsoup
import org.jsoup.nodes.Element

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
