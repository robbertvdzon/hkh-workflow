package nl.vdzon.hkhworkflow.items

import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse

/** Het cookie waarmee de browser na een herstart van de app vanzelf weer inlogt. */
interface LoginCookie {
    /** De naam en het wachtwoord uit het cookie, of null als het er niet is of niet te lezen is. */
    fun read(request: HttpServletRequest): Pair<String, String>?

    fun forget(response: HttpServletResponse)
}
