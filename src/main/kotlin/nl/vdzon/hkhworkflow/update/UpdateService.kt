package nl.vdzon.hkhworkflow.update

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.util.Properties
import java.util.zip.ZipFile
import nl.vdzon.hkhworkflow.adminlog.AppVersion
import nl.vdzon.hkhworkflow.settings.SettingsService
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service

/** Wat er nu loopt dat een herstart niet overleeft (een programma of een upload), per gebruiker; leeg als de app vrij is. */
interface Activity {
    fun running(): List<String>
}

/** Start de app opnieuw met exitcode 10: het startscript van de service (tools/windows-server/run.ps1) plaatst dan de klaargezette jar. Keert direct terug. */
interface AppRestarter {
    fun restart()
}

/** De stand voor de pagina Versie. */
data class UpdateStatus(
    val current: AppVersion.Info,
    val runsAsService: Boolean,
    val latest: Release?,
    val newer: Boolean,
    val checkedAt: Instant?,
    val checkError: String?,
    val busy: List<String>,
    val nightly: Boolean,
    val nightlyPending: Boolean,
    val restarting: Boolean,
    val events: List<String>,
)

/**
 * Houdt de app bij: kijkt op GitHub of er een nieuwere build is, zet die klaar in `updates/` (met sha256-controle) en laat de app opnieuw
 * starten, waarna het startscript van de service de jar verwisselt (en terugdraait als de nieuwe niet gezond wordt). Bijwerken kan alleen
 * als de app als service draait (`HKH_SERVICE=1`, door de service gezet), anders zou het afsluiten de app alleen maar doden. Er wordt nooit
 * bijgewerkt terwijl een programma of een upload loopt. Elke nacht om 00:00 gebeurt dit vanzelf (instelbaar), zie [nightly].
 * Ontwerp: docs/auto-update.md.
 */
