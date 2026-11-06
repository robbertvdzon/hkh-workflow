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
 * Wie wat mag in de REST-API. De gegevens van een collectie vragen dat je bent ingelogd bij ZCBS; de instellingen en het beheer vragen de beheerder.
 * De API stuurt nooit door: hij zegt met de status en een vaste [ApiProblem]-vorm waarom het niet mag.
 */
@Configuration
class WebConfig(
    private val items: ZcbsSession,
    private val admin: AdminAccess,
    private val mapper: ObjectMapper,
    private val remember: LoginCookie,
    private val adminLog: AdminLog,
) : WebMvcConfigurer {

    private val adminOnly = listOf("/api/settings", "/api/sessions", "/api/version", "/api/program-logs")

    private fun matches(path: String, prefix: String) = path == prefix || path.startsWith("$prefix/")

    /** Na een herstart van de app: weer inloggen met wat in het cookie staat. Klopt het wachtwoord niet meer, dan gaat het cookie weg. */
    private fun autoLogin(request: HttpServletRequest, response: HttpServletResponse, path: String) {
        if (items.loggedIn() || (path == "/api/session" && request.method != "GET")) return
        val (name, password) = remember.read(request) ?: return
        val error = items.login(name, password)
        adminLog.login(name, error == null, "COOKIE", error)
        if (error == null) request.session.setAttribute(CurrentContextFilter.USER, items.user)
        else if (error.startsWith("Naam of wachtwoord")) remember.forget(response)
    }

    private fun problemFor(path: String, isAdmin: Boolean): ApiProblem? = when {
        adminOnly.any { matches(path, it) } && !isAdmin -> ApiProblem(403, "admin-required", "Hiervoor moet je als beheerder zijn ingelogd.")
        path.startsWith("/api/collections/") && !items.loggedIn() -> ApiProblem(401, "not-logged-in", "Log eerst in bij ZCBS.")
        path.startsWith("/api/collections/") && !items.canUse(Current.collection()) -> ApiProblem(403, "no-rights", "Je kunt niet werken met de collectie ${Current.collection()}.")
        else -> null
    }

    override fun addInterceptors(registry: InterceptorRegistry) {
        registry.addInterceptor(object : HandlerInterceptor {
            override fun preHandle(request: HttpServletRequest, response: HttpServletResponse, handler: Any): Boolean {
                // Een stream (de console) die klaar is, komt hier nog een keer langs; dan is er niets meer te controleren.
                if (request.dispatcherType == DispatcherType.ASYNC) return true
                val path = request.requestURI.removePrefix(request.contextPath)
                autoLogin(request, response, path)
                val problem = problemFor(path, admin.isAdmin(request.session)) ?: return true
                response.status = problem.status
                response.contentType = "application/problem+json"
                response.characterEncoding = "UTF-8"
                response.writer.write(mapper.writeValueAsString(problem.body()))
                return false
            }
        }).addPathPatterns("/api/**")
    }
}
