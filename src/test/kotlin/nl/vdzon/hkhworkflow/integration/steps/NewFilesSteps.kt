package nl.vdzon.hkhworkflow.integration.steps

import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import java.nio.file.Files
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import nl.vdzon.hkhworkflow.integration.TestEnvironment

class NewFilesSteps {
    private val zcbs get() = TestEnvironment.zcbs

    private fun names(list: String) = list.split(",").map { it.trim() }

    @Given("{word} scanned {string}")
    fun scanned(scanner: String, files: String) {
        names(files).forEach { name ->
            val target = TestEnvironment.scans.resolve(scanner).resolve(name)
            when {
                name.endsWith(".pdf") -> TestEnvironment.copy("integration/scans/scan.pdf", target)
                else -> TestEnvironment.copy("integration/scans/foto.jpg", target)
            }
            // De nagemaakte pdf2zcbs herkent hieraan een scan waar het programma op vastloopt.
            if ("kapot" in name) Files.writeString(target, Files.readString(target, Charsets.ISO_8859_1) + "\n% kapot\n", Charsets.ISO_8859_1)
        }
    }

    @Given("{word} has added {string} from {word} up to the end")
    fun hasAdded(name: String, files: String, scanner: String) {
        AccessSteps.login(World.actor(name))
        startsAdding(name)
        picks(name, files, scanner)
        acceptsNumbers(name)
        finishes(name)
    }

    @When("{word} starts adding new files")
    fun startsAdding(name: String) {
        val actor = World.actor(name)
        actor.browser.get("/collectie/archief")
        actor.browser.get("/nieuw")
        actor.browser.click(actor.page.button("Alles in orde"))
    }

    @When("{word} opens the page for new files")
    fun opensPage(name: String) {
        val actor = World.actor(name)
        actor.browser.get("/collectie/archief")
        actor.browser.get("/nieuw")
    }

    @When("{word} picks {string} from {word}")
    fun picks(name: String, files: String, scanner: String) {
        val actor = World.actor(name)
        val page = actor.browser.get("/nieuw?bron=$scanner")
        actor.browser.submit(page.form("/nieuw/kopieer"), mapOf("files" to names(files)))
    }

    @When("{word} picks nothing from {word}")
    fun picksNothing(name: String, scanner: String) {
        val actor = World.actor(name)
        val page = actor.browser.get("/nieuw?bron=$scanner")
        actor.browser.submit(page.form("/nieuw/kopieer"))
    }

    @Then("{word} cannot pick {string} from {word} any more")
    fun cannotPick(name: String, file: String, scanner: String) {
        val box = World.actor(name).browser.get("/nieuw?bron=$scanner").doc.selectFirst("input[name=files][value=$file]") ?: error("$file staat niet in de lijst")
        assertTrue(box.hasAttr("disabled"), "$file is nog te kiezen")
    }

    @Then("{word} is offered {word} as the first number")
    fun offered(name: String, number: String) = assertEquals(number, World.actor(name).page.doc.selectFirst("input#start")?.attr("value"), World.actor(name).page.text)

    @When("{word} accepts the offered numbers")
    fun acceptsNumbers(name: String) {
        val actor = World.actor(name)
        actor.browser.submit(actor.page.form("/nieuw/voorbereiden"))
    }

    @When("{word} chooses {word} as the first number")
    fun choosesNumber(name: String, number: String) {
        val actor = World.actor(name)
        actor.browser.submit(actor.page.form("/nieuw/voorbereiden"), mapOf("start" to listOf(number)))
    }

    @When("{word} runs pdf2zcbs")
    fun runsPdf2zcbs(name: String) = runProgram(World.actor(name))

    @When("{word} runs images")
    fun runsImages(name: String) = runProgram(World.actor(name))

    @When("{word} tries again")
    fun triesAgain(name: String) {
        val actor = World.actor(name)
        actor.browser.click(actor.page.button("Opnieuw"))
        waitUntilIdle(actor)
    }

    /** Handmatig uitvoeren: elke vraag afwachten in het log en dan antwoorden, zoals iemand aan de console. Het wachtwoord en de veiligheidsvragen (nooit overschrijven of wissen) beantwoordt de app zelf. */
    @When("{word} runs images by hand")
    fun runsImagesByHand(name: String) {
        val actor = World.actor(name)
        actor.browser.click(actor.page.button("Handmatig uitvoeren"))
        val answers = listOf(
            "indien OK" to "a", "Kies 1,2,3" to "6", "UPLOADEN" to "j", "Nummer, naam" to "4", "FTP wachtwoord" to null, "Kies de ZCBS" to "archief",
            "OVERSCHRIJVEN" to null, "AANMAKEN" to "j", "aangemaakte mappen" to null, "met bron images" to null, "PC-bestand" to "j", "DOS-window" to "",
        )
        val control = actor.browser
        answers.forEach { (question, answer) ->
            Await.until("de vraag \"$question\"", seconds = 30) { control.get("/test/log").body.contains(question) }
            if (answer != null) control.post("/test/console/input", listOf("line" to answer))
        }
        actor.browser.get("/nieuw")
        waitUntilIdle(actor)
    }

