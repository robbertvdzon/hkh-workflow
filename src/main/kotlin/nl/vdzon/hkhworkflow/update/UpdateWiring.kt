package nl.vdzon.hkhworkflow.update

import java.time.Clock
import kotlin.system.exitProcess
import nl.vdzon.hkhworkflow.testrun.TestRunService
import org.springframework.boot.ExitCodeGenerator
import org.springframework.boot.SpringApplication
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.stereotype.Component

/** De exitcode waarmee de app het startscript van de service om een update vraagt (zie tools/windows-server/run.ps1). */
const val EXIT_UPDATE = 10

@Configuration
class UpdateConfig {
    @Bean
    fun updateClock(): Clock = Clock.systemDefaultZone()
}

/** Een doorloop is bezig als er een programma loopt of een stap (upload, back-up) bezig is; zo'n stap overleeft een herstart niet. */
@Component
class FlowActivity(private val testRunService: TestRunService) : Activity {
    override fun running(): List<String> =
        testRunService.sessions().filter { it.busy() }.map { (it.owner.ifEmpty { "beheerder" }) + " in " + it.collection }
}

/** Sluit Spring netjes af en eindigt het proces met [EXIT_UPDATE], na een paar seconden zodat het antwoord op de klik de browser nog bereikt. */
@Component
class SpringRestarter(private val context: ConfigurableApplicationContext) : AppRestarter {
    override fun restart() {
        Thread({
            Thread.sleep(2_000)
            exitProcess(SpringApplication.exit(context, ExitCodeGenerator { EXIT_UPDATE }))
        }, "update-herstart").start()
    }
}
