package nl.vdzon.hkhworkflow.testrun

/**
 * De stand van een doorloop zoals hij op schijf wordt bewaard (`sessie.json` in de map van de gebruiker), zodat hij een herstart van de app
 * overleeft. Wat een programma op dat moment deed, is dan verloren: zo'n stap staat na het herstellen op mislukt, en kan opnieuw.
 */
data class FlowState(
    val owner: String,
    val collection: String,
    val startNumber: String?,
    val files: List<StateFile>,
    val steps: List<StateStep>,
    val gated: Boolean,
    val reviewed: List<String>,
    val started: Boolean,
    val filesConfirmed: Boolean,
    val inputAcceptedFor: String?,
    val finishedShown: Boolean,
    val claimedSince: String?,
)

data class StateFile(val index: Int, val original: String, val number: String, val by: String, val folder: String)

data class StateStep(val id: Int, val type: String, val fileIndexes: List<Int>, val status: String, val message: String?, val uploaded: List<String>)
