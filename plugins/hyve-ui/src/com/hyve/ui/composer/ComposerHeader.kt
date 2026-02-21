// Copyright 2026 Hyve. All rights reserved.
package com.hyve.ui.composer

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hyve.common.compose.HyveOpacity
import com.hyve.common.compose.HyveShapes
import com.hyve.common.compose.HyveSpacing
import com.hyve.common.compose.HyveThemeColors
import com.hyve.common.compose.HyveTypography
import com.hyve.common.compose.components.HyveCloseIcon
import org.jetbrains.jewel.ui.component.Text

/**
 * Header bar for the Property Composer modal.
 *
 * Displays element metadata (type badge, editable ID, fill count) on the left,
 * and close button on the right.
 *
 * ## Spec Reference
 * - FR-3: Header Bar
 * - FR-4: Element ID Editing
 */
@Composable
fun ComposerHeader(
    elementType: String,
    elementId: String,
    filledCount: Int,
    totalSlots: Int,
    editingId: Boolean,
    onIdClick: () -> Unit,
    onIdChange: (String) -> Unit,
    onIdCommit: () -> Unit,
    onIdCancel: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = HyveThemeColors.colors

    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(colors.deepNight)
            .padding(horizontal = HyveSpacing.lg, vertical = HyveSpacing.mld),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left section: type badge + ID + fill count
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(HyveSpacing.mld),
            modifier = Modifier.weight(1f)
        ) {
            // Element type badge
            Box(
                modifier = Modifier
                    .background(colors.honeySubtle, HyveShapes.card)
                    .padding(horizontal = HyveSpacing.sm, vertical = HyveSpacing.inputVPad)
            ) {
                Text(
                    text = elementType,
                    color = colors.honey,
                    style = HyveTypography.sectionHeader.copy(fontWeight = FontWeight.SemiBold)
                )
            }

            // Element ID (editable)
            if (editingId) {
                IdEditField(
                    initialValue = elementId,
                    onCommit = onIdCommit,
                    onCancel = onIdCancel,
                    onChange = onIdChange
                )
            } else {
                IdBadge(
                    id = elementId,
                    onClick = onIdClick
                )
            }

            // Filled count badge
            Text(
                text = "$filledCount/$totalSlots filled",
                color = colors.textSecondary,
                style = HyveTypography.itemTitle
            )
        }

        // Right section: close
        CloseButton(onClick = onClose)
    }
}

/**
 * The clickable `#id` badge that triggers inline editing.
 */
@Composable
private fun IdBadge(
    id: String,
    onClick: () -> Unit
) {
    val colors = HyveThemeColors.colors
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    val borderColor = if (isHovered) colors.accent else colors.accent.copy(alpha = HyveOpacity.muted)

    Box(
        modifier = Modifier
            .hoverable(interactionSource)
            .clip(HyveShapes.card)
            .border(
                width = 1.dp,
                color = borderColor,
                shape = HyveShapes.card
            )
            .clickable(onClick = onClick)
            .padding(horizontal = HyveSpacing.sm, vertical = HyveSpacing.inputVPad)
    ) {
        Text(
            text = "#${id.ifBlank { "unnamed" }}",
            color = colors.accent,
            style = HyveTypography.sectionHeader
        )
    }
}

/**
 * Inline text field for editing the element ID.
 * Enter commits, Escape cancels.
 */
@Composable
private fun IdEditField(
    initialValue: String,
    onCommit: () -> Unit,
    onCancel: () -> Unit,
    onChange: (String) -> Unit
) {
    val colors = HyveThemeColors.colors
    var text by remember { mutableStateOf(initialValue) }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Box(
        modifier = Modifier
            .width(160.dp)
            .border(1.dp, colors.accent, HyveShapes.card)
            .padding(horizontal = HyveSpacing.sm, vertical = HyveSpacing.xs)
    ) {
        BasicTextField(
            value = text,
            onValueChange = {
                text = it
                onChange(it)
            },
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester)
                .onPreviewKeyEvent { event ->
                    when {
                        event.key == Key.Enter && event.type == KeyEventType.KeyDown -> {
                            onChange(text)
                            onCommit()
                            true
                        }
                        event.key == Key.Escape && event.type == KeyEventType.KeyDown -> {
                            onCancel()
                            true
                        }
                        else -> false
                    }
                },
            singleLine = true,
            textStyle = HyveTypography.sectionHeader.copy(color = colors.textPrimary),
            cursorBrush = SolidColor(colors.accent)
        )
        if (text.isEmpty()) {
            Text(
                text = "ElementId",
                color = colors.textDisabled,
                style = HyveTypography.sectionHeader
            )
        }
    }
}

/**
 * The ✕ close button.
 */
@Composable
private fun CloseButton(onClick: () -> Unit) {
    val colors = HyveThemeColors.colors
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    Box(
        modifier = Modifier
            .hoverable(interactionSource)
            .clip(HyveShapes.card)
            .clickable(onClick = onClick)
            .padding(horizontal = HyveSpacing.smd, vertical = HyveSpacing.xs),
        contentAlignment = Alignment.Center,
    ) {
        HyveCloseIcon(color = if (isHovered) colors.textPrimary else colors.textSecondary)
    }
}
