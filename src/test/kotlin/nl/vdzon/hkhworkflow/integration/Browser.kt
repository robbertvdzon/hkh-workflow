package nl.vdzon.hkhworkflow.integration

import java.net.CookieManager
import java.net.CookiePolicy
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

/** Een pagina zoals de browser hem ziet. */
class Page(val url: String, val status: Int, val body: String, val headers: Map<String, List<String>>) {
    val doc: Document = Jsoup.parse(body, url)
    val text: String get() = doc.text()
    val path: String get() = URI.create(url).path

    /** Het formulier dat naar [action] post. */
    fun form(action: String): Element = doc.select("form").firstOrNull { URI.create(it.absUrl("action").ifEmpty { url }).path == action }
        ?: error("Geen formulier naar $action op $path:\n$text")

    fun hasForm(action: String): Boolean = doc.select("form").any { URI.create(it.absUrl("action").ifEmpty { url }).path == action }

    /** De knop met deze tekst, waar die ook staat. */
    fun button(label: String): Element = buttonOrNull(label) ?: error("Geen knop \"$label\" op $path:\n$text")
    fun buttonOrNull(label: String): Element? = doc.select("button, input[type=submit]").firstOrNull { (it.text().ifEmpty { it.attr("value") }).trim().contains(label) }
}

/**
 * Doet wat een browser doet: cookies bewaren, redirects volgen en de formulieren versturen die de pagina echt bevat, met alles erin wat de pagina
 * invult. De tests praten alleen zo met de app, dus ze weten niets van de code erachter. Javascript draait niet; wat htmx ophaalt, haalt [get] op.
 */
class Browser(private val base: String) {
    private val cookies = CookieManager(null, CookiePolicy.ACCEPT_ALL)
    private val client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).cookieHandler(cookies).build()

    lateinit var page: Page
        private set

    /** Vergeet de sessie, zoals na een herstart van de app; de andere cookies blijven. */
    fun forgetSession() {
        cookies.cookieStore.cookies.filter { it.name == "JSESSIONID" }.forEach { c -> cookies.cookieStore.urIs.forEach { cookies.cookieStore.remove(it, c) } }
    }

    fun get(path: String): Page = load(HttpRequest.newBuilder(uri(path)).GET())

    fun post(path: String, fields: List<Pair<String, String>> = emptyList()): Page = load(
        HttpRequest.newBuilder(uri(path)).header("Content-Type", "application/x-www-form-urlencoded")
            .POST(HttpRequest.BodyPublishers.ofString(encode(fields))),
    )

    /** Verstuurt [form] met de waarden die erin staan, aangepast met [changes]. */
    fun submit(form: Element, changes: Map<String, List<String>> = emptyMap(), button: Element? = null): Page {
        val action = button?.takeIf { it.hasAttr("formaction") }?.absUrl("formaction")?.ifEmpty { null } ?: form.absUrl("action").ifEmpty { page.url }
        return post(action, fields(form, changes, button))
    }

    /** Klikt op de knop: verstuurt het formulier eromheen, met de naam en waarde van de knop. */
    fun click(button: Element, changes: Map<String, List<String>> = emptyMap()): Page {
        check(!button.hasAttr("disabled")) { "De knop \"${button.text()}\" staat uit op ${page.path}:\n${page.text}" }
        val form = button.closest("form") ?: error("De knop \"${button.text()}\" staat niet in een formulier")
        return submit(form, changes, button)
    }

    fun fields(form: Element, changes: Map<String, List<String>> = emptyMap(), button: Element? = null): List<Pair<String, String>> {
        val fields = mutableListOf<Pair<String, String>>()
        val done = mutableSetOf<String>()
        fun add(name: String, values: () -> List<String>) {
            if (name.isEmpty() || !done.add(name)) return
            (changes[name] ?: values()).forEach { fields += name to it }
        }
        form.select("input, select, textarea").forEach { el ->
            val name = el.attr("name")
            when (el.tagName()) {
                "select" -> add(name) { el.select("option[selected]").map { o -> o.attr("value") }.ifEmpty { if (el.hasAttr("multiple")) emptyList() else el.select("option").take(1).map { o -> o.attr("value") } } }
                "textarea" -> add(name) { listOf(el.wholeText()) }
                else -> when (el.attr("type").lowercase()) {
                    "submit", "button", "image", "reset", "file" -> {}
                    "checkbox", "radio" -> if (name !in changes) { if (el.hasAttr("checked")) fields += name to el.attr("value").ifEmpty { "on" } } else add(name) { emptyList() }
                    else -> add(name) { listOf(el.attr("value")) }
                }
            }
        }
        changes.keys.filter { it !in done && fields.none { f -> f.first == it } }.forEach { n -> changes.getValue(n).forEach { fields += n to it } }
        if (button != null && button.hasAttr("name")) fields += button.attr("name") to button.attr("value")
        return fields
    }

    private fun uri(path: String): URI = if (path.startsWith("http")) URI.create(path) else URI.create(base + path)

    private fun encode(fields: List<Pair<String, String>>) = fields.joinToString("&") {
        URLEncoder.encode(it.first, StandardCharsets.UTF_8) + "=" + URLEncoder.encode(it.second, StandardCharsets.UTF_8)
    }

    private fun load(request: HttpRequest.Builder): Page {
        var response = client.send(request.build(), HttpResponse.BodyHandlers.ofString())
        var hops = 0
        while (response.statusCode() in 300..399 && hops++ < 10) {
            val target = response.uri().resolve(response.headers().firstValue("Location").orElseThrow())
            response = client.send(HttpRequest.newBuilder(target).GET().build(), HttpResponse.BodyHandlers.ofString())
        }
        page = Page(response.uri().toString(), response.statusCode(), response.body(), response.headers().map())
        return page
    }
}
