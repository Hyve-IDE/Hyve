package com.hyve.ui.schema.tier1

import com.hyve.ui.core.id.ElementType
import com.hyve.ui.core.id.PropertyName
import com.hyve.ui.schema.*

/**
 * Schema for Button element - interactive clickable button.
 *
 * Button elements are used for user interactions. They can display text,
 * icons, and respond to click events. Buttons support visual states
 * (normal, hovered, pressed, disabled) and can trigger actions.
 *
 * Common properties:
 * - Text: Button label text
 * - OnClick: Event handler for click action
 * - Enabled: Whether the button is interactive
 */
object ButtonSchema {
    fun create(): ElementSchema = ElementSchema(
        type = ElementType("Button"),
        category = ElementCategory.INTERACTIVE,
        description = "Interactive button element that responds to click events",
        canHaveChildren = false,
        properties = listOf(
            // Content
            PropertySchema(
                name = PropertyName("Text"),
                type = PropertyType.TEXT,
                required = false,
                description = "The text displayed on the button"
            ),
            PropertySchema(
                name = PropertyName("Icon"),
                type = PropertyType.IMAGE_PATH,
                required = false,
                description = "Icon image displayed on the button"
            ),

            // Event handling
            PropertySchema(
                name = PropertyName("OnClick"),
                type = PropertyType.TEXT,
                required = false,
                description = "Action or event to trigger when button is clicked"
            ),

            // Visual states
            CommonPropertySchemas.background("Background color in normal state"),
            PropertySchema(
                name = PropertyName("BackgroundHover"),
                type = PropertyType.COLOR,
                required = false,
                description = "Background color when hovered"
            ),
            PropertySchema(
                name = PropertyName("BackgroundPressed"),
                type = PropertyType.COLOR,
                required = false,
                description = "Background color when pressed"
            ),
            PropertySchema(
                name = PropertyName("BackgroundDisabled"),
                type = PropertyType.COLOR,
                required = false,
                description = "Background color when disabled"
            ),

            // Text styling
            CommonPropertySchemas.fontSize("Size of the button text"),
            CommonPropertySchemas.color(),
            PropertySchema(
                name = PropertyName("RenderBold"),
                type = PropertyType.BOOLEAN,
                required = false,
                description = "Render text in bold"
            ),

            // Border and shape
            CommonPropertySchemas.borderColor(),
            CommonPropertySchemas.borderWidth(),
            PropertySchema(
                name = PropertyName("CornerRadius"),
                type = PropertyType.NUMBER,
                required = false,
                description = "Corner radius for rounded corners"
            ),

            // Layout
            CommonPropertySchemas.anchor("Position and size of the button"),
            CommonPropertySchemas.padding("Inner padding around button content"),

            // State
            CommonPropertySchemas.enabled("Whether the button is clickable"),
            CommonPropertySchemas.visible(),

            // Style reference
            CommonPropertySchemas.style()
        ),
        examples = listOf(
            """
            Button #SubmitButton {
                Text: "Submit";
                OnClick: "submit_form";
                Anchor: (Left: 10, Top: 10, Width: 100, Height: 30);
                Background: #4CAF50;
                Color: #ffffff;
            }
            """.trimIndent(),
            """
            Button #CloseButton {
                Text: "X";
                OnClick: "close_window";
                Anchor: (Right: 10, Top: 10, Width: 30, Height: 30);
                Background: #f44336;
                CornerRadius: 15;
            }
            """.trimIndent()
        )
    )
}
