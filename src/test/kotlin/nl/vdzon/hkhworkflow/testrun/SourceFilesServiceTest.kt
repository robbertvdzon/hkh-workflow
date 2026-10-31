package nl.vdzon.hkhworkflow.testrun

import java.nio.file.Files
import java.nio.file.Path
import java.time.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import nl.vdzon.hkhworkflow.items.ItemEditService
import nl.vdzon.hkhworkflow.settings.SettingsService
import nl.vdzon.hkhworkflow.settings.WorkflowProperties
import nl.vdzon.hkhworkflow.settings.WorkflowSettings
import org.junit.jupiter.api.io.TempDir
import tools.jackson.databind.ObjectMapper

class SourceFilesServiceTest {
    @TempDir
    lateinit var dir: Path

    private lateinit var processed: ProcessedLog

    private fun service(): SourceFilesService {
        val ria = Files.createDirectories(dir.resolve("k/scans ria"))
        val jelly = Files.createDirectories(dir.resolve("k/scans jelly"))
        Files.createDirectories(ria.resolve("map57"))
        Files.createDirectories(ria.resolve("map58"))
        Files.createDirectories(jelly.resolve("raadhuis"))
        listOf("scan_2.pdf", "scan_10.pdf", "scan_1.pdf").forEach { Files.writeString(ria.resolve("map57/$it"), "ria $it") }
        Files.writeString(ria.resolve("map57/notitie.txt"), "geen pdf")
        Files.writeString(jelly.resolve("raadhuis/voorgevel.pdf"), "jelly")
        Files.writeString(ria.resolve("los.pdf"), "direct in de bronmap")
        val inbox = Files.createDirectories(dir.resolve("werkmap"))
        Files.createDirectories(dir.resolve("werkmap/Test/archief/invoer"))
        val props = WorkflowProperties(settingsFile = dir.resolve("s.json").toString(), processedLog = dir.resolve("verwerkt.tsv").toString())
        val settings = SettingsService(ObjectMapper(), props)
        settings.save(WorkflowSettings(testFolder = inbox.toString(), sourceFolders = "Jelly=$jelly\nRia=$ria"))
        processed = ProcessedLog(settings, props)
        return SourceFilesService(settings, processed, WorkFolders(settings, "Test", "archief"))
    }

    @Test
    fun `afbeeldingen staan in de lijst en zijn net als pdfs te kiezen, een png wordt een jpg`() {
        val s = service()
        val ria = Path.of(s.roots().first { it.name == "Ria" }.path)
        javax.imageio.ImageIO.write(java.awt.image.BufferedImage(40, 30, java.awt.image.BufferedImage.TYPE_INT_RGB), "png", ria.resolve("map58/kaart.png").toFile())
        javax.imageio.ImageIO.write(java.awt.image.BufferedImage(20, 10, java.awt.image.BufferedImage.TYPE_INT_RGB), "jpg", ria.resolve("map58/foto.JPG").toFile())
        Files.writeString(ria.resolve("map58/klad.docx"), "negeren")
        val files = s.browse("Ria", "map58").files
        assertEquals(listOf("foto.JPG", "kaart.png"), files.map { it.name })
        assertEquals(listOf("JPG", "PNG"), files.map { it.kindLabel })
        val result = s.copy("Ria", "map58", listOf("kaart.png", "foto.JPG"))
        assertEquals(listOf("kaart.png", "foto.JPG"), result.copied)
        val folder = s.browse("Ria", "map58") // de lijst blijft hetzelfde
        assertEquals(2, folder.files.size)
        val inbox = dir.resolve("werkmap/Test/archief/invoer")
        assertEquals(setOf("kaart.jpg", "foto.JPG"), Files.list(inbox).use { it.map { p -> p.fileName.toString() }.toList().filter { n -> n.endsWith("pg") || n.endsWith("JPG") }.toSet() })
        // De png is nu echt een jpg, 40 × 30.
        val converted = javax.imageio.ImageIO.read(inbox.resolve("kaart.jpg").toFile())
        assertEquals(40 to 30, converted.width to converted.height)
        assertEquals("Ria", s.origin("kaart.jpg")?.by)
        // Een tweede keer kiezen slaat ze over.
        assertEquals(listOf("kaart.png (staat al in de invoermap)", "foto.JPG (staat al in de invoermap)"), s.copy("Ria", "map58", listOf("kaart.png", "foto.JPG")).skipped)
    }

