package nl.vdzon.hkhworkflow.ux.internal

import java.util.concurrent.TimeUnit
import org.springframework.context.annotation.Configuration
import org.springframework.core.io.ClassPathResource
import org.springframework.core.io.Resource
import org.springframework.http.CacheControl
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer
import org.springframework.web.servlet.resource.PathResourceResolver

/**
 * De React-frontend uit de jar (zie docs/frontend.md). Een adres als /collectie/archief is geen bestand maar een scherm van de frontend,
 * dus dan krijgt de browser index.html en kiest de frontend zelf het scherm. Bestanden in assets/ hebben een hash in hun naam en veranderen nooit.
 */
@Configuration
class FrontendConfig : WebMvcConfigurer {

    override fun addViewControllers(registry: ViewControllerRegistry) {
        // Een leeg pad komt niet bij de resolver hieronder aan.
        registry.addViewController("/").setViewName("forward:/index.html")
    }

    override fun addResourceHandlers(registry: ResourceHandlerRegistry) {
        registry.addResourceHandler("/assets/**")
            .addResourceLocations("classpath:/frontend/assets/")
            .setCacheControl(CacheControl.maxAge(365, TimeUnit.DAYS).cachePublic().immutable())
        registry.addResourceHandler("/**")
            .addResourceLocations("classpath:/frontend/")
            .setCacheControl(CacheControl.noCache())
            .resourceChain(false)
            .addResolver(ScreenOrFile())
    }

    private class ScreenOrFile : PathResourceResolver() {
        override fun getResource(resourcePath: String, location: Resource): Resource? {
            val file = location.createRelative(resourcePath)
            if (file.isReadable) return file
            // Iets met een extensie is een bestand dat niet bestaat, en geen scherm.
            if (resourcePath.substringAfterLast('/').contains('.')) return null
            return ClassPathResource("frontend/index.html").takeIf { it.isReadable }
        }
    }
}
