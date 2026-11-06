package nl.vdzon.hkhworkflow.integration.steps

import com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat
import io.cucumber.java.After
import io.cucumber.java.Scenario
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import nl.vdzon.hkhworkflow.integration.Screen
import nl.vdzon.hkhworkflow.integration.TestEnvironment

/** De scenario's die door de schermen gaan. Er is één browser per scenario; daar zit één persoon achter. */
class ScreenSteps {
    private val screen: Screen get() = current ?: Screen(World.baseUrl).also { current = it }

    @After("@screens")
    fun closeBrowser(scenario: Scenario) {
        current?.let {
            it.screenshot(scenario.name)
            val errors = it.errors.toList()
            it.close()
            current = null
            assertEquals(emptyList(), errors, "fouten in de browser")
        }
    }

    @When("the app is opened at {string}")
    fun open(path: String) = screen.open(path)

    @When("the page is reloaded")
    fun reload() = screen.reload()

    @When("{word} logs in on the screen")
    fun logsIn(name: String) = World.actor(name).let { loginOnScreen(it.account, it.password) }

    @When("{word} logs in on the screen with the password {string}")
    fun logsInWith(name: String, password: String) = loginOnScreen(World.actor(name).account, password)

    @Given("{word} is logged in on the screen")
    fun loggedIn(name: String) {
        screen.open("/app/")
        logsIn(name)
        assertThat(screen.heading("Met welke collectie wil je werken?")).isVisible()
    }

    @When("the admin logs in on the screen with the password {string}")
    fun adminLogsIn(password: String) {
        screen.fill("Beheerderswachtwoord", password)
        screen.button("Inloggen").click()
    }

    @When("the admin logs in on the screen with the right password")
    fun adminLogsInRight() = adminLogsIn(TestEnvironment.ADMIN_PASSWORD)

    @When("{string} is clicked")
    fun click(name: String) {
        val button = screen.button(name)
        if (button.count() > 0) button.first().click() else screen.link(name).first().click()
    }

    @When("the collection {string} is chosen on the screen")
    fun chooseCollection(collection: String) = screen.link(collection).click()

    @Then("the screen is titled {string}")
    fun titled(title: String) = assertThat(screen.heading(title)).isVisible()

    @Then("the screen says {string}")
    fun says(text: String) = assertThat(screen.text(text).first()).isVisible()

    @Then("the screen does not say {string}")
    fun doesNotSay(text: String) = assertThat(screen.text(text)).hasCount(0)

    @Then("the collection {string} can be chosen on the screen")
    fun canChoose(collection: String) = assertThat(screen.link(collection)).isVisible()

    @Then("the collection {string} cannot be chosen on the screen")
    fun cannotChoose(collection: String) {
        assertThat(screen.text(collection).first()).isVisible()
        assertThat(screen.link(collection)).hasCount(0)
    }

    @Then("the browser is at {string}")
    fun at(path: String) = assertThat(screen.page).hasURL(World.baseUrl + path)

    @Then("the file {string} does not exist")
    fun fileMissing(path: String) = assertEquals(404, status(path))

    @Then("the frontend is served with its own scripts")
    fun servedWithScripts() {
        val scripts = screen.page.locator("script[src]").all().map { it.getAttribute("src")!! }
        assertTrue(scripts.isNotEmpty(), "geen scripts in de pagina")
        scripts.forEach { assertEquals(200, status(it)) }
        val script = request(scripts.first())
        assertTrue(script.headers().firstValue("cache-control").orElse("").contains("immutable"), script.headers().toString())
        val index = request("/app/collectie/archief")
        assertTrue(index.headers().firstValue("cache-control").orElse("").contains("no-cache"), index.headers().toString())
    }

    private fun status(path: String) = request(path).statusCode()

    private fun request(path: String): HttpResponse<Void> =
        HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create(World.baseUrl + path)).build(), HttpResponse.BodyHandlers.discarding())

    private fun loginOnScreen(account: String, password: String) {
        screen.fill("Naam", account)
        screen.fill("Wachtwoord", password)
        screen.button("Inloggen").click()
    }

    companion object {
        private var current: Screen? = null
    }
}
