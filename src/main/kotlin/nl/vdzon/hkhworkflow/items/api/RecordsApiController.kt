package nl.vdzon.hkhworkflow.items.api

import com.fasterxml.jackson.annotation.JsonInclude
import nl.vdzon.hkhworkflow.items.Current
import nl.vdzon.hkhworkflow.items.internal.Change
import nl.vdzon.hkhworkflow.items.internal.EditLogReporter
import nl.vdzon.hkhworkflow.items.internal.EditStatus
import nl.vdzon.hkhworkflow.items.internal.ItemEditService
import nl.vdzon.hkhworkflow.items.internal.OpenOutcome
import nl.vdzon.hkhworkflow.items.internal.PreviewOutcome
import nl.vdzon.hkhworkflow.items.internal.RecentRecords
import nl.vdzon.hkhworkflow.items.internal.RecentScope
import nl.vdzon.hkhworkflow.items.internal.SaveOutcome
import nl.vdzon.hkhworkflow.restapi.ApiProblem
import nl.vdzon.hkhworkflow.zcbs.Control
import nl.vdzon.hkhworkflow.zcbs.ControlKind
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.util.HtmlUtils

data class RecentRecordsResponse(val items: List<RecentRecordResponse>, val more: Boolean)

data class RecentRecordResponse(
    val number: String,
    val title: String?,
    val toDescribe: Boolean,
    val addedBy: String,
    val addedAt: String,
    /** Wie het record nu open heeft. */
    val busyBy: String?,
    val media: MediaInfo?,
)

/** Het bestand van een record op de site: [kind] is `pdf` of `image`. */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class MediaInfo(val kind: String, val url: String, val title: String? = null)

data class NextRecordResponse(val number: String?)

data class FieldResponse(val name: String, val label: String, val kind: ControlKind, val options: List<String>, val values: List<String>, val help: String)

data class EditResponse(
    val number: String,
    val fields: List<FieldResponse>,
    val openedAt: String?,
    val lockedUntil: String?,
    val media: MediaInfo?,
    val ocrText: String?,
)

data class ValuesRequest(val values: Map<String, List<String>> = emptyMap())
data class SaveRequest(val values: Map<String, List<String>> = emptyMap(), val onConflict: String? = null)
data class ChangeResponse(val field: String, val label: String, val old: String, val new: String)
data class PreviewResponse(val changes: List<ChangeResponse>, val blockers: List<String>)

/** Opgeslagen ([saved]), niets te doen ([unchanged]), of bij `take-theirs` de velden om te controleren ([fields]). */
@JsonInclude(JsonInclude.Include.NON_NULL)
data class SaveResponse(val saved: Boolean, val unchanged: Boolean, val fields: List<FieldResponse>? = null)

