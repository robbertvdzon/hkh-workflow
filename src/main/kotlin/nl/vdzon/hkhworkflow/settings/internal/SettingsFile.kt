package nl.vdzon.hkhworkflow.settings.internal

import nl.vdzon.hkhworkflow.settings.CollectionSettings
import nl.vdzon.hkhworkflow.settings.WorkflowSettings
import tools.jackson.databind.JsonNode

/** Het stuk per collectie in het instellingenbestand. */
data class CollectionFile(val imagesResolution: String, val backup100pctFolder: String, val backupPdfFolder: String)

/**
 * Het instellingenbestand (`hkh-workflow-settings.json`) precies zoals het op schijf staat. [WorkflowSettings] is wat de app ermee doet; wat daaruit volgt
 * (het adres van ZCBS, de ftp-server) staat hier niet in. Bestanden van een oudere versie worden hier gelezen, zodat de rest van de app die niet hoeft te kennen.
 */
data class SettingsFile(
    val testFolder: String = "",
    val imagesExe: String = "",
    val pdf2zcbsExe: String = "",
    val perlioUnbuffered: Boolean = true,
    val collections: Map<String, CollectionFile> = emptyMap(),
    val leftoversFolder: String = "",
    val nightlyUpdate: Boolean = true,
    val useTestZcbs: Boolean = true,
    val sourceFolders: String = "",
    val ftpPassword: String = "",
) {
    fun toSettings() = WorkflowSettings(
        testFolder = testFolder,
        imagesExe = imagesExe,
        pdf2zcbsExe = pdf2zcbsExe,
        perlioUnbuffered = perlioUnbuffered,
        collections = collections.mapValues { (_, c) -> CollectionSettings(c.imagesResolution, c.backup100pctFolder, c.backupPdfFolder) },
        leftoversFolder = leftoversFolder,
        nightlyUpdate = nightlyUpdate,
        useTestZcbs = useTestZcbs,
        sourceFolders = sourceFolders,
        ftpPassword = ftpPassword,
    )

    companion object {
        fun of(s: WorkflowSettings) = SettingsFile(
            testFolder = s.testFolder,
            imagesExe = s.imagesExe,
            pdf2zcbsExe = s.pdf2zcbsExe,
            perlioUnbuffered = s.perlioUnbuffered,
            collections = s.collections.mapValues { (_, c) -> CollectionFile(c.imagesResolution, c.backup100pctFolder, c.backupPdfFolder) },
            leftoversFolder = s.leftoversFolder,
            nightlyUpdate = s.nightlyUpdate,
            useTestZcbs = s.useTestZcbs,
            sourceFolders = s.sourceFolders,
            ftpPassword = s.ftpPassword,
        )

        fun read(node: JsonNode): SettingsFile {
            fun text(name: String, oldName: String, default: String): String =
                node.path(name).takeIf { it.isString }?.asString() ?: node.path(oldName).takeIf { it.isString }?.asString() ?: default
            val oldBaseUrl = node.path("zcbsBaseUrl").asString("")
            // De antwoorden voor images.exe stonden vroeger in dit bestand (een lijst {pattern, answer, secret}); nu zijn ze vast, op de
            // resolutie en het ftp-wachtwoord na. Die twee nemen we over uit zo'n oud bestand.
            fun oldAnswer(pattern: String): String? {
                val array = node.path("imagesAnswers")
                if (!array.isArray) return null
                for (i in 0 until array.size()) {
                    val item = array.get(i)
                    if (item.path("pattern").asString("").equals(pattern, ignoreCase = true)) return item.path("answer").asString("")
                }
                return null
            }
            val oldResolution = oldAnswer("Kies 1,2,3")?.takeIf { it in CollectionSettings.RESOLUTIONS }
            val collections = linkedMapOf<String, CollectionFile>()
            val collectionsNode = node.path("collections")
            if (collectionsNode.isObject) {
                for (name in collectionsNode.propertyNames()) {
                    val c = collectionsNode.path(name)
                    collections[name] = CollectionFile(
                        imagesResolution = c.path("imagesResolution").asString("").takeIf { it in CollectionSettings.RESOLUTIONS } ?: CollectionSettings.DEFAULT_RESOLUTION,
                        backup100pctFolder = c.path("backup100pctFolder").asString(""),
                        backupPdfFolder = c.path("backupPdfFolder").asString(""),
                    )
                }
            } else {
                // Een bestand van voor de instellingen per collectie: alles hoorde bij het archief.
                collections[WorkflowSettings.COLLECTION] = CollectionFile(
                    imagesResolution = oldResolution ?: CollectionSettings.DEFAULT_RESOLUTION,
                    backup100pctFolder = node.path("backup100pctFolder").asString(""),
                    backupPdfFolder = node.path("backupPdfFolder").asString(""),
                )
            }
            return SettingsFile(
                testFolder = node.path("testFolder").asString(""),
                imagesExe = node.path("imagesExe").asString(""),
                pdf2zcbsExe = node.path("pdf2zcbsExe").asString(""),
                perlioUnbuffered = node.path("perlioUnbuffered").asBoolean(true),
                collections = collections,
                leftoversFolder = node.path("leftoversFolder").asString(""),
                nightlyUpdate = node.path("nightlyUpdate").asBoolean(true),
                // Een bestand van voor deze keuze: alles wat niet het echte archief is, geldt als test. Zo wijst een oude instelling nooit per ongeluk naar het echte archief.
                useTestZcbs = if (node.path("useTestZcbs").isBoolean) node.path("useTestZcbs").asBoolean(true) else !oldBaseUrl.contains("historischekringheemskerk"),
                sourceFolders = node.path("sourceFolders").asString(""),
                // Het wachtwoord: de eigen instelling, anders het oude antwoord op "FTP wachtwoord" (ook uit de tijd van sftp).
                ftpPassword = text("ftpPassword", "sftpPassword", "").ifEmpty { oldAnswer("FTP wachtwoord").orEmpty() },
            )
        }
    }
}
