package com.hyve.ui.schema.tier2

import com.hyve.ui.core.id.ElementType
import com.hyve.ui.core.id.PropertyName
import com.hyve.ui.schema.*

/**
 * Schema for CheckBox element - boolean toggle control.
 *
 * CheckBox elements allow users to toggle boolean values on/off.
 * They typically display a box that can be checked or unchecked,
 * often with accompanying label text.
 *
 * Common properties:
 * - Checked: Whether the checkbox is checked
 * - Text: Label text displayed next to checkbox
 * - OnChange: Event triggered when checked state changes
 */
object CheckBoxSchema {
    fun create(): ElementSchema = ElementSchema(
        type = ElementType("CheckBox"),
        category = ElementCategory.INPUT,
        description = "Checkbox control for boolean on/off toggle",
        canHaveChildren = false,
        properties = listOf(
            // State
            PropertySchema(
                name = PropertyName("Checked"),
                type = PropertyType.BOOLEAN,
                required = false,
                description = "Whether the checkbox is checked"
            ),

            // Content
            PropertySchema(
                name = PropertyName("Text"),
                type = PropertyType.TEXT,
                required = false,
                description = "Label text displayed next to checkbox"
            ),

            // Event handling
            PropertySchema(
                name = PropertyName("OnChange"),
                type = PropertyType.TEXT,
                required = false,
                description = "Event triggered when checked state changes"
            ),

            // Visual styling
            PropertySchema(
                name = PropertyName("BoxSize"),
                type = PropertyType.NUMBER,
                required = false,
                description = "Size of the checkbox box"
            ),
            PropertySchema(
                name = PropertyName("BoxColor"),
                type = PropertyType.COLOR,
                required = false,
                description = "Background color of the checkbox box"
            ),
            PropertySchema(
                name = PropertyName("CheckColor"),
                type = PropertyType.COLOR,
                required = false,
                description = "Color of the check mark"
            ),
            CommonPropertySchemas.borderColor("Border color of the checkbox box"),
            CommonPropertySchemas.borderWidth(),

            // Text styling
            CommonPropertySchemas.fontSize("Size of the label text"),
            CommonPropertySchemas.color("Color of the label text"),

            // Layout
            CommonPropertySchemas.anchor("Position and size of the checkbox"),
            CommonPropertySchemas.spacing("Gap between box and label text"),

            // State
            CommonPropertySchemas.enabled("Whether the checkbox is clickable"),
            CommonPropertySchemas.visible(),

            // Style reference
            CommonPropertySchemas.style()
        ),
        examples = listOf(
            """
            CheckBox #AcceptTerms {
                Text: "I accept the terms and conditions";
                Checked: false;
                OnChange: "terms_changed";
                Anchor: (Left: 10, Top: 50, Width: 300, Height: 20);
            }
            """.trimIndent()
        )
    )
}
