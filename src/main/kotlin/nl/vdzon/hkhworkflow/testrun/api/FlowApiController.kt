package nl.vdzon.hkhworkflow.testrun.api

import com.fasterxml.jackson.annotation.JsonInclude
import java.nio.file.Files
import nl.vdzon.hkhworkflow.exerunner.ExeRunner
import nl.vdzon.hkhworkflow.exerunner.RunMode
import nl.vdzon.hkhworkflow.exerunner.RunSnapshot
import nl.vdzon.hkhworkflow.ftp.FtpService
import nl.vdzon.hkhworkflow.items.ZcbsSession
import nl.vdzon.hkhworkflow.restapi.ApiProblem
import nl.vdzon.hkhworkflow.settings.SettingsService
import nl.vdzon.hkhworkflow.testrun.internal.CopyResult
import nl.vdzon.hkhworkflow.testrun.internal.FlowView
import nl.vdzon.hkhworkflow.testrun.internal.ProducedFile
import nl.vdzon.hkhworkflow.testrun.internal.StepStatus
import nl.vdzon.hkhworkflow.testrun.internal.StepType
import nl.vdzon.hkhworkflow.testrun.internal.StepView
import nl.vdzon.hkhworkflow.testrun.internal.TestRunService
import org.springframework.core.io.FileSystemResource
import org.springframework.core.io.Resource
import org.springframework.http.ContentDisposition
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
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter

data class FlowResponse(val phase: Int, val phases: List<PhaseResponse>, val review: Boolean, val finished: Boolean, val running: Boolean)
data class PhaseResponse(val number: Int, val title: String, val state: String, val note: String?)

data class ChecksResponse(val checks: List<CheckResponse>, val allOk: Boolean, val leftovers: List<LeftoverResponse>, val leftoversTarget: String?)
data class CheckResponse(val id: String, val label: String, val ok: Boolean, val detail: String)
data class LeftoverResponse(val area: String, val label: String, val files: List<String>)
data class MessageResponse(val message: String)

data class SourceResponse(val name: String, val path: String)
data class EntriesResponse(val source: String, val path: String, val crumbs: List<FolderResponse>, val folders: List<FolderResponse>, val files: List<SourceFileResponse>)
data class FolderResponse(val name: String, val path: String)
data class SourceFileResponse(val name: String, val kind: String, val sizeKb: Long, val modified: String, val processed: ProcessedResponse?)
data class ProcessedResponse(val number: String, val user: String, val date: String)

data class ChooseFilesRequest(val source: String, val path: String = "", val names: List<String> = emptyList())
data class ChooseFilesResponse(val skipped: List<String>)
data class ChosenFileResponse(val name: String, val by: String, val number: String?, val newName: String?)

data class NumbersResponse(val count: Int, val width: Int, val lastNumber: String?, val suggestion: String?, val ranges: List<RangeResponse>)

/** Een vrije reeks. [size] is null als de reeks open is (onbeperkt); [fits] zegt of alle gekozen bestanden erin passen. */
data class RangeResponse(val from: String, val label: String, val size: Long?, val fits: Boolean)
data class NumbersRequest(val start: String = "")

@JsonInclude(JsonInclude.Include.NON_NULL)
data class StepResponse(
    val name: String,
    val label: String,
    val status: StepStatus,
    val statusLabel: String,
    val current: Boolean,
    val usesProgram: Boolean,
    val question: String,
    val message: String?,
    val plan: List<String>,
    val problems: List<String>,
    val warnings: List<String>,
)

/** `auto` laat de app de vragen van het programma beantwoorden, `manual` laat de gebruiker dat doen via de console. */
data class RunStepRequest(val answers: String = "auto")

data class ResultResponse(
    val number: String,
    val originalName: String?,
    val kind: String,
    val label: String,
    val size: String,
    val resolution: String,
    val note: String,
    /** Staat het op de ftp-server? Null als dat niet is gecontroleerd. */
    val onServer: Boolean?,
)

data class SummaryResponse(val place: String, val kind: String, val count: Int, val files: String, val folder: String)

data class ConsoleResponse(
    val running: Boolean,
    val label: String? = null,
    val output: String = "",
    val exitCode: Int? = null,
    val auto: Boolean = false,
    val autoStuck: Boolean = false,
    val autoAnswered: Int = 0,
    /** De bekende vraag waar het programma op wacht, en het voorgestelde antwoord (nooit een wachtwoord). */
    val question: String? = null,
    val suggestion: String? = null,
    val secondsSinceOutput: Long = 0,
    val suspiciousSilence: Boolean = false,
)

data class ConsoleInputRequest(val line: String = "")

/**
 * "Nieuwe bestanden in ZCBS zetten": de doorloop van deze gebruiker in deze collectie. Er is er per gebruiker en collectie één, dus geen id. Zie docs/api.md
 * en de beschrijving van de fasen bij [FlowView].
 */
