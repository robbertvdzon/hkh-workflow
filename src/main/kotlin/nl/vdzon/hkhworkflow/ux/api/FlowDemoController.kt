package nl.vdzon.hkhworkflow.ux.api

import nl.vdzon.hkhworkflow.ux.DemoData
import nl.vdzon.hkhworkflow.ux.DemoRecord
import nl.vdzon.hkhworkflow.ux.FlowDemoData
import nl.vdzon.hkhworkflow.ux.FlowDemoState
import nl.vdzon.hkhworkflow.ux.RecStatus
import jakarta.servlet.http.HttpServletResponse
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.util.MultiValueMap
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestParam

/** De klikbare demo van de hele flow (inloggen tot afsluiten), met nepgegevens. */
@Controller
class FlowDemoController(private val state: FlowDemoState) {

    private fun common(model: Model) {
        model.addAttribute("active", "demoflow")
        model.addAttribute("state", state)
        model.addAttribute("phases", FlowDemoData.phases)
        model.addAttribute("zcbsBase", DemoData.ZCBS_BASE)
        model.addAttribute("inputFiles", FlowDemoData.inputFiles)
    }

    @GetMapping("/demo-flow")
    fun start(model: Model): String {
        common(model)
        if (state.user == null) return "demo-login"
        state.running()
        if (state.finished) return "redirect:/demo-flow/klaar"
        return "demo-flow"
    }

    @PostMapping("/demo-flow/login")
    fun login(@RequestParam("naam", defaultValue = "") name: String, @RequestParam("wachtwoord", defaultValue = "") password: String, model: Model): String {
        val error = state.login(name, password)
        if (error == null) return "redirect:/demo-flow"
        common(model)
        model.addAttribute("error", error)
        model.addAttribute("naam", name)
        return "demo-login"
    }

    @PostMapping("/demo-flow/opnieuw")
    fun again(): String { state.reset(); return "redirect:/demo-flow" }

    @GetMapping("/demo-flow/kaart")
    fun stage(model: Model, response: HttpServletResponse): String {
        common(model)
        state.running()
        if (state.finished) response.setHeader("HX-Redirect", "/demo-flow/klaar")
        return "demo-flow :: stage"
    }

    @PostMapping("/demo-flow/bestanden")
    fun confirmFiles(): String { state.confirmFiles(); return "redirect:/demo-flow" }

    @PostMapping("/demo-flow/voorbereiden")
    fun prepare(@RequestParam("start", defaultValue = "") start: String, model: Model): String {
        val error = state.prepare(start)
        if (error == null) return "redirect:/demo-flow"
        common(model)
        model.addAttribute("prepError", error)
        model.addAttribute("startInput", start)
        return "demo-flow"
    }

    @PostMapping("/demo-flow/start")
    fun run(@RequestParam("falen", defaultValue = "") fail: String): String {
        if (state.current == 5) state.failPhase5 = fail == "ja"
        state.start()
        return "redirect:/demo-flow"
    }

    @PostMapping("/demo-flow/opnieuw-proberen")
    fun retry(): String { state.retry(); return "redirect:/demo-flow" }

    @PostMapping("/demo-flow/volgende")
    fun next(): String {
        state.next()
        return if (state.current == 7 && state.allHandled()) "redirect:/demo-flow/records" else "redirect:/demo-flow"
    }

    @GetMapping("/demo-flow/klaar")
    fun done(model: Model): String {
        common(model)
        if (state.user == null) return "redirect:/demo-flow"
        return "demo-klaar"
    }

    // ---- records bewerken ----

    @GetMapping("/demo-flow/records")
    fun records(@RequestParam("opgeslagen", required = false) saved: Int?, model: Model): String {
        common(model)
        if (state.user == null || state.records.isEmpty()) return "redirect:/demo-flow"
        model.addAttribute("saved", saved)
        return "demo-records"
    }

