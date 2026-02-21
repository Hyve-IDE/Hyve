package com.hyve.ui.rendering.layout

import com.hyve.ui.core.domain.anchor.AnchorDimension
import com.hyve.ui.core.domain.anchor.AnchorValue

/**
 * Calculates absolute pixel bounds from anchor values.
 * Handles both absolute and relative anchor positioning.
 *
 * Anchor system rules:
 * - Left/Top: Distance from parent's left/top edge
 * - Right/Bottom: Distance from parent's right/bottom edge
 * - Width/Height: Explicit size
 * - Values can be absolute (pixels) or relative (0.0-1.0 ratio)
 *
 * Examples:
 * - Anchor: (Left: 10, Top: 20, Width: 100, Height: 50)
 *   → Position at (10, 20) with size 100x50
 *
 * - Anchor: (Left: 0.5, Top: 0.5, Width: 200, Height: 100)
 *   → Center at parent's 50%, 50%, with size 200x100
 *
 * - Anchor: (Left: 10, Top: 10, Right: 10, Bottom: 10)
 *   → Fill parent with 10px padding on all sides
 */
object AnchorCalculator {
    /**
     * Calculate absolute pixel bounds from anchor value and parent bounds.
     *
     * @param anchor The anchor value to convert
     * @param parentBounds The parent container's bounds
     * @return Absolute pixel bounds (Rect)
     */
    fun calculateBounds(anchor: AnchorValue, parentBounds: Rect): Rect {
        // Calculate X position and width
        val (x, width) = calculateHorizontal(anchor, parentBounds)

        // Calculate Y position and height
        val (y, height) = calculateVertical(anchor, parentBounds)

        return Rect(x, y, width, height)
    }

    /**
     * Calculate horizontal position (x) and width
     */
    private fun calculateHorizontal(anchor: AnchorValue, parentBounds: Rect): Pair<Float, Float> {
        // Store properties in local variables to avoid smart cast issues
        val left = anchor.left
        val right = anchor.right
        val width = anchor.width

        return when {
            // Case 1: Left + Width (most common)
            left != null && width != null -> {
                val x = resolvePosition(left, parentBounds.x, parentBounds.width)
                val w = resolveSize(width, parentBounds.width)
                x to w
            }

            // Case 2: Left + Right (stretch between edges)
            left != null && right != null -> {
                val leftPos = resolvePosition(left, parentBounds.x, parentBounds.width)
                val rightPos = resolvePositionFromEnd(right, parentBounds.right, parentBounds.width)
                val w = rightPos - leftPos
                leftPos to w
            }

            // Case 3: Right + Width (anchor to right edge)
            right != null && width != null -> {
                val w = resolveSize(width, parentBounds.width)
                val rightPos = resolvePositionFromEnd(right, parentBounds.right, parentBounds.width)
                val x = rightPos - w
                x to w
            }

            // Case 4: Only Width (center horizontally in parent)
            width != null -> {
                val w = resolveSize(width, parentBounds.width)
                val x = parentBounds.x + (parentBounds.width - w) / 2f
                x to w
            }

            // Case 5: Only Left (default to width = parent width)
            left != null -> {
                val x = resolvePosition(left, parentBounds.x, parentBounds.width)
                val w = parentBounds.width - (x - parentBounds.x)
                x to w
            }

            // Case 6: Only Right (default to fill from left edge)
            right != null -> {
                val rightPos = resolvePositionFromEnd(right, parentBounds.right, parentBounds.width)
                val w = rightPos - parentBounds.x
                parentBounds.x to w
            }

            // Case 7: No horizontal positioning - fill parent width
            else -> {
                parentBounds.x to parentBounds.width
            }
        }
    }

