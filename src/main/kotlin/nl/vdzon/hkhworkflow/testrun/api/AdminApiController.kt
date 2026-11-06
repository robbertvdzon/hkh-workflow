package nl.vdzon.hkhworkflow.testrun.api

import nl.vdzon.hkhworkflow.items.ZcbsSession
import nl.vdzon.hkhworkflow.restapi.ApiProblem
import nl.vdzon.hkhworkflow.testrun.internal.TestRunService
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

data class SessionsResponse(val users: List<String>, val flows: List<ActiveFlowResponse>)
data class ActiveFlowResponse(val collection: String, val user: String, val since: String, val lastActive: String, val phase: Int)
data class ProgramLogResponse(val file: String, val startedAt: String?, val label: String, val running: Boolean, val exitCode: Int?, val stopped: Boolean, val duration: String?)
data class DeletedLogsResponse(val deleted: Int)

/** Voor de beheerder: wie is er bezig, en de logs van de programma's. */
@RestController
@RequestMapping("/api")
class AdminApiController(private val testRunService: TestRunService, private val items: ZcbsSession) {

    @GetMapping("/sessions")
    fun sessions() = SessionsResponse(
        items.users(),
        testRunService.active().map { ActiveFlowResponse(it.collection, it.owner, it.since.toString(), it.lastActive.toString(), it.phase) },
    )

    @DeleteMapping("/sessions/flows/{collection}/{user}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun stopFlow(@PathVariable("collection") collection: String, @PathVariable("user") user: String) {
        if (!testRunService.stop(user, collection)) throw ApiProblem(404, "flow-not-found", "Deze doorloop was er al niet meer.")
    }

    @GetMapping("/program-logs")
    fun logs(): List<ProgramLogResponse> =
        testRunService.exe().history().map { ProgramLogResponse(it.file, it.startedAt?.toString(), it.label, it.running, it.exitCode, it.stopped, it.duration) }

    @GetMapping("/program-logs/{file}", produces = ["text/plain;charset=UTF-8"])
    fun log(@PathVariable("file") file: String): String =
        testRunService.exe().logText(file) ?: throw ApiProblem(404, "log-not-found", "Logbestand niet gevonden.")

    @DeleteMapping("/program-logs/{file}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun deleteLog(@PathVariable("file") file: String) {
        if (!testRunService.exe().deleteLog(file)) throw ApiProblem(409, "log-in-use", "Dit log kan niet verwijderd worden (bestaat het niet, of draait het programma nog?).")
    }

    @DeleteMapping("/program-logs")
    fun deleteLogs() = DeletedLogsResponse(testRunService.exe().deleteAllLogs())
}
