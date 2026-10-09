package nl.vdzon.hkhworkflow.ux

/**
 * Voorbeeldgegevens voor de schermen uit het UX-ontwerp. Deze schermen zijn in deze eerste versie
 * nog niet gekoppeld aan echte mappen of aan ZCBS.
 */
data class DemoFile(val id: String, val name: String, val pages: Int, val scanned: String, val hasText: Boolean, val error: String? = null)

data class DemoFolder(val id: String, val person: String, val path: String, val files: List<DemoFile>)

data class DemoBatch(
    val id: String,
    val startedAt: String,
    val by: String,
    val collection: String,
    val files: Int,
    val status: String,
    val statusKind: String,
    val described: Int?,
    val describable: Int?,
    val link: String,
)

data class DemoResult(val original: String, val record: String?, val described: Boolean, val error: String? = null)

object DemoData {
    val collections = listOf(
        "artikelen" to "Artikelen",
        "archief" to "Archief",
        "beeldbank" to "Beeldbank (foto's)",
        "library" to "Bibliotheek",
        "bidprent" to "Bidprentjes",
        "objecten" to "Objecten",
        "transcripties" to "Transcripties",
    )

    val folders = listOf(
        DemoFolder(
            "ria", "Ria", "K:\\scabslelly\\Ria\\map57",
            listOf(
                DemoFile("r1", "xx01.pdf", 2, "25 sep, 09:12", true),
                DemoFile("r2", "xx02.pdf", 1, "25 sep, 09:14", true),
                DemoFile("r3", "xx03.pdf", 4, "25 sep, 09:19", true),
                DemoFile("r4", "xx04.pdf", 1, "25 sep, 09:21", true),
                DemoFile("r5", "xx05.pdf", 2, "25 sep, 09:26", true),
                DemoFile("r6", "xx06.pdf", 3, "25 sep, 09:30", true),
            ),
        ),
        DemoFolder(
            "kees", "Kees", "K:\\scabslelly\\Kees\\raadhuis",
            listOf(
                DemoFile("k1", "raadhuis-1932-voorgevel.pdf", 1, "24 sep, 16:40", false),
                DemoFile("k2", "raadhuis-1932-plattegrond.pdf", 3, "24 sep, 16:44", false, "Mislukt in batch 24 sep 10:12"),
                DemoFile("k3", "raadhuis-bouwvergunning.pdf", 5, "24 sep, 16:51", true),
            ),
        ),
    )

    val allFiles: List<Pair<DemoFolder, DemoFile>> = folders.flatMap { folder -> folder.files.map { folder to it } }

    val batches = listOf(
        DemoBatch("B-20260925-1502", "25 sep 2026, 15:02", "Carla", "Archief", 3, "Bezig · 2/3", "busy", null, null, "/batches/demo-bezig"),
        DemoBatch("B-20260925-1441", "25 sep 2026, 14:41", "Carla", "Archief", 6, "Klaar, 1 fout", "error", 1, 5, "/batches/demo-klaar"),
        DemoBatch("B-20260924-1012", "24 sep 2026, 10:12", "Carla", "Archief", 12, "Klaar, 1 fout", "error", 11, 11, "/batches/demo-klaar"),
        DemoBatch("B-20260918-1530", "18 sep 2026, 15:30", "Carla", "Artikelen", 56, "Klaar", "ok", 41, 56, "/batches/demo-klaar"),
        DemoBatch("B-20260911-0945", "11 sep 2026, 09:45", "Jan", "Beeldbank", 24, "Klaar", "ok", 24, 24, "/batches/demo-klaar"),
    )

    val results = listOf(
        DemoResult("Ria\\map57\\xx01.pdf", "1711", true),
        DemoResult("Ria\\map57\\xx02.pdf", "1712", false),
        DemoResult("Ria\\map57\\xx03.pdf", "1713", false),
        DemoResult("Ria\\map57\\xx04.pdf", "1714", false),
        DemoResult("Ria\\map57\\xx05.pdf", "1715", false),
        DemoResult("Ria\\map57\\xx06.pdf", null, false, "Upload mislukt: verbinding met de website verbroken. Er is geen record aangemaakt."),
    )

    const val ZCBS_BASE = "https://www.historischekringheemskerk.nl/cgi-bin"
}
