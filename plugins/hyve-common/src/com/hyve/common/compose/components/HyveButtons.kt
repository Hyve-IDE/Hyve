// Copyright 2026 Hyve. All rights reserved.
package com.hyve.common.compose.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hyve.common.compose.*
import org.jetbrains.jewel.ui.component.Text

/**
 * Primary action button — honey background, white text.
 * Used for confirm/save/apply actions.
 */
@Composable
fun HyvePrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = HyveThemeColors.colors
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    val bgColor = when {
        !enabled -> colors.textDisabled
        isHovered -> colors.honeyLight
        else -> colors.honey
    }
    val textColor = when {
        !enabled -> colors.textSecondary
        else -> Color.White
    }

    Box(
        modifier = modifier
            .clip(HyveShapes.card)
            .hoverable(interactionSource)
            .background(bgColor)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = HyveSpacing.md, vertical = HyveSpacing.smd),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = HyveTypography.itemTitle.copy(fontWeight = FontWeight.SemiBold),
            color = textColor,
        )
    }
}

/**
 * Secondary action button — slate background, primary text.
 * Used for less prominent actions alongside primary buttons.
 */
@Composable
fun HyveSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = HyveThemeColors.colors
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    val bgColor = when {
        !enabled -> colors.deepNight
        isHovered -> colors.slateLight
        else -> colors.slate
    }
    val textColor = when {
        !enabled -> colors.textDisabled
        else -> colors.textPrimary
    }

    Box(
        modifier = modifier
            .clip(HyveShapes.card)
            .hoverable(interactionSource)
            .background(bgColor)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = HyveSpacing.md, vertical = HyveSpacing.smd),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = HyveTypography.itemTitle.copy(fontWeight = FontWeight.Medium),
            color = textColor,
        )
    }
}

/**
 * Ghost button — transparent background, secondary text, border on hover.
 * Used for tertiary actions, cancel buttons, and icon-only buttons.
 */
@Composable
fun HyveGhostButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = HyveThemeColors.colors
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    val bgColor = when {
        !enabled -> Color.Transparent
        isHovered -> colors.slateLight.copy(alpha = HyveOpacity.faint)
        else -> Color.Transparent
    }
    val borderColor = when {
        !enabled -> Color.Transparent
        isHovered -> colors.slate
        else -> Color.Transparent
    }
    val textColor = when {
        !enabled -> colors.textDisabled
        isHovered -> colors.textPrimary
        else -> colors.textSecondary
    }

    Box(
        modifier = modifier
            .clip(HyveShapes.card)
            .hoverable(interactionSource)
            .border(1.dp, borderColor, HyveShapes.card)
            .background(bgColor)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = HyveSpacing.md, vertical = HyveSpacing.smd),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = HyveTypography.itemTitle.copy(fontWeight = FontWeight.Medium),
            color = textColor,
        )
    }
}

/**
 * Danger button — error background, white text.
 * Used for destructive actions like delete/remove.
 */
@Composable
fun HyveDangerButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = HyveThemeColors.colors
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    val bgColor = when {
        !enabled -> colors.textDisabled
        isHovered -> colors.error
        else -> colors.errorDark
    }
    val textColor = when {
        !enabled -> colors.textSecondary
        else -> Color.White
    }

    Box(
        modifier = modifier
            .clip(HyveShapes.card)
            .hoverable(interactionSource)
            .background(bgColor)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = HyveSpacing.md, vertical = HyveSpacing.smd),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = HyveTypography.itemTitle.copy(fontWeight = FontWeight.SemiBold),
            color = textColor,
        )
    }
}
