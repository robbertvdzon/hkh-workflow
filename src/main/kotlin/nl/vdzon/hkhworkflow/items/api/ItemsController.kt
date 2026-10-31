package nl.vdzon.hkhworkflow.items.api

import nl.vdzon.hkhworkflow.adminlog.AdminLogClient
import nl.vdzon.hkhworkflow.items.Change
import nl.vdzon.hkhworkflow.items.ConflictRow
import nl.vdzon.hkhworkflow.items.EditLogReporter
import nl.vdzon.hkhworkflow.items.EditStatus
import nl.vdzon.hkhworkflow.items.ItemEditService
import nl.vdzon.hkhworkflow.items.OpenOutcome
import nl.vdzon.hkhworkflow.items.PreviewOutcome
import nl.vdzon.hkhworkflow.items.SaveOutcome
import nl.vdzon.hkhworkflow.zcbs.ControlKind
import jakarta.servlet.http.HttpServletResponse
import nl.vdzon.hkhworkflow.items.RememberLogin
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.util.MultiValueMap
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.servlet.mvc.support.RedirectAttributes

/** Inloggen bij ZCBS en de pagina's om records te bewerken via het ZCBS-formulier. */
@Controller
class ItemsController(
    private val items: ItemEditService,
    private val remember: RememberLogin,
    private val adminLog: AdminLogClient,
    private val editLog: EditLogReporter,
) {

    private fun back(target: String): String = when (target) { "/records" -> "redirect:/records"; "/nieuw" -> "redirect:/nieuw"; "/test" -> "redirect:/test"; else -> "redirect:/" }

    @PostMapping("/zcbs/login")
    fun login(
        @RequestParam("naam", defaultValue = "") name: String,
        @RequestParam("wachtwoord", defaultValue = "") password: String,
        @RequestParam("terug", defaultValue = "/") target: String,
        redirect: RedirectAttributes,
        response: HttpServletResponse,
    ): String {
        val error = items.login(name, password)
        adminLog.login(name.trim(), error == null, "PASSWORD", error)
        if (error != null) redirect.addFlashAttribute("error", error) else remember.remember(response, name, password)
        return back(target)
    }

    @PostMapping("/zcbs/logout")
    fun logout(@RequestParam("terug", defaultValue = "/") target: String, response: HttpServletResponse): String {
        items.logout()
        remember.forget(response)
        return back(target)
    }

    @GetMapping("/records")
    fun list(@RequestParam("opgeslagen", required = false) saved: String?, model: Model): String {
        model.addAttribute("active", "records")
        model.addAttribute("items", items)
        model.addAttribute("records", items.records())
        model.addAttribute("saved", saved)
        model.addAttribute("loggedIn", items.loggedIn())
        return "records"
    }

    /** Kiest de records die bewerkt worden: een nummer, een reeks of meer daarvan. */
    @PostMapping("/records/kies")
    fun choose(@RequestParam("nummers", defaultValue = "") numbers: String, redirect: RedirectAttributes): String {
        val result = items.chooseRecords(numbers)
        if (result.error != null) redirect.addFlashAttribute("error", result.error)
        else {
            val missing = if (result.missing.isEmpty()) "" else " Deze bestaan niet in ZCBS en staan er niet bij: ${result.missing.take(8).joinToString()}${if (result.missing.size > 8) " en ${result.missing.size - 8} andere" else ""}."
            redirect.addFlashAttribute("info", "${result.count} ${if (result.count == 1) "record staat" else "records staan"} klaar om te bewerken.$missing")
        }
        return "redirect:/records"
    }

    private fun showForm(model: Model, number: String, panel: String? = null, messages: List<String> = emptyList(), conflict: List<ConflictRow> = emptyList()): String {
        val r = items.record(number) ?: return "redirect:/records"
        model.addAttribute("active", "records")
        model.addAttribute("r", r)
        model.addAttribute("controls", items.controlsFor(number))
        model.addAttribute("panel", panel)
        model.addAttribute("messages", messages)
        model.addAttribute("conflict", conflict)
        model.addAttribute("position", items.records().indexOf(r) + 1)
        model.addAttribute("total", items.records().size)
        model.addAttribute("openedAt", items.openedAt(number))
        model.addAttribute("openedUntil", items.openedUntil(number))
        model.addAttribute("zcbsUrl", items.recordUrl(number))
        val pdfUrl = items.pdfUrl(number)
        model.addAttribute("pdfUrl", pdfUrl)
        model.addAttribute("imageUrl", if (pdfUrl == null) items.imageUrl(number) else null)
        model.addAttribute("ocr", items.ocrText(number))
        model.addAttribute("nextNumber", items.nextNumber(number))
        return "record"
    }

    @GetMapping("/records/{nr}")
    fun open(@PathVariable("nr") number: String, model: Model, redirect: RedirectAttributes): String {
        if (items.record(number) == null) return "redirect:/records"
        return when (val outcome = items.open(number)) {
            is OpenOutcome.Ready -> showForm(model, number)
            is OpenOutcome.Blocked -> {
                val panel = when (outcome.record.status) {
                    EditStatus.GEBLOKKEERD -> "locked"
                    EditStatus.ONVEILIG -> "unsafe"
                    else -> "error"
                }
                blockedPage(model, number, panel, outcome.message)
            }
            is OpenOutcome.NeedsLogin -> { redirect.addFlashAttribute("error", "Je sessie bij ZCBS is verlopen. Log opnieuw in."); "redirect:/" }
        }
    }

    /** De pagina van een record dat niet te bewerken is: zonder formulier, met uitleg en een link naar ZCBS. */
    private fun blockedPage(model: Model, number: String, panel: String, message: String): String {
        val r = items.record(number)!!
        model.addAttribute("active", "records")
        model.addAttribute("r", r)
        model.addAttribute("controls", emptyList<Any>())
        model.addAttribute("panel", panel)
        model.addAttribute("messages", listOf(message))
        model.addAttribute("conflict", emptyList<ConflictRow>())
        model.addAttribute("position", items.records().indexOf(r) + 1)
        model.addAttribute("total", items.records().size)
        model.addAttribute("openedAt", null)
        model.addAttribute("openedUntil", null)
        model.addAttribute("zcbsUrl", items.recordUrl(number))
        model.addAttribute("pdfUrl", null)
        model.addAttribute("imageUrl", null)
        model.addAttribute("nextNumber", items.nextNumber(number))
        model.addAttribute("ocr", null)
        return "record"
    }

    /** De waarden uit het formulier: per veld dat het record kent; een meerkeuze zonder keuze is leeg. */
    private fun values(number: String, params: MultiValueMap<String, String>): Map<String, List<String>> {
        val controls = items.record(number)?.baseline?.controls ?: return emptyMap()
        return controls.associate { c ->
            c.name to when (c.kind) {
                ControlKind.MULTI -> params[c.name].orEmpty().filter { it.isNotEmpty() }
                ControlKind.RADIO -> params[c.name]?.take(1) ?: c.values
                ControlKind.TEXTAREA -> listOf(params.getFirst(c.name).orEmpty().trim())
                else -> listOf(params.getFirst(c.name).orEmpty().trim())
            }
        }
    }

    /** Wat er ten opzichte van ZCBS verandert met deze invoer, voor het workflow-logboek. */
    private fun changesOf(number: String, values: Map<String, List<String>>): List<Change> =
        (items.preview(number, values) as? PreviewOutcome.Ok)?.changes.orEmpty()

    @PostMapping("/records/{nr}/voorbeeld")
    fun preview(@PathVariable("nr") number: String, @RequestParam params: MultiValueMap<String, String>, model: Model): String {
        val r = items.record(number) ?: return "redirect:/records"
        return when (val outcome = items.preview(number, values(number, params))) {
            is PreviewOutcome.Ok -> {
                model.addAttribute("active", "records")
                model.addAttribute("r", r)
                model.addAttribute("changes", outcome.changes)
                model.addAttribute("blockers", outcome.blockers)
                model.addAttribute("controls", items.controlsFor(number))
                model.addAttribute("zcbsUrl", items.recordUrl(number))
                "record-voorbeeld"
            }
            is PreviewOutcome.Failed -> showForm(model, number, "error", listOf(outcome.message))
        }
    }

    @PostMapping("/records/{nr}/terug")
    fun backToForm(@PathVariable("nr") number: String, @RequestParam params: MultiValueMap<String, String>, model: Model): String {
        items.preview(number, values(number, params))
        return showForm(model, number)
    }

    @PostMapping("/records/{nr}/opslaan")
    fun save(
        @PathVariable("nr") number: String,
        @RequestParam params: MultiValueMap<String, String>,
        @RequestParam("volgende", required = false) next: String?,
        model: Model,
        redirect: RedirectAttributes,
    ): String {
        // Het volgende record bepalen vóór het opslaan: daarna staat dit record op opgeslagen.
        val nextNumber = items.nextNumber(number)
        val values = values(number, params)
        editLog.willSave(number, changesOf(number, values))
        return handle(number, items.save(number, values), model, redirect, if (next != null) nextNumber else null)
    }

    @PostMapping("/records/{nr}/conflict")
    fun resolve(
        @PathVariable("nr") number: String,
        @RequestParam("keuze", defaultValue = "mijn") choice: String,
        @RequestParam params: MultiValueMap<String, String>,
        model: Model,
        redirect: RedirectAttributes,
    ): String {
        val values = values(number, params)
        if (choice == "mijn") editLog.willSave(number, changesOf(number, values))
        val outcome = items.resolveConflict(number, choice == "mijn", values)
        return if (choice == "mijn") handle(number, outcome, model, redirect)
        else showForm(model, number, "info", listOf("De velden die botsten zijn vervangen door de versie uit ZCBS. Controleer het formulier en sla dan op."))
    }

    private fun handle(number: String, outcome: SaveOutcome, model: Model, redirect: RedirectAttributes, next: String? = null): String = when (outcome) {
        is SaveOutcome.Saved -> if (next != null) {
            redirect.addFlashAttribute("saved", number)
            "redirect:/records/$next"
        } else "redirect:/records?opgeslagen=$number"
        is SaveOutcome.NoChanges -> showForm(model, number, "info", listOf("Er is niets gewijzigd; er is niets opgeslagen."))
        is SaveOutcome.Blocked -> showForm(model, number, "blocked", outcome.messages)
        is SaveOutcome.Locked -> showForm(model, number, "locked", listOf(outcome.message))
        is SaveOutcome.Conflict -> showForm(model, number, "conflict", conflict = outcome.rows)
        is SaveOutcome.Failed -> showForm(model, number, "error", listOf(outcome.message))
        is SaveOutcome.NeedsLogin -> { redirect.addFlashAttribute("error", "Je sessie bij ZCBS is verlopen. Log opnieuw in; je invoer blijft bewaard."); "redirect:/" }
    }

    @PostMapping("/records/{nr}/later")
    fun later(@PathVariable("nr") number: String, @RequestParam("volgende", required = false) next: String?): String {
        val nextNumber = items.nextNumber(number)
        items.later(number)
        return if (next != null && nextNumber != null) "redirect:/records/$nextNumber" else "redirect:/records"
    }
}
