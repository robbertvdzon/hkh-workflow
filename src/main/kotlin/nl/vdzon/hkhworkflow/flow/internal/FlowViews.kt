package nl.vdzon.hkhworkflow.flow.internal

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
import nl.vdzon.hkhworkflow.ftp.FtpService
import nl.vdzon.hkhworkflow.ftp.UploadItem
import nl.vdzon.hkhworkflow.items.Current
import nl.vdzon.hkhworkflow.items.ZcbsSession
import nl.vdzon.hkhworkflow.settings.SettingsService
import nl.vdzon.hkhworkflow.settings.WorkflowSettings
import org.slf4j.LoggerFactory

/** Wat de gebruiker te zien krijgt van een doorloop. Alleen lezen: hier verandert niets aan de doorloop. */
class FlowViews(private val input: WorkFolders, private val ftpService: FtpService, private val checks: StepChecks) {

    fun view(current: FlowRun, s: WorkflowSettings): FlowRunView {
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
                problems = if (isNext && step.status == StepStatus.PENDING) checks.problems(current, step, s, input) else emptyList(),
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
        return FlowRunView(
            startNumber = current.startNumber,
            inputFolder = current.inputFolder.toString(),
            workDir = current.workDir.toString(),
            fileCount = current.files.size,
            doneCount = current.steps.count { it.finished },
            totalCount = current.steps.size,
            rows = rows,
            stepTypes = StepType.entries.map { it.label },
            next = next?.let(::toView),
        )
    }

    private fun warnings(current: FlowRun, step: Step): List<String> = buildList {
        if (step.type == StepType.CLEANUP && current.steps.first { it.type == StepType.UPLOAD }.status == StepStatus.SKIPPED && current.files.any { !it.isImage }) {
            add("De upload is overgeslagen. Na het opruimen zijn de tekst en pdf's weg, tenzij je ze zelf op de server hebt gezet.")
        }
    }

    private fun plan(current: FlowRun, step: Step, s: WorkflowSettings): List<String> {
        val l = FlowLayout(s, input)
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

    private fun checkGroups(current: FlowRun, file: FlowFile, s: WorkflowSettings): List<CheckGroupView> {
        val l = FlowLayout(s, input)
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
    fun resultFile(current: FlowRun, s: WorkflowSettings, kind: String, number: String): Path? {
        val file = current.files.firstOrNull { it.number == number } ?: return null
        val l = FlowLayout(s, input)
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
    fun producedFiles(current: FlowRun, s: WorkflowSettings, type: StepType): List<ProducedFile> {
        val kinds = when (type) {
            StepType.PDF2ZCBS -> listOf("tekst" to "tekst", "pdf" to "pdf", "p2z-small" to "small", "p2z-large" to "large")
            StepType.IMAGES -> listOf("small" to "small", "large" to "large", "100pct" to "100pct")
            else -> return emptyList()
        }
        val files = if (type == StepType.PDF2ZCBS) current.files.filter { !it.isImage } else current.files
        return files.flatMap { f ->
            kinds.mapNotNull { (kind, label) ->
                val path = resultFile(current, s, kind, f.number) ?: return@mapNotNull null
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
}
