package nl.vdzon.hkhworkflow.flow.internal

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.name
import nl.vdzon.hkhworkflow.settings.WorkflowSettings

/** Waar alle bestanden staan die de stappen aanmaken of nodig hebben. */
class FlowLayout(val s: WorkflowSettings, folders: WorkFolders) {
    /** De mappen van deze gebruiker voor de programma's; daarin staat een kopie van het programma uit de hoofdmap. */
    val p2z: Path? = folders.pdf2zcbs()
    val img: Path? = folders.images()

    /** Het programma zelf, in de map van de gebruiker. */
    fun p2zExe(): Path? = p2z?.resolve(Path.of(s.pdf2zcbsExe).fileName)
    fun imgExe(): Path? = img?.resolve(Path.of(s.imagesExe).fileName)
    val backup: Path? = s.backup100pctFolder().trim().takeIf { it.isNotEmpty() }?.let { Path.of(it) }
    val backupPdf: Path? = s.backupPdfFolder().trim().takeIf { it.isNotEmpty() }?.let { Path.of(it) }

    fun p2zOutput(): Path? = p2z?.resolve("output")
    fun ocr(f: FlowFile): Path? = p2zOutput()?.resolve("ocr")?.resolve("${f.number}.txt")
    fun pdf(f: FlowFile): Path? = p2zOutput()?.resolve("pdf")?.resolve("${f.number}.pdf")
    fun p2zSmall(f: FlowFile): Path? = p2zOutput()?.resolve("small")?.resolve("${f.number}.jpg")
    fun p2zLarge(f: FlowFile): Path? = p2zOutput()?.resolve("large")?.resolve("${f.number}.jpg")
    fun imgIn(f: FlowFile): Path? = img?.resolve("images")?.resolve("${f.number}.jpg")
    fun imgSmall(f: FlowFile): Path? = img?.resolve("small")?.resolve("${f.number}.jpg")
    fun imgLarge(f: FlowFile): Path? = img?.resolve("large")?.resolve("${f.number}.jpg")
    fun img100(f: FlowFile): Path? = img?.resolve("100pct")?.resolve("${f.number}.jpg")
    fun backupOf(f: FlowFile): Path? = backup?.resolve("${f.number}.jpg")
    fun backupPdfOf(f: FlowFile): Path? = backupPdf?.resolve("${f.number}.pdf")
    fun p2zInput(f: FlowFile): Path? = p2z?.resolve("${f.number}.pdf")
}

/** Bestaat het bestand en is het niet leeg? Een tekstbestand mag leeg zijn: een pagina zonder tekst geeft een lege tekst (dat zie je dan bij het controleren). */
internal fun Path?.present(allowEmpty: Boolean = false): Boolean =
    this != null && Files.isRegularFile(this) && (allowEmpty || runCatching { Files.size(this) > 0 }.getOrDefault(false))

internal fun names(list: List<String>, max: Int = 8): String =
    list.take(max).joinToString() + if (list.size > max) " en ${list.size - max} andere" else ""

/** De naam van het bestand zonder de extensie. */
internal fun Path.nameWithoutExt(): String = name.substringBeforeLast('.')
