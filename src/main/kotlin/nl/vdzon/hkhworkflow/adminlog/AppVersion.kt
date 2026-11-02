package nl.vdzon.hkhworkflow.adminlog

import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.Properties

/**
 * Welke build dit is. Het buildnummer en de commit komen van de GitHub Action die de jar bouwt (zie `.github/workflows/build.yml` en de
 * `build-info` in de pom); de bouwtijd zet Maven er altijd in. Een lokale build heeft geen buildnummer en buiten een gebouwde jar
 * (vanuit de ontwikkelomgeving) is er niets bekend.
 */
object AppVersion {
    data class Info(val version: String, val number: Int?, val time: Instant?, val commit: String?) {
        /** De versie met de bouwtijd (UTC), bijvoorbeeld `0.0.1-SNAPSHOT+20261031.1405`; zonder bouwtijd alleen de versie. */
        val core: String get() = if (time == null) version else version + "+" + TIME.format(time)

        /** Zoals het in het workflow-logboek van hkh-admin staat: `build 57 (0.0.1-SNAPSHOT+20261102.1934)`, of zonder buildnummer alleen [core]. */
        val text: String get() = if (number != null) "build $number ($core)" else core

        /** De korte regel onderin het scherm: `build 57 · 2 nov 2026 20:34` (tijd in Nederlandse tijd), of `lokale build · …` zonder buildnummer. */
        val label: String
            get() = if (version == UNKNOWN && time == null) "ontwikkelversie"
            else listOfNotNull(if (number != null) "build $number" else "lokale build", time?.let { SCREEN.format(it) }).joinToString(" · ")

        /** Voor de hint bij de regel onderin het scherm: versie en commit. */
        val details: String get() = listOfNotNull(version.takeIf { it != UNKNOWN }, commit?.let { "commit $it" }).joinToString(", ")
    }

    val info: Info by lazy { parse(AppVersion::class.java.getResourceAsStream("/META-INF/build-info.properties")?.use { Properties().apply { load(it) } }) }

    val current: String get() = info.text

    internal fun read(info: Properties?): String = parse(info).text

    internal fun parse(info: Properties?): Info {
        val version = info?.getProperty("build.version")?.takeIf { it.isNotBlank() } ?: return Info(UNKNOWN, null, null, null)
        val time = info.getProperty("build.time")?.let { runCatching { Instant.parse(it) }.getOrNull() }
        val number = info.getProperty("build.number")?.trim()?.toIntOrNull()?.takeIf { it > 0 }
        val commit = info.getProperty("build.commit")?.trim()?.takeIf { it.isNotEmpty() && it != "-" }?.take(7)
        return Info(version, number, time, commit)
    }

    private const val UNKNOWN = "ontwikkel"
    private val TIME = DateTimeFormatter.ofPattern("yyyyMMdd.HHmm").withZone(ZoneOffset.UTC)
    private val SCREEN = DateTimeFormatter.ofPattern("d MMM yyyy HH:mm", Locale.of("nl", "NL")).withZone(ZoneId.of("Europe/Amsterdam"))
}
