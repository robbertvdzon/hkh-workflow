package nl.vdzon.hkhworkflow.testrun

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.readText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.jupiter.api.io.TempDir

class ProgramFilesTest {
    @TempDir
    lateinit var dir: Path

    private fun master(): Path {
        val m = Files.createDirectories(dir.resolve("hoofdmap"))
        Files.writeString(m.resolve("images.exe"), "programma")
        Files.writeString(m.resolve("images.ini"), "instelling")
        Files.writeString(m.resolve("i_view32.exe"), "viewer")
        Files.createDirectories(m.resolve("plugins")).resolve("postscript.dll").let { Files.writeString(it, "dll") }
        // Wat het programma zelf maakt of verwerkt, hoort niet bij het programma.
        Files.createDirectories(m.resolve("images")).resolve("1.jpg").let { Files.writeString(it, "bron") }
        Files.createDirectories(m.resolve("small")).resolve("1.jpg").let { Files.writeString(it, "klein") }
        Files.writeString(m.resolve("log-261019-2000.txt"), "log")
        Files.writeString(m.resolve("manual-images.pdf"), "handleiding")
        return m
    }

    @Test
    fun `het programma met zijn ini en plugins komt in de map van de gebruiker, de gegevens niet`() {
        val m = master()
        val target = dir.resolve("carla/archief/images")
        val copied = ProgramFiles.sync(m.resolve("images.exe").toString(), target)
        assertEquals(4, copied)
        assertEquals("programma", target.resolve("images.exe").readText())
        assertEquals("instelling", target.resolve("images.ini").readText())
        assertEquals("dll", target.resolve("plugins/postscript.dll").readText())
        listOf("images", "small", "log-261019-2000.txt", "manual-images.pdf").forEach { assertFalse(target.resolve(it).exists(), it) }
    }

    @Test
    fun `een tweede keer kopieert alleen wat veranderd is en er wordt nooit iets verwijderd`() {
        val m = master()
        val target = dir.resolve("carla/archief/images")
        ProgramFiles.sync(m.resolve("images.exe").toString(), target)
        assertEquals(0, ProgramFiles.sync(m.resolve("images.exe").toString(), target))

        Files.writeString(m.resolve("images.ini"), "andere instelling")
        Files.createDirectories(target.resolve("small")).resolve("9.jpg").let { Files.writeString(it, "van de gebruiker") }
        assertEquals(1, ProgramFiles.sync(m.resolve("images.exe").toString(), target))
        assertEquals("andere instelling", target.resolve("images.ini").readText())
        assertTrue(target.resolve("small/9.jpg").exists(), "de gegevens van de gebruiker blijven staan")
    }

    @Test
    fun `zonder programma, of als de map al de hoofdmap is, gebeurt er niets`() {
        val m = master()
        assertEquals(0, ProgramFiles.sync("", dir.resolve("x")))
        assertEquals(0, ProgramFiles.sync(m.resolve("images.exe").toString(), m))
        assertFalse(dir.resolve("x").exists())
    }
}
