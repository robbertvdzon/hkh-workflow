package nl.vdzon.hkhworkflow.zcbs

import java.nio.charset.StandardCharsets

/** Echte pagina's van de lokale ZCBS (tools/zcbs-local), in latin-1 opgeslagen zoals ZCBS ze stuurt. */
object Fixtures {
    fun page(name: String): String =
        String(Fixtures::class.java.getResourceAsStream("/zcbs/$name")!!.readAllBytes(), StandardCharsets.ISO_8859_1)

    fun form(name: String): ItemForm = FormParser.parse(page(name)) ?: error("Geen formulier in $name")
}
