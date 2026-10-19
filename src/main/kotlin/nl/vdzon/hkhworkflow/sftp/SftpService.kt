package nl.vdzon.hkhworkflow.sftp

import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.security.PublicKey
import java.util.Base64
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.common.Buffer
import net.schmizz.sshj.sftp.FileMode
import net.schmizz.sshj.sftp.SFTPClient
import net.schmizz.sshj.transport.verification.FingerprintVerifier
import net.schmizz.sshj.transport.verification.HostKeyVerifier
import net.schmizz.sshj.xfer.FileSystemFile
import nl.vdzon.hkhworkflow.settings.SettingsService
import nl.vdzon.hkhworkflow.settings.WorkflowSettings
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

/** Eén bestand dat naar de server moet: [local] wordt `<remoteDir>/<remoteName>`. */
data class UploadItem(val local: Path, val remoteDir: String, val remoteName: String) {
    val remotePath: String get() = remoteDir.trimEnd('/') + "/" + remoteName
}

data class UploadResult(val item: UploadItem, val ok: Boolean, val message: String)

data class RemoteDirStatus(val path: String, val exists: Boolean, val message: String? = null)

/** Uitkomst van "Test verbinding". [pinned] is MATCH, MISMATCH of NONE (nog geen vingerafdruk vastgezet). */
data class ConnectionTest(
    val ok: Boolean,
    val message: String,
    val fingerprint: String? = null,
    val pinned: String = "NONE",
    val dirs: List<RemoteDirStatus> = emptyList(),
)

/**
 * Uploadt bestanden naar de ZCBS-webserver met sftp. Bewust voorzichtig, want dit schrijft in productie:
 *
 * - alleen verbinden met een server met de vastgezette vingerafdruk;
 * - nooit een bestaand bestand overschrijven en nooit iets op de server verwijderen of mappen aanmaken;
 * - eerst alles controleren (mappen bestaan, geen enkel doelbestand bestaat al), pas dan uploaden;
 * - uploaden onder een tijdelijke naam, hernoemen, en de grootte controleren.
 */
@Service
class SftpService(private val settingsService: SettingsService) {
    private val log = LoggerFactory.getLogger(javaClass)

