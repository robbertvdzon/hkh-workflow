package nl.vdzon.hkhworkflow.update.api

import jakarta.servlet.http.HttpServletRequest
import nl.vdzon.hkhworkflow.restapi.ApiProblem
import nl.vdzon.hkhworkflow.settings.SettingsService
import nl.vdzon.hkhworkflow.update.internal.UpdatePhase
import nl.vdzon.hkhworkflow.update.internal.UpdateService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

data class VersionResponse(
    val current: CurrentBuildResponse,
    val runsAsService: Boolean,
    val latest: LatestBuildResponse?,
    val newer: Boolean,
    val checkedAt: String?,
    val checkError: String?,
    /** Wat er nu loopt en het bijwerken tegenhoudt. */
    val busy: List<String>,
    val nightly: Boolean,
    val nightlyPending: Boolean,
    val phase: UpdatePhase,
    val percent: Int?,
    val downloadedBytes: Long,
    val totalBytes: Long,
    val failure: String?,
    val events: List<String>,
)

data class CurrentBuildResponse(val label: String, val details: String)
data class LatestBuildResponse(val number: Int, val publishedAt: String?)
data class NightlyRequest(val enabled: Boolean)

/** Welke build draait, welke staat er klaar op GitHub, en bijwerken. */
@RestController
@RequestMapping("/api/version")
class VersionApiController(private val updates: UpdateService, private val settingsService: SettingsService) {

    @GetMapping
    fun get(): VersionResponse {
        val s = updates.status()
        return VersionResponse(
            CurrentBuildResponse(s.current.label, s.current.details), s.runsAsService,
            s.latest?.let { LatestBuildResponse(it.number, it.publishedAt?.toString()) }, s.newer, s.checkedAt?.toString(), s.checkError,
            s.busy, s.nightly, s.nightlyPending, s.phase, s.percent, s.downloaded, s.total, s.failure, s.events,
        )
    }

    /** Het resultaat, ook als GitHub niet te bereiken is, staat in [VersionResponse.checkError]. */
    @PostMapping("/check")
    fun check(): VersionResponse {
        updates.check()
        return get()
    }

    /** Het downloaden loopt op de achtergrond; [get] geeft de voortgang. */
    @PostMapping("/update")
    fun update(request: HttpServletRequest): VersionResponse {
        val problem = updates.updateNow(request.session.getAttribute("zcbsUser") as? String ?: "de beheerder")
        if (problem != null) throw ApiProblem(409, "update-refused", problem)
        return get()
    }

    @PutMapping("/nightly")
    fun nightly(@RequestBody body: NightlyRequest, request: HttpServletRequest): VersionResponse {
        settingsService.save(
            settingsService.get().copy(nightlyUpdate = body.enabled),
            reason = if (body.enabled) "Nachtelijke update aangezet" else "Nachtelijke update uitgezet",
            by = request.session.getAttribute("zcbsUser") as? String,
            address = request.remoteAddr,
        )
        return get()
    }
}