    /**
     * Calculate vertical position (y) and height
     */
    private fun calculateVertical(anchor: AnchorValue, parentBounds: Rect): Pair<Float, Float> {
        // Store properties in local variables to avoid smart cast issues
        val top = anchor.top
        val bottom = anchor.bottom
        val height = anchor.height

        return when {
            // Case 1: Top + Height (most common)
            top != null && height != null -> {
                val y = resolvePosition(top, parentBounds.y, parentBounds.height)
                val h = resolveSize(height, parentBounds.height)
                y to h
            }

            // Case 2: Top + Bottom (stretch between edges)
            top != null && bottom != null -> {
                val topPos = resolvePosition(top, parentBounds.y, parentBounds.height)
                val bottomPos = resolvePositionFromEnd(bottom, parentBounds.bottom, parentBounds.height)
                val h = bottomPos - topPos
                topPos to h
            }

            // Case 3: Bottom + Height (anchor to bottom edge)
            bottom != null && height != null -> {
                val h = resolveSize(height, parentBounds.height)
                val bottomPos = resolvePositionFromEnd(bottom, parentBounds.bottom, parentBounds.height)
                val y = bottomPos - h
                y to h
            }

            // Case 4: Only Height (center vertically in parent)
            height != null -> {
                val h = resolveSize(height, parentBounds.height)
                val y = parentBounds.y + (parentBounds.height - h) / 2f
                y to h
            }

            // Case 5: Only Top (default to height = parent height)
            top != null -> {
                val y = resolvePosition(top, parentBounds.y, parentBounds.height)
                val h = parentBounds.height - (y - parentBounds.y)
                y to h
            }

            // Case 6: Only Bottom (default to fill from top edge)
            bottom != null -> {
                val bottomPos = resolvePositionFromEnd(bottom, parentBounds.bottom, parentBounds.height)
                val h = bottomPos - parentBounds.y
                parentBounds.y to h
            }

            // Case 7: No vertical positioning - fill parent height
            else -> {
                parentBounds.y to parentBounds.height
            }
        }
    }

    /**
     * Resolve position from start edge (Left or Top)
     *
     * @param dimension The anchor dimension (absolute pixels or relative ratio)
     * @param parentStart The parent's start position (x or y)
     * @param parentSize The parent's size (width or height)
     * @return Absolute pixel position
     */
    private fun resolvePosition(
        dimension: AnchorDimension,
        parentStart: Float,
        parentSize: Float
    ): Float = when (dimension) {
        is AnchorDimension.Absolute -> parentStart + dimension.pixels
        is AnchorDimension.Relative -> parentStart + (dimension.ratio * parentSize)
    }

    /**
     * Resolve position from end edge (Right or Bottom)
     *
     * @param dimension The anchor dimension (absolute pixels or relative ratio)
     * @param parentEnd The parent's end position (right or bottom)
     * @param parentSize The parent's size (width or height)
     * @return Absolute pixel position
     */
    private fun resolvePositionFromEnd(
        dimension: AnchorDimension,
        parentEnd: Float,
        parentSize: Float
    ): Float = when (dimension) {
        is AnchorDimension.Absolute -> parentEnd - dimension.pixels
        is AnchorDimension.Relative -> parentEnd - (dimension.ratio * parentSize)
    }

    /**
     * Resolve size dimension (Width or Height)
     *
     * @param dimension The anchor dimension (absolute pixels or relative ratio)
     * @param parentSize The parent's size (width or height)
     * @return Absolute pixel size
     */
    private fun resolveSize(
        dimension: AnchorDimension,
        parentSize: Float
    ): Float = when (dimension) {
        is AnchorDimension.Absolute -> dimension.pixels
        is AnchorDimension.Relative -> dimension.ratio * parentSize
    }

    /**
     * Compute an anchor value that positions an element at the given absolute bounds
     * relative to a new parent's bounds.
     *
     * This is used when reparenting an element to preserve its visual position.
     * The resulting anchor uses absolute pixel values for Left, Top, Width, and Height.
     *
     * @param elementBounds The element's current absolute pixel bounds
     * @param newParentBounds The new parent's absolute pixel bounds
     * @return An anchor value that will position the element at the same visual location
     */
    fun computeAnchorForBounds(elementBounds: Rect, newParentBounds: Rect): AnchorValue {
        // Calculate position relative to new parent's top-left corner
        val relativeX = elementBounds.x - newParentBounds.x
        val relativeY = elementBounds.y - newParentBounds.y

        return AnchorValue(
            left = AnchorDimension.Absolute(relativeX),
            top = AnchorDimension.Absolute(relativeY),
            width = AnchorDimension.Absolute(elementBounds.width),
            height = AnchorDimension.Absolute(elementBounds.height),
            right = null,
            bottom = null
        )
    }
}
