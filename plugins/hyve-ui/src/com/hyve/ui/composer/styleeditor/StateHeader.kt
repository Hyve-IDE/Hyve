// Copyright 2026 Hyve. All rights reserved.
package com.hyve.ui.composer.styleeditor

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hyve.common.compose.HyveShapes
import com.hyve.common.compose.HyveSpacing
import com.hyve.common.compose.HyveThemeColors
import com.hyve.common.compose.HyveTypography
import com.hyve.common.compose.components.HyveChevronDownIcon
import org.jetbrains.jewel.ui.component.Text

/**
 * Collapsible state section header for the style editor (FR-3).
 *
 * Shows: collapse arrow (animated rotation) | state name | filled/total count.
 * Mirrors [com.hyve.ui.composer.propertyform.CategoryHeader] visually but
 * uses string state names in mixed case (not uppercase categories).
 *
 * @param stateName The visual state name (e.g. "Default", "Hovered")
 * @param filledCount Number of non-empty slots in this state
 * @param totalCount Total number of slots in this state
 * @param isCollapsed Whether this state section is currently collapsed
 * @param onToggle Called when the header is clicked to toggle collapse state
 * @param modifier Modifier for the outer row
 */
@Composable
fun StateHeader(
    stateName: String,
    filledCount: Int,
    totalCount: Int,
    isCollapsed: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = HyveThemeColors.colors
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val textColor = if (isHovered) colors.textPrimary else colors.textSecondary

    val arrowRotation by animateFloatAsState(
        targetValue = if (isCollapsed) -90f else 0f,
        animationSpec = tween(durationMillis = 150),
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(HyveShapes.card)
            .hoverable(interactionSource)
            .clickable(onClick = onToggle)
            .padding(horizontal = HyveSpacing.xs, vertical = HyveSpacing.smd),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(HyveSpacing.smd),
    ) {
        // Collapse arrow
        HyveChevronDownIcon(
            color = textColor,
            modifier = Modifier.size(10.dp).rotate(arrowRotation),
        )

        // State name (mixed case, not uppercased)
        Text(
            text = stateName,
            color = textColor,
            style = HyveTypography.caption.copy(fontWeight = FontWeight.SemiBold),
            modifier = Modifier.weight(1f),
        )

        // Filled / total count
        Text(
            text = "$filledCount/$totalCount",
            color = colors.textDisabled,
            style = HyveTypography.badge,
        )
    }
}
