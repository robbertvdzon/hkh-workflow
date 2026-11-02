package nl.vdzon.hkhworkflow.items.api

import nl.vdzon.hkhworkflow.adminlog.AdminLogClient
import nl.vdzon.hkhworkflow.items.Change
import nl.vdzon.hkhworkflow.items.ConflictRow
import nl.vdzon.hkhworkflow.items.EditLogReporter
import nl.vdzon.hkhworkflow.items.Current
import nl.vdzon.hkhworkflow.items.EditStatus
import nl.vdzon.hkhworkflow.items.ItemEditService
import nl.vdzon.hkhworkflow.items.OpenOutcome
import nl.vdzon.hkhworkflow.items.PreviewOutcome
import nl.vdzon.hkhworkflow.items.RecentRecord
import nl.vdzon.hkhworkflow.items.RecentRecords
import nl.vdzon.hkhworkflow.items.RecentScope
import nl.vdzon.hkhworkflow.items.SaveOutcome
import nl.vdzon.hkhworkflow.zcbs.ControlKind
import jakarta.servlet.http.HttpServletResponse
import jakarta.servlet.http.HttpSession
import nl.vdzon.hkhworkflow.items.RememberLogin
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.util.MultiValueMap
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.ResponseBody
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.servlet.mvc.support.RedirectAttributes

