// Copyright 2026 Hyve. All rights reserved.
package com.hyve.ui.composer.popover

import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import com.hyve.common.compose.HyveOpacity
import com.hyve.common.compose.HyveShapes
import com.hyve.common.compose.HyveSpacing
import com.hyve.common.compose.HyveThemeColors
import com.hyve.common.compose.HyveTypography
import com.hyve.common.compose.components.HyveCheckmarkIcon
import com.hyve.common.compose.components.HyveChevronDownIcon
import com.hyve.common.compose.components.HyveFileDocIcon
import com.hyve.ui.composer.model.ImportableExport
import com.hyve.ui.composer.model.ImportableFile
import org.jetbrains.jewel.ui.component.Text

/**
 * Text input with an optional fixed prefix label (e.g. "@").
 *
 * Midnight background, slate border, honey border+cursor on focus.
 */
@Composable
fun PopoverTextField(
    value: String,
    onValueChange: (String) -> Unit,
    prefix: String = "",
    placeholder: String = "",
    focusRequester: FocusRequester? = null,
    onKeyEvent: ((KeyEvent) -> Boolean)? = null,
    modifier: Modifier = Modifier,
) {
    val colors = HyveThemeColors.colors
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val borderColor = if (isFocused) colors.honey else colors.slate

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(HyveShapes.card)
            .background(colors.midnight)
            .border(1.dp, borderColor, HyveShapes.card)
            .padding(horizontal = HyveSpacing.sm, vertical = HyveSpacing.smd),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Fixed prefix
        if (prefix.isNotEmpty()) {
            Text(
                text = prefix,
                color = colors.textDisabled,
                style = HyveTypography.itemTitle.copy(fontFamily = FontFamily.Monospace),
            )
        }

        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            interactionSource = interactionSource,
            textStyle = HyveTypography.itemTitle.copy(
                color = colors.textPrimary,
                fontFamily = FontFamily.Monospace,
            ),
            cursorBrush = SolidColor(colors.honey),
            modifier = Modifier
                .weight(1f)
                .then(
                    if (focusRequester != null) Modifier.focusRequester(focusRequester)
                    else Modifier
                )
                .then(
                    if (onKeyEvent != null) {
                        Modifier.onPreviewKeyEvent { event -> onKeyEvent(event) }
                    } else {
                        Modifier
                    }
                ),
            decorationBox = { innerTextField ->
                Box {
                    if (value.isEmpty()) {
                        Text(
                            text = placeholder,
                            color = colors.textDisabled,
                            style = HyveTypography.itemTitle.copy(fontFamily = FontFamily.Monospace),
                        )
                    }
                    innerTextField()
                }
            },
        )
    }
}

/**
 * Dropdown select with a custom arrow indicator.
 *
 * Clicking opens an overlay menu of options. Clicking an option or the
 * backdrop closes the menu.
 */
