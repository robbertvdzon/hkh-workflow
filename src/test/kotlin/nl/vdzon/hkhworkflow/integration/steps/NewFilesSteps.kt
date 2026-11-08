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
    private val flow = "/api/collections/archief/flow"
    private var liveConsole = ""

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
            listOf("kapot", "ongeldig").firstOrNull { it in name && name.endsWith(".pdf") }?.let {
                Files.writeString(target, Files.readString(target, Charsets.ISO_8859_1) + "\n% $it\n", Charsets.ISO_8859_1)
            }
            if ("kapot" in name && !name.endsWith(".pdf")) Files.writeString(target, "Dit is geen afbeelding.\n")
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
        assertEquals(200, actor.api.post("$flow/start").status, actor.last.toString())
    }

    @When("{word} opens the page for new files")
    fun opensPage(name: String) { World.actor(name).api.get(flow) }

    @When("{word} picks {string} from {word}")
    fun picks(name: String, files: String, scanner: String) { World.actor(name).api.put("$flow/files", mapOf("source" to scanner, "names" to names(files))) }

    @When("{word} picks nothing from {word}")
    fun picksNothing(name: String, scanner: String) { World.actor(name).api.put("$flow/files", mapOf("source" to scanner, "names" to emptyList<String>())) }

    @Suppress("UNCHECKED_CAST")
    @Then("{word} cannot pick {string} from {word} any more")
    fun cannotPick(name: String, file: String, scanner: String) {
        val files = World.actor(name).api.read("$flow/sources/$scanner/entries")["files"] as List<Map<String, Any?>>
        val row = files.firstOrNull { it["name"] == file } ?: error("$file staat niet in de lijst")
        assertTrue(row["processed"] != null, "$file is nog te kiezen")
    }

    @Then("{word} has chosen only {string}")
    fun chosen(name: String, file: String) = assertEquals(listOf(file), World.actor(name).api.read("$flow/files").list.map { it["name"] })

    @Then("{word} is offered {word} as the first number")
    fun offered(name: String, number: String) = assertEquals(number, World.actor(name).api.read("$flow/numbers")["suggestion"])

    @When("{word} accepts the offered numbers")
    fun acceptsNumbers(name: String) {
        val actor = World.actor(name)
        actor.api.put("$flow/numbers", mapOf("start" to actor.api.read("$flow/numbers")["suggestion"]))
        assertEquals(200, actor.last.status, actor.last.toString())
    }

    @When("{word} chooses {word} as the first number")
    fun choosesNumber(name: String, number: String) { World.actor(name).api.put("$flow/numbers", mapOf("start" to number)) }

    @When("{word} runs pdf2zcbs")
    fun runsPdf2zcbs(name: String) = run(World.actor(name), "pdf2zcbs")

    @When("{word} runs images")
    fun runsImages(name: String) = run(World.actor(name), "images")

    @When("{word} runs pdf2zcbs and follows the console live")
    fun runsLive(name: String) {
        val actor = World.actor(name)
        actor.api.post("$flow/steps/pdf2zcbs/run", mapOf("answers" to "auto"))
        liveConsole = actor.api.read("$flow/console/events").body
    }

    @Then("the live console ended with the program finished")
    fun liveEnded() {
        assertTrue(liveConsole.contains("event:console"), liveConsole)
        assertTrue(liveConsole.trim().lines().last { it.startsWith("data:") }.contains("\"running\":false"), liveConsole)
    }

    @When("{word} retries pdf2zcbs")
    fun retries(name: String) { assertEquals(200, World.actor(name).api.post("$flow/steps/pdf2zcbs/retry").status) }

    /** Handmatig uitvoeren: elke vraag afwachten in het log en dan antwoorden, zoals iemand aan de console. Het wachtwoord en de veiligheidsvragen (nooit overschrijven of wissen) beantwoordt de app zelf. */
    @When("{word} runs images by hand")
    fun runsImagesByHand(name: String) {
        val actor = World.actor(name)
        assertEquals(200, actor.api.post("$flow/steps/images/run", mapOf("answers" to "manual")).status, actor.last.toString())
        val answers = listOf(
            "indien OK" to "a", "Kies 1,2,3" to "6", "UPLOADEN" to "j", "Nummer, naam" to "4", "FTP wachtwoord" to null, "Kies de ZCBS" to "archief",
            "OVERSCHRIJVEN" to null, "AANMAKEN" to "j", "aangemaakte mappen" to null, "met bron images" to null, "PC-bestand" to "j", "DOS-window" to "",
        )
        answers.forEach { (question, answer) ->
            Await.until("de vraag \"$question\"", seconds = 30) { actor.api.read("$flow/console/log").body.contains(question) }
            if (answer != null) assertEquals(204, actor.api.post("$flow/console/input", mapOf("line" to answer)).status)
        }
        waitUntilIdle(actor)
    }

    @Given("{word} left the file {string} in the folder of pdf2zcbs")
    fun leftFile(name: String, file: String) {
        val target = TestEnvironment.work.resolve(World.actor(name).account).resolve("archief/pdf2zcbs").resolve(file)
        TestEnvironment.copy("integration/scans/scan.pdf", target)
    }

    @When("{word} moves the leftovers away")
    fun movesLeftovers(name: String) { assertEquals(200, World.actor(name).api.post("$flow/leftovers/move").status) }

    @When("{word} deletes the leftovers")
    fun deletesLeftovers(name: String) { assertEquals(200, World.actor(name).api.delete("$flow/leftovers").status) }

    @Then("the file {string} is gone from the folder of pdf2zcbs of {word}")
    fun fileGone(file: String, name: String) =
        assertFalse(Files.exists(TestEnvironment.work.resolve(World.actor(name).account).resolve("archief/pdf2zcbs").resolve(file)))

    @Then("{word} can look at {string} from {word} before choosing it")
    fun canLook(name: String, file: String, scanner: String) {
        val api = World.actor(name).api
        val view = api.read("$flow/sources/$scanner/file?name=$file")
        assertEquals(200, view.status)
        assertTrue(view.body.startsWith("%PDF"), "Geen pdf")
        val details = api.read("$flow/sources/$scanner/details")
        assertTrue(details.body.contains(file), details.body)
    }

    @When("{word} approves the result")
    fun approves(name: String) = approve(World.actor(name))

    private fun approve(actor: Actor) {
        val step = when (phase(actor)) { 4 -> "pdf2zcbs"; 5 -> "images"; else -> error("Niets te controleren in fase ${phase(actor)}") }
        assertEquals(200, actor.api.post("$flow/steps/$step/approve").status, actor.last.toString())
    }

    @When("{word} runs the upload")
    fun runsUpload(name: String) = run(World.actor(name), "upload")

    @When("{word} runs the backup")
    fun runsBackup(name: String) = run(World.actor(name), "backup")

    @When("{word} starts over")
    fun startsOver(name: String) { assertEquals(204, World.actor(name).api.delete(flow).status) }

    /** Alle stappen na de nummers, zonder iets bijzonders. */
    @When("{word} finishes all the steps")
    fun finishes(name: String) {
        val actor = World.actor(name)
        var guard = 0
        while (actor.api.read(flow)["finished"] != true && guard++ < 12) {
            val state = actor.api.read(flow)
            when {
                state["review"] == true -> approve(actor)
                else -> run(actor, mapOf(4 to "pdf2zcbs", 5 to "images", 6 to "upload", 7 to "backup").getValue(state["phase"] as Int))
            }
        }
        assertEquals(true, actor.api.read(flow)["finished"], "De flow is niet afgemaakt: ${actor.api.read("$flow/steps")}")
    }

    private fun run(actor: Actor, step: String) {
        assertEquals(200, actor.api.post("$flow/steps/$step/run", mapOf("answers" to "auto")).status, actor.last.toString())
        waitUntilIdle(actor)
    }

    /** Wacht tot het programma of de stap klaar is, en kijkt dan hoe de stappen ervoor staan. */
    private fun waitUntilIdle(actor: Actor) {
        Await.until("einde van de stap", seconds = 60) { actor.api.read(flow)["running"] == false }
        actor.api.get("$flow/steps")
    }

    private fun phase(actor: Actor) = actor.api.read(flow)["phase"] as Int

    @Then("{word} has reached step {int}")
    fun reached(name: String, step: Int) = assertEquals(step, phase(World.actor(name)), World.actor(name).last.toString())

    @Then("{word} has finished")
    fun finished(name: String) {
        val api = World.actor(name).api
        assertEquals(true, api.read(flow)["finished"], api.read("$flow/steps").toString())
        assertTrue(api.read("$flow/summary").list.isNotEmpty())
    }

    @Then("{word} is asked to check what {word} made")
    fun asksReview(name: String, step: String) {
        val api = World.actor(name).api
        assertEquals(true, api.read(flow)["review"], api.read("$flow/steps").toString())
        assertEquals(mapOf("pdf2zcbs" to 4, "images" to 5).getValue(step), api.read(flow)["phase"])
        assertTrue(api.read("$flow/results?step=$step").list.isNotEmpty())
    }

    @Then("{word} is not asked to check anything")
    fun noReview(name: String) = assertEquals(false, World.actor(name).api.read(flow)["review"])

    @Then("the step {word} has failed for {word}")
    fun stepFailed(step: String, name: String) = assertEquals("FAILED", stepStatus(name, step))

    @Then("the step {word} is waiting for {word}")
    fun stepWaiting(step: String, name: String) = assertEquals("PENDING", stepStatus(name, step))

    private fun stepStatus(name: String, step: String) = World.actor(name).api.read("$flow/steps").list.first { it["name"] == step }["status"]

    @Suppress("UNCHECKED_CAST")
    private fun checks(name: String) = World.actor(name).api.read("$flow/checks")["checks"] as List<Map<String, Any?>>

    @Then("the check {string} is red for {word}")
    fun checkRed(check: String, name: String) {
        val row = checks(name).firstOrNull { (it["label"] as String).contains(check) } ?: error("Geen controle \"$check\": ${checks(name)}")
        assertEquals(false, row["ok"], row.toString())
    }

    @Then("{word} has to fix the red checks first")
    fun mustFix(name: String) {
        val api = World.actor(name).api
        assertEquals(false, api.read("$flow/checks")["allOk"])
        assertEquals("checks-failed", api.post("$flow/start").code, api.last.toString())
    }

    @Then("all checks are green for {word}")
    fun allGreen(name: String) {
        val rows = checks(name)
        assertTrue(rows.isNotEmpty() && rows.all { it["ok"] == true }, rows.filter { it["ok"] != true }.toString())
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

    @Then("{word} can view the {word} result of record {word}")
    fun canViewResult(name: String, kind: String, number: String) = assertEquals(200, World.actor(name).api.read("$flow/results/$number/$kind").status)

    @Then("{word} cannot view the {word} result of record {word}")
    fun cannotViewResult(name: String, kind: String, number: String) = assertEquals(404, World.actor(name).api.read("$flow/results/$number/$kind").status)
}
