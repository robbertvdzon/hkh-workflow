package nl.vdzon.hkhworkflow.integration.steps

import com.github.tomakehurst.wiremock.client.WireMock
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import nl.vdzon.hkhworkflow.integration.Browser
import nl.vdzon.hkhworkflow.integration.Page
import nl.vdzon.hkhworkflow.integration.TestEnvironment

class AccessSteps {
    private val visitor by lazy { Browser(World.baseUrl) }

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
    fun logsOut(name: String) {
        val actor = World.actor(name)
        actor.browser.submit(actor.page.form("/zcbs/logout"))
    }

    @When("{word} chooses the collection {string}")
    fun chooses(name: String, collection: String) { World.actor(name).browser.get("/collectie/$collection") }

    @When("a visitor opens {string}")
    fun visitorOpens(path: String) { World.lastPage = visitor.get(path) }

    @Then("the visitor is sent to {string}")
    fun visitorSentTo(path: String) = assertEquals(path, World.lastPage!!.path, World.lastPage!!.text)

    @Then("the visitor sees {string}")
    fun visitorSees(text: String) = assertTrue(World.lastPage!!.text.contains(text), World.lastPage!!.text)

    @Then("{word} sees {string}")
    fun sees(name: String, text: String) = World.actor(name).page.let { assertTrue(it.text.contains(text), "\"$text\" niet op ${it.path}:\n${it.text}") }

    @Then("{word} does not see {string}")
    fun doesNotSee(name: String, text: String) = World.actor(name).page.let { assertFalse(it.text.contains(text), "\"$text\" toch op ${it.path}:\n${it.text}") }

    @Then("{word} can choose the collection {string}")
    fun canChoose(name: String, collection: String) = assertTrue(collectionLink(World.actor(name).page, collection), World.actor(name).page.text)

    @Then("{word} cannot choose the collection {string}")
    fun cannotChoose(name: String, collection: String) = assertFalse(collectionLink(World.actor(name).page, collection), World.actor(name).page.text)

    @When("{word} comes back after a restart of the app")
    fun comesBack(name: String) {
        val actor = World.actor(name)
        actor.browser.forgetSession()
        actor.browser.get("/collectie")
    }

    @Then("the workflow log hears that {word} was logged in again from the cookie")
    fun reportedCookie(name: String) = Await.until("melding van de inlog met het cookie") {
        TestEnvironment.outside.findAll(WireMock.postRequestedFor(WireMock.urlPathEqualTo("/api/workflow-log/logins"))).any {
            it.bodyAsString.contains(World.actor(name).account) && it.bodyAsString.contains("COOKIE")
        }
    }

    @Then("{word} is back on the start page")
    fun backOnStart(name: String) {
        val page = World.actor(name).browser.get("/")
        assertEquals("/", page.path)
        assertTrue(page.hasForm("/zcbs/login"), page.text)
    }

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

    private fun collectionLink(page: Page, collection: String) = page.doc.select("a[href]").any { it.attr("href").endsWith("/collectie/$collection") }

    companion object {
        fun login(actor: Actor, password: String = actor.password): Page {
            val start = actor.browser.get("/")
            return actor.browser.submit(start.form("/zcbs/login"), mapOf("naam" to listOf(actor.account), "wachtwoord" to listOf(password)))
        }
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
