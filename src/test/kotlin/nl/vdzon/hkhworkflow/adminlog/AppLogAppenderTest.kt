package nl.vdzon.hkhworkflow.adminlog

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.LoggerContext
import ch.qos.logback.classic.spi.LoggingEvent
import java.nio.file.Files
import java.nio.file.Path
import java.util.Properties
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import tools.jackson.databind.json.JsonMapper

class AppLogAppenderTest {
    private val mapper = JsonMapper.builder().build()
    private val dir: Path = Files.createTempDirectory("app-log-appender")
    private val context = LoggerContext()

    private fun appender(url: String = "https://logboek.example") = AppLogAppender().also {
        it.context = context
        it.adminLogUrl = url
        it.directory = dir.toString()
        it.start()
    }

    private fun event(level: Level, logger: String, message: String, error: Throwable? = null) =
        LoggingEvent(logger, context.getLogger(logger), level, message, error, null)

    private fun written(): List<tools.jackson.databind.JsonNode> {
        AppLogSpool.active!!.flush()
        val file = dir.resolve(AppLogSpool.CURRENT)
        return if (Files.exists(file)) Files.readAllLines(file).map { mapper.readTree(it) } else emptyList()
    }

    @Test
    fun `zet INFO en hoger in de wachtrij met tijd, niveau, logger, bericht en versie`() {
        val a = appender()
        a.doAppend(event(Level.DEBUG, "nl.test.X", "te gedetailleerd"))
        a.doAppend(event(Level.INFO, "nl.test.X", "hallo"))
        a.doAppend(event(Level.ERROR, "nl.test.Y", "stuk", IllegalStateException("kapot")))

        val lines = written()
        assertEquals(2, lines.size)
        assertEquals("INFO", lines[0].path("level").asString())
        assertEquals("hallo", lines[0].path("message").asString())
        assertEquals("nl.test.X", lines[0].path("logger").asString())
        assertTrue(lines[0].path("time").asString().endsWith("Z"))
        assertEquals(AppVersion.current, lines[0].path("v").asString())
        assertEquals("ERROR", lines[1].path("level").asString())
        assertTrue(lines[1].path("message").asString().contains("IllegalStateException: kapot"), "de stacktrace hoort bij het bericht")
    }

    @Test
    fun `meldingen van het logboek zelf en geheimen blijven eruit`() {
        val a = appender()
        a.doAppend(event(Level.WARN, "nl.vdzon.hkhworkflow.adminlog.AppLogShipper", "geweigerd"))
        a.doAppend(event(Level.INFO, "nl.test.X", "Inloggen met password=geheim123 en wachtwoord: abc, Authorization: Bearer xyz.987"))

        val message = written().single().path("message").asString()
        assertFalse(message.contains("geheim123") || message.contains("abc,") || message.contains("xyz.987"), message)
        assertTrue(message.contains("password=***") && message.contains("wachtwoord: ***") && message.contains("Authorization: Bearer ***"), message)
    }

    @Test
    fun `regels met aanhalingstekens, newlines en een lange tekst blijven geldige json`() {
        val a = appender()
        a.doAppend(event(Level.INFO, "nl.test.X", "ze zei \"hoi\"\nregel twee\t\\ ${"x".repeat(20_000)}"))

        val message = written().single().path("message").asString()
        assertTrue(message.startsWith("ze zei \"hoi\"\nregel twee\t\\ "))
        assertEquals(AppLogAppender.MAX_MESSAGE, message.length)
    }

    @Test
    fun `zonder adres van het logboek doet de appender niets`() {
        val a = appender(url = "")
        assertFalse(a.isStarted)
    }

    @Test
    fun `het versienummer bevat de versie en de bouwtijd`() {
        assertEquals("ontwikkel", AppVersion.read(null))
        assertEquals("0.0.1-SNAPSHOT", AppVersion.read(Properties().apply { setProperty("build.version", "0.0.1-SNAPSHOT") }))
        assertEquals(
            "0.0.1-SNAPSHOT+20261031.1405",
            AppVersion.read(Properties().apply { setProperty("build.version", "0.0.1-SNAPSHOT"); setProperty("build.time", "2026-10-31T14:05:59Z") }),
        )
    }
}
