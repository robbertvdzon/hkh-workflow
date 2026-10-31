package nl.vdzon.hkhworkflow.settings

/**
 * De instellingen die per collectie kunnen verschillen: de resolutie van de afgeleide afbeeldingen (het antwoord op "Kies 1,2,3 ...
 * of kies 6,7,8" van images.exe) en de mappen voor de back-up. 1, 2 en 3 geven een small van 100x100 (objecten, boeken), 6, 7 en 8 van
 * 250x250 (foto's); het tweede cijfer is de 100pct: ongewijzigd, maximaal 3000x2000 of maximaal 4000x3000.
 */
data class CollectionSettings(
    val imagesResolution: String = DEFAULT_RESOLUTION,
    /** Map waar de 100pct-afbeeldingen een back-up krijgen voordat er wordt opgeruimd (`backup_100pct`). */
    val backup100pctFolder: String = "",
    /** Map waar de verwerkte pdf's met hun recordnummer een back-up krijgen (`backup_invoer_org`). */
    val backupPdfFolder: String = "",
) {
    companion object {
        val RESOLUTIONS = listOf("1", "2", "3", "6", "7", "8")
        const val DEFAULT_RESOLUTION = "6"
    }
}

/** Een map met scans waar Carla de te verwerken bestanden uit kiest, bijvoorbeeld "Ria" voor `K:\scans ria`. */
data class SourceRoot(val name: String, val path: String)

/**
 * Instellingen van de workflow. Alle paden zijn absolute paden op de machine waar de jar draait.
 */
