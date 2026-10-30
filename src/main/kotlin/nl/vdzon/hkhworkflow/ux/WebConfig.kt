package nl.vdzon.hkhworkflow.ux

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import nl.vdzon.hkhworkflow.items.ItemEditService
import org.springframework.context.annotation.Configuration
import org.springframework.web.servlet.HandlerInterceptor
import org.springframework.web.servlet.config.annotation.InterceptorRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

/**
 * Wie waar mag komen. De pagina's van Carla vragen dat je bent ingelogd bij ZCBS; de instellingen en de technische pagina's
 * vragen de beheerder. De stappen die de pagina Nieuwe bestanden en de technische pagina delen (de console, het uitvoeren van een
 * stap) mogen met een van beide.
 */
@Configuration
class WebConfig(private val items: ItemEditService, private val admin: AdminAccess) : WebMvcConfigurer {

    private val zcbsOnly = listOf("/collectie", "/nieuw", "/records", "/verwerkt")
    private val adminOnly = listOf("/instellingen", "/test/runs", "/test/start", "/test/reset")
    private val shared = listOf("/test")

    private fun matches(path: String, prefix: String) = path == prefix || path.startsWith("$prefix/")

    override fun addInterceptors(registry: InterceptorRegistry) {
        registry.addInterceptor(object : HandlerInterceptor {
            override fun preHandle(request: HttpServletRequest, response: HttpServletResponse, handler: Any): Boolean {
                val path = request.requestURI.removePrefix(request.contextPath)
                val isAdmin = admin.isAdmin(request.session)
                val needsAdmin = adminOnly.any { matches(path, it) } || path == "/test"
                val needsEither = !needsAdmin && shared.any { matches(path, it) }
                val needsZcbs = zcbsOnly.any { matches(path, it) }
                when {
                    needsAdmin && !isAdmin -> response.sendRedirect(request.contextPath + "/beheer?terug=" + java.net.URLEncoder.encode(path, Charsets.UTF_8))
                    needsEither && !isAdmin && !items.loggedIn() -> response.sendRedirect(request.contextPath + "/?inloggen=1")
                    needsZcbs && !items.loggedIn() -> response.sendRedirect(request.contextPath + "/?inloggen=1")
                    else -> return true
                }
                return false
            }
        })
    }
}
