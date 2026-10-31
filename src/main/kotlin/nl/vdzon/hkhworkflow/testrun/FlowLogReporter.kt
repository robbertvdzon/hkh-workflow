package nl.vdzon.hkhworkflow.testrun

import java.time.Instant
import java.util.UUID
import nl.vdzon.hkhworkflow.adminlog.AdminLogClient
import nl.vdzon.hkhworkflow.adminlog.SessionReport
import nl.vdzon.hkhworkflow.adminlog.SnapshotSanitizer
import nl.vdzon.hkhworkflow.exerunner.ExeRunner
import nl.vdzon.hkhworkflow.items.ItemEditService
import nl.vdzon.hkhworkflow.settings.SettingsService
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper
import tools.jackson.databind.node.ObjectNode

/**
 * Meldt elke doorloop van "Nieuwe bestanden" aan het workflow-logboek van hkh-admin, met alles wat de app weet: de instellingen, de gekozen
 * bestanden, de stappen en de uitvoer en het log van pdf2zcbs.exe en images.exe. Een doorloop is een sessie met een eigen uuid vanaf het
 * moment dat de gebruiker de controle van fase 1 heeft afgerond; ze eindigt bij Klaar of als de gebruiker opnieuw begint. De stand wordt
 * om de paar seconden gelezen (zonder de doorloop zelf aan te raken) en bij elke verandering opnieuw verstuurd; de uitvoer van een
 * programma hooguit eens per zes seconden.
 */
