package nl.vdzon.hkhworkflow.zcbs.internal

import nl.vdzon.hkhworkflow.zcbs.ItemForm

/**
 * Bouwt uit de velden van het formulier het record op zoals ZCBS dat zelf doet bij het opslaan (`OUTPUT_UPDATE` in
 * objecten.pl). Voor een ongewijzigd formulier moet dat hetzelfde zijn als `a0`; is het anders, dan begrijpt de app
 * het record niet goed genoeg om het veilig op te slaan.
 */
object RecordRebuilder {
    private val word = Regex("[A-Za-z0-9_]")
    private val subfields = mapOf(
        7 to "a7x", 8 to "a8x", 9 to "a9x", 10 to "a10x", 12 to "a12x", 13 to "a13x", 14 to "a14x", 15 to "a15x", 16 to "a16x",
        17 to "a17x", 18 to "a18x", 20 to "a20x", 22 to "a22x", 24 to "a24x", 26 to "a26x", 30 to "a30x",
    )

    /** Velden 0 t/m 32 van het record. (Veld 33 en 34 zijn de stempels, die ZCBS zelf zet.) */
    fun rebuild(form: ItemForm): List<String> {
        val old = form.a0Fields
        fun one(name: String) = form.values(name).firstOrNull().orEmpty()
        val a = Array(32) { i ->
            if (i < 3) "" else form.values("a$i").joinToString("\u0000")
        }
        for (i in 3..31) {
            val b = one("a${i}b")
            if (word.containsMatchIn(b)) a[i] = a[i].replaceFirst("== Anders ==", b)
        }
        subfields.forEach { (i, x) ->
            val extra = one(x)
            if (word.containsMatchIn(extra)) a[i] = a[i] + "|" + extra
        }
        for (i in 3..31) a[i] = a[i].replace('\t', ' ')
        val record = listOf(old.getOrElse(0) { "" }, old.getOrElse(1) { "" }, one("ident")) + (3..31).map { a[it] } +
            listOf(one("a32"), one("a33"), one("a34"))
        var t = record.joinToString("\t")
        t = t.replace(Regex("\u0000 *\t"), "\t")
        t = t.replace("\u0000", "; ")
        t = t.replace("\n", "<br>").replace("\r", " ")
        t = t.replace(Regex(" +"), " ").replace(Regex(" +\t"), "\t").replace(Regex("\t[; ]+"), "\t")
        t = t.replace("== Nog onbekend ==", "")
        t = t.replace(Regex("==Maak uw keuze==[; ]+"), "").replace(Regex("[; ]+==Maak uw keuze=="), "")
        t = t.replace("==Maak uw keuze==", "").replace("== Anders ==", "").replace("&quot;", "\"")
        return t.split("\t").take(33)
    }

    /** Velden (0-gebaseerd) waarin het opgebouwde record afwijkt van `a0`, zonder de bekende verschillen die geen fout zijn. */
    fun differences(form: ItemForm): List<Int> {
        val built = rebuild(form)
        val old = form.a0Fields.take(33)
        return built.indices.filter { i -> normalize(i, built[i]) != normalize(i, old.getOrElse(i) { "" }) }
    }

    /** Een leeg record heeft veld 21 leeg, maar het formulier toont "neen" (0); een regeleinde krijgt van een browser een spatie ervoor. */
    private fun normalize(index: Int, value: String): String {
        var v = value.replace(Regex(" *<br> *"), "<br>")
        if (index == 21 && v == "0") v = ""
        return v
    }
}

