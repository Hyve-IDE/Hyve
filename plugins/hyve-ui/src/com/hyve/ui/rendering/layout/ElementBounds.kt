package com.hyve.ui.rendering.layout

import com.hyve.ui.core.id.ElementId

/**
 * Calculated bounds for a UI element after layout processing.
 * Contains the final absolute pixel position and size.
 *
 * @param elementId The ID of the element (if present)
 * @param bounds The absolute pixel bounds (x, y, width, height)
 * @param visible Whether the element should be rendered (false if outside parent bounds, etc.)
 */
data class ElementBounds(
    val elementId: ElementId?,
    val bounds: Rect,
    val visible: Boolean = true
) {
    /**
     * Left edge position
     */
    val x: Float get() = bounds.x

    /**
     * Top edge position
     */
    val y: Float get() = bounds.y

    /**
     * Width
     */
    val width: Float get() = bounds.width

    /**
     * Height
     */
    val height: Float get() = bounds.height

    /**
     * Right edge position
     */
    val right: Float get() = bounds.right

    /**
     * Bottom edge position
     */
    val bottom: Float get() = bounds.bottom

    /**
     * Check if a point is within these bounds
     */
    fun contains(pointX: Float, pointY: Float): Boolean =
        visible && bounds.contains(pointX, pointY)

    override fun toString(): String =
        "ElementBounds(id=$elementId, bounds=$bounds, visible=$visible)"
}
