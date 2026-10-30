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
) {
    /** Zonder wachtwoord, zodat het nooit per ongeluk in een log belandt. */
    override fun toString(): String = "WorkflowProperties(settingsFile=$settingsFile, adminPassword=${if (adminPassword.isEmpty()) "(leeg)" else "***"})"
}
