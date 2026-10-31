package nl.vdzon.hkhworkflow.adminlog

import tools.jackson.databind.JsonNode
import tools.jackson.databind.ObjectMapper
import tools.jackson.databind.node.ArrayNode
import tools.jackson.databind.node.ObjectNode

/**
 * Wat naar het workflow-logboek gaat, bevat nooit een wachtwoord of sleutel. Velden met een naam die op een geheim lijkt worden
 * vervangen, antwoorden van regels die als geheim zijn gemarkeerd ook, en de waarden zelf worden uit alle tekst gehaald (de uitvoer van
 * een programma kan er een bevatten). Het logboek filtert zelf ook nog een keer.
 */
class SnapshotSanitizer(private val mapper: ObjectMapper) {
    /**
     * De boom van [value] (bijvoorbeeld de instellingen) zonder geheimen, plus die geheimen om uit tekst te halen. Velden met een naam uit
     * [keep] blijven leesbaar: de beheerder kiest bewust welk wachtwoord hij in het logboek wil kunnen controleren.
     */
    fun clean(value: Any, keep: Set<String> = emptySet()): Cleaned {
        val tree = mapper.valueToTree<JsonNode>(value)
        val secrets = linkedSetOf<String>()
        collect(tree, secrets, keep)
        return Cleaned(redact(tree, keep), secrets.filter { it.length >= MIN_SECRET_LENGTH }.toSet())
    }

    class Cleaned(val tree: JsonNode, private val secrets: Set<String>) {
        /** [text] met elk geheim vervangen door sterretjes. */
        fun scrub(text: String): String = secrets.fold(text) { acc, secret -> acc.replace(secret, "***") }
    }

    private fun collect(node: JsonNode, into: MutableSet<String>, keep: Set<String>) {
        when {
            node.isObject -> {
                val markedSecret = node.get("secret")?.takeIf { it.isBoolean }?.booleanValue() == true
                node.properties().forEach { (name, value) ->
                    if (value.isString && name !in keep && (isSecretName(name) || markedSecret && name in SECRET_ANSWER_FIELDS)) into += value.stringValue()
                    collect(value, into, keep)
                }
            }
            node.isArray -> node.forEach { collect(it, into, keep) }
        }
    }

    private fun redact(node: JsonNode, keep: Set<String>): JsonNode = when {
        node.isObject -> {
            val markedSecret = node.get("secret")?.takeIf { it.isBoolean }?.booleanValue() == true
            val out: ObjectNode = mapper.createObjectNode()
            node.properties().forEach { (name, value) ->
                val hide = value.isString && name !in keep && (isSecretName(name) || markedSecret && name in SECRET_ANSWER_FIELDS)
                out.set(name, if (hide) mapper.nodeFactory.stringNode(REDACTED) else redact(value, keep))
            }
            out
        }
        node.isArray -> {
            val out: ArrayNode = mapper.createArrayNode()
            node.forEach { out.add(redact(it, keep)) }
            out
        }
        else -> node
    }

    companion object {
        const val REDACTED = "(weggelaten)"
        private const val MIN_SECRET_LENGTH = 3
        private val SECRET_ANSWER_FIELDS = setOf("answer", "value")
        private val SECRET_PARTS = listOf("password", "passwd", "wachtwoord", "secret", "token", "apikey", "api-key", "privatekey", "credential")

        fun isSecretName(name: String): Boolean {
            val n = name.lowercase()
            return SECRET_PARTS.any { it in n } || n.endsWith("key")
        }
    }
}
