package nl.vdzon.hkhworkflow.ux

import java.time.LocalTime
import java.time.format.DateTimeFormatter
import org.springframework.stereotype.Component

/**
 * Klikbare demo van de hele flow die Carla gaat doorlopen (inloggen tot afsluiten). Alles is nepdata
 * in het geheugen: er wordt niets naar ZCBS, de exe's of de sftp-server gestuurd.
 */

enum class RecStatus(val label: String, val kind: String) {
    OPEN("nog niet bewerkt", "neutral"),
    BEZIG("bezig", "busy"),
    OPGESLAGEN("opgeslagen", "ok"),
    LATER("later", "busy"),
    ONVEILIG("niet veilig", "err"),
}

class DemoRecord(val nr: Int, val file: String, val pages: Int) {
    var status: RecStatus = RecStatus.OPEN
    var values: Map<String, String> = emptyMap()
    var themes: List<String> = emptyList()
    val title: String get() = values["a5"].orEmpty()
}

data class DemoField(
    val name: String,
    val label: String,
    val kind: String,
    val options: List<String> = emptyList(),
    val hint: String = "",
)

data class PhaseDef(
    val nr: Int,
    val title: String,
    val intro: String,
    val plan: List<String>,
    val checks: List<String>,
    val columns: List<String>,
    val runLabel: String,
    val rows: List<String>? = null,
)

/** Het scherm dat de runtime van een lopende fase toont. */
data class RunRow(val label: String, val state: String, val stateLabel: String)

object FlowDemoData {
    const val HIGHEST_IN_ZCBS = 11933
    const val FILE_COUNT = 12
    /** Zo staat een nieuw (leeg) record in ZCBS: het formulier heeft al enkele standaardkeuzes. */
    val DEFAULT_VALUES = mapOf("a21" to "neen", "a25" to "Heemskerk", "a22" to "HKH Jan de Ruijter", "a9" to "Historische Kring Heemskerk")

    const val THEIR_TITLE = "Heemskrink 1981 (aangepast door een collega)"

    /** De pdf's in de invoermap: bestandsnaam, aantal pagina's, gescand door. */
    val inputFiles = (0 until FILE_COUNT).map { i ->
        Triple("scan_%04d.pdf".format(412 + i), listOf(2, 1, 4, 1, 2, 3, 1, 5, 2, 1, 3, 2)[i], if (i < 7) "Ria" else "Kees")
    }

    val phases = listOf(
        PhaseDef(1, "Inloggen", "", emptyList(), emptyList(), emptyList(), ""),
        PhaseDef(2, "Nieuwe bestanden", "", emptyList(), emptyList(), emptyList(), ""),
        PhaseDef(3, "Voorbereiden", "", emptyList(), emptyList(), emptyList(), ""),
        PhaseDef(
            4, "pdf2zcbs",
            "pdf2zcbs maakt van elke pdf een doorzoekbare pdf, een tekstbestand en twee plaatjes.",
            listOf(
                "Kopieert de 12 hernoemde pdf's naar de map van pdf2zcbs",
                "Draait pdf2zcbs één keer voor de hele batch en beantwoordt de vragen zelf",
                "Controleert per bestand of tekst, pdf, small en large er zijn",
            ),
            listOf("De map pdf2zcbs\\output is leeg", "12 hernoemde pdf's staan klaar", "Geen losse pdf's in de programmamap"),
            listOf("Tekst", "PDF", "Small", "Large"), "Start pdf2zcbs",
        ),
        PhaseDef(
            5, "images.exe",
            "images.exe maakt de afbeeldingen voor de website, zet ze met FTP op de server en maakt de lege records aan in ZCBS.",
            listOf(
                "Kopieert de 12 large-jpg's naar de map van images.exe",
                "Maakt small, large en 100pct en uploadt ze met FTP",
                "Maakt 12 lege records aan (stap D van images.exe)",
            ),
            listOf("12 jpg's in de map images", "De nummers bestaan nog niet in ZCBS", "Geen oude resultaten in small, large en 100pct", "Het FTP-wachtwoord staat in de instellingen"),
            listOf("Small", "Large", "100pct", "Upload", "Record"), "Start images.exe",
        ),
        PhaseDef(
            6, "Upload ocr en pdf",
            "De tekst en de pdf's gaan met sftp naar de map archief/ocr en archief/pdf op de server. Bestaande bestanden worden nooit overschreven.",
            listOf(
                "Controleert de twee doelmappen op de server",
                "Uploadt 12 tekstbestanden naar archief/ocr",
                "Uploadt 12 pdf's naar archief/pdf en vergelijkt de grootte",
            ),
            listOf("Verbinding met de server en vingerafdruk kloppen", "Geen van de 24 bestanden bestaat al op de server"),
            listOf("Tekst (ocr)", "PDF", "Grootte gelijk"), "Start upload",
        ),
        PhaseDef(7, "Bewerken", "", emptyList(), emptyList(), emptyList(), ""),
        PhaseDef(
            8, "Afsluiten",
            "Als alles klaar is, ruimt de app op en archiveert hij de originelen.",
            listOf("Maakt een back-up van de 100pct-afbeeldingen", "Maakt de mappen van de programma's leeg", "Verplaatst de originele pdf's naar de archiefmap"),
            listOf("Alle records zijn opgeslagen of staan op later"),
            listOf("Resultaat"), "Sluit de batch af",
            rows = listOf("Back-up van de 100pct maken", "Mappen van de programma's opruimen", "Originele pdf's archiveren"),
        ),
    )

