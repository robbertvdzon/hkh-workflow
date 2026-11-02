package nl.vdzon.hkhworkflow.update

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

    /** Downloadt [url] naar [target] en geeft de sha256 (hex, kleine letters) van wat is binnengekomen. */
    fun download(url: String, target: Path): String
}
