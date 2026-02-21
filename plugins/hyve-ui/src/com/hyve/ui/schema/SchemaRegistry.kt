package com.hyve.ui.schema

import com.hyve.ui.core.domain.UIDocument
import com.hyve.ui.core.domain.elements.UIElement
import com.hyve.ui.core.id.ElementType

/**
 * Central registry for element schemas.
 *
 * Provides:
 * - Schema lookup by element type
 * - Element validation against schemas
 * - Document validation
 * - Metadata for editor (autocomplete, IntelliSense)
 *
 * This is a singleton registry that contains all hardcoded schemas for Tier 1 elements
 * and can be extended with additional schemas as needed.
 *
 * Usage:
 * ```kotlin
 * val registry = SchemaRegistry.default()
 * val buttonSchema = registry.getSchema(ElementType("Button"))
 * val validationResult = registry.validateElement(myButton)
 * ```
 */
class SchemaRegistry private constructor(
    private val schemas: Map<ElementType, ElementSchema>
) {
    /**
     * Get schema for an element type
     * Returns null if no schema is registered for this type
     */
    fun getSchema(type: ElementType): ElementSchema? =
        schemas[type]

    /**
     * Get schema for an element type by string name (convenience)
     */
    fun getSchema(typeName: String): ElementSchema? =
        getSchema(ElementType(typeName))

    /**
     * Check if a schema is registered for this element type
     */
    fun hasSchema(type: ElementType): Boolean =
        schemas.containsKey(type)

    /**
     * Get all registered element types
     */
    fun getAllTypes(): List<ElementType> =
        schemas.keys.toList()

    /**
     * Get all schemas, optionally filtered by category
     */
    fun getAllSchemas(category: ElementCategory? = null): List<ElementSchema> =
        if (category != null) {
            schemas.values.filter { it.category == category }
        } else {
            schemas.values.toList()
        }

    /**
     * Get schemas grouped by category
     */
    fun getSchemasByCategory(): Map<ElementCategory, List<ElementSchema>> =
        schemas.values.groupBy { it.category }

    /**
     * Validate a UIElement against its schema
     * Returns validation result with errors and warnings
     */
    fun validateElement(element: UIElement): ElementValidationResult {
        val schema = getSchema(element.type)
            ?: return ElementValidationResult(
                elementType = element.type,
                propertyValidation = ValidationResults.valid(),
                unknownProperties = emptyList(),
                errors = emptyList(),
                warnings = listOf("No schema found for element type '${element.type.value}'")
            )

        return schema.validate(element)
    }

    /**
     * Validate an entire UIDocument
     * Returns map of element ID/path to validation results
     */
    fun validateDocument(document: UIDocument): DocumentValidationResult {
        val results = mutableMapOf<String, ElementValidationResult>()

        fun validateElementRecursive(element: UIElement, path: String) {
            val validationResult = validateElement(element)
            results[path] = validationResult

            // Recursively validate children
            element.children.forEachIndexed { index, child ->
                val childPath = if (child.id != null) {
                    "$path/${child.id!!.value}"
                } else {
                    "$path/[${child.type.value}@$index]"
                }
                validateElementRecursive(child, childPath)
            }
        }

        // Validate root element and its descendants
        val rootPath = if (document.root.id != null) {
            document.root.id!!.value
        } else {
            document.root.type.value
        }
        validateElementRecursive(document.root, rootPath)

        return DocumentValidationResult(results)
    }

    companion object {
        private var instance: SchemaRegistry? = null

        /**
         * Get the default schema registry with all Tier 1 element schemas
         */
        fun default(): SchemaRegistry {
            if (instance == null) {
                instance = createDefaultRegistry()
            }
            return instance!!
        }

        /**
         * Reset the registry (useful for testing)
         */
        fun reset() {
            instance = null
        }

        /**
         * Create a custom registry with specific schemas
         */
        fun create(schemas: List<ElementSchema>): SchemaRegistry =
            SchemaRegistry(schemas.associateBy { it.type })

        /**
         * Create the default registry with all tier schemas
         */
        private fun createDefaultRegistry(): SchemaRegistry {
            val schemas = listOf(
                // Tier 1 - Must Have
                com.hyve.ui.schema.tier1.GroupSchema.create(),
                com.hyve.ui.schema.tier1.LabelSchema.create(),
                com.hyve.ui.schema.tier1.ButtonSchema.create(),
                com.hyve.ui.schema.tier1.TextFieldSchema.create(),
                com.hyve.ui.schema.tier1.ImageSchema.create(),

                // Tier 2 - Important
                com.hyve.ui.schema.tier2.SliderSchema.create(),
                com.hyve.ui.schema.tier2.CheckBoxSchema.create(),
                com.hyve.ui.schema.tier2.ScrollViewSchema.create(),
                com.hyve.ui.schema.tier2.DropdownBoxSchema.create(),
                com.hyve.ui.schema.tier2.MultilineTextFieldSchema.create(),
                com.hyve.ui.schema.tier2.NumberFieldSchema.create(),

                // Tier 3 - Nice to Have
                com.hyve.ui.schema.tier3.ProgressBarSchema.create(),
                com.hyve.ui.schema.tier3.TabPanelSchema.create(),
                com.hyve.ui.schema.tier3.TooltipSchema.create()
            )
            return create(schemas)
        }
    }
}

/**
 * Result of validating an entire UIDocument
 */
data class DocumentValidationResult(
    val elementResults: Map<String, ElementValidationResult>
) {
    fun isValid(): Boolean = elementResults.values.all { it.isValid() }
    fun hasErrors(): Boolean = elementResults.values.any { it.hasErrors() }
    fun hasWarnings(): Boolean = elementResults.values.any { it.hasWarnings() }

    fun getAllErrors(): Map<String, List<String>> =
        elementResults.filterValues { it.hasErrors() }
            .mapValues { (_, result) -> result.getAllErrors() }

    fun getAllWarnings(): Map<String, List<String>> =
        elementResults.filterValues { it.hasWarnings() }
            .mapValues { (_, result) -> result.getAllWarnings() }

    fun getErrorCount(): Int = elementResults.values.sumOf { it.getAllErrors().size }
    fun getWarningCount(): Int = elementResults.values.sumOf { it.getAllWarnings().size }
}
