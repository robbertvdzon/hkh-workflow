package nl.vdzon.hkhworkflow.integration.steps

import com.github.tomakehurst.wiremock.client.WireMock
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import nl.vdzon.hkhworkflow.integration.Api
import nl.vdzon.hkhworkflow.integration.ApiResponse
import nl.vdzon.hkhworkflow.integration.TestEnvironment
import tools.jackson.databind.SerializationFeature
import tools.jackson.databind.json.JsonMapper
import tools.jackson.databind.node.ObjectNode

class AccessSteps {
    private val visitor by lazy { Api(World.baseUrl) }

    @Given("{word} may edit records in ZCBS")
    fun mayEdit(name: String) { World.actor(name, "1") }

    @Given("{word} may only read records in ZCBS")
    fun mayRead(name: String) { World.actor(name, "3") }

    @Given("{word} is logged in")
    fun loggedIn(name: String) { login(World.actor(name, "1")) }

    @When("{word} logs in")
    fun logsIn(name: String) { login(World.actor(name)) }

    @When("{word} logs in with the password {string}")
    fun logsInWith(name: String, password: String) {
        val actor = World.actor(name)
        login(actor, password)
    }

    @When("{word} logs out")
    fun logsOut(name: String) { assertEquals(204, World.actor(name).api.delete("/api/session").status) }

    @When("{word} chooses the collection {string}")
    fun chooses(name: String, collection: String) { World.actor(name).api.get("/api/collections/$collection/flow") }

    @Then("{word} can work in the collection {string}")
    fun canWork(name: String, collection: String) = assertEquals(200, World.actor(name).api.read("/api/collections/$collection/flow").status)

    @When("a visitor asks for {string}")
    fun visitorAsks(path: String) { World.lastResponse = visitor.get(path) }

    @Then("the visitor is told to log in")
    fun visitorToldToLogIn() {
        assertEquals(401, World.lastResponse!!.status, World.lastResponse.toString())
        assertEquals("not-logged-in", World.lastResponse!!.code)
    }

    @Then("the visitor gets a problem with the code {string} and a message to show")
    fun visitorProblem(code: String) {
        val response = World.lastResponse!!
        assertEquals(code, response.code, response.toString())
        assertTrue(response.headers["content-type"]!!.first().startsWith("application/problem+json"), response.headers.toString())
        assertEquals(response.status, response["status"])
        assertTrue((response["detail"] as String).isNotBlank())
    }

    @Then("the description of the API has {string}")
    fun described(path: String) {
        val response = World.lastResponse!!
        assertEquals(200, response.status, response.toString())
        @Suppress("UNCHECKED_CAST")
        val paths = response["paths"] as Map<String, Map<String, Map<String, Any?>>>
        val operations = paths[path] ?: error("$path staat niet in de beschrijving: ${paths.keys}")
        val parameters = operations.values.first()["parameters"].toString()
        assertTrue(parameters.contains("collection"), parameters)
    }

    /** Met -Dopenapi.update=true werkt dit de kopie bij: zie tools/update-openapi.sh. */
    @Then("the description of the API is the same as the copy of the frontend")
    fun sameAsFrontend() {
        val mapper = JsonMapper.builder().enable(SerializationFeature.INDENT_OUTPUT).build()
        val published = mapper.readTree(World.lastResponse!!.body) as ObjectNode
        // Het adres van de server verschilt per keer, en de frontend gebruikt het niet.
        published.remove("servers")
        val copy = Path.of("frontend/openapi.json")
        if (System.getProperty("openapi.update") == "true") Files.writeString(copy, mapper.writeValueAsString(published) + "\n")
        assertEquals(mapper.readTree(Files.readString(copy)), published, "frontend/openapi.json loopt achter; draai tools/update-openapi.sh")
    }

    @Then("{word} sees {string}")
    fun sees(name: String, text: String) = World.actor(name).last.let { assertTrue(it.body.contains(text), "\"$text\" niet in $it") }

    @Then("{word} is refused with {string}")
    fun refused(name: String, code: String) = World.actor(name).last.let { assertEquals(code, it.code, it.toString()) }

    @Then("{word} can choose the collection {string}")
    fun canChoose(name: String, collection: String) = assertTrue(available(World.actor(name), collection), World.actor(name).api.read("/api/session").toString())

    @Then("{word} cannot choose the collection {string}")
    fun cannotChoose(name: String, collection: String) = assertFalse(available(World.actor(name), collection))

    @When("{word} comes back after a restart of the app")
    fun comesBack(name: String) {
        val actor = World.actor(name)
        actor.api.forgetSession()
        actor.api.get("/api/session")
    }

    @Then("the workflow log hears that {word} was logged in again from the cookie")
    fun reportedCookie(name: String) = Await.until("melding van de inlog met het cookie") {
        TestEnvironment.outside.findAll(WireMock.postRequestedFor(WireMock.urlPathEqualTo("/api/workflow-log/logins"))).any {
            it.bodyAsString.contains(World.actor(name).account) && it.bodyAsString.contains("COOKIE")
        }
    }

    @Then("{word} is not logged in")
    fun notLoggedIn(name: String) = assertNull(World.actor(name).api.read("/api/session")["user"])

    @Then("the workflow log hears of a successful login by {word}")
    fun reportedOk(name: String) = awaitLogin(World.actor(name).account, success = true)

    @Then("the workflow log hears of a failed login by {word}")
    fun reportedFailed(name: String) = awaitLogin(World.actor(name).account, success = false)

    private fun awaitLogin(account: String, success: Boolean) = Await.until("melding van de inlog van $account") {
        TestEnvironment.outside.findAll(WireMock.postRequestedFor(WireMock.urlPathEqualTo("/api/workflow-log/logins"))).any {
            val body = it.bodyAsString.replace(" ", "")
            body.contains("\"$account\"".replace(" ", "")) && body.contains("\"success\":$success")
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun available(actor: Actor, collection: String): Boolean =
        (actor.api.read("/api/session")["collections"] as List<Map<String, Any?>>).any { it["name"] == collection && it["available"] == true }

    companion object {
        fun login(actor: Actor, password: String = actor.password): ApiResponse =
            actor.api.post("/api/session", mapOf("name" to actor.account, "password" to password))
    }
}

object Await {
    fun until(what: String, seconds: Int = 20, condition: () -> Boolean) {
        val end = System.currentTimeMillis() + seconds * 1000L
        while (System.currentTimeMillis() < end) {
            if (condition()) return
            Thread.sleep(100)
        }
        throw AssertionError("Na $seconds seconden nog niet gezien: $what")
    }
}
