// Copyright 2026 Hyve. All rights reserved.
package com.hyve.ui.composer

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hyve.common.compose.HyveShapes
import com.hyve.common.compose.HyveSpacing
import com.hyve.common.compose.HyveThemeColors
import com.hyve.common.compose.HyveTypography
import com.hyve.common.compose.components.HyveCloseIcon
import com.hyve.common.compose.components.HyveDiamondFilledIcon
import com.hyve.common.compose.components.HyveDiamondOutlineIcon
import org.jetbrains.jewel.ui.component.Text

/**
 * Horizontal tab strip for switching between the element form and open style editors.
 *
 * Only renders when at least one style tab is open. The element tab is always
 * first and cannot be closed.
 *
 * ## Spec Reference
 * - FR-6: Tab Bar
 * - FR-7: Style Tab Lifecycle
 */
@Composable
fun ComposerTabBar(
    elementType: String,
    elementId: String,
    openStyleTabs: List<String>,
    activeTab: String?,
    onTabSelect: (String?) -> Unit,
    onTabClose: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = HyveThemeColors.colors
    val scrollState = rememberScrollState()

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.deepNight)
            .horizontalScroll(scrollState)
            .padding(horizontal = HyveSpacing.sm),
        verticalAlignment = Alignment.Bottom
    ) {
        // Element tab (always first, not closable)
        TabItem(
            icon = { HyveDiamondOutlineIcon(color = if (activeTab == null) colors.honey else colors.textDisabled) },
            label = "$elementType #${elementId.ifBlank { "unnamed" }}",
            isActive = activeTab == null,
            accentColor = colors.honey,
            onClick = { onTabSelect(null) },
            onClose = null // Not closable
        )

        // Style tabs
        for (styleName in openStyleTabs) {
            val isActive = activeTab == styleName
            TabItem(
                icon = { HyveDiamondFilledIcon(color = if (isActive) colors.info else colors.textDisabled) },
                label = styleName,
                isActive = isActive,
                accentColor = colors.info,
                onClick = { onTabSelect(styleName) },
                onClose = { onTabClose(styleName) }
            )
        }
    }
}

/**
 * A single tab in the tab bar.
 */
@Composable
private fun TabItem(
    icon: @Composable () -> Unit,
    label: String,
    isActive: Boolean,
    accentColor: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit,
    onClose: (() -> Unit)?
) {
    val colors = HyveThemeColors.colors
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    val textColor = when {
        isActive -> colors.textPrimary
        isHovered -> colors.textSecondary
        else -> colors.textDisabled
    }

    val bottomBorderColor = if (isActive) accentColor else androidx.compose.ui.graphics.Color.Transparent

    Row(
        modifier = Modifier
            .hoverable(interactionSource)
            .clickable(onClick = onClick)
            .drawBehind {
                // Bottom border indicator
                drawLine(
                    color = bottomBorderColor,
                    start = Offset(0f, size.height),
                    end = Offset(size.width, size.height),
                    strokeWidth = 2.dp.toPx()
                )
            }
            .padding(horizontal = HyveSpacing.md, vertical = HyveSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(HyveSpacing.smd)
    ) {
        // Icon
        icon()

        // Label
        Text(
            text = label,
            color = textColor,
            style = HyveTypography.itemTitle.copy(
                fontWeight = if (isActive) FontWeight.Medium else FontWeight.Normal
            )
        )

        // Close button (only for closable tabs)
        if (onClose != null) {
            TabCloseButton(onClick = onClose)
        }
    }
}

/**
 * Small ✕ button for closing a style tab.
 */
@Composable
private fun TabCloseButton(onClick: () -> Unit) {
    val colors = HyveThemeColors.colors
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    Box(
        modifier = Modifier
            .size(16.dp)
            .hoverable(interactionSource)
            .clip(HyveShapes.small)
            .clickable(onClick = {
                // Stop propagation — closing shouldn't also activate the tab
                onClick()
            })
            .padding(HyveSpacing.xxs),
        contentAlignment = Alignment.Center
    ) {
        HyveCloseIcon(
            color = if (isHovered) colors.error else colors.textSecondary,
            modifier = Modifier.size(8.dp),
        )
    }
}
