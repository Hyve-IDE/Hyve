package com.hyve.ui.schema

import com.hyve.ui.core.domain.elements.UIElement
import com.hyve.ui.core.id.ElementType
import com.hyve.ui.core.id.PropertyName

/**
 * Schema definition for a UI element type.
 *
 * Defines:
 * - Element type name (Group, Label, Button, etc.)
 * - Allowed properties with their schemas
 * - Whether the element can contain children
 * - Category for organization in editor
 *
 * Example:
 * ```kotlin
 * ElementSchema(
 *     type = ElementType("Button"),
 *     category = ElementCategory.INTERACTIVE,
 *     description = "Interactive button element",
 *     canHaveChildren = false,
 *     properties = listOf(
 *         PropertySchema(PropertyName("Text"), PropertyType.TEXT, required = true),
 *         PropertySchema(PropertyName("OnClick"), PropertyType.TEXT, required = false)
 *     )
 * )
 * ```
 */
data class ElementSchema(
    val type: ElementType,
    val category: ElementCategory,
    val description: String,
    val canHaveChildren: Boolean,
    val properties: List<PropertySchema>,
    val examples: List<String> = emptyList()
) {
    /**
     * Get property schema by name
     */
    fun getPropertySchema(name: PropertyName): PropertySchema? =
        properties.firstOrNull { it.name == name }

    /**
     * Get property schema by string name (convenience)
     */
    fun getPropertySchema(name: String): PropertySchema? =
        getPropertySchema(PropertyName(name))

    /**
     * Check if a property is defined in this schema
     */
    fun hasProperty(name: PropertyName): Boolean =
        properties.any { it.name == name }

    /**
     * Get all required properties
     */
    fun getRequiredProperties(): List<PropertySchema> =
        properties.filter { it.required }

    /**
     * Get all optional properties
     */
    fun getOptionalProperties(): List<PropertySchema> =
        properties.filter { !it.required }

    /**
     * Validate a UIElement against this schema
     */
    fun validate(element: UIElement): ElementValidationResult {
        val propertyResults = mutableMapOf<PropertyName, ValidationResult>()
        val unknownProperties = mutableListOf<PropertyName>()

        // Validate type matches
        if (element.type != type) {
            return ElementValidationResult(
                elementType = element.type,
                propertyValidation = ValidationResults.valid(),
                unknownProperties = emptyList(),
                errors = listOf("Element type '${element.type.value}' does not match schema type '${type.value}'")
            )
        }

        // Validate all defined properties
        for (propertySchema in properties) {
            val value = element.getProperty(propertySchema.name)
            val result = propertySchema.validate(value)
            propertyResults[propertySchema.name] = result
        }

        // Check for unknown properties
        for (entry in element.properties.entries()) {
            val name = entry.key
            if (!hasProperty(name)) {
                unknownProperties.add(name)
            }
        }

        // Validate children if present
        val childErrors = mutableListOf<String>()
        if (element.children.isNotEmpty() && !canHaveChildren) {
            childErrors.add("Element type '${type.value}' cannot have children")
        }

        // Collect all errors
        val allErrors = mutableListOf<String>()
        allErrors.addAll(propertyResults.values.mapNotNull { it.getErrorMessage() })
        allErrors.addAll(childErrors)

        return ElementValidationResult(
            elementType = element.type,
            propertyValidation = ValidationResults(propertyResults),
            unknownProperties = unknownProperties,
            errors = allErrors,
            warnings = if (unknownProperties.isNotEmpty()) {
                listOf("Unknown properties: ${unknownProperties.joinToString { it.value }}")
            } else emptyList()
        )
    }
}

/**
 * Element categories for organizing the toolbox in the visual editor
 */
enum class ElementCategory(val displayName: String) {
    CONTAINER("Containers"),
    TEXT("Text"),
    INTERACTIVE("Interactive"),
    INPUT("Input"),
    MEDIA("Media"),
    LAYOUT("Layout"),
    ADVANCED("Advanced"),
    OTHER("Other");

    override fun toString(): String = displayName
}

/**
 * Result of validating a UIElement against its schema
 */
data class ElementValidationResult(
    val elementType: ElementType,
    val propertyValidation: ValidationResults,
    val unknownProperties: List<PropertyName>,
    val errors: List<String> = emptyList(),
    val warnings: List<String> = emptyList()
) {
    fun isValid(): Boolean = errors.isEmpty() && propertyValidation.isValid()
    fun hasErrors(): Boolean = errors.isNotEmpty() || propertyValidation.hasErrors()
    fun hasWarnings(): Boolean = warnings.isNotEmpty() || unknownProperties.isNotEmpty()

    fun getAllErrors(): List<String> {
        val allErrors = mutableListOf<String>()
        allErrors.addAll(errors)
        allErrors.addAll(propertyValidation.getErrors().values)
        return allErrors
    }

    fun getAllWarnings(): List<String> {
        val allWarnings = mutableListOf<String>()
        allWarnings.addAll(warnings)
        allWarnings.addAll(propertyValidation.getWarnings().values)
        return allWarnings
    }
}
