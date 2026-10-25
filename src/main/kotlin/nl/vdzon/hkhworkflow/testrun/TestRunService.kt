package nl.vdzon.hkhworkflow.testrun

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlin.io.path.extension
import kotlin.io.path.isRegularFile
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import nl.vdzon.hkhworkflow.exerunner.AutoAnswer
import nl.vdzon.hkhworkflow.exerunner.ExeRunner
import nl.vdzon.hkhworkflow.exerunner.RunMode
import nl.vdzon.hkhworkflow.items.BatchRecord
import nl.vdzon.hkhworkflow.items.EditStatus
import nl.vdzon.hkhworkflow.items.ItemEditService
import nl.vdzon.hkhworkflow.settings.SettingsService
import nl.vdzon.hkhworkflow.settings.WorkflowSettings
import nl.vdzon.hkhworkflow.sftp.SftpService
import nl.vdzon.hkhworkflow.sftp.UploadItem
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

/**
 * De testworkflow, die Carla's handwerk volgt maar elke stap pas laat draaien nadat de gebruiker hem heeft
 * goedgekeurd:
 *
 * 1. hernoemen (per bestand): kopie met het nieuwe nummer in `werk`;
 * 2. pdf2zcbs.exe (één keer): maakt tekst, pdf, small en large;
 * 3. images.exe (één keer): krijgt de large-jpg's van pdf2zcbs en maakt small, large en 100pct;
 * 4. upload (één keer): tekst en pdf's met sftp naar de server;
 * 5. records bewerken (één keer): de records van de batch één voor één invullen via het ZCBS-formulier (pagina Records);
 * 6. opruimen (één keer): back-up van de 100pct, daarna de mappen van de programma's leegmaken;
 * 7. archiveren (per bestand): het origineel naar de archived-map.
 *
 * Vóór de run haalt [SourceFilesService] de bestanden die Ria heeft aangegeven op van de server.
 *
 * Zo lang er niets wordt opgeruimd of gearchiveerd, staat alles op schijf zoals het bij de handmatige werkwijze
 * ook zou staan, zodat een stap die misgaat op de oude manier kan worden afgemaakt. Een stap is pas "klaar" als
 * de verwachte bestanden er echt zijn; de exitcode alleen is niet genoeg.
 */
