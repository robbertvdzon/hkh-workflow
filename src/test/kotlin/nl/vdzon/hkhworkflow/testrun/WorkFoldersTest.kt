package nl.vdzon.hkhworkflow.testrun

import kotlin.test.Test
import kotlin.test.assertEquals

class WorkFoldersTest {
    @Test
    fun `een ZCBS-naam wordt een bruikbare mapnaam`() {
        assertEquals("Carla van Noort", WorkFolders.folderName("Carla van Noort"))
        assertEquals("Jos Wijnen-Smit", WorkFolders.folderName(" Jos Wijnen-Smit "))
        assertEquals("Zoë", WorkFolders.folderName("Zoë"))
    }

    @Test
    fun `tekens die niet in een mapnaam horen verdwijnen en een pad uit de map is niet mogelijk`() {
        assertEquals("a_b_c", WorkFolders.folderName("a/b\\c"))
        assertEquals("_", WorkFolders.folderName("<"), "alleen een teken dat niet mag")
        assertEquals("gebruiker", WorkFolders.folderName(".."))
        assertEquals("gebruiker", WorkFolders.folderName("  "))
        assertEquals("x", WorkFolders.folderName("..x.."))
    }

    @Test
    fun `elke gebruiker heeft per collectie eigen mappen onder de werkmap`() {
        val dir = java.nio.file.Files.createTempDirectory("werkmap")
        val settings = nl.vdzon.hkhworkflow.settings.SettingsService(
            tools.jackson.databind.ObjectMapper(), nl.vdzon.hkhworkflow.settings.WorkflowProperties(settingsFile = dir.resolve("s.json").toString()),
        )
        settings.save(nl.vdzon.hkhworkflow.settings.WorkflowSettings(testFolder = dir.toString()))
        val carla = WorkFolders(settings, "Carla", "archief")
        assertEquals(dir.resolve("Carla/archief"), carla.root())
        assertEquals(dir.resolve("Carla/archief/invoer"), carla.invoer())
        assertEquals(dir.resolve("Carla/archief/werk"), carla.werk())
        assertEquals(dir.resolve("Carla/archief/pdf2zcbs"), carla.pdf2zcbs())
        assertEquals(dir.resolve("Carla/archief/images"), carla.images())
        assertEquals(dir.resolve("Carla/archief/resten"), carla.resten())
        assertEquals(dir.resolve("Jelly/beeldbank/invoer"), WorkFolders(settings, "Jelly", "beeldbank").invoer())
        assertEquals(dir.resolve("gebruiker/archief/invoer"), WorkFolders(settings, "", "archief").invoer(), "zonder naam (de beheerder)")
        carla.ensureAll()
        assertEquals(true, java.nio.file.Files.isDirectory(dir.resolve("Carla/archief/invoer")))
        assertEquals(true, java.nio.file.Files.isDirectory(dir.resolve("Carla/archief/pdf2zcbs")))
        assertEquals(true, java.nio.file.Files.isDirectory(dir.resolve("Carla/archief/images")))
    }
}
