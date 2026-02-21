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
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hyve.common.compose.HyveOpacity
import com.hyve.common.compose.HyveShapes
import com.hyve.common.compose.HyveSpacing
import com.hyve.common.compose.HyveThemeColors
import com.hyve.common.compose.HyveTypography
import com.hyve.common.compose.components.HyveCheckmarkIcon
import com.hyve.common.compose.components.HyveCloseIcon
import com.hyve.common.compose.components.HyveCrossMarkIcon
import com.hyve.common.compose.components.HyveRightArrowIcon
import com.hyve.ui.components.colorpicker.ColorPicker
import com.hyve.ui.composer.model.ComposerPropertyType
import com.hyve.ui.composer.model.FillMode
import com.hyve.ui.composer.model.PropertySlot
import com.hyve.ui.schema.discovery.TupleFieldInfo
import org.jetbrains.jewel.ui.component.Text
import java.util.regex.Pattern

/**
 * A single property slot line in the form (FR-2 through FR-10).
 *
 * Renders: type dot -> property name -> colon -> value area -> semicolon.
 * The value area adapts based on fill mode and property type.
 *
 * @param slot The property slot data
 * @param onUpdateValue Called to update the slot's value (literal edit): (newValue) -> Unit
 * @param onUpdateFillMode Called to set fill mode + value together: (fillMode, value) -> Unit
 * @param onAnchorFieldChange Called for anchor field changes: (fieldKey, newValue) -> Unit
 * @param onClear Called to clear the slot back to empty
 * @param onStyleNavigate Called when the style navigation arrow is clicked: (styleName) -> Unit
 * @param isDropTarget Whether this line is the hovered valid drop target
 * @param isInvalidDrop Whether this line is the hovered invalid drop target
 * @param isDragging Whether a drag is currently in progress
 * @param isDropHint Whether this empty slot should show a compatibility hint during drag
 * @param onDragEnter Called when the pointer enters this line during an active drag
 * @param onDragExit Called when the pointer exits this line during an active drag
 * @param modifier Modifier for the outer row
 */