/** De records van een collectie: de recente lijst, en één record bewerken via het formulier van ZCBS. */
@RestController
@RequestMapping("/api/collections/{collection}/records")
class RecordsApiController(
    private val items: ItemEditService,
    private val editLog: EditLogReporter,
    private val recent: RecentRecords,
) {
    @GetMapping
    fun list(
        @RequestParam("scope", defaultValue = "mine") scope: String,
        @RequestParam("offset", defaultValue = "0") offset: Int,
        @RequestParam("limit", defaultValue = "20") limit: Int,
    ): RecentRecordsResponse {
        val recentScope = when (scope) {
            "mine" -> RecentScope.MINE
            "all" -> RecentScope.ALL
            "to-describe" -> RecentScope.TO_DESCRIBE
            else -> throw ApiProblem(400, "invalid-input", "Onbekende lijst: $scope.")
        }
        val page = try {
            recent.page(items.user.orEmpty(), Current.collection(), recentScope, limit.coerceIn(1, 100), offset.coerceAtLeast(0))
        } catch (e: Exception) {
            throw ApiProblem(502, "zcbs-failed", "De records konden niet worden opgehaald: ${e.message ?: e.javaClass.simpleName}")
        }
        return RecentRecordsResponse(
            page.records.map { RecentRecordResponse(it.number, it.title, it.toDescribe, it.by.trim(), it.at.toString(), it.busyBy, it.media?.let { m -> MediaInfo(m.kind, m.url) }) },
            page.more,
        )
    }

    @GetMapping("/next-to-describe")
    fun nextToDescribe(@RequestParam("after") after: String) =
        NextRecordResponse(recent.nextToDescribe(items.user.orEmpty(), Current.collection(), number(after)))

    @GetMapping("/{number}/media")
    fun media(@PathVariable("number") raw: String): MediaInfo {
        val padded = items.padded(number(raw))
        val media = items.mediaOf(padded) ?: throw ApiProblem(404, "no-media", "Bij record $padded staat geen pdf of afbeelding op de site.")
        return MediaInfo(media.kind, media.url, "Record $padded")
    }

    /** Openen vergrendelt het record bij ZCBS, en is dus geen `GET`. */
    @PostMapping("/{number}/edit")
    fun open(@PathVariable("number") raw: String): EditResponse {
        val clean = number(raw)
        val chosen = items.addRecord(clean)
        if (chosen.error != null) {
            throw if (chosen.missing.isNotEmpty()) notFound(clean) else ApiProblem(502, "zcbs-failed", chosen.error)
        }
        val padded = items.records().first().number
        when (val outcome = items.open(padded)) {
            is OpenOutcome.Ready -> return editResponse(padded)
            is OpenOutcome.NeedsLogin -> throw sessionExpired()
            is OpenOutcome.Blocked -> throw when {
                outcome.record.status == EditStatus.GEBLOKKEERD -> ApiProblem(409, "record-locked", outcome.message)
                outcome.record.status == EditStatus.ONVEILIG -> ApiProblem(422, "record-unsafe", outcome.message)
                outcome.message.endsWith("bestaat niet in ZCBS.") -> notFound(padded)
                else -> ApiProblem(502, "zcbs-failed", outcome.message)
            }
        }
    }

    /** De stand van een record dat al open is, bijvoorbeeld na het verversen van de pagina. Doet niets in ZCBS. */
    @GetMapping("/{number}/edit")
    fun current(@PathVariable("number") raw: String): EditResponse {
        val padded = openNumber(raw)
        return editResponse(padded)
    }

    @PostMapping("/{number}/edit/preview")
    fun preview(@PathVariable("number") raw: String, @RequestBody body: ValuesRequest): PreviewResponse {
        val padded = openNumber(raw)
        return when (val outcome = items.preview(padded, values(padded, body.values))) {
            is PreviewOutcome.Ok -> PreviewResponse(outcome.changes.map(::change), outcome.blockers)
            is PreviewOutcome.Failed -> throw ApiProblem(409, "not-open", outcome.message)
        }
    }

    /** Annuleren: er wordt niets opgeslagen. */
    @DeleteMapping("/{number}/edit")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun cancel(@PathVariable("number") raw: String) = items.later(items.padded(number(raw)))

    @PutMapping("/{number}")
    fun save(@PathVariable("number") raw: String, @RequestBody body: SaveRequest): SaveResponse {
        val padded = openNumber(raw)
        val values = values(padded, body.values)
        return when (body.onConflict) {
            null -> {
                editLog.willSave(padded, changes(padded, values))
                outcome(padded, items.save(padded, values))
            }
            "keep-mine" -> {
                editLog.willSave(padded, changes(padded, values))
                outcome(padded, items.resolveConflict(padded, true, values))
            }
            "take-theirs" -> {
                if (items.record(padded)?.fresh == null) throw ApiProblem(409, "no-conflict", "Er is geen conflict meer.")
                when (val outcome = items.resolveConflict(padded, false, values)) {
                    is SaveOutcome.NoChanges -> SaveResponse(saved = false, unchanged = false, fields = fields(padded))
                    else -> outcome(padded, outcome)
                }
            }
            else -> throw ApiProblem(400, "invalid-input", "Onbekende keuze bij een conflict: ${body.onConflict}.")
        }
    }

    /**
     * De enige uitzondering op JSON: een pagina die de browser met één formulier ingelogd naar ZCBS stuurt. Daarin staat het wachtwoord, dus de code van de
     * frontend krijgt het nooit te zien en de browser bewaart de pagina niet.
     */
    @GetMapping("/{number}/open-in-zcbs", produces = [MediaType.TEXT_HTML_VALUE])
    fun openInZcbs(@PathVariable("number") raw: String): ResponseEntity<String> {
        val handoff = items.zcbsHandoff(items.padded(number(raw))) ?: throw ApiProblem(401, "not-logged-in", "Log eerst in bij ZCBS.")
        fun e(value: String) = HtmlUtils.htmlEscape(value)
        val html = """
            <!DOCTYPE html>
            <html lang="nl">
            <head><meta charset="utf-8"><meta name="referrer" content="no-referrer"><title>Record openen in ZCBS</title></head>
            <body onload="document.getElementById('f').submit()" style="font-family:system-ui,sans-serif;padding:24px;color:#333">
            <p>Record ${e(handoff.number)} wordt geopend in ZCBS…</p>
            <form id="f" method="post" action="${e(handoff.action)}" accept-charset="ISO-8859-1">
            <input type="hidden" name="user" value="${e(handoff.user)}">
            <input type="hidden" name="pswd" value="${e(handoff.password)}">
            <input type="hidden" name="ident" value="${e(handoff.number)}">
            <noscript><button type="submit">Open in ZCBS</button></noscript>
            </form>
            </body>
            </html>
        """.trimIndent()
        return ResponseEntity.ok()
            .header("Cache-Control", "no-store")
            .header("Referrer-Policy", "no-referrer")
            .contentType(MediaType.parseMediaType("text/html;charset=UTF-8"))
            .body(html)
    }

    private fun outcome(padded: String, outcome: SaveOutcome): SaveResponse = when (outcome) {
        is SaveOutcome.Saved -> { recent.forget(padded); SaveResponse(saved = true, unchanged = false) }
        is SaveOutcome.NoChanges -> SaveResponse(saved = false, unchanged = true)
        is SaveOutcome.Blocked -> throw ApiProblem(422, "save-blocked", "Dit kan zo niet worden opgeslagen.", mapOf("blockers" to outcome.messages))
        is SaveOutcome.Locked -> throw ApiProblem(409, "record-locked", outcome.message)
        is SaveOutcome.Conflict -> throw ApiProblem(
            409, "edit-conflict", "Iemand heeft record $padded aangepast terwijl jij bezig was.",
            mapOf("conflicts" to outcome.rows.map { mapOf("field" to it.name, "label" to it.label, "mine" to it.mine, "theirs" to it.theirs) }),
        )
        is SaveOutcome.Failed -> throw ApiProblem(502, "zcbs-failed", outcome.message)
        is SaveOutcome.NeedsLogin -> throw sessionExpired()
    }

    /** Het nummer van een record dat open is; zo niet, dan is dat een fout. */
    private fun openNumber(raw: String): String {
        val padded = items.padded(number(raw))
        if (items.record(padded)?.baseline == null) throw ApiProblem(409, "not-open", "Open het record eerst.")
        return padded
    }

    private fun number(raw: String): String {
        val clean = raw.trim()
        if (!clean.matches(Regex("\\d{1,9}"))) throw ApiProblem(400, "invalid-number", "Vul één recordnummer in, bijvoorbeeld 11934.")
        return clean
    }

    private fun notFound(number: String) = ApiProblem(404, "record-not-found", "Record $number bestaat niet in ZCBS.")
    private fun sessionExpired() = ApiProblem(401, "not-logged-in", "Je sessie bij ZCBS is verlopen. Log opnieuw in.")

    private fun editResponse(padded: String): EditResponse {
        val pdf = items.pdfUrl(padded)
        val image = if (pdf == null) items.imageUrl(padded) else null
        return EditResponse(
            number = padded,
            fields = fields(padded),
            openedAt = items.openedAt(padded),
            lockedUntil = items.openedUntil(padded),
            media = pdf?.let { MediaInfo("pdf", it) } ?: image?.let { MediaInfo("image", it) },
            ocrText = items.ocrText(padded),
        )
    }

    private fun fields(padded: String) = items.controlsFor(padded).map(::field)
    private fun field(c: Control) = FieldResponse(c.name, c.label, c.kind, c.options, c.values, c.help)
    private fun change(c: Change) = ChangeResponse(c.name, c.label, c.old, c.new)

    /** Wat er ten opzichte van ZCBS verandert met deze invoer, voor het workflow-logboek. */
    private fun changes(padded: String, values: Map<String, List<String>>): List<Change> =
        (items.preview(padded, values) as? PreviewOutcome.Ok)?.changes.orEmpty()

    /** De invoer, alleen voor velden die het record kent. Een veld dat ontbreekt, blijft zoals het is. */
    private fun values(padded: String, input: Map<String, List<String>>): Map<String, List<String>> {
        val controls = items.record(padded)?.baseline?.controls.orEmpty()
        return controls.filter { it.name in input }.associate { c ->
            val given = input.getValue(c.name)
            c.name to when (c.kind) {
                ControlKind.MULTI -> given.filter { it.isNotEmpty() }
                ControlKind.RADIO -> given.take(1)
                else -> given.take(1).map { it.trim() }
            }
        }
    }
}
