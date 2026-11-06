package nl.vdzon.hkhworkflow.restapi.internal

import io.swagger.v3.oas.models.parameters.Parameter
import org.springdoc.core.customizers.OpenApiCustomizer
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * De controllers lezen de collectie niet zelf (dat doet `CurrentContextFilter`), dus de beschrijving van de API kent de parameter `{collection}`
 * niet vanzelf. Zonder dit zou de frontend er geen type voor krijgen.
 */
@Configuration
class OpenApiConfig {
    @Bean
    fun collectionParameter() = OpenApiCustomizer { api ->
        api.paths.orEmpty().filterKeys { "{collection}" in it }.values.forEach { path ->
            path.readOperations().forEach { operation ->
                val parameters = operation.parameters.orEmpty()
                if (parameters.none { it.name == "collection" }) {
                    operation.parameters = parameters + Parameter().name("collection").`in`("path").required(true)
                        .schema(io.swagger.v3.oas.models.media.StringSchema())
                }
            }
        }
    }
}
