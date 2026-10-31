package nl.vdzon.hkhworkflow.settings

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "hkh.workflow")
data class WorkflowProperties(
    val settingsFile: String = "hkh-workflow-settings.json",
    val consoleCharset: String = "IBM850",
    /** Wachtwoord van de beheerder (`hkh.workflow.admin-password`). Leeg = niemand kan als beheerder inloggen, dus de instellingen zijn dicht. */
    val adminPassword: String = "",
    /** Het bestand met wat er al verwerkt is, naast het instellingenbestand als het geen pad heeft. */
    val processedLog: String = "hkh-workflow-verwerkt.tsv",
    /** Waar de test-ZCBS draait (`hkh.workflow.test-zcbs-url`): standaard op deze computer, zie tools/docker/zcbs. Op een andere computer is dit het adres van de computer met de container. */
    val testZcbsUrl: String = "http://localhost:8081",
    /** De ftp-server van de test-ZCBS (`hkh.workflow.test-ftp-host` en `test-ftp-port`). */
    val testFtpHost: String = "localhost",
    val testFtpPort: Int = 21,
) {
    /** Zonder wachtwoord, zodat het nooit per ongeluk in een log belandt. */
    override fun toString(): String = "WorkflowProperties(settingsFile=$settingsFile, adminPassword=${if (adminPassword.isEmpty()) "(leeg)" else "***"})"
}
