package nl.vdzon.hkhworkflow.integration

import java.net.ServerSocket
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import org.testcontainers.DockerClientFactory
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.wait.strategy.Wait
import org.testcontainers.images.builder.ImageFromDockerfile

/**
 * De ZCBS van de integratietests in een Docker-container: Apache met Perl en een ftp-server, gebouwd uit src/test/resources/zcbs-docker.
 * Standaard draait daarin onze eigen nabouw (`cgi-bin/archief.pl`). Met `-Dzcbs.real=true` draait dezelfde beginstand op de echte
 * ZCBS-scripts uit de lokale, niet in git opgenomen map tools/docker/zcbs/files: het ijkpunt voor de nabouw (zie docs/integratietests.md).
 *
 * Deze klasse kent de applicatie niet: de tests zien ZCBS alleen zoals de buitenwereld hem ziet.
 */
class ZcbsServer private constructor(val real: Boolean, context: Path) : AutoCloseable {
    private class Container(image: ImageFromDockerfile) : GenericContainer<Container>(image) {
        fun bind(hostPort: Int, containerPort: Int) = addFixedExposedPort(hostPort, containerPort)
    }

    private val passivePorts = freePortRange(PASSIVE_PORTS)
    private val container = Container(
        ImageFromDockerfile(if (real) "hkh-workflow-zcbs-echt" else "hkh-workflow-zcbs-nabouw", false).withFileFromPath(".", context),
    ).apply {
        withExposedPorts(80, 21)
        withEnv("PASV_MIN_PORT", passivePorts.first.toString())
        withEnv("PASV_MAX_PORT", passivePorts.last.toString())
        // Passieve ftp geeft het poortnummer door aan de client, dus die poorten moeten op de host hetzelfde nummer hebben.
        passivePorts.forEach { bind(it, it) }
        waitingFor(Wait.forHttp(SCRIPT).forPort(80).forStatusCode(200))
    }

    fun start(): ZcbsServer = also { container.start() }

    /** Het adres van de ZCBS op de host, op een willekeurige poort: de eigen test-ZCBS op 8081 blijft ongemoeid. */
    val baseUrl: String get() = "http://${container.host}:${container.getMappedPort(80)}"
    val ftpHost: String get() = container.host
    val ftpPort: Int get() = container.getMappedPort(21)

    /** Zet alle bestanden terug in de beginstand: een leeg archief met de 2 testitems, geen vergrendelingen, geen sessies. */
    fun reset() = sh("zcbs-reset")

    /** Laat alle sessies verlopen. Alleen bij de nabouw; de echte ZCBS doet dat pas na uren. */
    fun expireSessions() {
        check(!real) { "De echte ZCBS kan de sessies niet zomaar laten verlopen." }
        sh(": > $ROOT/archief/logging/sessions.txt")
    }

    /** Voegt een gebruiker toe aan `permit.lst`, met rechten [level]: 1+ is beheerder, 1 is bewerken, 3 is lezen. */
    fun addUser(name: String, password: String, level: String = "1+") {
        sh("printf 'archief\\t$level\\txxx.xxx.xxx.xx\\t$name - $password\\n' >> $ROOT/cgi-bin/misc/permit.lst")
    }

    fun fileExists(path: String): Boolean = container.execInContainer("test", "-f", "$ROOT/$path").exitCode == 0

    fun readFile(path: String): String =
        container.execInContainer(StandardCharsets.ISO_8859_1, "cat", "$ROOT/$path").also { check(it.exitCode == 0) { "$path niet te lezen: ${it.stderr}" } }.stdout

    private fun sh(command: String) {
        val result = container.execInContainer("sh", "-c", command)
        check(result.exitCode == 0) { "$command mislukte: ${result.stderr}" }
    }

    override fun close() = container.stop()

    companion object {
        const val SCRIPT = "/cgi-bin/archief.pl"
        const val FTP_PASSWORD = "hkhtest"
        const val ADMIN = "Test Beheerder"
        const val ADMIN_PASSWORD = "Beheer1"
        const val EDITOR = "Test Bewerker"
        const val EDITOR_PASSWORD = "Bewerk1"
        const val READER = "Test Lezer"
        const val READER_PASSWORD = "Lees1"
        private const val ROOT = "/home/hkhadmin/domains/historischekringheemskerk.nl/public_html"
        private const val PASSIVE_PORTS = 10
        private val FIXTURE: Path = Path.of("src/test/resources/zcbs-docker")
        private val REAL_ZCBS: Path = Path.of("tools/docker/zcbs/files")

        /** Vraagt de echte ZCBS als ijkpunt (`-Dzcbs.real=true`); dan is een ontbrekende kopie een fout en wordt er niets overgeslagen. */
        val wantsReal: Boolean get() = System.getProperty("zcbs.real") == "true"

        fun dockerAvailable(): Boolean = runCatching { DockerClientFactory.instance().isDockerAvailable }.getOrDefault(false)

        fun create(): ZcbsServer = if (wantsReal) ZcbsServer(true, withRealScripts()) else ZcbsServer(false, FIXTURE)

        /** Eén ZCBS voor de hele testrun. */
        val shared: ZcbsServer by lazy { create().start().also { server -> Runtime.getRuntime().addShutdownHook(Thread { server.close() }) } }

        /** De map van het image met daarin de echte scripts van ZCBS: tijdelijk en lokaal, want die code hoort niet in deze repository. */
        private fun withRealScripts(): Path {
            check(Files.isRegularFile(REAL_ZCBS.resolve("cgi-bin/objecten.pl"))) { "Geen echte ZCBS gevonden in $REAL_ZCBS (zie tools/docker/zcbs/README.md)." }
            val target = Files.createTempDirectory("zcbs-echt")
            FIXTURE.toFile().copyRecursively(target.toFile(), overwrite = true)
            val html = target.resolve("public_html")
            fun copy(from: String, to: String = from, skip: Set<String> = emptySet()) {
                val source = REAL_ZCBS.resolve(from).toFile()
                if (source.isFile) { Files.copy(source.toPath(), html.resolve(to), StandardCopyOption.REPLACE_EXISTING); return }
                Files.createDirectories(html.resolve(to))
                source.listFiles().orEmpty().filter { it.isFile && it.name !in skip }.forEach { Files.copy(it.toPath(), html.resolve(to).resolve(it.name), StandardCopyOption.REPLACE_EXISTING) }
            }
            copy("cgi-bin/archief.pl")
            copy("cgi-bin/objecten.pl")
            copy("cgi-bin/misc", skip = setOf("permit.lst", "images.log", "backup.log", "downloads.txt", "whitelist.txt"))
            Files.writeString(html.resolve("cgi-bin/misc/whitelist.txt"), "")
            copy("cgi-bin/archief/misc")
            copy("archief/misc")
            Files.createDirectories(html.resolve("cgi-bin/archief/backup1"))
            Files.createDirectories(html.resolve("cgi-bin/archief/addenda"))
            Files.writeString(html.resolve("cgi-bin/archief/addenda/addenda.lst"), "")
            return target
        }

        private fun freePortRange(size: Int): IntRange {
            var start = 31000
            while (start < 60000) {
                val range = start until start + size
                if (range.all { portFree(it) }) return range
                start += size
            }
            error("Geen $size vrije poorten op een rij gevonden.")
        }

        private fun portFree(port: Int) = runCatching { ServerSocket(port).close() }.isSuccess
    }
}
