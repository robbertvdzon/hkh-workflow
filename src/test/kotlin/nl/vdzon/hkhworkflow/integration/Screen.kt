package nl.vdzon.hkhworkflow.integration

import com.microsoft.playwright.Browser as PlaywrightBrowser
import com.microsoft.playwright.BrowserContext
import com.microsoft.playwright.Locator
import com.microsoft.playwright.Page as PlaywrightPage
import com.microsoft.playwright.Playwright
import com.microsoft.playwright.options.AriaRole
import java.nio.file.Files
import java.nio.file.Path

/** De frontend in een echte browser, zoals iemand hem ziet. Elk scenario begint met een schone browser, dus zonder cookies. */
class Screen(private val baseUrl: String) {
    private val context: BrowserContext = browser.newContext(PlaywrightBrowser.NewContextOptions().setLocale("nl-NL"))
    val page: PlaywrightPage = context.newPage()

    /** Wat er in de browser fout ging: dat hoort er nooit te zijn, ook niet als het scherm er goed uitziet. */
    val errors = mutableListOf<String>()

    init {
        page.setDefaultTimeout(10_000.0)
        page.onPageError { errors += "JavaScript: $it" }
        // De vragen "Weet je het zeker?" zijn in deze scenario's altijd met ja beantwoord.
        page.onDialog { it.accept() }
    }

    fun open(path: String) { page.navigate(baseUrl + path) }

    fun reload() { page.reload() }

    fun fill(label: String, value: String) = page.getByLabel(label, PlaywrightPage.GetByLabelOptions().setExact(true)).fill(value)

    /** Een knop of link met precies deze tekst. */
    fun action(name: String): Locator = button(name).or(page.getByRole(AriaRole.LINK, PlaywrightPage.GetByRoleOptions().setName(name).setExact(true))).first()

    fun button(name: String): Locator = page.getByRole(AriaRole.BUTTON, PlaywrightPage.GetByRoleOptions().setName(name).setExact(true))

    fun link(name: String): Locator = page.getByRole(AriaRole.LINK, PlaywrightPage.GetByRoleOptions().setName(name).setExact(true))

    /** Een grote keuze op het scherm: een link waarvan de tekst met deze naam begint. */
    fun choice(name: String): Locator = page.getByRole(AriaRole.LINK, PlaywrightPage.GetByRoleOptions().setName(java.util.regex.Pattern.compile("^$name")))

    /** De rij van een tabel waarin deze tekst staat. */
    fun row(text: String): Locator = page.getByRole(AriaRole.ROW).filter(Locator.FilterOptions().setHasText(text))

    fun heading(name: String): Locator = page.getByRole(AriaRole.HEADING, PlaywrightPage.GetByRoleOptions().setName(name))

    fun text(text: String): Locator = page.getByText(text)

    val path: String get() = java.net.URI(page.url()).path

    fun screenshot(name: String) {
        val target = Path.of("target/screenshots", name.replace(Regex("[^A-Za-z0-9-]+"), "-") + ".png")
        Files.createDirectories(target.parent)
        page.screenshot(PlaywrightPage.ScreenshotOptions().setPath(target).setFullPage(true))
    }

    fun close() = context.close()

    companion object {
        private val playwright: Playwright by lazy { Playwright.create() }
        private val browser: PlaywrightBrowser by lazy {
            playwright.chromium().launch().also { launched -> Runtime.getRuntime().addShutdownHook(Thread { launched.close(); playwright.close() }) }
        }
    }
}
