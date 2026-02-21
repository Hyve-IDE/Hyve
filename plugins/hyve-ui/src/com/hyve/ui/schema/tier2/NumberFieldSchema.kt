package com.hyve.ui.schema.tier2

import com.hyve.ui.core.id.ElementType
import com.hyve.ui.core.id.PropertyName
import com.hyve.ui.schema.ElementCategory
import com.hyve.ui.schema.ElementSchema
import com.hyve.ui.schema.PropertySchema
import com.hyve.ui.schema.CommonPropertySchemas
import com.hyve.ui.schema.PropertyType

/**
 * Schema for NumberField element (numeric input with validation).
 *
 * NumberField is a specialized text input that only accepts numeric values,
 * with formatting, validation, and range constraints. It provides built-in
 * handling for integers and floating-point numbers.
 *
 * Found in Hytale .ui files at:
 * - Client/Data/Game/Interface/InGame/Pages/Inventory/BuilderTools/Input/Number.ui
 *
 * Key Properties:
 * - Value: Current numeric value
 * - MinValue: Minimum allowed value
 * - MaxValue: Maximum allowed value
 * - Format: Number formatting options
 * - Step: Increment/decrement step size
 */
object NumberFieldSchema {
    fun create(): ElementSchema {
        return ElementSchema(
            type = ElementType("NumberField"),
            category = ElementCategory.INPUT,
            description = "Numeric input field with formatting, validation, and range constraints",
            canHaveChildren = false,
            properties = listOf(
                // Value
                PropertySchema(
                    name = PropertyName("Value"),
                    type = PropertyType.NUMBER,
                    required = false,
                    description = "Current numeric value"
                ),
                PropertySchema(
                    name = PropertyName("PlaceholderText"),
                    type = PropertyType.TEXT,
                    required = false,
                    description = "Hint text displayed when field is empty"
                ),

                // Range Constraints
                PropertySchema(
                    name = PropertyName("MinValue"),
                    type = PropertyType.NUMBER,
                    required = false,
                    description = "Minimum allowed value"
                ),
                PropertySchema(
                    name = PropertyName("MaxValue"),
                    type = PropertyType.NUMBER,
                    required = false,
                    description = "Maximum allowed value"
                ),
                PropertySchema(
                    name = PropertyName("Step"),
                    type = PropertyType.NUMBER,
                    required = false,
                    description = "Step size for increment/decrement"
                ),

                // Formatting
                PropertySchema(
                    name = PropertyName("Format"),
                    type = PropertyType.ANY,
                    required = false,
                    description = "Number formatting options (MinValue, MaxValue, DecimalPlaces, etc.)"
                ),
                PropertySchema(
                    name = PropertyName("DecimalPlaces"),
                    type = PropertyType.NUMBER,
                    required = false,
                    description = "Number of decimal places to display"
                ),
                PropertySchema(
                    name = PropertyName("ShowThousandsSeparator"),
                    type = PropertyType.BOOLEAN,
                    required = false,
                    description = "Whether to show thousands separator (e.g., 1,000)"
                ),
                PropertySchema(
                    name = PropertyName("Prefix"),
                    type = PropertyType.TEXT,
                    required = false,
                    description = "Text to display before the number (e.g., '$')"
                ),
                PropertySchema(
                    name = PropertyName("Suffix"),
                    type = PropertyType.TEXT,
                    required = false,
                    description = "Text to display after the number (e.g., 'kg')"
                ),

                // Size and Layout
                CommonPropertySchemas.anchor("Position and size anchor"),
                CommonPropertySchemas.padding("Padding around the element"),

                // Behavior
                PropertySchema(
                    name = PropertyName("AllowNegative"),
                    type = PropertyType.BOOLEAN,
                    required = false,
                    description = "Whether negative numbers are allowed"
                ),
                PropertySchema(
                    name = PropertyName("AllowDecimal"),
                    type = PropertyType.BOOLEAN,
                    required = false,
                    description = "Whether decimal numbers are allowed"
                ),
                PropertySchema(
                    name = PropertyName("ReadOnly"),
                    type = PropertyType.BOOLEAN,
                    required = false,
                    description = "Whether the field is read-only"
                ),
                CommonPropertySchemas.enabled("Whether the field is enabled for input"),
                PropertySchema(
                    name = PropertyName("WrapAround"),
                    type = PropertyType.BOOLEAN,
                    required = false,
                    description = "Whether to wrap from max to min (and vice versa)"
                ),

                // Styling
                CommonPropertySchemas.style("Text styling (FontSize, TextColor, etc.)"),
                PropertySchema(
                    name = PropertyName("PlaceholderStyle"),
                    type = PropertyType.ANY,
                    required = false,
                    description = "Styling for placeholder text"
                ),
                CommonPropertySchemas.background("Background color or texture"),

                // Increment/Decrement Buttons
                PropertySchema(
                    name = PropertyName("ShowButtons"),
                    type = PropertyType.BOOLEAN,
                    required = false,
                    description = "Whether to show increment/decrement buttons"
                ),
                PropertySchema(
                    name = PropertyName("ButtonStyle"),
                    type = PropertyType.ANY,
                    required = false,
                    description = "Styling for increment/decrement buttons"
                ),

                // Events
                PropertySchema(
                    name = PropertyName("OnChange"),
                    type = PropertyType.TEXT,
                    required = false,
                    description = "Callback when value changes"
                ),
                PropertySchema(
                    name = PropertyName("OnFocus"),
                    type = PropertyType.TEXT,
                    required = false,
                    description = "Callback when field gains focus"
                ),
                PropertySchema(
                    name = PropertyName("OnBlur"),
                    type = PropertyType.TEXT,
                    required = false,
                    description = "Callback when field loses focus"
                ),
                PropertySchema(
                    name = PropertyName("OnIncrement"),
                    type = PropertyType.TEXT,
                    required = false,
                    description = "Callback when value is incremented"
                ),
                PropertySchema(
                    name = PropertyName("OnDecrement"),
                    type = PropertyType.TEXT,
                    required = false,
                    description = "Callback when value is decremented"
                ),

                // Validation
                PropertySchema(
                    name = PropertyName("ValidationMessage"),
                    type = PropertyType.TEXT,
                    required = false,
                    description = "Error message for invalid input"
                ),
                PropertySchema(
                    name = PropertyName("ShowValidationError"),
                    type = PropertyType.BOOLEAN,
                    required = false,
                    description = "Whether to show validation errors"
                ),

                // Common Properties
                CommonPropertySchemas.visible()
            )
        )
    }
}