    @Given("{word} left the file {string} in the folder of pdf2zcbs")
    fun leftFile(name: String, file: String) {
        val target = TestEnvironment.work.resolve(World.actor(name).account).resolve("archief/pdf2zcbs").resolve(file)
        TestEnvironment.copy("integration/scans/scan.pdf", target)
    }

    @When("{word} moves the leftovers away")
    fun movesLeftovers(name: String) {
        val actor = World.actor(name)
        actor.browser.click(actor.page.button("Verplaats ze"))
    }

    @When("{word} deletes the leftovers")
    fun deletesLeftovers(name: String) {
        val actor = World.actor(name)
        actor.browser.click(actor.page.button("Verwijder ze"))
    }

    @Then("the file {string} is gone from the folder of pdf2zcbs of {word}")
    fun fileGone(file: String, name: String) =
        assertFalse(Files.exists(TestEnvironment.work.resolve(World.actor(name).account).resolve("archief/pdf2zcbs").resolve(file)))

    @Then("{word} can look at {string} from {word} before choosing it")
    fun canLook(name: String, file: String, scanner: String) {
        val browser = World.actor(name).browser
        val view = browser.get("/nieuw/bron/bestand?bron=$scanner&naam=$file")
        assertEquals(200, view.status)
        assertTrue(view.body.startsWith("%PDF"), "Geen pdf")
        val info = browser.get("/nieuw/bron/info?bron=$scanner")
        assertTrue(info.body.contains(file), info.body)
        browser.get("/nieuw")
    }

    @When("{word} approves the result")
    fun approves(name: String) {
        val actor = World.actor(name)
        actor.browser.click(actor.page.button("Ja, goed"))
    }

    @When("{word} runs the upload")
    fun runsUpload(name: String) = runStep(World.actor(name))

    @When("{word} runs the backup")
    fun runsBackup(name: String) = runStep(World.actor(name))

    @When("{word} starts over")
    fun startsOver(name: String) {
        val actor = World.actor(name)
        actor.browser.post("/nieuw/opnieuw")
    }

    /** Alle stappen na de nummers, zonder iets bijzonders. */
    @When("{word} finishes all the steps")
    fun finishes(name: String) {
        val actor = World.actor(name)
        var guard = 0
        while (!actor.page.text.contains("bestanden staan nu op het archief") && !actor.page.text.contains("bestand staat nu op het archief") && guard++ < 12) {
            when {
                actor.page.buttonOrNull("Ja, goed") != null -> actor.browser.click(actor.page.button("Ja, goed"))
                actor.page.buttonOrNull("Automatisch uitvoeren") != null -> runProgram(actor)
                actor.page.buttonOrNull("Uitvoeren") != null -> runStep(actor)
                else -> error("Niets te doen op de pagina:\n${actor.page.text}")
            }
        }
        val failed = actor.page.doc.select(".problem, .alert-err").text()
        assertTrue(actor.page.text.contains("op het archief"), "De flow is niet afgemaakt: $failed\n${actor.page.text}")
    }

    private fun runProgram(actor: Actor) {
        actor.browser.click(actor.page.button("Automatisch uitvoeren"))
        waitUntilIdle(actor)
    }

    private fun runStep(actor: Actor) {
        actor.browser.click(actor.page.button("Uitvoeren"))
        waitUntilIdle(actor)
    }

    /** Wacht tot het programma of de stap klaar is, door de pagina te verversen zoals de browser dat doet. */
    private fun waitUntilIdle(actor: Actor) {
        // Alleen verversen zolang de pagina zegt dat het nog bezig is: de pagina "Klaar" is maar één keer te zien.
        if (!actor.page.text.contains("Bezig…")) return
        Await.until("einde van de stap op /nieuw", seconds = 60) { !actor.browser.get("/nieuw").text.contains("Bezig…") }
    }

    @Then("{word} has reached step {int}")
    fun reached(name: String, step: Int) {
        val heading = World.actor(name).page.doc.select("h2").first()?.text().orEmpty()
        assertTrue(heading.startsWith("$step."), "Op stap \"$heading\" in plaats van $step:\n${World.actor(name).page.text}")
    }

