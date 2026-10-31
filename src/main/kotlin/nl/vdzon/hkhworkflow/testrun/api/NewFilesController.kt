package nl.vdzon.hkhworkflow.testrun.api

import java.nio.file.Files
import nl.vdzon.hkhworkflow.ftp.FtpService
import nl.vdzon.hkhworkflow.items.ItemEditService
import nl.vdzon.hkhworkflow.settings.SettingsService
import nl.vdzon.hkhworkflow.testrun.ProcessedLog
import nl.vdzon.hkhworkflow.testrun.ProducedFile
import nl.vdzon.hkhworkflow.testrun.CopyResult
import nl.vdzon.hkhworkflow.testrun.StepType
import nl.vdzon.hkhworkflow.testrun.TestRunService
import org.springframework.core.io.FileSystemResource
import org.springframework.core.io.Resource
import org.springframework.http.ContentDisposition
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.servlet.mvc.support.RedirectAttributes

/**
 * "Nieuwe bestanden in ZCBS zetten": de flow die Carla doorloopt. Ze kiest scans uit de mappen op K:, geeft ze recordnummers uit
 * de vrije nummers van ZCBS, draait pdf2zcbs.exe en images.exe (met een controle na elk), uploadt tekst en pdf en eindigt met de
 * back-up. Het invullen van de gegevens in ZCBS hoort er niet bij.
 */