    /** Verbindt, toont de vingerafdruk van de server en controleert of [dirs] bestaan. Schrijft niets. */
    fun testConnection(): ConnectionTest {
        val s = settingsService.get()
        val problems = configProblems(s, requireHostKey = false)
        if (problems.isNotEmpty()) return ConnectionTest(false, problems.joinToString(" "))
        var fingerprint: String? = null
        val capture = object : HostKeyVerifier {
            override fun verify(hostname: String, port: Int, key: PublicKey): Boolean {
                fingerprint = fingerprintOf(key)
                return true
            }

            override fun findExistingAlgorithms(hostname: String, port: Int): List<String> = emptyList()
        }
        return try {
            val dirs = connect(s, capture) { sftp ->
                listOf(s.remoteOcrDir(), s.remotePdfDir()).distinct().map { dir ->
                    val attrs = sftp.statExistence(dir)
                    when {
                        attrs == null -> RemoteDirStatus(dir, false)
                        attrs.type != FileMode.Type.DIRECTORY -> RemoteDirStatus(dir, false, "is geen map")
                        else -> RemoteDirStatus(dir, true)
                    }
                }
            }
            val pinned = when {
                s.sftpHostKey.isBlank() -> "NONE"
                s.sftpHostKey == fingerprint -> "MATCH"
                else -> "MISMATCH"
            }
            ConnectionTest(true, "Verbonden met ${s.sftpHost} als ${s.sftpUser}.", fingerprint, pinned, dirs)
        } catch (e: Exception) {
            log.warn("Sftp-test mislukt: {}", e.message)
            ConnectionTest(false, "Verbinden mislukt: ${e.message ?: e.javaClass.simpleName}", fingerprint)
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
        val problems = configProblems(s, requireHostKey = true)
        check(problems.isEmpty()) { problems.joinToString("\n") }
        val duplicates = items.groupBy { it.remotePath }.filter { it.value.size > 1 }.keys
        check(duplicates.isEmpty()) { "Twee bestanden zouden op dezelfde plek terechtkomen: ${duplicates.joinToString()}" }

        return connect(s, FingerprintVerifier.getInstance(s.sftpHostKey)) { sftp ->
            val found = mutableListOf<String>()
            items.map { it.remoteDir }.distinct().forEach { dir ->
                val attrs = sftp.statExistence(dir)
                if (attrs == null) found += "Map bestaat niet op de server: $dir (er worden nooit mappen aangemaakt)"
                else if (attrs.type != FileMode.Type.DIRECTORY) found += "Is geen map op de server: $dir"
            }
            items.forEach { item ->
                if (!Files.isRegularFile(item.local) || Files.size(item.local) == 0L) found += "Lokaal bestand ontbreekt of is leeg: ${item.local}"
                if (found.none { it.contains(item.remoteDir) }) {
                    if (sftp.statExistence(item.remotePath) != null) found += "Bestaat al op de server: ${item.remotePath}"
                    if (sftp.statExistence(tempPath(item)) != null) found += "Tijdelijk bestand van een eerdere poging staat er nog: ${tempPath(item)}"
                }
            }
            check(found.isEmpty()) { "Niets geüpload, de controle vooraf vond:\n" + found.joinToString("\n") }

            val results = mutableListOf<UploadResult>()
            for (item in items) {
                val result = uploadOne(sftp, item)
                results += result
                if (!result.ok) break
            }
            log.info("Sftp-upload naar {}: {} van {} bestanden gelukt", s.sftpHost, results.count { it.ok }, items.size)
            results
        }
    }

    private fun uploadOne(sftp: SFTPClient, item: UploadItem): UploadResult {
        val temp = tempPath(item)
        return try {
            sftp.fileTransfer.setPreserveAttributes(false)
            sftp.put(FileSystemFile(item.local.toFile()), temp)
            // Rename overschrijft op sftp niet; mocht er tussendoor toch iets zijn verschenen, dan faalt dit.
            sftp.rename(temp, item.remotePath)
            sftp.chmod(item.remotePath, 0b110_100_100) // 0644: leesbaar voor de webserver
            val expected = Files.size(item.local)
            val actual = sftp.size(item.remotePath)
            if (actual == expected) UploadResult(item, true, "geüpload ($actual bytes)")
            else UploadResult(item, false, "grootte op de server ($actual) wijkt af van het origineel ($expected): ${item.remotePath}")
        } catch (e: Exception) {
            UploadResult(item, false, "mislukt: ${e.message ?: e.javaClass.simpleName} (controleer ${item.remotePath} en $temp op de server)")
        }
    }

    private fun tempPath(item: UploadItem) = item.remoteDir.trimEnd('/') + "/." + item.remoteName + ".uploading"

    private fun configProblems(s: WorkflowSettings, requireHostKey: Boolean): List<String> = buildList {
        if (s.sftpHost.isBlank()) add("Sftp-host is niet ingesteld.")
        if (s.sftpUser.isBlank()) add("Sftp-gebruiker is niet ingesteld.")
        if (s.sftpPassword.isEmpty()) add("Sftp-wachtwoord is niet ingesteld.")
        if (requireHostKey && s.sftpHostKey.isBlank()) add("De vingerafdruk van de server is nog niet vastgezet (Instellingen, Test verbinding).")
        if (s.collection.isBlank()) add("Collectie is niet ingesteld.")
    }

    private fun <T> connect(s: WorkflowSettings, verifier: HostKeyVerifier, block: (SFTPClient) -> T): T {
        SSHClient().use { ssh ->
            ssh.addHostKeyVerifier(verifier)
            ssh.connectTimeout = 15_000
            ssh.timeout = 60_000
            ssh.connect(s.sftpHost, s.sftpPort)
            ssh.authPassword(s.sftpUser, s.sftpPassword)
            return ssh.newSFTPClient().use(block)
        }
    }

    companion object {
        /** Vingerafdruk in het formaat van OpenSSH: "SHA256:" en de base64 (zonder opvulling) van de sha-256 van de sleutel. */
        fun fingerprintOf(key: PublicKey): String {
            val blob = Buffer.PlainBuffer().putPublicKey(key).compactData
            val digest = MessageDigest.getInstance("SHA-256").digest(blob)
            return "SHA256:" + Base64.getEncoder().withoutPadding().encodeToString(digest)
        }
    }
}
