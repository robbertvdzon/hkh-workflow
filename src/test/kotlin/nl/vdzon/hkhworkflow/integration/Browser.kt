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
import org.jsoup.nodes.Element

/** Een pagina zoals de browser hem ziet. */
class Page(val status: Int, val body: String) {
    val text: String get() = Jsoup.parse(body).text()
}

/**
 * Doet wat een browser doet voor wat buiten de API valt: een bestand van de site ophalen, en het formulier uit "Open in ZCBS" naar ZCBS zelf sturen. Javascript
 * draait niet.
 */
class Browser {
    private val client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).cookieHandler(CookieManager(null, CookiePolicy.ACCEPT_ALL)).build()

    fun get(url: String): Page = load(HttpRequest.newBuilder(URI.create(url)).GET())

    /** Verstuurt het formulier met de waarden die erin staan. */
    fun submit(form: Element): Page {
        val fields = form.select("input[name]").joinToString("&") {
            URLEncoder.encode(it.attr("name"), StandardCharsets.UTF_8) + "=" + URLEncoder.encode(it.attr("value"), StandardCharsets.UTF_8)
        }
        return load(HttpRequest.newBuilder(URI.create(form.attr("action"))).header("Content-Type", "application/x-www-form-urlencoded").POST(HttpRequest.BodyPublishers.ofString(fields)))
    }

    private fun load(request: HttpRequest.Builder): Page {
        val response = client.send(request.build(), HttpResponse.BodyHandlers.ofString())
        return Page(response.statusCode(), response.body())
    }
}