    @Test
    fun `het aantal pagina's en de afmeting komen uit de bestanden zelf`() {
        val s = service()
        val ria = Path.of(s.roots().first { it.name == "Ria" }.path)
        org.apache.pdfbox.pdmodel.PDDocument().use { doc ->
            repeat(3) { doc.addPage(org.apache.pdfbox.pdmodel.PDPage()) }
            doc.save(ria.resolve("map58/drie.pdf").toFile())
        }
        org.apache.pdfbox.pdmodel.PDDocument().use { doc -> doc.addPage(org.apache.pdfbox.pdmodel.PDPage()); doc.save(ria.resolve("map58/een.pdf").toFile()) }
        Files.writeString(ria.resolve("map58/kapot.pdf"), "dit is geen pdf")
        javax.imageio.ImageIO.write(java.awt.image.BufferedImage(40, 30, java.awt.image.BufferedImage.TYPE_INT_RGB), "png", ria.resolve("map58/kaart.png").toFile())
        val details = s.details("Ria", "map58")
        assertEquals("3 pagina's", details["drie.pdf"])
        assertEquals("1 pagina", details["een.pdf"])
        assertEquals("40 × 30 px", details["kaart.png"])
        assertEquals("kan niet worden gelezen", details["kapot.pdf"])
    }

    @Test
    fun `alleen pdfs en afbeeldingen uit de bronmap zijn te bekijken`() {
        val s = service()
        assertTrue(s.fileToView("Ria", "map57", "scan_1.pdf").toString().endsWith("scan_1.pdf"))
        assertFailsWith<IllegalArgumentException> { s.fileToView("Ria", "map57", "notitie.txt") }
        assertFailsWith<IllegalArgumentException> { s.fileToView("Ria", "map57", "../los.pdf") }
        assertFailsWith<IllegalArgumentException> { s.fileToView("Ria", "../../k", "los.pdf") }
        assertFailsWith<IllegalArgumentException> { s.fileToView("Ria", "map57", "bestaat-niet.pdf") }
    }

    @Test
    fun `de mappen met scans komen uit de instellingen`() {
        assertEquals(listOf("Jelly", "Ria"), service().roots().map { it.name })
    }

    @Test
    fun `bladeren toont submappen en pdfs op natuurlijke volgorde`() {
        val s = service()
        val top = s.browse("Ria", "")
        assertEquals(listOf("map57", "map58"), top.dirs.map { it.name })
        assertEquals(listOf("los.pdf"), top.files.map { it.name })
        assertNull(top.parent)

        val map = s.browse("Ria", "map57")
        assertEquals(listOf("scan_1.pdf", "scan_2.pdf", "scan_10.pdf"), map.files.map { it.name }, "scan 2 komt voor scan 10")
        assertEquals(emptyList(), map.dirs)
        assertEquals(listOf("map57"), map.crumbs.map { it.name })
        assertEquals("", map.parent)
        assertEquals("map57", map.folderName)
    }

    @Test
    fun `bladeren kan niet buiten de bronmap en een onbekende of ontbrekende map geeft een duidelijke melding`() {
        val s = service()
        assertTrue(assertFailsWith<IllegalArgumentException> { s.browse("Ria", "../scans jelly") }.message!!.contains("buiten"))
        assertTrue(assertFailsWith<IllegalArgumentException> { s.browse("Ria", "../../invoer") }.message!!.contains("buiten"))
        assertTrue(assertFailsWith<IllegalArgumentException> { s.browse("Piet", "") }.message!!.contains("Onbekende map"))
        assertTrue(assertFailsWith<IllegalArgumentException> { s.browse("Ria", "bestaat-niet") }.message!!.contains("bereikbaar"))
    }

    @Test
    fun `kopieren zet de bestanden in de invoermap, onthoudt wie het scande en laat de originelen staan`() {
        val s = service()
        val result = s.copy("Ria", "map57", listOf("scan_1.pdf", "scan_2.pdf"))
        assertEquals(listOf("scan_1.pdf", "scan_2.pdf"), result.copied)
        assertEquals(emptyList(), result.skipped)
        assertEquals(listOf("herkomst.tsv", "scan_1.pdf", "scan_2.pdf"), Files.list(dir.resolve("werkmap/Test/archief/invoer")).use { it.map { p -> p.fileName.toString() }.sorted().toList() })
        assertEquals("Ria", s.origin("scan_1.pdf")?.by)
        assertTrue(s.origin("scan_1.pdf")!!.folder.endsWith("map57"))
        assertTrue(Files.exists(dir.resolve("k/scans ria/map57/scan_1.pdf")))
        assertNull(s.origin("onbekend.pdf"))
    }

