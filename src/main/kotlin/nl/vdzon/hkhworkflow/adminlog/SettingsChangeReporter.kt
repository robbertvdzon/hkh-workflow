package nl.vdzon.hkhworkflow.adminlog

import nl.vdzon.hkhworkflow.settings.SettingsChanged
import org.slf4j.LoggerFactory
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component
import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper
import tools.jackson.databind.node.ObjectNode

/**
 * Meldt elke wijziging van de instellingen aan het workflow-logboek van hkh-admin: wie en waarom, per veld de oude en de nieuwe waarde, en de
 * volledige nieuwe instellingen. Geheimen worden weggelaten ([SnapshotSanitizer]); alleen het ftp-wachtwoord is leesbaar als de beheerder dat
 * zelf heeft aangezet (`logFtpPassword`), net als bij de doorlopen. Dat een wachtwoord veranderde, staat er altijd bij.
 */
@Component
class SettingsChangeReporter(private val mapper: ObjectMapper, private val client: AdminLogClient) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val sanitizer = SnapshotSanitizer(mapper)

    @EventListener
    fun changed(event: SettingsChanged) {
        if (!client.enabled) return
        try {
            val keep = if (event.after.logFtpPassword) setOf("ftpPassword") else emptySet()
            val changes = changes(mapper.valueToTree(event.before), mapper.valueToTree(event.after), keep)
            if (changes.isEmpty()) return
            val body = mapper.createObjectNode().apply {
                put("reason", event.reason.take(200))
                put("user", event.by.orEmpty())
                put("address", event.address.orEmpty())
                set("changes", mapper.createArrayNode().also { list -> changes.forEach { list.add(it) } })
                set("settings", sanitizer.clean(event.after, keep).tree)
            }
            client.settingsChange(body)
        } catch (e: Exception) {
            log.debug("Melding van de gewijzigde instellingen overgeslagen: {}", e.message)
        }
    }

    /** Per gewijzigd veld `{field, old, new}`; geneste velden heten bijvoorbeeld `collections.archief.imagesResolution`. */
    internal fun changes(before: JsonNode, after: JsonNode, keep: Set<String>): List<ObjectNode> {
        val a = flatten(before)
        val b = flatten(after)
        return (a.keys + b.keys).filter { it !in DERIVED && a[it] != b[it] }.sorted().map { path ->
            val hide = SnapshotSanitizer.isSecretName(path.substringAfterLast('.')) && path.substringAfterLast('.') !in keep
            mapper.createObjectNode().apply {
                put("field", path)
                put("old", if (hide) SnapshotSanitizer.REDACTED else a[path])
                put("new", if (hide) SnapshotSanitizer.REDACTED else b[path])
            }
        }
    }

    private fun flatten(node: JsonNode, prefix: String = "", into: MutableMap<String, String?> = linkedMapOf()): Map<String, String?> {
        if (node.isObject) node.properties().forEach { (name, value) -> flatten(value, if (prefix.isEmpty()) name else "$prefix.$name", into) }
        else into[prefix] = if (node.isNull) null else if (node.isString) node.stringValue() else node.toString()
        return into
    }

    private companion object {
        /** Wat uit de keuze voor de test-ZCBS volgt of altijd hetzelfde is; het wijzigt nooit los van `useTestZcbs`. */
        val DERIVED = setOf("zcbsBaseUrl", "ftpHost", "ftpPort", "ftpUser", "ftpBaseDir", "collection")
    }
}
