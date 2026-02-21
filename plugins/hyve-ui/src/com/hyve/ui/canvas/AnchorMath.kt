// Copyright 2026 Hyve. All rights reserved.
package com.hyve.ui.canvas

import androidx.compose.ui.geometry.Offset
import com.hyve.ui.core.domain.anchor.AnchorDimension
import com.hyve.ui.core.domain.anchor.AnchorValue
import com.hyve.ui.rendering.layout.Rect

/**
 * Pure functions for anchor arithmetic — move, resize, and bounds calculation.
 *
 * Extracted from CanvasState to keep anchor math testable and reusable
 * without requiring canvas state context.
 */

/**
 * Calculate new anchor after moving by delta.
 *
 * Handles all four edge anchors:
 * - Left/Top: increase with positive delta (moving right/down)
 * - Right/Bottom: decrease with positive delta (moving right/down reduces distance from edge)
 */
internal fun calculateMovedAnchor(
    currentAnchor: AnchorValue,
    delta: Offset,
    parentBounds: Rect? = null
): AnchorValue {
    val pw = parentBounds?.width ?: 1920f
    val ph = parentBounds?.height ?: 1080f

    val newLeft = when (val left = currentAnchor.left) {
        is AnchorDimension.Absolute -> AnchorDimension.Absolute(left.pixels + delta.x)
        is AnchorDimension.Relative -> AnchorDimension.Absolute(left.ratio * pw + delta.x)
        null -> null
    }

    val newTop = when (val top = currentAnchor.top) {
        is AnchorDimension.Absolute -> AnchorDimension.Absolute(top.pixels + delta.y)
        is AnchorDimension.Relative -> AnchorDimension.Absolute(top.ratio * ph + delta.y)
        null -> null
    }

    val newRight = when (val right = currentAnchor.right) {
        is AnchorDimension.Absolute -> AnchorDimension.Absolute(right.pixels - delta.x)
        is AnchorDimension.Relative -> AnchorDimension.Absolute(right.ratio * pw - delta.x)
        null -> null
    }

    val newBottom = when (val bottom = currentAnchor.bottom) {
        is AnchorDimension.Absolute -> AnchorDimension.Absolute(bottom.pixels - delta.y)
        is AnchorDimension.Relative -> AnchorDimension.Absolute(bottom.ratio * ph - delta.y)
        null -> null
    }

    return AnchorValue(
        left = newLeft,
        top = newTop,
        right = newRight,
        bottom = newBottom,
        width = currentAnchor.width,
        height = currentAnchor.height
    )
}

/**
 * Calculate new anchor after resizing via a specific handle.
 *
 * Respects the anchor model:
 * - Left+Width → moving left edge adjusts left and shrinks width
 * - Right+Width → moving right edge adjusts right and grows width
 * - Left+Right (stretch) → only edge positions change
 * - Width-only (centered) → only width changes
 *
 * Minimum dimension is 20px to prevent zero-size elements.
 */
