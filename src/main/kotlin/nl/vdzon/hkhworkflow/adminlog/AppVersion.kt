package nl.vdzon.hkhworkflow.adminlog

import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Properties

/**
 * Het versienummer van deze build, zoals het in het workflow-logboek van hkh-admin staat: de versie uit de pom plus de bouwtijd (UTC),
 * bijvoorbeeld `0.0.1-SNAPSHOT+20261031.1405`. De bouwtijd maakt elke build herkenbaar zonder dat de pom steeds omhoog hoeft. Buiten een
 * gebouwde jar (vanuit de ontwikkelomgeving) is het `ontwikkel`.
 */
object AppVersion {
    val current: String by lazy { read(AppVersion::class.java.getResourceAsStream("/META-INF/build-info.properties")?.use { Properties().apply { load(it) } }) }

    internal fun read(info: Properties?): String {
        val version = info?.getProperty("build.version")?.takeIf { it.isNotBlank() } ?: return "ontwikkel"
        val time = info.getProperty("build.time")?.let { runCatching { Instant.parse(it) }.getOrNull() } ?: return version
        return version + "+" + TIME.format(time)
    }

    private val TIME = DateTimeFormatter.ofPattern("yyyyMMdd.HHmm").withZone(ZoneOffset.UTC)
}
