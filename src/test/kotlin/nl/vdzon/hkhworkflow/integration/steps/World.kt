package nl.vdzon.hkhworkflow.integration.steps

import nl.vdzon.hkhworkflow.integration.Browser
import nl.vdzon.hkhworkflow.integration.Page
import nl.vdzon.hkhworkflow.integration.TestEnvironment

/** Iemand in een scenario: een eigen account in ZCBS (per scenario uniek, zodat de app niets onthoudt van een eerder scenario) en een eigen browser. */
class Actor(val name: String, val account: String, val password: String, val browser: Browser) {
    val page: Page get() = browser.page
}

/** De stand van het lopende scenario. Scenario's draaien na elkaar. */
object World {
    private var counter = 0
    lateinit var baseUrl: String
    val actors = linkedMapOf<String, Actor>()
    var lastPage: Page? = null

    fun reset() {
        actors.clear()
        lastPage = null
    }

    /** Maakt [name] aan met rechten [level] in ZCBS. */
    fun actor(name: String, level: String = "1"): Actor = actors.getOrPut(name) {
        counter++
        val account = "$name $counter"
        val password = "Test$counter"
        TestEnvironment.zcbs.addUser(account, password, level)
        Actor(name, account, password, Browser(baseUrl))
    }
}