@Composable
fun <T> PopoverDropdown(
    selected: T,
    options: List<T>,
    onSelect: (T) -> Unit,
    displayName: (T) -> String,
    modifier: Modifier = Modifier,
) {
    val colors = HyveThemeColors.colors
    var expanded by remember { mutableStateOf(false) }
    val interactionSource = remember { MutableInteractionSource() }

    Box(modifier = modifier) {
        // Trigger
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(HyveShapes.card)
                .background(colors.midnight)
                .border(1.dp, colors.slate, HyveShapes.card)
                .clickable { expanded = !expanded }
                .padding(horizontal = HyveSpacing.sm, vertical = HyveSpacing.smd),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = displayName(selected),
                color = colors.textPrimary,
                style = HyveTypography.itemTitle,
            )
            HyveChevronDownIcon(
                color = colors.textSecondary,
                modifier = Modifier.size(10.dp),
            )
        }

        // Dropdown overlay — rendered in a Popup so it doesn't inflate
        // the parent layout (the old fillMaxSize scrim was stretching the
        // popover card to full modal height).
        if (expanded) {
            Popup(
                onDismissRequest = { expanded = false },
            ) {
                Box(modifier = Modifier.width(280.dp)) {
                    // Menu card
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(HyveShapes.card)
                            .background(colors.deepNight)
                            .border(1.dp, colors.slate, HyveShapes.card)
                            .heightIn(max = 200.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        for (option in options) {
                            val optionInteraction = remember { MutableInteractionSource() }
                            val optionHovered by optionInteraction.collectIsHoveredAsState()
                            val isSelected = option == selected

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(
                                        when {
                                            isSelected -> colors.honey.copy(alpha = HyveOpacity.light)
                                            optionHovered -> colors.textPrimary.copy(alpha = HyveOpacity.faint)
                                            else -> Color.Transparent
                                        }
                                    )
                                    .hoverable(optionInteraction)
                                    .clickable {
                                        onSelect(option)
                                        expanded = false
                                    }
                                    .padding(horizontal = HyveSpacing.sm, vertical = 5.dp),
                            ) {
                                Text(
                                    text = displayName(option),
                                    color = if (isSelected) colors.honey else colors.textPrimary,
                                    style = HyveTypography.itemTitle,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Action row with Cancel and Confirm buttons.
 */
@Composable
fun PopoverActions(
    confirmText: String,
    confirmColor: Color,
    confirmTextColor: Color,
    confirmEnabled: Boolean,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
) {
    val colors = HyveThemeColors.colors

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(HyveSpacing.sm, Alignment.End),
    ) {
        // Cancel button
        val cancelInteraction = remember { MutableInteractionSource() }
        val cancelHovered by cancelInteraction.collectIsHoveredAsState()

        Box(
            modifier = Modifier
                .clip(HyveShapes.card)
                .background(if (cancelHovered) colors.slate.copy(alpha = HyveOpacity.muted) else Color.Transparent)
                .hoverable(cancelInteraction)
                .clickable(onClick = onCancel)
                .padding(horizontal = HyveSpacing.md, vertical = HyveSpacing.smd),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "Cancel",
                color = colors.textSecondary,
                style = HyveTypography.itemTitle,
            )
        }

        // Confirm button
        val confirmInteraction = remember { MutableInteractionSource() }
        val confirmHovered by confirmInteraction.collectIsHoveredAsState()

        Box(
            modifier = Modifier
                .clip(HyveShapes.card)
                .alpha(if (confirmEnabled) 1f else 0.3f)
                .background(
                    if (confirmHovered && confirmEnabled) {
                        confirmColor.copy(alpha = 0.85f)
                    } else {
                        confirmColor
                    }
                )
                .hoverable(confirmInteraction)
                .then(
                    if (confirmEnabled) Modifier.clickable(onClick = onConfirm) else Modifier
                )
                .padding(horizontal = HyveSpacing.md, vertical = HyveSpacing.smd),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = confirmText,
                color = confirmTextColor,
                style = HyveTypography.itemTitle.copy(fontWeight = FontWeight.SemiBold),
            )
        }
    }
}

/**
 * File list item for import step 1 (spec 06 FR-4).
 *
 * Shows a file icon, the file name (monospace), and export count.
 */
@Composable
fun ImportFileItem(
    file: ImportableFile,
    onClick: () -> Unit,
) {
    val colors = HyveThemeColors.colors
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(HyveShapes.card)
            .background(if (isHovered) colors.textPrimary.copy(alpha = HyveOpacity.faint) else Color.Transparent)
            .border(
                1.dp,
                if (isHovered) colors.slateLight else colors.slate,
                HyveShapes.card,
            )
            .hoverable(interactionSource)
            .clickable(onClick = onClick)
            .padding(horizontal = HyveSpacing.mld, vertical = HyveSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(HyveSpacing.sm),
    ) {
        // File icon
        HyveFileDocIcon(
            color = colors.textSecondary,
            modifier = Modifier.size(14.dp),
        )

        // File name
        Text(
            text = file.fileName,
            color = colors.textPrimary,
            style = HyveTypography.itemTitle.copy(fontFamily = FontFamily.Monospace),
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        // Export count
        Text(
            text = "${file.exports.size} export${if (file.exports.size != 1) "s" else ""}",
            color = colors.textDisabled,
            style = HyveTypography.badge,
        )
    }
}

/**
 * Checkbox row for import export selection (spec 06 FR-4).
 *
 * Shows a custom checkbox, export name (monospace with @), and type label.
 */
@Composable
fun ExportCheckboxRow(
    export: ImportableExport,
    isChecked: Boolean,
    onToggle: () -> Unit,
) {
    val colors = HyveThemeColors.colors
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(HyveShapes.input)
            .background(if (isHovered) colors.textPrimary.copy(alpha = HyveOpacity.faint) else Color.Transparent)
            .hoverable(interactionSource)
            .clickable(onClick = onToggle)
            .padding(horizontal = HyveSpacing.sm, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(HyveSpacing.sm),
    ) {
        // Custom checkbox
        Box(
            modifier = Modifier
                .size(14.dp)
                .clip(HyveShapes.small)
                .border(
                    1.dp,
                    if (isChecked) colors.honey else colors.slate,
                    HyveShapes.small,
                )
                .background(if (isChecked) colors.honey else Color.Transparent),
            contentAlignment = Alignment.Center,
        ) {
            if (isChecked) {
                HyveCheckmarkIcon(
                    color = colors.deepNight,
                    modifier = Modifier.size(8.dp),
                )
            }
        }

        // Export name
        Text(
            text = "@${export.name}",
            color = colors.textPrimary,
            style = HyveTypography.caption.copy(fontFamily = FontFamily.Monospace),
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        // Type label
        Text(
            text = export.type.displayName.uppercase(),
            color = colors.textDisabled,
            style = HyveTypography.micro.copy(letterSpacing = 0.3.sp),
        )
    }
}

/**
 * Back button for import step 2 (spec 06 FR-4).
 */
@Composable
fun PopoverBackButton(
    onClick: () -> Unit,
) {
    val colors = HyveThemeColors.colors
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    Box(
        modifier = Modifier
            .clip(HyveShapes.input)
            .hoverable(interactionSource)
            .clickable(onClick = onClick)
            .padding(vertical = HyveSpacing.xs),
    ) {
        Text(
            text = "\u2190 Back", // ← Back
            color = if (isHovered) colors.textPrimary else colors.textSecondary,
            style = HyveTypography.caption,
        )
    }
}
