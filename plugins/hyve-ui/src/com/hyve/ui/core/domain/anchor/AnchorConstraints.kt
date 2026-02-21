package com.hyve.ui.core.domain.anchor

import com.hyve.ui.rendering.layout.Rect

/**
 * Constraint modes for the horizontal axis.
 * Describes which anchor fields control horizontal positioning.
 */
enum class HorizontalConstraint(val displayName: String) {
    LEFT_WIDTH("Left + Width"),
    RIGHT_WIDTH("Right + Width"),
    LEFT_RIGHT("Left + Right"),
    CENTER("Center"),
    FREE("Free");
}

/**
 * Constraint modes for the vertical axis.
 * Describes which anchor fields control vertical positioning.
 */
enum class VerticalConstraint(val displayName: String) {
    TOP_HEIGHT("Top + Height"),
    BOTTOM_HEIGHT("Bottom + Height"),
    TOP_BOTTOM("Top + Bottom"),
    MIDDLE("Middle"),
    FREE("Free");
}

/**
 * Pure functions for detecting and applying anchor constraint modes.
 * All functions are side-effect-free and fully testable.
 */
object AnchorConstraints {

    /**
     * Detect the horizontal constraint mode from an anchor value.
     *
     * Priority: Left+Right (stretch) > Left+Width > Right+Width > Width-only (center) > Free.
     * If all three (Left+Right+Width) are present, treat as LEFT_RIGHT (stretch takes precedence).
     */
    fun detectHorizontalMode(anchor: AnchorValue): HorizontalConstraint {
        val hasLeft = anchor.left != null
        val hasRight = anchor.right != null
        val hasWidth = anchor.width != null

        return when {
            hasLeft && hasRight -> HorizontalConstraint.LEFT_RIGHT
            hasLeft && hasWidth -> HorizontalConstraint.LEFT_WIDTH
            hasRight && hasWidth -> HorizontalConstraint.RIGHT_WIDTH
            hasWidth -> HorizontalConstraint.CENTER
            hasLeft -> HorizontalConstraint.LEFT_WIDTH // Left-only treated as Left+Width
            hasRight -> HorizontalConstraint.RIGHT_WIDTH // Right-only treated as Right+Width
            else -> HorizontalConstraint.FREE
        }
    }

    /**
     * Detect the vertical constraint mode from an anchor value.
     *
     * Priority: Top+Bottom (stretch) > Top+Height > Bottom+Height > Height-only (middle) > Free.
     * If all three (Top+Bottom+Height) are present, treat as TOP_BOTTOM (stretch takes precedence).
     */
    fun detectVerticalMode(anchor: AnchorValue): VerticalConstraint {
        val hasTop = anchor.top != null
        val hasBottom = anchor.bottom != null
        val hasHeight = anchor.height != null

        return when {
            hasTop && hasBottom -> VerticalConstraint.TOP_BOTTOM
            hasTop && hasHeight -> VerticalConstraint.TOP_HEIGHT
            hasBottom && hasHeight -> VerticalConstraint.BOTTOM_HEIGHT
            hasHeight -> VerticalConstraint.MIDDLE
            hasTop -> VerticalConstraint.TOP_HEIGHT // Top-only treated as Top+Height
            hasBottom -> VerticalConstraint.BOTTOM_HEIGHT // Bottom-only treated as Bottom+Height
            else -> VerticalConstraint.FREE
        }
    }

