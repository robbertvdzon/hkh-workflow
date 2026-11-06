package nl.vdzon.hkhworkflow.update.internal

import java.nio.file.Path
import java.time.Instant

/** Een build die op GitHub als Release klaarstaat: tag `build-<nummer>` met `hkh-workflow.jar` en `hkh-workflow.jar.sha256`. */
data class Release(val number: Int, val tag: String, val publishedAt: Instant?, val jarUrl: String, val shaUrl: String?)

/** Waar nieuwe versies vandaan komen. Los van de rest, zodat de tests een nep-bron kunnen gebruiken. */
interface ReleaseSource {
    /** De nieuwste build, of null als er geen is. Gooit een fout als de bron niet bereikbaar is. */
    fun latest(): Release?

    /** De tekst op [url] (het sha256-bestand). */
    fun text(url: String): String

    /**
     * Downloadt [url] naar [target] en geeft de sha256 (hex, kleine letters) van wat is binnengekomen. [progress] krijgt af en toe
     * (bytes binnen, totaal of -1 als dat niet bekend is).
     */
    fun download(url: String, target: Path, progress: (Long, Long) -> Unit = { _, _ -> }): String
}
