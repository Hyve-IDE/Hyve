// Copyright 2026 Hyve. All rights reserved.
package com.hyve.ui.composer.styleeditor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hyve.common.compose.HyveShapes
import com.hyve.common.compose.HyveSpacing
import com.hyve.common.compose.HyveThemeColors
import com.hyve.common.compose.HyveTypography
import com.hyve.common.compose.components.HyveGhostButton
import org.jetbrains.jewel.ui.component.Text

/**
 * "+ State" button and inline form for adding custom visual states (FR-5).
 *
 * Default: a dashed-border button reading "+ STATE".
 * Active: an inline form with text input, Add button, and Cancel button.
 * Validates: trims whitespace, prevents duplicate state names, Enter/Escape keys.
 *
 * @param existingStateNames Names of states that already exist (for duplicate prevention)
 * @param onAddState Called with the new state name when confirmed
 * @param modifier Modifier for the outer container
 */
@Composable
fun AddStateSection(
    existingStateNames: Set<String>,
    onAddState: (name: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = HyveThemeColors.colors
    var showingForm by remember { mutableStateOf(false) }
    var nameBuffer by remember { mutableStateOf("") }

    if (!showingForm) {
        AddStateButton(
            onClick = {
                nameBuffer = ""
                showingForm = true
            },
            modifier = modifier,
        )
    } else {
        AddStateInlineForm(
            nameBuffer = nameBuffer,
            onNameChange = { nameBuffer = it },
            existingStateNames = existingStateNames,
            onConfirm = {
                val trimmed = nameBuffer.trim()
                if (trimmed.isNotEmpty() && trimmed !in existingStateNames) {
                    onAddState(trimmed)
                    nameBuffer = ""
                    showingForm = false
                }
            },
            onCancel = {
                nameBuffer = ""
                showingForm = false
            },
            modifier = modifier,
        )
    }
}

@Composable
private fun AddStateButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = HyveThemeColors.colors
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val borderColor = if (isHovered) colors.info else colors.textDisabled

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = HyveSpacing.xs, vertical = HyveSpacing.sm)
            .clip(HyveShapes.dialog)
            .border(
                width = 1.dp,
                color = borderColor,
                shape = HyveShapes.dialog,
            )
            .hoverable(interactionSource)
            .clickable(onClick = onClick)
            .padding(vertical = HyveSpacing.sm),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "+ STATE",
            color = if (isHovered) colors.info else colors.textDisabled,
            style = HyveTypography.badge.copy(
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.8.sp,
            ),
        )
    }
}

@Composable
private fun AddStateInlineForm(
    nameBuffer: String,
    onNameChange: (String) -> Unit,
    existingStateNames: Set<String>,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = HyveThemeColors.colors
    val focusRequester = remember { FocusRequester() }
    val trimmed = nameBuffer.trim()
    val isDuplicate = trimmed in existingStateNames
    val canSubmit = trimmed.isNotEmpty() && !isDuplicate

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = HyveSpacing.xs, vertical = HyveSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(HyveSpacing.sm),
    ) {
        // Text input
        BasicTextField(
            value = nameBuffer,
            onValueChange = onNameChange,
            singleLine = true,
            textStyle = HyveTypography.itemTitle.copy(color = colors.textPrimary),
            cursorBrush = SolidColor(colors.info),
            modifier = Modifier
                .weight(1f)
                .clip(HyveShapes.card)
                .background(colors.deepNight)
                .border(1.dp, colors.info, HyveShapes.card)
                .focusRequester(focusRequester)
                .onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    when (event.key) {
                        Key.Enter -> {
                            if (canSubmit) onConfirm()
                            true
                        }
                        Key.Escape -> {
                            onCancel()
                            true
                        }
                        else -> false
                    }
                }
                .padding(horizontal = HyveSpacing.sm, vertical = HyveSpacing.smd),
            decorationBox = { innerTextField ->
                Box {
                    if (nameBuffer.isEmpty()) {
                        Text(
                            text = "State name (e.g. Selected)",
                            color = colors.textDisabled,
                            style = HyveTypography.itemTitle,
                        )
                    }
                    innerTextField()
                }
            },
        )

        // Add button
        val addInteraction = remember { MutableInteractionSource() }
        val addHovered by addInteraction.collectIsHoveredAsState()
        Box(
            modifier = Modifier
                .clip(HyveShapes.card)
                .alpha(if (canSubmit) 1f else 0.3f)
                .background(if (addHovered && canSubmit) colors.info.copy(alpha = 0.85f) else colors.info)
                .hoverable(addInteraction)
                .then(
                    if (canSubmit) Modifier.clickable(onClick = onConfirm) else Modifier
                )
                .padding(horizontal = HyveSpacing.mld, vertical = 5.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "Add",
                color = colors.deepNight,
                style = HyveTypography.caption.copy(fontWeight = FontWeight.SemiBold),
            )
        }

        // Cancel button
        HyveGhostButton(
            text = "Cancel",
            onClick = onCancel,
        )
    }
}