    val typeOptions = listOf(
        "Advertentie", "Diploma", "Heemskring", "Krantenartikel", "Kroniek", "Notariële akte", "Officiële bekendmaking",
        "Overeenkomst", "Tekening", "Kaart/Plattegrond", "Uitnodiging", "Verzameling gegevens", "== Anders ==",
    )
    val themeOptions = listOf(
        "Bedrijven/Winkels/Detailhandel/Ondernemingen", "Begraafplaatsen", "Bestemmingsplan", "Bloemen- en groenteteelt, Aardbeienteelt",
        "Boerderijen en Tuingebied", "Brandweer, Politie en Veiligheid", "Festiviteiten en Volksfeesten", "Financiele zaken (Banken)",
        "Gebouwen en Terreinen", "Gereedschappen", "Gemeente, bestuur, vergunningen en politiek", "Geschiedenis", "Kastelen en Monumenten",
        "Kerken en Religie", "Kunst en Cultuur", "Landbouw en Veeteelt", "Locale geschiedenis en bewoners", "Markt",
        "Milieuzaken en Afvalverwerking", "Onderwijs, Scholen en Kinderopvang", "Openbare leven (bioscoop, café, horeca)", "Recreatie",
        "Sport en Sportverenigingen", "Scheepvaart", "Verenigingsleven en Gezelschappen", "Vergunningen", "Verkeer en Wegen",
        "Vluchtelingen, Asielzoekers",
    )

    val fields = listOf(
        DemoField("a5", "Titel", "text"),
        DemoField("a7", "Auteur(s)", "text"),
        DemoField("a8", "Datum", "text", hint = "bijvoorbeeld 03-09-1970"),
        DemoField("a6", "Omschrijving", "area"),
        DemoField("a3", "Type publicatie", "select", typeOptions),
        DemoField("a22", "Oorspronkelijk archief", "select", listOf("HKH Jan de Ruijter", "Pierre Janssens", "van Wijk", "Jaap Schoen", "Onbekend")),
        DemoField("a24", "Thema", "multi", themeOptions, hint = "Houd Ctrl (of Cmd) ingedrukt om meer dan één te kiezen."),
        DemoField("a4", "Trefwoord", "text"),
        DemoField("a9", "Uitgever", "select", listOf("Noord Hollands Dagblad", "Heemskerkse Courant", "Nieuwsblad de Kennemer", "Kennemerland op Zondag", "Dagblad Kennemerland", "Museum Kennemerland", "IJmuider Courant", "Gemeente Heemskerk", "Historische Kring Heemskerk", "Typhoon", "Onbekend")),
        DemoField("a11", "Aantal pagina's", "text"),
        DemoField("a25", "Plaats", "select", listOf("Heemskerk", "Beverwijk", "Castricum", "Wijk aan Zee", "Velsen", "Uitgeest")),
        DemoField("a26", "Straatnaam", "text"),
        DemoField("a21", "Plaatsing op internet", "radio", listOf("neen", "ja")),
        DemoField("a16", "Doos nummer", "text"),
        DemoField("a15", "Map nummer", "text"),
        DemoField("a27", "Locatie op PC", "text", hint = "de oorspronkelijke bestandsnaam"),
        DemoField("a19", "Interne opmerking", "area"),
    )

    val labels: Map<String, String> = fields.associate { it.name to it.label }

    const val SAMPLE_OCR = "Historische Kring Heemskerk · ledenvergadering · het bestuur opent de vergadering en heet de aanwezigen welkom. " +
        "Op de agenda staan de jaarcijfers, de nieuwe tentoonstelling over het Slot Assumburg en de rondvraag."
}

@Component
class FlowDemoState {
    private val formatter = DateTimeFormatter.ofPattern("HH:mm")
    /** Hoe lang de demo over één bestand doet; de tests zetten dit laag. */
    var stepMillis = 350L

    var user: String? = null
    var current = 1
    var finished = false
    var loginAttempts = 0
    var startNr = FlowDemoData.HIGHEST_IN_ZCBS + 1
    var records: List<DemoRecord> = emptyList()
    var failPhase5 = false
    var scenario = "normaal"
    private val done = mutableSetOf<Int>()
    private var runningSince: Long? = null
    private var failedAt: Int? = null

