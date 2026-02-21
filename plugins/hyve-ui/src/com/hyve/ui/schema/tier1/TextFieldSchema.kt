package com.hyve.ui.schema.tier1

import com.hyve.ui.core.id.ElementType
import com.hyve.ui.core.id.PropertyName
import com.hyve.ui.schema.*

/**
 * Schema for TextField element - text input field.
 *
 * TextField elements allow users to enter text. They support placeholders,
 * validation, character limits, and various input types (text, password, etc.).
 *
 * Common properties:
 * - Text: Current text value
 * - Placeholder: Hint text when empty
 * - MaxLength: Maximum character count
 * - OnChange: Event triggered when text changes
 */
object TextFieldSchema {
    fun create(): ElementSchema = ElementSchema(
        type = ElementType("TextField"),
        category = ElementCategory.INPUT,
        description = "Text input field for user text entry",
        canHaveChildren = false,
        properties = listOf(
            // Content
            PropertySchema(
                name = PropertyName("Text"),
                type = PropertyType.TEXT,
                required = false,
                description = "Current text value in the field"
            ),
            PropertySchema(
                name = PropertyName("Placeholder"),
                type = PropertyType.TEXT,
                required = false,
                description = "Hint text displayed when field is empty"
            ),

            // Input constraints
            PropertySchema(
                name = PropertyName("MaxLength"),
                type = PropertyType.NUMBER,
                required = false,
                description = "Maximum number of characters allowed"
            ),
            PropertySchema(
                name = PropertyName("InputType"),
                type = PropertyType.TEXT,
                required = false,
                description = "Type of input: Text, Password, Number, Email"
            ),
            PropertySchema(
                name = PropertyName("ReadOnly"),
                type = PropertyType.BOOLEAN,
                required = false,
                description = "Whether the field is read-only"
            ),

            // Event handling
            PropertySchema(
                name = PropertyName("OnChange"),
                type = PropertyType.TEXT,
                required = false,
                description = "Event triggered when text changes"
            ),
            PropertySchema(
                name = PropertyName("OnSubmit"),
                type = PropertyType.TEXT,
                required = false,
                description = "Event triggered when Enter key is pressed"
            ),
            PropertySchema(
                name = PropertyName("OnFocus"),
                type = PropertyType.TEXT,
                required = false,
                description = "Event triggered when field gains focus"
            ),
            PropertySchema(
                name = PropertyName("OnBlur"),
                type = PropertyType.TEXT,
                required = false,
                description = "Event triggered when field loses focus"
            ),

            // Visual styling
            CommonPropertySchemas.fontSize("Size of the text"),
            CommonPropertySchemas.color(),
            PropertySchema(
                name = PropertyName("PlaceholderColor"),
                type = PropertyType.COLOR,
                required = false,
                description = "Color of placeholder text"
            ),
            CommonPropertySchemas.background("Background color of the field"),
            CommonPropertySchemas.borderColor(),
            CommonPropertySchemas.borderWidth(),
            PropertySchema(
                name = PropertyName("CornerRadius"),
                type = PropertyType.NUMBER,
                required = false,
                description = "Corner radius for rounded corners"
            ),

            // Focus state
            PropertySchema(
                name = PropertyName("BorderColorFocus"),
                type = PropertyType.COLOR,
                required = false,
                description = "Border color when focused"
            ),

            // Layout
            CommonPropertySchemas.anchor("Position and size of the text field"),
            CommonPropertySchemas.padding("Inner padding around text content"),

            // State
            CommonPropertySchemas.enabled("Whether the field is editable"),
            CommonPropertySchemas.visible(),

            // Style reference
            CommonPropertySchemas.style()
        ),
        examples = listOf(
            """
            TextField #UsernameInput {
                Placeholder: "Enter username";
                MaxLength: 20;
                Anchor: (Left: 10, Top: 50, Width: 200, Height: 30);
                Background: #ffffff;
                BorderColor: #cccccc;
                BorderWidth: 1;
            }
            """.trimIndent(),
            """
            TextField #PasswordInput {
                InputType: Password;
                Placeholder: "Enter password";
                MaxLength: 50;
                Anchor: (Left: 10, Top: 90, Width: 200, Height: 30);
                OnSubmit: "login";
            }
            """.trimIndent()
        )
    )
}
