package nl.vdzon.hkhworkflow.testrun

import kotlin.test.Test
import kotlin.test.assertEquals

class InputFolderTest {
    @Test
    fun `een ZCBS-naam wordt een bruikbare mapnaam`() {
        assertEquals("Carla van Noort", InputFolder.folderName("Carla van Noort"))
        assertEquals("Jos Wijnen-Smit", InputFolder.folderName(" Jos Wijnen-Smit "))
        assertEquals("Zoë", InputFolder.folderName("Zoë"))
    }

    @Test
    fun `tekens die niet in een mapnaam horen verdwijnen en een pad uit de map is niet mogelijk`() {
        assertEquals("a_b_c", InputFolder.folderName("a/b\\c"))
        assertEquals("_", InputFolder.folderName("<"), "alleen een teken dat niet mag")
        assertEquals("gebruiker", InputFolder.folderName(".."))
        assertEquals("gebruiker", InputFolder.folderName("  "))
        assertEquals("x", InputFolder.folderName("..x.."))
        assertEquals("Resten_", InputFolder.folderName("Resten"), "de map voor resten is gereserveerd, ook op Windows en Mac waar hoofdletters niet tellen")
    }
}
