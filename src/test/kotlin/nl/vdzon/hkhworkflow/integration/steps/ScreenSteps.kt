package nl.vdzon.hkhworkflow.integration.steps

import com.microsoft.playwright.Locator
import com.microsoft.playwright.Page as PlaywrightPage
import com.microsoft.playwright.assertions.LocatorAssertions
import com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat
import com.microsoft.playwright.options.AriaRole
import com.microsoft.playwright.options.KeyboardModifier
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
        screen.open("/")
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
    fun click(name: String) = screen.action(name).click()

    @When("the choice {string} is made")
    fun makeChoice(name: String) = screen.choice(name).click()

    @When("the flow of {word} is stopped on the screen")
    fun stopFlowOf(name: String) = screen.row(World.actor(name).account).getByRole(AriaRole.BUTTON, Locator.GetByRoleOptions().setName("Stoppen")).click()

    @Then("{string} can be clicked within a minute")
    fun canClickSoon(name: String) = assertThat(screen.action(name)).isEnabled(LocatorAssertions.IsEnabledOptions().setTimeout(60_000.0))

    @Then("{string} cannot be clicked")
    fun cannotClick(name: String) = assertThat(screen.action(name)).isDisabled()

    @When("{string} is filled in as {string}")
    fun fill(label: String, value: String) = screen.fill(label, value)

    @Then("{string} on the screen is {string}")
    fun fieldIs(label: String, value: String) = assertThat(screen.page.getByLabel(label, PlaywrightPage.GetByLabelOptions().setExact(true))).hasValue(value)

    // ---- Records ----

    @When("the {word} of the record is changed to {string} on the screen")
    fun changeField(field: String, value: String) = fieldOf(field).fill(value)

    @Then("the {word} of the record on the screen is {string}")
    fun fieldOfRecordIs(field: String, value: String) = assertThat(fieldOf(field)).hasValue(value)

    private fun fieldOf(field: String) = screen.page.locator("#a" + mapOf("title" to 5, "description" to 6, "keyword" to 4).getValue(field))

    @When("record {word} is opened in ZCBS itself from the screen")
    fun openInZcbs(number: String) {
        screen.fill("Recordnummer", number)
        val tab = screen.page.waitForPopup { screen.button("Open in ZCBS").click() }
        tab.waitForLoadState()
        popup = tab
    }

    @Then("the new tab shows {string}")
    fun newTabShows(text: String) = assertThat(popup!!.getByText(text).first()).isVisible()

    @Then("a window shows the file {string}")
    fun windowShows(title: String) {
        val dialog = screen.page.getByRole(AriaRole.DIALOG)
        assertThat(dialog).isVisible()
        assertThat(dialog.getByText(title).first()).isVisible()
    }

    @Then("{string} cannot be found")
    fun cannotBeFound(name: String) = assertThat(screen.button(name)).hasCount(0)

    @Then("the window shows the text {string}")
    fun windowShowsText(text: String) = assertThat(screen.page.getByRole(AriaRole.DIALOG).frameLocator("iframe").getByText(text).first()).isVisible()

    @When("the window is closed")
    fun closeWindow() {
        screen.button("Sluiten").click()
        assertThat(screen.page.getByRole(AriaRole.DIALOG)).hasCount(0)
    }

    // ---- Nieuwe bestanden ----

    @When("{string} is ticked")
    fun tick(file: String) = screen.page.getByLabel(file, PlaywrightPage.GetByLabelOptions().setExact(true)).check()

    @When("{string} is ticked with shift")
    fun tickWithShift(file: String) = screen.page.getByLabel(file, PlaywrightPage.GetByLabelOptions().setExact(true)).click(Locator.ClickOptions().setModifiers(listOf(KeyboardModifier.SHIFT)))

    @Then("{string} is ticked on the screen")
    fun isTicked(file: String) = assertThat(screen.page.getByLabel(file, PlaywrightPage.GetByLabelOptions().setExact(true))).isChecked()

    @Then("{string} cannot be ticked")
    fun cannotTick(file: String) = assertThat(screen.page.getByLabel(file, PlaywrightPage.GetByLabelOptions().setExact(true))).isDisabled()

    @Given("{word} has chosen {string} of {word} on the screen")
    fun hasChosen(name: String, file: String, scanner: String) {
        loggedIn(name)
        screen.open("/collectie/archief/nieuw?bron=$scanner")
        screen.action("Alles in orde: verder met bestanden kiezen").click()
        tick(file)
        screen.action("Verder").click()
        assertThat(screen.heading("3. Nummers")).isVisible()
    }

    @Given("{word} has given the numbers on the screen")
    fun hasGivenNumbers(name: String) {
        screen.action("Geef de bestanden deze nummers en ga verder").click()
        assertThat(screen.heading("4. ")).isVisible()
    }

    /** Elke vraag afwachten in de console en dan antwoorden, zoals iemand dat doet. Het wachtwoord en de veiligheidsvragen beantwoordt de app zelf. */
    @When("images is answered by hand on the screen")
    fun answerImagesByHand() {
        val output = screen.page.getByLabel("Uitvoer van het programma")
        val answers = listOf(
            "indien OK" to "a", "Kies 1,2,3" to "6", "UPLOADEN" to "j", "Nummer, naam" to "4", "FTP wachtwoord" to null, "Kies de ZCBS" to "archief",
            "OVERSCHRIJVEN" to null, "AANMAKEN" to "j", "aangemaakte mappen" to null, "met bron images" to null, "PC-bestand" to "j", "DOS-window" to "",
        )
        answers.forEach { (question, answer) ->
            assertThat(output).containsText(question, LocatorAssertions.ContainsTextOptions().setTimeout(30_000.0))
            when (answer) {
                null -> {}
                "" -> screen.button("Enter").click()
                "j" -> screen.button("J").click()
                else -> {
                    screen.fill("Antwoord", answer)
                    screen.button("Verstuur").click()
                }
            }
        }
    }

    @Then("the program output shows {string}")
    fun outputShows(text: String) = assertThat(screen.page.getByLabel("Uitvoer van het programma")).containsText(text, LocatorAssertions.ContainsTextOptions().setTimeout(30_000.0))

    @Then("the screen says {string} within a minute")
    fun saysSoon(text: String) = assertThat(screen.text(text).first()).isVisible(LocatorAssertions.IsVisibleOptions().setTimeout(60_000.0))

    @Then("the screen mentions the account of {word}")
    fun mentionsAccount(name: String) = assertThat(screen.text(World.actor(name).account).first()).isVisible()

    @Then("no flow of {word} is on the screen")
    fun noFlowOf(name: String) = assertThat(screen.row(World.actor(name).account)).hasCount(0)

    @When("the collection {string} is chosen on the screen")
    fun chooseCollection(collection: String) = screen.choice(collection).click()

    @Then("the screen is titled {string}")
    fun titled(title: String) = assertThat(screen.heading(title)).isVisible()

    @Then("the screen says {string}")
    fun says(text: String) = assertThat(screen.text(text).first()).isVisible()

    @Then("the screen does not say {string}")
    fun doesNotSay(text: String) = assertThat(screen.text(text)).hasCount(0)

    @Then("the collection {string} can be chosen on the screen")
    fun canChoose(collection: String) = assertThat(screen.choice(collection)).isVisible()

    @Then("the collection {string} cannot be chosen on the screen")
    fun cannotChoose(collection: String) {
        assertThat(screen.text(collection).first()).isVisible()
        assertThat(screen.choice(collection)).hasCount(0)
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
        val index = request("/collectie/archief")
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
        private var popup: PlaywrightPage? = null
    }
}
