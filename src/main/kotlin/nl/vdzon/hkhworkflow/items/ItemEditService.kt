package nl.vdzon.hkhworkflow.items

import java.time.LocalTime
import java.time.format.DateTimeFormatter
import nl.vdzon.hkhworkflow.settings.SettingsService
import nl.vdzon.hkhworkflow.zcbs.ItemForm
import nl.vdzon.hkhworkflow.zcbs.LoginOutcome
import nl.vdzon.hkhworkflow.zcbs.Page
import nl.vdzon.hkhworkflow.zcbs.ControlKind
import nl.vdzon.hkhworkflow.zcbs.RecordRebuilder
import nl.vdzon.hkhworkflow.zcbs.UnencodableException
import nl.vdzon.hkhworkflow.zcbs.ZcbsClient
import nl.vdzon.hkhworkflow.zcbs.ZcbsText
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

enum class EditStatus(val label: String, val kind: String) {
    OPEN("nog niet bewerkt", "neutral"),
    BEZIG("bezig", "busy"),
    OPGESLAGEN("opgeslagen", "ok"),
    LATER("later", "busy"),
    GEBLOKKEERD("geblokkeerd", "busy"),
    ONVEILIG("niet veilig", "err"),
    FOUT("fout", "err"),
}

/** Een record van de batch dat bewerkt moet worden, met waar het bestand vandaan kwam. */
data class BatchRecord(val number: String, val fileName: String, val by: String = "", val folder: String = "")

class EditRecord(val number: String, val fileName: String, val by: String, val folder: String) {
    @Volatile var status: EditStatus = EditStatus.OPEN
    @Volatile var message: String? = null

    /** Het formulier zoals het bij het openen was: de basis voor de wijzigingen en voor de controle op tussentijdse wijzigingen. */
    @Volatile var baseline: ItemForm? = null

    /** Het formulier zoals het op het moment van opslaan was, als iemand anders het record intussen heeft gewijzigd. */
    @Volatile var fresh: ItemForm? = null

    /** De invoer van de gebruiker die nog niet is opgeslagen. */
    @Volatile var draft: Map<String, List<String>>? = null
    @Volatile var title: String = ""
    @Volatile var openedAt: LocalTime? = null
}

data class Change(val name: String, val label: String, val old: String, val new: String)
data class ConflictRow(val name: String, val label: String, val mine: String, val theirs: String)

sealed interface OpenOutcome {
    data class Ready(val record: EditRecord, val form: ItemForm) : OpenOutcome
    data class Blocked(val record: EditRecord, val message: String) : OpenOutcome
    data object NeedsLogin : OpenOutcome
}

sealed interface PreviewOutcome {
    data class Ok(val changes: List<Change>, val blockers: List<String>) : PreviewOutcome
    data class Failed(val message: String) : PreviewOutcome
}

sealed interface SaveOutcome {
    data object Saved : SaveOutcome
    data object NoChanges : SaveOutcome
    data class Blocked(val messages: List<String>) : SaveOutcome
    data class Locked(val message: String) : SaveOutcome
    data class Conflict(val rows: List<ConflictRow>) : SaveOutcome
    data class Failed(val message: String) : SaveOutcome
    data object NeedsLogin : SaveOutcome
}

/**
 * Het bewerken van de records van een batch via het ZCBS-formulier. De ZCBS-naam en het wachtwoord staan alleen in het
 * geheugen. Elke opslag volgt dezelfde stappen: opnieuw inloggen, het formulier opnieuw ophalen (dat zet de vergrendeling),
 * controleren of iemand anders het record intussen heeft gewijzigd, posten en teruglezen.
 */
