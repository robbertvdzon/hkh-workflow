package nl.vdzon.hkhworkflow.integration.steps

import io.cucumber.java.Before
import nl.vdzon.hkhworkflow.integration.TestEnvironment
import nl.vdzon.hkhworkflow.integration.ZcbsServer
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.springframework.core.env.Environment

class Hooks(private val environment: Environment) {
    @Before(order = 0)
    fun cleanStart() {
        assumeTrue(ZcbsServer.dockerAvailable(), "Docker is niet beschikbaar: de integratietests worden overgeslagen.")
        World.baseUrl = "http://127.0.0.1:" + environment.getRequiredProperty("local.server.port")
        World.reset()
        TestEnvironment.reset()
    }

    /** Sommige scenario's kunnen alleen op de nabouw, omdat ze iets doen wat de echte ZCBS niet zomaar toelaat. */
    @Before("@alleen-nabouw", order = 1)
    fun onlyOnOurOwnZcbs() = assumeTrue(!ZcbsServer.wantsReal, "Dit scenario draait alleen op de nabouw.")
}
