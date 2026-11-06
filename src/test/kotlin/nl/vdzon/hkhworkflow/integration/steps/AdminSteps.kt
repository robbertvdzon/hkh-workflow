package nl.vdzon.hkhworkflow.integration.steps

import com.github.tomakehurst.wiremock.client.WireMock
import io.cucumber.java.After
import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import nl.vdzon.hkhworkflow.integration.Api
import nl.vdzon.hkhworkflow.integration.TestEnvironment
import nl.vdzon.hkhworkflow.integration.ZcbsServer

class AdminSteps {
    private val admin by lazy { Api(World.baseUrl) }

    @Given("the administrator is logged in")
    fun adminLoggedIn() { assertEquals(204, admin.post("/api/admin-session", mapOf("password" to TestEnvironment.ADMIN_PASSWORD)).status) }

    @When("someone tries the administrator password {string}")
    fun adminTries(password: String) { admin.post("/api/admin-session", mapOf("password" to password)) }

    @Then("the settings are closed for this person")
    fun settingsClosed() = admin.read("/api/settings").let {
        assertEquals(403, it.status, it.toString())
        assertEquals("admin-required", it.code)
    }

    @Then("the administrator sees {string}")
    fun adminSees(text: String) = assertTrue(admin.last.body.contains(text), "\"$text\" niet in ${admin.last}")

    @When("the administrator opens the settings")
    fun opensSettings() { admin.get("/api/settings") }

    @Then("the settings do not contain the ftp password")
    fun noPassword() = assertFalse(admin.read("/api/settings").body.contains(ZcbsServer.FTP_PASSWORD))

    @When("the administrator tests the ftp connection")
    fun ftpTest() { admin.post("/api/settings/ftp-test") }

    @When("the administrator changes the folder for leftovers to {string}")
    fun changesLeftovers(folder: String) {
        val settings = admin.read("/api/settings").obj + ("leftoversFolder" to folder)
        assertEquals(200, admin.put("/api/settings", settings).status, admin.last.toString())
    }

    @When("the administrator stops the session of {word}")
    fun stopsSession(name: String) {
        assertEquals(204, admin.delete("/api/sessions/flows/archief/${World.encoded(World.actor(name).account)}").status, admin.last.toString())
    }

    @Then("the administrator sees {word} among the sessions")
    fun seesSession(name: String) = assertTrue(flowUsers().contains(World.actor(name).account), flowUsers().toString())

    @Then("the administrator does not see {word} among the sessions any more")
    fun seesNoSession(name: String) = assertFalse(flowUsers().contains(World.actor(name).account), flowUsers().toString())

    @Suppress("UNCHECKED_CAST")
    private fun flowUsers() = (admin.read("/api/sessions")["flows"] as List<Map<String, Any?>>).map { it["user"] }

    @When("the administrator deletes all program logs")
    fun deletesLogs() { assertEquals(200, admin.delete("/api/program-logs").status, admin.last.toString()) }

    @Then("there are no program logs left")
    fun noLogs() = assertEquals(emptyList(), admin.read("/api/program-logs").list)

    @When("the administrator checks for a new version")
    fun checksVersion() { admin.post("/api/version/check") }

    @Then("the settings show {string} as the folder for leftovers")
    fun leftoversShown(folder: String) = assertEquals(folder, admin.read("/api/settings")["leftoversFolder"])

    @Then("the workflow log hears that the folder for leftovers changed to {string}")
    fun reportedChange(folder: String) = Await.until("melding van de gewijzigde instellingen") {
        TestEnvironment.outside.findAll(WireMock.postRequestedFor(WireMock.urlPathEqualTo("/api/workflow-log/settings-changes"))).any { it.bodyAsString.contains(folder) }
    }

    @After("@wijzigt-instellingen")
    fun restoreSettings() {
        adminLoggedIn()
        changesLeftovers("")
    }
}
