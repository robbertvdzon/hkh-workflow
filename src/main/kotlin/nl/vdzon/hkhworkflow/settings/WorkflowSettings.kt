package nl.vdzon.hkhworkflow.settings

/**
 * Instellingen van de workflow. Alle paden zijn absolute paden op de machine waar de jar draait.
 */
data class WorkflowSettings(
    /** Map waar de te verwerken pdf's staan (voor de testpagina). */
    val testFolder: String = "",
    /** Map waar de originele bestanden na verwerking naartoe worden verplaatst. */
    val archivedFolder: String = "",
    /** Volledig pad naar images.exe. */
    val imagesExe: String = "",
    /** Volledig pad naar pdf2zcbs.exe. */
    val pdf2zcbsExe: String = "",
)