    @Then("the check {string} is red for {word}")
    fun checkRed(check: String, name: String) {
        val row = World.actor(name).page.doc.select(".envlist li").firstOrNull { it.text().contains(check) } ?: error("Geen controle \"$check\":\n${World.actor(name).page.text}")
        assertTrue(row.hasClass("bad"), row.text())
    }

    @Then("all checks are green for {word}")
    fun allGreen(name: String) {
        val rows = World.actor(name).page.doc.select(".envlist li")
        assertTrue(rows.isNotEmpty() && rows.all { it.hasClass("ok") }, rows.filter { !it.hasClass("ok") }.joinToString("\n") { it.text() })
    }

    @Given("someone else already used number {word} in ZCBS")
    fun numberTaken(number: String) = zcbs.addRecord(number, "Door iemand anders")

    @Given("the server already has the file {string}")
    fun serverHas(path: String) = zcbs.putFile(path)

    @Then("the server has the pdf, the text and the images of record {word}")
    fun serverHasAll(number: String) {
        listOf("archief/pdf/$number.pdf", "archief/ocr/$number.txt").forEach { assertTrue(zcbs.fileExists(it), it) }
        serverHasImages(number)
    }

    @Then("the server has the images of record {word}")
    fun serverHasImages(number: String) {
        listOf("archief/small/$number.jpg", "archief/large/$number.jpg", "cgi-bin/archief/100pct/$number.jpg").forEach { assertTrue(zcbs.fileExists(it), it) }
    }

    @Then("the server has no pdf or text of record {word}")
    fun serverHasNoPdf(number: String) {
        listOf("archief/pdf/$number.pdf", "archief/ocr/$number.txt").forEach { assertFalse(zcbs.fileExists(it), it) }
    }

    @Then("the server has no files of record {word}")
    fun serverHasNothing(number: String) {
        listOf("archief/pdf/$number.pdf", "archief/ocr/$number.txt", "archief/small/$number.jpg", "archief/large/$number.jpg", "cgi-bin/archief/100pct/$number.jpg")
            .forEach { assertFalse(zcbs.fileExists(it), it) }
    }

    @Then("ZCBS has a record {word} that still has to be described")
    fun zcbsHasNew(number: String) {
        assertTrue(zcbs.record(number)[5].startsWith("Een nog niet beschreven record"), zcbs.record(number).toString())
    }

    @Then("ZCBS has no record {word}")
    fun zcbsHasNo(number: String) = assertFalse(zcbs.hasRecord(number))

    @Then("the backup has the pdf and the image of record {word}")
    fun backupHasBoth(number: String) {
        assertTrue(Files.isRegularFile(TestEnvironment.backupPdf.resolve("$number.pdf")), "pdf-back-up van $number ontbreekt")
        backupHasImage(number)
    }

    @Then("the backup has the image of record {word}")
    fun backupHasImage(number: String) = assertTrue(Files.isRegularFile(TestEnvironment.backup100pct.resolve("$number.jpg")), "100pct-back-up van $number ontbreekt")

    @Then("the originals {string} of {word} are still there")
    fun originals(files: String, scanner: String) = names(files).forEach { assertTrue(Files.isRegularFile(TestEnvironment.scans.resolve(scanner).resolve(it)), it) }

    @Then("nothing of {word} is left in the work folders")
    fun workClean(name: String) {
        val root = TestEnvironment.work.resolve(World.actor(name).account).resolve("archief")
        listOf("invoer", "werk", "pdf2zcbs/output", "images/images", "images/small", "images/large", "images/100pct").forEach { sub ->
            val dir = root.resolve(sub)
            if (Files.isDirectory(dir)) {
                val left = Files.walk(dir).use { w -> w.filter { Files.isRegularFile(it) && it.fileName.toString() !in setOf("herkomst.tsv", "sessie.json") && !it.toString().contains("/log/") }.toList() }
                assertTrue(left.isEmpty(), "Blijft staan in $sub: $left")
            }
        }
    }

    @Then("{word} sees the produced file {string} for record {word}")
    fun seesProduced(name: String, kind: String, number: String) {
        val page = World.actor(name).page
        assertTrue(page.text.contains(number) && page.text.contains(kind), page.text)
    }

    @Then("{word} can view the {word} result of record {word}")
    fun canViewResult(name: String, kind: String, number: String) {
        val actor = World.actor(name)
        val status = actor.browser.get("/nieuw/resultaat/$kind/$number").status
        actor.browser.get("/nieuw")
        assertEquals(200, status)
    }

    @Then("{word} cannot view the {word} result of record {word}")
    fun cannotViewResult(name: String, kind: String, number: String) {
        val actor = World.actor(name)
        val status = actor.browser.get("/nieuw/resultaat/$kind/$number").status
        actor.browser.get("/nieuw")
        assertEquals(404, status)
    }
}
