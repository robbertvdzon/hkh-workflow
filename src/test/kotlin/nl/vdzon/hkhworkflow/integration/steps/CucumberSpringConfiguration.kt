package nl.vdzon.hkhworkflow.integration.steps

import io.cucumber.spring.CucumberContextConfiguration
import nl.vdzon.hkhworkflow.HkhWorkflowApplication
import nl.vdzon.hkhworkflow.integration.TestEnvironment
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource

/** Start de hele app, eenmaal voor alle scenario's, op een willekeurige poort. Alles daarbuiten staat in [TestEnvironment]. */
@CucumberContextConfiguration
@SpringBootTest(classes = [HkhWorkflowApplication::class], webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CucumberSpringConfiguration {
    companion object {
        @JvmStatic
        @DynamicPropertySource
        fun properties(registry: DynamicPropertyRegistry) = TestEnvironment.register(registry)
    }
}
