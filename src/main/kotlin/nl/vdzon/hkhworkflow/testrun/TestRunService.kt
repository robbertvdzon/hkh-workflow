package nl.vdzon.hkhworkflow.testrun

import java.nio.file.Path
import java.time.Duration
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

/** Wie een collectie nu in gebruik heeft voor "Nieuwe bestanden", sinds wanneer en hoe lang het al stil is. */
data class FlowLock(val collection: String, val owner: String, val since: Instant, val lastActive: Instant, val phase: Int)

/** Een andere gebruiker heeft de collectie in gebruik: er is hooguit één doorloop per collectie. */
class FlowLockedException(val lock: FlowLock) : IllegalStateException(
    "In ${lock.collection} werkt ${lock.owner.ifEmpty { "de beheerder" }} al" +
        " (sinds ${java.time.LocalTime.ofInstant(lock.since, java.time.ZoneId.systemDefault()).withNano(0).toString().take(5)}," +
        " laatst actief ${java.time.LocalTime.ofInstant(lock.lastActive, java.time.ZoneId.systemDefault()).toString().take(5)}). " +
        "Wacht tot die klaar is, of vraag de beheerder om de collectie vrij te geven.",
)

/**
 * De doorlopen van "Nieuwe bestanden": per collectie hooguit één, van de gebruiker die ermee begon ([FlowSession]); twee gebruikers kunnen wel
 * tegelijk in twee verschillende collecties werken. Elke aanroep gaat naar de doorloop van de gebruiker en collectie die nu aan het woord zijn
 * ([Current]). Een doorloop die een tijd niets meer heeft gedaan (standaard 2 uur) komt vrij voor een ander; de beheerder kan hem ook vrijgeven.
 * Heeft iemand anders de collectie in gebruik, dan geven de aanroepen een [FlowLockedException].
 */
@Service
class TestRunService(
    private val settingsService: SettingsService,
    private val ftpService: FtpService,
    private val itemEditService: ItemEditService,
    private val processed: ProcessedLog,
    @Value("\${hkh.workflow.console-charset:IBM850}") private val charsetName: String,
    @Value("\${hkh.workflow.log-dir:logs}") private val logDir: String,
    @Value("\${hkh.workflow.session-timeout-minutes:120}") timeoutMinutes: Long,
    private val mapper: ObjectMapper,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val timeout: Duration = Duration.ofMinutes(timeoutMinutes)
    private val flows = LinkedHashMap<String, FlowSession>()

    private fun create(user: String, collection: String): FlowSession {
        val folders = WorkFolders(settingsService, user, collection)
        val runner = ExeRunner(charsetName, Path.of(logDir, collection, WorkFolders.folderName(user)).toString())
        return FlowSession(settingsService, runner, ftpService, itemEditService, SourceFilesService(settingsService, processed, folders), processed, folders, collection)
    }

    private fun expired(f: FlowSession, now: Instant = Instant.now()) = f.claimed && Duration.between(f.lastActivity, now) > timeout

    private fun lockOf(f: FlowSession) = FlowLock(f.collection, f.owner, f.claimedSince ?: f.lastActivity, f.lastActivity, f.flowViewQuietly())

    /** De doorloop van de gebruiker die nu aan het woord is, in zijn collectie. Gooit [FlowLockedException] als een ander de collectie heeft. */
    fun current(): FlowSession = synchronized(flows) {
        val user = Current.user().orEmpty()
        val collection = Current.collection()
        val existing = flows[collection]
        val flow = when {
            existing == null -> create(user, collection).also { flows[collection] = it }
            existing.owner.equals(user, ignoreCase = true) -> existing
            !existing.claimed || expired(existing) -> {
                // Een ander die niets gestart heeft, of te lang stil is: de collectie komt vrij voor deze gebruiker.
                if (existing.claimed) existing.release()
                create(user, collection).also { flows[collection] = it }
            }
            else -> throw FlowLockedException(lockOf(existing))
        }
        flow.touch()
        flow
    }

    /** Wie de collectie van de gebruiker die nu aan het woord is, in gebruik heeft, als dat een ander is; anders null. */
    fun lockedBy(collection: String = Current.collection()): FlowLock? = synchronized(flows) {
        val existing = flows[collection] ?: return null
        if (existing.owner.equals(Current.user().orEmpty(), ignoreCase = true) || !existing.claimed || expired(existing)) null else lockOf(existing)
    }

    /** Alle doorlopen die er nu zijn (ook zonder iets gestart te hebben), met hun eigenaar. */
    fun sessions(): List<FlowSession> = synchronized(flows) { flows.values.toList() }

    /** De doorlopen die een gebruiker in gebruik heeft, voor de beheerder. */
    fun locks(): List<FlowLock> = synchronized(flows) { flows.values.filter { it.claimed }.map { lockOf(it) } }

    /** Geeft een collectie vrij (de beheerder): de doorloop wordt gestopt en de gebruiker moet opnieuw beginnen. */
    fun release(collection: String): Boolean = synchronized(flows) { flows.remove(collection)?.also { it.release() } != null }

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
                            if (state.collection !in flows) {
                                val flow = create(state.owner, state.collection)
                                flow.restore(state)
                                flows[state.collection] = flow
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
