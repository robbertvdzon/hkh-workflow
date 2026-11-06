package nl.vdzon.hkhworkflow.integration

import java.net.CookieManager
import java.net.CookiePolicy
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import tools.jackson.databind.json.JsonMapper

/** Een antwoord van de API. De JSON is gewoon `Map`, `List` en tekst: de tests kennen de klassen van de app niet. */
class ApiResponse(val status: Int, val body: String, val headers: Map<String, List<String>>) {
    val json: Any? by lazy { MAPPER.readValue(body, Any::class.java) }

    @Suppress("UNCHECKED_CAST")
    val obj: Map<String, Any?> get() = json as Map<String, Any?>

    @Suppress("UNCHECKED_CAST")
    val list: List<Map<String, Any?>> get() = json as List<Map<String, Any?>>

    operator fun get(field: String): Any? = obj[field]
    val code: String? get() = if (body.startsWith("{")) obj["code"] as? String else null

    override fun toString(): String = "$status: $body"
}

private val MAPPER = JsonMapper.builder().build()

/**
 * Praat met de app zoals de frontend dat doet: JSON heen en weer en cookies bewaren. De tests praten alleen zo met de app, dus ze weten niets van de code erachter.
 * [last] is het antwoord op de laatste handeling; met [read] kijk je iets na zonder dat dit verandert.
 */
class Api(private val base: String) {
    private val cookies = CookieManager(null, CookiePolicy.ACCEPT_ALL)
    private val client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).cookieHandler(cookies).build()

    lateinit var last: ApiResponse
        private set

    /** Vergeet de sessie, zoals na een herstart van de app; de andere cookies blijven. */
    fun forgetSession() {
        cookies.cookieStore.cookies.filter { it.name == "JSESSIONID" }.forEach { c -> cookies.cookieStore.urIs.forEach { cookies.cookieStore.remove(it, c) } }
    }

    fun get(path: String) = remember(read(path))
    fun post(path: String, body: Any? = null) = remember(send("POST", path, body))
    fun put(path: String, body: Any?) = remember(send("PUT", path, body))
    fun delete(path: String) = remember(send("DELETE", path, null))

    fun read(path: String): ApiResponse = send("GET", path, null)

    private fun remember(response: ApiResponse) = response.also { last = it }

    private fun send(method: String, path: String, body: Any?): ApiResponse {
        val request = HttpRequest.newBuilder(URI.create(base + path)).header("Accept", "application/json, text/plain, text/html, text/event-stream, */*")
        val publisher = if (body == null) HttpRequest.BodyPublishers.noBody() else HttpRequest.BodyPublishers.ofString(MAPPER.writeValueAsString(body))
        if (body != null) request.header("Content-Type", "application/json")
        val response = client.send(request.method(method, publisher).build(), HttpResponse.BodyHandlers.ofString())
        return ApiResponse(response.statusCode(), response.body(), response.headers().map())
    }
}