    @Synchronized
    fun reset() {
        user = null; current = 1; finished = false; loginAttempts = 0
        startNr = FlowDemoData.HIGHEST_IN_ZCBS + 1; records = emptyList()
        failPhase5 = false; scenario = "normaal"
        done.clear(); runningSince = null; failedAt = null
    }

    /** @return een foutmelding, of null als de inlog gelukt is. */
    @Synchronized
    fun login(name: String, password: String): String? {
        if (name.isBlank() || password.isBlank()) return "Vul je naam en je wachtwoord in."
        if (loginAttempts >= 3) return "Drie keer mislukt. Wacht even en probeer het dan opnieuw."
        if (password.equals("fout", ignoreCase = true)) {
            loginAttempts++
            return "Naam of wachtwoord niet herkend. Probeer het nog eens (poging $loginAttempts van 3)."
        }
        user = name.trim(); current = 2; done += 1; loginAttempts = 0
        return null
    }

    @Synchronized
    fun confirmFiles() {
        if (current == 2) { done += 2; current = 3 }
    }

    /** @return een foutmelding, of null als de nummers vrij zijn en de bestanden zijn hernoemd. */
    @Synchronized
    fun prepare(start: String): String? {
        val nr = start.trim().toIntOrNull() ?: return "Geef een nummer van vijf cijfers op."
        if (nr < 10000 || nr > 99999 - FlowDemoData.FILE_COUNT) return "Een recordnummer heeft vijf cijfers."
        if (nr <= FlowDemoData.HIGHEST_IN_ZCBS) return "Record $nr bestaat al in ZCBS. images.exe zou een bestaand record niet mogen aanmaken. Kies $startNr of hoger."
        startNr = nr
        records = (0 until FlowDemoData.FILE_COUNT).map { i ->
            DemoRecord(nr + i, FlowDemoData.inputFiles[i].first, FlowDemoData.inputFiles[i].second).also { it.values = FlowDemoData.DEFAULT_VALUES }
        }
        done += 3; current = 4
        return null
    }

    private fun rowCount(phase: Int) = FlowDemoData.phases[phase - 1].rows?.size ?: FlowDemoData.FILE_COUNT

    @Synchronized
    fun start() {
        if (current in listOf(4, 5, 6, 8) && current !in done && runningSince == null) { runningSince = System.currentTimeMillis(); failedAt = null }
    }

    @Synchronized
    fun retry() { failedAt = null; failPhase5 = false; start() }

    @Synchronized
    fun running(): Boolean { tick(); return runningSince != null }

    @Synchronized
    fun failed(): Boolean { tick(); return failedAt != null }

    @Synchronized
    fun phaseDone(nr: Int): Boolean { tick(); return nr in done }

    private fun progress(): Int {
        val since = runningSince ?: return 0
        return ((System.currentTimeMillis() - since) / stepMillis).toInt()
    }

    private fun tick() {
        if (runningSince == null) return
        val n = rowCount(current)
        if (current == 5 && failPhase5 && progress() >= 8) {
            failedAt = 8; runningSince = null; return
        }
        if (progress() >= n) { done += current; runningSince = null; if (current == 8) finished = true }
    }

    /** Gaat naar de volgende fase als de huidige klaar is (voor fase 7: als elk record is opgeslagen of op later staat). */
    @Synchronized
    fun next() {
        tick()
        when {
            current == 7 -> if (allHandled()) { done += 7; current = 8 }
            current in listOf(4, 5, 6) && current in done -> current++
        }
    }

    @Synchronized
    fun rows(): List<RunRow> {
        tick()
        val def = FlowDemoData.phases[current - 1]
        val n = rowCount(current)
        val p = progress()
        return (0 until n).map { i ->
            val label = def.rows?.get(i) ?: "${records[i].nr} · ${records[i].file}"
            val failed = failedAt
            val state = when {
                current in done -> "ok"
                failed != null && i == failed -> "err"
                failed != null && i > failed -> "wait"
                runningSince != null && i < p -> "ok"
                runningSince != null && i == p -> "busy"
                else -> "wait"
            }
            RunRow(label, state, when (state) { "ok" -> "Klaar"; "busy" -> "Bezig"; "err" -> "Mislukt"; else -> "Wacht" })
        }
    }

    fun handled() = records.count { it.status == RecStatus.OPGESLAGEN }
    fun later() = records.count { it.status == RecStatus.LATER }
    fun allHandled() = records.isNotEmpty() && records.all { it.status == RecStatus.OPGESLAGEN || it.status == RecStatus.LATER }

    fun record(nr: Int): DemoRecord? = records.firstOrNull { it.nr == nr }

    /** Zet de lock en de status bij het openen van een record. */
    @Synchronized
    fun open(r: DemoRecord) {
        if (r === records.last() && r.status == RecStatus.OPEN) r.status = RecStatus.ONVEILIG
        else if (r.status == RecStatus.OPEN) r.status = RecStatus.BEZIG
    }

    fun lockFrom(): String = LocalTime.now().format(formatter)
    fun lockUntil(): String = LocalTime.now().plusMinutes(10).format(formatter)
}
