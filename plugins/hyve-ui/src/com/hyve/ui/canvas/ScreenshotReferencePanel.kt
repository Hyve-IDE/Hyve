// Copyright 2026 Hyve. All rights reserved.
package com.hyve.ui.canvas

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hyve.common.compose.HyveOpacity
import com.hyve.common.compose.HyveThemeColors
import com.hyve.common.compose.HyveShapes
import com.hyve.common.compose.HyveSpacing
import com.hyve.common.compose.HyveTypography
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Text

/**
 * Screenshot reference controls panel shown in the property inspector
 * when the Root element is selected.
 *
 * Provides:
 * - Toggle to show/hide the screenshot overlay (B hotkey)
 * - Opacity slider
 * - HUD / No-HUD mode toggle (Shift+B hotkey)
 */
@Composable
fun ScreenshotReferencePanel(
    canvasState: CanvasState,
    modifier: Modifier = Modifier
) {
    val colors = HyveThemeColors.colors
    val showScreenshot = canvasState.showScreenshot.value
    val opacity = canvasState.screenshotOpacity.value
    val mode = canvasState.screenshotMode.value

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(HyveShapes.dialog)
            .border(1.dp, colors.slate, HyveShapes.dialog)
            .background(colors.deepNight, HyveShapes.dialog)
            .padding(HyveSpacing.md),
        verticalArrangement = Arrangement.spacedBy(HyveSpacing.mld)
    ) {
        // Header row with title and toggle
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Screenshot Reference",
                color = colors.textPrimary,
                style = HyveTypography.itemTitle.copy(fontWeight = FontWeight.SemiBold)
            )

            // Toggle pill
            TogglePill(
                checked = showScreenshot,
                onCheckedChange = { canvasState.setShowScreenshot(it) },
                label = if (showScreenshot) "ON" else "OFF"
            )
        }

        // Hint for hotkeys
        Text(
            text = "B toggle \u2022 Shift+B switch mode",
            color = colors.textDisabled,
            style = HyveTypography.badge
        )

        // Mode selector (only visible when screenshot is on)
        if (showScreenshot) {
            // Mode toggle row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(HyveSpacing.smd)
            ) {
                for (m in ScreenshotMode.entries) {
                    ModeButton(
                        label = m.label,
                        selected = mode == m,
                        onClick = { canvasState.setScreenshotMode(m) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Opacity slider
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(HyveSpacing.sm)
            ) {
                Text(
                    text = "Opacity",
                    color = colors.textSecondary,
                    style = HyveTypography.caption
                )

                OpacitySlider(
                    value = opacity,
                    onValueChange = { canvasState.setScreenshotOpacity(it) },
                    modifier = Modifier.weight(1f).height(20.dp)
                )

                Text(
                    text = "${(opacity * 100).toInt()}%",
                    color = colors.textSecondary,
                    style = HyveTypography.caption
                )
            }
        }
    }
}

/**
 * Small toggle pill (ON/OFF) styled with Hyve theme.
 */
@Composable
private fun TogglePill(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    label: String
) {
    val colors = HyveThemeColors.colors
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    val bgColor = when {
        checked -> colors.honey.copy(alpha = HyveOpacity.medium)
        isHovered -> colors.slate.copy(alpha = HyveOpacity.muted)
        else -> colors.slate.copy(alpha = HyveOpacity.strong)
    }
    val borderColor = when {
        checked -> colors.honey
        isHovered -> colors.slateLight
        else -> colors.slate
    }
    val textColor = when {
        checked -> colors.honey
        isHovered -> colors.textSecondary
        else -> colors.textDisabled
    }

    Box(
        modifier = Modifier
            .clip(HyveShapes.dialog)
            .border(1.dp, borderColor, HyveShapes.dialog)
            .background(bgColor, HyveShapes.dialog)
            .hoverable(interactionSource)
            .clickable { onCheckedChange(!checked) }
            .padding(horizontal = HyveSpacing.mld, vertical = 3.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = textColor,
            style = HyveTypography.badge.copy(fontWeight = FontWeight.SemiBold)
        )
    }
}

/**
 * Mode selection button for HUD / No-HUD.
 */
@Composable
private fun ModeButton(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = HyveThemeColors.colors
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    val bgColor = when {
        selected -> colors.honey.copy(alpha = HyveOpacity.medium)
        isHovered -> colors.slate.copy(alpha = HyveOpacity.muted)
        else -> Color.Transparent
    }
    val borderColor = if (selected) colors.honey else colors.slate
    val textColor = if (selected) colors.honey else colors.textSecondary

    Box(
        modifier = modifier
            .clip(HyveShapes.card)
            .border(1.dp, borderColor, HyveShapes.card)
            .background(bgColor, HyveShapes.card)
            .hoverable(interactionSource)
            .clickable(onClick = onClick)
            .padding(vertical = HyveSpacing.smd),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = textColor,
            style = TextStyle(fontSize = 11.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
        )
    }
}

/**
 * Custom opacity slider with Hyve honey accent.
 */
@Composable
private fun OpacitySlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = HyveThemeColors.colors
    val trackColor = colors.slate
    val activeColor = colors.honey
    val thumbColor = colors.honey

    Canvas(
        modifier = modifier
            .clip(HyveShapes.card)
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    val newValue = (offset.x / size.width).coerceIn(0f, 1f)
                    onValueChange(newValue)
                }
            }
            .pointerInput(Unit) {
                detectDragGestures { change, _ ->
                    change.consume()
                    val newValue = (change.position.x / size.width).coerceIn(0f, 1f)
                    onValueChange(newValue)
                }
            }
    ) {
        val trackHeight = 4.dp.toPx()
        val trackY = (size.height - trackHeight) / 2

        // Background track
        drawRoundRect(
            color = trackColor,
            topLeft = Offset(0f, trackY),
            size = Size(size.width, trackHeight),
            cornerRadius = CornerRadius(2.dp.toPx())
        )

        // Active track
        drawRoundRect(
            color = activeColor,
            topLeft = Offset(0f, trackY),
            size = Size(size.width * value, trackHeight),
            cornerRadius = CornerRadius(2.dp.toPx())
        )

        // Thumb
        val thumbRadius = 6.dp.toPx()
        val thumbX = size.width * value
        drawCircle(
            color = thumbColor,
            radius = thumbRadius,
            center = Offset(thumbX.coerceIn(thumbRadius, size.width - thumbRadius), size.height / 2)
        )
    }
}
