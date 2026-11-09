package nl.vdzon.hkhworkflow.integration

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import org.springframework.test.context.DynamicPropertyRegistry

/**
 * Alles buiten de app, eenmaal per testrun: de ZCBS in Docker, WireMock voor GitHub en het workflow-logboek, en de mappen op "schijf" (scans, werkmap,
 * back-ups, de programma's) in een tijdelijke map. De app krijgt dit net als in productie via zijn instellingenbestand en eigenschappen.
 */
object TestEnvironment {
    const val ADMIN_PASSWORD = "beheerder-test"

    val root: Path by lazy { Files.createTempDirectory("hkh-workflow-it") }
    val scans: Path get() = root.resolve("scans")
    val backupPdf: Path get() = root.resolve("backup-pdf")
    val backup100pct: Path get() = root.resolve("backup-100pct")
    val work: Path get() = root.resolve("werkmap")
    val settingsFile: Path get() = root.resolve("hkh-workflow-settings.json")
    val processedLog: Path get() = root.resolve("verwerkt.tsv")

    val zcbs: ZcbsServer get() = ZcbsServer.shared

    /** GitHub en het workflow-logboek: de enige andere servers waar de app mee praat. */
    val outside: WireMockServer by lazy {
        WireMockServer(WireMockConfiguration.options().dynamicPort()).also {
            it.start()
            it.stubFor(WireMock.post(WireMock.anyUrl()).willReturn(WireMock.ok()))
            it.stubFor(WireMock.get(WireMock.urlPathEqualTo("/repos/robbertvdzon/hkh-workflow/releases")).willReturn(WireMock.okJson(RELEASES)))
            Runtime.getRuntime().addShutdownHook(Thread { it.stop() })
        }
    }

    fun register(registry: DynamicPropertyRegistry) {
        prepareDisk()
        if (ZcbsServer.dockerAvailable()) {
            registry.add("hkh.workflow.test-zcbs-url") { zcbs.baseUrl }
            registry.add("hkh.workflow.test-ftp-host") { zcbs.ftpHost }
            registry.add("hkh.workflow.test-ftp-port") { zcbs.ftpPort }
            prepareSettings()
        }
        registry.add("server.address") { "127.0.0.1" }
        registry.add("hkh.workflow.settings-file") { settingsFile.toString() }
        registry.add("hkh.workflow.processed-log") { processedLog.toString() }
        registry.add("hkh.workflow.log-dir") { root.resolve("logs").toString() }
        registry.add("hkh.workflow.update-dir") { root.resolve("updates").toString() }
        registry.add("hkh.workflow.admin-password") { ADMIN_PASSWORD }
        registry.add("hkh.workflow.console-charset") { "UTF-8" }
        registry.add("hkh.workflow.test-admin-log-url") { outside.baseUrl() }
        registry.add("hkh.workflow.update-api") { outside.baseUrl() }
    }

    /** Een schone stand voor het volgende scenario, voor zover die buiten de app ligt. */
    fun reset() {
        zcbs.reset()
        Files.deleteIfExists(processedLog)
        outside.resetRequests()
        listOf(scans.resolve("Jelly"), scans.resolve("Ria")).forEach { dir -> dir.toFile().listFiles().orEmpty().forEach { it.deleteRecursively() } }
        listOf(backupPdf, backup100pct).forEach { dir -> dir.toFile().listFiles().orEmpty().forEach { it.deleteRecursively() } }
    }

    private fun prepareDisk() {
        listOf(scans.resolve("Jelly"), scans.resolve("Ria"), backupPdf, backup100pct, work, root.resolve("programs/pdf2zcbs"), root.resolve("programs/images")).forEach { Files.createDirectories(it) }
    }

    /** De programma's staan vast in de instellingen, dus de server in het script ook. */
    private fun prepareSettings() {
        val pdf2zcbs = root.resolve("programs/pdf2zcbs/pdf2zcbs.sh")
        val images = root.resolve("programs/images/images.sh")
        copy("integration/programs/pdf2zcbs/pdf2zcbs.sh", pdf2zcbs)
        copy("integration/programs/sample.bin", pdf2zcbs.resolveSibling("sample.bin"))
        copy("integration/programs/images/images.sh", images)
        Files.writeString(images, Files.readString(images).replace("@FTP_HOST@", zcbs.ftpHost).replace("@FTP_PORT@", zcbs.ftpPort.toString()))
        listOf(pdf2zcbs, images).forEach { it.toFile().setExecutable(true) }
        val json = """
            {
              "testFolder": "$work",
              "imagesExe": "$images",
              "pdf2zcbsExe": "$pdf2zcbs",
              "perlioUnbuffered": true,
              "useTestZcbs": true,
              "nightlyUpdate": false,
              "sourceFolders": "Jelly=${scans.resolve("Jelly")}\nRia=${scans.resolve("Ria")}",
              "ftpPassword": "${ZcbsServer.FTP_PASSWORD}",
              "collections": { "archief": { "imagesResolution": "6", "backup100pctFolder": "$backup100pct", "backupPdfFolder": "$backupPdf" } }
            }
        """.trimIndent()
        Files.writeString(settingsFile, json)
    }

    fun copy(resource: String, target: Path) {
        Files.createDirectories(target.parent)
        checkNotNull(javaClass.classLoader.getResourceAsStream(resource)) { "$resource ontbreekt" }.use { Files.copy(it, target, StandardCopyOption.REPLACE_EXISTING) }
    }

    private val RELEASES = """
        [{"tag_name":"build-99999","published_at":"2026-11-01T10:00:00Z","assets":[
          {"name":"hkh-workflow.jar","browser_download_url":"https://example.test/hkh-workflow.jar"},
          {"name":"hkh-workflow.jar.sha256","browser_download_url":"https://example.test/hkh-workflow.jar.sha256"}]}]
    """.trimIndent()
}
