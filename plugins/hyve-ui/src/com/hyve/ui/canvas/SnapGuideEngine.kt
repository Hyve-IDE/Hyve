// Copyright 2026 Hyve. All rights reserved.
package com.hyve.ui.canvas

import androidx.compose.ui.geometry.Offset
import com.hyve.ui.rendering.layout.Rect
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Pure snap calculation functions for element alignment guides.
 *
 * Follows the AnchorMath.kt pattern — top-level pure functions, zero dependencies,
 * trivially testable. Computes snap corrections and guide lines when dragging
 * elements near other elements on the canvas.
 */

enum class SnapAxis { HORIZONTAL, VERTICAL }

/**
 * A visual guide line shown when elements align.
 *
 * @param axis Whether this is a horizontal or vertical guide
 * @param position Canvas-space coordinate of the line (x for VERTICAL, y for HORIZONTAL)
 * @param spanStart Extent start (min of involved elements along the perpendicular axis)
 * @param spanEnd Extent end (max of involved elements along the perpendicular axis)
 */
data class SnapGuide(
    val axis: SnapAxis,
    val position: Float,
    val spanStart: Float,
    val spanEnd: Float
)

/**
 * Result of a snap calculation.
 *
 * @param snapDelta Correction to apply to raw drag offset
 * @param guides Guide lines to render
 */
data class SnapResult(
    val snapDelta: Offset,
    val guides: List<SnapGuide>
) {
    companion object {
        val NONE = SnapResult(Offset.Zero, emptyList())
    }
}

/** Default snap threshold in canvas pixels. */
const val SNAP_THRESHOLD = 5f

/**
 * Calculate snap correction and guide lines for a moving element against targets.
 *
 * Algorithm per axis:
 * 1. Extract 3 reference positions from moving rect: left/center/right (or top/center/bottom)
 * 2. For each target, extract same 3 positions
 * 3. Compare all 9 pairs — find closest match within threshold
 * 4. The closest match wins; collect all guide lines at that distance
 * 5. Return snap delta (correction) + guide lines
 *
 * @param movingBounds Dragged element(s) bbox AFTER raw offset, BEFORE snap
 * @param targetBounds All non-selected visible elements' bounds
 * @param threshold Maximum distance in canvas pixels for snapping
 */
fun calculateSnap(
    movingBounds: Rect,
    targetBounds: List<Rect>,
    threshold: Float = SNAP_THRESHOLD
): SnapResult {
    if (targetBounds.isEmpty()) return SnapResult.NONE

    // Extract reference positions for the moving rect
    val movingH = floatArrayOf(movingBounds.x, movingBounds.centerX, movingBounds.right)
    val movingV = floatArrayOf(movingBounds.y, movingBounds.centerY, movingBounds.bottom)

    // Best horizontal snap (corrects X)
    var bestHDist = Float.MAX_VALUE
    var bestHDelta = 0f
    val hGuides = mutableListOf<SnapGuide>()

    // Best vertical snap (corrects Y)
    var bestVDist = Float.MAX_VALUE
    var bestVDelta = 0f
    val vGuides = mutableListOf<SnapGuide>()

    for (target in targetBounds) {
        val targetH = floatArrayOf(target.x, target.centerX, target.right)
        val targetV = floatArrayOf(target.y, target.centerY, target.bottom)

        // Check horizontal alignment (vertical guide lines — corrects X position)
        for (mPos in movingH) {
            for (tPos in targetH) {
                val dist = abs(mPos - tPos)
                if (dist > threshold) continue

                val delta = tPos - mPos
                if (dist < bestHDist) {
                    bestHDist = dist
                    bestHDelta = delta
                    hGuides.clear()
                    hGuides.add(makeVerticalGuide(tPos, movingBounds, target))
                } else if (dist == bestHDist && delta == bestHDelta) {
                    hGuides.add(makeVerticalGuide(tPos, movingBounds, target))
                }
            }
        }

        // Check vertical alignment (horizontal guide lines — corrects Y position)
        for (mPos in movingV) {
            for (tPos in targetV) {
                val dist = abs(mPos - tPos)
                if (dist > threshold) continue

                val delta = tPos - mPos
                if (dist < bestVDist) {
                    bestVDist = dist
                    bestVDelta = delta
                    vGuides.clear()
                    vGuides.add(makeHorizontalGuide(tPos, movingBounds, target))
                } else if (dist == bestVDist && delta == bestVDelta) {
                    vGuides.add(makeHorizontalGuide(tPos, movingBounds, target))
                }
            }
        }
    }

    val snapDeltaX = if (bestHDist <= threshold) bestHDelta else 0f
    val snapDeltaY = if (bestVDist <= threshold) bestVDelta else 0f

    if (snapDeltaX == 0f && snapDeltaY == 0f && hGuides.isEmpty() && vGuides.isEmpty()) {
        return SnapResult.NONE
    }

    // Merge overlapping guides on the same axis+position into one with the widest span
    val mergedGuides = (hGuides + vGuides)
        .groupBy { it.axis to it.position }
        .map { (_, group) ->
            group.reduce { acc, g ->
                SnapGuide(acc.axis, acc.position, min(acc.spanStart, g.spanStart), max(acc.spanEnd, g.spanEnd))
            }
        }

    return SnapResult(
        snapDelta = Offset(snapDeltaX, snapDeltaY),
        guides = mergedGuides
    )
}

/**
 * Compute the union bounding box of a list of rects.
 * Returns null for an empty list.
 */
fun boundingBoxOf(rects: List<Rect>): Rect? {
    if (rects.isEmpty()) return null
    var minX = Float.MAX_VALUE
    var minY = Float.MAX_VALUE
    var maxX = Float.MIN_VALUE
    var maxY = Float.MIN_VALUE
    for (r in rects) {
        minX = min(minX, r.x)
        minY = min(minY, r.y)
        maxX = max(maxX, r.right)
        maxY = max(maxY, r.bottom)
    }
    return Rect.fromEdges(minX, minY, maxX, maxY)
}

/** Create a VERTICAL guide line at the given x position, spanning both rects vertically. */
private fun makeVerticalGuide(x: Float, a: Rect, b: Rect): SnapGuide {
    val spanStart = min(a.y, b.y)
    val spanEnd = max(a.bottom, b.bottom)
    return SnapGuide(SnapAxis.VERTICAL, x, spanStart, spanEnd)
}

/** Create a HORIZONTAL guide line at the given y position, spanning both rects horizontally. */
private fun makeHorizontalGuide(y: Float, a: Rect, b: Rect): SnapGuide {
    val spanStart = min(a.x, b.x)
    val spanEnd = max(a.right, b.right)
    return SnapGuide(SnapAxis.HORIZONTAL, y, spanStart, spanEnd)
}
