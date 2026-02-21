package com.hyve.ui.schema

import com.hyve.ui.core.domain.properties.PropertyValue
import com.hyve.ui.core.id.PropertyName

/**
 * Schema definition for a single property within a UI element.
 *
 * Defines:
 * - Property name
 * - Expected value type(s)
 * - Whether it's required or optional
 * - Default value (if any)
 * - Validation rules
 *
 * Example:
 * ```kotlin
 * PropertySchema(
 *     name = PropertyName("Text"),
 *     type = PropertyType.TEXT,
 *     required = true,
 *     description = "The text content to display"
 * )
 * ```
 */
data class PropertySchema(
    val name: PropertyName,
    val type: PropertyType,
    val required: Boolean = false,
    val defaultValue: PropertyValue? = null,
    val description: String = "",
    val validator: ((PropertyValue) -> ValidationResult)? = null
) {
    /**
     * Validate a property value against this schema
     */
    fun validate(value: PropertyValue?): ValidationResult {
        // Check if value is missing when required
        if (value == null || value is PropertyValue.Null) {
            return if (required) {
                ValidationResult.Error("Property '${name.value}' is required but not provided")
            } else {
                ValidationResult.Valid
            }
        }

        // Check type compatibility
        val typeCheckResult = type.isCompatible(value)
        if (typeCheckResult is ValidationResult.Error) {
            return typeCheckResult
        }

        // Run custom validator if provided
        validator?.let { validatorFn ->
            val customResult = validatorFn(value)
            if (customResult is ValidationResult.Error) {
                return customResult
            }
        }

        return ValidationResult.Valid
    }
}

/**
 * Supported property types in .ui files
 */
enum class PropertyType(
    val displayName: String,
    val isCompatible: (PropertyValue) -> ValidationResult
) {
    TEXT("Text", { value ->
        when (value) {
            is PropertyValue.Text -> ValidationResult.Valid
            else -> ValidationResult.Error("Expected Text, got ${value::class.simpleName}")
        }
    }),

    NUMBER("Number", { value ->
        when (value) {
            is PropertyValue.Number -> ValidationResult.Valid
            else -> ValidationResult.Error("Expected Number, got ${value::class.simpleName}")
        }
    }),

    PERCENT("Percent", { value ->
        when (value) {
            is PropertyValue.Percent -> ValidationResult.Valid
            is PropertyValue.Number -> ValidationResult.Valid // Numbers can represent percentages too
            else -> ValidationResult.Error("Expected Percent, got ${value::class.simpleName}")
        }
    }),

    BOOLEAN("Boolean", { value ->
        when (value) {
            is PropertyValue.Boolean -> ValidationResult.Valid
            else -> ValidationResult.Error("Expected Boolean, got ${value::class.simpleName}")
        }
    }),

    COLOR("Color", { value ->
        when (value) {
            is PropertyValue.Color -> ValidationResult.Valid
            else -> ValidationResult.Error("Expected Color, got ${value::class.simpleName}")
        }
    }),

    ANCHOR("Anchor", { value ->
        when (value) {
            is PropertyValue.Anchor -> ValidationResult.Valid
            else -> ValidationResult.Error("Expected Anchor, got ${value::class.simpleName}")
        }
    }),

    STYLE("Style", { value ->
        when (value) {
            is PropertyValue.Style -> ValidationResult.Valid
            else -> ValidationResult.Error("Expected Style, got ${value::class.simpleName}")
        }
    }),

    TUPLE("Tuple", { value ->
        when (value) {
            is PropertyValue.Tuple -> ValidationResult.Valid
            else -> ValidationResult.Error("Expected Tuple, got ${value::class.simpleName}")
        }
    }),

    LIST("List", { value ->
        when (value) {
            is PropertyValue.List -> ValidationResult.Valid
            else -> ValidationResult.Error("Expected List, got ${value::class.simpleName}")
        }
    }),

    IMAGE_PATH("Image Path", { value ->
        when (value) {
            is PropertyValue.ImagePath -> ValidationResult.Valid
            is PropertyValue.Text -> ValidationResult.Valid // Accept text as path fallback
            else -> ValidationResult.Error("Expected Image Path, got ${value::class.simpleName}")
        }
    }),

    FONT_PATH("Font Path", { value ->
        when (value) {
            is PropertyValue.FontPath -> ValidationResult.Valid
            is PropertyValue.Text -> ValidationResult.Valid // Accept text as path fallback
            else -> ValidationResult.Error("Expected Font Path, got ${value::class.simpleName}")
        }
    }),

    ANY("Any", { _ ->
        ValidationResult.Valid // Accept any value type
    });

    override fun toString(): String = displayName
}

/**
 * Result of property validation
 */
sealed class ValidationResult {
    data object Valid : ValidationResult()
    data class Error(val message: String) : ValidationResult()
    data class Warning(val message: String) : ValidationResult()

    fun isValid(): Boolean = this is Valid
    fun isError(): Boolean = this is Error
    fun isWarning(): Boolean = this is Warning

    fun getErrorMessage(): String? = when (this) {
        is Error -> message
        else -> null
    }

    fun getWarningMessage(): String? = when (this) {
        is Warning -> message
        else -> null
    }
}

/**
 * Validation result container for multiple properties
 */
data class ValidationResults(
    val results: Map<PropertyName, ValidationResult>
) {
    fun isValid(): Boolean = results.values.all { it.isValid() }
    fun hasErrors(): Boolean = results.values.any { it.isError() }
    fun hasWarnings(): Boolean = results.values.any { it.isWarning() }

    fun getErrors(): Map<PropertyName, String> =
        results.filterValues { it.isError() }
            .mapValues { (it.value as ValidationResult.Error).message }

    fun getWarnings(): Map<PropertyName, String> =
        results.filterValues { it.isWarning() }
            .mapValues { (it.value as ValidationResult.Warning).message }

    companion object {
        fun valid() = ValidationResults(emptyMap())
    }
}
