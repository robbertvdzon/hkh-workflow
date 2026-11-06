package nl.vdzon.hkhworkflow.ux.internal

import jakarta.servlet.DispatcherType
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import nl.vdzon.hkhworkflow.adminlog.AdminLog
import nl.vdzon.hkhworkflow.items.Current
import nl.vdzon.hkhworkflow.items.LoginCookie
import nl.vdzon.hkhworkflow.items.ZcbsSession
import nl.vdzon.hkhworkflow.restapi.ApiProblem
import org.springframework.context.annotation.Configuration
import org.springframework.web.servlet.HandlerInterceptor
import org.springframework.web.servlet.config.annotation.InterceptorRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer
import tools.jackson.databind.ObjectMapper

/**
 * Wie waar mag komen. De pagina's van Carla vragen dat je bent ingelogd bij ZCBS; de instellingen en de technische pagina's
 * vragen de beheerder. De stappen die de pagina Nieuwe bestanden en de technische pagina delen (de console, het uitvoeren van een
 * stap) mogen met een van beide.
 */
@Configuration
class WebConfig(
    private val items: ZcbsSession,
    private val admin: AdminAccess,
    private val mapper: ObjectMapper,
    private val remember: LoginCookie,
    private val adminLog: AdminLog,
) : WebMvcConfigurer {

    private val zcbsOnly = listOf("/collectie", "/nieuw", "/records")
    private val apiAdminOnly = listOf("/api/settings", "/api/sessions", "/api/version", "/api/program-logs")
    private val adminOnly = listOf("/instellingen", "/test/runs", "/test/start", "/test/reset")
    private val shared = listOf("/test")

    private fun matches(path: String, prefix: String) = path == prefix || path.startsWith("$prefix/")

    /** Na een herstart van de app: weer inloggen met wat in het cookie staat. Klopt het wachtwoord niet meer, dan gaat het cookie weg. */
    private fun autoLogin(request: HttpServletRequest, response: HttpServletResponse, path: String) {
        if (items.loggedIn() || path == "/zcbs/login" || path == "/zcbs/logout" || (path == "/api/session" && request.method != "GET")) return
        val (name, password) = remember.read(request) ?: return
        val error = items.login(name, password)
        adminLog.login(name, error == null, "COOKIE", error)
        if (error == null) request.session.setAttribute(CurrentContextFilter.USER, items.user)
        else if (error.startsWith("Naam of wachtwoord")) remember.forget(response)
    }

    /** De REST-API stuurt nooit door: hij zegt met de status en een vaste [ApiProblem]-vorm waarom het niet mag. */
    private fun apiAllowed(path: String, isAdmin: Boolean, response: HttpServletResponse): Boolean {
        val problem = when {
            apiAdminOnly.any { matches(path, it) } && !isAdmin -> ApiProblem(403, "admin-required", "Hiervoor moet je als beheerder zijn ingelogd.")
            path.startsWith("/api/collections/") && !items.loggedIn() -> ApiProblem(401, "not-logged-in", "Log eerst in bij ZCBS.")
            path.startsWith("/api/collections/") && !items.canUse(Current.collection()) -> ApiProblem(403, "no-rights", "Je kunt niet werken met de collectie ${Current.collection()}.")
            else -> return true
        }
        response.status = problem.status
        response.contentType = "application/problem+json"
        response.characterEncoding = "UTF-8"
        response.writer.write(mapper.writeValueAsString(problem.body()))
        return false
    }

    override fun addInterceptors(registry: InterceptorRegistry) {
        registry.addInterceptor(object : HandlerInterceptor {
            override fun preHandle(request: HttpServletRequest, response: HttpServletResponse, handler: Any): Boolean {
                // Een stream (de console) die klaar is, komt hier nog een keer langs; dan is er niets meer te controleren.
                if (request.dispatcherType == DispatcherType.ASYNC) return true
                val path = request.requestURI.removePrefix(request.contextPath)
                autoLogin(request, response, path)
                val isAdmin = admin.isAdmin(request.session)
                if (path.startsWith("/api/")) return apiAllowed(path, isAdmin, response)
                val needsAdmin = adminOnly.any { matches(path, it) } || path == "/test"
                val needsEither = !needsAdmin && shared.any { matches(path, it) }
                val needsZcbs = zcbsOnly.any { matches(path, it) }
                // Een verzoek van htmx (een stuk van de pagina dat later wordt opgehaald) volgt een redirect niet in de pagina, maar laat de hele pagina navigeren.
                fun redirect(target: String) {
                    if (request.getHeader("HX-Request") != null) { response.setHeader("HX-Redirect", request.contextPath + target); response.status = 200 }
                    else response.sendRedirect(request.contextPath + target)
                }
                when {
                    needsAdmin && !isAdmin -> redirect("/beheer?terug=" + java.net.URLEncoder.encode(path, Charsets.UTF_8))
                    needsEither && !isAdmin && !items.loggedIn() -> redirect("/?inloggen=1")
                    needsZcbs && !items.loggedIn() -> redirect("/?inloggen=1")
                    else -> return true
                }
                return false
            }
        }).excludePathPatterns("/css/**", "/js/**", "/favicon*", "/app", "/app/**")
    }
}