    private fun form(model: Model, r: DemoRecord, values: Map<String, String>, themes: List<String>, panel: String?, scenario: String = "normaal"): String {
        common(model)
        model.addAttribute("r", r)
        model.addAttribute("fields", FlowDemoData.fields)
        model.addAttribute("v", values)
        model.addAttribute("themes", ArrayList(themes))
        model.addAttribute("panel", panel)
        model.addAttribute("lockFrom", state.lockFrom())
        model.addAttribute("lockUntil", state.lockUntil())
        model.addAttribute("position", state.records.indexOf(r) + 1)
        model.addAttribute("ocr", FlowDemoData.SAMPLE_OCR)
        model.addAttribute("scenario", scenario)
        return "demo-record"
    }

    private fun values(params: MultiValueMap<String, String>): Pair<Map<String, String>, List<String>> {
        val v = FlowDemoData.fields.filter { it.kind != "multi" }.associate { it.name to params.getFirst(it.name).orEmpty().trim() }
        return v to params["a24"].orEmpty().filter { it.isNotBlank() }
    }

    @GetMapping("/demo-flow/records/{nr}")
    fun record(@PathVariable nr: Int, model: Model): String {
        val r = state.record(nr) ?: return "redirect:/demo-flow/records"
        state.open(r)
        val panel = if (r.status == RecStatus.ONVEILIG) "onveilig" else null
        return form(model, r, r.values, r.themes, panel)
    }

    @PostMapping("/demo-flow/records/{nr}/voorbeeld")
    fun preview(@PathVariable nr: Int, @RequestParam params: MultiValueMap<String, String>, model: Model): String {
        val r = state.record(nr) ?: return "redirect:/demo-flow/records"
        val (v, themes) = values(params)
        common(model)
        val changes = FlowDemoData.fields.mapNotNull { f ->
            val old = if (f.kind == "multi") r.themes.joinToString("; ") else r.values[f.name].orEmpty()
            val new = if (f.kind == "multi") themes.joinToString("; ") else v[f.name].orEmpty()
            if (old != new && !(f.name == "a21" && old.isEmpty() && new == "neen")) mapOf("label" to f.label, "old" to old, "new" to new) else null
        }
        model.addAttribute("r", r)
        model.addAttribute("changes", changes)
        model.addAttribute("v", v)
        model.addAttribute("themes", themes)
        model.addAttribute("scenario", params.getFirst("scenario") ?: "normaal")
        return "demo-voorbeeld"
    }

    @PostMapping("/demo-flow/records/{nr}/terug")
    fun back(@PathVariable nr: Int, @RequestParam params: MultiValueMap<String, String>, model: Model): String {
        val r = state.record(nr) ?: return "redirect:/demo-flow/records"
        val (v, themes) = values(params)
        return form(model, r, v, themes, null, params.getFirst("scenario") ?: "normaal")
    }

    @PostMapping("/demo-flow/records/{nr}/zcbs-versie")
    fun theirVersion(@PathVariable nr: Int, @RequestParam params: MultiValueMap<String, String>, model: Model): String {
        val r = state.record(nr) ?: return "redirect:/demo-flow/records"
        val (v, themes) = values(params)
        return form(model, r, v + ("a5" to FlowDemoData.THEIR_TITLE), themes, null, "normaal")
    }

    @PostMapping("/demo-flow/records/{nr}/opslaan")
    fun save(@PathVariable nr: Int, @RequestParam params: MultiValueMap<String, String>, model: Model): String {
        val r = state.record(nr) ?: return "redirect:/demo-flow/records"
        val (v, themes) = values(params)
        val force = params.getFirst("force") == "ja"
        val scenario = if (force) "normaal" else params.getFirst("scenario") ?: "normaal"
        return when (scenario) {
            "vergrendeld" -> form(model, r, v, themes, "vergrendeld", "normaal")
            "gewijzigd" -> {
                model.addAttribute("theirTitle", FlowDemoData.THEIR_TITLE)
                model.addAttribute("myTitle", v["a5"].orEmpty())
                form(model, r, v, themes, "gewijzigd", "normaal")
            }
            else -> {
                r.values = v; r.themes = themes; r.status = RecStatus.OPGESLAGEN
                "redirect:/demo-flow/records?opgeslagen=$nr"
            }
        }
    }

    @PostMapping("/demo-flow/records/{nr}/later")
    fun later(@PathVariable nr: Int): String {
        state.record(nr)?.let { if (it.status != RecStatus.OPGESLAGEN) it.status = RecStatus.LATER }
        return "redirect:/demo-flow/records"
    }
}