@Composable
fun PropertyLine(
    slot: PropertySlot,
    onUpdateValue: (String) -> Unit,
    onUpdateFillMode: (FillMode, String) -> Unit,
    onAnchorFieldChange: (String, String) -> Unit,
    onTupleFieldChange: (String, String) -> Unit = { _, _ -> },
    onTupleFieldAdd: (String) -> Unit = {},
    onTupleFieldRemove: (String) -> Unit = {},
    knownFields: List<TupleFieldInfo> = emptyList(),
    onClear: () -> Unit,
    onStyleNavigate: (String) -> Unit,
    isDropTarget: Boolean = false,
    isInvalidDrop: Boolean = false,
    isDragging: Boolean = false,
    isDropHint: Boolean = false,
    onDragEnter: () -> Unit = {},
    onDragExit: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val colors = HyveThemeColors.colors
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val isEmpty = slot.fillMode == FillMode.EMPTY

    // Editing state for inline text input
    var editing by remember { mutableStateOf(false) }
    var editBuffer by remember(slot.value) { mutableStateOf(slot.value) }

    // Color picker state for inline hex detection
    var showInlineColorPicker by remember { mutableStateOf(false) }
    val detectedHexColor = remember(slot.value) { detectHexColor(slot.value) }

    // Drop target visual styling
    val dropModifier = when {
        isInvalidDrop -> Modifier
            .border(1.dp, colors.error.copy(alpha = HyveOpacity.muted), HyveShapes.card)
            .background(colors.error.copy(alpha = HyveOpacity.subtle), HyveShapes.card)
        isDropTarget -> Modifier
            .border(1.dp, colors.honey.copy(alpha = HyveOpacity.muted), HyveShapes.card)
            .background(colors.honey.copy(alpha = HyveOpacity.subtle), HyveShapes.card)
        else -> Modifier
    }

    // Line hover background
    val lineBg = when {
        isHovered && !isEmpty -> colors.textPrimary.copy(alpha = HyveOpacity.faint)
        else -> Color.Transparent
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(HyveShapes.card)
            .then(dropModifier)
            .background(lineBg, HyveShapes.card)
            .hoverable(interactionSource)
            .pointerInput(isDragging) {
                if (!isDragging) return@pointerInput
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        when (event.type) {
                            PointerEventType.Enter -> onDragEnter()
                            PointerEventType.Exit -> onDragExit()
                        }
                    }
                }
            }
            .padding(horizontal = HyveSpacing.sm, vertical = HyveSpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(HyveSpacing.smd),
    ) {
        // Type dot — 6dp colored circle
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(typeColor(slot.type, colors))
        )

        // Property name area — 120dp minimum
        Row(
            modifier = Modifier.widthIn(min = 120.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = slot.name,
                color = colors.textPrimary,
                style = HyveTypography.itemTitle.copy(fontFamily = FontFamily.Monospace),
            )
            if (slot.required) {
                Text(
                    text = "*",
                    color = colors.error,
                    style = HyveTypography.itemTitle.copy(fontWeight = FontWeight.Bold),
                    modifier = Modifier.padding(start = HyveSpacing.xxs),
                )
            }
        }

        // Colon separator
        Text(
            text = ":",
            color = colors.textSecondary,
            style = HyveTypography.itemTitle.copy(fontFamily = FontFamily.Monospace),
        )

        // Value area — flex
        Box(modifier = Modifier.weight(1f)) {
            when {
                isEmpty -> EmptyPlaceholder(
                    type = slot.type,
                    isDropHint = isDropHint,
                    onClick = {
                        onUpdateFillMode(FillMode.LITERAL, defaultLiteralValue(slot.type))
                        editing = true
                    },
                )

                slot.type == ComposerPropertyType.BOOLEAN && slot.fillMode == FillMode.LITERAL ->
                    BooleanToggle(
                        value = slot.value,
                        onToggle = {
                            onUpdateValue(if (slot.value == "true") "false" else "true")
                        },
                    )

                slot.type == ComposerPropertyType.COLOR && slot.fillMode == FillMode.LITERAL ->
                    ColorEditor(
                        value = slot.value,
                        onValueChange = onUpdateValue,
                    )

                slot.type == ComposerPropertyType.ANCHOR && slot.fillMode == FillMode.LITERAL ->
                    AnchorEditor(
                        anchorValues = slot.anchorValues,
                        onFieldChange = onAnchorFieldChange,
                    )

                slot.type == ComposerPropertyType.TUPLE && slot.fillMode == FillMode.LITERAL ->
                    TupleEditor(
                        tupleValues = slot.tupleValues,
                        onFieldChange = onTupleFieldChange,
                        onFieldAdd = onTupleFieldAdd,
                        onFieldRemove = onTupleFieldRemove,
                        knownFields = knownFields,
                    )

                else -> FilledValueDisplay(
                    slot = slot,
                    editing = editing,
                    detectedHexColor = detectedHexColor,
                    editBuffer = editBuffer,
                    onEditBufferChange = { editBuffer = it },
                    onStartEdit = { editing = true },
                    onCommitEdit = {
                        onUpdateValue(editBuffer)
                        editing = false
                    },
                    onCancelEdit = {
                        editBuffer = slot.value
                        editing = false
                    },
                    showInlineColorPicker = showInlineColorPicker,
                    onToggleInlineColorPicker = { showInlineColorPicker = !showInlineColorPicker },
                    onInlineColorChange = { newHex ->
                        // Replace just the hex portion within the original value string
                        val oldHex = detectedHexColor
                        if (oldHex != null) {
                            onUpdateValue(slot.value.replace(oldHex, newHex))
                        } else {
                            onUpdateValue(newHex)
                        }
                    },
                    onStyleNavigate = onStyleNavigate,
                )
            }
        }

        // Clear button — visible on hover for filled slots
        if (!isEmpty) {
            val clearAlpha by animateFloatAsState(
                targetValue = if (isHovered) 1f else 0f,
                animationSpec = tween(durationMillis = 100),
            )
            Box(
                modifier = Modifier
                    .alpha(clearAlpha)
                    .clip(HyveShapes.input)
                    .clickable(enabled = isHovered) { onClear() }
                    .padding(horizontal = HyveSpacing.xs, vertical = HyveSpacing.xxs),
                contentAlignment = Alignment.Center,
            ) {
                HyveCloseIcon(
                    color = colors.textDisabled,
                    modifier = Modifier.size(8.dp),
                )
            }
        }

        // Semicolon
        Text(
            text = ";",
            color = colors.textSecondary,
            style = HyveTypography.itemTitle.copy(fontFamily = FontFamily.Monospace),
        )
    }
}

// -- Private sub-composables --

/**
 * Dashed placeholder button for empty slots (FR-3).
 */
@Composable
private fun EmptyPlaceholder(
    type: ComposerPropertyType,
    isDropHint: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = HyveThemeColors.colors
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    val borderColor = when {
        isHovered -> colors.honey
        else -> colors.slate
    }
    val bgColor = when {
        isDropHint -> colors.honey.copy(alpha = HyveOpacity.faint)
        isHovered -> colors.honey.copy(alpha = HyveOpacity.faint)
        else -> Color.Transparent
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(HyveShapes.card)
            .background(bgColor, HyveShapes.card)
            .border(1.dp, borderColor.copy(alpha = HyveOpacity.muted), HyveShapes.card)
            .hoverable(interactionSource)
            .clickable(onClick = onClick)
            .padding(horizontal = HyveSpacing.sm, vertical = HyveSpacing.inputVPad),
    ) {
        Text(
            text = emptyPlaceholder(type),
            color = colors.textDisabled,
            style = HyveTypography.caption.copy(fontFamily = FontFamily.Monospace),
        )
    }
}