    /**
     * Apply a horizontal constraint mode to an anchor, preserving the element's visual position.
     *
     * @param anchor Current anchor value
     * @param mode Target horizontal constraint mode
     * @param elementBounds Current absolute pixel bounds of the element (from layout engine)
     * @param parentBounds Absolute pixel bounds of the parent container
     * @return New anchor value with horizontal fields updated, vertical fields preserved
     */
    fun applyHorizontalMode(
        anchor: AnchorValue,
        mode: HorizontalConstraint,
        elementBounds: Rect,
        parentBounds: Rect
    ): AnchorValue {
        // Calculate element's position relative to parent
        val relLeft = elementBounds.x - parentBounds.x
        val relRight = (parentBounds.x + parentBounds.width) - (elementBounds.x + elementBounds.width)
        val width = elementBounds.width

        return when (mode) {
            HorizontalConstraint.LEFT_WIDTH -> anchor.copy(
                left = AnchorDimension.Absolute(relLeft),
                right = null,
                width = AnchorDimension.Absolute(width)
            )
            HorizontalConstraint.RIGHT_WIDTH -> anchor.copy(
                left = null,
                right = AnchorDimension.Absolute(clampNonNegative(relRight)),
                width = AnchorDimension.Absolute(width)
            )
            HorizontalConstraint.LEFT_RIGHT -> anchor.copy(
                left = AnchorDimension.Absolute(relLeft),
                right = AnchorDimension.Absolute(clampNonNegative(relRight)),
                width = null
            )
            HorizontalConstraint.CENTER -> anchor.copy(
                left = null,
                right = null,
                width = AnchorDimension.Absolute(width)
            )
            HorizontalConstraint.FREE -> anchor.copy(
                left = null,
                right = null,
                width = null
            )
        }
    }

    /**
     * Apply a vertical constraint mode to an anchor, preserving the element's visual position.
     *
     * @param anchor Current anchor value
     * @param mode Target vertical constraint mode
     * @param elementBounds Current absolute pixel bounds of the element (from layout engine)
     * @param parentBounds Absolute pixel bounds of the parent container
     * @return New anchor value with vertical fields updated, horizontal fields preserved
     */
    fun applyVerticalMode(
        anchor: AnchorValue,
        mode: VerticalConstraint,
        elementBounds: Rect,
        parentBounds: Rect
    ): AnchorValue {
        // Calculate element's position relative to parent
        val relTop = elementBounds.y - parentBounds.y
        val relBottom = (parentBounds.y + parentBounds.height) - (elementBounds.y + elementBounds.height)
        val height = elementBounds.height

        return when (mode) {
            VerticalConstraint.TOP_HEIGHT -> anchor.copy(
                top = AnchorDimension.Absolute(relTop),
                bottom = null,
                height = AnchorDimension.Absolute(height)
            )
            VerticalConstraint.BOTTOM_HEIGHT -> anchor.copy(
                top = null,
                bottom = AnchorDimension.Absolute(clampNonNegative(relBottom)),
                height = AnchorDimension.Absolute(height)
            )
            VerticalConstraint.TOP_BOTTOM -> anchor.copy(
                top = AnchorDimension.Absolute(relTop),
                bottom = AnchorDimension.Absolute(clampNonNegative(relBottom)),
                height = null
            )
            VerticalConstraint.MIDDLE -> anchor.copy(
                top = null,
                bottom = null,
                height = AnchorDimension.Absolute(height)
            )
            VerticalConstraint.FREE -> anchor.copy(
                top = null,
                bottom = null,
                height = null
            )
        }
    }

