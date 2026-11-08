package nl.vdzon.hkhworkflow.update.internal

import java.time.Clock
import kotlin.system.exitProcess
import nl.vdzon.hkhworkflow.flow.RunningFlows
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

/** Wat er bezig is: de doorlopen van Nieuwe bestanden. */
@Component
class FlowActivity(private val flows: RunningFlows) : Activity {
    override fun running(): List<String> = flows.running()
}

/** Sluit Spring netjes af en eindigt het proces met [EXIT_UPDATE], na een paar seconden zodat het antwoord op de klik de browser nog bereikt. */
@Component
class SpringRestarter(private val context: ConfigurableApplicationContext) : AppRestarter {
    override fun restart() {
        newThread {
            Thread.sleep(2_000)
            exitProcess(SpringApplication.exit(context, ExitCodeGenerator { EXIT_UPDATE }))
        }.start()
    }

    companion object {
        /**
         * Geen daemon-thread, ook niet als de aanroeper er een is (een thread erft dat): zodra Spring is afgesloten eindigt de JVM anders zelf, met code 0, nog voordat
         * deze thread [exitProcess] met [EXIT_UPDATE] aanroept, en dan denkt het startscript van de service dat het een gewone stop was.
         */
        internal fun newThread(task: Runnable): Thread = Thread(task, "update-herstart").apply { isDaemon = false }
    }
}
