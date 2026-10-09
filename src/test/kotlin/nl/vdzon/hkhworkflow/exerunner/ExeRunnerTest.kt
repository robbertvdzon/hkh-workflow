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
}
