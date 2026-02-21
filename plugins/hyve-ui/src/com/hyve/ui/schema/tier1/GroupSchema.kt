package com.hyve.ui.schema.tier1

import com.hyve.ui.core.id.ElementType
import com.hyve.ui.core.id.PropertyName
import com.hyve.ui.schema.*

/**
 * Schema for Group element - the primary container element in HyUI.
 *
 * Group elements can contain other elements and control their layout behavior.
 * They support various layout modes (Top, Left, Right, Bottom, Middle) and
 * can be positioned using anchors.
 *
 * Common properties:
 * - LayoutMode: How child elements are arranged
 * - Anchor: Position and size
 * - Background: Background color
 * - Padding: Inner spacing
 * - Spacing: Gap between children
 */
object GroupSchema {
    fun create(): ElementSchema = ElementSchema(
        type = ElementType("Group"),
        category = ElementCategory.CONTAINER,
        description = "Container element that groups other UI elements with optional layout modes",
        canHaveChildren = true,
        properties = listOf(
            // Layout properties
            PropertySchema(
                name = PropertyName("LayoutMode"),
                type = PropertyType.TEXT,
                required = false,
                description = "Layout mode for child elements: Top, Left, Right, Bottom, Middle"
            ),
            CommonPropertySchemas.anchor("Position and size of the group"),
            CommonPropertySchemas.padding(),
            CommonPropertySchemas.spacing(),

            // Visual properties
            CommonPropertySchemas.background(),
            PropertySchema(
                name = PropertyName("BackgroundImage"),
                type = PropertyType.IMAGE_PATH,
                required = false,
                description = "Background image path"
            ),
            CommonPropertySchemas.borderColor(),
            CommonPropertySchemas.borderWidth(),
            PropertySchema(
                name = PropertyName("CornerRadius"),
                type = PropertyType.NUMBER,
                required = false,
                description = "Corner radius for rounded corners"
            ),

            // Visibility and interaction
            CommonPropertySchemas.visible(),
            CommonPropertySchemas.enabled("Whether the group and its children are enabled"),
            PropertySchema(
                name = PropertyName("ClipChildren"),
                type = PropertyType.BOOLEAN,
                required = false,
                description = "Whether to clip children that overflow the group bounds"
            ),

            // Style reference
            CommonPropertySchemas.style()
        ),
        examples = listOf(
            """
            Group #Container {
                LayoutMode: Top;
                Anchor: (Left: 10, Top: 10, Width: 200, Height: 300);
                Background: #ffffff(0.9);
                Padding: (Left: 5, Top: 5, Right: 5, Bottom: 5);
                Spacing: 10;
            }
            """.trimIndent(),
            """
            Group #Header {
                LayoutMode: Left;
                Anchor: (Width: 100%, Height: 50);
                Background: #333333;
            }
            """.trimIndent()
        )
    )
}
