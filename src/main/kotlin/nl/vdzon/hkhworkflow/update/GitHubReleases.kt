package nl.vdzon.hkhworkflow.update

import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.time.Duration
import java.time.Instant
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import tools.jackson.databind.ObjectMapper

/**
 * De Releases van de GitHub-repository (publiek, dus zonder token). De GitHub Action zet bij elke push naar `main` een Release `build-<nummer>`
 * met de jar en een sha256 klaar (`.github/workflows/build.yml`). Het nieuwste buildnummer wint, niet de nieuwste aanmaaktijd: twee bouwsels die
 * kort na elkaar klaarkomen kunnen in de verkeerde volgorde worden gepubliceerd.
 */
@Component
class GitHubReleases(
    private val mapper: ObjectMapper,
    @Value("\${hkh.workflow.update-repo:robbertvdzon/hkh-workflow}") private val repo: String,
    @Value("\${hkh.workflow.update-api:https://api.github.com}") private val api: String,
) : ReleaseSource {
    private val client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).connectTimeout(Duration.ofSeconds(10)).build()

    private fun request(url: String, accept: String) =
        HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofMinutes(5)).header("User-Agent", "hkh-workflow-updater").header("Accept", accept).GET().build()

    override fun latest(): Release? {
        val response = client.send(request("${api.trimEnd('/')}/repos/$repo/releases?per_page=50", "application/vnd.github+json"), HttpResponse.BodyHandlers.ofString())
        check(response.statusCode() == 200) { "GitHub antwoordt met ${response.statusCode()}" }
        return parse(response.body())
    }

    internal fun parse(json: String): Release? =
        mapper.readTree(json).mapNotNull { node ->
            if (node.path("draft").asBoolean(false) || node.path("prerelease").asBoolean(false)) return@mapNotNull null
            val tag = node.path("tag_name").asString("")
            val number = TAG.matchEntire(tag)?.groupValues?.get(1)?.toIntOrNull() ?: return@mapNotNull null
            val assets = node.path("assets").associate { it.path("name").asString("") to it.path("browser_download_url").asString("") }
            val jar = assets[JAR]?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            Release(number, tag, node.path("published_at").asString("").let { runCatching { Instant.parse(it) }.getOrNull() }, jar, assets[SHA]?.takeIf { it.isNotBlank() })
        }.maxByOrNull { it.number }

    override fun text(url: String): String {
        val response = client.send(request(url, "*/*"), HttpResponse.BodyHandlers.ofString())
        check(response.statusCode() == 200) { "Ophalen van $url mislukt (${response.statusCode()})" }
        return response.body()
    }

    override fun download(url: String, target: Path, progress: (Long, Long) -> Unit): String {
        val response = client.send(request(url, "application/octet-stream"), HttpResponse.BodyHandlers.ofInputStream())
        check(response.statusCode() == 200) { "Downloaden mislukt (${response.statusCode()})" }
        val total = response.headers().firstValueAsLong("content-length").orElse(-1L)
        val digest = MessageDigest.getInstance("SHA-256")
        var done = 0L
        var reported = 0L
        response.body().use { body ->
            Files.newOutputStream(target).use { out ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val n = body.read(buffer)
                    if (n < 0) break
                    out.write(buffer, 0, n)
                    digest.update(buffer, 0, n)
                    done += n
                    if (done - reported >= 256 * 1024) { reported = done; progress(done, total) }
                }
            }
        }
        progress(done, total)
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    companion object {
        const val JAR = "hkh-workflow.jar"
        const val SHA = "hkh-workflow.jar.sha256"
        private val TAG = Regex("build-(\\d+)")
    }
}
