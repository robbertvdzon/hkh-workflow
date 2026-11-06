package nl.vdzon.hkhworkflow.restapi.internal

import io.swagger.v3.oas.annotations.Hidden
import nl.vdzon.hkhworkflow.restapi.ApiProblem
import org.slf4j.LoggerFactory
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.ErrorResponse
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.bind.annotation.RestControllerAdvice

/** Elke fout van de REST-API krijgt dezelfde vorm, zie [ApiProblem]. */
@RestControllerAdvice(annotations = [RestController::class])
class ApiExceptionHandler {
    private val log = LoggerFactory.getLogger(javaClass)

    @ExceptionHandler(Exception::class)
    fun handle(e: Exception): ResponseEntity<Map<String, Any?>> {
        val problem = when (e) {
            is ApiProblem -> e
            is IllegalArgumentException -> ApiProblem(400, "invalid-input", e.message ?: "Deze invoer klopt niet.")
            is IllegalStateException -> ApiProblem(409, "wrong-state", e.message ?: "Dit kan nu niet.")
            is ErrorResponse -> ApiProblem(e.statusCode.value(), "invalid-request", e.body.detail ?: "Dit verzoek klopt niet.")
            else -> {
                log.warn("Onverwachte fout in de REST-API", e)
                ApiProblem(500, "internal", "Er ging iets mis in de app: ${e.message ?: e.javaClass.simpleName}")
            }
        }
        return ResponseEntity.status(problem.status).contentType(MediaType.APPLICATION_PROBLEM_JSON).body(problem.body())
    }
}

/** Een adres onder `/api` dat niet bestaat is ook een fout in de vaste vorm, en niet de foutpagina van de server. */
@Hidden
@RestController
class ApiNotFoundController {
    @RequestMapping("/api/**")
    fun notFound(): Nothing = throw ApiProblem(404, "not-found", "Dit adres bestaat niet in de API.")
}
