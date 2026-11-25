package nl.vdzon.hkhworkflow.flow.internal

import java.time.Instant
import java.util.Locale
import org.slf4j.LoggerFactory

/** Eén tijdmeting: wat er gebeurde, hoe lang het duurde en waar de tijd in zat. */
data class Timing(val at: Instant, val what: String, val millis: Long, val detail: String) {
    val text: String get() = "$what: ${Timings.seconds(millis)}" + (if (detail.isEmpty()) "" else " ($detail)")
}

/**
 * De tijdmetingen van één doorloop: hoe lang de controles, het lezen van de mappen met scans, het kopiëren en de stappen duurden. Elke meting
 * gaat als logregel naar het app-log (en zo naar hkh-admin) en staat in de momentopname van de doorloop, zodat achteraf te zien is of een
 * trage schijf, het netwerk, ZCBS of de ftp-server de tijd kostte.
 */
class Timings(private val user: String, private val collection: String) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val list = ArrayDeque<Timing>()

    fun add(what: String, millis: Long, detail: String = "") {
        val timing = Timing(Instant.now(), what, millis, detail)
        synchronized(list) {
            list.addLast(timing)
            while (list.size > MAX) list.removeFirst()
        }
        log.info("Tijdmeting {} in {}: {}", user.ifEmpty { "beheerder" }, collection, timing.text)
    }

    /** Meet [block]; [detail] maakt van de uitkomst de toelichting. Ook als het mislukt, komt de meting erbij. */
    fun <T> measure(what: String, detail: (T) -> String = { "" }, block: () -> T): T {
        val start = System.nanoTime()
        val result = try {
            block()
        } catch (e: Exception) {
            add(what, since(start), "mislukt: ${e.message ?: e.javaClass.simpleName}".take(200))
            throw e
        }
        add(what, since(start), detail(result))
        return result
    }

    fun all(): List<Timing> = synchronized(list) { list.toList() }

    /** Een nieuwe doorloop begint met een lege lijst; de logregels van de vorige blijven in het app-log staan. */
    fun clear() = synchronized(list) { list.clear() }

    companion object {
        private const val MAX = 200

        fun since(startNanos: Long): Long = (System.nanoTime() - startNanos) / 1_000_000

        /** `0,3 s`, `12,4 s` of `2 min 5 s`. */
        fun seconds(millis: Long): String =
            if (millis >= 120_000) "${millis / 60_000} min ${millis / 1000 % 60} s" else String.format(Locale.forLanguageTag("nl"), "%.1f s", millis / 1000.0)

        /** De snelheid van een kopie, of leeg als die te kort duurde om iets over te zeggen. */
        fun speed(bytes: Long, millis: Long): String =
            if (millis < 50 || bytes <= 0) "" else String.format(Locale.forLanguageTag("nl"), "%.1f MB/s", bytes / 1_048_576.0 / (millis / 1000.0))

        fun megabytes(bytes: Long): String = String.format(Locale.forLanguageTag("nl"), "%.1f MB", bytes / 1_048_576.0)
    }
}