@Controller
class NewFilesController(
    private val testRunService: TestRunService,
    private val settingsService: SettingsService,
    private val itemEditService: ItemEditService,
    private val processedLog: ProcessedLog,
    private val ftpService: FtpService,
) {
    /** Een gekozen bestand in de invoermap, met de bronmap waar het vandaan kwam. */
    data class PdfRow(val name: String, val by: String, val folder: String)

    /** Een vrije reeks uit ZCBS voor de tabel: [fits] is waar als alle bestanden erin passen. */
    data class RangeRow(val from: String, val label: String, val size: String, val fits: Boolean)

    @GetMapping("/nieuw")
    fun page(
        @RequestParam("bron", required = false) rootName: String?,
        @RequestParam("pad", defaultValue = "") path: String,
        model: Model,
    ): String {
        // Heeft een andere gebruiker deze collectie in gebruik, dan is er maar één ding te zien: wie en sinds wanneer.
        testRunService.lockedBy()?.let { lock ->
            model.addAttribute("active", "nieuw")
            model.addAttribute("lock", lock)
            model.addAttribute("sinceText", java.time.LocalTime.ofInstant(lock.since, java.time.ZoneId.systemDefault()).toString().take(5))
            model.addAttribute("activeText", java.time.LocalTime.ofInstant(lock.lastActive, java.time.ZoneId.systemDefault()).toString().take(5))
            return "nieuw-bezet"
        }
        val settings = settingsService.get()
        // Klaar is één keer te zien; opent Carla de pagina daarna weer, dan begint ze weer bij het begin.
        val (flow, summary) = testRunService.pageState()
        if (summary != null) model.addAttribute("summary", summary)
        // Standaard de eerste map met scans uit de instellingen, zodat Carla meteen bestanden ziet.
        val rootName = rootName?.takeIf { it.isNotBlank() } ?: testRunService.sources().roots().firstOrNull()?.name.takeIf { flow.current == 2 }
        model.addAttribute("active", "nieuw")
        model.addAttribute("returnTo", "/nieuw")
        model.addAttribute("settings", settings)
        model.addAttribute("flow", flow)
        model.addAttribute("run", flow.run)
        model.addAttribute("console", testRunService.exe().snapshot())
        when (flow.current) {
            1 -> {
                val checks = testRunService.environment()
                val leftovers = testRunService.leftovers()
                model.addAttribute("checks", checks)
                model.addAttribute("allOk", checks.all { it.ok })
                model.addAttribute("leftovers", leftovers)
                model.addAttribute("leftoversTarget", testRunService.leftoversTarget()?.toString().orEmpty())
            }

            2 -> {
                val chosen = testRunService.filesInTestFolder().map {
                    val origin = testRunService.sources().origin(it.fileName.toString())
                    PdfRow(it.fileName.toString(), origin?.by.orEmpty(), origin?.folder.orEmpty())
                }
                model.addAttribute("roots", testRunService.sources().roots())
                model.addAttribute("chosen", chosen)
                if (rootName != null) {
                    try {
                        model.addAttribute("listing", testRunService.sources().browse(rootName, path))
                    } catch (e: IllegalArgumentException) {
                        model.addAttribute("browseError", e.message)
                    }
                }
            }

            3 -> {
                val count = testRunService.filesInTestFolder().size
                val free = itemEditService.freeNumbers()
                model.addAttribute("count", count)
                model.addAttribute("free", free)
                if (free != null) {
                    model.addAttribute("ranges", free.ranges.map { RangeRow(free.format(it.from), free.format(it), if (it.open) "onbeperkt" else it.size.toString(), it.size >= count) })
                    model.addAttribute("lastNumber", free.last?.let { free.format(it) })
                    model.addAttribute("suggestion", free.suggest(count)?.let { free.format(it) })
                    model.addAttribute("width", free.width)
                }
            }

            4, 5 -> if (flow.review && flow.run != null) {
                val type = if (flow.current == 4) StepType.PDF2ZCBS else StepType.IMAGES
                model.addAttribute("reviewStepId", flow.run.rows.first().cells[StepType.entries.indexOf(type)].id)
                model.addAttribute("reviewType", type.name)
                model.addAttribute("produced", withServerStatus(type, testRunService.producedFiles(type)))
                model.addAttribute("names", flow.run.rows.associate { it.number to it.originalName })
            }
        }
        return "nieuw"
    }

    /**
     * Bij images.exe: staat elk gemaakte bestand (small, large, 100pct) op de ftp-server? Het programma uploadt ze zelf; zo zie je of dat gelukt is.
     * Lukt de verbinding niet, dan blijft de kolom leeg in plaats van "nee".
     */
    private fun withServerStatus(type: StepType, produced: List<ProducedFile>): List<ProducedFile> {
        if (type != StepType.IMAGES) return produced
        val s = settingsService.get()
        fun path(p: ProducedFile) = "${s.remoteImagesDir(p.kind)}/${p.number}.jpg"
        val existing = ftpService.existing(produced.filter { it.kind in setOf("small", "large", "100pct") }.map(::path)) ?: return produced
        return produced.map { if (it.kind in setOf("small", "large", "100pct")) it.copy(server = if (path(it) in existing) "ja" else "nee") else it }
    }

    /** Alles is in orde en Carla gaat verder met het kiezen van bestanden. */
    @PostMapping("/nieuw/start")
    fun begin(redirect: RedirectAttributes): String = attempt(redirect) { testRunService.startFlow() }

    private fun attempt(redirect: RedirectAttributes, vararg query: Pair<String, String>, action: () -> String?): String {
        try {
            action()?.let { redirect.addFlashAttribute("error", it) }
        } catch (e: Exception) {
            redirect.addFlashAttribute("error", e.message ?: e.javaClass.simpleName)
        }
        query.forEach { (k, v) -> redirect.addAttribute(k, v) }
        return "redirect:/nieuw"
    }

    /**
     * "Verder" bij het kiezen: de aangevinkte pdf's uit de map waar Carla nu is worden gekozen (naar de invoermap gekopieerd) en de flow gaat door naar de nummers.
     * Lukt dat niet (niets aangevinkt, alles al verwerkt), dan blijft ze in de map met een melding.
     */
    @PostMapping("/nieuw/kopieer")
    fun copy(
        @RequestParam("bron") rootName: String,
        @RequestParam("pad", defaultValue = "") path: String,
        @RequestParam("files", required = false) files: List<String>?,
        redirect: RedirectAttributes,
    ): String {
        fun stay(message: String): String {
            redirect.addFlashAttribute("error", message)
            redirect.addAttribute("bron", rootName)
            redirect.addAttribute("pad", path)
            return "redirect:/nieuw"
        }
        return try {
            val result = if (files.isNullOrEmpty()) CopyResult(emptyList(), emptyList()) else testRunService.sources().copy(rootName, path, files)
            testRunService.acceptInput()
            val skipped = if (result.skipped.isEmpty()) "" else " Overgeslagen: ${result.skipped.joinToString()}."
            if (testRunService.filesInTestFolder().isEmpty()) return stay("Vink minstens één bestand aan.$skipped")
            testRunService.confirmFiles()?.let { return stay(it) }
            if (skipped.isNotEmpty()) redirect.addFlashAttribute("info", skipped.trim())
            "redirect:/nieuw"
        } catch (e: Exception) {
            stay(e.message ?: e.javaClass.simpleName)
        }
    }

    /** Haalt de gekozen bestanden weer weg (alleen de kopieën; de originelen blijven staan). */
    @PostMapping("/nieuw/bestanden/leeg")
    fun clearChosen(redirect: RedirectAttributes): String = attempt(redirect) { testRunService.sources().removeAllCopies(); null }

    /** Resten van een eerdere run uit de mappen van de programma's halen: verplaatsen of verwijderen. */
    @PostMapping("/nieuw/resten")
    fun leftovers(@RequestParam("actie") action: String, redirect: RedirectAttributes): String = attempt(redirect) {
        if (action == "accepteer") {
            testRunService.acceptInput()
            redirect.addFlashAttribute("info", "Je gekozen bestanden horen erbij.")
        } else redirect.addFlashAttribute("info", testRunService.clearLeftovers(move = action == "verplaats"))
        null
    }

    /** Geeft de bestanden hun recordnummers en maakt kopieën met het nummer als naam. */
    @PostMapping("/nieuw/voorbereiden")
    fun prepare(@RequestParam("start", defaultValue = "") start: String, redirect: RedirectAttributes): String =
        attempt(redirect) { testRunService.prepare(start) }

    /** Carla heeft de uitvoer van pdf2zcbs.exe of images.exe gecontroleerd. */
    @PostMapping("/nieuw/gecontroleerd")
    fun reviewed(@RequestParam("stap") type: StepType, redirect: RedirectAttributes): String =
        attempt(redirect) { testRunService.confirmReview(type) }

    @PostMapping("/nieuw/opnieuw")
    fun reset(redirect: RedirectAttributes): String = attempt(redirect) { testRunService.reset(); null }

    /** Een bestand uit de uitvoer van de programma's, om te controleren: de afbeelding, de pdf of de tekst. */
    @GetMapping("/nieuw/resultaat/{kind}/{number}")
    fun result(
        @PathVariable("kind") kind: String,
        @PathVariable("number") number: String,
        @RequestParam("download", required = false) download: String?,
    ): ResponseEntity<ByteArray> {
        val file = testRunService.resultFile(kind, number) ?: return ResponseEntity.notFound().build()
        val (type, extension) = when (kind) {
            "pdf" -> "application/pdf" to "pdf"
            "tekst" -> "text/plain;charset=UTF-8" to "txt"
            else -> "image/jpeg" to "jpg"
        }
        val name = "$number-${kind.removePrefix("p2z-")}.$extension"
        val disposition = (if (download != null) ContentDisposition.attachment() else ContentDisposition.inline()).filename(name, Charsets.UTF_8).build()
        return ResponseEntity.ok().header("Content-Type", type).header("Content-Disposition", disposition.toString()).header("Cache-Control", "no-store").body(Files.readAllBytes(file))
    }

    /** Een pdf of afbeelding uit een map met scans, om te bekijken in de popup (of met `download` te bewaren). */
    @GetMapping("/nieuw/bron/bestand")
    fun sourceFile(
        @RequestParam("bron") rootName: String,
        @RequestParam("pad", defaultValue = "") path: String,
        @RequestParam("naam") name: String,
        @RequestParam("download", required = false) download: String?,
    ): ResponseEntity<Resource> {
        val file = try { testRunService.sources().fileToView(rootName, path, name) } catch (e: IllegalArgumentException) { return ResponseEntity.notFound().build() }
        val type = when (name.substringAfterLast('.').lowercase()) {
            "pdf" -> "application/pdf"
            "png" -> "image/png"
            else -> "image/jpeg"
        }
        val disposition = (if (download != null) ContentDisposition.attachment() else ContentDisposition.inline()).filename(name, Charsets.UTF_8).build()
        return ResponseEntity.ok().header("Content-Type", type).header("Content-Disposition", disposition.toString()).header("Cache-Control", "no-store").body(FileSystemResource(file))
    }

    /** Het aantal pagina's van elke pdf en de afmeting van elke afbeelding in de map, apart opgehaald zodat de lijst snel verschijnt. */
    @GetMapping("/nieuw/bron/info")
    fun sourceInfo(@RequestParam("bron") rootName: String, @RequestParam("pad", defaultValue = "") path: String): ResponseEntity<Map<String, String>> =
        try { ResponseEntity.ok().header("Cache-Control", "no-store").body(testRunService.sources().details(rootName, path)) } catch (e: IllegalArgumentException) { ResponseEntity.badRequest().build() }

    /** Wat er al op het archief is gezet, per keer verwerken. */
    @GetMapping("/verwerkt")
    fun processed(model: Model): String {
        model.addAttribute("active", "verwerkt")
        model.addAttribute("runs", processedLog.runs())
        model.addAttribute("logFile", processedLog.file().toString())
        model.addAttribute("settings", settingsService.get())
        return "verwerkt"
    }
}