@RestController
@RequestMapping("/api/collections/{collection}/flow")
class FlowApiController(
    private val flow: TestRunService,
    private val settingsService: SettingsService,
    private val zcbs: ZcbsSession,
    private val ftpService: FtpService,
) {
    @GetMapping
    fun get(): FlowResponse {
        val view = flow.flowView()
        return FlowResponse(
            view.current,
            view.phases.map { PhaseResponse(it.nr, it.title, it.state, it.note) },
            view.review,
            view.finished,
            flow.exe().isRunning() || view.run?.next?.status == StepStatus.RUNNING.name,
        )
    }

    /** Opnieuw beginnen. De originelen blijven staan. */
    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun reset() = flow.reset()

    @PostMapping("/start")
    fun start(): FlowResponse {
        flow.startFlow()?.let { throw ApiProblem(409, "checks-failed", it) }
        return get()
    }

    // ---- 1. Controleren ----

    @GetMapping("/checks")
    fun checks(): ChecksResponse {
        val checks = flow.environment()
        return ChecksResponse(
            checks.map { CheckResponse(it.id, it.label, it.ok, it.detail) },
            checks.all { it.ok },
            flow.leftovers().map { LeftoverResponse(it.area, it.label, it.names) },
            flow.leftoversTarget()?.toString(),
        )
    }

    @PostMapping("/leftovers/move")
    fun moveLeftovers(): MessageResponse {
        requireNoRun()
        return MessageResponse(flow.clearLeftovers(move = true))
    }

    @PostMapping("/leftovers/accept")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun acceptLeftovers() = flow.acceptInput()

    @DeleteMapping("/leftovers")
    fun deleteLeftovers(): MessageResponse {
        requireNoRun()
        return MessageResponse(flow.clearLeftovers(move = false))
    }

    // ---- 2. Bestanden kiezen ----

    @GetMapping("/sources")
    fun sources() = flow.sources().roots().map { SourceResponse(it.name, it.path) }

    @GetMapping("/sources/{source}/entries")
    fun entries(@PathVariable("source") source: String, @RequestParam("path", defaultValue = "") path: String): EntriesResponse {
        val listing = flow.sources().browse(source, path)
        return EntriesResponse(
            listing.root.name,
            listing.relPath,
            listing.crumbs.map { FolderResponse(it.name, it.relPath) },
            listing.dirs.map { FolderResponse(it.name, it.relPath) },
            listing.files.map { f -> SourceFileResponse(f.name, f.kind, f.sizeKb, f.modified, f.processed?.let { ProcessedResponse(it.number, it.user, it.dateText) }) },
        )
    }

    /** Het aantal pagina's of de afmeting per bestand; apart, omdat het even duurt. */
    @GetMapping("/sources/{source}/details")
    fun details(@PathVariable("source") source: String, @RequestParam("path", defaultValue = "") path: String): Map<String, String> =
        flow.sources().details(source, path)

    @GetMapping("/sources/{source}/file")
    fun sourceFile(
        @PathVariable("source") source: String,
        @RequestParam("path", defaultValue = "") path: String,
        @RequestParam("name") name: String,
        @RequestParam("download", defaultValue = "false") download: Boolean,
    ): ResponseEntity<Resource> {
        val file = try { flow.sources().fileToView(source, path, name) } catch (e: IllegalArgumentException) { throw ApiProblem(404, "file-not-found", e.message ?: "Bestand niet gevonden.") }
        val type = when (name.substringAfterLast('.').lowercase()) {
            "pdf" -> "application/pdf"
            "png" -> "image/png"
            else -> "image/jpeg"
        }
        return ResponseEntity.ok().header("Content-Type", type).header("Content-Disposition", disposition(name, download)).header("Cache-Control", "no-store").body(FileSystemResource(file))
    }

    @GetMapping("/files")
    fun files(): List<ChosenFileResponse> {
        val run = flow.view()
        if (run != null) return run.rows.map { ChosenFileResponse(it.originalName, it.by, it.number, it.newName) }
        return flow.filesInTestFolder().map { ChosenFileResponse(it.fileName.toString(), flow.sources().origin(it.fileName.toString())?.by.orEmpty(), null, null) }
    }

    /** Kiest bestanden uit één map (de originelen blijven staan) en gaat door naar de nummers. Lukt dat niet, dan blijft de flow bij het kiezen. */
    @PutMapping("/files")
    fun chooseFiles(@RequestBody body: ChooseFilesRequest): ChooseFilesResponse {
        requirePhase(2)
        val result = if (body.names.isEmpty()) CopyResult(emptyList(), emptyList()) else flow.sources().copy(body.source, body.path, body.names)
        flow.acceptInput()
        val skipped = if (result.skipped.isEmpty()) "" else " Overgeslagen: ${result.skipped.joinToString()}."
        if (flow.filesInTestFolder().isEmpty()) throw ApiProblem(400, "nothing-selected", "Vink minstens één bestand aan.$skipped")
        flow.confirmFiles()?.let { throw ApiProblem(409, "wrong-phase", it) }
        return ChooseFilesResponse(result.skipped)
    }

    /** Haalt de gekozen kopieën weer weg; de originelen blijven staan. */
    @DeleteMapping("/files")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun clearFiles() {
        requireNoRun()
        flow.sources().removeAllCopies()
    }

    // ---- 3. Nummers ----

    @GetMapping("/numbers")
    fun numbers(): NumbersResponse {
        val count = flow.filesInTestFolder().size
        val free = zcbs.freeNumbers()
            ?: throw ApiProblem(502, "zcbs-failed", "ZCBS gaf de lijst met vrije nummers niet. Is je sessie verlopen, heb je geen recht om records aan te maken, of is ZCBS niet bereikbaar?")
        return NumbersResponse(
            count,
            free.width,
            free.last?.let { free.format(it) },
            free.suggest(count)?.let { free.format(it) },
            free.ranges.map { RangeResponse(free.format(it.from), free.format(it), if (it.open) null else it.size, it.size >= count) },
        )
    }

    /** Geeft de bestanden hun nummers en maakt kopieën met het nummer als naam. */
    @PutMapping("/numbers")
    fun assignNumbers(@RequestBody body: NumbersRequest): FlowResponse {
        requirePhase(3)
        flow.prepare(body.start)?.let { throw ApiProblem(400, "numbers-refused", it) }
        return get()
    }

    // ---- 4 t/m 7. De stappen ----

    @GetMapping("/steps")
    fun steps(): List<StepResponse> =
        flow.view()?.rows?.firstOrNull()?.cells.orEmpty().filter { it.type != StepType.RENAME.name }.map(::step)

    /**
     * Voert de stap uit. Een programma loopt door na dit antwoord (zie de console); de upload en de back-up zijn klaar als het antwoord komt. Mislukt de stap
     * zelf, dan staat dat in zijn status en [StepResponse.message], want dat is een uitkomst en geen fout van de API.
     */
    @PostMapping("/steps/{step}/run")
    fun run(@PathVariable("step") name: String, @RequestBody(required = false) body: RunStepRequest?): StepResponse {
        val type = stepType(name)
        if (flow.flowView().review) throw ApiProblem(409, "review-pending", "Controleer eerst wat het programma heeft gemaakt.")
        val auto = when (body?.answers ?: "auto") {
            "auto" -> true
            "manual" -> false
            else -> throw ApiProblem(400, "invalid-input", "Onbekende keuze voor de antwoorden: ${body?.answers}.")
        }
        flow.execute(stepId(type), RunMode.IN_APP, auto)
        return stepOf(type)
    }

    @PostMapping("/steps/{step}/retry")
    fun retry(@PathVariable("step") name: String): StepResponse {
        val type = stepType(name)
        flow.retry(stepId(type))
        return stepOf(type)
    }

    /** De gebruiker heeft gecontroleerd wat het programma heeft gemaakt: "ja, goed". */
    @PostMapping("/steps/{step}/approve")
    fun approve(@PathVariable("step") name: String): FlowResponse {
        flow.confirmReview(stepType(name))?.let { throw ApiProblem(409, "wrong-phase", it) }
        return get()
    }

    @GetMapping("/results")
    fun results(@RequestParam("step") name: String): List<ResultResponse> {
        val type = stepType(name)
        val names = flow.view()?.rows.orEmpty().associate { it.number to it.originalName }
        return withServerStatus(type, flow.producedFiles(type)).map {
            ResultResponse(it.number, names[it.number], it.kind, it.label, it.size, it.resolution, it.note, when (it.server) { "ja" -> true; "nee" -> false; else -> null })
        }
    }

    @GetMapping("/results/{number}/{kind}")
    fun result(
        @PathVariable("number") number: String,
        @PathVariable("kind") kind: String,
        @RequestParam("download", defaultValue = "false") download: Boolean,
    ): ResponseEntity<ByteArray> {
        val file = flow.resultFile(kind, number) ?: throw ApiProblem(404, "file-not-found", "Dit bestand hoort niet bij deze doorloop.")
        val (type, extension) = when (kind) {
            "pdf" -> "application/pdf" to "pdf"
            "tekst" -> "text/plain;charset=UTF-8" to "txt"
            else -> "image/jpeg" to "jpg"
        }
        val name = "$number-${kind.removePrefix("p2z-")}.$extension"
        return ResponseEntity.ok().header("Content-Type", type).header("Content-Disposition", disposition(name, download)).header("Cache-Control", "no-store").body(Files.readAllBytes(file))
    }

    @GetMapping("/summary")
    fun summary() = flow.summary().map { SummaryResponse(it.place, it.kind, it.count, it.files, it.folder) }

    // ---- De console van het programma ----

    @GetMapping("/console")
    fun console(): ConsoleResponse = console(flow.exe().snapshot())

    /** De console live: een event `console` bij elke verandering; de stream sluit als het programma klaar is. */
    @GetMapping("/console/events", produces = [MediaType.TEXT_EVENT_STREAM_VALUE])
    fun consoleEvents(): SseEmitter {
        val emitter = SseEmitter(EVENT_STREAM_TIMEOUT_MILLIS)
        val runner = flow.exe()
        Thread.ofVirtual().start { streamConsole(runner, emitter) }
        return emitter
    }

    private fun streamConsole(runner: ExeRunner, emitter: SseEmitter) {
        try {
            var last: ConsoleResponse? = null
            while (true) {
                val now = console(runner.snapshot())
                // De tijd sinds de laatste uitvoer loopt altijd door en telt niet als verandering.
                if (now.copy(secondsSinceOutput = 0) != last?.copy(secondsSinceOutput = 0)) emitter.send(SseEmitter.event().name("console").data(now, MediaType.APPLICATION_JSON))
                last = now
                if (!now.running) break
                Thread.sleep(EVENT_STREAM_INTERVAL_MILLIS)
            }
            emitter.complete()
        } catch (e: Exception) {
            // Meestal heeft de browser de pagina verlaten.
            emitter.completeWithError(e)
        }
    }

    @GetMapping("/console/log", produces = ["text/plain;charset=UTF-8"])
    fun consoleLog(): String = flow.exe().logText().orEmpty()

    @PostMapping("/console/input")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun consoleInput(@RequestBody body: ConsoleInputRequest) = flow.exe().sendInput(body.line)

    @PostMapping("/console/stop")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun consoleStop() = flow.exe().kill()

    // ---- Hulp ----

    private fun requirePhase(phase: Int) {
        val current = flow.flowView().current
        if (current != phase) throw ApiProblem(409, "wrong-phase", "De flow is bij fase $current en niet bij fase $phase.")
    }

    private fun requireNoRun() {
        if (flow.view() != null) throw ApiProblem(409, "wrong-phase", "Er is al een doorloop bezig. Begin eerst opnieuw.")
    }

    private fun stepType(name: String): StepType = STEPS[name] ?: throw ApiProblem(404, "step-not-found", "Onbekende stap: $name.")

    private fun stepId(type: StepType): Int = flow.stepId(type) ?: throw ApiProblem(409, "wrong-phase", "Er is nog geen doorloop gestart.")

    private fun stepOf(type: StepType): StepResponse = steps().first { it.name == nameOf(type) }

    private fun nameOf(type: StepType): String = STEPS.entries.first { it.value == type }.key

    private fun step(v: StepView) = StepResponse(
        nameOf(StepType.valueOf(v.type)), v.typeLabel, StepStatus.valueOf(v.status), v.statusLabel, v.current, v.usesExe, v.question, v.message, v.plan, v.problems, v.warnings,
    )

    private fun console(s: RunSnapshot?): ConsoleResponse =
        if (s == null) ConsoleResponse(running = false)
        else ConsoleResponse(s.running, s.label, s.output, s.exitCode, s.auto, s.autoStuck, s.autoAnswered, s.question, s.suggestion, s.secondsSinceOutput, s.suspiciousSilence)

    private fun disposition(name: String, download: Boolean): String =
        (if (download) ContentDisposition.attachment() else ContentDisposition.inline()).filename(name, Charsets.UTF_8).build().toString()

    /** Bij images.exe: staat elk gemaakt bestand op de ftp-server? Lukt de verbinding niet, dan blijft het onbekend. */
    private fun withServerStatus(type: StepType, produced: List<ProducedFile>): List<ProducedFile> {
        if (type != StepType.IMAGES) return produced
        val s = settingsService.get()
        val checked = setOf("small", "large", "100pct")
        fun path(p: ProducedFile) = "${s.remoteImagesDir(p.kind)}/${p.number}.jpg"
        val existing = ftpService.existing(produced.filter { it.kind in checked }.map(::path)) ?: return produced
        return produced.map { if (it.kind in checked) it.copy(server = if (path(it) in existing) "ja" else "nee") else it }
    }

    private companion object {
        val STEPS = mapOf("pdf2zcbs" to StepType.PDF2ZCBS, "images" to StepType.IMAGES, "upload" to StepType.UPLOAD, "backup" to StepType.CLEANUP)
        const val EVENT_STREAM_TIMEOUT_MILLIS = 30 * 60_000L
        const val EVENT_STREAM_INTERVAL_MILLIS = 500L
    }
}
