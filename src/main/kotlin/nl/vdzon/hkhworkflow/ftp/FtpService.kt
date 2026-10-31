package nl.vdzon.hkhworkflow.ftp

import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import nl.vdzon.hkhworkflow.settings.SettingsService
import nl.vdzon.hkhworkflow.settings.WorkflowSettings
import org.apache.commons.net.ftp.FTP
import org.apache.commons.net.ftp.FTPClient
import org.apache.commons.net.ftp.FTPReply
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

/** Eén bestand dat naar de server moet: [local] wordt `<remoteDir>/<remoteName>`. */
data class UploadItem(val local: Path, val remoteDir: String, val remoteName: String) {
    val remotePath: String get() = remoteDir.trimEnd('/') + "/" + remoteName
}

data class UploadResult(val item: UploadItem, val ok: Boolean, val message: String)

data class RemoteDirStatus(val path: String, val exists: Boolean, val message: String? = null)

/** Uitkomst van "Test verbinding". */
data class ConnectionTest(val ok: Boolean, val message: String, val dirs: List<RemoteDirStatus> = emptyList())

/**
 * Uploadt bestanden naar de ZCBS-webserver met ftp (net als images.exe). Gewone ftp is niet versleuteld: het wachtwoord en de bestanden gaan leesbaar
 * over het netwerk. Bewust voorzichtig, want dit schrijft in productie:
 *
 * - nooit een bestaand bestand overschrijven en nooit iets op de server verwijderen of mappen aanmaken;
 * - eerst alles controleren (mappen bestaan, geen enkel doelbestand bestaat al), pas dan uploaden;
 * - uploaden onder een tijdelijke naam, hernoemen, en de grootte controleren.
 */
@Service
class FtpService(private val settingsService: SettingsService) {
    private val log = LoggerFactory.getLogger(javaClass)

    /** Verbindt, logt in en controleert of de mappen voor tekst en pdf bestaan. Schrijft niets. */
    fun testConnection(timeoutMillis: Int = 15_000): ConnectionTest {
        val s = settingsService.get()
        val problems = configProblems(s)
        if (problems.isNotEmpty()) return ConnectionTest(false, problems.joinToString(" "))
        return try {
            val dirs = connect(s, timeoutMillis) { ftp ->
                listOf(s.remoteOcrDir(), s.remotePdfDir()).distinct().map { dir ->
                    if (ftp.isDirectory(dir)) RemoteDirStatus(dir, true) else RemoteDirStatus(dir, false)
                }
            }
            ConnectionTest(true, "Verbonden met ${s.ftpHost} als ${s.ftpUser}.", dirs)
        } catch (e: Exception) {
            log.warn("Ftp-test mislukt: {}", e.message)
            ConnectionTest(false, "Verbinden mislukt: ${e.message ?: e.javaClass.simpleName}")
        }
    }

    /**
     * Uploadt [items]. Gooit een [IllegalStateException] met alle gevonden problemen als de controle vooraf
     * faalt; dan is er niets geüpload. Anders staat per bestand in het resultaat of het gelukt is; bij de eerste
     * fout wordt gestopt.
     */
    fun upload(items: List<UploadItem>): List<UploadResult> {
        if (items.isEmpty()) return emptyList()
        val s = settingsService.get()
        val problems = configProblems(s)
        check(problems.isEmpty()) { problems.joinToString("\n") }
        val duplicates = items.groupBy { it.remotePath }.filter { it.value.size > 1 }.keys
        check(duplicates.isEmpty()) { "Twee bestanden zouden op dezelfde plek terechtkomen: ${duplicates.joinToString()}" }

        return connect(s) { ftp ->
            val found = mutableListOf<String>()
            items.map { it.remoteDir }.distinct().forEach { dir ->
                if (!ftp.isDirectory(dir)) found += "Map bestaat niet op de server: $dir (er worden nooit mappen aangemaakt)"
            }
            items.forEach { item ->
                if (!Files.isRegularFile(item.local) || Files.size(item.local) == 0L) found += "Lokaal bestand ontbreekt of is leeg: ${item.local}"
                if (found.none { it.contains(item.remoteDir) }) {
                    if (ftp.fileExists(item.remotePath)) found += "Bestaat al op de server: ${item.remotePath}"
                    if (ftp.fileExists(tempPath(item))) found += "Tijdelijk bestand van een eerdere poging staat er nog: ${tempPath(item)}"
                }
            }
            check(found.isEmpty()) { "Niets geüpload, de controle vooraf vond:\n" + found.joinToString("\n") }

            val results = mutableListOf<UploadResult>()
            for (item in items) {
                val result = uploadOne(ftp, item)
                results += result
                if (!result.ok) break
            }
            log.info("Ftp-upload naar {}: {} van {} bestanden gelukt", s.ftpHost, results.count { it.ok }, items.size)
            results
        }
    }

