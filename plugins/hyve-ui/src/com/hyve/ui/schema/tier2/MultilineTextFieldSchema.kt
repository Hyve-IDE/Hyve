package com.hyve.ui.schema.tier2

import com.hyve.ui.core.id.ElementType
import com.hyve.ui.core.id.PropertyName
import com.hyve.ui.schema.ElementCategory
import com.hyve.ui.schema.ElementSchema
import com.hyve.ui.schema.PropertySchema
import com.hyve.ui.schema.CommonPropertySchemas
import com.hyve.ui.schema.PropertyType

/**
 * Schema for MultilineTextField element (multi-line text input).
 *
 * MultilineTextField is a text input element that supports multiple lines of text entry,
 * automatic line wrapping, and scrolling for longer content. It's distinct from TextField
 * which only supports single-line input.
 *
 * Found in Hytale .ui files at:
 * - Client/Data/Game/Interface/InGame/Pages/Inventory/BuilderTools/Input/MultilineText.ui
 *
 * Key Properties:
 * - Text: Current content
 * - PlaceholderText: Hint text when empty
 * - MaxLength: Character limit
 * - MaxVisibleLines: Number of visible lines before scrolling
 * - AutoGrow: Whether to expand vertically to fit content
 * - ContentPadding: Internal spacing
 */
object MultilineTextFieldSchema {
    fun create(): ElementSchema {
        return ElementSchema(
            type = ElementType("MultilineTextField"),
            category = ElementCategory.INPUT,
            description = "Multi-line text input element with scrolling and line wrapping support",
            canHaveChildren = false,
            properties = listOf(
                // Content
                PropertySchema(
                    name = PropertyName("Text"),
                    type = PropertyType.TEXT,
                    required = false,
                    description = "Current text content"
                ),
                PropertySchema(
                    name = PropertyName("PlaceholderText"),
                    type = PropertyType.TEXT,
                    required = false,
                    description = "Hint text displayed when field is empty"
                ),

                // Size and Layout
                CommonPropertySchemas.anchor("Position and size anchor"),
                CommonPropertySchemas.padding("Outer padding around the element"),
                PropertySchema(
                    name = PropertyName("ContentPadding"),
                    type = PropertyType.ANY,
                    required = false,
                    description = "Inner padding for text content"
                ),

                // Text Constraints
                PropertySchema(
                    name = PropertyName("MaxLength"),
                    type = PropertyType.NUMBER,
                    required = false,
                    description = "Maximum number of characters allowed"
                ),
                PropertySchema(
                    name = PropertyName("MaxVisibleLines"),
                    type = PropertyType.NUMBER,
                    required = false,
                    description = "Maximum number of lines visible before scrolling"
                ),

                // Behavior
                PropertySchema(
                    name = PropertyName("AutoGrow"),
                    type = PropertyType.BOOLEAN,
                    required = false,
                    description = "Whether the field should automatically expand to fit content"
                ),
                PropertySchema(
                    name = PropertyName("ReadOnly"),
                    type = PropertyType.BOOLEAN,
                    required = false,
                    description = "Whether the field is read-only"
                ),
                CommonPropertySchemas.enabled("Whether the field is enabled for input"),

                // Styling
                CommonPropertySchemas.style("Text styling (FontSize, TextColor, etc.)"),
                PropertySchema(
                    name = PropertyName("PlaceholderStyle"),
                    type = PropertyType.ANY,
                    required = false,
                    description = "Styling for placeholder text"
                ),
                CommonPropertySchemas.background("Background color or texture"),

                // Scrolling
                PropertySchema(
                    name = PropertyName("ShowScrollbar"),
                    type = PropertyType.BOOLEAN,
                    required = false,
                    description = "Whether to show scrollbar when content exceeds visible area"
                ),
                PropertySchema(
                    name = PropertyName("ScrollbarStyle"),
                    type = PropertyType.ANY,
                    required = false,
                    description = "Scrollbar styling properties"
                ),

                // Events
                PropertySchema(
                    name = PropertyName("OnChange"),
                    type = PropertyType.TEXT,
                    required = false,
                    description = "Callback when text content changes"
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

                // Common Properties
                CommonPropertySchemas.visible()
            )
        )
    }
}
