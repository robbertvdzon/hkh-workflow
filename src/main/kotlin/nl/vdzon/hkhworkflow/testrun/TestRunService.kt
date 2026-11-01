package nl.vdzon.hkhworkflow.testrun

import java.nio.file.Path
import java.time.Instant
import nl.vdzon.hkhworkflow.exerunner.ExeRunner
import nl.vdzon.hkhworkflow.exerunner.RunMode
import nl.vdzon.hkhworkflow.ftp.FtpService
import nl.vdzon.hkhworkflow.items.Current
import nl.vdzon.hkhworkflow.items.ItemEditService
import nl.vdzon.hkhworkflow.settings.SettingsService
import org.springframework.beans.factory.annotation.Value
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import tools.jackson.databind.ObjectMapper

/** Een doorloop die nu loopt, voor het overzicht van de beheerder. */
data class ActiveFlow(val collection: String, val owner: String, val since: Instant, val lastActive: Instant, val phase: Int)

/**
 * De doorlopen van "Nieuwe bestanden": één per gebruiker en collectie ([FlowSession]), elk in zijn eigen mappen. Er is geen slot per collectie:
 * meer gebruikers kunnen tegelijk in dezelfde collectie werken. Het enige dat ze kunnen delen zijn de recordnummers; daarom controleert de
 * doorloop vlak voor images.exe nog eens of de nummers vrij zijn in ZCBS. Elke aanroep gaat naar de doorloop van de gebruiker en collectie die
 * nu aan het woord zijn ([Current]).
 */
