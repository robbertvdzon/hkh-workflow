package nl.vdzon.hkhworkflow.settings

/**
 * De instellingen zijn gewijzigd en opgeslagen. De module `adminlog` luistert hiernaar en meldt het aan het workflow-logboek van hkh-admin.
 * [by] is de ZCBS-naam van wie het deed (als die bekend is), [address] het adres van de browser, [reason] een korte omschrijving.
 */
data class SettingsChanged(
    val before: WorkflowSettings,
    val after: WorkflowSettings,
    val reason: String,
    val by: String?,
    val address: String?,
)
