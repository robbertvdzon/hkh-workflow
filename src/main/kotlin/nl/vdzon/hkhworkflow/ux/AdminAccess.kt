package nl.vdzon.hkhworkflow.ux

import jakarta.servlet.http.HttpSession
import java.security.MessageDigest
import nl.vdzon.hkhworkflow.settings.WorkflowProperties
import org.springframework.stereotype.Component

/**
 * De beheerder: wie de instellingen mag wijzigen en de technische pagina's mag gebruiken. Het wachtwoord staat in het
 * configuratiebestand van de server (`hkh.workflow.admin-password` in application.properties naast de jar). Is er geen wachtwoord
 * ingesteld, dan kan niemand als beheerder inloggen: de instellingen blijven dicht.
 */
@Component
class AdminAccess(private val properties: WorkflowProperties) {
    fun configured(): Boolean = properties.adminPassword.isNotEmpty()

    /** Klopt het wachtwoord? Vergelijkt de hashes, in constante tijd. */
    fun check(password: String): Boolean {
        if (!configured()) return false
        fun sha(s: String) = MessageDigest.getInstance("SHA-256").digest(s.toByteArray(Charsets.UTF_8))
        return MessageDigest.isEqual(sha(password), sha(properties.adminPassword))
    }

    fun isAdmin(session: HttpSession): Boolean = session.getAttribute(KEY) == true

    fun login(session: HttpSession) = session.setAttribute(KEY, true)

    fun logout(session: HttpSession) = session.removeAttribute(KEY)

    private companion object {
        const val KEY = "hkh.admin"
    }
}