@Service
class TestRunService(
    private val settingsService: SettingsService,
    private val ftpService: FtpService,
    private val itemEditService: ItemEditService,
    private val processed: ProcessedLog,
    @Value("\${hkh.workflow.console-charset:IBM850}") private val charsetName: String,
    @Value("\${hkh.workflow.log-dir:logs}") private val logDir: String,
    private val mapper: ObjectMapper,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val flows = LinkedHashMap<String, FlowSession>()

    private fun key(user: String, collection: String) = "${user.trim().lowercase()}/$collection"

    private fun create(user: String, collection: String): FlowSession {
        val folders = WorkFolders(settingsService, user, collection)
        val runner = ExeRunner(charsetName, Path.of(logDir, collection, WorkFolders.folderName(user)).toString())
        return FlowSession(settingsService, runner, ftpService, itemEditService, SourceFilesService(settingsService, processed, folders), processed, folders, collection)
    }

    /** De doorloop van de gebruiker die nu aan het woord is, in zijn collectie; een nieuwe als hij er nog geen heeft. */
    fun current(): FlowSession = synchronized(flows) {
        val user = Current.user().orEmpty()
        val collection = Current.collection()
        flows.getOrPut(key(user, collection)) { create(user, collection) }.also { it.touch() }
    }

    /** Alle doorlopen die er nu zijn (ook zonder iets gestart te hebben), met hun eigenaar. */
    fun sessions(): List<FlowSession> = synchronized(flows) { flows.values.toList() }

    /** De doorlopen waar iemand mee bezig is, voor de beheerder. */
    fun active(): List<ActiveFlow> = synchronized(flows) {
        flows.values.filter { it.claimed }.map { ActiveFlow(it.collection, it.owner, it.claimedSince ?: it.lastActivity, it.lastActivity, it.flowViewQuietly()) }
    }

    /** Stopt de doorloop van [user] in [collection] (de beheerder): wat loopt wordt gestopt en de gebruiker moet opnieuw beginnen. */
    fun stop(user: String, collection: String): Boolean = synchronized(flows) { flows.remove(key(user, collection))?.also { it.release() } != null }

    private val lastSaved = java.util.IdentityHashMap<FlowSession, String>()

    /** Bewaart de stand van elke doorloop in zijn eigen map, zodat hij een herstart overleeft; een doorloop zonder stand krijgt zijn bestand weg. */
    @Scheduled(initialDelay = 5_000, fixedDelay = 5_000)
    fun saveStates() {
        for (f in sessions()) {
            try {
                val file = f.stateFile() ?: continue
                val state = f.state()
                if (state == null) {
                    if (lastSaved.remove(f) != null || java.nio.file.Files.exists(file)) java.nio.file.Files.deleteIfExists(file)
                    continue
                }
                val json = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(state)
                if (lastSaved[f] == json) continue
                java.nio.file.Files.createDirectories(file.parent)
                val tmp = file.resolveSibling("sessie.json.tmp")
                java.nio.file.Files.writeString(tmp, json)
                java.nio.file.Files.move(tmp, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
                lastSaved[f] = json
            } catch (e: Exception) {
                log.warn("Bewaren van de stand van een doorloop mislukt: {}", e.message)
            }
        }
    }

    /** Leest bij het opstarten de bewaarde doorlopen terug uit de werkmap (`<werkmap>/<gebruiker>/<collectie>/sessie.json`). */
    @jakarta.annotation.PostConstruct
    fun restore() {
        val base = settingsService.get().testFolder.trim().takeIf { it.isNotEmpty() }?.let { Path.of(it) }?.takeIf { java.nio.file.Files.isDirectory(it) } ?: return
        runCatching {
            java.nio.file.Files.list(base).use { users -> users.toList() }.filter { java.nio.file.Files.isDirectory(it) }.forEach { userDir ->
                java.nio.file.Files.list(userDir).use { it.toList() }.forEach { collectionDir ->
                    val file = collectionDir.resolve("sessie.json")
                    if (!java.nio.file.Files.isRegularFile(file)) return@forEach
                    runCatching {
                        val state = mapper.readValue(java.nio.file.Files.readString(file), FlowState::class.java)
                        synchronized(flows) {
                            if (key(state.owner, state.collection) !in flows) {
                                val flow = create(state.owner, state.collection)
                                flow.restore(state)
                                flows[key(state.owner, state.collection)] = flow
                                log.info("Doorloop van {} in {} teruggezet (fase {})", state.owner, state.collection, flow.flowViewQuietly())
                            }
                        }
                    }.onFailure { log.warn("De bewaarde doorloop in {} is onleesbaar: {}", file, it.message) }
                }
            }
        }.onFailure { log.warn("Bewaarde doorlopen terugzetten mislukt: {}", it.message) }
    }

    fun exe(): ExeRunner = current().exeRunner
    fun sources(): SourceFilesService = current().sourceFiles

    fun acceptInput() = current().acceptInput()
    fun inputFolder(): Path? = current().inputFolder()
    fun confirmFiles(): String? = current().confirmFiles()
    fun filesInTestFolder(): List<Path> = current().filesInTestFolder()
    fun start(startNumber: String) = current().start(startNumber)
    fun reset() = current().reset()
    fun pageState(): Pair<FlowView, List<SummaryLine>?> = current().pageState()
    fun summary(): List<SummaryLine> = current().summary()
    fun prepare(startNumber: String): String? = current().prepare(startNumber)
    fun confirmReview(type: StepType): String? = current().confirmReview(type)
    fun flowView(): FlowView = current().flowView()
    fun environment(): List<EnvCheck> = current().environment()
    fun startFlow(): String? = current().startFlow()
    fun leftovers(): List<Leftover> = current().leftovers()
    fun leftoversTarget(): Path? = current().leftoversTarget()
    fun clearLeftovers(move: Boolean): String = current().clearLeftovers(move)
    fun execute(stepId: Int, mode: RunMode, auto: Boolean = false) = current().execute(stepId, mode, auto)
    fun skip(stepId: Int) = current().skip(stepId)
    fun markDone(stepId: Int) = current().markDone(stepId)
    fun retry(stepId: Int) = current().retry(stepId)
    fun view(): TestRunView? = current().view()
    fun resultFile(kind: String, number: String): Path? = current().resultFile(kind, number)
    fun producedFiles(type: StepType): List<ProducedFile> = current().producedFiles(type)
}