@Service
class UpdateService(
    private val source: ReleaseSource,
    private val settingsService: SettingsService,
    private val activity: Activity,
    private val restarter: AppRestarter,
    private val clock: Clock,
    @Value("\${hkh.workflow.update-dir:updates}") updateDir: String,
    @Value("\${hkh.service:false}") val runsAsService: Boolean,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val dir: Path = Path.of(updateDir).toAbsolutePath()
    private val events = ArrayDeque<String>()

    @Volatile private var latest: Release? = null

    @Volatile private var checkedAt: Instant? = null

    @Volatile private var checkError: String? = null

    @Volatile private var restarting = false

    @Volatile private var pendingUntil: Instant? = null

    private fun now(): Instant = Instant.now(clock)

    private fun event(text: String) {
        log.info("Bijwerken: {}", text)
        synchronized(events) {
            val line = java.time.LocalDateTime.ofInstant(now(), ZONE).let { "%02d-%02d %02d:%02d  %s".format(it.dayOfMonth, it.monthValue, it.hour, it.minute, text) }
            if (events.firstOrNull()?.substring(13) == text) events.removeFirst() // dezelfde melding vlak na elkaar: alleen de laatste
            events.addFirst(line)
            while (events.size > MAX_EVENTS) events.removeLast()
        }
    }

    /** Vraagt GitHub om de nieuwste build en onthoudt het antwoord. Null als dat niet lukte (de reden staat dan in de status). */
    @Synchronized
    fun check(): Release? {
        checkedAt = now()
        return try {
            latest = source.latest()
            checkError = if (latest == null) "Er staat geen build klaar op GitHub." else null
            latest
        } catch (e: Exception) {
            checkError = "GitHub is niet bereikbaar: ${e.message}"
            log.warn("Controle op een nieuwe versie mislukt: {}", e.message)
            null
        }
    }

    private fun isNewer(release: Release?): Boolean = release != null && release.number > (AppVersion.info.number ?: 0)

    fun status(): UpdateStatus = UpdateStatus(
        current = AppVersion.info,
        runsAsService = runsAsService,
        latest = latest,
        newer = isNewer(latest),
        checkedAt = checkedAt,
        checkError = checkError,
        busy = activity.running(),
        nightly = settingsService.get().nightlyUpdate,
        nightlyPending = pendingUntil != null,
        restarting = restarting,
        events = synchronized(events) { events.toList() },
    )

    /**
     * Haalt de nieuwste build binnen en laat de app opnieuw starten. Geeft null als dat is gestart, anders waarom het niet kan.
     * De app is dan binnen een paar seconden weg en komt na ongeveer een halve minuut terug met de nieuwe versie.
     */
    fun updateNow(by: String): String? {
        if (restarting) return "De app wordt al opnieuw gestart."
        if (!runsAsService) return "Bijwerken kan alleen als de app als Windows-service draait (zie tools/windows-server)."
        val release = check() ?: return checkError
        if (!isNewer(release)) return "Je draait al de nieuwste build (${release.number})."
        busyMessage()?.let { return it }
        try {
            stage(release)
        } catch (e: Exception) {
            event("Build ${release.number} klaarzetten mislukt: ${e.message}")
            return "Klaarzetten van build ${release.number} mislukt: ${e.message}"
        }
        busyMessage()?.let { return it } // het downloaden kan even duren: kijk vlak voor de herstart nog eens
        restarting = true
        event("Build ${release.number} staat klaar (gestart door $by); de app start opnieuw.")
        restarter.restart()
        return null
    }

    private fun busyMessage(): String? {
        val busy = activity.running()
        return if (busy.isEmpty()) null else "Er loopt nog een programma of upload (${busy.joinToString(", ")}). Wacht tot die klaar is."
    }

    /** Downloadt de jar naar `updates/` en controleert hem; pas dan komt hij onder zijn echte naam te staan, zodat het startscript nooit een half bestand ziet. */
    internal fun stage(release: Release) {
        val shaUrl = release.shaUrl ?: error("bij deze build staat geen sha256")
        val expected = source.text(shaUrl).trim().split(Regex("\\s+")).first().lowercase()
        Files.createDirectories(dir)
        val part = dir.resolve("hkh-workflow.jar.part")
        val actual = try {
            source.download(release.jarUrl, part)
        } catch (e: Exception) {
            Files.deleteIfExists(part)
            throw e
        }
        if (actual != expected) {
            Files.deleteIfExists(part)
            error("de sha256 van het gedownloade bestand klopt niet")
        }
        val number = jarBuildNumber(part)
        if (number != release.number) {
            Files.deleteIfExists(part)
            error("het bestand blijkt build ${number ?: "?"} te zijn en niet build ${release.number}")
        }
        Files.writeString(dir.resolve("hkh-workflow.jar.sha256"), "$actual  hkh-workflow.jar\n")
        Files.move(part, dir.resolve("hkh-workflow.jar"), StandardCopyOption.REPLACE_EXISTING)
    }

    /** Elke nacht om 00:00 (Nederlandse tijd; `hkh.workflow.update-cron` om dat te testen): als de schakelaar aan staat en er een nieuwere build is, bijwerken zodra niemand een programma draait. */
    @Scheduled(cron = "\${hkh.workflow.update-cron:0 0 0 * * *}", zone = "Europe/Amsterdam")
    fun nightly() {
        if (!runsAsService || !settingsService.get().nightlyUpdate) return
        pendingUntil = now().plus(NIGHT_WINDOW)
        event("Nachtelijke controle.")
        attempt()
    }

    /** Is er 's nachts nog iemand bezig, dan kijkt de app elke 10 minuten opnieuw, tot het venster voorbij is. */
    @Scheduled(initialDelay = 600_000, fixedDelay = 600_000)
    fun retryNightly() {
        if (pendingUntil != null) attempt()
    }

    internal fun attempt() {
        val until = pendingUntil ?: return
        if (now().isAfter(until)) {
            pendingUntil = null
            event("Nachtelijke update overgeslagen: er was het hele venster iemand bezig of GitHub was niet bereikbaar.")
            return
        }
        val release = check()
        if (release == null) {
            event(checkError ?: "Geen build gevonden.")
            return
        }
        if (!isNewer(release)) {
            pendingUntil = null
            event("Nachtelijke controle: build ${release.number} is de nieuwste.")
            return
        }
        val problem = updateNow("de nachtelijke update")
        if (problem == null) pendingUntil = null else event("Nachtelijke update wacht: $problem")
    }

    companion object {
        private val ZONE = ZoneId.of("Europe/Amsterdam")
        private val NIGHT_WINDOW = Duration.ofHours(3)
        private const val MAX_EVENTS = 20

        /** Het buildnummer dat in een jar staat (`build-info.properties`), of null als dat er niet is. */
        internal fun jarBuildNumber(jar: Path): Int? = ZipFile(jar.toFile()).use { zip ->
            val entry = zip.getEntry("BOOT-INF/classes/META-INF/build-info.properties") ?: zip.getEntry("META-INF/build-info.properties") ?: return null
            Properties().apply { zip.getInputStream(entry).use { load(it) } }.getProperty("build.number")?.trim()?.toIntOrNull()
        }
    }
}
