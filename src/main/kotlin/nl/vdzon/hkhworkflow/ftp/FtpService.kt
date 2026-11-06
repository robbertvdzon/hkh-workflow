package nl.vdzon.hkhworkflow.ftp

import java.nio.file.Path


/** Eén bestand dat naar de server moet: [local] wordt `<remoteDir>/<remoteName>`. */
data class UploadItem(val local: Path, val remoteDir: String, val remoteName: String) {
    val remotePath: String get() = remoteDir.trimEnd('/') + "/" + remoteName
}

data class UploadResult(val item: UploadItem, val ok: Boolean, val message: String)

data class RemoteDirStatus(val path: String, val exists: Boolean, val message: String? = null)

/** Uitkomst van "Test verbinding". */
data class ConnectionTest(val ok: Boolean, val message: String, val dirs: List<RemoteDirStatus> = emptyList())

/** Uploadt bestanden naar de ZCBS-webserver met ftp, zoals images.exe. Overschrijft nooit iets en verwijdert nooit iets op de server. */
interface FtpService {
    /** Verbindt, logt in en controleert of de mappen voor tekst en pdf bestaan. Schrijft niets. */
    fun testConnection(timeoutMillis: Int = 15_000): ConnectionTest

    /**
     * Uploadt [items]. Gooit een [IllegalStateException] met alle gevonden problemen als de controle vooraf faalt; dan is er niets geüpload.
     * Anders staat per bestand in het resultaat of het gelukt is; bij de eerste fout wordt gestopt.
     */
    fun upload(items: List<UploadItem>): List<UploadResult>

    /** Welke van [paths] op de server staan, of null als de server niet te bereiken is. */
    fun existing(paths: Collection<String>): Set<String>?
}
