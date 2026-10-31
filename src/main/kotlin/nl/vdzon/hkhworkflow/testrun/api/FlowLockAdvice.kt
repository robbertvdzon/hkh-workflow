package nl.vdzon.hkhworkflow.testrun.api

import nl.vdzon.hkhworkflow.testrun.FlowLockedException
import org.springframework.web.bind.annotation.ControllerAdvice
import org.springframework.web.bind.annotation.ExceptionHandler

/** Wie een stap probeert in een collectie die een ander in gebruik heeft, komt op de pagina terecht die zegt wie er werkt. */
@ControllerAdvice
class FlowLockAdvice {
    @ExceptionHandler(FlowLockedException::class)
    fun locked(): String = "redirect:/nieuw"
}
