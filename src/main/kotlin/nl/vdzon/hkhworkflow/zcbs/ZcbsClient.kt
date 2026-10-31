package nl.vdzon.hkhworkflow.zcbs

import java.io.ByteArrayOutputStream
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.util.UUID

/** Eén veld dat ZCBS (latin-1) niet kan opslaan, met het teken. */
class UnencodableException(val field: String, val chars: List<Char>) :
    IllegalArgumentException("Veld $field bevat tekens die ZCBS niet kan opslaan: ${chars.joinToString(" ")}")

sealed interface LoginOutcome {
    data object Ok : LoginOutcome
    data class Failed(val message: String, val rateLimited: Boolean = false) : LoginOutcome
}

/**
 * De aanroepen naar ZCBS, zoals een browser ze doet: inloggen, het formulier ophalen (dat zet de vergrendeling van ZCBS),
 * en een formulier posten. Alle antwoorden worden als ISO-8859-1 gelezen. Er is geen sessie-object: de cookies blijven
 * in deze client, in het geheugen.
 */
class ZcbsClient(
    baseUrl: String,
    private val scriptPath: String,
    private val http: HttpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).version(HttpClient.Version.HTTP_1_1).build(),
) {
    private val base = baseUrl.trim().trimEnd('/')
    private val cookies = linkedMapOf<String, String>()

    /** Het adres van ZCBS voor mensen, bijvoorbeeld om een record in ZCBS zelf te openen. */
    fun recordUrl(number: String, edit: Boolean = false): String =
        "$base$scriptPath?ident=$number" + if (edit) "&upd_form=edt" else ""

    @Synchronized
    private fun send(request: HttpRequest.Builder): String {
        val request = request.header("User-Agent", USER_AGENT).timeout(Duration.ofSeconds(25))
            .also { if (cookies.isNotEmpty()) it.header("Cookie", cookies.entries.joinToString("; ") { "${it.key}=${it.value}" }) }
            .build()
        val response = http.send(request, HttpResponse.BodyHandlers.ofByteArray())
        response.headers().allValues("set-cookie").forEach { storeCookie(it) }
        return String(response.body(), StandardCharsets.ISO_8859_1)
    }

    private fun storeCookie(header: String) {
        val pair = header.substringBefore(';')
        val name = pair.substringBefore('=').trim()
        val value = pair.substringAfter('=', "").trim()
        if (name.isEmpty()) return
        val expired = header.contains("expires=Thu, 01-Jan-1970", ignoreCase = true) || value == "deleted"
        if (expired) cookies.remove(name) else cookies[name] = value
    }

    private fun get(query: String): String = send(HttpRequest.newBuilder(URI.create("$base$scriptPath?$query")).GET())

    /** Logt in met naam en wachtwoord. Op een routeradres (het HKH-netwerk) vraagt ZCBS eerst wie je bent; dan volgt de routerweg. */
    fun login(user: String, password: String): LoginOutcome {
        cookies.clear()
        var page = ZcbsPages.classify(postForm(mapOf("user" to user, "pswd" to password)))
        if (page is Page.RouterQuestion) page = ZcbsPages.classify(postForm(mapOf("user" to user, "pswd" to password, "veld" to "access")))
        return when (page) {
            is Page.LoginOk, is Page.Form -> LoginOutcome.Ok
            is Page.LoginFailed -> LoginOutcome.Failed("Naam of wachtwoord niet herkend.")
            is Page.RateLimited -> LoginOutcome.Failed("ZCBS vindt dat er te veel verzoeken zijn. Wacht even en probeer het opnieuw.", rateLimited = true)
            else -> LoginOutcome.Failed("ZCBS gaf een onverwacht antwoord op het inloggen: ${(page as? Page.Other)?.text ?: page}")
        }
    }

    private fun postForm(fields: Map<String, String>): String {
        val body = fields.entries.joinToString("&") { "${enc(it.key)}=${enc(it.value)}" }
        return send(
            HttpRequest.newBuilder(URI.create(base + scriptPath)).header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.ISO_8859_1)),
        )
    }

    private fun enc(s: String) = URLEncoder.encode(s, StandardCharsets.ISO_8859_1)

    /** Het hoogste recordnummer, ook van lege records. Alleen ingelogd; zonder inlog ontbreken de lege records. */
    fun lastNumber(): Int? = ZcbsPages.lastNumber(get("search=LAST"))

    /**
     * De vrije recordnummers, zoals ZCBS ze toont bij een nieuw record (alleen lezen: de pagina maakt niets aan en zet geen
     * vergrendeling). Null als ZCBS de lijst niet geeft, bijvoorbeeld zonder inlog of zonder recht om records aan te maken.
     */
    fun freeNumbers(): FreeNumbers? = ZcbsPages.freeNumbers(get("ident=0&upd_form=new"), lastNumber()?.toLong())

    /** Bestaat het record? Gewone weergave: zet geen vergrendeling. */
    fun exists(number: String): Boolean? = when (val page = ZcbsPages.classify(get("ident=$number"))) {
        is Page.NotAvailable -> false
        is Page.Other, is Page.Form, is Page.LoginOk -> true
        else -> if (page is Page.Locked) true else null
    }

    /** Het adres van een bestand op de site van de collectie, bijvoorbeeld `archief/pdf/11934.pdf`. */
    fun fileUrl(path: String): String = "$base/${path.trimStart('/')}"

    /** Haalt een klein tekstbestand van de site op (bijvoorbeeld de ocr-tekst), of null als het er niet is. */
    fun fetchText(path: String, maxChars: Int = 800): String? = runCatching {
        val response = http.send(
            HttpRequest.newBuilder(URI.create(fileUrl(path))).header("User-Agent", USER_AGENT).timeout(Duration.ofSeconds(15)).GET().build(),
            HttpResponse.BodyHandlers.ofByteArray(),
        )
        if (response.statusCode() == 200) decodeText(response.body()).take(maxChars) else null
    }.getOrNull()

    /** De ocr-tekst van pdf2zcbs is UTF-8; een ouder bestand kan latin-1 zijn. */
    private fun decodeText(bytes: ByteArray): String = runCatching {
        StandardCharsets.UTF_8.newDecoder().onMalformedInput(java.nio.charset.CodingErrorAction.REPORT).decode(java.nio.ByteBuffer.wrap(bytes)).toString()
    }.getOrElse { String(bytes, StandardCharsets.ISO_8859_1) }

    /** Bestaat een bestand op de site? */
    fun fileExists(path: String): Boolean = runCatching {
        http.send(
            HttpRequest.newBuilder(URI.create(fileUrl(path))).header("User-Agent", USER_AGENT).timeout(Duration.ofSeconds(15)).method("HEAD", HttpRequest.BodyPublishers.noBody()).build(),
            HttpResponse.BodyHandlers.discarding(),
        ).statusCode() == 200
    }.getOrDefault(false)

    /** Haalt het bewerkformulier op. Dit zet de vergrendeling van ZCBS op de ingelogde gebruiker (10 minuten). */
    fun openForm(number: String): Page = ZcbsPages.classify(get("ident=$number&upd_form=edt"))

    /** Post het formulier (met `write=1`). Het antwoord is een pagina; of het gelukt is, blijkt pas uit teruglezen. */
    fun save(form: ItemForm): Page {
        val (type, body) = multipart(form.posts)
        return ZcbsPages.classify(
            send(HttpRequest.newBuilder(URI.create(base + scriptPath)).header("Content-Type", type).POST(HttpRequest.BodyPublishers.ofByteArray(body))),
        )
    }

    companion object {
        const val USER_AGENT = "hkh-workflow"

        /** `multipart/form-data` in latin-1, zoals het ZCBS-formulier zelf (`accept-charset="ISO-8859-1"`). */
        fun multipart(posts: List<Post>): Pair<String, ByteArray> {
            val boundary = "----hkh" + UUID.randomUUID().toString().replace("-", "")
            val out = ByteArrayOutputStream()
            posts.forEach { p ->
                val bad = ZcbsText.unencodable(p.value)
                if (bad.isNotEmpty()) throw UnencodableException(p.name, bad)
                out.write("--$boundary\r\nContent-Disposition: form-data; name=\"${p.name}\"\r\n\r\n".toByteArray(StandardCharsets.ISO_8859_1))
                out.write(p.value.toByteArray(StandardCharsets.ISO_8859_1))
                out.write("\r\n".toByteArray(StandardCharsets.ISO_8859_1))
            }
            out.write("--$boundary--\r\n".toByteArray(StandardCharsets.ISO_8859_1))
            return "multipart/form-data; boundary=$boundary" to out.toByteArray()
        }
    }
}
