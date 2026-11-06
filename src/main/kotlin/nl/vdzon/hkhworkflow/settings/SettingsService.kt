package nl.vdzon.hkhworkflow.settings

import java.nio.file.Path

/** De instellingen van de app, voor de andere modules. Hoe ze worden bewaard, is de zaak van deze module. */
interface SettingsService {
    fun settingsFile(): Path

    fun get(): WorkflowSettings

    /** Vult in wat uit de keuze voor de test-ZCBS volgt: het adres van ZCBS en de ftp-server. */
    fun withEnvironment(settings: WorkflowSettings): WorkflowSettings

    /** Bewaart de instellingen en meldt een echte wijziging met een [SettingsChanged]-event. */
    fun save(settings: WorkflowSettings, reason: String = "Instellingen opgeslagen", by: String? = null, address: String? = null): WorkflowSettings

    /** Waarschuwingen per veld (bestaat de map/het bestand?). Blokkeert het opslaan niet. */
    fun check(settings: WorkflowSettings): Map<String, String>

    /** Waarschuwingen over de instellingen die alleen de latere stappen (upload, back-up) nodig hebben. */
    fun checkExtras(settings: WorkflowSettings): Map<String, String>
}
