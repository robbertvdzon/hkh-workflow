package nl.vdzon.hkhworkflow.items

import jakarta.servlet.http.Cookie
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import nl.vdzon.hkhworkflow.settings.WorkflowProperties
import org.springframework.stereotype.Component
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.PosixFilePermissions
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * "Ingelogd blijven" na een herstart van de app: de naam en het wachtwoord van ZCBS gaan versleuteld (AES-GCM) in een cookie van
 * 30 dagen. De sleutel staat in een bestand naast het instellingenbestand en verlaat de computer niet; zonder dat bestand is het cookie
 * waardeloos. Uitloggen haalt het cookie weg.
 */
@Component
class RememberLogin(properties: WorkflowProperties) {
    private val keyFile: Path = Path.of(properties.settingsFile).toAbsolutePath().resolveSibling("hkh-workflow-inloggen.key")
    private val key: SecretKeySpec by lazy { loadKey() }
    private val random = SecureRandom()

    private fun loadKey(): SecretKeySpec {
        if (Files.isRegularFile(keyFile)) return SecretKeySpec(Base64.getDecoder().decode(Files.readString(keyFile).trim()), "AES")
        val bytes = ByteArray(32).also { random.nextBytes(it) }
        Files.writeString(keyFile, Base64.getEncoder().encodeToString(bytes))
        runCatching { Files.setPosixFilePermissions(keyFile, PosixFilePermissions.fromString("rw-------")) }
        return SecretKeySpec(bytes, "AES")
    }

    internal fun encrypt(name: String, password: String): String {
        val iv = ByteArray(12).also { random.nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, iv)) }
        val data = cipher.doFinal("$name\n$password".toByteArray(StandardCharsets.UTF_8))
        return Base64.getUrlEncoder().withoutPadding().encodeToString(iv + data)
    }

    internal fun decrypt(value: String): Pair<String, String>? = runCatching {
        val all = Base64.getUrlDecoder().decode(value)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, all.copyOfRange(0, 12))) }
        val text = String(cipher.doFinal(all, 12, all.size - 12), StandardCharsets.UTF_8)
        text.substringBefore('\n') to text.substringAfter('\n')
    }.getOrNull()

    fun remember(response: HttpServletResponse, name: String, password: String) {
        response.addCookie(cookie(encrypt(name.trim(), password), MAX_AGE))
    }

    fun forget(response: HttpServletResponse) {
        response.addCookie(cookie("", 0))
    }

    /** De onthouden naam en het wachtwoord uit het cookie van dit verzoek, of null. */
    fun read(request: HttpServletRequest): Pair<String, String>? =
        request.cookies?.firstOrNull { it.name == COOKIE }?.value?.takeIf { it.isNotEmpty() }?.let { decrypt(it) }

    private fun cookie(value: String, maxAge: Int) = Cookie(COOKIE, value).apply {
        isHttpOnly = true
        path = "/"
        this.maxAge = maxAge
        setAttribute("SameSite", "Lax")
    }

    companion object {
        const val COOKIE = "hkh_inloggen"
        private const val MAX_AGE = 30 * 24 * 60 * 60
    }
}