@Service
class ItemEditService(private val settingsService: SettingsService) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val time = DateTimeFormatter.ofPattern("HH:mm")

    @Volatile private var client: ZcbsClient? = null
    @Volatile private var userName: String? = null
    val user: String? get() = userName
    private var password: String = ""
    @Volatile private var batch: List<EditRecord> = emptyList()

    fun loggedIn(): Boolean = userName != null && client != null

    /** @return een foutmelding, of null als de inlog gelukt is. */
    @Synchronized
    fun login(name: String, pw: String): String? {
        val s = settingsService.get()
        if (s.zcbsBaseUrl.isBlank()) return "Stel eerst het adres van ZCBS in bij Instellingen."
        if (name.isBlank() || pw.isEmpty()) return "Vul je naam en je wachtwoord in."
        val candidate = ZcbsClient(s.zcbsBaseUrl, s.zcbsScriptPath())
        return try {
            when (val result = candidate.login(name.trim(), pw)) {
                is LoginOutcome.Ok -> { client = candidate; userName = name.trim(); password = pw; null }
                is LoginOutcome.Failed -> result.message
            }
        } catch (e: Exception) {
            log.warn("Inloggen bij ZCBS mislukt: {}", e.message)
            "Geen verbinding met ZCBS (${s.zcbsBaseUrl}): ${e.message ?: e.javaClass.simpleName}"
        }
    }

    @Synchronized
    fun logout() { client = null; userName = null; password = "" }

    /** Opnieuw inloggen met de bewaarde gegevens, bijvoorbeeld omdat de sessie bij ZCBS verlopen is. */
    private fun relogin(): Boolean {
        val c = client ?: return false
        return runCatching { c.login(userName.orEmpty(), password) is LoginOutcome.Ok }.getOrDefault(false)
    }

    @Synchronized
    fun startBatch(records: List<BatchRecord>) {
        batch = records.map { EditRecord(it.number, it.fileName, it.by, it.folder) }
    }

    fun records(): List<EditRecord> = batch
    fun record(number: String): EditRecord? = batch.firstOrNull { it.number == number }
    fun statusOf(number: String): EditStatus? = record(number)?.status
    fun handledCount(): Int = batch.count { it.status == EditStatus.OPGESLAGEN }
    fun laterCount(): Int = batch.count { it.status == EditStatus.LATER }
    fun allHandled(): Boolean = batch.isNotEmpty() && batch.all { it.status == EditStatus.OPGESLAGEN || it.status == EditStatus.LATER }

    fun recordUrl(number: String, edit: Boolean = true): String? = client?.recordUrl(number, edit) ?: settingsService.get().let {
        if (it.zcbsBaseUrl.isBlank()) null else ZcbsClient(it.zcbsBaseUrl, it.zcbsScriptPath()).recordUrl(number, edit)
    }

    fun pdfUrl(number: String): String? = client?.let { c ->
        val path = "${settingsService.get().collection.trim()}/pdf/$number.pdf"
        if (c.fileExists(path)) c.fileUrl(path) else null
    }

    fun ocrText(number: String): String? = client?.fetchText("${settingsService.get().collection.trim()}/ocr/$number.txt")

    /** Het volgende recordnummer na het hoogste in ZCBS, of null als dat niet te vinden is. */
    @Synchronized
    fun nextFreeNumber(): Int? = client?.lastNumber()?.plus(1)

    /** @return de nummers die nog niet in ZCBS bestaan (gewone weergave, zet geen vergrendeling). */
    @Synchronized
    fun missingInZcbs(numbers: List<String>): List<String> {
        val c = client ?: return numbers
        return numbers.filter { c.exists(it) == false }
    }

    /**
     * Controleert of de nummers nog vrij zijn: images.exe maakt de records aan en mag ze niet nog eens aanmaken.
     * @return een melding als een nummer al bestaat of niet te controleren is, anders null.
     */
    @Synchronized
    fun checkNumbersFree(numbers: List<String>): String? {
        val c = client ?: return "Log eerst in bij ZCBS."
        val taken = mutableListOf<String>()
        for (n in numbers) {
            when (runCatching { c.exists(n) }.getOrNull()) {
                true -> taken += n
                null -> return "Kan niet controleren of record $n al bestaat. Is je sessie bij ZCBS verlopen, of is ZCBS niet bereikbaar?"
                false -> {}
            }
        }
        return if (taken.isEmpty()) null else
            "Record ${taken.take(5).joinToString(", ")}${if (taken.size > 5) " en ${taken.size - 5} andere" else ""} bestaat al in ZCBS. images.exe zou een bestaand record niet mogen aanmaken. Kies een hoger beginnummer."
    }

    // ---- Openen ----

    private fun openPage(number: String): Page {
        val c = client ?: return Page.NotLoggedIn
        var page = c.openForm(number)
        if (page is Page.SessionExpired || page is Page.NotLoggedIn) {
            if (relogin()) page = c.openForm(number)
        }
        return page
    }

    private fun labelOf(form: ItemForm, index: Int): String = form.control("a$index")?.label ?: "veld $index"

    /** Opent het record: haalt het formulier op (dit zet de vergrendeling bij ZCBS) en controleert of de app het begrijpt. */
    @Synchronized
    fun open(number: String): OpenOutcome {
        val r = record(number) ?: error("Onbekend record $number.")
        if (!loggedIn()) return OpenOutcome.NeedsLogin
        return when (val page = try { openPage(number) } catch (e: Exception) { return fail(r, EditStatus.FOUT, "Geen verbinding met ZCBS: ${e.message}") }) {
            is Page.Form -> {
                val form = page.form.withMissingFields()
                val diffs = RecordRebuilder.differences(form)
                if (diffs.isNotEmpty()) {
                    return fail(
                        r, EditStatus.ONVEILIG,
                        "De app begrijpt dit record niet goed genoeg om het veilig op te slaan (verschil in: ${diffs.joinToString { labelOf(form, it) }}). Er is niets gewijzigd. Open het record in ZCBS.",
                    )
                }
                r.baseline = form
                r.fresh = null
                r.title = form.control("a5")?.value.orEmpty()
                r.openedAt = LocalTime.now()
                if (r.status != EditStatus.OPGESLAGEN) r.status = EditStatus.BEZIG
                r.message = null
                OpenOutcome.Ready(r, form)
            }
            is Page.Locked -> fail(r, EditStatus.GEBLOKKEERD, lockedMessage(number, page))
            is Page.NotAvailable -> fail(r, EditStatus.FOUT, "Record $number bestaat niet in ZCBS.")
            is Page.Refused -> fail(r, EditStatus.FOUT, "ZCBS weigert dit verzoek (==Sorry:${page.code}==).")
            is Page.RateLimited -> fail(r, EditStatus.FOUT, "ZCBS vindt dat er te veel verzoeken zijn. Wacht even.")
            is Page.SessionExpired, is Page.NotLoggedIn, is Page.LoginFailed, is Page.RouterQuestion -> OpenOutcome.NeedsLogin
            else -> fail(r, EditStatus.FOUT, "Onverwacht antwoord van ZCBS: ${(page as? Page.Other)?.text ?: page}")
        }
    }

    private fun fail(r: EditRecord, status: EditStatus, message: String): OpenOutcome.Blocked {
        if (r.status != EditStatus.OPGESLAGEN) r.status = status
        r.message = message
        return OpenOutcome.Blocked(r, message)
    }

    private fun lockedMessage(number: String, page: Page.Locked): String =
        "Record $number is nog ${page.minutes?.let { "ongeveer $it minuten" } ?: "even"} in gebruik" + (page.by?.let { " bij $it" } ?: "") + ". Je invoer blijft bewaard."

    // ---- Wijzigingen ----

    /** De invoer opgeschoond: tekens uit Word vervangen en regeleinden gelijkgetrokken. */
    private fun cleaned(values: Map<String, List<String>>): Map<String, List<String>> =
        values.mapValues { (_, list) -> list.map { ZcbsText.clean(it).replace("\r\n", "\n").replace("\r", "\n") } }

    private fun same(a: List<String>, b: List<String>): Boolean = a.map { it.replace("\r\n", "\n") } == b.map { it.replace("\r\n", "\n") }

    private fun changesFor(base: ItemForm, values: Map<String, List<String>>): List<Change> =
        base.controls.mapNotNull { c ->
            val new = values[c.name] ?: return@mapNotNull null
            if (same(c.values, new)) null
            else Change(c.name, c.label, c.values.joinToString("; ").replace("\r\n", "\n"), new.joinToString("; "))
        }.filterNot { it.name == "a21" && it.old == "0" && it.new == "0" }

    private fun blockers(base: ItemForm, changes: List<Change>, values: Map<String, List<String>>): List<String> = buildList {
        for (change in changes) {
            for (value in values[change.name].orEmpty()) {
                ZcbsText.unencodable(value).takeIf { it.isNotEmpty() }?.let {
                    add("In \"${change.label}\" staat ${it.joinToString(" en ") { c -> "het teken '$c'" }}. ZCBS kan dat niet opslaan; haal het weg of vervang het.")
                }
                ZcbsText.refusedWord(value)?.let { add("In \"${change.label}\" staat het woord \"$it\". ZCBS weigert dat in een veld; gebruik een ander woord.") }
            }
        }
    }

    @Synchronized
    fun preview(number: String, values: Map<String, List<String>>): PreviewOutcome {
        val r = record(number) ?: return PreviewOutcome.Failed("Onbekend record $number.")
        val base = r.baseline ?: return PreviewOutcome.Failed("Open het record eerst.")
        val clean = cleaned(values)
        r.draft = clean
        val changes = changesFor(base, clean)
        return PreviewOutcome.Ok(changes, blockers(base, changes, clean))
    }

    @Synchronized
    fun later(number: String) {
        val r = record(number) ?: return
        if (r.status != EditStatus.OPGESLAGEN) r.status = EditStatus.LATER
    }

    /** Wat de gebruiker op het scherm ziet: de velden met de invoer die nog niet is opgeslagen. */
    fun controlsFor(number: String): List<nl.vdzon.hkhworkflow.zcbs.Control> {
        val r = record(number) ?: return emptyList()
        val base = r.baseline ?: return emptyList()
        val draft = r.draft ?: return base.controls
        return base.controls.map { c -> draft[c.name]?.let { c.copy(values = it) } ?: c }
    }

    // ---- Opslaan ----

    @Synchronized
    fun save(number: String, values: Map<String, List<String>>, force: Boolean = false): SaveOutcome {
        val r = record(number) ?: return SaveOutcome.Failed("Onbekend record $number.")
        val base = r.baseline ?: return SaveOutcome.Failed("Open het record eerst.")
        val clean = cleaned(values)
        r.draft = clean
        val changes = changesFor(base, clean)
        if (changes.isEmpty()) return SaveOutcome.NoChanges
        val blocked = blockers(base, changes, clean)
        if (blocked.isNotEmpty()) return SaveOutcome.Blocked(blocked)
        val c = client ?: return SaveOutcome.NeedsLogin
        return try {
            if (!relogin()) return SaveOutcome.NeedsLogin
            val fresh = when (val page = c.openForm(number)) {
                is Page.Form -> page.form.withMissingFields()
                is Page.Locked -> { r.status = EditStatus.GEBLOKKEERD; r.message = lockedMessage(number, page); return SaveOutcome.Locked(r.message!!) }
                is Page.NotAvailable -> return SaveOutcome.Failed("Record $number bestaat niet (meer) in ZCBS.")
                else -> return SaveOutcome.Failed("Onverwacht antwoord van ZCBS bij het ophalen van het formulier: ${(page as? Page.Other)?.text ?: page}")
            }
            if (RecordRebuilder.differences(fresh).isNotEmpty()) {
                return SaveOutcome.Failed("Het formulier van ZCBS wijkt nu af van wat de app begrijpt; er is niets opgeslagen. Open het record in ZCBS.")
            }
            if (!force && fresh.a0 != base.a0) {
                val rows = changes.mapNotNull { ch ->
                    val theirs = fresh.control(ch.name)?.values.orEmpty()
                    val before = base.control(ch.name)?.values.orEmpty()
                    if (same(theirs, before)) null else ConflictRow(ch.name, ch.label, clean[ch.name].orEmpty().joinToString("; "), theirs.joinToString("; "))
                }
                if (rows.isNotEmpty()) { r.fresh = fresh; return SaveOutcome.Conflict(rows) }
            }
            val target = fresh.withChanges(changes.associate { it.name to clean.getValue(it.name) })
            when (val answer = c.save(target)) {
                is Page.Locked -> { r.status = EditStatus.GEBLOKKEERD; r.message = lockedMessage(number, answer); return SaveOutcome.Locked(r.message!!) }
                is Page.Refused -> return SaveOutcome.Failed("ZCBS weigerde het opslaan (==Sorry:${answer.code}==). Er is niets opgeslagen.")
                is Page.SessionExpired, is Page.NotLoggedIn -> return SaveOutcome.NeedsLogin
                is Page.RateLimited -> return SaveOutcome.Failed("ZCBS vindt dat er te veel verzoeken zijn. Wacht even en probeer het opnieuw.")
                else -> {}
            }
            verify(r, c, target)
        } catch (e: UnencodableException) {
            SaveOutcome.Blocked(listOf("In veld ${e.field} staat ${e.chars.joinToString(" en ") { "het teken '$it'" }}. ZCBS kan dat niet opslaan."))
        } catch (e: Exception) {
            log.warn("Opslaan van {} mislukt: {}", number, e.message)
            SaveOutcome.Failed("Geen verbinding met ZCBS of onverwachte fout: ${e.message ?: e.javaClass.simpleName}. Controleer in ZCBS of het record is opgeslagen.")
        }
    }

    /** Leest het record terug en vergelijkt het met wat er is gepost. Pas dan geldt het als opgeslagen. */
    private fun verify(r: EditRecord, c: ZcbsClient, posted: ItemForm): SaveOutcome {
        val after = when (val page = c.openForm(r.number)) {
            is Page.Form -> page.form.withMissingFields()
            else -> return failRead(r, "Het record kon niet teruggelezen worden (${(page as? Page.Other)?.text ?: page}). Controleer in ZCBS of het is opgeslagen.")
        }
        val expected = RecordRebuilder.rebuild(posted)
        val actual = after.a0Fields.take(33)
        val wrong = (0..31).filter { i -> norm(i, expected[i]) != norm(i, actual.getOrElse(i) { "" }) }
        if (wrong.isNotEmpty()) {
            return failRead(
                r,
                "Opgeslagen, maar ${wrong.joinToString("; ") { "\"${labelOf(after, it)}\" is nu \"${actual.getOrElse(it) { "" }.take(60)}\" in plaats van \"${expected[it].take(60)}\"" }}. Controleer het record in ZCBS.",
            )
        }
        r.baseline = after
        r.fresh = null
        r.draft = null
        r.title = after.control("a5")?.value.orEmpty()
        r.status = EditStatus.OPGESLAGEN
        r.message = null
        return SaveOutcome.Saved
    }

    private fun failRead(r: EditRecord, message: String): SaveOutcome.Failed {
        r.status = EditStatus.FOUT
        r.message = message
        return SaveOutcome.Failed(message)
    }

    private fun norm(index: Int, value: String): String {
        val v = value.replace(Regex(" *<br> *"), "<br>")
        return if (index == 21 && v == "0") "" else v
    }

    /** De keuze bij een conflict: de eigen versie bewaren, of de nieuwe versie uit ZCBS gebruiken voor de velden die botsen. */
    @Synchronized
    fun resolveConflict(number: String, keepMine: Boolean, values: Map<String, List<String>>): SaveOutcome {
        val r = record(number) ?: return SaveOutcome.Failed("Onbekend record $number.")
        if (keepMine) return save(number, values, force = true)
        val fresh = r.fresh ?: return SaveOutcome.Failed("Er is geen conflict meer.")
        val base = r.baseline ?: return SaveOutcome.Failed("Open het record eerst.")
        val mine = cleaned(values)
        val merged = mine.toMutableMap()
        changesFor(base, mine).forEach { ch ->
            val theirs = fresh.control(ch.name)?.values.orEmpty()
            if (!same(theirs, base.control(ch.name)?.values.orEmpty())) merged[ch.name] = theirs
        }
        r.baseline = fresh
        r.fresh = null
        r.draft = merged
        return SaveOutcome.NoChanges
    }

    fun openedUntil(number: String): String? = record(number)?.openedAt?.plusMinutes(10)?.format(time)
    fun openedAt(number: String): String? = record(number)?.openedAt?.format(time)

    /** Of de vorm van een veld meerkeuze is; de controller leest dan alle gekozen waarden. */
    fun isMulti(number: String, name: String): Boolean = record(number)?.baseline?.control(name)?.kind == ControlKind.MULTI
}
