package nl.vdzon.hkhworkflow.integration.steps

import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import java.net.URI
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import nl.vdzon.hkhworkflow.integration.Browser
import nl.vdzon.hkhworkflow.integration.TestEnvironment

class RecordSteps {
    private val zcbs get() = TestEnvironment.zcbs

    /** De namen van de velden in de database en in het formulier. */
    private val fields = mapOf("title" to 5, "description" to 6, "keyword" to 4, "author" to 7, "date" to 8)
    private fun field(name: String) = fields[name] ?: error("Onbekend veld $name")
    private fun input(name: String) = "a" + field(name)

    @When("{word} opens record {word} for editing")
    fun opens(name: String, number: String) {
        val actor = World.actor(name)
        val list = actor.browser.get("/records")
        actor.browser.submit(list.form("/records/kies"), mapOf("nummer" to listOf(number)))
    }

    @Given("{word} has opened record {word} for editing")
    fun hasOpened(name: String, number: String) {
        AccessSteps.login(World.actor(name, "1"))
        opens(name, number)
    }

    @Then("the {word} of the form shows {string}")
    fun formShows(field: String, value: String) {
        val page = World.actors.values.last().page
        assertEquals(value, page.doc.selectFirst("[name=${input(field)}]")?.let { if (it.tagName() == "textarea") it.wholeText() else it.attr("value") }, page.text)
    }

    @Then("{word} sees the {word} {string} in the form")
    fun seesInForm(name: String, field: String, value: String) {
        val page = World.actor(name).page
        assertEquals(value, page.doc.selectFirst("[name=${input(field)}]")?.let { if (it.tagName() == "textarea") it.wholeText() else it.attr("value") }, page.text)
    }

    @When("{word} saves the {word} {string}")
    fun saves(name: String, field: String, value: String) = saveWith(name, mapOf(input(field) to listOf(value.replace("\\n", "\n"))))

    @When("{word} saves the form without changes")
    fun savesUnchanged(name: String) = saveWith(name, emptyMap())

    private fun saveWith(name: String, changes: Map<String, List<String>>) {
        val actor = World.actor(name)
        actor.browser.click(actor.page.button("Opslaan"), changes)
    }

    @When("{word} previews the {word} {string}")
    fun previews(name: String, field: String, value: String) {
        val actor = World.actor(name)
        val number = actor.page.path.split("/").last { it.isNotEmpty() && it.all(Char::isDigit) }
        val form = actor.page.form("/records/$number/opslaan")
        actor.browser.post("/records/$number/voorbeeld", actor.browser.fields(form, mapOf(input(field) to listOf(value))))
    }

    @When("{word} cancels editing")
    fun cancels(name: String) {
        val actor = World.actor(name)
        actor.browser.click(actor.page.button("Annuleren"))
    }

    @When("{word} keeps {word} own version of the conflict")
    fun keepsOwn(name: String, @Suppress("UNUSED_PARAMETER") whose: String) {
        val actor = World.actor(name)
        val button = actor.page.doc.select("button[name=keuze][value=mijn]").first() ?: error("Geen knop voor het conflict:\n${actor.page.text}")
        actor.browser.click(button)
    }

    @When("{word} takes the version from ZCBS in the conflict")
    fun takesZcbs(name: String) {
        val actor = World.actor(name)
        val button = actor.page.doc.select("button[name=keuze][value=zcbs]").first() ?: error("Geen knop voor het conflict:\n${actor.page.text}")
        actor.browser.click(button)
    }

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

    @Then("{word} sees the scan of record {word} as a pdf")
    fun seesPdf(name: String, number: String) {
        val page = World.actor(name).browser.get("/records/media?nummer=$number")
        assertTrue(page.body.contains("\"kind\":\"pdf\""), page.body)
        assertTrue(page.body.contains("/archief/pdf/$number.pdf"), page.body)
        val url = Regex("\"url\":\"([^\"]+)\"").find(page.body)!!.groupValues[1]
        val pdf = Browser(World.baseUrl).get(url)
        assertEquals(200, pdf.status)
        assertTrue(pdf.body.startsWith("%PDF"))
    }

    @Then("{word} is told that record {word} has no scan")
    fun noScan(name: String, number: String) {
        val page = World.actor(name).browser.get("/records/media?nummer=$number")
        assertTrue(page.body.contains("geen pdf of afbeelding"), page.body)
    }

    @When("{word} opens record {word} in ZCBS itself")
    fun opensInZcbs(name: String, number: String) {
        val actor = World.actor(name)
        val handoff = actor.browser.get("/records/$number/zcbs")
        assertEquals("no-store", handoff.headers["cache-control"]?.first(), "de pagina met het wachtwoord mag niet bewaard worden")
        val form = handoff.doc.selectFirst("form#f") ?: error("Geen formulier naar ZCBS:\n${handoff.text}")
        assertEquals(URI.create(zcbs.baseUrl + "/cgi-bin/archief.pl"), URI.create(form.attr("action")))
        val own = Browser(zcbs.baseUrl)
        World.lastPage = own.post(form.attr("action"), actor.browser.fields(form))
        // De echte ZCBS remt een nieuw bezoek tijdens de snelle testrun soms even af.
        if (World.lastPage!!.text.contains("Access ZCBS denied")) {
            Thread.sleep(6000)
            World.lastPage = own.post(form.attr("action"), actor.browser.fields(form))
        }
    }

    @Then("ZCBS itself shows {string}")
    fun zcbsShows(text: String) = assertTrue(World.lastPage!!.text.contains(text), World.lastPage!!.text)

    @Then("{word} sees record {word} in the recent list with title {string}")
    fun recentHas(name: String, number: String, title: String) {
        val row = recentRow(name, number, "mijn") ?: error("Record $number staat niet in de lijst:\n${World.actor(name).page.text}")
        assertTrue(row.contains(title), row)
    }

    @Then("{word} does not see record {word} in the {word} recent list")
    fun recentHasNot(name: String, number: String, scope: String) = assertFalse(recentRow(name, number, scope(scope)) != null)

    @Then("{word} sees record {word} in the {word} recent list")
    fun recentHasIn(name: String, number: String, scope: String) { assertTrue(recentRow(name, number, scope(scope)) != null, World.actor(name).page.text) }

    private fun scope(name: String) = mapOf("own" to "mijn", "complete" to "alles", "to-describe" to "beschrijven").getValue(name)

    private fun recentRow(name: String, number: String, scope: String): String? =
        World.actor(name).browser.get("/records/lijst?toon=$scope").doc.select(".rec").firstOrNull { it.selectFirst(".c-nr")?.text() == number }?.text()

    @Then("{word} is pointed to record {word} as the next to describe after {word}")
    fun nextToDescribe(name: String, next: String, after: String) {
        val page = World.actor(name).browser.get("/records/volgende?na=$after")
        assertTrue(page.text.contains("Volgende om te beschrijven: $next"), page.text)
    }
}
