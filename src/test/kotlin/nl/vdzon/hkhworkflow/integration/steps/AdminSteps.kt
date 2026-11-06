package nl.vdzon.hkhworkflow.integration.steps

import com.github.tomakehurst.wiremock.client.WireMock
import io.cucumber.java.After
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import nl.vdzon.hkhworkflow.integration.Browser
import nl.vdzon.hkhworkflow.integration.TestEnvironment

class AdminSteps {
    private val admin by lazy { Browser(World.baseUrl) }

    @Given("the administrator is logged in")
    fun adminLoggedIn() {
        val page = admin.get("/beheer")
        admin.submit(page.form("/beheer/inloggen"), mapOf("wachtwoord" to listOf(TestEnvironment.ADMIN_PASSWORD)))
    }

    @When("someone tries the administrator password {string}")
    fun adminTries(password: String) {
        val page = admin.get("/beheer")
        admin.submit(page.form("/beheer/inloggen"), mapOf("wachtwoord" to listOf(password)))
    }

    @When("the administrator opens {string}")
    fun adminOpens(path: String) { admin.get(path) }

    @Then("the administrator sees {string}")
    fun adminSees(text: String) = assertTrue(admin.page.text.contains(text), "\"$text\" niet op ${admin.page.path}:\n${admin.page.text}")

    @Then("the administrator is on {string}")
    fun adminOn(path: String) = assertEquals(path, admin.page.path, admin.page.text)

    @When("the administrator tests the ftp connection")
    fun ftpTest() {
        admin.get("/instellingen")
        admin.click(admin.page.button("Test verbinding"))
    }

    @When("the administrator changes the folder for leftovers to {string}")
    fun changesLeftovers(folder: String) {
        admin.get("/instellingen")
        val form = admin.page.form("/instellingen")
        admin.submit(form, mapOf("leftoversFolder" to listOf(folder)))
    }

    @When("the administrator stops the session of {word}")
    fun stopsSession(name: String) {
        admin.get("/instellingen/sessies")
        val row = admin.page.doc.select("form").firstOrNull { it.select("input[name=gebruiker]").attr("value") == World.actor(name).account }
            ?: error("Geen doorloop van $name:\n${admin.page.text}")
        admin.submit(row)
    }

    @Then("the administrator sees {word} among the sessions")
    fun seesSession(name: String) {
        admin.get("/instellingen/sessies")
        assertTrue(admin.page.text.contains(World.actor(name).account), admin.page.text)
    }

    @When("the administrator deletes all program logs")
    fun deletesLogs() {
        admin.post("/test/runs/delete-all")
    }

    @When("the administrator checks for a new version")
    fun checksVersion() {
        admin.get("/instellingen/versie")
        admin.click(admin.page.button("Controleer"))
    }

    @Then("the settings show {string} as the folder for leftovers")
    fun leftoversShown(folder: String) = assertEquals(folder, admin.get("/instellingen").doc.selectFirst("input[name=leftoversFolder]")?.attr("value"))

    @Then("the workflow log hears that the folder for leftovers changed to {string}")
    fun reportedChange(folder: String) = Await.until("melding van de gewijzigde instellingen") {
        TestEnvironment.outside.findAll(WireMock.postRequestedFor(WireMock.urlPathEqualTo("/api/workflow-log/settings-changes"))).any { it.bodyAsString.contains(folder) }
    }

    @After("@wijzigt-instellingen")
    fun restoreSettings() {
        changesLeftovers("")
    }
}
