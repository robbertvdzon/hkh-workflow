package nl.vdzon.hkhworkflow.exerunner

import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledOnOs
import org.junit.jupiter.api.condition.OS
import org.junit.jupiter.api.io.TempDir

@EnabledOnOs(OS.MAC, OS.LINUX)
class ExeRunnerTest {
    @TempDir
    lateinit var dir: Path

    private fun script(body: String): Path {
        val file = dir.resolve("prog.sh")
        Files.writeString(file, "#!/bin/sh\n$body\n")
        file.toFile().setExecutable(true)
        return file
    }

    @Test
    fun `answers recognised questions automatically and hides secrets`() {
        val exe = script(
            """
            printf 'Splitsen? [J/N] '; read a; echo "splitsen=${'$'}a"
            printf 'FTP wachtwoord? '; read p; echo "lengte=${'$'}{#p}"
            printf 'Type return to exit'; read x; echo klaar
            """.trimIndent(),
        )
        val runner = ExeRunner("UTF-8", dir.resolve("logs").toString())
        val done = CountDownLatch(1)
        var exit: Int? = -1
        runner.start(
            exe, dir, "test", RunMode.IN_APP,
            autoAnswers = listOf(
                AutoAnswer("splitsen?", "n"),
                AutoAnswer("FTP wachtwoord", "geheim", secret = true),
                AutoAnswer("return to exit", ""),
            ),
        ) { exit = it; done.countDown() }

        assertTrue(done.await(10, TimeUnit.SECONDS), "programma moet stoppen")
        val snapshot = runner.snapshot()!!
        assertEquals(0, exit)
        assertEquals(3, snapshot.autoAnswered)
        assertTrue(snapshot.output.contains("splitsen=n"))
        assertTrue(snapshot.output.contains("lengte=6"))
        assertFalse(snapshot.output.contains("geheim"), "wachtwoord mag niet in de uitvoer staan")
        assertFalse(runner.logText()!!.contains("geheim"), "wachtwoord mag niet in het log staan")
    }

    @Test
    fun `history lists finished runs newest first and clear empties the console`() {
        val exe = script("echo hallo")
        val runner = ExeRunner("UTF-8", dir.resolve("logs").toString())
        repeat(2) { i ->
            val done = CountDownLatch(1)
            runner.start(exe, dir, "run $i", RunMode.IN_APP) { done.countDown() }
            assertTrue(done.await(10, TimeUnit.SECONDS))
            Thread.sleep(1100) // de tijdstempel in de bestandsnaam heeft secondenresolutie
        }

        val history = runner.history()
        assertEquals(listOf("run 1", "run 0"), history.map { it.label })
        assertTrue(history.all { !it.running && it.exitCode == 0 && it.duration != null })
        assertTrue(runner.logText(history[0].file)!!.contains("EXIT code=0"))
        assertEquals(null, runner.logText("../geheim.txt"), "alleen eigen lognamen")

        assertTrue(runner.stopAndWait(), "niets draait, dus meteen klaar")
        runner.clear()
        assertEquals(null, runner.snapshot())
    }

    @Test
    fun `stopAndWait kills a running program`() {
        val exe = script("printf 'wacht...'; read a; echo nooit")
        val runner = ExeRunner("UTF-8", dir.resolve("logs").toString())
        runner.start(exe, dir, "hangt", RunMode.IN_APP) {}
        assertTrue(runner.isRunning())
        assertTrue(runner.stopAndWait(), "programma moet binnen de tijd stoppen")
        assertFalse(runner.isRunning())
        assertTrue(runner.history().first().stopped)
        runner.clear()
        assertEquals(null, runner.snapshot())
    }

    @Test
    fun `logs can be deleted one by one or all at once, except a running one`() {
        val runner = ExeRunner("UTF-8", dir.resolve("logs").toString())
        val done = CountDownLatch(1)
        runner.start(script("echo klaar"), dir, "klaar", RunMode.IN_APP) { done.countDown() }
        assertTrue(done.await(10, TimeUnit.SECONDS))
        Thread.sleep(1100)
        runner.start(script("printf 'wacht'; read a"), dir, "hangt", RunMode.IN_APP) {}
        assertEquals(2, runner.history().size)

        val running = runner.history().first { it.running }
        assertFalse(runner.deleteLog(running.file), "lopend log mag niet weg")
        assertFalse(runner.deleteLog("../iets.log"))
        assertEquals(1, runner.deleteAllLogs())
        assertEquals(listOf(running.file), runner.history().map { it.file })

        runner.stopAndWait()
        assertTrue(runner.deleteLog(running.file))
        assertEquals(null, runner.snapshot(), "console van een gewist log is weg")
        assertTrue(runner.history().isEmpty())
    }

