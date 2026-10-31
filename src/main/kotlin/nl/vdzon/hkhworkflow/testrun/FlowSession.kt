package nl.vdzon.hkhworkflow.testrun

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.io.path.extension
import kotlin.io.path.isRegularFile
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import nl.vdzon.hkhworkflow.exerunner.AutoAnswer
import nl.vdzon.hkhworkflow.exerunner.ExeRunner
import nl.vdzon.hkhworkflow.exerunner.RunMode
import nl.vdzon.hkhworkflow.items.Current
import nl.vdzon.hkhworkflow.items.ItemEditService
import nl.vdzon.hkhworkflow.settings.SettingsService
import nl.vdzon.hkhworkflow.settings.WorkflowSettings
import nl.vdzon.hkhworkflow.ftp.FtpService
import nl.vdzon.hkhworkflow.ftp.UploadItem
import org.slf4j.LoggerFactory

/**
 * De doorloop van "Nieuwe bestanden" van één gebruiker in één collectie: de stand (bestanden, stappen, controles) en alles wat er uit volgt. Per
 * collectie is er hooguit één doorloop tegelijk ([TestRunService] houdt dat bij en geeft elke gebruiker de zijne); elke doorloop heeft zijn eigen
 * mappen ([WorkFolders]), zijn eigen programma's ([ExeRunner]) en kijkt via [Current] naar de ZCBS-sessie van zijn eigenaar.
 */
