package nl.vdzon.hkhworkflow.ux

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import nl.vdzon.hkhworkflow.items.Current
import nl.vdzon.hkhworkflow.items.ItemEditService
import nl.vdzon.hkhworkflow.settings.WorkflowSettings
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

/**
 * Zet bij elk verzoek wie er aan het woord is ([Current]): de ZCBS-naam die bij de browsersessie hoort en de collectie waarmee hij of zij werkt.
 * Is de sessie van die naam er niet meer (de app is herstart, of hij is elders uitgelogd), dan geldt de gebruiker als niet ingelogd.
 */
@Component
class CurrentContextFilter(private val items: ItemEditService) : OncePerRequestFilter() {
    override fun doFilterInternal(request: HttpServletRequest, response: HttpServletResponse, chain: FilterChain) {
        val session = request.getSession(false)
        val user = (session?.getAttribute(USER) as? String)?.takeIf { items.hasSession(it) }
        if (session != null && user == null) session.removeAttribute(USER)
        val collection = (session?.getAttribute(COLLECTION) as? String) ?: WorkflowSettings.COLLECTION
        Current.set(Current.Context(user, collection))
        try {
            chain.doFilter(request, response)
        } finally {
            Current.set(null)
        }
    }

    companion object {
        /** De naam in de browsersessie van wie is ingelogd. */
        const val USER = "zcbsUser"

        /** De collectie waarmee in de browsersessie wordt gewerkt. */
        const val COLLECTION = "collectie"
    }
}