    @Test
    fun `knows which question the program waits on, suggests the remembered answer and remembers what is typed`() {
        val exe = script(
            """
            printf 'Splitsen? [J/N] '; read a; echo "splitsen=${'$'}a"
            printf 'FTP wachtwoord? '; read p
            printf 'Type return to exit'; read x; echo klaar
            """.trimIndent(),
        )
        val runner = ExeRunner("UTF-8", dir.resolve("logs").toString())
        val done = CountDownLatch(1)
        runner.start(
            exe, dir, "test", RunMode.IN_APP,
            knownQuestions = listOf(AutoAnswer("splitsen?", "n"), AutoAnswer("FTP wachtwoord", "oud-wachtwoord", secret = true), AutoAnswer("return to exit", "")),
        ) { done.countDown() }

        fun waitForQuestion(pattern: String) {
            val deadline = System.currentTimeMillis() + 10_000
            while (runner.snapshot()?.question != pattern && System.currentTimeMillis() < deadline) Thread.sleep(20)
            assertEquals(pattern, runner.snapshot()?.question)
        }

        waitForQuestion("splitsen?")
        assertEquals("n", runner.snapshot()!!.suggestion, "het onthouden antwoord wordt voorgesteld")
        runner.sendInput("j")
        waitForQuestion("FTP wachtwoord")
        assertEquals(null, runner.snapshot()!!.suggestion, "een wachtwoord wordt nooit voorgesteld")
        runner.sendInput("mijn-wachtwoord")
        waitForQuestion("return to exit")
        runner.sendInput("")
        assertTrue(done.await(10, TimeUnit.SECONDS))

        assertEquals(mapOf("splitsen?" to "j", "return to exit" to ""), runner.typedAnswers(), "het wachtwoord wordt niet onthouden")
        assertEquals(runner.typedAnswers(), runner.givenAnswers())
        assertEquals(null, runner.snapshot()!!.suggestion, "klaar: geen voorstel meer")
    }

    @Test
    fun `automatische antwoorden tellen mee in de gegeven antwoorden maar niet als ingetypt`() {
        val exe = script("printf 'Splitsen? [J/N] '; read a; echo \"splitsen=\${'$'}a\"")
        val runner = ExeRunner("UTF-8", dir.resolve("logs").toString())
        val done = CountDownLatch(1)
        runner.start(exe, dir, "auto", RunMode.IN_APP, autoAnswers = listOf(AutoAnswer("splitsen?", "n"))) { done.countDown() }
        assertTrue(done.await(10, TimeUnit.SECONDS))
        assertEquals(mapOf("splitsen?" to "n"), runner.givenAnswers())
        assertEquals(emptyMap(), runner.typedAnswers())
    }

    @Test
    fun `answers forced questions itself even when a person answers the rest`() {
        val exe = script(
            """
            printf 'Uploaden? [J/N] '; read a; echo "upload=${'$'}a"
            printf 'VERWIJDEREN mappen? [J/N] '; read b; echo "verwijder=${'$'}b"
            printf 'Type return to exit'; read x; echo klaar
            """.trimIndent(),
        )
        val runner = ExeRunner("UTF-8", dir.resolve("logs").toString())
        val done = CountDownLatch(1)
        runner.start(
            exe, dir, "handmatig", RunMode.IN_APP,
            knownQuestions = listOf(AutoAnswer("uploaden?", "n"), AutoAnswer("return to exit", "")),
            forcedAnswers = listOf(AutoAnswer("VERWIJDEREN mappen", "n")),
        ) { done.countDown() }

        fun waitForQuestion(pattern: String) {
            val deadline = System.currentTimeMillis() + 10_000
            while (runner.snapshot()?.question != pattern && System.currentTimeMillis() < deadline) Thread.sleep(20)
            assertEquals(pattern, runner.snapshot()?.question)
        }
        waitForQuestion("uploaden?")
        assertFalse(runner.snapshot()!!.auto, "dit is geen automatische run")
        runner.sendInput("j")
        waitForQuestion("return to exit")
        runner.sendInput("")
        assertTrue(done.await(10, TimeUnit.SECONDS))

        val output = runner.snapshot()!!.output
        assertTrue(output.contains("upload=j"), output)
        assertTrue(output.contains("verwijder=n"), "de app antwoordde nee: $output")
        assertEquals(mapOf("VERWIJDEREN mappen" to "n", "uploaden?" to "j", "return to exit" to ""), runner.givenAnswers())
        assertEquals(mapOf("uploaden?" to "j", "return to exit" to ""), runner.typedAnswers(), "alleen wat de gebruiker typte")
    }

