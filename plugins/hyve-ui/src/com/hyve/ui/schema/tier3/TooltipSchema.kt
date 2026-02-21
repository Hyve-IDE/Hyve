package com.hyve.ui.schema.tier3

import com.hyve.ui.core.id.ElementType
import com.hyve.ui.core.id.PropertyName
import com.hyve.ui.schema.*

/**
 * Schema for Tooltip element - contextual help popup.
 *
 * Tooltip elements display additional information when the user hovers
 * over a UI element. They typically show brief text descriptions or hints.
 *
 * Common properties:
 * - Text: Tooltip content text
 * - TargetElement: ID of element this tooltip is attached to
 * - ShowDelay: Delay before showing tooltip
 */
object TooltipSchema {
    fun create(): ElementSchema = ElementSchema(
        type = ElementType("Tooltip"),
        category = ElementCategory.LAYOUT,
        description = "Contextual help popup that appears on hover",
        canHaveChildren = false,
        properties = listOf(
            // Content
            PropertySchema(
                name = PropertyName("Text"),
                type = PropertyType.TEXT,
                required = true,
                description = "Tooltip content text"
            ),
            PropertySchema(
                name = PropertyName("Title"),
                type = PropertyType.TEXT,
                required = false,
                description = "Optional tooltip title"
            ),

            // Behavior
            PropertySchema(
                name = PropertyName("ShowDelay"),
                type = PropertyType.NUMBER,
                required = false,
                description = "Delay in seconds before showing tooltip"
            ),
            PropertySchema(
                name = PropertyName("HideDelay"),
                type = PropertyType.NUMBER,
                required = false,
                description = "Delay in seconds before hiding tooltip"
            ),
            PropertySchema(
                name = PropertyName("Position"),
                type = PropertyType.TEXT,
                required = false,
                description = "Tooltip position relative to target: Top, Bottom, Left, Right, Auto"
            ),
            PropertySchema(
                name = PropertyName("FollowCursor"),
                type = PropertyType.BOOLEAN,
                required = false,
                description = "Whether tooltip follows mouse cursor"
            ),

            // Visual styling
            CommonPropertySchemas.background(),
            CommonPropertySchemas.borderColor(),
            CommonPropertySchemas.borderWidth(),
            PropertySchema(
                name = PropertyName("CornerRadius"),
                type = PropertyType.NUMBER,
                required = false,
                description = "Corner radius for rounded corners"
            ),
            PropertySchema(
                name = PropertyName("Shadow"),
                type = PropertyType.BOOLEAN,
                required = false,
                description = "Whether to show drop shadow"
            ),
            PropertySchema(
                name = PropertyName("ShadowColor"),
                type = PropertyType.COLOR,
                required = false,
                description = "Drop shadow color"
            ),

            // Text styling
            CommonPropertySchemas.fontSize("Font size of tooltip text"),
            CommonPropertySchemas.color(),
            PropertySchema(
                name = PropertyName("TitleFontSize"),
                type = PropertyType.NUMBER,
                required = false,
                description = "Font size of tooltip title"
            ),
            PropertySchema(
                name = PropertyName("TitleColor"),
                type = PropertyType.COLOR,
                required = false,
                description = "Title text color"
            ),

            // Layout
            CommonPropertySchemas.padding("Inner padding around content"),
            PropertySchema(
                name = PropertyName("MaxWidth"),
                type = PropertyType.NUMBER,
                required = false,
                description = "Maximum width before text wrapping"
            ),
            PropertySchema(
                name = PropertyName("Offset"),
                type = PropertyType.TUPLE,
                required = false,
                description = "Offset from target element (X, Y)"
            ),

            // Arrow
            PropertySchema(
                name = PropertyName("ShowArrow"),
                type = PropertyType.BOOLEAN,
                required = false,
                description = "Whether to show pointing arrow"
            ),
            PropertySchema(
                name = PropertyName("ArrowSize"),
                type = PropertyType.NUMBER,
                required = false,
                description = "Size of the pointing arrow"
            ),

            // Style reference
            CommonPropertySchemas.style()
        ),
        examples = listOf(
            """
            Tooltip #HelpTooltip {
                Text: "Click here to save your changes";
                Position: Top;
                ShowDelay: 0.5;
                Background: #333333;
                Color: #ffffff;
                ShowArrow: true;
            }
            """.trimIndent(),
            """
            Tooltip #ItemTooltip {
                Title: "Iron Sword";
                Text: "A basic sword made of iron. Damage: 10";
                Position: Auto;
                MaxWidth: 200;
            }
            """.trimIndent()
        )
    )
}