internal fun calculateResizedAnchor(
    currentAnchor: AnchorValue,
    handle: CanvasState.ResizeHandle,
    delta: Offset
): AnchorValue {
    var newLeft = currentAnchor.left
    var newTop = currentAnchor.top
    var newRight = currentAnchor.right
    var newBottom = currentAnchor.bottom
    var newWidth = currentAnchor.width
    var newHeight = currentAnchor.height

    val hasLeft = currentAnchor.left is AnchorDimension.Absolute
    val hasRight = currentAnchor.right is AnchorDimension.Absolute
    val hasTop = currentAnchor.top is AnchorDimension.Absolute
    val hasBottom = currentAnchor.bottom is AnchorDimension.Absolute

    // --- Horizontal resize ---
    if (handle.affectsLeft()) {
        when {
            hasLeft -> {
                newLeft = AnchorDimension.Absolute(currentAnchor.left.pixels + delta.x)
                when (val width = currentAnchor.width) {
                    is AnchorDimension.Absolute -> {
                        newWidth = AnchorDimension.Absolute(kotlin.math.max(20f, width.pixels - delta.x))
                    }
                    else -> {}
                }
            }
            hasRight -> {
                when (val width = currentAnchor.width) {
                    is AnchorDimension.Absolute -> {
                        newWidth = AnchorDimension.Absolute(kotlin.math.max(20f, width.pixels - delta.x))
                    }
                    else -> {}
                }
            }
            else -> {
                when (val width = currentAnchor.width) {
                    is AnchorDimension.Absolute -> {
                        newWidth = AnchorDimension.Absolute(kotlin.math.max(20f, width.pixels - delta.x))
                    }
                    else -> {}
                }
            }
        }
    } else if (handle.affectsRight()) {
        when {
            hasRight && !hasLeft -> {
                newRight = AnchorDimension.Absolute(currentAnchor.right.pixels - delta.x)
                when (val width = currentAnchor.width) {
                    is AnchorDimension.Absolute -> {
                        newWidth = AnchorDimension.Absolute(kotlin.math.max(20f, width.pixels + delta.x))
                    }
                    else -> {}
                }
            }
            hasLeft && hasRight -> {
                newRight = AnchorDimension.Absolute(currentAnchor.right.pixels - delta.x)
            }
            else -> {
                when (val width = currentAnchor.width) {
                    is AnchorDimension.Absolute -> {
                        newWidth = AnchorDimension.Absolute(kotlin.math.max(20f, width.pixels + delta.x))
                    }
                    else -> {}
                }
            }
        }
    }

    // --- Vertical resize ---
    if (handle.affectsTop()) {
        when {
            hasTop -> {
                newTop = AnchorDimension.Absolute(currentAnchor.top.pixels + delta.y)
                when (val height = currentAnchor.height) {
                    is AnchorDimension.Absolute -> {
                        newHeight = AnchorDimension.Absolute(kotlin.math.max(20f, height.pixels - delta.y))
                    }
                    else -> {}
                }
            }
            hasBottom -> {
                when (val height = currentAnchor.height) {
                    is AnchorDimension.Absolute -> {
                        newHeight = AnchorDimension.Absolute(kotlin.math.max(20f, height.pixels - delta.y))
                    }
                    else -> {}
                }
            }
            else -> {
                when (val height = currentAnchor.height) {
                    is AnchorDimension.Absolute -> {
                        newHeight = AnchorDimension.Absolute(kotlin.math.max(20f, height.pixels - delta.y))
                    }
                    else -> {}
                }
            }
        }
    } else if (handle.affectsBottom()) {
        when {
            hasBottom && !hasTop -> {
                newBottom = AnchorDimension.Absolute(currentAnchor.bottom.pixels - delta.y)
                when (val height = currentAnchor.height) {
                    is AnchorDimension.Absolute -> {
                        newHeight = AnchorDimension.Absolute(kotlin.math.max(20f, height.pixels + delta.y))
                    }
                    else -> {}
                }
            }
            hasTop && hasBottom -> {
                newBottom = AnchorDimension.Absolute(currentAnchor.bottom.pixels - delta.y)
            }
            else -> {
                when (val height = currentAnchor.height) {
                    is AnchorDimension.Absolute -> {
                        newHeight = AnchorDimension.Absolute(kotlin.math.max(20f, height.pixels + delta.y))
                    }
                    else -> {}
                }
            }
        }
    }

    return AnchorValue(
        left = newLeft,
        top = newTop,
        right = newRight,
        bottom = newBottom,
        width = newWidth,
        height = newHeight
    )
}

/**
 * Calculate visual bounds after applying a resize handle delta.
 * Used for live preview during resize drag.
 */
internal fun calculateResizedBounds(
    bounds: Rect,
    handle: CanvasState.ResizeHandle,
    delta: Offset
): Rect {
    var x = bounds.x
    var y = bounds.y
    var width = bounds.width
    var height = bounds.height

    if (handle.affectsLeft()) {
        x += delta.x
        width = kotlin.math.max(20f, width - delta.x)
    } else if (handle.affectsWidth()) {
        width = kotlin.math.max(20f, width + delta.x)
    }

    if (handle.affectsTop()) {
        y += delta.y
        height = kotlin.math.max(20f, height - delta.y)
    } else if (handle.affectsHeight()) {
        height = kotlin.math.max(20f, height + delta.y)
    }

    return Rect(x, y, width, height)
}