    private fun uploadOne(ftp: FTPClient, item: UploadItem): UploadResult {
        val temp = tempPath(item)
        return try {
            Files.newInputStream(item.local).use { input ->
                check(ftp.storeFile(temp, input)) { "uploaden is geweigerd: ${ftp.replyString.trim()}" }
            }
            // Hernoemen kan op ftp een bestaand bestand overschrijven; kijk daarom nog een keer vlak ervoor.
            check(!ftp.fileExists(item.remotePath)) { "het bestand is er intussen al: ${item.remotePath}" }
            check(ftp.rename(temp, item.remotePath)) { "hernoemen is geweigerd: ${ftp.replyString.trim()}" }
            // Leesbaar voor de webserver (0644); niet elke server kent dit commando, dus een weigering is geen fout.
            runCatching { ftp.sendSiteCommand("CHMOD 644 ${item.remotePath}") }
            val expected = Files.size(item.local)
            val actual = ftp.getSize(item.remotePath)?.trim()?.toLongOrNull()
            if (actual == expected) UploadResult(item, true, "geüpload ($actual bytes)")
            else UploadResult(item, false, "grootte op de server (${actual ?: "onbekend"}) wijkt af van het origineel ($expected): ${item.remotePath}")
        } catch (e: Exception) {
            UploadResult(item, false, "mislukt: ${e.message ?: e.javaClass.simpleName} (controleer ${item.remotePath} en $temp op de server)")
        }
    }

    private fun tempPath(item: UploadItem) = item.remoteDir.trimEnd('/') + "/." + item.remoteName + ".uploading"

    /** Welke van deze volledige paden op de ftp-server bestaan? null als de server niet te bereiken is. */
    fun existing(paths: Collection<String>): Set<String>? =
        runCatching { connect(settingsService.get(), 5_000) { ftp -> paths.filter { ftp.fileExists(it) }.toSet() } }.getOrNull()

    private fun configProblems(s: WorkflowSettings): List<String> = buildList {
        if (s.ftpHost.isBlank()) add("Ftp-host is niet ingesteld.")
        if (s.ftpUser.isBlank()) add("Ftp-gebruiker is niet ingesteld.")
        if (s.ftpPassword.isEmpty()) add("Ftp-wachtwoord is niet ingesteld.")
        if (s.collection.isBlank()) add("Collectie is niet ingesteld.")
    }

    private fun <T> connect(s: WorkflowSettings, connectTimeoutMillis: Int = 15_000, block: (FTPClient) -> T): T {
        val ftp = FTPClient()
        ftp.connectTimeout = connectTimeoutMillis
        ftp.defaultTimeout = 60_000
        ftp.setDataTimeout(Duration.ofSeconds(60))
        try {
            ftp.connect(s.ftpHost, s.ftpPort)
            check(FTPReply.isPositiveCompletion(ftp.replyCode)) { "de server weigert de verbinding: ${ftp.replyString.trim()}" }
            check(ftp.login(s.ftpUser, s.ftpPassword)) { "inloggen is mislukt: ${ftp.replyString.trim()}" }
            ftp.enterLocalPassiveMode()
            check(ftp.setFileType(FTP.BINARY_FILE_TYPE)) { "kan niet naar binaire overdracht schakelen: ${ftp.replyString.trim()}" }
            return block(ftp)
        } finally {
            runCatching { if (ftp.isConnected) { runCatching { ftp.logout() }; ftp.disconnect() } }
        }
    }

    /** Is [path] een map? Probeert er naartoe te gaan en gaat daarna terug. */
    private fun FTPClient.isDirectory(path: String): Boolean {
        val origin = printWorkingDirectory()
        val exists = changeWorkingDirectory(path)
        if (exists && origin != null) changeWorkingDirectory(origin)
        return exists
    }

    /** Bestaat het bestand? Eerst de grootte (SIZE), anders de lijst van de map. */
    private fun FTPClient.fileExists(path: String): Boolean =
        getSize(path) != null || (listNames(path)?.isNotEmpty() == true)
}