    @Test
    fun `een bestand dat al in de invoermap staat wordt nooit overschreven`() {
        val s = service()
        Files.writeString(dir.resolve("werkmap/Test/archief/invoer/scan_1.pdf"), "EIGEN VERSIE")
        val result = s.copy("Ria", "map57", listOf("scan_1.pdf"))
        assertEquals(emptyList(), result.copied)
        assertEquals(1, result.skipped.size)
        assertEquals("EIGEN VERSIE", Files.readString(dir.resolve("werkmap/Test/archief/invoer/scan_1.pdf")))
    }

    @Test
    fun `alleen pdfs uit de gekozen map mogen gekopieerd worden`() {
        val s = service()
        assertFailsWith<IllegalArgumentException> { s.copy("Ria", "map57", listOf("../../s.json")) }
        assertFailsWith<IllegalArgumentException> { s.copy("Ria", "map57", listOf("notitie.txt")) }
        assertFailsWith<IllegalArgumentException> { s.copy("Ria", "map57", listOf("bestaat-niet.pdf")) }
        assertFailsWith<IllegalArgumentException> { s.copy("Ria", "../scans jelly", listOf("voorgevel.pdf")) }
        assertFailsWith<IllegalArgumentException> { s.copy("Ria", "map57", emptyList()) }
        assertFalse(Files.exists(dir.resolve("invoer/s.json")))
    }

    @Test
    fun `verwerkte bestanden zijn gemarkeerd en worden niet nog eens gekopieerd`() {
        val s = service()
        val folder = dir.resolve("k/scans ria/map57").toAbsolutePath().normalize().toString()
        processed.add(listOf(ProcessedFile("run1", LocalDateTime.of(2026, 10, 30, 10, 0), "Carla", "archief", "11935", "Ria", folder, "scan_1.pdf")))

        val files = s.browse("Ria", "map57").files
        assertEquals("11935", files.first { it.name == "scan_1.pdf" }.processed?.number)
        assertNull(files.first { it.name == "scan_2.pdf" }.processed)
        assertNull(s.browse("Ria", "").files.first().processed, "dezelfde naam in een andere map telt niet")

        val result = s.copy("Ria", "map57", listOf("scan_1.pdf", "scan_2.pdf"))
        assertEquals(listOf("scan_2.pdf"), result.copied)
        assertTrue(result.skipped.single().contains("al verwerkt als 11935"))
    }

    @Test
    fun `de gekozen kopieën weghalen laat de originelen en eigen bestanden staan`() {
        val s = service()
        s.copy("Ria", "map57", listOf("scan_1.pdf", "scan_2.pdf"))
        Files.writeString(dir.resolve("werkmap/Test/archief/invoer/zelf.pdf"), "door Carla zelf neergezet")
        assertEquals(2, s.removeAllCopies())
        assertFalse(Files.exists(dir.resolve("werkmap/Test/archief/invoer/scan_1.pdf")))
        assertTrue(Files.exists(dir.resolve("k/scans ria/map57/scan_1.pdf")))
        assertTrue(Files.exists(dir.resolve("werkmap/Test/archief/invoer/zelf.pdf")), "wat de app niet zelf kopieerde, blijft staan")
        assertNull(s.origin("scan_1.pdf"))
        assertFalse(Files.exists(dir.resolve("invoer/herkomst.tsv")), "zonder kopieën geen herkomstlijst")
        assertEquals(0, s.removeAllCopies())
    }

    @Test
    fun `natuurlijke volgorde zet getallen als getal en negeert hoofdletters`() {
        val sorted = listOf("Scan 10", "scan 9", "scan 100", "Scan 1b", "scan 1a").sortedWith(SourceFilesService.naturalOrder)
        assertEquals(listOf("scan 1a", "Scan 1b", "scan 9", "Scan 10", "scan 100"), sorted)
    }
}
