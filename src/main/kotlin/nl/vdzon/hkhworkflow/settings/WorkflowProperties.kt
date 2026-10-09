package nl.vdzon.hkhworkflow.settings

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "hkh.workflow")
data class WorkflowProperties(
    val settingsFile: String = "hkh-workflow-settings.json",
    val consoleCharset: String = "IBM850",
)
