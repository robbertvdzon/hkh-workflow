package nl.vdzon.hkhworkflow.adminlog

import java.util.UUID
import tools.jackson.databind.node.ObjectNode


/** Eén melding over een doorloop (sessie), zoals het workflow-logboek van hkh-admin die verwacht. [snapshot] is alles wat de app weet. */
data class SessionReport(
    val id: UUID,
    /** NEW_FILES (nieuwe bestanden in ZCBS zetten) of EDIT (records aanpassen). */
    val kind: String,
    /** Willekeurig geheim van deze sessie; alleen wie het kent mag de sessie bijwerken. */
    val secret: String,
    val user: String?,
    val collection: String?,
    val phase: Int,
    val phaseCount: Int,
    val phaseLabel: String?,
    /** RUNNING, FINISHED of FAILED. */
    val status: String,
    val fileCount: Int,
    val event: String?,
    val snapshot: ObjectNode,
)

/** Het workflow-logboek van hkh-admin. Een storing daar houdt de app nooit op: de meldingen gaan op de achtergrond. */
interface AdminLog {
    /** Zonder adres van het logboek wordt er niets gemeld. */
    val enabled: Boolean

    /** Meldt de stand van een doorloop of bewerksessie. */
    fun session(report: SessionReport)

    /** Meldt een inlogpoging. [method] zegt hoe: met het wachtwoord of met het cookie. */
    fun login(user: String?, success: Boolean, method: String, detail: String? = null)
}