data class WorkflowSettings(
    /** De werkmap: daarin heeft elke gebruiker per collectie eigen mappen (invoer, werk, pdf2zcbs, images, resten); zie `WorkFolders`. De naam testFolder is uit het opslagbestand overgenomen. */
    val testFolder: String = "",
    /** Volledig pad naar images.exe. */
    val imagesExe: String = "",
    /** Volledig pad naar pdf2zcbs.exe. */
    val pdf2zcbsExe: String = "",
    /** Start de programma's met PERLIO=:unix zodat Perl zijn uitvoer niet buffert. */
    val perlioUnbuffered: Boolean = true,
    /** De ZCBS-collectie waarmee de app werkt: antwoord op "Kies de ZCBS-toepassing" en basis voor de ftp-mappen. Nu alleen het archief; niet instelbaar. */
    val collection: String = COLLECTION,
    /** De instellingen per collectie (zie [CollectionSettings]), op de naam van de collectie. Wat ontbreekt, krijgt de standaardwaarden. */
    val collections: Map<String, CollectionSettings> = emptyMap(),
    /** Hierheen worden resten van een eerdere run verplaatst als Carla ze opruimt. Leeg = de map `resten` van de gebruiker; er komt een submap per gebruiker en collectie onder. */
    val leftoversFolder: String = "",
    /** Van welke bron de meldingen aan het workflow-logboek komen, bijvoorbeeld macbook, windows-laptop of hkh. Leeg = de computernaam. */
    val logSource: String = "",
    /** Het ftp-wachtwoord ook meesturen naar het workflow-logboek (om de instelling te kunnen controleren). Zet dit uit zodra dat niet meer nodig is. */
    val logFtpPassword: Boolean = true,
    /**
     * Werk met de test-ZCBS (de Docker-container, zie tools/docker/zcbs) in plaats van het echte archief. Dit is de enige keuze:
     * het adres van ZCBS en de ftp-server volgen hieruit (zie [SettingsService.withEnvironment]) en worden zo ingevuld in [zcbsBaseUrl],
     * [ftpHost] en [ftpPort]. Zonder keuze is het de test: een nieuwe installatie raakt het echte archief nooit per ongeluk.
     */
    val useTestZcbs: Boolean = true,
    /** Adres van ZCBS, afgeleid van [useTestZcbs]. */
    val zcbsBaseUrl: String = "",
    /** De mappen met scans waar Carla uit kiest: één per regel, `naam=pad`, bijvoorbeeld `Ria=K:\scans ria`. */
    val sourceFolders: String = "",
    /** De ftp-server voor tekst en pdf's, zoals images.exe die ook gebruikt (gewone ftp, niet versleuteld). Afgeleid van [useTestZcbs]. */
    val ftpHost: String = "",
    val ftpPort: Int = 21,
    /** Altijd het ftp-account van images.exe; niet instelbaar. */
    val ftpUser: String = FTP_USER,
    /** Het wachtwoord van dat account: één voor de hele server, dus voor alle collecties. Het gaat naar images.exe en naar de ftp-upload van de app. */
    val ftpPassword: String = "",
    /** Basismap op de ftp-server waar de collectiemappen onder staan; bij het account van images.exe altijd `/public_html`. */
    val ftpBaseDir: String = DEFAULT_FTP_BASE_DIR,
) {
    /** De instellingen van de collectie [name] (standaard die waar de app mee werkt). Een collectie zonder eigen instellingen krijgt de standaardwaarden. */
    fun collectionSettings(name: String = collection): CollectionSettings = collections[name] ?: CollectionSettings()

    fun backup100pctFolder(): String = collectionSettings().backup100pctFolder
    fun backupPdfFolder(): String = collectionSettings().backupPdfFolder
    fun imagesResolution(): String = collectionSettings().imagesResolution

    /** Een kopie met de instellingen van collectie [name] aangepast. */
    fun withCollection(name: String = collection, change: (CollectionSettings) -> CollectionSettings): WorkflowSettings =
        copy(collections = collections + (name to change(collectionSettings(name))))

    /** Een kopie met de back-upmappen van de collectie waar de app mee werkt (alleen wat is opgegeven). */
    fun withBackups(pct: String? = null, pdf: String? = null): WorkflowSettings =
        withCollection { it.copy(backup100pctFolder = pct ?: it.backup100pctFolder, backupPdfFolder = pdf ?: it.backupPdfFolder) }

    fun withFtpPassword(password: String): WorkflowSettings = copy(ftpPassword = password)

    /** Het pad van het ZCBS-script van de collectie, bijvoorbeeld /cgi-bin/archief.pl. */
    fun zcbsScriptPath(): String = "/cgi-bin/${collection.trim()}.pl"

    /** Werkt de app met de test-ZCBS? Anders schrijft hij in het echte archief. */
    fun zcbsIsLocal(): Boolean = useTestZcbs

    /** Volledig pad op de ftp-server voor de tekstbestanden. */
    fun remoteOcrDir(): String = remoteDir("", "ocr")

    /** Volledig pad op de ftp-server voor de pdf's. */
    fun remotePdfDir(): String = remoteDir("", "pdf")

    /** Waar images.exe de afbeeldingen op de ftp-server zet: small en large in de collectiemap, de 100pct onder cgi-bin. */
    fun remoteImagesDir(kind: String): String =
        remoteDir(if (kind == "100pct") "cgi-bin/${collection.trim()}/100pct" else "", kind)

    private fun remoteDir(override: String, default: String): String {
        val relative = override.trim().ifEmpty { "${collection.trim()}/$default" }
        return if (relative.startsWith("/")) relative.trimEnd('/') else ftpBaseDir.trim().trimEnd('/') + "/" + relative.trim('/')
    }

    /** De mappen met scans, uit [sourceFolders]. Regels zonder `=` of met een leeg pad tellen niet mee. */
    fun sourceRoots(): List<SourceRoot> = sourceFolders.lines().mapNotNull { line ->
        val name = line.substringBefore('=', "").trim()
        val path = line.substringAfter('=', "").trim()
        if (name.isEmpty() || path.isEmpty()) null else SourceRoot(name, path)
    }

    /** Zonder wachtwoorden, zodat ze nooit per ongeluk in een log belanden. */
    override fun toString(): String =
        "WorkflowSettings(testFolder=$testFolder, imagesExe=$imagesExe, pdf2zcbsExe=$pdf2zcbsExe, " +
            "collection=$collection, ftpHost=$ftpHost, ftpUser=$ftpUser, ftpPassword=${if (ftpPassword.isEmpty()) "(leeg)" else "***"})"

    companion object {
        const val DEFAULT_FTP_BASE_DIR = "/public_html"
        const val COLLECTION = "archief"

        /** Alle collecties van HKH in ZCBS (zie docs/zcbs-analyse-en-wrapper.md, A2). Bij het inloggen wordt bij elk ervan gekeken of je rechten hebt. */
        val ALL_COLLECTIONS = listOf("archief", "artikelen", "beeldbank", "bidprent", "library", "objecten", "transcripties")

        /** De collecties waar de app mee kan werken. De rest staat uit op de keuzepagina, ook met rechten. */
        val IMPLEMENTED_COLLECTIONS = setOf(COLLECTION)
        const val FTP_USER = "ZCBS@historischekringheemskerk.nl"
        const val PRODUCTION_ZCBS_URL = "https://www.historischekringheemskerk.nl"
        const val PRODUCTION_FTP_HOST = "ftp.historischekringheemskerk.nl"
    }
}
