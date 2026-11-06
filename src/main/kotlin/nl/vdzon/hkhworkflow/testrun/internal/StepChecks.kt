package nl.vdzon.hkhworkflow.testrun.internal

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

/** De controles vooraf: wat houdt een stap nu tegen? Er gebeurt pas iets als er niets tegenhoudt. */
class StepChecks {
    fun problems(current: TestRun, step: Step, s: WorkflowSettings, input: WorkFolders): List<String> {
        val l = FlowLayout(s, input)
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
                if (s.ftpPassword.isEmpty()) add("Ftp-wachtwoord is niet ingesteld (Instellingen, bij FTP-wachtwoord).")
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
}
