package nl.vdzon.hkhworkflow.settings

/**
 * Een automatisch antwoord: als de uitvoer van het programma [pattern] bevat (hoofdletters maken niet
 * uit), stuurt de app [answer] plus Enter. Een leeg antwoord is gewoon Enter. [secret] = wachtwoord:
 * wordt nergens getoond of gelogd.
 */
data class AnswerRule(
    val pattern: String = "",
    val answer: String = "",
    val secret: Boolean = false,
)

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
    /** Start de programma's met PERLIO=:unix zodat Perl zijn uitvoer niet buffert. */
    val perlioUnbuffered: Boolean = true,
    /** Automatische antwoorden voor pdf2zcbs.exe. */
    val pdf2zcbsAnswers: List<AnswerRule> = DEFAULT_PDF2ZCBS_ANSWERS,
    /** Automatische antwoorden voor images.exe. */
    val imagesAnswers: List<AnswerRule> = DEFAULT_IMAGES_ANSWERS,
) {
    companion object {
        /** Gebaseerd op de vragen in de handleiding van pdf2zcbs (docs/050.pdf, hoofdstuk 3). */
        val DEFAULT_PDF2ZCBS_ANSWERS = listOf(
            AnswerRule("Deze PDF splitsen", "n"),
            AnswerRule("verwerken naar txt", "j"),
            AnswerRule("(presentatie) slides", "n"),
            AnswerRule("Type return to exit", ""),
        )

        /** Gebaseerd op de vragen in de handleiding van images.exe (docs/042.pdf, hoofdstuk 5). */
        val DEFAULT_IMAGES_ANSWERS = listOf(
            AnswerRule("Type ENTER/RETURN indien OK", ""),
            AnswerRule("afgeleide images small/large/100pct opnieuw aanmaken", "j"),
            AnswerRule("Kies 1,2,3", "1"),
            AnswerRule("UPLOADEN aangemaakte images", "n"),
            AnswerRule("Nummer, naam of plaats instelling", "4"),
            AnswerRule("FTP wachtwoord", "", secret = true),
            AnswerRule("Kies de ZCBS-toepassing", "test"),
            AnswerRule("OVERSCHRIJVEN", "n"),
            AnswerRule("AANMAKEN en UPLOADEN van de bijbehorende", "n"),
            AnswerRule("VERWIJDEREN (tijdelijk) aangemaakte mappen", "j"),
            AnswerRule("VERWIJDEREN (tijdelijk) PC-bestand", "j"),
            AnswerRule("VERWIJDEREN inhoud van de map [images]", "j"),
            AnswerRule("om dit DOS-window te verlaten", ""),
        )
    }
}
