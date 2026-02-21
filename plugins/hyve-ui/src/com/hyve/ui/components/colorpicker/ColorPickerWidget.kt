// Copyright 2026 Hyve. All rights reserved.
package com.hyve.ui.components.colorpicker

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import com.hyve.common.compose.HyveShapes
import com.hyve.common.compose.HyveSpacing
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import org.jetbrains.jewel.foundation.theme.JewelTheme

/**
 * HSV Color Wheel picker with saturation/value square and opacity slider.
 *
 * Accepts and emits Hytale color strings: `#aabbcc` or `#aabbcc(0.5)`.
 *
 * @param currentColor The current color value (hex, optionally with opacity suffix)
 * @param onColorChanged Called when color changes (does NOT close the picker)
 * @param onDismiss Called when user wants to close the picker
 */
@Composable
fun ColorPicker(
    currentColor: String,
    onColorChanged: (String) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val (initialHex, initialOpacity) = remember { splitColorOpacity(currentColor) }
    val initialHsv = remember { hexToHsv(initialHex) }

    var hue by remember { mutableStateOf(initialHsv.first) }
    var saturation by remember { mutableStateOf(initialHsv.second) }
    var value by remember { mutableStateOf(initialHsv.third) }
    var opacity by remember { mutableStateOf(initialOpacity ?: 1f) }

    var lastEmittedColor by remember { mutableStateOf(currentColor) }

    fun emitColor() {
        val hex = hsvToHex(hue, saturation, value)
        val result = formatColorWithOpacity(hex, opacity)
        if (result != lastEmittedColor) {
            lastEmittedColor = result
            onColorChanged(result)
        }
    }

    val gap = with(LocalDensity.current) { 4.dp.roundToPx() }

    val belowAnchor = remember(gap) {
        object : PopupPositionProvider {
            override fun calculatePosition(
                anchorBounds: IntRect,
                windowSize: IntSize,
                layoutDirection: LayoutDirection,
                popupContentSize: IntSize
            ): IntOffset {
                val x = anchorBounds.left
                val y = anchorBounds.bottom + gap
                // If it would overflow the bottom, flip above
                val adjustedY = if (y + popupContentSize.height > windowSize.height) {
                    anchorBounds.top - popupContentSize.height - gap
                } else {
                    y
                }
                return IntOffset(x, adjustedY)
            }
        }
    }

    Popup(
        popupPositionProvider = belowAnchor,
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true),
    ) {
        Box(
            modifier = modifier
                .width(280.dp)
                .background(JewelTheme.globalColors.panelBackground, HyveShapes.dialog)
        ) {
            Column(
                modifier = Modifier.padding(HyveSpacing.md),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Saturation/Value square with current hue
                SaturationValuePicker(
                    hue = hue,
                    saturation = saturation,
                    value = value,
                    onSaturationValueChange = { s, v ->
                        saturation = s
                        value = v
                        emitColor()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                )

                Spacer(modifier = Modifier.height(HyveSpacing.md))

                // Hue slider bar
                HueSlider(
                    hue = hue,
                    onHueChange = { newHue ->
                        hue = newHue
                        emitColor()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(24.dp)
                )

                Spacer(modifier = Modifier.height(HyveSpacing.sm))

                // Opacity slider
                OpacitySlider(
                    opacity = opacity,
                    hue = hue,
                    saturation = saturation,
                    value = value,
                    onOpacityChange = { newOpacity ->
                        opacity = newOpacity
                        emitColor()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(24.dp)
                )
            }
        }
    }
}

/**
 * Saturation/Value picker square - X axis is saturation, Y axis is value (inverted).
 */
@Composable
fun SaturationValuePicker(
    hue: Float,
    saturation: Float,
    value: Float,
    onSaturationValueChange: (Float, Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val currentCallback by rememberUpdatedState(onSaturationValueChange)

    Box(modifier = modifier) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .clip(HyveShapes.dialog)
                .pointerInput(Unit) {
                    detectTapGestures { offset ->
                        val s = (offset.x / size.width).coerceIn(0f, 1f)
                        val v = 1f - (offset.y / size.height).coerceIn(0f, 1f)
                        currentCallback(s, v)
                    }
                }
                .pointerInput(Unit) {
                    detectDragGestures { change, _ ->
                        change.consume()
                        val s = (change.position.x / size.width).coerceIn(0f, 1f)
                        val v = 1f - (change.position.y / size.height).coerceIn(0f, 1f)
                        currentCallback(s, v)
                    }
                }
        ) {
            val hueColor = Color.hsv(hue, 1f, 1f)

            drawRect(color = hueColor)

            drawRect(
                brush = Brush.horizontalGradient(
                    colors = listOf(Color.White, Color.Transparent)
                )
            )

            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(Color.Transparent, Color.Black)
                )
            )

            val selectorX = saturation * size.width
            val selectorY = (1f - value) * size.height
            val selectorRadius = 8.dp.toPx()

            drawCircle(
                color = Color.White,
                radius = selectorRadius,
                center = Offset(selectorX, selectorY),
                style = Stroke(width = 3.dp.toPx())
            )
            drawCircle(
                color = Color.Black,
                radius = selectorRadius - 1.5.dp.toPx(),
                center = Offset(selectorX, selectorY),
                style = Stroke(width = 1.dp.toPx())
            )
        }
    }
}

