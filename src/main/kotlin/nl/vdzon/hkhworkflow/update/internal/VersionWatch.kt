package nl.vdzon.hkhworkflow.update.internal

import java.nio.file.Files
import java.nio.file.Path
import nl.vdzon.hkhworkflow.adminlog.AdminLog
import nl.vdzon.hkhworkflow.adminlog.AppVersion
import nl.vdzon.hkhworkflow.settings.SettingsService
import org.slf4j.LoggerFactory
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component

/**
 * Merkt bij het opstarten of de app een andere versie is dan de vorige keer: het startscript van de service heeft dan een nieuwe jar geplaatst
 * (of de vorige teruggezet). Dat komt in het app-log en bij hkh-admin, zodat de beheerder ziet wanneer een server is bijgewerkt. De versie van
 * de vorige start staat in een tekstbestand naast het instellingenbestand.
 */
@Component
class VersionWatch(private val settingsService: SettingsService, private val adminLog: AdminLog) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun file(): Path = (settingsService.settingsFile().parent ?: Path.of(".")).resolve(FILE_NAME)

    @EventListener(ApplicationReadyEvent::class)
    fun started() {
        try {
            check(AppVersion.current, file())
        } catch (e: Exception) {
            log.warn("De versie van de vorige start kon niet worden vergeleken: {}", e.message)
        }
    }

    internal fun check(current: String, file: Path) {
        val previous = if (Files.isRegularFile(file)) Files.readString(file).trim().takeIf { it.isNotEmpty() } else null
        when (previous) {
            null -> log.info("App gestart met {} (de versie van de vorige start is niet bekend)", current)
            current -> log.info("App gestart met {} (dezelfde versie als de vorige keer)", current)
            else -> {
                val reason = reason(previous, current)
                log.info("{}: {} (de vorige was {})", reason, current, previous)
                adminLog.versionChanged(previous, current, reason)
            }
        }
        if (previous != current) {
            file.parent?.let { Files.createDirectories(it) }
            Files.writeString(file, current + "\n")
        }
    }

    companion object {
        const val FILE_NAME = "hkh-workflow-versie.txt"
        private val BUILD = Regex("""build (\d+)""")

        /** Een lager buildnummer dan de vorige keer betekent dat het startscript de vorige versie heeft teruggezet. */
        internal fun reason(previous: String, current: String): String {
            val before = BUILD.find(previous)?.groupValues?.get(1)?.toIntOrNull()
            val now = BUILD.find(current)?.groupValues?.get(1)?.toIntOrNull()
            return if (before != null && now != null && now < before) "Vorige versie teruggezet" else "Nieuwe versie geïnstalleerd"
        }
    }
}
