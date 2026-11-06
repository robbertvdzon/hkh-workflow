package nl.vdzon.hkhworkflow.zcbs

import nl.vdzon.hkhworkflow.zcbs.internal.RecordRebuilder


/** Eén naam-waardepaar zoals een browser het zou posten. Bij een meerkeuzelijst staat er één per gekozen optie. */
data class Post(val name: String, val value: String)

enum class ControlKind { TEXT, TEXTAREA, SELECT, MULTI, RADIO }

/** Een veld dat de gebruiker ziet en kan wijzigen, met de keuzes die ZCBS zelf in het formulier zet. */
data class Control(
    val name: String,
    val label: String,
    val kind: ControlKind,
    val options: List<String> = emptyList(),
    val values: List<String> = emptyList(),
    /** De helptekst die ZCBS bij het veld toont (het vraagteken), als platte tekst; leeg als er geen is. */
    val help: String = "",
) {
    val value: String get() = values.firstOrNull().orEmpty()
}

/**
 * Het bewerkformulier van één record zoals ZCBS het levert: alle velden die een browser zou posten ([posts], met de
 * verborgen velden en `a0`) en de velden die de gebruiker ziet ([controls]).
 */
class ItemForm(val number: String, val posts: List<Post>, val controls: List<Control>) {
    /** Het hele oude record, tab-gescheiden (veld 0 t/m 34). ZCBS vergelijkt hiermee of er iets is gewijzigd. */
    val a0: String get() = posts.firstOrNull { it.name == "a0" }?.value.orEmpty()
    val a0Fields: List<String> get() = a0.split("\t")

    /** De titel in het formulier. */
    val title: String get() = control("a5")?.value.orEmpty()

    /** Staat het record op internet? Null als het formulier dat niet toont. */
    val online: Boolean? get() = control("a21")?.value?.let { it.isNotBlank() && it != "0" }

    fun control(name: String): Control? = controls.firstOrNull { it.name == name }

    fun values(name: String): List<String> = posts.filter { it.name == name }.map { it.value }

    /** De huidige waarden van alle zichtbare velden, bijvoorbeeld om met een nieuwe invoer te vergelijken. */
    fun currentValues(): Map<String, List<String>> = controls.associate { it.name to it.values }

    /**
     * Zet de waarden van de opgegeven velden en laat al het andere ongemoeid. Een regeleinde in een tekstvak gaat als
     * CR+LF, zoals een browser dat doet.
     */
    fun withChanges(changes: Map<String, List<String>>): ItemForm {
        if (changes.isEmpty()) return this
        val newPosts = mutableListOf<Post>()
        val placed = mutableSetOf<String>()
        posts.forEach { post ->
            val replacement = changes[post.name]
            if (replacement == null) {
                newPosts += post
            } else if (placed.add(post.name)) {
                replacement.forEach { newPosts += Post(post.name, encodeValue(post.name, it)) }
            }
        }
        changes.forEach { (name, values) ->
            if (placed.add(name)) values.forEach { newPosts += Post(name, encodeValue(name, it)) }
        }
        val newControls = controls.map { c ->
            changes[c.name]?.let { c.copy(values = it) } ?: c
        }
        return ItemForm(number, newPosts, newControls)
    }

    private fun encodeValue(name: String, value: String): String =
        if (control(name)?.kind == ControlKind.TEXTAREA) value.replace("\r\n", "\n").replace("\r", "\n").replace("\n", "\r\n") else value

    /**
     * ZCBS bouwt het nieuwe record alleen uit de velden die het ontvangt. Velden die het formulier niet toont (bijvoorbeeld
     * veld 28) zouden dus leeg worden opgeslagen. Hier komen ze uit `a0` terug mee.
     */
    fun withMissingFields(): ItemForm {
        val known = (posts.map { it.name } + controls.map { it.name }).toSet()
        val old = a0Fields
        val extra = (3..31).filter { "a$it" !in known && it < old.size }.map { Post("a$it", old[it]) }
        return if (extra.isEmpty()) this else ItemForm(number, posts + extra, controls)
    }

    /** Velden 0 t/m 32 van het record, opgebouwd uit dit formulier zoals ZCBS dat doet bij het opslaan. */
    fun rebuilt(): List<String> = RecordRebuilder.rebuild(this)

    /** Velden waarin het opgebouwde record afwijkt van `a0`. Niet leeg: de app begrijpt dit record niet goed genoeg om het veilig op te slaan. */
    fun differencesFromRecord(): List<Int> = RecordRebuilder.differences(this)
}

