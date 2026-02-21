// Copyright 2026 Hyve. All rights reserved.
package com.hyve.common.compose.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

/**
 * Canvas-drawn icons for consistent rendering across platforms.
 * All icons default to 12dp and accept a color + modifier override.
 */

/** Two diagonal lines forming an X. */
@Composable
fun HyveCloseIcon(color: Color, modifier: Modifier = Modifier.size(12.dp)) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val sw = 1.4.dp.toPx()
        val pad = w * 0.15f
        drawLine(color, Offset(pad, pad), Offset(w - pad, h - pad), sw, StrokeCap.Round)
        drawLine(color, Offset(w - pad, pad), Offset(pad, h - pad), sw, StrokeCap.Round)
    }
}

/** Two lines meeting at bottom center forming a V / chevron-down. */
@Composable
fun HyveChevronDownIcon(color: Color, modifier: Modifier = Modifier.size(12.dp)) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val sw = 1.4.dp.toPx()
        drawLine(color, Offset(w * 0.2f, h * 0.3f), Offset(w * 0.5f, h * 0.7f), sw, StrokeCap.Round)
        drawLine(color, Offset(w * 0.5f, h * 0.7f), Offset(w * 0.8f, h * 0.3f), sw, StrokeCap.Round)
    }
}

/** Filled right-pointing triangle. */
@Composable
fun HyveTriangleRightIcon(color: Color, modifier: Modifier = Modifier.size(12.dp)) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val path = Path().apply {
            moveTo(w * 0.25f, h * 0.15f)
            lineTo(w * 0.8f, h * 0.5f)
            lineTo(w * 0.25f, h * 0.85f)
            close()
        }
        drawPath(path, color)
    }
}

/** Filled down-pointing triangle. */
@Composable
fun HyveTriangleDownIcon(color: Color, modifier: Modifier = Modifier.size(12.dp)) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val path = Path().apply {
            moveTo(w * 0.15f, h * 0.25f)
            lineTo(w * 0.85f, h * 0.25f)
            lineTo(w * 0.5f, h * 0.8f)
            close()
        }
        drawPath(path, color)
    }
}

/** Stroked rotated square (diamond outline). */
@Composable
fun HyveDiamondOutlineIcon(color: Color, modifier: Modifier = Modifier.size(12.dp)) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val sw = 1.4.dp.toPx()
        val path = Path().apply {
            moveTo(w * 0.5f, h * 0.1f)
            lineTo(w * 0.9f, h * 0.5f)
            lineTo(w * 0.5f, h * 0.9f)
            lineTo(w * 0.1f, h * 0.5f)
            close()
        }
        drawPath(path, color, style = Stroke(width = sw))
    }
}

/** Filled rotated square (diamond filled). */
@Composable
fun HyveDiamondFilledIcon(color: Color, modifier: Modifier = Modifier.size(12.dp)) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val path = Path().apply {
            moveTo(w * 0.5f, h * 0.1f)
            lineTo(w * 0.9f, h * 0.5f)
            lineTo(w * 0.5f, h * 0.9f)
            lineTo(w * 0.1f, h * 0.5f)
            close()
        }
        drawPath(path, color)
    }
}

/** Stroked rectangle (square outline). */
@Composable
fun HyveSquareOutlineIcon(color: Color, modifier: Modifier = Modifier.size(12.dp)) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val sw = 1.4.dp.toPx()
        val pad = w * 0.15f
        drawRect(
            color = color,
            topLeft = Offset(pad, pad),
            size = androidx.compose.ui.geometry.Size(w - pad * 2, h - pad * 2),
            style = Stroke(width = sw),
        )
    }
}

/** Two-segment checkmark. */
@Composable
fun HyveCheckmarkIcon(color: Color, modifier: Modifier = Modifier.size(12.dp)) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val sw = 1.6.dp.toPx()
        drawLine(color, Offset(w * 0.15f, h * 0.5f), Offset(w * 0.4f, h * 0.75f), sw, StrokeCap.Round)
        drawLine(color, Offset(w * 0.4f, h * 0.75f), Offset(w * 0.85f, h * 0.25f), sw, StrokeCap.Round)
    }
}

/** Two diagonal lines forming a smaller X (cross mark). */
@Composable
fun HyveCrossMarkIcon(color: Color, modifier: Modifier = Modifier.size(12.dp)) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val sw = 1.6.dp.toPx()
        val pad = w * 0.2f
        drawLine(color, Offset(pad, pad), Offset(w - pad, h - pad), sw, StrokeCap.Round)
        drawLine(color, Offset(w - pad, pad), Offset(pad, h - pad), sw, StrokeCap.Round)
    }
}

/** Horizontal line with arrowhead (right arrow). */
@Composable
fun HyveRightArrowIcon(color: Color, modifier: Modifier = Modifier.size(12.dp)) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val sw = 1.4.dp.toPx()
        // Shaft
        drawLine(color, Offset(w * 0.1f, h * 0.5f), Offset(w * 0.85f, h * 0.5f), sw, StrokeCap.Round)
        // Arrowhead
        drawLine(color, Offset(w * 0.6f, h * 0.25f), Offset(w * 0.85f, h * 0.5f), sw, StrokeCap.Round)
        drawLine(color, Offset(w * 0.85f, h * 0.5f), Offset(w * 0.6f, h * 0.75f), sw, StrokeCap.Round)
    }
}

/** Document with folded corner (file icon). */
@Composable
fun HyveFileDocIcon(color: Color, modifier: Modifier = Modifier.size(14.dp)) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val sw = 1.4.dp.toPx()
        val fold = w * 0.3f
        val path = Path().apply {
            moveTo(w * 0.15f, h * 0.05f)
            lineTo(w * 0.85f - fold, h * 0.05f)
            lineTo(w * 0.85f, h * 0.05f + fold)
            lineTo(w * 0.85f, h * 0.95f)
            lineTo(w * 0.15f, h * 0.95f)
            close()
        }
        drawPath(path, color, style = Stroke(width = sw))
        // Fold line
        drawLine(color, Offset(w * 0.85f - fold, h * 0.05f), Offset(w * 0.85f - fold, h * 0.05f + fold), sw)
        drawLine(color, Offset(w * 0.85f - fold, h * 0.05f + fold), Offset(w * 0.85f, h * 0.05f + fold), sw)
    }
}
