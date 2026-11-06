package nl.vdzon.hkhworkflow.zcbs

/** De tekens waar ZCBS (latin-1) niet mee overweg kan, en de woorden die ZCBS weigert. */
object ZcbsText {
    private val replacements = mapOf(
        '“' to "\"", '”' to "\"", '„' to "\"", '‘' to "'", '’' to "'", '‚' to "'",
        '–' to "-", '—' to "-", '…' to "...", ' ' to " ", ' ' to " ", '​' to "",
    )

    /** Vervangt veelvoorkomende tekens uit Word door tekens die in latin-1 passen. */
    fun clean(value: String): String = buildString { value.forEach { append(replacements[it] ?: it.toString()) } }

    /** @return de tekens die niet in latin-1 passen. */
    fun unencodable(value: String): List<Char> = value.filter { it.code > 0xFF }.toList().distinct()

    private val sql = Regex("""CHAR\(|UNION|\bSELECT\b|name_const|select\*from|benchmark|\bCHAR\b|CONCAT|\bFROM\b|\bNULL|\bTHEN\(""")
    private val events = Regex("""\bonload\b|\bonclick\b|\bonerror\b|\bonfocus\b|\bonselect\b|\bonsubmit\b""", RegexOption.IGNORE_CASE)
    private val files = Regex("""permit\.lst|objecten\.pl|cfg-cbs\d*\.pl""")

    /** @return het woord dat ZCBS in een veld weigert (de hele opslag mislukt dan met een lege pagina), of null. */
    fun refusedWord(value: String): String? =
        sql.find(value)?.value ?: events.find(value)?.value ?: files.find(value)?.value
}
