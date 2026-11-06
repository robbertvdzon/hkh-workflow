package nl.vdzon.hkhworkflow.integration.steps

import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import java.net.URI
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import nl.vdzon.hkhworkflow.integration.ApiResponse
import nl.vdzon.hkhworkflow.integration.Browser
import nl.vdzon.hkhworkflow.integration.TestEnvironment
import org.jsoup.Jsoup

class RecordSteps {
    private val zcbs get() = TestEnvironment.zcbs
    private val records = "/api/collections/archief/records"

    /** De namen van de velden in de database en in het formulier. */
    private val fields = mapOf("title" to 5, "description" to 6, "keyword" to 4, "author" to 7, "date" to 8)
    private fun field(name: String) = fields[name] ?: error("Onbekend veld $name")
    private fun input(name: String) = "a" + field(name)

    private val numbers = mutableMapOf<String, String>()

    @When("{word} opens record {word} for editing")
    fun opens(name: String, number: String) {
        World.actor(name).api.post("$records/$number/edit")
        numbers[name] = number
    }

    @Given("{word} has opened record {word} for editing")
    fun hasOpened(name: String, number: String) {
        AccessSteps.login(World.actor(name, "1"))
        opens(name, number)
    }

    @Suppress("UNCHECKED_CAST")
    private fun form(response: ApiResponse) = response["fields"] as List<Map<String, Any?>>

    @Suppress("UNCHECKED_CAST")
    private fun currentValues(name: String): Map<String, List<String>> =
        form(World.actor(name).api.read("$records/${numbers.getValue(name)}/edit")).associate { it["name"] as String to it["values"] as List<String> }

    @Then("{word} sees the {word} {string} in the form")
    fun seesInForm(name: String, field: String, value: String) = assertEquals(listOf(value), currentValues(name)[input(field)], World.actor(name).last.toString())

    @When("{word} saves the {word} {string}")
    fun saves(name: String, field: String, value: String) = saveWith(name, mapOf(input(field) to listOf(value.replace("\\n", "\n"))))

    @When("{word} saves the form without changes")
    fun savesUnchanged(name: String) = saveWith(name, emptyMap())

    /** Verstuurt het hele formulier, zoals de frontend dat doet. */
    private fun saveWith(name: String, changes: Map<String, List<String>>) {
        val actor = World.actor(name)
        actor.values = currentValues(name) + changes
        actor.api.put("$records/${numbers.getValue(name)}", mapOf("values" to actor.values))
    }

    @Then("record {word} is saved for {word}")
    fun isSaved(number: String, name: String) = World.actor(name).last.let {
        assertEquals(200, it.status, it.toString())
        assertEquals(true, it["saved"], it.toString())
        assertEquals(number, numbers[name])
    }

    @Then("{word} is told that nothing changed")
    fun nothingChanged(name: String) = World.actor(name).last.let {
        assertEquals(true, it["unchanged"], it.toString())
        assertEquals(false, it["saved"], it.toString())
    }

    @When("{word} previews the {word} {string}")
    fun previews(name: String, field: String, value: String) {
        World.actor(name).api.post("$records/${numbers.getValue(name)}/edit/preview", mapOf("values" to currentValues(name) + (input(field) to listOf(value))))
    }

    @When("{word} cancels editing")
    fun cancels(name: String) { assertEquals(204, World.actor(name).api.delete("$records/${numbers.getValue(name)}/edit").status) }

    @When("{word} keeps {word} own version of the conflict")
    fun keepsOwn(name: String, @Suppress("UNUSED_PARAMETER") whose: String) = resolve(name, "keep-mine")

    @When("{word} takes the version from ZCBS in the conflict")
    fun takesZcbs(name: String) = resolve(name, "take-theirs")

    private fun resolve(name: String, choice: String) {
        val actor = World.actor(name)
        actor.api.put("$records/${numbers.getValue(name)}", mapOf("values" to actor.values, "onConflict" to choice))
        assertEquals(200, actor.last.status, actor.last.toString())
    }

    @Then("{word} cannot edit the record")
    fun cannotEdit(name: String) = assertNotEquals(200, World.actor(name).last.status, World.actor(name).last.toString())

    @Given("someone changes the {word} of record {word} to {string} directly in ZCBS")
    fun changedElsewhere(field: String, number: String, value: String) = zcbs.changeField(number, field(field), value)