class FlowSession(
    private val settingsService: SettingsService,
    val exeRunner: ExeRunner,
    private val ftpService: FtpService,
    private val itemEditService: ItemEditService,
    val sourceFiles: SourceFilesService,
    private val processed: ProcessedLog,
    private val input: WorkFolders,
    val collection: String,
) {
    /** Wie deze doorloop heeft: de ZCBS-naam, of leeg voor de technische pagina van de beheerder. */
    val owner: String get() = input.user

    @Volatile
    var lastActivity: java.time.Instant = java.time.Instant.now()
        private set

    @Volatile
    var claimedSince: java.time.Instant? = null
        private set

    /** Er is gestart (de controle van fase 1 is gedaan) of er loopt een doorloop: de collectie is dan van de eigenaar. */
    val claimed: Boolean get() = started || run != null

    fun touch() { lastActivity = java.time.Instant.now() }

    /** Stopt wat er loopt en wist de stand: de collectie komt vrij. De bestanden op schijf blijven staan (de controle op resten wijst er de gebruiker op). */
    fun release() {
        runCatching { reset() }
    }

    /** De fase waar de doorloop nu is, zonder dat een fout de aanroeper raakt. */
    fun flowViewQuietly(): Int = runCatching { flowView().current }.getOrDefault(0)

    private fun claim() { if (claimedSince == null) claimedSince = java.time.Instant.now() }

    /** De instellingen voor deze collectie: de instellingen van de app met de collectie van deze doorloop. */
    private fun settings(): WorkflowSettings = settingsService.get().copy(collection = collection)

    private val log = LoggerFactory.getLogger(javaClass)

    @Volatile
    private var run: TestRun? = null

    /** Heeft de gebruiker de lijst met pdf's in de invoermap bevestigd (fase Bestanden kiezen)? */
    @Volatile
    private var filesConfirmed: Boolean = false

    /** Heeft Carla de controle van de omgeving gehad en op "verder" gedrukt (fase 1)? */
    @Volatile
    private var started: Boolean = false

    /** De gebruiker voor wie de bestanden in de invoermap bij deze doorloop horen ("" zonder inlog), of null als dat nog niet bevestigd is. */
    @Volatile
    private var inputAcceptedFor: String? = null

    /** De bestanden die nu in de invoermap staan horen bij deze doorloop (Carla heeft ze zelf gekozen of zegt dat ze erbij horen). */
    @Synchronized
    fun acceptInput() { inputAcceptedFor = input.user }

    /** De invoermap van de ingelogde gebruiker, of null als de invoermap niet is ingesteld. */
    fun inputFolder(): Path? = input.invoer()

    /** @return een melding als er nog resten van een eerdere run staan, anders null. */
    @Synchronized
    fun confirmFiles(): String? {
        if (leftovers().isNotEmpty()) return LEFTOVERS_MESSAGE
        if (filesInTestFolder().isEmpty()) return "Er staan geen bestanden in je invoermap. Kies eerst bestanden en kopieer ze."
        filesConfirmed = true
        return null
    }

    /** De gekozen bestanden (pdf's en jpg's) die nu in de invoermap van de gebruiker staan (alleen de map zelf, geen submappen). */
    fun filesInTestFolder(): List<Path> {
        val dir = input.invoer() ?: return emptyList()
        if (!Files.isDirectory(dir)) return emptyList()
        return dir.listDirectoryEntries()
            .filter { it.isRegularFile() && it.extension.lowercase() in CHOSEN_EXTENSIONS }
            .sortedBy { it.name.lowercase() }
    }

    @Synchronized
    fun start(startNumber: String) {
        stopPrevious()
        val settings = settings()
        input.ensureAll()
        val testFolder = input.invoer()!!
        val chosen = filesInTestFolder()
        require(chosen.isNotEmpty()) { "Er staan geen bestanden in de testmap." }

        val numbers = sequenceNumbers(startNumber, chosen.size)
        val files = chosen.mapIndexed { i, path ->
            val origin = sourceFiles.origin(path.name)
            TestFile(i, path, numbers[i], origin?.by.orEmpty(), origin?.folder.orEmpty())
        }
        var id = 0
        val steps = StepType.entries.flatMap { type ->
            if (type.perFile) files.map { Step(++id, type, listOf(it)) } else listOf(Step(++id, type, files))
        }
        run = TestRun(startNumber.trim(), testFolder, input.werk()!!, files, steps)
        claim()
        log.info("Testrun gestart: {} bestanden vanaf {}", files.size, startNumber)
    }

    @Synchronized
    fun reset() {
        stopPrevious()
        // Nog geen doorloop gestart: dan is er alleen een keuze gemaakt, en die gaat weg (de kopieën, niet de originelen).
        if (run == null) sourceFiles.removeAllCopies()
        run = null
        filesConfirmed = false
        started = false
        inputAcceptedFor = null
        finishedShown = false
        claimedSince = null
    }

    private var finishedShown = false

    /** Het bestand waarin de stand van deze doorloop wordt bewaard, in de map van de gebruiker. */
    fun stateFile(): Path? = input.root()?.resolve("sessie.json")

    /** De stand om te bewaren, of null als er niets te bewaren valt (er is niets gestart). */
    @Synchronized
    fun state(): FlowState? {
        if (!claimed) return null
        val current = run
        val files = current?.files.orEmpty()
        return FlowState(
            owner = owner, collection = collection, startNumber = current?.startNumber,
            files = files.map { StateFile(it.index, it.original.toString(), it.number, it.by, it.folder) },
            steps = current?.steps.orEmpty().map { st -> StateStep(st.id, st.type.name, st.files.map { it.index }, st.status.name, st.message, st.uploaded.sorted()) },
            gated = current?.gated == true, reviewed = current?.reviewed.orEmpty().map { it.name }.sorted(),
            started = started, filesConfirmed = filesConfirmed, inputAcceptedFor = inputAcceptedFor, finishedShown = finishedShown,
            claimedSince = claimedSince?.toString(),
        )
    }

    /** Zet een bewaarde stand terug (na een herstart van de app). Een stap die toen liep, is mislukt: het programma is er niet meer. */
    @Synchronized
    fun restore(state: FlowState) {
        started = state.started
        filesConfirmed = state.filesConfirmed
        inputAcceptedFor = state.inputAcceptedFor
        finishedShown = state.finishedShown
        claimedSince = state.claimedSince?.let { runCatching { java.time.Instant.parse(it) }.getOrNull() }
        touch()
        val startNumber = state.startNumber ?: return
        val files = state.files.map { TestFile(it.index, Path.of(it.original), it.number, it.by, it.folder) }
        val steps = state.steps.map { st ->
            Step(st.id, StepType.valueOf(st.type), st.fileIndexes.mapNotNull { i -> files.firstOrNull { it.index == i } }).also { step ->
                step.status = StepStatus.valueOf(st.status)
                step.message = st.message
                step.uploaded += st.uploaded
                if (step.status == StepStatus.RUNNING) {
                    step.status = StepStatus.FAILED
                    step.message = "De app is herstart terwijl deze stap bezig was. Controleer wat er al staat en probeer het opnieuw."
                }
            }
        }
        run = TestRun(startNumber, input.invoer()!!, input.werk()!!, files, steps).also { r ->
            r.gated = state.gated
            r.reviewed += state.reviewed.map { StepType.valueOf(it) }
        }
    }

    /**
     * De stand voor de pagina Nieuwe bestanden, in één keer gelezen (de laatste stap loopt in een andere thread). Is de doorloop klaar
     * en is dat al eens getoond, dan begint de pagina opnieuw bij het begin. Het overzicht van de gemaakte bestanden zit erbij zolang de pagina klaar is.
     */
    @Synchronized
    fun pageState(): Pair<FlowView, List<SummaryLine>?> {
        if (finishedShown && run?.nextStep() == null && run != null) reset()
        val flow = flowView()
        if (!flow.finished) return flow to null
        finishedShown = true
        return flow to summary()
    }

    /** Alles wat deze doorloop heeft gemaakt en waar het staat: op de server (ftp) en in de back-up. */
    @Synchronized
    fun summary(): List<SummaryLine> {
        val current = run ?: return emptyList()
        val s = settings()
        val files = current.files
        if (files.isEmpty()) return emptyList()
        val pdfs = files.filter { !it.isImage }
        // "99062.pdf t/m 99064.pdf" als de nummers aaneensluiten, anders een opsomming.
        fun range(list: List<TestFile>, ext: String): String {
            val numbers = list.map { it.number }
            val consecutive = numbers.size > 1 && numbers.map { it.toLongOrNull() }.zipWithNext().all { (a, b) -> a != null && b != null && b == a + 1 }
            return when {
                numbers.size == 1 -> numbers.first() + "." + ext
                consecutive -> numbers.first() + "." + ext + " t/m " + numbers.last() + "." + ext
                else -> names(numbers.map { "$it.$ext" }, 6)
            }
        }
        fun line(place: String, kind: String, ext: String, folder: String, list: List<TestFile> = files) =
            if (list.isEmpty()) null else SummaryLine(place, kind, list.size, range(list, ext), folder)
        val server = "Server (ftp ${s.ftpHost})"
        return listOfNotNull(
            line(server, "small", "jpg", s.remoteImagesDir("small")),
            line(server, "large", "jpg", s.remoteImagesDir("large")),
            line(server, "100pct", "jpg", s.remoteImagesDir("100pct")),
            line(server, "tekst (ocr)", "txt", s.remoteOcrDir(), pdfs),
            line(server, "pdf", "pdf", s.remotePdfDir(), pdfs),
            line("Back-up", "100pct", "jpg", s.backup100pctFolder()),
            line("Back-up", "pdf", "pdf", s.backupPdfFolder(), pdfs),
        )
    }

    /**
     * Fase Nummers: controleert dat alle nummers aaneengesloten vrij zijn in ZCBS, start de run en maakt van elk bestand een kopie
     * met het recordnummer in `werk` (het origineel blijft staan).
     * @return een melding als het niet kan, anders null.
     */
    @Synchronized
    fun prepare(startNumber: String): String? {
        val chosen = filesInTestFolder()
        if (chosen.isEmpty()) return "Er staan geen bestanden in de invoermap."
        if (leftovers().isNotEmpty()) return LEFTOVERS_MESSAGE
        val start = startNumber.trim().toLongOrNull() ?: return "Geef een beginnummer van alleen cijfers op."
        val free = itemEditService.freeNumbers()
            ?: return "ZCBS gaf de lijst met vrije nummers niet. Is je sessie verlopen, heb je geen recht om records aan te maken, of is ZCBS niet bereikbaar? Log opnieuw in."
        free.check(start, chosen.size)?.let { return it }
        return try {
            start(free.format(start))
            run!!.gated = true
            while (true) {
                val next = run!!.nextStep() ?: break
                if (next.type != StepType.RENAME) break
                execute(next.id, RunMode.IN_APP)
                if (next.status != StepStatus.DONE) return next.message ?: "Hernoemen is mislukt."
            }
            skipUnneeded(run!!)
            null
        } catch (e: Exception) {
            e.message ?: e.javaClass.simpleName
        }
    }

    /** Carla heeft de uitvoer van deze stap gecontroleerd; de volgende stap mag nu. @return een melding of null. */
    @Synchronized
    fun confirmReview(type: StepType): String? {
        val current = run ?: return "Er is nog niets gestart."
        val step = current.steps.firstOrNull { it.type == type } ?: return "Onbekende stap."
        if (!step.finished) return "${type.label} is nog niet klaar."
        current.reviewed += type
        skipUnneeded(current)
        return null
    }

    /** De flow in zeven fasen, zoals de gebruiker hem ziet. */
    @Synchronized
    fun flowView(): FlowView {
        val v = view()
        val current = run
        val nextType = current?.nextStep()?.type
        val gated = current?.gated == true
        var review = false
        val phase = when {
            v == null && !started -> 1
            v == null && !filesConfirmed -> 2
            v == null -> 3
            nextType == null -> 8
            nextType == StepType.RENAME -> 3
            nextType == StepType.PDF2ZCBS -> 4
            nextType == StepType.IMAGES -> if (gated && StepType.PDF2ZCBS !in current!!.reviewed) { review = true; 4 } else 5
            nextType == StepType.UPLOAD -> if (gated && StepType.IMAGES !in current!!.reviewed) { review = true; 5 } else 6
            else -> 7
        }
        val titles = listOf("Controleren", "Bestanden kiezen", "Nummers", "pdf2zcbs", "images.exe", "Upload ocr en pdf", "Back-up")
        val chosen = filesInTestFolder().size
        val notes = mapOf(
            2 to chosen.takeIf { it > 0 }?.let { "$it gekozen" },
            3 to current?.files?.let { it.first().number + (if (it.size > 1) " t/m " + it.last().number else "") },
            4 to "overgeslagen".takeIf { current?.steps?.firstOrNull { s -> s.type == StepType.PDF2ZCBS }?.status == StepStatus.SKIPPED },
            6 to "overgeslagen".takeIf { current?.steps?.firstOrNull { s -> s.type == StepType.UPLOAD }?.status == StepStatus.SKIPPED },
        )
        val phases = titles.mapIndexed { i, title ->
            val nr = i + 1
            PhaseView(nr, title, when { nr < phase -> "done"; nr == phase -> "now"; else -> "todo" }, notes[nr])
        }
        return FlowView(phases, phase, phase == 8, review, v)
    }

    // ---- Fase 1: de omgeving controleren ----

    /**
     * Staat alles klaar om te beginnen? Ingelogd, de lijst met vrije nummers uit ZCBS, de mappen met scans, de programma's, de back-upmappen, de upload,
     * en geen resten van een eerdere keer. Wat niet klopt, krijgt een uitleg; de instellingen zelf wijzigt de beheerder.
     */
    fun environment(): List<EnvCheck> {
        val s = settings()
        val admin = "Dit stelt de beheerder in bij Instellingen."
        fun folder(path: String) = path.isNotBlank() && Files.isDirectory(Path.of(path))
        fun file(path: String) = path.isNotBlank() && Files.isRegularFile(Path.of(path))
        fun unset(path: String) = path.ifBlank { "(niet ingesteld)" }
        fun check(id: String, ok: Boolean, okLabel: String, failLabel: String, detail: String = "", okDetail: String = "") =
            if (ok) EnvCheck(id, okLabel, true, okDetail) else EnvCheck(id, failLabel, false, detail)
        val roots = s.sourceRoots()
        val unreachable = roots.filter { !folder(it.path) }
        val backups = listOf(s.backup100pctFolder(), s.backupPdfFolder()).filter { !folder(it) }
        val exes = listOf(s.pdf2zcbsExe, s.imagesExe).filter { !file(it) }
        val ftpSettled = s.ftpHost.isNotBlank() && s.ftpUser.isNotBlank() && s.ftpPassword.isNotEmpty()
        val ftp = if (ftpSettled) ftpService.testConnection(5_000) else null
        val ftpMissingDirs = ftp?.dirs?.filter { !it.exists }.orEmpty()
        return listOf(
            check("login", itemEditService.loggedIn(), "Ingelogd bij ZCBS als ${itemEditService.user}", "Niet ingelogd bij ZCBS", "Log eerst in via Start."),
            check("numbers", itemEditService.freeNumbers() != null, "ZCBS geeft de vrije recordnummers", "De vrije recordnummers uit ZCBS zijn niet op te halen",
                "Is je sessie verlopen, of heb je geen recht om records aan te maken? Log opnieuw in via Start."),
            check("scans", roots.isNotEmpty() && unreachable.isEmpty(), "De mappen met scans zijn bereikbaar", if (roots.isEmpty()) "Er zijn geen mappen met scans ingesteld" else "De mappen met scans zijn niet bereikbaar",
                if (roots.isEmpty()) admin else "Niet bereikbaar: ${unreachable.joinToString { it.path }}. Is de schijf (K:) gekoppeld?", okDetail = roots.joinToString { it.name }),
            check("exes", exes.isEmpty(), "pdf2zcbs.exe en images.exe zijn gevonden", "pdf2zcbs.exe of images.exe is niet gevonden", "Niet gevonden: ${exes.joinToString { unset(it) }}. $admin"),
            check("backup", backups.isEmpty(), "De back-upmappen zijn bereikbaar", "De back-upmappen zijn niet bereikbaar",
                "Niet bereikbaar: ${backups.joinToString { unset(it) }}. Is de schijf (K:) gekoppeld? Anders stelt de beheerder dit in bij Instellingen."),
            when {
                !ftpSettled -> EnvCheck("ftp", "De ftp-gegevens voor de upload zijn niet ingesteld", false, "Host, gebruiker of wachtwoord ontbreekt. $admin")
                ftp != null && !ftp.ok -> EnvCheck("ftp", "De ftp-server is niet bereikbaar", false, ftp.message)
                ftpMissingDirs.isNotEmpty() -> EnvCheck("ftp", "Op de ftp-server ontbreken mappen", false, "Niet gevonden: ${ftpMissingDirs.joinToString { it.path }}. $admin")
                else -> EnvCheck("ftp", "De ftp-server is bereikbaar en de mappen voor tekst en pdf bestaan", true, s.ftpHost)
            },
            check("leftovers", leftovers().isEmpty(), "Er staat geen werk van een eerdere keer", "Er staat nog werk van een eerdere keer", "Zie hieronder: verplaats of verwijder het."),
        )
    }

    /** Zet de programma's uit de hoofdmap (de instellingen) in de map van deze gebruiker, als ze er nog niet staan of veranderd zijn. */
    private fun syncPrograms() {
        val s = settings()
        ProgramFiles.sync(s.pdf2zcbsExe, input.pdf2zcbs() ?: return)
        ProgramFiles.sync(s.imagesExe, input.images() ?: return)
    }

    /** Fase 1 is klaar: alles is in orde en Carla gaat verder. @return een melding als er nog iets niet klopt, anders null. */
    @Synchronized
    fun startFlow(): String? {
        if (run != null) return null
        val problem = environment().firstOrNull { !it.ok } ?: run { input.ensureAll(); syncPrograms(); started = true; claim(); return null }
        return "${problem.label}: ${problem.detail}".trim().trimEnd(':')
    }

    // ---- Resten van een eerdere run ----

    /**
     * Wat er nog in de mappen van pdf2zcbs.exe en images.exe staat van een eerdere run (of van handwerk): pdf's naast pdf2zcbs, de uitvoer in
     * `output` (behalve `log` en `updates.txt`), de bestanden in `images` en de resultaten in `small`, `large` en `100pct`.
     */
    fun leftovers(): List<Leftover> {
        val l = Layout(settings(), input)
        fun regular(dir: Path, deep: Boolean, skip: (Path) -> Boolean = { false }): List<Path> {
            if (!Files.isDirectory(dir)) return emptyList()
            return runCatching {
                if (deep) Files.walk(dir).use { w -> w.filter { Files.isRegularFile(it) && !skip(it) }.sorted().toList() }
                else dir.listDirectoryEntries().filter { it.isRegularFile() && !skip(it) }.sortedBy { it.name }
            }.getOrDefault(emptyList())
        }
        return buildList {
            // Wat al in de invoermap staat zonder dat Carla het in deze doorloop koos, is van een eerdere keer.
            input.invoer()?.takeIf { run == null && inputAcceptedFor != input.user }?.let { dir ->
                regular(dir, true) { it.name == "herkomst.tsv" }.takeIf { it.isNotEmpty() }
                    ?.let { add(Leftover("invoer", "je gekozen bestanden", dir, it)) }
            }
            // De kopieën met het recordnummer van een doorloop die niet is afgemaakt (in een doorloop horen ze er juist te staan).
            input.werk()?.takeIf { run == null }?.let { dir ->
                regular(dir, true).takeIf { it.isNotEmpty() }?.let { add(Leftover("werk", "de kopieën met recordnummer", dir, it)) }
            }
            l.p2z?.let { dir ->
                regular(dir, false) { !it.extension.equals("pdf", ignoreCase = true) }.takeIf { it.isNotEmpty() }?.let { add(Leftover("pdf2zcbs", "pdf2zcbs: pdf's in de map van het programma", dir, it)) }
                val out = dir.resolve("output")
                regular(out, true) { out.relativize(it).getName(0).toString() == "log" || it.name == "updates.txt" }.takeIf { it.isNotEmpty() }
                    ?.let { add(Leftover("pdf2zcbs", "pdf2zcbs: de map output", dir, it)) }
            }
            l.img?.let { dir ->
                regular(dir.resolve("images"), false).takeIf { it.isNotEmpty() }?.let { add(Leftover("images", "images.exe: bronbestanden in de map images", dir, it)) }
                listOf("small", "large", "100pct").forEach { sub ->
                    regular(dir.resolve(sub), true).takeIf { it.isNotEmpty() }?.let { add(Leftover("images", "images.exe: de map $sub", dir, it)) }
                }
            }
        }
    }

    /** De map waar resten naartoe gaan: de instelling (met een submap per gebruiker en collectie), of de map `resten` van de gebruiker. */
    fun leftoversTarget(): Path? {
        return input.resten()
    }

    /**
     * Ruimt de resten op: verplaatst ze naar een submap met datum en tijd van [leftoversTarget] (en behoudt de mapstructuur), of verwijdert ze.
     * Alleen de bestanden uit [leftovers]; lege mappen daaronder verdwijnen. Alleen als er geen doorloop of programma bezig is.
     * @return een melding over wat er gebeurd is, of waarom het niet kan.
     */
    @Synchronized
    fun clearLeftovers(move: Boolean): String {
        if (run != null) return "Er is al een doorloop bezig. Begin eerst opnieuw."
        if (exeRunner.isRunning()) return "Er draait nog een programma."
        val items = leftovers()
        if (items.isEmpty()) return "Er stond niets meer om op te ruimen."
        val total = items.sumOf { it.files.size }
        val target = if (move) {
            val root = leftoversTarget() ?: return "Stel eerst de invoermap of de map voor resten in bij Instellingen."
            root.resolve(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")))
        } else null
        try {
            items.forEach { item ->
                val area = item.area
                item.files.forEach { file ->
                    check(file.toAbsolutePath().normalize().startsWith(item.base.toAbsolutePath().normalize())) { "Weigert iets buiten de map van het programma aan te raken: $file" }
                    if (target != null) {
                        val dest = target.resolve(area).resolve(item.base.relativize(file).toString())
                        Files.createDirectories(dest.parent)
                        Files.move(file, dest)
                    } else Files.delete(file)
                }
            }
            val l = Layout(settings(), input)
            listOfNotNull(l.p2zOutput(), l.img?.resolve("small"), l.img?.resolve("large"), l.img?.resolve("100pct"), input.werk()).forEach { pruneEmptyDirs(it) }
            if (items.any { it.area == "invoer" }) {
                inputAcceptedFor = null
                input.invoer()?.resolve("herkomst.tsv")?.let { Files.deleteIfExists(it) }
            }
        } catch (e: Exception) {
            log.warn("Resten opruimen mislukt: {}", e.message)
            return "Opruimen is niet helemaal gelukt: ${e.message ?: e.javaClass.simpleName}. Controleer de mappen van de programma's."
        }
        log.info("{} bestanden van een eerdere run {}", total, if (target != null) "verplaatst naar $target" else "verwijderd")
        return if (target != null) "$total bestanden verplaatst naar $target." else "$total bestanden verwijderd."
    }

    /** Verwijdert lege (sub)mappen onder [dir], en [dir] zelf als die leeg is. */
    private fun pruneEmptyDirs(dir: Path) {
        if (!Files.isDirectory(dir)) return
        Files.walk(dir).use { w -> w.sorted(Comparator.reverseOrder()).filter { Files.isDirectory(it) }.toList() }.forEach { runCatching { Files.deleteIfExists(it) } }
    }

    /** Een nieuwe test begint schoon: een nog lopend programma wordt gestopt en de console leeggemaakt. */
    private fun stopPrevious() {
        check(exeRunner.stopAndWait()) { "Het lopende programma wil niet stoppen; probeer het nog eens." }
        exeRunner.clear()
        log.info("Vorige testrun opgeruimd")
    }

    @Synchronized
    fun execute(stepId: Int, mode: RunMode, auto: Boolean = false) {
        val current = run ?: error("Er is geen testrun gestart.")
        val step = current.steps.firstOrNull { it.id == stepId } ?: error("Onbekende stap $stepId.")
        check(current.nextStep()?.id == step.id) { "Deze stap is nog niet aan de beurt." }
        check(step.status != StepStatus.RUNNING) { "Deze stap is al bezig." }
        check(!exeRunner.isRunning()) { "Er draait nog een programma." }
        step.message = null
        if ((step.type == StepType.PDF2ZCBS || step.type == StepType.UPLOAD) && step.activeFiles.isEmpty()) {
            skipStep(current, step)
            return
        }
        try {
            val settings = settings()
            val problems = problems(current, step, settings)
            check(problems.isEmpty()) { problems.joinToString("\n") }
            when (step.type) {
                StepType.RENAME -> rename(current, step)
                StepType.PDF2ZCBS -> runPdf2zcbs(current, step, settings, mode, auto)
                StepType.IMAGES -> runImages(current, step, settings, mode, auto)
                StepType.UPLOAD -> upload(step, settings)
                StepType.CLEANUP -> cleanup(current, step, settings)
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

    /** Een stap zonder bestanden om te verwerken (alleen afbeeldingen gekozen: geen tekst en pdf) slaat zichzelf over. */
    private fun skipStep(current: TestRun, step: Step) {
        step.status = StepStatus.SKIPPED
        step.message = "Overgeslagen: er zijn alleen afbeeldingen gekozen, en die hebben geen tekst of pdf."
        if (step.type == StepType.PDF2ZCBS) current.reviewed += step.type
    }

    /**
     * Slaat de stappen over waar geen enkel bestand voor is. pdf2zcbs.exe meteen; de upload pas als de uitvoer van images.exe is gecontroleerd
     * (anders zou de controle erna worden overgeslagen).
     */
    private fun skipUnneeded(current: TestRun) {
        current.steps.firstOrNull { it.type == StepType.PDF2ZCBS && it.status == StepStatus.PENDING && it.activeFiles.isEmpty() }?.let { skipStep(current, it) }
        if (!current.gated || StepType.IMAGES in current.reviewed) {
            current.steps.firstOrNull { it.type == StepType.UPLOAD && it.status == StepStatus.PENDING && it.activeFiles.isEmpty() && current.steps.first { s -> s.type == StepType.IMAGES }.finished }
                ?.let { skipStep(current, it) }
        }
    }

    private fun update(stepId: Int, change: (Step) -> Unit) {
        val step = run?.steps?.firstOrNull { it.id == stepId } ?: error("Onbekende stap $stepId.")
        check(step.status != StepStatus.RUNNING) { "Deze stap is nog bezig." }
        change(step)
    }

    // ---- Locaties van alle bestanden die de stappen aanmaken of nodig hebben ----

    private class Layout(val s: WorkflowSettings, folders: WorkFolders) {
        /** De mappen van deze gebruiker voor de programma's; daarin staat een kopie van het programma uit de hoofdmap. */
        val p2z: Path? = folders.pdf2zcbs()
        val img: Path? = folders.images()

        /** Het programma zelf, in de map van de gebruiker. */
        fun p2zExe(): Path? = p2z?.resolve(Path.of(s.pdf2zcbsExe).fileName)
        fun imgExe(): Path? = img?.resolve(Path.of(s.imagesExe).fileName)
        val backup: Path? = s.backup100pctFolder().trim().takeIf { it.isNotEmpty() }?.let { Path.of(it) }
        val backupPdf: Path? = s.backupPdfFolder().trim().takeIf { it.isNotEmpty() }?.let { Path.of(it) }

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
        fun backupPdfOf(f: TestFile): Path? = backupPdf?.resolve("${f.number}.pdf")
        fun p2zInput(f: TestFile): Path? = p2z?.resolve("${f.number}.pdf")
    }

    /** Bestaat het bestand en is het niet leeg? Een tekstbestand mag leeg zijn: een pagina zonder tekst geeft een lege tekst (dat zie je dan bij het controleren). */
    private fun Path?.present(allowEmpty: Boolean = false): Boolean =
        this != null && Files.isRegularFile(this) && (allowEmpty || runCatching { Files.size(this) > 0 }.getOrDefault(false))

    private fun names(list: List<String>, max: Int = 8): String =
        list.take(max).joinToString() + if (list.size > max) " en ${list.size - max} andere" else ""

    // ---- Controles vooraf: wat houdt een stap nu tegen? ----

    private fun problems(current: TestRun, step: Step, s: WorkflowSettings): List<String> {
        val l = Layout(s, input)
        val batch = step.activeFiles.map { it.number }.toSet()
        return when (step.type) {
            StepType.RENAME -> buildList {
                val f = step.file
                if (!Files.exists(f.original)) add("Origineel niet gevonden: ${f.original}")
                if (Files.exists(current.workDir.resolve(f.newName))) add("${f.newName} bestaat al in ${current.workDir}.")
            }

            StepType.PDF2ZCBS -> buildList {
                exeProblem("pdf2zcbs.exe", s.pdf2zcbsExe)?.let { add(it) }
                step.activeFiles.forEach { if (!Files.exists(current.workDir.resolve(it.newName))) add("Werkbestand ontbreekt: ${current.workDir.resolve(it.newName)} (is de hernoem-stap gedaan?)") }
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
                if (current.gated && StepType.PDF2ZCBS !in current.reviewed) add("Controleer eerst de bestanden van pdf2zcbs.exe en bevestig dat ze goed zijn.")
                exeProblem("images.exe", s.imagesExe)?.let { add(it) }
                step.files.forEach {
                    if (it.isImage) {
                        if (!current.workDir.resolve(it.newName).present()) add("Werkbestand ontbreekt: ${current.workDir.resolve(it.newName)} (is de hernoem-stap gedaan?)")
                    } else if (!l.p2zLarge(it).present()) add("Large-jpg van pdf2zcbs ontbreekt: ${l.p2zLarge(it) ?: "(pdf2zcbs.exe niet ingesteld)"} (is de pdf2zcbs-stap gedaan?)")
                }
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
                if (current.gated && StepType.IMAGES !in current.reviewed) add("Controleer eerst de bestanden van images.exe en bevestig dat ze goed zijn.")
                if (s.ftpHost.isBlank()) add("Ftp-host is niet ingesteld (Instellingen).")
                if (s.ftpUser.isBlank()) add("Ftp-gebruiker is niet ingesteld (Instellingen).")
                if (s.ftpPassword.isEmpty()) add("Ftp-wachtwoord is niet ingesteld (Instellingen, bij de antwoorden voor images.exe).")
                if (s.collection.isBlank()) add("Collectie is niet ingesteld (Instellingen).")
                step.activeFiles.forEach {
                    if (!l.ocr(it).present(allowEmpty = true)) add("Tekstbestand ontbreekt: ${l.ocr(it) ?: "(pdf2zcbs.exe niet ingesteld)"}")
                    if (!l.pdf(it).present()) add("Pdf ontbreekt: ${l.pdf(it) ?: "(pdf2zcbs.exe niet ingesteld)"}")
                }
            }

            StepType.CLEANUP -> buildList {
                if (l.p2z == null || l.img == null) add("Stel eerst de paden van pdf2zcbs.exe en images.exe in.")
                if (s.backup100pctFolder().isBlank()) add("Stel eerst de back-upmap voor de 100pct in (Instellingen).")
                else if (!Files.isDirectory(Path.of(s.backup100pctFolder()))) add("Back-upmap voor de 100pct bestaat niet: ${s.backup100pctFolder()}")
                val hasPdfs = step.files.any { !it.isImage }
                if (hasPdfs && s.backupPdfFolder().isBlank()) add("Stel eerst de back-upmap voor de pdf's in (Instellingen).")
                else if (hasPdfs && !Files.isDirectory(Path.of(s.backupPdfFolder()))) add("Back-upmap voor de pdf's bestaat niet: ${s.backupPdfFolder()}")
                step.files.forEach {
                    if (!l.img100(it).present()) add("100pct ontbreekt, dus geen back-up mogelijk: ${l.img100(it) ?: "(images.exe niet ingesteld)"}")
                    if (!it.isImage && !current.workDir.resolve(it.newName).present()) add("De pdf met recordnummer ontbreekt, dus geen back-up mogelijk: ${current.workDir.resolve(it.newName)}")
                }
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
        syncPrograms()
        val l = Layout(s, input)
        val dir = l.p2z!!
        val copies = step.activeFiles.map { current.workDir.resolve(it.newName) to dir.resolve(it.newName) }
        val expected = step.activeFiles.flatMap {
            listOf("tekst" to l.ocr(it)!!, "pdf" to l.pdf(it)!!, "small" to l.p2zSmall(it)!!, "large" to l.p2zLarge(it)!!)
        }
        val known = ExeAnswers.PDF2ZCBS
        val skipped = step.files.size - step.activeFiles.size
        val note = if (skipped > 0) " ${if (skipped == 1) "1 afbeelding is" else "$skipped afbeeldingen zijn"} overgeslagen (geen pdf: ze gaan rechtstreeks naar images.exe)." else ""
        runExe(current, step, s, l.p2zExe()!!, dir, copies, expected, mode, if (auto) known else null, known, extraNote = note)
    }

    private fun runImages(current: TestRun, step: Step, s: WorkflowSettings, mode: RunMode, auto: Boolean) {
        if (auto) {
            check(s.ftpPassword.isNotEmpty()) {
                "Voor automatisch uitvoeren moet bij Instellingen het FTP-wachtwoord staan. Gebruik anders Handmatig uitvoeren: typ het één keer in, dan neemt de app het over."
            }
        }
        syncPrograms()
        val l = Layout(s, input)
        val dir = l.img!!
        // Een pdf geeft de large-jpg van pdf2zcbs door; een afbeelding gaat zelf rechtstreeks naar images.exe.
        val copies = step.files.map { (if (it.isImage) current.workDir.resolve(it.newName) else l.p2zLarge(it)!!) to l.imgIn(it)!! }
        val expected = step.files.flatMap { listOf("small" to l.imgSmall(it)!!, "large" to l.imgLarge(it)!!, "100pct" to l.img100(it)!!) }
        val known = ExeAnswers.images(s)
        // De vragen over verwijderen en overschrijven beantwoordt de app ook bij handmatig uitvoeren zelf met nee: images.exe wist anders de
        // afbeeldingen die de app nog nodig heeft voor de controle en de back-up (het opruimen doet de app daarna zelf). Bij handmatig
        // uitvoeren stelt hij de andere antwoorden alleen voor; een ingesteld FTP-wachtwoord geeft hij wel één keer vanzelf.
        val forced = if (auto) emptyList() else ExeAnswers.SAFETY + known.filter { it.secret && it.answer.isNotEmpty() }.map { it.copy(once = true) }
        runExe(current, step, s, l.imgExe()!!, dir, copies, expected, mode, if (auto) known else null, known, forced)
    }

    private fun yes(answer: String?): Boolean = answer?.trim()?.lowercase() in setOf("j", "ja", "y", "yes")

    /**
     * Het ftp-wachtwoord dat iemand bij handmatig uitvoeren van images.exe typt, wordt de instelling als die nog leeg is. Verder
     * leert de app niets: de antwoorden zijn vast.
     */
    private fun learnPassword(typedSecrets: Map<String, String>) {
        val typed = typedSecrets.entries.firstOrNull { it.key.contains(ExeAnswers.FTP_PASSWORD_QUESTION, ignoreCase = true) }?.value.orEmpty()
        val s = settingsService.get()
        if (typed.isEmpty() || s.ftpPassword.isNotEmpty()) return
        settingsService.save(s.withFtpPassword(typed))
        log.info("Het FTP-wachtwoord dat is getypt bij handmatig uitvoeren is overgenomen als instelling.")
    }

    private fun runExe(
        current: TestRun,
        step: Step,
        s: WorkflowSettings,
        exe: Path,
        exeDir: Path,
        copies: List<Pair<Path, Path>>,
        expected: List<Pair<String, Path>>,
        mode: RunMode,
        autoAnswers: List<AutoAnswer>?,
        knownQuestions: List<AutoAnswer>,
        forcedAnswers: List<AutoAnswer> = emptyList(),
        extraNote: String = "",
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
        val label = "${step.type.label} – ${step.activeFiles.size} ${if (step.activeFiles.size == 1) "bestand" else "bestanden"} (${step.fileNames})"
        exeRunner.start(exe, exeDir, label, mode, env, notes, autoAnswers, knownQuestions, forcedAnswers) { code ->
            Current.runAs(input.user.ifEmpty { null }, collection) {
            try {
                val missing = if (code == 0) expected.filter { !it.second.present(allowEmpty = it.first == "tekst") } else emptyList()
                // Buiten het slot: dit leest een paar pagina's van ZCBS.
                val note = if (code == 0 && missing.isEmpty() && step.type == StepType.IMAGES) imagesNote(step) else ""
                if (code == 0 && missing.isEmpty() && autoAnswers == null && step.type == StepType.IMAGES) learnPassword(exeRunner.typedSecretAnswers())
                synchronized(this) {
                    if (code != 0) {
                        step.status = StepStatus.FAILED
                        step.message = "Gestopt met exitcode ${code ?: "onbekend"}. Controleer de uitvoer; zet de stap handmatig op klaar als het toch goed ging."
                    } else if (missing.isEmpty()) {
                        step.status = StepStatus.DONE
                        step.message = "Klaar: alle ${expected.size} verwachte bestanden zijn aangemaakt.$note$extraNote"
                        // Ook na automatisch uitvoeren ziet Carla eerst wat er gemaakt is en gaat ze zelf verder (de controle in de flow).
                    } else {
                        step.status = StepStatus.FAILED
                        step.message = "Het programma is klaar (exitcode 0), maar er ontbreken bestanden: " +
                            names(missing.map { "${it.first} ${it.second}" }, 6) + ". Bekijk het log; zet de stap handmatig op klaar als het toch goed ging."
                    }
                }
            } catch (e: Exception) {
                // Een stap mag nooit op "bezig" blijven staan als het afronden mislukt.
                log.warn("Afronden van stap {} ({}) mislukt", step.id, step.type, e)
                synchronized(this) {
                    step.status = StepStatus.FAILED
                    step.message = "Het programma is klaar, maar het afronden is mislukt: ${e.message ?: e.javaClass.simpleName}. Bekijk het log; zet de stap handmatig op klaar als het toch goed ging."
                }
            }
            }
        }
    }

    /** Wat images.exe heeft gedaan met uploaden en records aanmaken, en of die records nu in ZCBS staan. */
    private fun imagesNote(step: Step): String {
        val given = exeRunner.givenAnswers()
        fun answer(question: String) = given.entries.firstOrNull { it.key.contains(question, ignoreCase = true) }?.value
        val uploaded = yes(answer(ExeAnswers.UPLOAD_QUESTION))
        val records = yes(answer(ExeAnswers.RECORDS_QUESTION))
        return buildList {
            if (!uploaded) add(" Let op: de afbeeldingen zijn niet naar de server geüpload.")
            if (!records) add(" Let op: er zijn geen records aangemaakt in ZCBS.")
            else if (itemEditService.loggedIn()) {
                val missing = runCatching { itemEditService.missingInZcbs(step.files.map { it.number }) }.getOrNull()
                when {
                    missing == null -> add(" De records in ZCBS konden niet worden gecontroleerd.")
                    missing.isEmpty() -> add(" De ${step.files.size} records staan in ZCBS.")
                    else -> add(" Let op: deze records staan nog niet in ZCBS: ${names(missing)}.")
                }
            }
        }.joinToString("")
    }

    private fun upload(step: Step, s: WorkflowSettings) {
        val l = Layout(s, input)
        val items = step.activeFiles.flatMap {
            listOf(
                UploadItem(l.ocr(it)!!, s.remoteOcrDir(), "${it.number}.txt"),
                UploadItem(l.pdf(it)!!, s.remotePdfDir(), "${it.number}.pdf"),
            )
        }
        // Bij opnieuw proberen alleen wat nog niet is geüpload; wat er al staat blijft ongemoeid.
        val pending = items.filter { it.remotePath !in step.uploaded }
        step.status = StepStatus.RUNNING
        step.message = "Bezig met uploaden van ${pending.size} bestanden naar ${s.ftpHost}..."
        val results = ftpService.upload(pending)
        results.filter { it.ok }.forEach { step.uploaded += it.item.remotePath }
        val failed = results.firstOrNull { !it.ok }
        if (failed == null && step.activeFiles.all { f -> items.filter { it.local.nameWithoutExt() == f.number }.all { it.remotePath in step.uploaded } }) {
            step.status = StepStatus.DONE
            step.message = "${items.size} bestanden staan op de server en zijn gecontroleerd: ${s.remoteOcrDir()} en ${s.remotePdfDir()}."
        } else {
            step.status = StepStatus.FAILED
            step.message = "Upload gestopt na ${results.count { it.ok }} van ${pending.size} bestanden: ${failed?.message ?: "niet alles geüpload"}. " +
                "De bestanden die wel staan, blijven staan en worden bij opnieuw proberen overgeslagen."
        }
    }

    /**
     * Eerst de back-up van de 100pct en de pdf's (met hun recordnummer) en pas daarna het verwijderen, en alleen wat bij deze
     * flow hoort: de mappen van de programma's en de kopieën in de invoermap. De originelen op K: blijven staan. Als laatste komt
     * er in het verwerkt-log te staan wat er klaar is.
     */
    private fun cleanup(current: TestRun, step: Step, s: WorkflowSettings) {
        val l = Layout(s, input)
        step.status = StepStatus.RUNNING
        var copied = 0
        step.files.forEach { f ->
            copied += backupCopy(l.img100(f)!!, l.backup!!)
            if (!f.isImage) copied += backupCopy(current.workDir.resolve(f.newName), l.backupPdf!!)
        }
        var removed = 0
        removed += deleteTree(l.p2zOutput()!!, l.p2z!!)
        listOf("small", "large", "100pct").forEach { removed += deleteTree(l.img!!.resolve(it), l.img) }
        step.files.forEach { f ->
            listOf(l.imgIn(f)!!, l.p2zInput(f)!!, current.workDir.resolve(f.newName), f.original).forEach { if (Files.deleteIfExists(it)) removed++ }
        }
        l.img!!.listDirectoryEntries("log-*.txt").filter { it.isRegularFile() }.forEach { Files.delete(it); removed++ }
        runCatching { Files.deleteIfExists(current.workDir) } // alleen als de map leeg is
        sourceFiles.forget(step.files.map { it.originalName })
        val now = LocalDateTime.now()
        val runId = now.format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"))
        processed.add(step.files.map { ProcessedFile(runId, now, itemEditService.user.orEmpty(), s.collection, it.number, it.by, it.folder, it.originalName) })
        step.status = StepStatus.DONE
        val backups = if (step.files.any { !it.isImage }) "${l.backup} en ${l.backupPdf}" else "${l.backup}"
        step.message = "Back-up gemaakt in $backups ($copied nieuw), daarna $removed bestanden en mappen opgeruimd. " +
            "De ${step.files.size} bestanden staan in het verwerkt-log."
    }

    /** Kopieert [source] naar [dir] en controleert de grootte. Staat hetzelfde bestand er al (opnieuw proberen), dan gebeurt er niets. @return 1 als er gekopieerd is. */
    private fun backupCopy(source: Path, dir: Path): Int {
        val wanted = dir.resolve(source.name)
        val target = when {
            !Files.exists(wanted) -> wanted
            Files.mismatch(source, wanted) == -1L -> return 0
            else -> uniqueTarget(dir, source.name)
        }
        Files.copy(source, target)
        check(Files.size(target) == Files.size(source)) { "Back-up van ${source.name} is niet even groot als het origineel; er is niets verwijderd." }
        return 1
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
        val s = settings()
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
                number = file.number,
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
        val s = settings()
        if (step.type == StepType.CLEANUP && current.steps.first { it.type == StepType.UPLOAD }.status == StepStatus.SKIPPED && current.files.any { !it.isImage }) {
            add("De upload is overgeslagen. Na het opruimen zijn de tekst en pdf's weg, tenzij je ze zelf op de server hebt gezet.")
        }
    }

    private fun plan(current: TestRun, step: Step, s: WorkflowSettings): List<String> {
        val l = Layout(s, input)
        val n = step.files.size
        val unset = "(niet ingesteld)"
        return when (step.type) {
            StepType.RENAME -> listOf("Maakt een kopie van ${step.file.originalName} als ${current.workDir.resolve(step.file.newName)}.", "Het origineel blijft staan.")
            StepType.PDF2ZCBS -> listOf(
                "Zet ${step.activeFiles.size} bestand(en) uit ${current.workDir} in ${l.p2z ?: unset}.",
                "Start ${l.p2zExe() ?: unset} één keer in die map.",
                "Verwacht per bestand in ${l.p2zOutput() ?: unset}: tekst, pdf, small en large.",
            )
            StepType.IMAGES -> listOf(
                "Zet de large-jpg's van pdf2zcbs uit ${l.p2zOutput()?.resolve("large") ?: unset} (en de gekozen afbeeldingen zelf) in ${l.img?.resolve("images") ?: unset}.",
                "Start ${l.imgExe() ?: unset} één keer. Handmatig typ je zelf de antwoorden; de app stelt de vaste antwoorden voor.",
                "Automatisch geeft de app de vaste antwoorden: uploaden naar de server ja, records aanmaken ja, collectie ${s.collection}, resolutie ${s.imagesResolution()}.",
                "Er wordt nooit overschreven en niets verwijderd: de vragen daarover beantwoordt de app zelf met nee (ook bij handmatig), zodat de mappen small, large, 100pct en images blijven staan.",
                "Verwacht per bestand in ${l.img ?: unset}: small, large en 100pct.",
            )
            StepType.UPLOAD -> listOf(
                "Verbindt met ftp://${s.ftpHost.ifBlank { unset }} als ${s.ftpUser.ifBlank { unset }}.",
                "Tekst: ${step.activeFiles.size} bestand(en) naar ${s.remoteOcrDir()}",
                "Pdf: ${step.activeFiles.size} bestand(en) naar ${s.remotePdfDir()}",
                "Eerst een controle: de mappen bestaan en geen enkel bestand bestaat al. Er wordt niets overschreven, aangemaakt of verwijderd.",
            )
            StepType.CLEANUP -> listOf(
                "Kopieert de $n 100pct-afbeeldingen naar ${s.backup100pctFolder().ifBlank { unset }}.",
                "Kopieert de ${step.files.count { !it.isImage }} pdf's met hun recordnummer naar ${s.backupPdfFolder().ifBlank { unset }}.",
                "Verwijdert daarna ${l.p2zOutput() ?: unset}, ${l.img?.resolve("small") ?: unset}, large en 100pct, de bronbestanden in ${l.img?.resolve("images") ?: unset}, log-*.txt in ${l.img ?: unset} en de $n kopieën in de invoermap.",
                "De bestanden op K: blijven staan. De tekst en pdf's in output zijn dan weg: die moeten op de server staan (stap Upload).",
                "Zet de bestanden in het verwerkt-log, zodat je later kunt zien wat al op het archief staat.",
            )
        }
    }

    private fun checkGroups(current: TestRun, file: TestFile, s: WorkflowSettings): List<CheckGroupView> {
        val l = Layout(s, input)
        fun stepOf(type: StepType) = current.steps.first { it.type == type && it.files.contains(file) }
        val cleaned = stepOf(StepType.CLEANUP).status == StepStatus.DONE

        fun local(type: StepType, label: String, path: Path?): CheckView {
            val status = stepOf(type).status
            val state = when {
                status == StepStatus.PENDING -> "PENDING"
                path.present(allowEmpty = label == "tekst") -> "OK"
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
            if (!cleaned && !file.isImage) {
                add(CheckGroupView("pdf2zcbs", listOf(
                    local(StepType.PDF2ZCBS, "tekst", l.ocr(file)),
                    local(StepType.PDF2ZCBS, "pdf", l.pdf(file)),
                    local(StepType.PDF2ZCBS, "small", l.p2zSmall(file)),
                    local(StepType.PDF2ZCBS, "large", l.p2zLarge(file)),
                )))
            }
            if (!cleaned) {
                add(CheckGroupView("images", listOf(
                    local(StepType.IMAGES, "small", l.imgSmall(file)),
                    local(StepType.IMAGES, "large", l.imgLarge(file)),
                    local(StepType.IMAGES, "100pct", l.img100(file)),
                )))
            }
            if (!file.isImage) add(CheckGroupView("server", listOf(
                remote("tekst", s.remoteOcrDir() + "/" + file.number + ".txt"),
                remote("pdf", s.remotePdfDir() + "/" + file.number + ".pdf"),
            )))
            add(CheckGroupView("back-up", listOfNotNull(
                run {
                    val st = stepOf(StepType.CLEANUP).status
                    val path = l.backupOf(file)
                    CheckView("100pct", when { path.present() -> "OK"; st == StepStatus.PENDING -> "PENDING"; else -> "MISSING" }, path?.toString().orEmpty())
                },
                run {
                    if (file.isImage) return@run null
                    val st = stepOf(StepType.CLEANUP).status
                    val path = l.backupPdfOf(file)
                    CheckView("pdf", when { path.present() -> "OK"; st == StepStatus.PENDING -> "PENDING"; else -> "MISSING" }, path?.toString().orEmpty())
                },
            )))
        }
    }

    /** Een bestand van de uitvoer van pdf2zcbs of images.exe om te bekijken: alleen voor de nummers van de huidige flow en alleen als het bestaat. */
    @Synchronized
    fun resultFile(kind: String, number: String): Path? {
        val current = run ?: return null
        val file = current.files.firstOrNull { it.number == number } ?: return null
        val l = Layout(settings(), input)
        val path = when (kind) {
            "pdf" -> l.pdf(file)
            "tekst" -> l.ocr(file)
            "p2z-small" -> l.p2zSmall(file)
            "p2z-large" -> l.p2zLarge(file)
            "small" -> l.imgSmall(file)
            "large" -> l.imgLarge(file)
            "100pct" -> l.img100(file)
            else -> null
        }
        return path?.takeIf { it.present(allowEmpty = kind == "tekst") }
    }

    /**
     * De bestanden die pdf2zcbs.exe of images.exe voor de gebruiker heeft gemaakt, om te controleren: per bestand de soort, de grootte en (bij een afbeelding) de afmeting.
     * Een leeg tekstbestand (een pagina zonder herkende tekst) staat er met een opmerking bij.
     */
    @Synchronized
    fun producedFiles(type: StepType): List<ProducedFile> {
        val current = run ?: return emptyList()
        val kinds = when (type) {
            StepType.PDF2ZCBS -> listOf("tekst" to "tekst", "pdf" to "pdf", "p2z-small" to "small", "p2z-large" to "large")
            StepType.IMAGES -> listOf("small" to "small", "large" to "large", "100pct" to "100pct")
            else -> return emptyList()
        }
        val files = if (type == StepType.PDF2ZCBS) current.files.filter { !it.isImage } else current.files
        return files.flatMap { f ->
            kinds.mapNotNull { (kind, label) ->
                val path = resultFile(kind, f.number) ?: return@mapNotNull null
                val bytes = runCatching { Files.size(path) }.getOrDefault(0)
                val resolution = if (kind == "tekst" || kind == "pdf") "" else imageSize(path)
                val note = when {
                    kind == "tekst" && bytes == 0L -> "leeg: geen tekst gevonden"
                    kind != "tekst" && kind != "pdf" && resolution.isEmpty() -> "geen geldige afbeelding"
                    else -> ""
                }
                ProducedFile(f.number, kind, label, readableSize(bytes), resolution, note)
            }
        }
    }

    private fun readableSize(bytes: Long): String = when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${(bytes + 512) / 1024} kB"
        else -> "%.1f MB".format(java.util.Locale.forLanguageTag("nl"), bytes / 1024.0 / 1024.0)
    }

    /** "1654 × 2339 px" voor een afbeelding, zonder haar helemaal te lezen; leeg als dat niet lukt. */
    private fun imageSize(path: Path): String = runCatching {
        javax.imageio.ImageIO.createImageInputStream(path.toFile()).use { stream ->
            val readers = javax.imageio.ImageIO.getImageReaders(stream)
            if (!readers.hasNext()) return@runCatching ""
            val reader = readers.next()
            try {
                reader.input = stream
                "${reader.getWidth(0)} × ${reader.getHeight(0)} px"
            } finally { reader.dispose() }
        }
    }.getOrDefault("")

    private companion object {
        /** Wat in de invoermap als gekozen bestand telt: pdf's en jpg's (een png is bij het kiezen een jpg geworden). */
        val CHOSEN_EXTENSIONS = setOf("pdf", "jpg", "jpeg")
        const val LEFTOVERS_MESSAGE = "Er staan nog bestanden van een eerdere run in je invoermap of in de mappen van pdf2zcbs.exe of images.exe. Verplaats of verwijder ze eerst (of kies bij je invoermap voor \"horen erbij\")."
    }
}
