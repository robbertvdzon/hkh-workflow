package nl.vdzon.hkhworkflow.flow.internal

import nl.vdzon.hkhworkflow.items.NewRecord
import nl.vdzon.hkhworkflow.items.NewRecordsSource
import org.springframework.stereotype.Component

/** De records die met de flow Nieuwe bestanden zijn toegevoegd, uit het verwerkt-log, voor de lijst met recente records bij Records aanpassen. */
@Component
class ProcessedNewRecords(private val processed: ProcessedLog) : NewRecordsSource {
    override fun newRecords(): List<NewRecord> = processed.all().map { NewRecord(it.at, it.user, it.collection, it.number, it.name) }
}
