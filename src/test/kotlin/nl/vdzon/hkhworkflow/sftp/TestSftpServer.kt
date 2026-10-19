package nl.vdzon.hkhworkflow.sftp

import java.nio.file.Path
import org.apache.sshd.common.config.keys.KeyUtils
import org.apache.sshd.server.SshServer
import org.apache.sshd.server.keyprovider.SimpleGeneratorHostKeyProvider
import org.apache.sshd.sftp.server.SftpSubsystemFactory
import org.apache.sshd.common.file.virtualfs.VirtualFileSystemFactory

/** Sftp-server in het geheugen voor de tests. `root` is de wortel van het bestandssysteem voor de ingelogde gebruiker. */
class TestSftpServer(val root: Path, hostKeyFile: Path) : AutoCloseable {
    private val server: SshServer = SshServer.setUpDefaultServer().apply {
        port = 0
        keyPairProvider = SimpleGeneratorHostKeyProvider(hostKeyFile).apply { algorithm = "EC"; keySize = 256 }
        setPasswordAuthenticator { user, password, _ -> user == USER && password == PASSWORD }
        subsystemFactories = listOf(SftpSubsystemFactory())
        fileSystemFactory = VirtualFileSystemFactory(root)
        start()
    }

    val port: Int get() = server.port

    /** Vingerafdruk van de hostsleutel, berekend door de serverbibliotheek zelf (onafhankelijk van onze code). */
    val fingerprint: String get() = KeyUtils.getFingerPrint(server.keyPairProvider.loadKeys(null).first().public)

    override fun close() = server.stop(true)

    companion object {
        const val USER = "robbert"
        const val PASSWORD = "geheim"
    }
}