    @Test
    fun `recognises a question whatever the amount of whitespace, also automatically`() {
        // het echte images.exe schrijft "Kies  1,2,3 (...),  of kies  6,7,8" met dubbele spaties
        val exe = script(
            """
            printf '  Kies  1,2,3 (objecten),  of kies  6,7,8 (foto s)\n'; read a; echo "res=${'$'}a"
            printf 'Type return to exit'; read x; echo klaar
            """.trimIndent(),
        )
        val runner = ExeRunner("UTF-8", dir.resolve("logs").toString())
        val done = CountDownLatch(1)
        runner.start(
            exe, dir, "spaties", RunMode.IN_APP,
            autoAnswers = listOf(AutoAnswer("Kies 1,2,3", "7"), AutoAnswer("return to exit", "")),
        ) { done.countDown() }
        assertTrue(done.await(10, TimeUnit.SECONDS))
        assertTrue(runner.snapshot()!!.output.contains("res=7"), runner.snapshot()!!.output)
        assertEquals("7", runner.givenAnswers()["Kies 1,2,3"])
    }

    @Test
    fun `a remembered password is given once, and a typed one is masked and kept apart`() {
        val exe = script(
            """
            printf 'FTP wachtwoord? '; read p1; echo "eerste=${'$'}{#p1}"
            printf 'FTP wachtwoord? '; read p2; echo "tweede=${'$'}{#p2}"
            printf 'Type return to exit'; read x; echo klaar
            """.trimIndent(),
        )
        val runner = ExeRunner("UTF-8", dir.resolve("logs").toString())
        val done = CountDownLatch(1)
        runner.start(
            exe, dir, "wachtwoord", RunMode.IN_APP,
            knownQuestions = listOf(AutoAnswer("FTP wachtwoord", "oud-pw", secret = true), AutoAnswer("return to exit", "")),
            forcedAnswers = listOf(AutoAnswer("FTP wachtwoord", "oud-pw", secret = true, once = true)),
        ) { done.countDown() }

        // de eerste keer geeft de app het onthouden wachtwoord, de tweede keer (het klopte niet) wacht hij op de gebruiker
        val deadline = System.currentTimeMillis() + 10_000
        while (runner.snapshot()?.output?.contains("eerste=6") != true && System.currentTimeMillis() < deadline) Thread.sleep(20)
        while (runner.snapshot()?.question != "FTP wachtwoord" && System.currentTimeMillis() < deadline) Thread.sleep(20)
        assertEquals("FTP wachtwoord", runner.snapshot()!!.question)
        assertEquals(null, runner.snapshot()!!.suggestion, "een wachtwoord wordt nooit voorgesteld")
        runner.sendInput("nieuw-pw!")
        while (runner.snapshot()?.question != "return to exit" && System.currentTimeMillis() < deadline) Thread.sleep(20)
        runner.sendInput("")
        assertTrue(done.await(10, TimeUnit.SECONDS))

        val output = runner.snapshot()!!.output
        assertTrue(output.contains("tweede=9"), output)
        assertFalse(output.contains("oud-pw") || output.contains("nieuw-pw"), "geen wachtwoord in de uitvoer: $output")
        assertFalse(runner.logText()!!.contains("oud-pw") || runner.logText()!!.contains("nieuw-pw"), "geen wachtwoord in het log")
        assertEquals(mapOf("FTP wachtwoord" to "nieuw-pw!"), runner.typedSecretAnswers())
        assertEquals(mapOf("return to exit" to ""), runner.typedAnswers(), "een wachtwoord zit niet bij de gewone antwoorden")
    }
}