/**
 * Toggle button for boolean-type literal slots (FR-5).
 */
@Composable
private fun BooleanToggle(
    value: String,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = HyveThemeColors.colors
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val isTrue = value == "true"

    val bgColor = if (isHovered) colors.textPrimary.copy(alpha = HyveOpacity.subtle) else colors.textPrimary.copy(alpha = HyveOpacity.faint)
    val indicatorColor = if (isTrue) colors.success else colors.error

    Row(
        modifier = modifier
            .clip(HyveShapes.card)
            .background(bgColor, HyveShapes.card)
            .hoverable(interactionSource)
            .clickable(onClick = onToggle)
            .padding(horizontal = HyveSpacing.sm, vertical = HyveSpacing.inputVPad),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(HyveSpacing.smd),
    ) {
        if (isTrue) {
            HyveCheckmarkIcon(color = indicatorColor, modifier = Modifier.size(10.dp))
        } else {
            HyveCrossMarkIcon(color = indicatorColor, modifier = Modifier.size(10.dp))
        }
        Text(
            text = value,
            color = colors.textPrimary,
            style = HyveTypography.itemTitle.copy(fontFamily = FontFamily.Monospace),
        )
    }
}

/**
 * Color swatch + hex input for color-type literal slots (FR-6).
 * Clicking the swatch opens a shared ColorPicker popover.
 */
@Composable
private fun ColorEditor(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = HyveThemeColors.colors
    val parsedColor = remember(value) { parseHexColor(value) }
    val interactionSource = remember { MutableInteractionSource() }
    var showColorPicker by remember { mutableStateOf(false) }

    Column(modifier = modifier) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(HyveSpacing.smd),
        ) {
            // Color swatch — 22x22dp, clickable to toggle color picker
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .clip(HyveShapes.input)
                    .background(parsedColor ?: Color.Black)
                    .border(1.dp, colors.slate, HyveShapes.input)
                    .clickable { showColorPicker = !showColorPicker }
            )

            // Hex input — 90dp
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier
                    .width(90.dp)
                    .background(colors.midnight, HyveShapes.input)
                    .border(1.dp, colors.slate, HyveShapes.input)
                    .padding(horizontal = HyveSpacing.smd, vertical = HyveSpacing.inputVPad),
                singleLine = true,
                interactionSource = interactionSource,
                textStyle = TextStyle(
                    color = colors.textPrimary,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                ),
                cursorBrush = SolidColor(colors.honey),
                decorationBox = { innerTextField ->
                    Box {
                        if (value.isEmpty()) {
                            Text(
                                text = "#000000",
                                color = colors.textDisabled,
                                style = HyveTypography.caption.copy(fontFamily = FontFamily.Monospace),
                            )
                        }
                        innerTextField()
                    }
                },
            )
        }

        // Color picker popover
        if (showColorPicker) {
            ColorPicker(
                currentColor = value,
                onColorChanged = onValueChange,
                onDismiss = { showColorPicker = false },
                modifier = Modifier.widthIn(max = 300.dp)
            )
        }
    }
}

/**
 * Fill mode indicator badge (FR-8).
 */
@Composable
private fun FillModeBadge(
    fillMode: FillMode,
    modifier: Modifier = Modifier,
) {
    if (fillMode == FillMode.EMPTY) return

    val colors = HyveThemeColors.colors
    val badgeColor = fillModeBadgeColor(fillMode, colors)

    Box(
        modifier = modifier
            .size(18.dp)
            .clip(HyveShapes.card)
            .background(badgeColor.copy(alpha = HyveOpacity.medium)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = fillModeIcon(fillMode),
            color = badgeColor,
            style = HyveTypography.badge.copy(
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
            ),
        )
    }
}

/**
 * Filled value display with optional inline editing (FR-9).
 */
