// Copyright 2026 Hyve. All rights reserved.
package com.hyve.common.compose.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
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
import androidx.compose.ui.unit.sp
import com.hyve.common.compose.*
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.icon.IconKey

/**
 * Section header with left accent bar, expand arrow, title, and optional count badge.
 * Generalized from the prefab GroupHeader pattern.
 */
@Composable
fun SectionHeader(
    title: String,
    accentColor: Color,
    isExpanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    iconKey: IconKey? = null,
    iconLabel: String? = null,
    count: Int? = null,
) {
    val colors = HyveThemeColors.colors
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val bgColor = if (isHovered) colors.slateLight else colors.slate

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(HyveShapes.card)
            .hoverable(interactionSource)
            .background(bgColor)
            .clickable { onToggle() }
            .padding(start = HyveSpacing.xs, end = HyveSpacing.sm, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Left accent bar
        Box(
            Modifier
                .width(3.dp)
                .height(20.dp)
                .clip(HyveShapes.accentBar)
                .background(accentColor)
        )
        Spacer(Modifier.width(6.dp))
        // Expand arrow
        Text(
            text = if (isExpanded) "\u25BE" else "\u25B8",
            style = HyveTypography.sectionHeader.copy(
                fontWeight = FontWeight.Bold,
            ),
        )
        Spacer(Modifier.width(HyveSpacing.xs))
        // Optional corpus icon — text label preferred over IconKey (which tints to solid blocks)
        if (iconLabel != null) {
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .clip(HyveShapes.badge)
                    .background(accentColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = iconLabel,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = accentColor,
                )
            }
            Spacer(Modifier.width(HyveSpacing.xs))
        } else if (iconKey != null) {
            Icon(
                key = iconKey,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = accentColor,
            )
            Spacer(Modifier.width(HyveSpacing.xs))
        }
        // Title
        Text(
            text = title,
            style = HyveTypography.sectionHeader,
            modifier = Modifier.weight(1f),
        )
        // Optional count badge
        if (count != null) {
            Box(
                modifier = Modifier
                    .clip(HyveShapes.badge)
                    .background(accentColor.copy(alpha = 0.2f))
                    .padding(horizontal = 5.dp, vertical = 1.dp),
            ) {
                Text(
                    text = "$count",
                    style = HyveTypography.badge,
                )
            }
        }
    }
}

/**
 * Rounded pill chip for filtering. Accent-colored when active, muted when inactive.
 * Hover feedback: background brightens on hover.
 */
@Composable
fun FilterChip(
    label: String,
    isActive: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    activeColor: Color = HyveThemeColors.colors.honey,
    leadingContent: (@Composable () -> Unit)? = null,
) {
    val colors = HyveThemeColors.colors
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    val bgColor = when {
        isActive && isHovered -> activeColor.copy(alpha = 0.20f)
        isActive -> activeColor.copy(alpha = 0.10f)
        isHovered -> colors.slateLight.copy(alpha = 0.5f)
        else -> Color.Transparent
    }
    val borderColor = when {
        isActive && isHovered -> activeColor.copy(alpha = 0.65f)
        isActive -> activeColor.copy(alpha = 0.45f)
        isHovered -> colors.textDisabled
        else -> colors.slateLight
    }
    val textColor = when {
        isActive -> colors.textPrimary
        isHovered -> colors.textPrimary
        else -> colors.textSecondary
    }

    Row(
        modifier = modifier
            .clip(HyveShapes.chip)
            .hoverable(interactionSource)
            .border(1.dp, borderColor, HyveShapes.chip)
            .background(bgColor)
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        leadingContent?.invoke()
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = if (isActive) FontWeight.Medium else FontWeight.Normal,
            color = textColor,
        )
    }
}

/**
 * 24dp status bar row with panel background.
 */
@Composable
fun StatusBar(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(24.dp)
            .background(JewelTheme.globalColors.panelBackground)
            .padding(horizontal = HyveSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(HyveSpacing.lg),
        content = content,
    )
}

/**
 * Centered placeholder text for empty states.
 */
@Composable
fun EmptyState(
    text: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.fillMaxWidth().padding(HyveSpacing.lg),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = HyveTypography.placeholder,
        )
    }
}
