package nl.vdzon.hkhworkflow.zcbs

/** Wat een antwoord van ZCBS is. ZCBS geeft altijd HTTP 200; alleen de tekst verraadt wat er gebeurd is. */
sealed interface Page {
    data class Form(val form: ItemForm) : Page
    data class Locked(val minutes: Int?, val by: String?) : Page
    data object NotAvailable : Page
    data object SessionExpired : Page
    data object NotLoggedIn : Page
    data object LoginOk : Page
    data object LoginFailed : Page
    data object RouterQuestion : Page
    data class Refused(val code: Int) : Page
    data object RateLimited : Page
    data class Other(val text: String) : Page
}

/** Wat de gewone weergave van een record laat zien: de titel en of het record op internet staat (null als de pagina dat niet zegt). */
data class RecordSummary(val title: String, val online: Boolean?)