    /**
     * Toggle an individual edge anchor on/off, adjusting the constraint mode accordingly.
     * Preserves element visual position.
     *
     * @param anchor Current anchor value
     * @param edge Which edge to toggle ("left", "top", "right", "bottom")
     * @param elementBounds Current absolute pixel bounds of the element
     * @param parentBounds Absolute pixel bounds of the parent
     * @return New anchor value with the edge toggled
     */
    fun toggleEdge(
        anchor: AnchorValue,
        edge: String,
        elementBounds: Rect,
        parentBounds: Rect
    ): AnchorValue {
        val relLeft = elementBounds.x - parentBounds.x
        val relRight = (parentBounds.x + parentBounds.width) - (elementBounds.x + elementBounds.width)
        val relTop = elementBounds.y - parentBounds.y
        val relBottom = (parentBounds.y + parentBounds.height) - (elementBounds.y + elementBounds.height)
        val width = elementBounds.width
        val height = elementBounds.height

        return when (edge) {
            "left" -> {
                if (anchor.left != null) {
                    // Removing left: if Left+Right → keep Right+Width; if Left+Width → Center (Width only)
                    if (anchor.right != null) {
                        anchor.copy(left = null, width = AnchorDimension.Absolute(width))
                    } else {
                        anchor.copy(left = null)
                    }
                } else {
                    // Adding left: if Right+Width → Left+Right (remove Width); otherwise add Left
                    if (anchor.right != null && anchor.width != null) {
                        anchor.copy(left = AnchorDimension.Absolute(relLeft), width = null)
                    } else {
                        anchor.copy(left = AnchorDimension.Absolute(relLeft))
                    }
                }
            }
            "right" -> {
                if (anchor.right != null) {
                    // Removing right: if Left+Right → keep Left+Width; if Right+Width → Center (Width only)
                    if (anchor.left != null) {
                        anchor.copy(right = null, width = AnchorDimension.Absolute(width))
                    } else {
                        anchor.copy(right = null)
                    }
                } else {
                    // Adding right: if Left+Width → Left+Right (remove Width); otherwise add Right
                    if (anchor.left != null && anchor.width != null) {
                        anchor.copy(right = AnchorDimension.Absolute(clampNonNegative(relRight)), width = null)
                    } else {
                        anchor.copy(right = AnchorDimension.Absolute(clampNonNegative(relRight)))
                    }
                }
            }
            "top" -> {
                if (anchor.top != null) {
                    if (anchor.bottom != null) {
                        anchor.copy(top = null, height = AnchorDimension.Absolute(height))
                    } else {
                        anchor.copy(top = null)
                    }
                } else {
                    if (anchor.bottom != null && anchor.height != null) {
                        anchor.copy(top = AnchorDimension.Absolute(relTop), height = null)
                    } else {
                        anchor.copy(top = AnchorDimension.Absolute(relTop))
                    }
                }
            }
            "bottom" -> {
                if (anchor.bottom != null) {
                    if (anchor.top != null) {
                        anchor.copy(bottom = null, height = AnchorDimension.Absolute(height))
                    } else {
                        anchor.copy(bottom = null)
                    }
                } else {
                    if (anchor.top != null && anchor.height != null) {
                        anchor.copy(bottom = AnchorDimension.Absolute(clampNonNegative(relBottom)), height = null)
                    } else {
                        anchor.copy(bottom = AnchorDimension.Absolute(clampNonNegative(relBottom)))
                    }
                }
            }
            else -> anchor
        }
    }

    /**
     * Check which edges have active anchors.
     * Returns a set of edge names: "left", "top", "right", "bottom".
     */
    fun activeEdges(anchor: AnchorValue): Set<String> {
        val edges = mutableSetOf<String>()
        if (anchor.left != null) edges.add("left")
        if (anchor.top != null) edges.add("top")
        if (anchor.right != null) edges.add("right")
        if (anchor.bottom != null) edges.add("bottom")
        return edges
    }

    /**
     * Convert a string-keyed anchor values map (as used in Composer slots) to an [AnchorValue].
     * Keys: "left", "top", "right", "bottom", "width", "height".
     * Values: "10" (absolute pixels) or "50%" (relative ratio).
     */
    fun fromStringMap(values: Map<String, String>): AnchorValue {
        fun parseDimension(key: String): AnchorDimension? {
            val raw = values[key] ?: return null
            if (raw.endsWith("%")) {
                val pct = raw.removeSuffix("%").toFloatOrNull() ?: return null
                return AnchorDimension.Relative(pct / 100f)
            }
            val num = raw.toFloatOrNull() ?: return null
            return AnchorDimension.Absolute(num)
        }

        return AnchorValue(
            left = parseDimension("left"),
            top = parseDimension("top"),
            right = parseDimension("right"),
            bottom = parseDimension("bottom"),
            width = parseDimension("width"),
            height = parseDimension("height")
        )
    }

    private fun clampNonNegative(value: Float): Float = if (value < 0f) 0f else value
}
