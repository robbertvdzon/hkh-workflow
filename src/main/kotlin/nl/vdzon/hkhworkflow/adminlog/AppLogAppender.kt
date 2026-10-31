package nl.vdzon.hkhworkflow.adminlog

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.classic.spi.ThrowableProxyUtil
import ch.qos.logback.core.AppenderBase
import java.nio.file.Path
import java.time.format.DateTimeFormatter

/**
 * Logback-appender die elke logregel van INFO en hoger in de wachtrij ([AppLogSpool]) zet, voor het workflow-logboek van hkh-admin.
 * Hij wordt geconfigureerd in `logback-spring.xml` en doet niets zolang er geen adres van het logboek is ingesteld ([adminLogUrl] leeg).
 *
 * Geheimen worden al hier gemaskeerd ([AppLogMasker]); de ontvanger doet dat nog een keer. De regels van de meldingen zelf (pakket `adminlog`)
 * blijven erbuiten, zodat de verzender zijn eigen werk niet in het logboek zet.
 */
class AppLogAppender : AppenderBase<ILoggingEvent>() {
    /** Het adres van het workflow-logboek; leeg = uit. */
    var adminLogUrl: String = ""

    /** De map voor de wachtrij. */
    var directory: String = ""

    private var spool: AppLogSpool? = null

    override fun start() {
        if (adminLogUrl.isBlank() || directory.isBlank()) return
        spool = AppLogSpool.start(Path.of(directory).toAbsolutePath())
        super.start()
    }

    override fun stop() {
        spool?.flush()
        super.stop()
    }

    override fun append(event: ILoggingEvent) {
        val target = spool ?: return
        if (event.level.toInt() < Level.INFO_INT) return
        if (event.loggerName.startsWith(OWN_PACKAGE)) return
        val text = StringBuilder(event.formattedMessage)
        event.throwableProxy?.let { text.append('\n').append(ThrowableProxyUtil.asString(it)) }
        val message = AppLogMasker.mask(text.toString().trimEnd()).take(MAX_MESSAGE)
        target.add(
            buildString {
                append("{\"time\":\"").append(DateTimeFormatter.ISO_INSTANT.format(event.instant)).append('"')
                append(",\"level\":\"").append(if (event.level.toInt() >= Level.ERROR_INT) "ERROR" else if (event.level.toInt() >= Level.WARN_INT) "WARN" else "INFO").append('"')
                append(",\"logger\":\"").append(escape(event.loggerName.take(200))).append('"')
                append(",\"message\":\"").append(escape(message)).append('"')
                append(",\"v\":\"").append(escape(AppVersion.current)).append("\"}")
            },
        )
    }

    companion object {
        private const val OWN_PACKAGE = "nl.vdzon.hkhworkflow.adminlog"
        const val MAX_MESSAGE = 8000

        /** Tekst als inhoud van een json-string (zonder aanhalingstekens eromheen). */
        internal fun escape(text: String): String = buildString(text.length + 16) {
            for (c in text) when {
                c == '"' -> append("\\\"")
                c == '\\' -> append("\\\\")
                c == '\n' -> append("\\n")
                c == '\r' -> append("\\r")
                c == '\t' -> append("\\t")
                c < ' ' -> append("\\u%04x".format(c.code))
                else -> append(c)
            }
        }
    }
}

/** Haalt wachtwoorden en sleutels uit een stuk logtekst: wat achter een naam als `password=`, `wachtwoord:` of `Authorization: Bearer` staat. */
object AppLogMasker {
    private val SECRET = Regex(
        "(?i)(password|passwd|pswd|wachtwoord|secret|token|api[-_]?key|authorization)([\"']?\\s*[:=]\\s*[\"']?(?:bearer\\s+)?)([^\\s\"',;&)\\]}]+)",
    )

    fun mask(text: String): String = SECRET.replace(text) { "${it.groupValues[1]}${it.groupValues[2]}***" }
}
