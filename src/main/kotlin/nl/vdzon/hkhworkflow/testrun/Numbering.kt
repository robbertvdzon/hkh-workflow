package nl.vdzon.hkhworkflow.testrun

/**
 * Volgnummers vanaf [start], steeds één hoger. Voorloopnullen blijven behouden ("01650" → "01651").
 */
internal fun sequenceNumbers(start: String, count: Int): List<String> {
    val trimmed = start.trim()
    require(trimmed.matches(Regex("\\d+"))) { "Het volgnummer mag alleen cijfers bevatten." }
    val width = trimmed.length
    val base = trimmed.toBigInteger()
    return (0 until count).map { (base + it.toBigInteger()).toString().padStart(width, '0') }
}