@Service
class TestRunService(
    private val settingsService: SettingsService,
    private val exeRunner: ExeRunner,
    private val sftpService: SftpService,
    private val itemEditService: ItemEditService,
    private val sourceFiles: SourceFilesService,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Volatile
    private var run: TestRun? = null

    /** Heeft de gebruiker de lijst met pdf's in de invoermap bevestigd (fase Bestanden ophalen)? */
    @Volatile
    private var filesConfirmed: Boolean = false

    @Synchronized
    fun confirmFiles() { filesConfirmed = true }

    @Synchronized
    fun reopenFiles() { filesConfirmed = false }

    /** Pdf's die nu in de testmap staan (alleen de map zelf, geen submappen). */
    fun pdfsInTestFolder(): List<Path> {
        val folder = settingsService.get().testFolder
        if (folder.isBlank()) return emptyList()
        val dir = Path.of(folder)
        if (!Files.isDirectory(dir)) return emptyList()
        return dir.listDirectoryEntries()
            .filter { it.isRegularFile() && it.extension.equals("pdf", ignoreCase = true) }
            .sortedBy { it.name.lowercase() }
    }

    @Synchronized
    fun start(startNumber: String) {
        stopPrevious()
        val settings = settingsService.get()
        require(settings.testFolder.isNotBlank()) { "Stel eerst de testmap in bij Instellingen." }
        val testFolder = Path.of(settings.testFolder)
        require(Files.isDirectory(testFolder)) { "De testmap bestaat niet: $testFolder" }
        val pdfs = pdfsInTestFolder()
        require(pdfs.isNotEmpty()) { "Er staan geen pdf's in de testmap." }

        val numbers = sequenceNumbers(startNumber, pdfs.size)
        val files = pdfs.mapIndexed { i, path ->
            val origin = sourceFiles.origin(path.name)
            TestFile(i, path, numbers[i], origin?.by.orEmpty(), origin?.folder.orEmpty())
        }
        var id = 0
        val steps = StepType.entries.flatMap { type ->
            if (type.perFile) files.map { Step(++id, type, listOf(it)) } else listOf(Step(++id, type, files))
        }
        run = TestRun(startNumber.trim(), testFolder, testFolder.resolve("werk"), files, steps)
        log.info("Testrun gestart: {} bestanden vanaf {}", files.size, startNumber)
    }

    @Synchronized
    fun reset() {
        stopPrevious()
        run = null
        filesConfirmed = false
    }

    /**
     * Fase Voorbereiden: controleert dat de nummers nog niet in ZCBS bestaan, start de run en maakt van elk bestand een
     * kopie met het nummer in `werk` (het origineel blijft staan).
     * @return een melding als het niet kan, anders null.
     */
    @Synchronized
    fun prepare(startNumber: String): String? {
        val pdfs = pdfsInTestFolder()
        if (pdfs.isEmpty()) return "Er staan geen pdf's in de testmap."
        val numbers = try { sequenceNumbers(startNumber, pdfs.size) } catch (e: Exception) { return "Geef een beginnummer van alleen cijfers op." }
        itemEditService.checkNumbersFree(numbers)?.let { return it }
        return try {
            start(startNumber)
            while (true) {
                val next = run!!.nextStep() ?: break
                if (next.type != StepType.RENAME) break
                execute(next.id, RunMode.IN_APP)
                if (next.status != StepStatus.DONE) return next.message ?: "Hernoemen is mislukt."
            }
            null
        } catch (e: Exception) {
            e.message ?: e.javaClass.simpleName
        }
    }

    /** Fase Afsluiten: opruimen en archiveren, na één goedkeuring. Stopt bij de eerste stap die niet lukt. @return een melding of null. */
    @Synchronized
    fun finish(): String? {
        val current = run ?: return "Er is geen batch gestart."
        while (true) {
            val next = current.nextStep() ?: return null
            if (next.type != StepType.CLEANUP && next.type != StepType.ARCHIVE) return "Er is nog een stap voor het afsluiten: ${next.type.label}."
            execute(next.id, RunMode.IN_APP)
            if (next.status != StepStatus.DONE) return next.message ?: "${next.type.label} is mislukt."
        }
    }

    /** De batch in acht fasen, zoals de gebruiker hem ziet. */
    @Synchronized
    fun batchView(): BatchView {
        val v = view()
        val loggedIn = itemEditService.loggedIn()
        val nextType = v?.next?.type
        val current = when {
            !loggedIn -> 1
            v == null && !filesConfirmed -> 2
            v == null -> 3
            nextType == null -> 9
            nextType == "RENAME" -> 3
            nextType == "PDF2ZCBS" -> 4
            nextType == "IMAGES" -> 5
            nextType == "UPLOAD" -> 6
            nextType == "EDIT" -> 7
            else -> 8
        }
        val titles = listOf("Inloggen", "Bestanden ophalen", "Voorbereiden", "pdf2zcbs", "images.exe", "Upload ocr en pdf", "Bewerken", "Afsluiten")
        val phases = titles.mapIndexed { i, title ->
            val nr = i + 1
            val note = if (nr == 7 && itemEditService.records().isNotEmpty()) "${itemEditService.handledCount()}/${itemEditService.records().size}" + (itemEditService.laterCount().takeIf { it > 0 }?.let { " · $it later" } ?: "") else null
            PhaseView(nr, title, when { nr < current -> "done"; nr == current -> "now"; else -> "todo" }, note)
        }
        return BatchView(phases, current, current == 9, v)
    }

    /** Een nieuwe test begint schoon: een nog lopend programma wordt gestopt en de console leeggemaakt. */
    private fun stopPrevious() {
        check(exeRunner.stopAndWait()) { "Het lopende programma wil niet stoppen; probeer het nog eens." }
        exeRunner.clear()
        log.info("Vorige testrun opgeruimd")
    }

    @Synchronized
    fun execute(stepId: Int, mode: RunMode, auto: Boolean = false, options: StepOptions = StepOptions()) {
        val current = run ?: error("Er is geen testrun gestart.")
        syncEdit(current)
        val step = current.steps.firstOrNull { it.id == stepId } ?: error("Onbekende stap $stepId.")
        check(current.nextStep()?.id == step.id) { "Deze stap is nog niet aan de beurt." }
        check(step.status != StepStatus.RUNNING) { "Deze stap is al bezig." }
        check(!exeRunner.isRunning()) { "Er draait nog een programma." }
        step.message = null
        try {
            val settings = settingsService.get()
            val problems = problems(current, step, settings)
            check(problems.isEmpty()) { problems.joinToString("\n") }
            when (step.type) {
                StepType.RENAME -> rename(current, step)
                StepType.PDF2ZCBS -> runPdf2zcbs(current, step, settings, mode, auto)
                StepType.IMAGES -> runImages(step, settings, mode, auto, options)
                StepType.UPLOAD -> upload(step, settings)
                StepType.EDIT -> edit(current, step)
                StepType.CLEANUP -> cleanup(step, settings)
                StepType.ARCHIVE -> archive(step, settings)
            }
        } catch (e: Exception) {
            step.status = StepStatus.FAILED
            step.message = e.message ?: e.javaClass.simpleName
            log.warn("Stap {} ({}) mislukt: {}", step.id, step.type, step.message)
        }
    }

    @Synchronized
    fun skip(stepId: Int) = update(stepId) { it.status = StepStatus.SKIPPED }

    @Synchronized
    fun markDone(stepId: Int) = update(stepId) { it.status = StepStatus.DONE; it.message = "Handmatig op klaar gezet." }

    @Synchronized
    fun retry(stepId: Int) = update(stepId) { it.status = StepStatus.PENDING; it.message = null }

    private fun update(stepId: Int, change: (Step) -> Unit) {
        val step = run?.steps?.firstOrNull { it.id == stepId } ?: error("Onbekende stap $stepId.")
        // Het bewerken van de records is geen programma: de gebruiker mag het zelf afsluiten.
        check(step.status != StepStatus.RUNNING || step.type == StepType.EDIT) { "Deze stap is nog bezig." }
        change(step)
    }

    /** De stap Records bewerken is klaar zodra elk record is opgeslagen of op "later" staat. */
    private fun syncEdit(current: TestRun) {
        val step = current.steps.firstOrNull { it.type == StepType.EDIT } ?: return
        if (step.status == StepStatus.RUNNING && itemEditService.allHandled()) {
            step.status = StepStatus.DONE
            step.message = "${itemEditService.handledCount()} records opgeslagen" + (itemEditService.laterCount().takeIf { it > 0 }?.let { ", $it op later" } ?: "") + "."
        }
    }

    // ---- Locaties van alle bestanden die de stappen aanmaken of nodig hebben ----

    private class Layout(val s: WorkflowSettings) {
        val p2z: Path? = s.pdf2zcbsExe.trim().takeIf { it.isNotEmpty() }?.let { Path.of(it).toAbsolutePath().parent }
        val img: Path? = s.imagesExe.trim().takeIf { it.isNotEmpty() }?.let { Path.of(it).toAbsolutePath().parent }
        val backup: Path? = s.backup100pctFolder.trim().takeIf { it.isNotEmpty() }?.let { Path.of(it) }

        fun p2zOutput(): Path? = p2z?.resolve("output")
        fun ocr(f: TestFile): Path? = p2zOutput()?.resolve("ocr")?.resolve("${f.number}.txt")
        fun pdf(f: TestFile): Path? = p2zOutput()?.resolve("pdf")?.resolve("${f.number}.pdf")
        fun p2zSmall(f: TestFile): Path? = p2zOutput()?.resolve("small")?.resolve("${f.number}.jpg")
        fun p2zLarge(f: TestFile): Path? = p2zOutput()?.resolve("large")?.resolve("${f.number}.jpg")
        fun imgIn(f: TestFile): Path? = img?.resolve("images")?.resolve("${f.number}.jpg")
        fun imgSmall(f: TestFile): Path? = img?.resolve("small")?.resolve("${f.number}.jpg")
        fun imgLarge(f: TestFile): Path? = img?.resolve("large")?.resolve("${f.number}.jpg")
        fun img100(f: TestFile): Path? = img?.resolve("100pct")?.resolve("${f.number}.jpg")
        fun backupOf(f: TestFile): Path? = backup?.resolve("${f.number}.jpg")
    }

    private fun Path?.present(): Boolean = this != null && Files.isRegularFile(this) && runCatching { Files.size(this) > 0 }.getOrDefault(false)

    private fun names(list: List<String>, max: Int = 8): String =
        list.take(max).joinToString() + if (list.size > max) " en ${list.size - max} andere" else ""

    // ---- Controles vooraf: wat houdt een stap nu tegen? ----

    private fun problems(current: TestRun, step: Step, s: WorkflowSettings): List<String> {
        val l = Layout(s)
        val batch = step.files.map { it.number }.toSet()
        return when (step.type) {
            StepType.RENAME -> buildList {
                val f = step.file
                if (!Files.exists(f.original)) add("Origineel niet gevonden: ${f.original}")
                if (Files.exists(current.workDir.resolve(f.newName))) add("${f.newName} bestaat al in ${current.workDir}.")
            }

            StepType.PDF2ZCBS -> buildList {
                exeProblem("pdf2zcbs.exe", s.pdf2zcbsExe)?.let { add(it) }
                step.files.forEach { if (!Files.exists(current.workDir.resolve(it.newName))) add("Werkbestand ontbreekt: ${current.workDir.resolve(it.newName)} (is de hernoem-stap gedaan?)") }
                val dir = l.p2z ?: return@buildList
                if (Files.isDirectory(dir)) {
                    val strangers = dir.listDirectoryEntries().filter { it.isRegularFile() && it.extension.equals("pdf", true) && it.nameWithoutExt() !in batch }.map { it.name }.sorted()
                    if (strangers.isNotEmpty()) add("In $dir staan nog andere pdf's die pdf2zcbs.exe ook zou verwerken: ${names(strangers)}. Verwijder die eerst.")
                    val out = dir.resolve("output")
                    if (Files.isDirectory(out)) {
                        val old = Files.walk(out).use { w ->
                            w.filter { Files.isRegularFile(it) }
                                .filter { out.relativize(it).getName(0).toString() != "log" && it.name != "updates.txt" }
                                .filter { it.extension.lowercase() in setOf("txt", "pdf", "jpg", "jpeg") && it.nameWithoutExt() !in batch }
                                .map { out.relativize(it).toString() }.sorted().toList()
                        }
                        if (old.isNotEmpty()) add("In $out staan resultaten van een vorige run: ${names(old)}. pdf2zcbs.exe wist die map bij de start. Zet ze eerst veilig of verwijder de map.")
                    }
                }
            }

            StepType.IMAGES -> buildList {
                exeProblem("images.exe", s.imagesExe)?.let { add(it) }
                step.files.forEach { if (!l.p2zLarge(it).present()) add("Large-jpg van pdf2zcbs ontbreekt: ${l.p2zLarge(it) ?: "(pdf2zcbs.exe niet ingesteld)"} (is de pdf2zcbs-stap gedaan?)") }
                val dir = l.img ?: return@buildList
                val ours = batch.map { "$it.jpg" }.toSet()
                val input = dir.resolve("images")
                if (Files.isDirectory(input)) {
                    val strangers = input.listDirectoryEntries().filter { it.isRegularFile() && it.name !in ours }.map { it.name }.sorted()
                    if (strangers.isNotEmpty()) add("In $input staan nog andere bestanden die images.exe ook zou verwerken: ${names(strangers)}. Verwijder die eerst.")
                }
                listOf("small", "large", "100pct").forEach { sub ->
                    val derived = dir.resolve(sub)
                    if (Files.isDirectory(derived)) {
                        val old = derived.listDirectoryEntries().filter { it.isRegularFile() && it.name !in ours }.map { it.name }.sorted()
                        if (old.isNotEmpty()) add("In $derived staan resultaten van een vorige run: ${names(old)}. Die zouden opnieuw worden geüpload. Ruim eerst op.")
                    }
                }
            }

            StepType.UPLOAD -> buildList {
                if (s.sftpHost.isBlank()) add("Sftp-host is niet ingesteld (Instellingen).")
                if (s.sftpUser.isBlank()) add("Sftp-gebruiker is niet ingesteld (Instellingen).")
                if (s.sftpPassword.isEmpty()) add("Sftp-wachtwoord is niet ingesteld (Instellingen).")
                if (s.sftpHostKey.isBlank()) add("De vingerafdruk van de server is nog niet vastgezet (Instellingen, Test verbinding).")
                if (s.collection.isBlank()) add("Collectie is niet ingesteld (Instellingen).")
                step.files.forEach {
                    if (!l.ocr(it).present()) add("Tekstbestand ontbreekt: ${l.ocr(it) ?: "(pdf2zcbs.exe niet ingesteld)"}")
                    if (!l.pdf(it).present()) add("Pdf ontbreekt: ${l.pdf(it) ?: "(pdf2zcbs.exe niet ingesteld)"}")
                }
            }

            StepType.EDIT -> buildList {
                if (s.zcbsBaseUrl.isBlank()) add("Het adres van ZCBS is niet ingesteld (Instellingen).")
                if (s.collection.isBlank()) add("Collectie is niet ingesteld (Instellingen).")
                if (!itemEditService.loggedIn()) add("Log eerst in bij ZCBS met je eigen naam en wachtwoord.")
            }

            StepType.CLEANUP -> buildList {
                if (l.p2z == null || l.img == null) add("Stel eerst de paden van pdf2zcbs.exe en images.exe in.")
                if (s.backup100pctFolder.isBlank()) add("Stel eerst de back-upmap voor de 100pct in (Instellingen).")
                else if (!Files.isDirectory(Path.of(s.backup100pctFolder))) add("Back-upmap bestaat niet: ${s.backup100pctFolder}")
                step.files.forEach { if (!l.img100(it).present()) add("100pct ontbreekt, dus geen back-up mogelijk: ${l.img100(it) ?: "(images.exe niet ingesteld)"}") }
            }

            StepType.ARCHIVE -> buildList {
                if (s.archivedFolder.isBlank()) add("Stel eerst de archived-map in bij Instellingen.")
                if (!Files.exists(step.file.original)) add("Origineel niet gevonden: ${step.file.original}")
            }
        }
    }

    private fun exeProblem(label: String, setting: String): String? = when {
        setting.isBlank() -> "Stel eerst het pad naar $label in."
        !Files.isRegularFile(Path.of(setting)) -> "$label niet gevonden: $setting"
        else -> null
    }

    private fun Path.nameWithoutExt(): String = name.substringBeforeLast('.')

    // ---- De stappen ----

    private fun rename(current: TestRun, step: Step) {
        Files.createDirectories(current.workDir)
        val target = current.workDir.resolve(step.file.newName)
        Files.copy(step.file.original, target)
        step.status = StepStatus.DONE
        step.message = "Kopie gemaakt: werk\\${step.file.newName}"
    }

    private fun runPdf2zcbs(current: TestRun, step: Step, s: WorkflowSettings, mode: RunMode, auto: Boolean) {
        val l = Layout(s)
        val dir = l.p2z!!
        val copies = step.files.map { current.workDir.resolve(it.newName) to dir.resolve(it.newName) }
        val expected = step.files.flatMap {
            listOf("tekst" to l.ocr(it)!!, "pdf" to l.pdf(it)!!, "small" to l.p2zSmall(it)!!, "large" to l.p2zLarge(it)!!)
        }
        val answers = if (auto) s.pdf2zcbsAnswers.map { AutoAnswer(it.pattern, it.answer, it.secret) } else null
        runExe(step, s, Path.of(s.pdf2zcbsExe), dir, copies, expected, mode, answers)
    }

    private fun runImages(step: Step, s: WorkflowSettings, mode: RunMode, auto: Boolean, options: StepOptions) {
        check(!options.records || options.upload) { "Lege records aanmaken kan alleen samen met uploaden." }
        if (auto && options.upload) {
            check(s.imagesAnswers.any { it.pattern.contains("FTP wachtwoord", ignoreCase = true) && it.answer.isNotEmpty() }) {
                "Voor uploaden met automatisch uitvoeren moet bij Instellingen het FTP-wachtwoord staan (regel 'FTP wachtwoord'). Gebruik anders Handmatig uitvoeren."
            }
        }
        val l = Layout(s)
        val dir = l.img!!
        val copies = step.files.map { l.p2zLarge(it)!! to l.imgIn(it)!! }
        val expected = step.files.flatMap { listOf("small" to l.imgSmall(it)!!, "large" to l.imgLarge(it)!!, "100pct" to l.img100(it)!!) }
        val answers = if (auto) imagesRules(s, options) + s.imagesAnswers.map { AutoAnswer(it.pattern, it.answer, it.secret) } else null
        runExe(step, s, Path.of(s.imagesExe), dir, copies, expected, mode, answers)
    }

    /**
     * Antwoorden die per run of per collectie verschillen. Ze staan vóór de algemene antwoorden uit Instellingen en
     * gaan dus voor. Overschrijven en het verwijderen van de mappen is altijd nee.
     */
    private fun imagesRules(s: WorkflowSettings, options: StepOptions): List<AutoAnswer> = listOf(
        AutoAnswer("Type ENTER/RETURN indien OK", s.imagesFileType),
        AutoAnswer("Kies 1,2,3", s.imagesResolution),
        AutoAnswer("UPLOADEN aangemaakte images", if (options.upload) "j" else "n"),
        AutoAnswer("Nummer, naam of plaats instelling", s.imagesInstitution),
        AutoAnswer("Kies de ZCBS-toepassing", s.collection),
        AutoAnswer("OVERSCHRIJVEN", "n"),
        AutoAnswer("AANMAKEN en UPLOADEN van de bijbehorende", if (options.records) "j" else "n"),
        AutoAnswer("VERWIJDEREN (tijdelijk) aangemaakte mappen", "n"),
        AutoAnswer("VERWIJDEREN inhoud van de map [images]", "n"),
    )

    private fun runExe(
        step: Step,
        s: WorkflowSettings,
        exe: Path,
        exeDir: Path,
        copies: List<Pair<Path, Path>>,
        expected: List<Pair<String, Path>>,
        mode: RunMode,
        autoAnswers: List<AutoAnswer>?,
    ) {
        val notes = mutableListOf<String>()
        var replaced = 0
        copies.forEach { (from, to) ->
            Files.createDirectories(to.parent)
            // Bij opnieuw proberen staan de bestanden er vaak nog van de vorige poging: overschrijven.
            val existed = Files.exists(to)
            if (existed) replaced++
            Files.copy(from, to, StandardCopyOption.REPLACE_EXISTING)
            notes += "${if (existed) "overschreven" else "gekopieerd"}: $from -> $to"
        }
        step.status = StepStatus.RUNNING
        step.message = "${copies.size} bestanden klaargezet" + (if (replaced > 0) " ($replaced overschreven)" else "")
        val env = if (s.perlioUnbuffered) mapOf("PERLIO" to ":unix") else emptyMap()
        val label = "${step.type.label} – ${step.files.size} ${if (step.files.size == 1) "bestand" else "bestanden"} (${step.fileNames})"
        exeRunner.start(exe, exeDir, label, mode, env, notes, autoAnswers) { code ->
            synchronized(this) {
                if (code != 0) {
                    step.status = StepStatus.FAILED
                    step.message = "Gestopt met exitcode ${code ?: "onbekend"}. Controleer de uitvoer; zet de stap handmatig op klaar als het toch goed ging."
                } else {
                    val missing = expected.filter { !it.second.present() }
                    if (missing.isEmpty()) {
                        step.status = StepStatus.DONE
                        step.message = "Klaar: alle ${expected.size} verwachte bestanden zijn aangemaakt."
                    } else {
                        step.status = StepStatus.FAILED
                        step.message = "Het programma is klaar (exitcode 0), maar er ontbreken bestanden: " +
                            names(missing.map { "${it.first} ${it.second}" }, 6) + ". Bekijk het log; zet de stap handmatig op klaar als het toch goed ging."
                    }
                }
            }
        }
    }

    private fun upload(step: Step, s: WorkflowSettings) {
        val l = Layout(s)
        val items = step.files.flatMap {
            listOf(
                UploadItem(l.ocr(it)!!, s.remoteOcrDir(), "${it.number}.txt"),
                UploadItem(l.pdf(it)!!, s.remotePdfDir(), "${it.number}.pdf"),
            )
        }
        // Bij opnieuw proberen alleen wat nog niet is geüpload; wat er al staat blijft ongemoeid.
        val pending = items.filter { it.remotePath !in step.uploaded }
        step.status = StepStatus.RUNNING
        step.message = "Bezig met uploaden van ${pending.size} bestanden naar ${s.sftpHost}..."
        val results = sftpService.upload(pending)
        results.filter { it.ok }.forEach { step.uploaded += it.item.remotePath }
        val failed = results.firstOrNull { !it.ok }
        if (failed == null && step.files.all { f -> items.filter { it.local.nameWithoutExt() == f.number }.all { it.remotePath in step.uploaded } }) {
            step.status = StepStatus.DONE
            step.message = "${items.size} bestanden staan op de server en zijn gecontroleerd: ${s.remoteOcrDir()} en ${s.remotePdfDir()}."
        } else {
            step.status = StepStatus.FAILED
            step.message = "Upload gestopt na ${results.count { it.ok }} van ${pending.size} bestanden: ${failed?.message ?: "niet alles geüpload"}. " +
                "De bestanden die wel staan, blijven staan en worden bij opnieuw proberen overgeslagen."
        }
    }

    private fun edit(current: TestRun, step: Step) {
        val missing = itemEditService.missingInZcbs(step.files.map { it.number })
        check(missing.isEmpty()) {
            "Deze records bestaan nog niet in ZCBS: ${names(missing)}. Zijn ze aangemaakt (images.exe met \"lege records aanmaken\")? Zo niet, dan is er niets te bewerken."
        }
        itemEditService.startBatch(step.files.map { BatchRecord(it.number, it.originalName, it.by, it.folder) })
        step.status = StepStatus.RUNNING
        step.message = "Bewerk de records op de pagina Records. De stap is klaar als elk record is opgeslagen of op later staat."
    }

    /**
     * Eerst de back-up van de 100pct en pas daarna het verwijderen, en alleen in de mappen van de programma's zelf.
     * Het origineel (en de kopie in `werk`) blijft staan; die gaan in de archiveerstap weg.
     */
    private fun cleanup(step: Step, s: WorkflowSettings) {
        val l = Layout(s)
        val backupDir = l.backup!!
        step.status = StepStatus.RUNNING
        var copied = 0
        step.files.forEach { f ->
            val source = l.img100(f)!!
            val wanted = backupDir.resolve(source.name)
            val target = when {
                !Files.exists(wanted) -> wanted
                Files.mismatch(source, wanted) == -1L -> null // zelfde bestand staat er al, bijv. na opnieuw proberen
                else -> uniqueTarget(backupDir, source.name)
            }
            if (target != null) {
                Files.copy(source, target)
                check(Files.size(target) == Files.size(source)) { "Back-up van ${source.name} is niet even groot als het origineel; er is niets verwijderd." }
                copied++
            }
            check(l.backupOf(f).present() || target != null) { "Back-up van ${source.name} niet gevonden; er is niets verwijderd." }
        }
        var removed = 0
        removed += deleteTree(l.p2zOutput()!!, l.p2z!!)
        listOf("small", "large", "100pct").forEach { removed += deleteTree(l.img!!.resolve(it), l.img) }
        step.files.forEach { if (Files.deleteIfExists(l.imgIn(it)!!)) removed++ }
        l.img!!.listDirectoryEntries("log-*.txt").filter { it.isRegularFile() }.forEach { Files.delete(it); removed++ }
        step.status = StepStatus.DONE
        step.message = "Back-up gemaakt in $backupDir ($copied nieuw), daarna $removed bestanden en mappen opgeruimd in ${l.p2z} en ${l.img}."
    }

    /** Verwijdert [target] en alles eronder, maar alleen als het binnen [root] ligt. @return aantal verwijderde items. */
    private fun deleteTree(target: Path, root: Path): Int {
        if (!Files.isDirectory(target)) return 0
        val absTarget = target.toAbsolutePath().normalize()
        val absRoot = root.toAbsolutePath().normalize()
        check(absTarget.startsWith(absRoot) && absTarget != absRoot) { "Weigert te verwijderen buiten de map van het programma: $target" }
        var count = 0
        Files.walk(absTarget).use { walk ->
            walk.sorted(Comparator.reverseOrder()).forEach { Files.delete(it); count++ }
        }
        return count
    }

    private fun archive(step: Step, s: WorkflowSettings) {
        val archived = Path.of(s.archivedFolder)
        Files.createDirectories(archived)
        val target = uniqueTarget(archived, step.file.originalName)
        Files.move(step.file.original, target)
        step.status = StepStatus.DONE
        step.message = "Verplaatst naar $target"
    }

    private fun uniqueTarget(dir: Path, name: String): Path {
        val base = name.substringBeforeLast('.')
        val ext = name.substringAfterLast('.', "")
        var candidate = dir.resolve(name)
        var i = 1
        while (Files.exists(candidate)) {
            candidate = dir.resolve(if (ext.isEmpty()) "$base-$i" else "$base-$i.$ext")
            i++
        }
        return candidate
    }

    // ---- Wat de gebruiker te zien krijgt ----

    @Synchronized
    fun view(): TestRunView? {
        val current = run ?: return null
        syncEdit(current)
        val s = settingsService.get()
        val next = current.nextStep()

        fun toView(step: Step): StepView {
            val isNext = step.id == next?.id
            return StepView(
                id = step.id,
                type = step.type.name,
                typeLabel = step.type.label,
                fileCount = step.files.size,
                status = step.status.name,
                statusLabel = step.status.label,
                message = step.message,
                question = step.question,
                current = isNext,
                usesExe = step.type.usesExe,
                plan = if (isNext) plan(current, step, s) else emptyList(),
                problems = if (isNext && step.status == StepStatus.PENDING) problems(current, step, s) else emptyList(),
                warnings = if (isNext) warnings(current, step) else emptyList(),
            )
        }

        val rows = current.files.map { file ->
            FileRowView(
                originalName = file.originalName,
                newName = file.newName,
                by = file.by,
                cells = StepType.entries.map { type -> toView(current.steps.first { it.type == type && it.files.contains(file) }) },
                checks = checkGroups(current, file, s),
            )
        }
        return TestRunView(
            startNumber = current.startNumber,
            testFolder = current.testFolder.toString(),
            workDir = current.workDir.toString(),
            fileCount = current.files.size,
            doneCount = current.steps.count { it.finished },
            totalCount = current.steps.size,
            rows = rows,
            stepTypes = StepType.entries.map { it.label },
            next = next?.let(::toView),
        )
    }

    private fun warnings(current: TestRun, step: Step): List<String> = buildList {
        if (step.type == StepType.CLEANUP && current.steps.first { it.type == StepType.UPLOAD }.status == StepStatus.SKIPPED) {
            add("De upload is overgeslagen. Na het opruimen zijn de tekst en pdf's weg, tenzij je ze zelf op de server hebt gezet.")
        }
        if (step.type == StepType.EDIT && !settingsService.get().zcbsIsLocal()) {
            add("Dit schrijft in het echte archief (${settingsService.get().zcbsBaseUrl}). Elke opslag stuurt een e-mail naar de beheerder.")
        }
    }

    private fun plan(current: TestRun, step: Step, s: WorkflowSettings): List<String> {
        val l = Layout(s)
        val n = step.files.size
        val unset = "(niet ingesteld)"
        return when (step.type) {
            StepType.RENAME -> listOf("Maakt een kopie van ${step.file.originalName} als ${current.workDir.resolve(step.file.newName)}.", "Het origineel blijft staan.")
            StepType.PDF2ZCBS -> listOf(
                "Zet $n bestand(en) uit ${current.workDir} in ${l.p2z ?: unset}.",
                "Start ${s.pdf2zcbsExe.ifBlank { unset }} één keer in die map.",
                "Verwacht per bestand in ${l.p2zOutput() ?: unset}: tekst, pdf, small en large.",
            )
            StepType.IMAGES -> listOf(
                "Zet de $n large-jpg's uit ${l.p2zOutput()?.resolve("large") ?: unset} in ${l.img?.resolve("images") ?: unset}.",
                "Start ${s.imagesExe.ifBlank { unset }} één keer. Bij automatisch uitvoeren geeft de app deze antwoorden: bestandstype ${s.imagesFileType}, resolutie ${s.imagesResolution}, instelling ${s.imagesInstitution}, collectie ${s.collection}.",
                "Er wordt nooit overschreven en niets verwijderd: de mappen small, large, 100pct en images blijven staan.",
                "Verwacht per bestand in ${l.img ?: unset}: small, large en 100pct.",
            )
            StepType.UPLOAD -> listOf(
                "Verbindt met sftp://${s.sftpHost.ifBlank { unset }} als ${s.sftpUser.ifBlank { unset }}, alleen met de vastgezette vingerafdruk.",
                "Tekst: $n bestand(en) naar ${s.remoteOcrDir()}",
                "Pdf: $n bestand(en) naar ${s.remotePdfDir()}",
                "Eerst een controle: de mappen bestaan en geen enkel bestand bestaat al. Er wordt niets overschreven, aangemaakt of verwijderd.",
            )
            StepType.EDIT -> listOf(
                "Controleert dat de $n records bestaan in ZCBS (${s.zcbsBaseUrl.ifBlank { unset }}).",
                "Je bewerkt ze één voor één op de pagina Records. Elk record dat je opent is 10 minuten voor anderen vergrendeld.",
                "Elke opslag gaat via het formulier van ZCBS, met een voorbeeld vooraf en teruglezen achteraf. Zie je een melding, dan kun je het record altijd in ZCBS zelf bewerken.",
            )
            StepType.CLEANUP -> listOf(
                "Kopieert de $n 100pct-afbeeldingen naar ${s.backup100pctFolder.ifBlank { unset }}.",
                "Verwijdert daarna ${l.p2zOutput() ?: unset}, ${l.img?.resolve("small") ?: unset}, large en 100pct, de $n bronbestanden in ${l.img?.resolve("images") ?: unset} en log-*.txt in ${l.img ?: unset}.",
                "De tekst en pdf's in output zijn dan weg: die moeten op de server staan (stap Upload).",
            )
            StepType.ARCHIVE -> listOf("Verplaatst ${step.file.original} naar ${s.archivedFolder.ifBlank { unset }}.")
        }
    }

    private fun checkGroups(current: TestRun, file: TestFile, s: WorkflowSettings): List<CheckGroupView> {
        val l = Layout(s)
        fun stepOf(type: StepType) = current.steps.first { it.type == type && it.files.contains(file) }
        val cleaned = stepOf(StepType.CLEANUP).status == StepStatus.DONE

        fun local(type: StepType, label: String, path: Path?): CheckView {
            val status = stepOf(type).status
            val state = when {
                status == StepStatus.PENDING -> "PENDING"
                path.present() -> "OK"
                status == StepStatus.RUNNING -> "PENDING" // wordt nog gemaakt: niet alarmerend rood
                else -> "MISSING"
            }
            return CheckView(label, state, path?.toString().orEmpty())
        }

        fun remote(label: String, remotePath: String): CheckView {
            val up = stepOf(StepType.UPLOAD)
            val state = when {
                remotePath in up.uploaded -> "OK"
                up.status == StepStatus.PENDING || up.status == StepStatus.RUNNING -> "PENDING"
                else -> "MISSING"
            }
            return CheckView(label, state, remotePath)
        }

        return buildList {
            if (!cleaned) {
                add(CheckGroupView("pdf2zcbs", listOf(
                    local(StepType.PDF2ZCBS, "tekst", l.ocr(file)),
                    local(StepType.PDF2ZCBS, "pdf", l.pdf(file)),
                    local(StepType.PDF2ZCBS, "small", l.p2zSmall(file)),
                    local(StepType.PDF2ZCBS, "large", l.p2zLarge(file)),
                )))
                add(CheckGroupView("images", listOf(
                    local(StepType.IMAGES, "small", l.imgSmall(file)),
                    local(StepType.IMAGES, "large", l.imgLarge(file)),
                    local(StepType.IMAGES, "100pct", l.img100(file)),
                )))
            }
            add(CheckGroupView("server", listOf(
                remote("tekst", s.remoteOcrDir() + "/" + file.number + ".txt"),
                remote("pdf", s.remotePdfDir() + "/" + file.number + ".pdf"),
            )))
            add(CheckGroupView("ZCBS", listOf(
                run {
                    val st = itemEditService.statusOf(file.number)
                    val state = when (st) {
                        EditStatus.OPGESLAGEN -> "OK"
                        EditStatus.ONVEILIG, EditStatus.FOUT, EditStatus.GEBLOKKEERD -> "MISSING"
                        else -> "PENDING"
                    }
                    CheckView("record bewerkt" + (st?.takeIf { it == EditStatus.LATER }?.let { " (later)" } ?: ""), state, itemEditService.recordUrl(file.number).orEmpty())
                },
            )))
            add(CheckGroupView("back-up", listOf(
                run {
                    val st = stepOf(StepType.CLEANUP).status
                    val path = l.backupOf(file)
                    CheckView("100pct", when { path.present() -> "OK"; st == StepStatus.PENDING -> "PENDING"; else -> "MISSING" }, path?.toString().orEmpty())
                },
            )))
        }
    }
}
