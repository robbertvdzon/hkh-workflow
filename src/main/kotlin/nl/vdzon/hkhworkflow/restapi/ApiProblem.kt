package nl.vdzon.hkhworkflow.restapi

/**
 * Een fout van de REST-API (zie docs/api.md): de http-status, een vaste [code] voor de frontend en een [detail] om aan de gebruiker te tonen.
 * Met [extra] gaat er meer mee, bijvoorbeeld de velden die botsen.
 */
class ApiProblem(val status: Int, val code: String, val detail: String, val extra: Map<String, Any?> = emptyMap()) : RuntimeException(detail) {
    /** Het antwoord zoals het als `application/problem+json` wordt verstuurd. */
    fun body(): Map<String, Any?> = linkedMapOf<String, Any?>("status" to status, "code" to code, "detail" to detail) + extra
}