@Composable
private fun FilledValueDisplay(
    slot: PropertySlot,
    editing: Boolean,
    detectedHexColor: String?,
    editBuffer: String,
    onEditBufferChange: (String) -> Unit,
    onStartEdit: () -> Unit,
    onCommitEdit: () -> Unit,
    onCancelEdit: () -> Unit,
    showInlineColorPicker: Boolean,
    onToggleInlineColorPicker: () -> Unit,
    onInlineColorChange: (String) -> Unit,
    onStyleNavigate: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = HyveThemeColors.colors
    val parsedColor = remember(detectedHexColor) { detectedHexColor?.let { parseHexColor(it) } }

    Column(
        modifier = modifier,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(HyveSpacing.xs),
        ) {
            FillModeBadge(fillMode = slot.fillMode)

            // Inline color swatch (if hex detected)
            if (detectedHexColor != null && parsedColor != null && !editing) {
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .clip(HyveShapes.input)
                        .background(parsedColor)
                        .border(1.dp, colors.slate, HyveShapes.input)
                        .clickable { onToggleInlineColorPicker() }
                )
            }

            if (editing) {
                InlineEditField(
                    value = editBuffer,
                    onValueChange = onEditBufferChange,
                    onCommit = onCommitEdit,
                    onCancel = onCancelEdit,
                    modifier = Modifier.weight(1f),
                )
            } else {
                val interactionSource = remember { MutableInteractionSource() }
                val isHovered by interactionSource.collectIsHoveredAsState()
                val valueBg = if (isHovered) colors.textPrimary.copy(alpha = HyveOpacity.subtle) else colors.textPrimary.copy(alpha = HyveOpacity.faint)
                val valueBorder = if (isHovered) colors.slate else Color.Transparent

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(HyveShapes.input)
                        .background(valueBg, HyveShapes.input)
                        .then(
                            if (valueBorder != Color.Transparent) {
                                Modifier.border(1.dp, valueBorder, HyveShapes.input)
                            } else {
                                Modifier
                            }
                        )
                        .hoverable(interactionSource)
                        .clickable(onClick = onStartEdit)
                        .padding(horizontal = HyveSpacing.smd, vertical = HyveSpacing.inputVPad),
                ) {
                    Text(
                        text = slot.value.ifEmpty { "(empty)" },
                        color = if (slot.value.isEmpty()) colors.textDisabled else colors.textPrimary,
                        style = HyveTypography.itemTitle.copy(fontFamily = FontFamily.Monospace),
                    )
                }

                // Style navigation arrow
                if (slot.type == ComposerPropertyType.STYLE &&
                    slot.fillMode == FillMode.VARIABLE &&
                    slot.value.startsWith("@")
                ) {
                    Box(
                        modifier = Modifier
                            .clip(HyveShapes.input)
                            .clickable { onStyleNavigate(slot.value) }
                            .padding(horizontal = HyveSpacing.xs, vertical = HyveSpacing.xxs),
                        contentAlignment = Alignment.Center,
                    ) {
                        HyveRightArrowIcon(
                            color = colors.info,
                            modifier = Modifier.size(10.dp),
                        )
                    }
                }
            }
        }

        // Inline color picker popover
        if (showInlineColorPicker && detectedHexColor != null) {
            ColorPicker(
                currentColor = detectedHexColor,
                onColorChanged = onInlineColorChange,
                onDismiss = { onToggleInlineColorPicker() },
                modifier = Modifier
                    .widthIn(max = 300.dp)
                    .padding(top = HyveSpacing.xs)
            )
        }
    }
}

/**
 * Inline text input with auto-focus, honey styling (FR-4).
 */
@Composable
private fun InlineEditField(
    value: String,
    onValueChange: (String) -> Unit,
    onCommit: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = HyveThemeColors.colors
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier
            .focusRequester(focusRequester)
            .background(colors.midnight, HyveShapes.input)
            .border(1.dp, colors.honey, HyveShapes.input)
            .padding(horizontal = HyveSpacing.smd, vertical = HyveSpacing.inputVPad)
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (event.key) {
                    Key.Enter -> {
                        onCommit()
                        focusManager.clearFocus()
                        true
                    }
                    Key.Escape -> {
                        onCancel()
                        focusManager.clearFocus()
                        true
                    }
                    else -> false
                }
            },
        singleLine = true,
        textStyle = HyveTypography.itemTitle.copy(
            color = colors.textPrimary,
            fontFamily = FontFamily.Monospace,
        ),
        cursorBrush = SolidColor(colors.honey),
    )
}

// -- Utility --

private fun parseHexColor(hex: String): Color? =
    com.hyve.ui.components.colorpicker.parseHexColorOrNull(hex)

private val HEX_COLOR_REGEX = Pattern.compile("#[0-9a-fA-F]{6,8}(\\([0-9]*\\.?[0-9]+\\))?")


private fun detectHexColor(value: String): String? {
    val matcher = HEX_COLOR_REGEX.matcher(value)
    return if (matcher.find()) {
        matcher.group()
    } else {
        null
    }
}
