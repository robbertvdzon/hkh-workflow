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
}
