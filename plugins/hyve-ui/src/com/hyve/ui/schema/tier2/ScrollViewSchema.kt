package com.hyve.ui.schema.tier2

import com.hyve.ui.core.id.ElementType
import com.hyve.ui.core.id.PropertyName
import com.hyve.ui.schema.*

/**
 * Schema for ScrollView element - scrollable container.
 *
 * ScrollView elements provide scrolling functionality for content that
 * exceeds the visible area. They can scroll horizontally, vertically, or both,
 * and support customizable scrollbars.
 *
 * Common properties:
 * - ScrollDirection: Horizontal, Vertical, or Both
 * - ShowScrollbars: Whether to display scrollbars
 * - ContentSize: Size of the scrollable content area
 */
object ScrollViewSchema {
    fun create(): ElementSchema = ElementSchema(
        type = ElementType("ScrollView"),
        category = ElementCategory.CONTAINER,
        description = "Scrollable container for content that exceeds visible area",
        canHaveChildren = true,
        properties = listOf(
            // Scroll behavior
            PropertySchema(
                name = PropertyName("ScrollDirection"),
                type = PropertyType.TEXT,
                required = false,
                description = "Scroll direction: Horizontal, Vertical, Both"
            ),
            PropertySchema(
                name = PropertyName("ShowScrollbars"),
                type = PropertyType.BOOLEAN,
                required = false,
                description = "Whether to display scrollbars"
            ),
            PropertySchema(
                name = PropertyName("ScrollPosition"),
                type = PropertyType.TUPLE,
                required = false,
                description = "Current scroll position (X, Y)"
            ),

            // Content sizing
            PropertySchema(
                name = PropertyName("ContentWidth"),
                type = PropertyType.NUMBER,
                required = false,
                description = "Width of the scrollable content area"
            ),
            PropertySchema(
                name = PropertyName("ContentHeight"),
                type = PropertyType.NUMBER,
                required = false,
                description = "Height of the scrollable content area"
            ),

            // Scrollbar styling
            PropertySchema(
                name = PropertyName("ScrollbarColor"),
                type = PropertyType.COLOR,
                required = false,
                description = "Color of the scrollbar"
            ),
            PropertySchema(
                name = PropertyName("ScrollbarWidth"),
                type = PropertyType.NUMBER,
                required = false,
                description = "Width of the scrollbar"
            ),
            PropertySchema(
                name = PropertyName("ScrollbarTrackColor"),
                type = PropertyType.COLOR,
                required = false,
                description = "Color of the scrollbar track"
            ),

            // Visual styling
            CommonPropertySchemas.background(),
            CommonPropertySchemas.borderColor(),
            CommonPropertySchemas.borderWidth(),

            // Layout
            CommonPropertySchemas.anchor("Position and size of the scroll view"),
            CommonPropertySchemas.padding("Inner padding around content"),

            // Behavior
            PropertySchema(
                name = PropertyName("ClipContent"),
                type = PropertyType.BOOLEAN,
                required = false,
                description = "Whether to clip content that overflows"
            ),
            PropertySchema(
                name = PropertyName("ScrollSensitivity"),
                type = PropertyType.NUMBER,
                required = false,
                description = "Scroll wheel sensitivity multiplier"
            ),

            // State
            CommonPropertySchemas.enabled("Whether scrolling is enabled"),
            CommonPropertySchemas.visible(),

            // Style reference
            CommonPropertySchemas.style()
        ),
        examples = listOf(
            """
            ScrollView #ContentScroll {
                ScrollDirection: Vertical;
                ShowScrollbars: true;
                Anchor: (Left: 0, Top: 0, Width: 300, Height: 400);
                ContentHeight: 800;
            }
            """.trimIndent()
        )
    )
}
