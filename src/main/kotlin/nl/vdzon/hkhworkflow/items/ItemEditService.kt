package nl.vdzon.hkhworkflow.items

import java.util.concurrent.ConcurrentHashMap
import nl.vdzon.hkhworkflow.settings.SettingsService
import nl.vdzon.hkhworkflow.zcbs.Control
import nl.vdzon.hkhworkflow.zcbs.FreeNumbers
import org.springframework.stereotype.Service

/**
 * De ZCBS-sessies van alle gebruikers die nu zijn ingelogd, elk met hun eigen inlog en gekozen records ([EditSession]). Elke aanroep
 * gaat naar de sessie van de gebruiker die nu aan het woord is ([Current]); zonder inlog geeft hij een "niet ingelogd"-antwoord. Zo kunnen
 * meer mensen tegelijk werken zonder dat de een de ander uitlogt.
 */
@Service
class ItemEditService(private val settingsService: SettingsService) {
    private val sessions = ConcurrentHashMap<String, EditSession>()

    private fun key(name: String) = name.trim().lowercase()

    private fun session(): EditSession? = Current.user()?.let { sessions[key(it)] }

    /** De sessie van [user], los van wie er nu aan het woord is (voor achtergronddraden). */
    fun sessionOf(user: String): EditSession? = sessions[key(user)]

    /** De namen van wie nu is ingelogd. */
    fun users(): List<String> = sessions.values.mapNotNull { it.user }.sorted()

    /** Is er een ZCBS-sessie voor deze naam (nog)? Na een herstart van de app niet meer. */
    fun hasSession(user: String): Boolean = sessions[key(user)]?.loggedIn() == true

    val user: String? get() = session()?.user

    fun loggedIn(): Boolean = session()?.loggedIn() == true

    fun rights(): List<String> = session()?.rights().orEmpty()

    fun canUse(collection: String): Boolean = session()?.canUse(collection) == true

    /**
     * Logt in bij ZCBS. Lukt het, dan is dit de sessie van de gebruiker en geldt hij voor de rest van dit verzoek als de gebruiker aan het woord.
     * @return een foutmelding, of null als de inlog gelukt is.
     */
    fun login(name: String, pw: String): String? {
        val existing = sessions[key(name)]
        val candidate = existing ?: EditSession(settingsService)
        val error = candidate.login(name, pw)
        if (error == null) {
            sessions[key(name)] = candidate
            Current.set(Current.Context(candidate.user, Current.collection()))
        }
        return error
    }

    fun logout() {
        val current = Current.user() ?: return
        sessions.remove(key(current))?.logout()
        Current.set(Current.Context(null, Current.collection()))
    }

    fun startBatch(records: List<BatchRecord>) { session()?.startBatch(records) }
    fun records(): List<EditRecord> = session()?.records().orEmpty()
    fun record(number: String): EditRecord? = session()?.record(number)
    fun statusOf(number: String): EditStatus? = session()?.statusOf(number)
    fun handledCount(): Int = session()?.handledCount() ?: 0
    fun laterCount(): Int = session()?.laterCount() ?: 0
    fun allHandled(): Boolean = session()?.allHandled() == true
    fun recordUrl(number: String, edit: Boolean = true): String? = session()?.recordUrl(number, edit)
    fun pdfUrl(number: String): String? = session()?.pdfUrl(number)
    fun imageUrl(number: String): String? = session()?.imageUrl(number)
    fun nextNumber(number: String): String? = session()?.nextNumber(number)
    fun ocrText(number: String): String? = session()?.ocrText(number)
    fun freeNumbers(): FreeNumbers? = session()?.freeNumbers()
    fun missingInZcbs(numbers: List<String>): List<String> = session()?.missingInZcbs(numbers) ?: numbers
    fun chooseRecords(input: String): ChooseResult = session()?.chooseRecords(input) ?: ChooseResult(0, emptyList(), NOT_LOGGED_IN)
    fun open(number: String): OpenOutcome = session()?.open(number) ?: OpenOutcome.NeedsLogin
    fun preview(number: String, values: Map<String, List<String>>): PreviewOutcome = session()?.preview(number, values) ?: PreviewOutcome.Failed(NOT_LOGGED_IN)
    fun later(number: String) { session()?.later(number) }
    fun controlsFor(number: String): List<Control> = session()?.controlsFor(number).orEmpty()
    fun save(number: String, values: Map<String, List<String>>, force: Boolean = false): SaveOutcome = session()?.save(number, values, force) ?: SaveOutcome.NeedsLogin
    fun resolveConflict(number: String, keepMine: Boolean, values: Map<String, List<String>>): SaveOutcome =
        session()?.resolveConflict(number, keepMine, values) ?: SaveOutcome.NeedsLogin
    fun openedUntil(number: String): String? = session()?.openedUntil(number)
    fun openedAt(number: String): String? = session()?.openedAt(number)
    fun isMulti(number: String, name: String): Boolean = session()?.isMulti(number, name) == true

    private companion object {
        const val NOT_LOGGED_IN = "Je bent niet ingelogd bij ZCBS."
    }
}
