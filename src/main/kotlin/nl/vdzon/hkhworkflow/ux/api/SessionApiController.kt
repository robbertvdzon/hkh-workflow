package nl.vdzon.hkhworkflow.ux.api

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import jakarta.servlet.http.HttpSession
import nl.vdzon.hkhworkflow.adminlog.AdminLog
import nl.vdzon.hkhworkflow.adminlog.AppVersion
import nl.vdzon.hkhworkflow.items.LoginCookie
import nl.vdzon.hkhworkflow.items.ZcbsSession
import nl.vdzon.hkhworkflow.restapi.ApiProblem
import nl.vdzon.hkhworkflow.settings.SettingsService
import nl.vdzon.hkhworkflow.settings.WorkflowSettings
import nl.vdzon.hkhworkflow.ux.internal.AdminAccess
import nl.vdzon.hkhworkflow.ux.internal.CurrentContextFilter
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

data class LoginRequest(val name: String, val password: String)
data class AdminLoginRequest(val password: String)

data class SessionResponse(
    val user: String?,
    val admin: Boolean,
    val collections: List<CollectionResponse>,
    val zcbs: ZcbsResponse,
    val build: BuildResponse,
)

/** Een collectie in ZCBS. [reason] zegt waarom hij niet te kiezen is. */
data class CollectionResponse(val name: String, val available: Boolean, val reason: String?)
data class ZcbsResponse(val configured: Boolean, val address: String, val realArchive: Boolean)
data class BuildResponse(val label: String, val details: String)

/** Wie er aan het woord is, en in- en uitloggen. De sessie-cookie onthoudt het. */
@RestController
@RequestMapping("/api")
class SessionApiController(
    private val items: ZcbsSession,
    private val admin: AdminAccess,
    private val settingsService: SettingsService,
    private val cookie: LoginCookie,
    private val adminLog: AdminLog,
) {
    @GetMapping("/session")
    fun session(session: HttpSession): SessionResponse {
        val settings = settingsService.get()
        val rights = items.rights()
        return SessionResponse(
            user = items.user,
            admin = admin.isAdmin(session),
            collections = if (!items.loggedIn()) emptyList() else WorkflowSettings.ALL_COLLECTIONS.map {
                when {
                    it !in rights -> CollectionResponse(it, false, "Geen rechten")
                    it !in WorkflowSettings.IMPLEMENTED_COLLECTIONS -> CollectionResponse(it, false, "Nog niet beschikbaar")
                    else -> CollectionResponse(it, true, null)
                }
            },
            zcbs = ZcbsResponse(settings.zcbsBaseUrl.isNotBlank(), settings.zcbsBaseUrl, items.loggedIn() && !settings.zcbsIsLocal()),
            build = BuildResponse(AppVersion.info.label, AppVersion.info.details),
        )
    }

    @PostMapping("/session")
    fun login(@RequestBody body: LoginRequest, response: HttpServletResponse, session: HttpSession): SessionResponse {
        val error = items.login(body.name, body.password)
        adminLog.login(body.name.trim(), error == null, "PASSWORD", error)
        if (error != null) throw if (error.startsWith("Naam of wachtwoord")) ApiProblem(401, "wrong-credentials", error) else ApiProblem(502, "zcbs-failed", error)
        session.setAttribute(CurrentContextFilter.USER, items.user)
        cookie.remember(response, body.name, body.password)
        return session(session)
    }

    @DeleteMapping("/session")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun logout(response: HttpServletResponse, session: HttpSession) {
        items.logout()
        session.removeAttribute(CurrentContextFilter.USER)
        cookie.forget(response)
    }

    @PostMapping("/admin-session")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun adminLogin(@RequestBody body: AdminLoginRequest, request: HttpServletRequest) {
        if (!admin.check(body.password)) {
            // Een foute poging kost een seconde: remt het raden van het wachtwoord.
            Thread.sleep(1000)
            throw ApiProblem(401, "wrong-credentials", if (admin.configured()) "Dat wachtwoord klopt niet." else "Er is geen beheerderswachtwoord ingesteld op de server.")
        }
        if (request.getSession(false) != null) request.changeSessionId()
        admin.login(request.session)
    }

    @DeleteMapping("/admin-session")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun adminLogout(session: HttpSession) = admin.logout(session)
}
