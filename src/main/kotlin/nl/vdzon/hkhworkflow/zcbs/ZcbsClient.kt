package nl.vdzon.hkhworkflow.zcbs


/** Eén veld dat ZCBS (latin-1) niet kan opslaan, met het teken. */
class UnencodableException(val field: String, val chars: List<Char>) :
    IllegalArgumentException("Veld $field bevat tekens die ZCBS niet kan opslaan: ${chars.joinToString(" ")}")

sealed interface LoginOutcome {
    data object Ok : LoginOutcome
    data class Failed(val message: String, val rateLimited: Boolean = false) : LoginOutcome
}

/** Het antwoord van de gewone weergave op de vraag of een record bestaat. */
sealed interface RecordLookup {
    data object Missing : RecordLookup
    data class Found(val summary: RecordSummary) : RecordLookup
}

/**
 * De aanroepen naar ZCBS, zoals een browser ze doet. ZCBS geeft altijd HTTP 200; alleen de tekst verraadt wat er gebeurd is, en dat is hier al vertaald
 * naar een [Page]. De client onthoudt zijn eigen inlog (cookies); elke gebruiker en collectie heeft dus een eigen client.
 */
interface ZcbsClient {
    /** Het adres van ZCBS voor mensen, bijvoorbeeld om een record in ZCBS zelf te openen. */
    fun recordUrl(number: String, edit: Boolean = false): String

    /** Logt in met naam en wachtwoord. */
    fun login(user: String, password: String): LoginOutcome

    /** Het hoogste recordnummer, ook van lege records. Alleen ingelogd; zonder inlog ontbreken de lege records. */
    fun lastNumber(): Int?

    /** De vrije recordnummers. Null als ZCBS de lijst niet geeft, bijvoorbeeld zonder inlog of zonder recht om records aan te maken. */
    fun freeNumbers(): FreeNumbers?

    /** Bestaat het record? Null als ZCBS het niet duidelijk zegt. Zet geen vergrendeling. */
    fun exists(number: String): Boolean?

    /** Zoekt het record op, zonder vergrendeling. Null als ZCBS geen duidelijk antwoord gaf. */
    fun lookup(number: String): RecordLookup?

    /** Het adres van een bestand op de site van de collectie, bijvoorbeeld `archief/pdf/11934.pdf`. */
    fun fileUrl(path: String): String

    /** Haalt een klein tekstbestand van de site op (bijvoorbeeld de ocr-tekst), of null als het er niet is. */
    fun fetchText(path: String, maxChars: Int = 800): String?

    fun fileExists(path: String): Boolean

    /** Haalt het bewerkformulier op. Dit zet de vergrendeling van ZCBS op de ingelogde gebruiker (10 minuten). */
    fun openForm(number: String): Page

    /** Slaat het formulier op. Of het gelukt is, blijkt pas uit teruglezen. */
    fun save(form: ItemForm): Page
}

/** Maakt een client voor het script van een collectie. */
fun interface ZcbsClientFactory {
    fun create(baseUrl: String, scriptPath: String): ZcbsClient
}
