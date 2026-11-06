package nl.vdzon.hkhworkflow.zcbs

import nl.vdzon.hkhworkflow.zcbs.internal.RecordRebuilder
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Een eenvoudige vervanging van [ZcbsClient] voor unit-tests: geen netwerk en geen html, alleen wat de client aan de rest van de app teruggeeft, met een
 * paar records in het geheugen. Of ZCBS zich echt zo gedraagt, staat in `ZcbsContractTest` en de scenario's in `src/test/resources/features`.
 */
class StubZcbs : AutoCloseable {
    class Rec(var form: ItemForm)

    val baseUrl = "http://zcbs.test"
    val users = mutableMapOf("Test Beheerder" to "Beheer1", "Test Bewerker" to "Bewerk1")
    val records = linkedMapOf<String, Rec>()

    /** Elke keer dat er iets wordt opgeslagen: de velden die zijn verstuurd. */
    val posts = CopyOnWriteArrayList<List<Post>>()

    /** De aanroepen die de app deed, leesbaar voor een test. */
    val requests = CopyOnWriteArrayList<String>()
    var refuseSaves = false
    var ignoreFields = emptySet<String>()
    var numberWidth = 5
    private val lockedBy = mutableMapOf<String, String>()
    private val otherCollections = mutableMapOf<String, MutableMap<String, String>>()
    private var sessionEpoch = 0

    val factory = ZcbsClientFactory { base, script -> Client(base, script) }

    /** Geeft [user] rechten bij een andere collectie dan het archief. Zonder `grant` bestaat het script niet. */
    fun grant(collection: String, user: String, password: String = users.getValue(user)) {
        otherCollections.getOrPut(collection) { mutableMapOf() }[user] = password
    }

    fun addRecord(fixture: String, number: String) {
        val form = Fixtures.form(fixture)
        val a0 = form.a0Fields.toMutableList().also { it[2] = number }
        val posts = form.posts.map { if (it.name == "ident" || it.name == "ident_next" || it.name == "onload") Post(it.name, number) else if (it.name == "a0") Post("a0", a0.joinToString("\t")) else it }
        records[number] = Rec(ItemForm(number, posts, form.controls))
    }

    /** Een andere gebruiker slaat het record op. */
    fun editElsewhere(number: String, changes: Map<String, List<String>>) {
        val rec = records.getValue(number)
        apply(rec, rec.form.withChanges(changes).posts, "Test Bewerker")
    }

    /** Maakt het record zo dat de app het niet begrijpt: a0 wijkt af van wat de velden zeggen. */
    fun corruptA0(number: String) {
        val rec = records.getValue(number)
        val fields = rec.form.a0Fields.toMutableList().also { it[5] = it[5] + " (afwijkend)" }
        rec.form = ItemForm(number, rec.form.posts.map { if (it.name == "a0") Post("a0", fields.joinToString("\t")) else it }, rec.form.controls)
    }

    fun lock(number: String, by: String) { lockedBy[number] = by }
    fun unlock(number: String) { lockedBy.remove(number) }
    fun killSessions() { sessionEpoch++ }

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

    private inner class Client(private val base: String, private val script: String) : ZcbsClient {
        private var user: String? = null
        private var epoch = -1
        private val archive = script == "/cgi-bin/archief.pl"
        private val valid: Boolean get() = user != null && epoch == sessionEpoch

        override fun recordUrl(number: String, edit: Boolean) = "$base$script?ident=$number" + if (edit) "&upd_form=edt" else ""

        override fun login(user: String, password: String): LoginOutcome {
            requests += "POST $script"
            if (base != baseUrl) throw java.net.ConnectException("Connection refused")
            val known = if (archive) users else otherCollections[script.removePrefix("/cgi-bin/").removeSuffix(".pl")].orEmpty()
            if (known[user] != null && known[user] == password) {
                this.user = user
                epoch = sessionEpoch
                return LoginOutcome.Ok
            }
            this.user = null
            return LoginOutcome.Failed("Naam of wachtwoord niet herkend.")
        }

        override fun lastNumber() = records.keys.maxOfOrNull { it.toInt() } ?: 11933

        override fun freeNumbers(): FreeNumbers? {
            if (!valid) return null
            val ranges = mutableListOf<NumberRange>()
            var next = 1L
            for (n in records.keys.map { it.toLong() }.sorted()) {
                if (n > next) ranges += NumberRange(next, n - 1)
                next = n + 1
            }
            ranges += NumberRange(next, "9".repeat(numberWidth).toLong(), open = true)
            return FreeNumbers(ranges, numberWidth, lastNumber().toLong())
        }

        override fun exists(number: String): Boolean? {
            requests += "GET $script?ident=$number"
            return records.containsKey(number)
        }

        override fun lookup(number: String): RecordLookup? {
            requests += "GET $script?ident=$number"
            return if (records.containsKey(number)) RecordLookup.Found(RecordSummary("Testrecord & meer", false)) else RecordLookup.Missing
        }

        override fun fileUrl(path: String) = "$base/$path"
        override fun fetchText(path: String, maxChars: Int): String? = null
        override fun fileExists(path: String) = false

        override fun openForm(number: String): Page {
            requests += "GET $script?ident=$number&upd_form=edt"
            val rec = records[number]
            return when {
                !valid -> Page.SessionExpired
                rec == null -> Page.NotAvailable
                lockedBy[number]?.let { it != user } == true -> Page.Locked(10, lockedBy[number])
                else -> Page.Form(ItemForm(number, rec.form.posts.map { if (it.name == "a34") Post("a34", "261025-20:00|$user") else it }, rec.form.controls))
            }
        }

        override fun save(form: ItemForm): Page {
            form.posts.forEach { p -> ZcbsText.unencodable(p.value).takeIf { it.isNotEmpty() }?.let { throw UnencodableException(p.name, it) } }
            requests += "POST $script"
            posts += form.posts
            val number = form.number
            val rec = records[number]
            return when {
                !valid -> Page.SessionExpired
                refuseSaves -> Page.Refused(4)
                rec == null -> Page.NotAvailable
                lockedBy[number]?.let { it != user } == true -> Page.Locked(10, lockedBy[number])
                else -> { apply(rec, form.posts, user.orEmpty()); Page.Other("opgeslagen") }
            }
        }
    }

    override fun close() {}
}