/**
 * Horizontal hue slider showing the full color spectrum.
 */
@Composable
fun HueSlider(
    hue: Float,
    onHueChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val currentCallback by rememberUpdatedState(onHueChange)

    Box(modifier = modifier) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .clip(HyveShapes.card)
                .pointerInput(Unit) {
                    detectTapGestures { offset ->
                        val newHue = (offset.x / size.width).coerceIn(0f, 1f) * 360f
                        currentCallback(newHue)
                    }
                }
                .pointerInput(Unit) {
                    detectDragGestures { change, _ ->
                        change.consume()
                        val newHue = (change.position.x / size.width).coerceIn(0f, 1f) * 360f
                        currentCallback(newHue)
                    }
                }
        ) {
            val hueColors = (0..360 step 30).map { h -> Color.hsv(h.toFloat(), 1f, 1f) }
            drawRect(
                brush = Brush.horizontalGradient(colors = hueColors)
            )

            val selectorX = (hue / 360f) * size.width
            val selectorWidth = 6.dp.toPx()

            drawRect(
                color = Color.White,
                topLeft = Offset(selectorX - selectorWidth / 2, 0f),
                size = Size(selectorWidth, size.height),
                style = Stroke(width = 2.dp.toPx())
            )
            drawRect(
                color = Color.Black,
                topLeft = Offset(selectorX - selectorWidth / 2 + 1.dp.toPx(), 1.dp.toPx()),
                size = Size(selectorWidth - 2.dp.toPx(), size.height - 2.dp.toPx()),
                style = Stroke(width = 1.dp.toPx())
            )
        }
    }
}

/**
 * Horizontal opacity slider showing transparent → opaque gradient of the current color.
 * Renders a checkerboard pattern behind the gradient to visualize transparency.
 */
@Composable
fun OpacitySlider(
    opacity: Float,
    hue: Float,
    saturation: Float,
    value: Float,
    onOpacityChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val currentCallback by rememberUpdatedState(onOpacityChange)
    val solidColor = Color.hsv(hue, saturation, value)

    Box(modifier = modifier) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .clip(HyveShapes.card)
                .pointerInput(Unit) {
                    detectTapGestures { offset ->
                        currentCallback((offset.x / size.width).coerceIn(0f, 1f))
                    }
                }
                .pointerInput(Unit) {
                    detectDragGestures { change, _ ->
                        change.consume()
                        currentCallback((change.position.x / size.width).coerceIn(0f, 1f))
                    }
                }
        ) {
            // Checkerboard pattern to show transparency
            val checkSize = 6.dp.toPx()
            val lightCheck = Color(0xFFCCCCCC)
            val darkCheck = Color(0xFF999999)
            var cx = 0f
            while (cx < size.width) {
                var cy = 0f
                while (cy < size.height) {
                    val isLight = ((cx / checkSize).toInt() + (cy / checkSize).toInt()) % 2 == 0
                    drawRect(
                        color = if (isLight) lightCheck else darkCheck,
                        topLeft = Offset(cx, cy),
                        size = Size(checkSize, checkSize)
                    )
                    cy += checkSize
                }
                cx += checkSize
            }

            // Gradient: transparent → solid color
            drawRect(
                brush = Brush.horizontalGradient(
                    colors = listOf(solidColor.copy(alpha = 0f), solidColor)
                )
            )

            // Selector handle
            val selectorX = opacity * size.width
            val selectorWidth = 6.dp.toPx()

            drawRect(
                color = Color.White,
                topLeft = Offset(selectorX - selectorWidth / 2, 0f),
                size = Size(selectorWidth, size.height),
                style = Stroke(width = 2.dp.toPx())
            )
            drawRect(
                color = Color.Black,
                topLeft = Offset(selectorX - selectorWidth / 2 + 1.dp.toPx(), 1.dp.toPx()),
                size = Size(selectorWidth - 2.dp.toPx(), size.height - 2.dp.toPx()),
                style = Stroke(width = 1.dp.toPx())
            )
        }
    }
}
