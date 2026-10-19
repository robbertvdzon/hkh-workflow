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
    /** Automatische antwoorden voor images.exe (aangevuld met de vaste antwoorden hieronder). */
    val imagesAnswers: List<AnswerRule> = DEFAULT_IMAGES_ANSWERS,
    /** Naam van de ZCBS-collectie: antwoord op "Kies de ZCBS-toepassing" en basis voor de sftp-mappen. */
    val collection: String = "archief",
    /** Antwoord op de vraag naar het bestandstype van images.exe; "a" forceert type A (recordnummer als naam). */
    val imagesFileType: String = "a",
    /** Resolutiekeuze van images.exe: 1 t/m 3 = small 100x100 (documenten), 6 t/m 8 = small 250x250 (foto's). */
    val imagesResolution: String = "6",
    /** Nummer van de ZCBS-instelling (HKH = 4) voor de uploadvraag van images.exe. */
    val imagesInstitution: String = "4",
    /** Map waar de 100pct-afbeeldingen een back-up krijgen voordat er wordt opgeruimd. */
    val backup100pctFolder: String = "",
    val sftpHost: String = "",
    val sftpPort: Int = 22,
    val sftpUser: String = "",
    val sftpPassword: String = "",
    /** Vastgezette vingerafdruk van de server, bijv. "SHA256:abc...". Zonder deze wordt er niet geüpload. */
    val sftpHostKey: String = "",
    /** Basismap op de sftp-server waar de collectiemappen onder staan. */
    val sftpBaseDir: String = DEFAULT_SFTP_BASE_DIR,
    /** Map voor de tekstbestanden, relatief aan de basismap. Leeg = "<collectie>/ocr". */
    val sftpOcrDir: String = "",
    /** Map voor de pdf's, relatief aan de basismap. Leeg = "<collectie>/pdf". */
    val sftpPdfDir: String = "",
) {
    /** Volledig pad op de sftp-server voor de tekstbestanden. */
    fun remoteOcrDir(): String = remoteDir(sftpOcrDir, "ocr")

    /** Volledig pad op de sftp-server voor de pdf's. */
    fun remotePdfDir(): String = remoteDir(sftpPdfDir, "pdf")

    private fun remoteDir(override: String, default: String): String {
        val relative = override.trim().ifEmpty { "${collection.trim()}/$default" }
        return if (relative.startsWith("/")) relative.trimEnd('/') else sftpBaseDir.trim().trimEnd('/') + "/" + relative.trim('/')
    }

    /** Zonder wachtwoorden, zodat ze nooit per ongeluk in een log belanden. */
    override fun toString(): String =
        "WorkflowSettings(testFolder=$testFolder, archivedFolder=$archivedFolder, imagesExe=$imagesExe, pdf2zcbsExe=$pdf2zcbsExe, " +
            "collection=$collection, sftpHost=$sftpHost, sftpUser=$sftpUser, sftpPassword=${if (sftpPassword.isEmpty()) "(leeg)" else "***"})"

    companion object {
        const val DEFAULT_SFTP_BASE_DIR = "/domains/historischekringheemskerk.nl/public_html"

        /** Gebaseerd op de vragen in de handleiding van pdf2zcbs (docs/050.pdf, hoofdstuk 3). */
        val DEFAULT_PDF2ZCBS_ANSWERS = listOf(
            AnswerRule("Deze PDF splitsen", "n"),
            AnswerRule("verwerken naar txt", "j"),
            AnswerRule("(presentatie) slides", "n"),
            AnswerRule("Type return to exit", ""),
        )

        /**
         * Gebaseerd op de vragen in de handleiding van images.exe (docs/042.pdf, hoofdstuk 5). De testpagina zet
         * bestandstype, resolutie, instelling, collectie, uploaden, records en het verwijderen van mappen er
         * bovenop (die gaan voor); hier staat de rest, met veilige antwoorden.
         */
        val DEFAULT_IMAGES_ANSWERS = listOf(
            AnswerRule("Type ENTER/RETURN indien OK", "a"),
            AnswerRule("afgeleide images small/large/100pct opnieuw aanmaken", "j"),
            AnswerRule("Kies 1,2,3", "6"),
            AnswerRule("UPLOADEN aangemaakte images", "n"),
            AnswerRule("Nummer, naam of plaats instelling", "4"),
            AnswerRule("FTP wachtwoord", "", secret = true),
            AnswerRule("Kies de ZCBS-toepassing", "test"),
            AnswerRule("OVERSCHRIJVEN", "n"),
            AnswerRule("AANMAKEN en UPLOADEN van de bijbehorende", "n"),
            AnswerRule("VERWIJDEREN (tijdelijk) aangemaakte mappen", "n"),
            AnswerRule("VERWIJDEREN (tijdelijk) PC-bestand", "j"),
            AnswerRule("VERWIJDEREN inhoud van de map [images]", "n"),
            AnswerRule("om dit DOS-window te verlaten", ""),
        )
    }
}
