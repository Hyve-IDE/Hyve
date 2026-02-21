package com.hyve.ui.schema.tier1

import com.hyve.ui.core.id.ElementType
import com.hyve.ui.core.id.PropertyName
import com.hyve.ui.schema.*

/**
 * Schema for Label element - displays text content.
 *
 * Label elements are used to display static or dynamic text.
 * They support various text styling options including font, size, color,
 * alignment, and text effects.
 *
 * Common properties:
 * - Text: The text content to display
 * - FontSize: Size of the text
 * - Color: Text color
 * - TextAlign: Horizontal alignment
 * - RenderBold: Bold text rendering
 */
object LabelSchema {
    fun create(): ElementSchema = ElementSchema(
        type = ElementType("Label"),
        category = ElementCategory.TEXT,
        description = "Text display element for showing static or dynamic text content",
        canHaveChildren = false,
        properties = listOf(
            // Content
            PropertySchema(
                name = PropertyName("Text"),
                type = PropertyType.TEXT,
                required = true,
                description = "The text content to display"
            ),

            // Font properties
            PropertySchema(
                name = PropertyName("Font"),
                type = PropertyType.FONT_PATH,
                required = false,
                description = "Path to custom font file"
            ),
            CommonPropertySchemas.fontSize("Size of the text in points"),
            PropertySchema(
                name = PropertyName("RenderBold"),
                type = PropertyType.BOOLEAN,
                required = false,
                description = "Render text in bold"
            ),
            PropertySchema(
                name = PropertyName("RenderItalic"),
                type = PropertyType.BOOLEAN,
                required = false,
                description = "Render text in italics"
            ),
            PropertySchema(
                name = PropertyName("RenderUnderline"),
                type = PropertyType.BOOLEAN,
                required = false,
                description = "Render text with underline"
            ),

            // Color and effects
            CommonPropertySchemas.color(),
            PropertySchema(
                name = PropertyName("OutlineColor"),
                type = PropertyType.COLOR,
                required = false,
                description = "Text outline/shadow color"
            ),
            PropertySchema(
                name = PropertyName("OutlineWidth"),
                type = PropertyType.NUMBER,
                required = false,
                description = "Width of text outline in pixels"
            ),

            // Alignment and layout
            PropertySchema(
                name = PropertyName("TextAlign"),
                type = PropertyType.TEXT,
                required = false,
                description = "Horizontal text alignment: Left, Center, Right"
            ),
            PropertySchema(
                name = PropertyName("VerticalAlign"),
                type = PropertyType.TEXT,
                required = false,
                description = "Vertical text alignment: Top, Middle, Bottom"
            ),
            PropertySchema(
                name = PropertyName("WrapText"),
                type = PropertyType.BOOLEAN,
                required = false,
                description = "Whether to wrap text to multiple lines"
            ),
            PropertySchema(
                name = PropertyName("MaxWidth"),
                type = PropertyType.NUMBER,
                required = false,
                description = "Maximum width before text wrapping"
            ),

            // Position
            CommonPropertySchemas.anchor("Position and size of the label"),

            // Visibility
            CommonPropertySchemas.visible(),

            // Style reference
            CommonPropertySchemas.style()
        ),
        examples = listOf(
            """
            Label {
                Text: "Hello World";
                FontSize: 16;
                Color: #ffffff;
                RenderBold: true;
            }
            """.trimIndent(),
            """
            Label #Title {
                Text: "Game Title";
                FontSize: 32;
                Color: #ffcc00;
                TextAlign: Center;
                Anchor: (Left: 0, Top: 20, Width: 100%, Height: 40);
            }
            """.trimIndent()
        )
    )
}