    @Given("all ZCBS sessions expire")
    fun sessionsExpire() = zcbs.expireSessions()

    @Then("ZCBS has the {word} {string} for record {word}")
    fun zcbsHas(field: String, value: String, number: String) = assertEquals(value, zcbs.record(number)[field(field)])

    @Then("ZCBS stored the {word} of record {word} as last changed by {word}")
    fun lastChangedBy(@Suppress("UNUSED_PARAMETER") field: String, number: String, name: String) =
        assertTrue(zcbs.record(number)[34].endsWith("|" + World.actor(name).account), zcbs.record(number)[34])

    @Then("record {word} is unchanged in ZCBS")
    fun unchanged(number: String) = assertEquals("Kerkstraat 1960, de kerk van Heemskerk", zcbs.record(number)[5].takeIf { number == "99003" } ?: error("Alleen voor 99003"))

    @When("{word} reads the scan of record {word}")
    fun readsScan(name: String, number: String) { assertEquals(200, World.actor(name).api.read("$records/$number/media").status) }

    @Then("{word} sees the scan of record {word} as a pdf")
    fun seesPdf(name: String, number: String) {
        val media = World.actor(name).api.read("$records/$number/media")
        assertEquals("pdf", media["kind"], media.toString())
        val url = media["url"] as String
        assertTrue(url.contains("/archief/pdf/$number.pdf"), url)
        val pdf = Browser().get(url)
        assertEquals(200, pdf.status)
        assertTrue(pdf.body.startsWith("%PDF"))
    }

    @Then("{word} is told that record {word} has no scan")
    fun noScan(name: String, number: String) {
        val media = World.actor(name).api.read("$records/$number/media")
        assertEquals(404, media.status, media.toString())
        assertEquals("no-media", media.code)
        assertTrue((media["detail"] as String).contains("geen pdf of afbeelding"), media.toString())
    }

    @When("{word} opens record {word} in ZCBS itself")
    fun opensInZcbs(name: String, number: String) {
        val handoff = World.actor(name).api.read("$records/$number/open-in-zcbs")
        assertEquals("no-store", handoff.headers["cache-control"]?.first(), "de pagina met het wachtwoord mag niet bewaard worden")
        val form = Jsoup.parse(handoff.body).selectFirst("form#f") ?: error("Geen formulier naar ZCBS:\n${handoff.body}")
        assertEquals(URI.create(zcbs.baseUrl + "/cgi-bin/archief.pl"), URI.create(form.attr("action")))
        val own = Browser()
        World.lastPage = own.submit(form)
        // De echte ZCBS remt een nieuw bezoek tijdens de snelle testrun soms even af.
        if (World.lastPage!!.text.contains("Access ZCBS denied")) {
            Thread.sleep(6000)
            World.lastPage = own.submit(form)
        }
    }

    @Then("ZCBS itself shows {string}")
    fun zcbsShows(text: String) = assertTrue(World.lastPage!!.text.contains(text), World.lastPage!!.text)

    @Then("{word} sees record {word} in the recent list with title {string}")
    fun recentHas(name: String, number: String, title: String) {
        val row = recentRow(name, number, "mine") ?: error("Record $number staat niet in de lijst")
        assertEquals(title, row["title"], row.toString())
    }

    @Then("{word} does not see record {word} in the {word} recent list")
    fun recentHasNot(name: String, number: String, scope: String) = assertFalse(recentRow(name, number, scope(scope)) != null)

    @Then("{word} sees record {word} in the {word} recent list")
    fun recentHasIn(name: String, number: String, scope: String) { assertTrue(recentRow(name, number, scope(scope)) != null, "Record $number staat niet in de lijst $scope") }

    private fun scope(name: String) = mapOf("own" to "mine", "complete" to "all", "to-describe" to "to-describe").getValue(name)

    @Suppress("UNCHECKED_CAST")
    private fun recentRow(name: String, number: String, scope: String): Map<String, Any?>? =
        (World.actor(name).api.read("$records?scope=$scope")["items"] as List<Map<String, Any?>>).firstOrNull { it["number"] == number }

    @Then("{word} is pointed to record {word} as the next to describe after {word}")
    fun nextToDescribe(name: String, next: String, after: String) = assertEquals(next, World.actor(name).api.read("$records/next-to-describe?after=$after")["number"])
}
