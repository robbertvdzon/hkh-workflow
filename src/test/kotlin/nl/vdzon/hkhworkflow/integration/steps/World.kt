package nl.vdzon.hkhworkflow.integration.steps

import java.net.URLEncoder
import nl.vdzon.hkhworkflow.integration.Api
import nl.vdzon.hkhworkflow.integration.ApiResponse
import nl.vdzon.hkhworkflow.integration.Page
import nl.vdzon.hkhworkflow.integration.TestEnvironment

/** Iemand in een scenario: een eigen account in ZCBS (per scenario uniek, zodat de app niets onthoudt van een eerder scenario) en een eigen verbinding met de API. */
class Actor(val name: String, val account: String, val password: String, val api: Api) {
    val last: ApiResponse get() = api.last

    /** De laatste invoer van het formulier, om na een conflict opnieuw te versturen. */
    var values: Map<String, List<String>> = emptyMap()
}

/** De stand van het lopende scenario. Scenario's draaien na elkaar. */
object World {
    private var counter = 0
    lateinit var baseUrl: String
    val actors = linkedMapOf<String, Actor>()

    /** Wat een bezoeker zonder inlog of ZCBS zelf als laatste liet zien. */
    var lastResponse: ApiResponse? = null
    var lastPage: Page? = null

    fun reset() {
        actors.clear()
        lastResponse = null
        lastPage = null
    }

    /** Maakt [name] aan met rechten [level] in ZCBS. */
    fun actor(name: String, level: String = "1"): Actor = actors.getOrPut(name) {
        counter++
        val account = "$name $counter"
        val password = "Test$counter"
        TestEnvironment.zcbs.addUser(account, password, level)
        Actor(name, account, password, Api(baseUrl))
    }

    fun encoded(value: String): String = URLEncoder.encode(value, Charsets.UTF_8).replace("+", "%20")
}
