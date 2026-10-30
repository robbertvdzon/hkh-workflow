package nl.vdzon.hkhworkflow.items

import nl.vdzon.hkhworkflow.settings.WorkflowProperties
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RememberLoginTest {
    private fun remember(dir: java.nio.file.Path) = RememberLogin(WorkflowProperties(settingsFile = dir.resolve("settings.json").toString()))

    @Test
    fun `naam en wachtwoord komen versleuteld terug, ook na een herstart`() {
        val dir = Files.createTempDirectory("remember")
        val value = remember(dir).encrypt("Carla", "gehéim wachtwoord")
        assertTrue("gehéim" !in value && "Carla" !in value)
        // een nieuwe instantie (herstart van de app) leest dezelfde sleutel uit het bestand
        assertEquals("Carla" to "gehéim wachtwoord", remember(dir).decrypt(value))
    }

    @Test
    fun `een aangepast of vreemd cookie wordt niet geaccepteerd`() {
        val dir = Files.createTempDirectory("remember")
        val r = remember(dir)
        val value = r.encrypt("Carla", "abc")
        assertNull(r.decrypt(value.dropLast(3) + "AAA"))
        assertNull(r.decrypt("onzin"))
        assertNull(remember(Files.createTempDirectory("andere")).decrypt(value))
    }
}
