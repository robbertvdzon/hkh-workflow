package nl.vdzon.hkhworkflow.items

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import nl.vdzon.hkhworkflow.adminlog.AdminLogClient
import nl.vdzon.hkhworkflow.adminlog.SessionReport
import nl.vdzon.hkhworkflow.settings.WorkflowSettings
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper

/**
 * Meldt elke doorloop van "ZCBS records aanpassen" aan het workflow-logboek van hkh-admin: de gekozen records met hun status, wat er per
 * record is gewijzigd en een tijdlijn van wat er gebeurde. Een doorloop is een sessie met een eigen uuid vanaf het moment dat records
 * zijn gekozen. De stand wordt om de paar seconden gelezen en bij elke verandering opnieuw verstuurd.
 */
@Component
class EditLogReporter(private val items: ItemEditService, private val client: AdminLogClient, private val mapper: ObjectMapper) {
    private val log = LoggerFactory.getLogger(javaClass)

    private class Session(val batch: List<EditRecord>) {
        val id: UUID = UUID.randomUUID()
        val secret: String = UUID.randomUUID().toString() + UUID.randomUUID()
        val statuses = mutableMapOf<String, EditStatus>()
        val saved = linkedMapOf<String, List<Change>>()
        var json = ""
        var lastSentMillis = 0L
        var started = false
        var finished = false
        val pendingEvents = mutableListOf<String>()
    }

    /** Per ingelogde gebruiker de sessie in het logboek; er kunnen er meer tegelijk zijn. */
    private val sessions = HashMap<String, Session>()

    /** Wat er is gewijzigd bij een record dat nu wordt opgeslagen; de melding van het opslaan neemt het mee. */
    private val upcoming = ConcurrentHashMap<String, List<Change>>()

    /** Vóór het opslaan aangeroepen: bewaart de wijzigingen om bij de melding te zetten zodra het record echt is opgeslagen. */
    fun willSave(number: String, changes: List<Change>) {
        if (client.enabled) upcoming[number] = changes
    }

    @Scheduled(initialDelay = 6_000, fixedDelay = 3_000)
    fun tick() {
        if (!client.enabled) return
        val users = items.users()
        for (user in users) {
            try {
                Current.runAs(user, WorkflowSettings.COLLECTION) { report(user) }
            } catch (e: Exception) {
                log.debug("Melding aan het workflow-logboek overgeslagen: {}", e.message)
            }
        }
        synchronized(this) { sessions.keys.retainAll(users.toSet()) }
    }

    @Synchronized
    private fun report(user: String) {
        val batch = items.records()
        if (batch.isEmpty()) { sessions.remove(user); return }
        var current = sessions[user]
        if (current == null || current.batch !== batch) { current = Session(batch); sessions[user] = current }

        val events = current.pendingEvents
        if (!current.started && events.none { it.startsWith("Gestart") }) events += "Gestart: ${batch.size} ${if (batch.size == 1) "record" else "records"} gekozen"
        batch.forEach { r ->
            // Een record dat bij de eerste keer kijken al bewerkt blijkt, telt als overgang vanaf "nog niet bewerkt".
            val before = current.statuses[r.number] ?: EditStatus.OPEN
            val status = r.status
            if (before != status) {
                events += when (status) {
                    EditStatus.OPGESLAGEN -> {
                        val changes = upcoming.remove(r.number).orEmpty()
                        if (changes.isNotEmpty()) current.saved[r.number] = changes
                        "Record ${r.number} opgeslagen" + (changes.takeIf { it.isNotEmpty() }?.let { " (gewijzigd: ${it.joinToString { c -> c.label }})" } ?: "")
                    }
                    EditStatus.BEZIG -> "Record ${r.number} geopend"
                    EditStatus.LATER -> "Record ${r.number} voor later gezet"
                    else -> "Record ${r.number}: ${status.label}" + (r.message?.let { " – ${it.take(200)}" } ?: "")
                }
            }
            current.statuses[r.number] = status
        }

        val handled = batch.count { it.status == EditStatus.OPGESLAGEN }
        val later = batch.count { it.status == EditStatus.LATER }
        val finished = batch.all { it.status == EditStatus.OPGESLAGEN || it.status == EditStatus.LATER }
        val snapshot = mapper.createObjectNode()
        snapshot.set("samenvatting", mapper.createObjectNode().apply {
            put("gebruiker", items.user.orEmpty())
            put("aantalRecords", batch.size)
            put("opgeslagen", handled)
            put("later", later)
            put("klaar", finished)
        })
        snapshot.set("records", mapper.createArrayNode().also { out ->
            batch.forEach { r ->
                out.add(mapper.createObjectNode().apply {
                    put("nummer", r.number)
                    put("bestand", r.fileName)
                    put("gescandDoor", r.by)
                    put("map", r.folder)
                    put("titel", r.title)
                    put("status", r.status.label)
                    put("melding", r.message.orEmpty())
                    put("geopend", r.openedAt?.toString().orEmpty())
                })
            }
        })
        snapshot.set("wijzigingen", mapper.createObjectNode().also { out ->
            current.saved.forEach { (number, changes) ->
                out.set(number, mapper.createArrayNode().also { arr ->
                    changes.forEach { c -> arr.add(mapper.createObjectNode().put("veld", c.label).put("oud", c.old.take(2_000)).put("nieuw", c.new.take(2_000))) }
                })
            }
        })
        val json = snapshot.toString()
        if (json == current.json && current.started) return
        val now = System.currentTimeMillis()
        if (now - current.lastSentMillis < MIN_MILLIS) return
        current.json = json
        current.lastSentMillis = now
        current.started = true
        current.finished = finished
        val report =
            SessionReport(
                id = current.id, kind = "EDIT", secret = current.secret, user = items.user, collection = null,
                phase = handled, phaseCount = batch.size, phaseLabel = "opgeslagen", status = if (finished) "FINISHED" else "RUNNING",
                fileCount = batch.size, event = events.joinToString(" · ").takeIf { it.isNotEmpty() }, snapshot = snapshot,
            )
        events.clear()
        client.session(report)
    }

    private companion object {
        const val MIN_MILLIS = 2_500L
    }
}
