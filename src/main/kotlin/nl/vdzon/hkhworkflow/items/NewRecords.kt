package nl.vdzon.hkhworkflow.items

import java.time.LocalDateTime

/** Een record dat via de app in het archief is gezet (de flow Nieuwe bestanden). Een andere module levert ze aan via [NewRecordsSource]. */
data class NewRecord(val at: LocalDateTime, val user: String, val collection: String, val number: String, val fileName: String)

/** Levert de records die met de app zijn toegevoegd; de module `flow` kent ze van het verwerkt-log. */
fun interface NewRecordsSource {
    fun newRecords(): List<NewRecord>
}