/** Inloggen bij ZCBS en de pagina's om records te bewerken via het ZCBS-formulier. */
@Controller
class ItemsController(
    private val items: ItemEditService,
    private val remember: RememberLogin,
    private val adminLog: AdminLogClient,
    private val editLog: EditLogReporter,
    private val recent: RecentRecords,
) {

    private fun back(target: String): String = when (target) { "/records" -> "redirect:/records"; "/nieuw" -> "redirect:/nieuw"; "/test" -> "redirect:/test"; else -> "redirect:/" }

    @PostMapping("/zcbs/login")
    fun login(
        @RequestParam("naam", defaultValue = "") name: String,
        @RequestParam("wachtwoord", defaultValue = "") password: String,
        @RequestParam("terug", defaultValue = "/") target: String,
        redirect: RedirectAttributes,
        response: HttpServletResponse,
        session: HttpSession,
    ): String {
        val error = items.login(name, password)
        adminLog.login(name.trim(), error == null, "PASSWORD", error)
        if (error != null) redirect.addFlashAttribute("error", error)
        else {
            session.setAttribute(USER_ATTRIBUTE, items.user)
            remember.remember(response, name, password)
        }
        return back(target)
    }

    @PostMapping("/zcbs/logout")
    fun logout(@RequestParam("terug", defaultValue = "/") target: String, response: HttpServletResponse, session: HttpSession): String {
        items.logout()
        session.removeAttribute(USER_ATTRIBUTE)
        remember.forget(response)
        return back(target)
    }

    /** Een rij van de lijst met recente records, klaar voor de pagina (de kolommen staan in `records-lijst.html`). */
    data class RecentRow(
        val number: String, val title: String?, val toDescribe: Boolean,
        /** `pdf` of `jpg`, of leeg als het record geen bestand op de site heeft. */
        val type: String,
        val date: String, val time: String,
        /** De gebruiker, hooguit 8 tekens. */
        val who: String,
        val busyBy: String?,
        /** `pdf` of `image`, en het adres, als het record een bestand op de site heeft; zie de knop Open. */
        val mediaKind: String?, val mediaUrl: String?,
    )

    /**
     * De pagina zelf: het invoerveld staat er meteen. De lijst met recente records duurt even (de titels komen uit ZCBS) en wordt daarom
     * apart opgehaald, zie [recentList].
     */
    @GetMapping("/records")
    fun list(
        @RequestParam("opgeslagen", required = false) saved: String?,
        @RequestParam("toon", defaultValue = "mijn") show: String,
        model: Model,
    ): String {
        model.addAttribute("active", "records")
        model.addAttribute("items", items)
        model.addAttribute("saved", saved)
        model.addAttribute("loggedIn", items.loggedIn())
        model.addAttribute("show", scopeName(scopeOf(show)))
        return "records"
    }

    private fun scopeOf(show: String) = when (show) { "alles" -> RecentScope.ALL; "beschrijven" -> RecentScope.TO_DESCRIBE; else -> RecentScope.MINE }
    private fun scopeName(scope: RecentScope) = when (scope) { RecentScope.ALL -> "alles"; RecentScope.TO_DESCRIBE -> "beschrijven"; else -> "mijn" }

    /** Een stuk van de lijst met recente records (het eerste, of met [offset] het volgende bij "Meer laden"); wordt door de pagina met htmx opgehaald. */
    @GetMapping("/records/lijst")
    fun recentList(
        @RequestParam("toon", defaultValue = "mijn") show: String,
        @RequestParam("van", defaultValue = "0") offset: Int,
        model: Model,
    ): String {
        val scope = scopeOf(show)
        val from = offset.coerceAtLeast(0)
        val me = items.user.orEmpty()
        val page = try {
            recent.page(me, Current.collection(), scope, PAGE_SIZE, from)
        } catch (e: Exception) {
            model.addAttribute("failed", "De records konden niet worden opgehaald: ${e.message ?: e.javaClass.simpleName}")
            null
        }
        model.addAttribute("show", scopeName(scope))
        model.addAttribute("first", from == 0)
        model.addAttribute("empty", page != null && page.records.isEmpty() && from == 0)
        model.addAttribute("rows", page?.let { rows(it.records) }.orEmpty())
        model.addAttribute("more", page?.more == true)
        model.addAttribute("nextFrom", from + PAGE_SIZE)
        return "records-lijst :: rows"
    }

    /** De knop naar het volgende eigen record dat nog beschreven moet worden, na het opslaan van [number]; apart opgehaald omdat het even duurt. */
    @GetMapping("/records/volgende")
    fun nextToDescribeButton(@RequestParam("na") number: String, model: Model): String {
        model.addAttribute("nextNumber", nextToDescribe(number))
        return "records-lijst :: volgende"
    }

    /** Het bestand van een record voor de knop Open bij het invoerveld: `{kind, url}` of `{error}`. */
    @GetMapping("/records/media")
    @ResponseBody
    fun media(@RequestParam("nummer", defaultValue = "") number: String): Map<String, String> {
        val clean = number.trim()
        if (!clean.matches(Regex("\\d{1,9}"))) return mapOf("error" to "Vul één recordnummer in, bijvoorbeeld 11934.")
        val padded = items.padded(clean)
        val media = items.mediaOf(padded) ?: return mapOf("error" to "Bij record $padded staat geen pdf of afbeelding op de site.")
        return mapOf("kind" to media.kind, "url" to media.url, "title" to "Record $padded")
    }

    /**
     * Opent een record in ZCBS zelf, meteen ingelogd met de gegevens waarmee je hier bent ingelogd: een pagina die de browser met één formulier naar
     * ZCBS laat posten (naam, wachtwoord en recordnummer). ZCBS toont dan het record en zet zijn eigen cookies, zodat je daar ingelogd blijft.
     * De pagina bevat je wachtwoord en wordt dus nooit onthouden of doorgegeven.
     */
    @GetMapping("/records/{nr}/zcbs")
    fun openInZcbs(@PathVariable("nr") number: String, model: Model, response: HttpServletResponse, redirect: RedirectAttributes): String {
        val clean = number.trim()
        val handoff = if (clean.matches(Regex("\\d{1,9}"))) items.zcbsHandoff(items.padded(clean)) else null
        if (handoff == null) {
            redirect.addFlashAttribute("error", "Je bent niet ingelogd bij ZCBS of dit is geen recordnummer.")
            return "redirect:/records"
        }
        response.setHeader("Cache-Control", "no-store")
        response.setHeader("Referrer-Policy", "no-referrer")
        model.addAttribute("h", handoff)
        return "zcbs-open"
    }

    private fun rows(records: List<RecentRecord>): List<RecentRow> {
        val date = java.time.format.DateTimeFormatter.ofPattern("dd-MM-yyyy")
        val time = java.time.format.DateTimeFormatter.ofPattern("HH:mm")
        return records.map {
            RecentRow(
                it.number, it.title, it.toDescribe, when (it.media?.kind) { "pdf" -> "pdf"; "image" -> "jpg"; else -> "" },
                it.at.format(date), it.at.format(time), it.by.trim().take(8), it.busyBy, it.media?.kind, it.media?.url,
            )
        }
    }

    /** Opent één record: het nummer dat iemand invult. */
    @PostMapping("/records/kies")
    fun choose(@RequestParam("nummer", defaultValue = "") number: String, redirect: RedirectAttributes): String {
        val clean = number.trim()
        if (!clean.matches(Regex("\\d{1,9}"))) {
            redirect.addFlashAttribute("error", "Vul één recordnummer in, bijvoorbeeld 11934.")
            return "redirect:/records"
        }
        val result = items.chooseRecords(clean)
        if (result.error != null) {
            redirect.addFlashAttribute("error", if (result.missing.isNotEmpty()) "Record $clean bestaat niet in ZCBS." else result.error)
            return "redirect:/records"
        }
        return "redirect:/records/${items.records().first().number}"
    }

    /** Het volgende eigen record dat nog niet is beschreven, na [number]. */
    private fun nextToDescribe(number: String): String? =
        if (items.loggedIn()) recent.nextToDescribe(items.user.orEmpty(), Current.collection(), number) else null

    private fun showForm(model: Model, number: String, panel: String? = null, messages: List<String> = emptyList(), conflict: List<ConflictRow> = emptyList()): String {
        val r = items.record(number) ?: return "redirect:/records"
        model.addAttribute("active", "records")
        model.addAttribute("r", r)
        model.addAttribute("controls", items.controlsFor(number))
        model.addAttribute("panel", panel)
        model.addAttribute("messages", messages)
        model.addAttribute("conflict", conflict)
        model.addAttribute("openedAt", items.openedAt(number))
        model.addAttribute("openedUntil", items.openedUntil(number))
        model.addAttribute("zcbsUrl", items.recordUrl(number))
        val pdfUrl = items.pdfUrl(number)
        model.addAttribute("pdfUrl", pdfUrl)
        model.addAttribute("imageUrl", if (pdfUrl == null) items.imageUrl(number) else null)
        model.addAttribute("ocr", items.ocrText(number))
        return "record"
    }

    @GetMapping("/records/{nr}")
    fun open(@PathVariable("nr") number: String, model: Model, redirect: RedirectAttributes): String {
        // Een record uit de lijst: het wordt gekozen als het nog niet klaarstaat.
        if (items.record(number) == null && (!items.loggedIn() || items.chooseRecords(number).error != null)) {
            redirect.addFlashAttribute("error", if (items.loggedIn()) "Record $number bestaat niet in ZCBS." else "Log eerst in bij ZCBS.")
            return "redirect:/records"
        }
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
        model.addAttribute("openedAt", null)
        model.addAttribute("openedUntil", null)
        model.addAttribute("zcbsUrl", items.recordUrl(number))
        model.addAttribute("pdfUrl", null)
        model.addAttribute("imageUrl", null)
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
        model: Model,
        redirect: RedirectAttributes,
    ): String {
        val values = values(number, params)
        editLog.willSave(number, changesOf(number, values))
        return handle(number, items.save(number, values), model, redirect)
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

    private fun handle(number: String, outcome: SaveOutcome, model: Model, redirect: RedirectAttributes): String = when (outcome) {
        is SaveOutcome.Saved -> { recent.forget(number); "redirect:/records?opgeslagen=$number" }
        is SaveOutcome.NoChanges -> showForm(model, number, "info", listOf("Er is niets gewijzigd; er is niets opgeslagen."))
        is SaveOutcome.Blocked -> showForm(model, number, "blocked", outcome.messages)
        is SaveOutcome.Locked -> showForm(model, number, "locked", listOf(outcome.message))
        is SaveOutcome.Conflict -> showForm(model, number, "conflict", conflict = outcome.rows)
        is SaveOutcome.Failed -> showForm(model, number, "error", listOf(outcome.message))
        is SaveOutcome.NeedsLogin -> { redirect.addFlashAttribute("error", "Je sessie bij ZCBS is verlopen. Log opnieuw in; je invoer blijft bewaard."); "redirect:/" }
    }

    /** Annuleren: het record wordt niet opgeslagen en je gaat terug naar Records aanpassen. */
    @PostMapping("/records/{nr}/later")
    fun later(@PathVariable("nr") number: String): String {
        items.later(number)
        return "redirect:/records"
    }

    private companion object {
        /** Zoveel records tegelijk in de lijst met recente records. */
        const val PAGE_SIZE = 20

        /** De naam van het sessiekenmerk waarin staat wie is ingelogd; zie `CurrentContextFilter` (die staat in een andere module). */
        const val USER_ATTRIBUTE = "zcbsUser"
    }
}
