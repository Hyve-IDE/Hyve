// Copyright 2026 Hyve. All rights reserved.
package com.hyve.ui.composer.propertyform

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hyve.common.compose.HyveOpacity
import com.hyve.common.compose.HyveShapes
import com.hyve.common.compose.HyveSpacing
import com.hyve.common.compose.HyveThemeColors
import com.hyve.common.compose.HyveTypography
import com.hyve.common.compose.components.HyveChevronDownIcon
import com.hyve.ui.composer.model.PropertySlot
import com.hyve.ui.composer.model.SlotCategory
import org.jetbrains.jewel.ui.component.Text

/**
 * Collapsible category section header (FR-1).
 *
 * Shows: collapse arrow (animated rotation) | uppercase category label | count pill or plain count.
 *
 * When hidden properties exist, the count is rendered as a honey-tinted pill badge
 * with "+" appended (e.g. "1/19 +"). Clicking the pill opens [AddPropertyPopup].
 * When all properties are visible, the count shows as plain disabled text.
 *
 * @param category The slot category
 * @param filledCount Number of non-empty slots in this category
 * @param totalCount Total number of slots in this category
 * @param isCollapsed Whether this category is currently collapsed
 * @param onToggle Called when the header is clicked to toggle collapse state
 * @param unsetSlots Slots that are currently hidden (empty and not revealed)
 * @param onRevealSlot Called when a user picks a property from the "+" popup
 * @param modifier Modifier for the outer row
 */
@Composable
fun CategoryHeader(
    category: SlotCategory,
    filledCount: Int,
    totalCount: Int,
    isCollapsed: Boolean,
    onToggle: () -> Unit,
    unsetSlots: List<PropertySlot> = emptyList(),
    onRevealSlot: (String) -> Unit = {},
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

    // Popup state for the pill badge
    var showAddPopup by remember { mutableStateOf(false) }

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

        // Category label
        Text(
            text = category.displayName.uppercase(),
            color = textColor,
            style = HyveTypography.badge.copy(
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.5.sp,
            ),
            modifier = Modifier.weight(1f),
        )

        if (unsetSlots.isNotEmpty()) {
            // Pill badge: "filled/total +" — interactive, honey-tinted
            val pillInteraction = remember { MutableInteractionSource() }
            val pillHovered by pillInteraction.collectIsHoveredAsState()

            val pillShape = HyveShapes.chip
            val textAlpha = if (pillHovered) 1f else HyveOpacity.muted
            val bgAlpha = if (pillHovered) HyveOpacity.light else HyveOpacity.subtle
            val borderAlpha = if (pillHovered) HyveOpacity.strong else HyveOpacity.medium

            Box {
                Box(
                    modifier = Modifier
                        .clip(pillShape)
                        .background(colors.honey.copy(alpha = bgAlpha))
                        .border(1.dp, colors.honey.copy(alpha = borderAlpha), pillShape)
                        .hoverable(pillInteraction)
                        .clickable { showAddPopup = true }
                        .padding(horizontal = HyveSpacing.smd, vertical = 1.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = if (filledCount == 0) "+" else "$filledCount/$totalCount +",
                        color = colors.honey.copy(alpha = textAlpha),
                        style = HyveTypography.badge.copy(fontWeight = FontWeight.SemiBold),
                    )
                }

                if (showAddPopup) {
                    AddPropertyPopup(
                        unsetSlots = unsetSlots,
                        onSelect = { slotName ->
                            onRevealSlot(slotName)
                            showAddPopup = false
                        },
                        onDismissRequest = { showAddPopup = false },
                    )
                }
            }
        } else {
            // Plain count — all properties visible
            Text(
                text = "$filledCount",
                color = colors.textDisabled,
                style = HyveTypography.badge,
            )
        }
    }
}
