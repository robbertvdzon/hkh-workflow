package nl.vdzon.hkhworkflow.ftp

import java.nio.file.Path
import org.apache.ftpserver.DataConnectionConfigurationFactory
import org.apache.ftpserver.FtpServer
import org.apache.ftpserver.FtpServerFactory
import org.apache.ftpserver.ftplet.Authority
import org.apache.ftpserver.listener.ListenerFactory
import org.apache.ftpserver.usermanager.ClearTextPasswordEncryptor
import org.apache.ftpserver.usermanager.PropertiesUserManagerFactory
import org.apache.ftpserver.usermanager.impl.BaseUser
import org.apache.ftpserver.usermanager.impl.WritePermission

/** Ftp-server in het geheugen voor de tests. `root` is de wortel van het bestandssysteem voor de ingelogde gebruiker. */
class TestFtpServer(val root: Path, fixedPort: Int = 0) : AutoCloseable {
    private val server: FtpServer
    val port: Int

    init {
        val users = PropertiesUserManagerFactory().apply { setPasswordEncryptor(ClearTextPasswordEncryptor()) }.createUserManager()
        users.save(BaseUser().apply {
            name = USER
            setPassword(PASSWORD)
            homeDirectory = root.toString()
            authorities = listOf<Authority>(WritePermission())
        })
        val listener = ListenerFactory().apply {
            this.port = fixedPort
            dataConnectionConfiguration = DataConnectionConfigurationFactory().apply { passiveExternalAddress = "127.0.0.1" }.createDataConnectionConfiguration()
        }.createListener()
        server = FtpServerFactory().apply {
            userManager = users
            addListener("default", listener)
        }.createServer()
        server.start()
        port = listener.port
    }

    override fun close() = server.stop()

    companion object {
        const val USER = "robbert"
        const val PASSWORD = "geheim"
    }
}
