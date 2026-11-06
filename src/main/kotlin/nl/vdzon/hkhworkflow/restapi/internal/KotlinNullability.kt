package nl.vdzon.hkhworkflow.restapi.internal

import com.fasterxml.jackson.databind.JavaType

import io.swagger.v3.core.converter.AnnotatedType
import io.swagger.v3.core.converter.ModelConverter
import io.swagger.v3.core.converter.ModelConverterContext
import io.swagger.v3.oas.models.media.JsonSchema
import io.swagger.v3.oas.models.media.Schema
import kotlin.reflect.KClass
import kotlin.reflect.full.primaryConstructor
import org.springframework.stereotype.Component


/**
 * Zet in de beschrijving van de API wat Kotlin al weet: `String` is er altijd, `String?` kan null zijn. Zonder dit is voor de frontend elk veld
 * optioneel en niets ooit null, en dan zeggen de types van de frontend niet meer wat de API echt doet.
 */
@Component
class KotlinNullability : ModelConverter {
    override fun resolve(type: AnnotatedType, context: ModelConverterContext, chain: MutableIterator<ModelConverter>): Schema<*>? {
        val schema = if (chain.hasNext()) chain.next().resolve(type, context, chain) else return null
        val model = schema?.takeIf { it.properties != null } ?: schema?.`$ref`?.substringAfterLast('/')?.let { context.definedModels[it] } ?: return schema
        rawClass(type)?.takeIf { it.isData }?.primaryConstructor?.parameters?.forEach { parameter ->
            val name = parameter.name ?: return@forEach
            val property = model.properties?.get(name) ?: return@forEach
            if (parameter.type.isMarkedNullable && "null" !in property.types.orEmpty()) model.properties[name] = nullable(property)
            if (!parameter.isOptional && name !in model.required.orEmpty()) model.addRequiredItem(name)
        }
        return schema
    }

    private fun rawClass(type: AnnotatedType): KClass<*>? =
        (type.type as? Class<*> ?: (type.type as? JavaType)?.rawClass)?.takeIf { it.isAnnotationPresent(Metadata::class.java) }?.kotlin

    private fun nullable(property: Schema<*>): Schema<*> =
        if (property.`$ref` != null) JsonSchema().anyOf(listOf(JsonSchema().`$ref`(property.`$ref`), JsonSchema().types(setOf("null"))))
        else property.apply { types = (types ?: setOfNotNull(type)) + "null" }
}
