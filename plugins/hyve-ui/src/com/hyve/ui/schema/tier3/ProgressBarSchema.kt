package com.hyve.ui.schema.tier3

import com.hyve.ui.core.id.ElementType
import com.hyve.ui.core.id.PropertyName
import com.hyve.ui.schema.*

/**
 * Schema for ProgressBar element - visual progress indicator.
 *
 * ProgressBar elements display progress as a filled bar that grows
 * from 0% to 100%. They're commonly used for loading screens,
 * health bars, experience bars, and other progress indicators.
 *
 * Common properties:
 * - Value: Current progress value (0.0 to 1.0)
 * - FillColor: Color of the filled portion
 * - BackgroundColor: Color of the unfilled portion
 */
object ProgressBarSchema {
    fun create(): ElementSchema = ElementSchema(
        type = ElementType("ProgressBar"),
        category = ElementCategory.LAYOUT,
        description = "Visual progress indicator showing completion from 0% to 100%",
        canHaveChildren = false,
        properties = listOf(
            // Value
            PropertySchema(
                name = PropertyName("Value"),
                type = PropertyType.NUMBER,
                required = false,
                description = "Current progress value (0.0 to 1.0)"
            ),
            PropertySchema(
                name = PropertyName("MinValue"),
                type = PropertyType.NUMBER,
                required = false,
                description = "Minimum value (default: 0.0)"
            ),
            PropertySchema(
                name = PropertyName("MaxValue"),
                type = PropertyType.NUMBER,
                required = false,
                description = "Maximum value (default: 1.0)"
            ),

            // Visual styling
            PropertySchema(
                name = PropertyName("FillColor"),
                type = PropertyType.COLOR,
                required = false,
                description = "Color of the filled portion"
            ),
            CommonPropertySchemas.background("Color of the unfilled portion"),
            CommonPropertySchemas.borderColor(),
            CommonPropertySchemas.borderWidth(),
            PropertySchema(
                name = PropertyName("CornerRadius"),
                type = PropertyType.NUMBER,
                required = false,
                description = "Corner radius for rounded corners"
            ),

            // Direction and animation
            PropertySchema(
                name = PropertyName("FillDirection"),
                type = PropertyType.TEXT,
                required = false,
                description = "Fill direction: LeftToRight, RightToLeft, TopToBottom, BottomToTop"
            ),
            PropertySchema(
                name = PropertyName("Animated"),
                type = PropertyType.BOOLEAN,
                required = false,
                description = "Whether to animate value changes"
            ),
            PropertySchema(
                name = PropertyName("AnimationDuration"),
                type = PropertyType.NUMBER,
                required = false,
                description = "Duration of animation in seconds"
            ),

            // Text overlay
            PropertySchema(
                name = PropertyName("ShowPercentage"),
                type = PropertyType.BOOLEAN,
                required = false,
                description = "Whether to display percentage text overlay"
            ),
            PropertySchema(
                name = PropertyName("TextColor"),
                type = PropertyType.COLOR,
                required = false,
                description = "Color of percentage text"
            ),
            CommonPropertySchemas.fontSize("Size of percentage text"),

            // Layout
            CommonPropertySchemas.anchor("Position and size of the progress bar"),

            // State
            CommonPropertySchemas.visible(),

            // Style reference
            CommonPropertySchemas.style()
        ),
        examples = listOf(
            """
            ProgressBar #HealthBar {
                Value: 0.75;
                FillColor: #00ff00;
                Background: #333333;
                Anchor: (Left: 10, Top: 10, Width: 200, Height: 20);
                ShowPercentage: true;
            }
            """.trimIndent(),
            """
            ProgressBar #LoadingBar {
                Value: 0.5;
                FillColor: #4CAF50;
                Background: #cccccc;
                Animated: true;
                AnimationDuration: 0.3;
            }
            """.trimIndent()
        )
    )
}
