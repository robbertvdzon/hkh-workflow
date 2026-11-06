package nl.vdzon.hkhworkflow.items

import nl.vdzon.hkhworkflow.zcbs.FreeNumbers

/**
 * De inlog bij ZCBS van de gebruiker die nu aan het woord is ([Current]), voor de andere modules. Iedereen heeft zijn eigen sessie, dus meer mensen
 * kunnen tegelijk werken. Het bewerken van records zelf hoort bij deze module en staat er niet in.
 */
interface ZcbsSession {
    /** De naam van wie aan het woord is en is ingelogd. */
    val user: String?

    fun loggedIn(): Boolean

    /**
     * Logt in bij ZCBS. Lukt het, dan geldt deze gebruiker voor de rest van dit verzoek als de gebruiker aan het woord.
     * @return een foutmelding, of null als de inlog gelukt is.
     */
    fun login(name: String, pw: String): String?

    /** Logt de gebruiker die aan het woord is uit bij ZCBS. */
    fun logout()

    /** De collecties waar de gebruiker rechten heeft. */
    fun rights(): List<String>

    /** Mag de gebruiker met deze collectie werken? Rechten hebben en door de app ondersteund. */
    fun canUse(collection: String): Boolean

    /** Is er een sessie voor deze naam (nog)? Na een herstart van de app niet meer. */
    fun hasSession(user: String): Boolean

    /** De namen van wie nu is ingelogd. */
    fun users(): List<String>

    /** De vrije recordnummers uit ZCBS, of null als ZCBS ze niet geeft. */
    fun freeNumbers(): FreeNumbers?

    /** Welke van deze nummers (nog) niet bestaan in ZCBS. */
    fun missingInZcbs(numbers: List<String>): List<String>
}