@Component
class FlowLogReporter(
    private val flow: TestRunService,
    private val exeRunner: ExeRunner,
    private val settingsService: SettingsService,
    private val items: ItemEditService,
    private val processed: ProcessedLog,
    private val client: AdminLogClient,
    private val mapper: ObjectMapper,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val sanitizer = SnapshotSanitizer(mapper)

    private class Session(val id: UUID = UUID.randomUUID(), val secret: String = UUID.randomUUID().toString() + UUID.randomUUID(), val startedAt: Instant = Instant.now()) {
        val consoles = linkedMapOf<String, ObjectNode>()
        val produced = mutableMapOf<String, List<ProducedFile>>()
        var summary: List<SummaryLine>? = null
        var core = ""
        var consolesJson = ""
        var lastSentMillis = 0L
        var phase = 0
        var review = false
        var finished = false
        val stepStatus = mutableMapOf<String, String>()
        var last: SessionReport? = null
        val recordedRuns = mutableSetOf<Long>()
        val numbers = mutableSetOf<String>()

        /** Wat er sinds de laatste melding gebeurde; blijft staan tot er echt een melding is verstuurd. */
        val pendingEvents = mutableListOf<String>()
    }

    private var session: Session? = null

    @Scheduled(initialDelay = 5_000, fixedDelay = 3_000)
    fun tick() {
        if (!client.enabled) return
        try {
            report()
        } catch (e: Exception) {
            log.debug("Melding aan het workflow-logboek overgeslagen: {}", e.message)
        }
    }

    @Synchronized
    private fun report() {
        val view = flow.flowView()
        var current = session
        if (view.current <= 1) {
            // De gebruiker is opnieuw begonnen (of heeft nog niets gedaan). Een doorloop die niet klaar was, krijgt nog één regel in de tijdlijn.
            current?.takeIf { !it.finished }?.let { endSession(it) }
            session = null
            return
        }
        if (current == null || current.finished && !view.finished) {
            current = Session()
            session = current
        }
        val s = settingsService.get()
        val cleaned = sanitizer.clean(s)
        recordConsole(current, cleaned)

        val run = view.run
        run?.rows?.forEach { current.numbers += it.number }
        val chosen = if (run == null) flow.pdfsInTestFolder().map { it.fileName.toString() } else emptyList()
        val fileCount = run?.fileCount ?: chosen.size
        val titles = view.phases.map { it.title }
        val phaseLabel = if (view.finished) "Klaar" else titles.getOrNull(view.current - 1).orEmpty()
        val stepViews = run?.rows?.firstOrNull()?.cells.orEmpty()
        val failed = stepViews.any { it.status == "FAILED" } && !view.finished
        val status = when { view.finished -> "FINISHED"; failed -> "FAILED"; else -> "RUNNING" }

        val events = current.pendingEvents
        if (current.phase == 0) events += "Gestart" + (if (fileCount > 0) ": $fileCount ${if (fileCount == 1) "bestand" else "bestanden"}" else "")
        else if (view.current != current.phase) events += if (view.finished) "Klaar" else "Fase ${view.current}: $phaseLabel"
        if (view.review != current.review && view.review) events += "Wacht op controle van de uitvoer (${phaseLabel})"
        stepViews.filter { it.type != StepType.RENAME.name }.forEach { step ->
            val before = current.stepStatus[step.type]
            if (before != null && before != step.status) {
                events += "${step.typeLabel}: ${step.statusLabel.lowercase()}" + (if (step.status == "FAILED") " – ${step.message.orEmpty().take(300)}" else "")
            }
            current.stepStatus[step.type] = step.status
        }
        current.phase = view.current
        current.review = view.review
        if (view.finished) {
            current.finished = true
            if (current.summary == null) current.summary = runCatching { flow.summary() }.getOrNull()
        }
        StepType.entries.filter { it.usesExe }.forEach { type ->
            val done = stepViews.firstOrNull { it.type == type.name }?.status == "DONE"
            if (done && type.name !in current.produced) current.produced[type.name] = runCatching { flow.producedFiles(type) }.getOrDefault(emptyList())
        }

        val core = mapper.createObjectNode()
        core.set("samenvatting", mapper.createObjectNode().apply {
            put("fase", view.current)
            put("faseNaam", phaseLabel)
            put("klaar", view.finished)
            put("wachtOpControle", view.review)
            put("startnummer", run?.startNumber.orEmpty())
            put("aantalBestanden", fileCount)
            put("gebruiker", items.user.orEmpty())
            put("invoermap", run?.testFolder ?: flow.inputFolder()?.toString().orEmpty())
            put("werkmap", run?.workDir.orEmpty())
            put("sessieGestart", current.startedAt.toString())
        })
        core.set("fases", mapper.createArrayNode().also { out ->
            view.phases.forEach { p -> out.add(mapper.createObjectNode().put("nr", p.nr).put("titel", p.title).put("status", p.state).put("notitie", p.note.orEmpty())) }
        })
        core.set("instellingen", cleaned.tree)
        if (run == null) core.set("gekozenBestanden", mapper.createArrayNode().also { out -> chosen.forEach { out.add(it) } })
        if (run != null) {
            core.set("stappen", mapper.createArrayNode().also { out ->
                val renamed = run.rows.count { it.cells.firstOrNull { c -> c.type == StepType.RENAME.name }?.status == "DONE" }
                out.add(mapper.createObjectNode().put("stap", StepType.RENAME.label).put("status", "$renamed van ${run.rows.size} klaar"))
                stepViews.filter { it.type != StepType.RENAME.name }.forEach { step ->
                    out.add(mapper.createObjectNode().put("stap", step.typeLabel).put("status", step.statusLabel).put("melding", step.message.orEmpty()))
                }
            })
            core.set("bestanden", mapper.createArrayNode().also { out ->
                run.rows.forEach { row ->
                    out.add(mapper.createObjectNode().apply {
                        put("origineleNaam", row.originalName)
                        put("nieuweNaam", row.newName)
                        put("nummer", row.number)
                        put("gescandDoor", row.by)
                        set("controles", mapper.createArrayNode().also { groups ->
                            row.checks.forEach { g ->
                                groups.add(mapper.createObjectNode().put("groep", g.label).also { n ->
                                    n.set("onderdelen", mapper.createArrayNode().also { arr -> g.items.forEach { arr.add("${it.state}: ${it.label} – ${it.path}") } })
                                })
                            }
                        })
                    })
                }
            })
        }
        if (current.produced.isNotEmpty()) core.set("gemaakteBestanden", mapper.valueToTree<JsonNode>(current.produced.mapKeys { (k, _) -> StepType.valueOf(k).label }))
        current.summary?.let { core.set("overzichtKlaar", mapper.valueToTree<JsonNode>(it)) }

        val consoles = mapper.createObjectNode().also { out -> current.consoles.forEach { (k, v) -> out.set(k, v) } }
        val coreJson = core.toString()
        val consolesJson = consoles.toString()
        val coreChanged = coreJson != current.core
        val consolesChanged = consolesJson != current.consolesJson
        if (!coreChanged && !consolesChanged) return
        val now = System.currentTimeMillis()
        val sinceLast = now - current.lastSentMillis
        val programFinished = current.consoles.values.any { it.path("loopt").asBoolean(true).not() } && consolesChanged
        if (coreChanged && sinceLast < MIN_MILLIS || !coreChanged && sinceLast < CONSOLE_MILLIS && !programFinished) return

        val snapshot = core.also { it.set("uitvoerProgrammas", consoles) }
        val report = SessionReport(
            id = current.id, kind = "NEW_FILES", secret = current.secret, user = items.user, collection = s.collection,
            phase = view.current, phaseCount = titles.size, phaseLabel = phaseLabel, status = status, fileCount = fileCount,
            event = events.joinToString(" · ").takeIf { it.isNotEmpty() }, snapshot = snapshot,
        )
        events.clear()
        current.core = coreJson
        current.consolesJson = consolesJson
        current.lastSentMillis = now
        current.last = report
        client.session(report)
    }

    /**
     * De doorloop is uit beeld verdwenen terwijl ze niet als klaar was gemeld. Staan haar nummers in het verwerkt-log, dan is ze afgerond
     * (de pagina Klaar was al weer weg voordat de stand werd gelezen); anders is de gebruiker opnieuw begonnen.
     */
    private fun endSession(ended: Session) {
        val last = ended.last ?: return
        val since = java.time.LocalDateTime.ofInstant(ended.startedAt, java.time.ZoneId.systemDefault()).minusSeconds(5)
        val done = if (ended.numbers.isEmpty()) emptyList() else processed.all().filter { it.number in ended.numbers && !it.at.isBefore(since) }
        if (done.isEmpty()) {
            client.session(last.copy(event = "Opnieuw begonnen bij stap 1; deze doorloop is niet afgemaakt"))
            return
        }
        val snapshot = last.snapshot.deepCopy()
        (snapshot.get("samenvatting") as? ObjectNode)?.apply { put("fase", 8); put("faseNaam", "Klaar"); put("klaar", true) }
        snapshot.set("verwerktLog", mapper.createArrayNode().also { out ->
            done.forEach { out.add(mapper.createObjectNode().put("nummer", it.number).put("bestand", it.name).put("map", it.folder).put("gescandDoor", it.by).put("gebruiker", it.user).put("tijd", it.at.toString())) }
        })
        client.session(last.copy(phase = last.phaseCount + 1, phaseLabel = "Klaar", status = "FINISHED", event = "Klaar: ${done.size} bestanden staan in het verwerkt-log", snapshot = snapshot))
    }

    /** Bewaart de uitvoer en het log van het programma dat nu (of als laatste) draaide, per programma, zodat het later nog te lezen is. */
    private fun recordConsole(current: Session, cleaned: SnapshotSanitizer.Cleaned) {
        finishUnseen(current, cleaned, exeRunner.snapshot()?.takeIf { it.running }?.id)
        val snap = exeRunner.snapshot() ?: return
        if (snap.startedAt.isBefore(current.startedAt.minusSeconds(2))) return
        // Een afgerond programma verandert niet meer: één keer vastleggen is genoeg.
        if (!snap.running && snap.id in current.recordedRuns) return
        if (!snap.running) current.recordedRuns += snap.id
        val key = snap.label.substringBefore(" – ").ifBlank { snap.label }
        val node = mapper.createObjectNode().apply {
            put("omschrijving", snap.label)
            put("runId", snap.id)
            put("logbestand", java.nio.file.Path.of(snap.logFile).fileName.toString())
            put("opdracht", cleaned.scrub(snap.command))
            put("uitvoeren", snap.mode.label)
            put("automatisch", snap.auto)
            put("automatischBeantwoord", snap.autoAnswered)
            put("gestart", snap.startedAt.toString())
            put("klaar", snap.finishedAt?.toString().orEmpty())
            put("loopt", snap.running)
            snap.exitCode?.let { put("exitcode", it) }
            put("uitvoer", cleaned.scrub(snap.output).takeLast(OUTPUT_CHARS))
            put("log", cleaned.scrub(exeRunner.logText().orEmpty()).takeLast(OUTPUT_CHARS))
        }
        current.consoles[key] = node
    }

    /**
     * Een programma dat als lopend is vastgelegd maar intussen klaar is zonder dat het eindigen is gezien (het volgende programma
     * was al gestart): de uitkomst komt dan uit het logbestand van die run.
     */
    private fun finishUnseen(current: Session, cleaned: SnapshotSanitizer.Cleaned, runningId: Long?) {
        current.consoles.values.filter { it.path("loopt").asBoolean(false) && it.path("runId").asLong(-1) != runningId }.forEach { node ->
            val text = exeRunner.logText(node.path("logbestand").asString("")) ?: return@forEach
            val exit = Regex("""EXIT code=(-?\d+)""").find(text)?.groupValues?.get(1)?.toIntOrNull()
            node.put("loopt", false)
            exit?.let { node.put("exitcode", it) }
            node.put("klaar", "(tijdstip niet gezien, zie het log)")
            node.put("log", cleaned.scrub(text).takeLast(OUTPUT_CHARS))
        }
    }

    private companion object {
        const val MIN_MILLIS = 2_500L
        const val CONSOLE_MILLIS = 6_000L
        const val OUTPUT_CHARS = 150_000
    }
}
