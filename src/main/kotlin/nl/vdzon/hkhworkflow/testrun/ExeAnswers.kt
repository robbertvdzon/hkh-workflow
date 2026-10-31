package nl.vdzon.hkhworkflow.testrun

import nl.vdzon.hkhworkflow.exerunner.AutoAnswer
import nl.vdzon.hkhworkflow.settings.WorkflowSettings

/**
 * De vragen van pdf2zcbs.exe en images.exe en wat de app daarop antwoordt. We kennen de vragen (images.exe 3.27, gemeten op 30 oktober 2026,
 * zie docs/zcbs-achtergrond.md) en bijna alle antwoorden zijn vast. Alleen de resolutie verschilt per collectie en het ftp-wachtwoord
 * komt uit de instellingen. Een vraag die hier niet in staat (een andere versie van het programma) beantwoordt de app niet: hij stopt en toont de vraag.
 */
object ExeAnswers {
    const val UPLOAD_QUESTION = "UPLOADEN aangemaakte images"
    const val RECORDS_QUESTION = "AANMAKEN en UPLOADEN van de bijbehorende"
    const val FTP_PASSWORD_QUESTION = "FTP wachtwoord"

    /** De submap is een keuze per keer en een naam leert de app niet: die vraag blijft een Enter. */
    const val SUBFOLDER_QUESTION = "Geef een ENTER/RETURN"

    /**
     * pdf2zcbs.exe vraagt bij HKH niets zolang `pdf2zcbs.ini` ernaast staat. Zonder die ini zijn dit de vragen (docs/050.pdf, hoofdstuk 3).
     */
    val PDF2ZCBS = listOf(
        AutoAnswer("Deze PDF splitsen", "n"),
        AutoAnswer("verwerken naar txt", "j"),
        AutoAnswer("(presentatie) slides", "n"),
        AutoAnswer("Type return to exit", ""),
    )

    /** Nooit overschrijven op de server en nooit de afbeeldingen van images.exe wissen: dat is altijd nee, ook als iemand zelf typt. */
    val SAFETY = listOf(
        AutoAnswer("OVERSCHRIJVEN", "n"),
        AutoAnswer("VERWIJDEREN (tijdelijk) aangemaakte mappen", "n"),
        AutoAnswer("VERWIJDEREN inhoud van de map [images]", "n"),
    )

    /** Alle vragen van images.exe met het antwoord dat de app voorstelt of geeft. */
    fun images(s: WorkflowSettings): List<AutoAnswer> = listOf(
        AutoAnswer("Type ENTER/RETURN indien OK", "a"),
        AutoAnswer("afgeleide images small/large/100pct opnieuw aanmaken", "j"),
        AutoAnswer("Kies 1,2,3", s.imagesResolution()),
        AutoAnswer(UPLOAD_QUESTION, "j"),
        AutoAnswer("Nummer, naam of plaats instelling", "4"),
        AutoAnswer(FTP_PASSWORD_QUESTION, s.ftpPassword, secret = true),
        AutoAnswer("Kies de ZCBS-toepassing", s.collection),
        AutoAnswer(RECORDS_QUESTION, "j"),
        AutoAnswer("VERWIJDEREN (tijdelijk) PC-bestand", "j"),
        AutoAnswer(SUBFOLDER_QUESTION, ""),
        AutoAnswer("om dit DOS-window te verlaten", ""),
    ) + SAFETY
}
