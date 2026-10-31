package nl.vdzon.hkhworkflow.items

import java.time.LocalTime
import nl.vdzon.hkhworkflow.zcbs.ItemForm

enum class EditStatus(val label: String, val kind: String) {
    OPEN("nog niet bewerkt", "neutral"),
    BEZIG("bezig", "busy"),
    OPGESLAGEN("opgeslagen", "ok"),
    LATER("later", "busy"),
    GEBLOKKEERD("geblokkeerd", "busy"),
    ONVEILIG("niet veilig", "err"),
    FOUT("fout", "err"),
}

/** Een record dat bewerkt moet worden. */
data class BatchRecord(
    val number: String,
    val fileName: String = "",
    val by: String = "",
    val folder: String = "",
    /** De titel en de plaatsing op internet zoals ZCBS ze liet zien toen het record werd gekozen. */
    val title: String = "",
    val online: Boolean? = null,
    /** Het bestand van het record op de site (pdf of anders de grote afbeelding), voor de popup. [mediaKind] is `pdf` of `image`. */
    val mediaUrl: String? = null,
    val mediaKind: String? = null,
)

/** Wat er van een gekozen nummer of reeks is gekomen: hoeveel records klaarstaan en welke niet bestaan. [error] is gezet als er niets klaarstaat. */
data class ChooseResult(val count: Int, val missing: List<String>, val error: String?)

class EditRecord(val number: String, val fileName: String, val by: String, val folder: String) {
    /** Staat het record op internet? Null als dat (nog) niet bekend is. */
    @Volatile var online: Boolean? = null
    @Volatile var mediaUrl: String? = null
    @Volatile var mediaKind: String? = null

    @Volatile var status: EditStatus = EditStatus.OPEN
    @Volatile var message: String? = null

    /** Het formulier zoals het bij het openen was: de basis voor de wijzigingen en voor de controle op tussentijdse wijzigingen. */
    @Volatile var baseline: ItemForm? = null

    /** Het formulier zoals het op het moment van opslaan was, als iemand anders het record intussen heeft gewijzigd. */
    @Volatile var fresh: ItemForm? = null

    /** De invoer van de gebruiker die nog niet is opgeslagen. */
    @Volatile var draft: Map<String, List<String>>? = null
    @Volatile var title: String = ""
    @Volatile var openedAt: LocalTime? = null
}

data class Change(val name: String, val label: String, val old: String, val new: String)
data class ConflictRow(val name: String, val label: String, val mine: String, val theirs: String)

sealed interface OpenOutcome {
    data class Ready(val record: EditRecord, val form: ItemForm) : OpenOutcome
    data class Blocked(val record: EditRecord, val message: String) : OpenOutcome
    data object NeedsLogin : OpenOutcome
}

sealed interface PreviewOutcome {
    data class Ok(val changes: List<Change>, val blockers: List<String>) : PreviewOutcome
    data class Failed(val message: String) : PreviewOutcome
}

sealed interface SaveOutcome {
    data object Saved : SaveOutcome
    data object NoChanges : SaveOutcome
    data class Blocked(val messages: List<String>) : SaveOutcome
    data class Locked(val message: String) : SaveOutcome
    data class Conflict(val rows: List<ConflictRow>) : SaveOutcome
    data class Failed(val message: String) : SaveOutcome
    data object NeedsLogin : SaveOutcome
}

