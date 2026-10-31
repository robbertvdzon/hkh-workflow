package nl.vdzon.hkhworkflow.items

import nl.vdzon.hkhworkflow.settings.WorkflowSettings

/**
 * Wie er nu aan het woord is: de ZCBS-naam van de gebruiker en de collectie waarmee hij of zij werkt. Bij een verzoek zet de app dit uit de
 * browsersessie; een achtergronddraad (een programma dat klaar is, de meldingen aan het logboek) geeft het zelf mee met [runAs].
 * Zonder gebruiker (de technische pagina van de beheerder) is [user] leeg.
 */
object Current {
    data class Context(val user: String?, val collection: String)

    private val holder = ThreadLocal<Context?>()

    fun user(): String? = holder.get()?.user

    fun collection(): String = holder.get()?.collection ?: WorkflowSettings.COLLECTION

    fun snapshot(): Context? = holder.get()

    fun set(context: Context?) {
        if (context == null) holder.remove() else holder.set(context)
    }

    /** Voert [block] uit als [user] in [collection], en zet daarna terug wat er eerst was. */
    fun <T> runAs(user: String?, collection: String, block: () -> T): T {
        val before = holder.get()
        holder.set(Context(user, collection))
        try {
            return block()
        } finally {
            if (before == null) holder.remove() else holder.set(before)
        }
    }
}
