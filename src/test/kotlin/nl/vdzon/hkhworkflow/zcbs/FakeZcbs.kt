package nl.vdzon.hkhworkflow.zcbs

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.net.InetSocketAddress
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Een nagemaakte ZCBS voor de tests: dezelfde aanroepen en dezelfde soorten pagina's als de echte (de foutpagina's zijn
 * de echte pagina's uit src/test/resources/zcbs), met een paar records in het geheugen. Het formulier wordt hier gegenereerd
 * uit de records; of ZCBS het zelf precies zo doet, controleren de tests tegen de lokale ZCBS en de opgeslagen pagina's.
 */
class FakeZcbs : AutoCloseable {
    class Rec(var form: ItemForm)

    val users = mutableMapOf("Test Beheerder" to "Beheer1", "Test Bewerker" to "Bewerk1")
    val records = linkedMapOf<String, Rec>()
    val posts = CopyOnWriteArrayList<List<Post>>()
    val rawBodies = CopyOnWriteArrayList<ByteArray>()
    val requests = CopyOnWriteArrayList<String>()
    private val lockedBy = mutableMapOf<String, String>()
    private val sessions = mutableMapOf<String, String>()
    var refuseSaves = false
    var ignoreFields = emptySet<String>()
    var expireSessions = false

    private val server: HttpServer = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0).also {
        it.createContext("/cgi-bin/archief.pl") { ex -> handle(ex) }
        it.start()
    }
    val baseUrl: String get() = "http://127.0.0.1:${server.address.port}"

    fun addRecord(fixture: String, number: String) {
        val form = Fixtures.form(fixture)
        val a0 = form.a0Fields.toMutableList().also { it[2] = number }
        val posts = form.posts.map { if (it.name == "ident" || it.name == "ident_next" || it.name == "onload") Post(it.name, number) else if (it.name == "a0") Post("a0", a0.joinToString("\t")) else it }
        records[number] = Rec(ItemForm(number, posts, form.controls))
    }

    /** Een andere gebruiker slaat het record op. */
    fun editElsewhere(number: String, changes: Map<String, List<String>>) {
        val rec = records.getValue(number)
        val received = rec.form.withChanges(changes).posts
        apply(rec, received, "Test Bewerker")
    }

    /** Maakt het record zo dat de app het niet begrijpt: a0 wijkt af van wat de velden zeggen. */
    fun corruptA0(number: String) {
        val rec = records.getValue(number)
        val fields = rec.form.a0Fields.toMutableList().also { it[5] = it[5] + " (afwijkend)" }
        rec.form = ItemForm(number, rec.form.posts.map { if (it.name == "a0") Post("a0", fields.joinToString("\t")) else it }, rec.form.controls)
    }

    fun lock(number: String, by: String) { lockedBy[number] = by }
    fun unlock(number: String) { lockedBy.remove(number) }
    fun killSessions() { sessions.clear() }

    private val fieldName = Regex("a\\d+[bx]?")

    /** Verwerkt een opslag zoals ZCBS: het record wordt opgebouwd uit de ontvangen velden; is het gelijk aan het oude, dan gebeurt er niets. */
    private fun apply(rec: Rec, received: List<Post>, user: String) {
        val incoming = received.filter { fieldName.matches(it.name) && it.name !in setOf("a32", "a33", "a34") && it.name !in ignoreFields }
        val names = incoming.map { it.name }.toSet()
        val posts = rec.form.posts.filter { it.name !in names } + incoming
        val candidate = ItemForm(rec.form.number, posts, rec.form.controls)
        val built = RecordRebuilder.rebuild(candidate)
        val old = rec.form.a0Fields
        val unchanged = (0..32).all { i -> built[i].replace(Regex(" *<br> *"), "<br>") == old[i].replace(Regex(" *<br> *"), "<br>") || (i == 21 && built[i] == "0" && old[i].isEmpty()) }
        if (unchanged) return
        val stamp = "261025-20:00|$user"
        val a0 = (built.take(33) + listOf(old[33], stamp)).joinToString("\t")
        val newPosts = posts.map { if (it.name == "a0") Post("a0", a0) else if (it.name == "a34") Post("a34", stamp) else it }
        rec.form = ItemForm(rec.form.number, newPosts, rec.form.controls.map { c ->
            val v = newPosts.filter { it.name == c.name }.map { it.value }
            c.copy(values = if (c.kind == ControlKind.SELECT && v.isEmpty()) c.values else v)
        })
    }

    private fun handle(ex: HttpExchange) {
        requests += "${ex.requestMethod} ${ex.requestURI}"
        val query = ex.requestURI.rawQuery.orEmpty().split('&').filter { it.isNotEmpty() }.associate {
            URLDecoder.decode(it.substringBefore('='), "ISO-8859-1") to URLDecoder.decode(it.substringAfter('=', ""), "ISO-8859-1")
        }
        val cookie = ex.requestHeaders.getFirst("Cookie").orEmpty()
        val user = cookie.split("; ").firstOrNull { it.startsWith("zcbs-user=") }?.substringAfter('=')?.substringBefore('|')
        val token = cookie.split("; ").firstOrNull { it.startsWith("zcbs-token=") }?.substringAfter('=')
        val loggedInAs = if (token != null && sessions[token] == user && user != null) user else null
        val body = ex.requestBody.readAllBytes()
        var html: String
        val headers = mutableListOf<String>()
        if (ex.requestMethod == "POST" && ex.requestHeaders.getFirst("Content-Type").orEmpty().startsWith("application/x-www-form-urlencoded")) {
            val form = String(body, StandardCharsets.ISO_8859_1).split('&').associate {
                URLDecoder.decode(it.substringBefore('='), "ISO-8859-1") to URLDecoder.decode(it.substringAfter('=', ""), "ISO-8859-1")
            }
            val name = form["user"].orEmpty()
            if (users[name] != null && users[name] == form["pswd"]) {
                val t = "t${sessions.size + 1}"
                sessions[t] = name
                headers += "zcbs-user=$name|hash; SameSite=lax"
                headers += "zcbs-token=$t; SameSite=lax"
                html = Fixtures.page("login-ok.html")
            } else {
                headers += "zcbs-user=$name|fout; SameSite=lax"
                html = Fixtures.page("login-fout.html")
            }
        } else if (ex.requestMethod == "POST") {
            rawBodies += body
            val received = parseMultipart(ex.requestHeaders.getFirst("Content-Type"), body)
            posts += received
            html = if (loggedInAs == null || expireSessions) Fixtures.page("sessie-verlopen.html")
            else if (refuseSaves) "<html><head></head><body>==Sorry:4==</body></html>"
            else {
                val number = received.firstOrNull { it.name == "ident" }?.value.orEmpty()
                val rec = records[number]
                if (rec == null) Fixtures.page("edit-niet-bestaand.html")
                else if (lockedBy[number]?.let { it != loggedInAs } == true) Fixtures.page("vergrendeld.html")
                else {
                    apply(rec, received, loggedInAs)
                    Fixtures.page("opslaan-antwoord.html")
                }
            }
        } else if (query["upd_form"] == "edt") {
            val number = query["ident"].orEmpty()
            val rec = records[number]
            html = when {
                loggedInAs == null || expireSessions -> Fixtures.page("sessie-verlopen.html")
                rec == null -> Fixtures.page("edit-niet-bestaand.html")
                lockedBy[number]?.let { it != loggedInAs } == true -> Fixtures.page("vergrendeld.html")
                else -> render(rec.form, loggedInAs)
            }
        } else if (query["search"] == "LAST") {
            html = Fixtures.page("last.html").replace("11933", (records.keys.maxOfOrNull { it.toInt() } ?: 11933).toString())
        } else if (query["ident"] != null) {
            html = if (records.containsKey(query["ident"])) Fixtures.page("weergave-bestaand.html") else Fixtures.page("weergave-niet-bestaand.html")
        } else html = Fixtures.page("login-ok.html")
        val bytes = html.toByteArray(StandardCharsets.ISO_8859_1)
        headers.forEach { ex.responseHeaders.add("Set-Cookie", it) }
        ex.responseHeaders.add("Content-Type", "text/html; charset=iso-8859-1")
        ex.sendResponseHeaders(200, bytes.size.toLong())
        ex.responseBody.use { it.write(bytes) }
    }

    private fun render(form: ItemForm, user: String): String {
        fun esc(s: String) = s.replace("&", "&amp;").replace("\"", "&quot;").replace("<", "&lt;")
        val sb = StringBuilder("<html><head><meta charset=\"iso-8859-1\"></head><body><form name=zcbsform method=\"POST\" enctype=\"multipart/form-data\" action=\"/cgi-bin/archief.pl\"><table>")
        val visible = form.controls.filterNot { it.name.matches(Regex("a\\d+[bx]")) }
        visible.forEach { c ->
            sb.append("<tr><th align=\"left\">${esc(c.label)}</th><td>")
            (listOf(c) + form.controls.filter { it.name.startsWith(c.name) && it.name.matches(Regex("${c.name}[bx]")) }).forEach { x ->
                when (x.kind) {
                    ControlKind.TEXT -> sb.append("<input type=\"text\" name=\"${x.name}\" value=\"${esc(x.value)}\">")
                    ControlKind.TEXTAREA -> sb.append("<textarea name=\"${x.name}\">${esc(x.value.replace("\r\n", "\n"))}</textarea>")
                    ControlKind.SELECT, ControlKind.MULTI -> {
                        sb.append("<select name=\"${x.name}\"${if (x.kind == ControlKind.MULTI) " MULTIPLE" else ""}>")
                        x.options.forEach { o -> sb.append("<option value=\"${esc(o)}\"${if (o in x.values) " SELECTED" else ""}> ${esc(o)}\n") }
                        sb.append("</select>")
                    }
                    ControlKind.RADIO -> x.options.forEach { o -> sb.append("<input type=\"radio\" name=\"${x.name}\"${if (o in x.values) " CHECKED" else ""} value=\"${esc(o)}\">") }
                }
            }
            sb.append("</td></tr>")
        }
        sb.append("</table>")
        val controlNames = form.controls.map { it.name }.toSet()
        form.posts.filter { it.name !in controlNames }.forEach { p ->
            val v = if (p.name == "a34") "261025-20:00|$user" else p.value
            sb.append("<input type=\"hidden\" name=\"${p.name}\" value=\"${esc(v)}\">")
        }
        sb.append("<input type=\"submit\" value=\"VERWERK\"></form></body></html>")
        return sb.toString()
    }

    private fun parseMultipart(contentType: String?, body: ByteArray): List<Post> {
        val boundary = "--" + contentType.orEmpty().substringAfter("boundary=")
        val text = String(body, StandardCharsets.ISO_8859_1)
        return text.split(boundary).drop(1).filter { it.startsWith("\r\n") }.map { part ->
            val head = part.substringAfter("\r\n").substringBefore("\r\n\r\n")
            val value = part.substringAfter("\r\n\r\n").removeSuffix("\r\n")
            Post(Regex("name=\"([^\"]*)\"").find(head)!!.groupValues[1], value)
        }
    }

    override fun close() = server.stop(0)
}
