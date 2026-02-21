// Copyright 2026 Hyve. All rights reserved.
package com.hyve.ui.composer.propertyform

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.*
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
import com.hyve.ui.components.colorpicker.ColorPicker
import com.hyve.ui.schema.discovery.TupleFieldInfo
import org.jetbrains.jewel.ui.component.Text

/**
 * Structured multi-field editor for tuple-type literal slots.
 *
 * Each tuple field is rendered as its own editable row with a label, a
 * type-adaptive value editor, and a remove button. An "Add field" row
 * at the bottom allows adding new fields.
 *
 * Value editors adapt based on detected type:
 * - Starts with `#` -> color swatch + hex input
 * - `true`/`false` -> boolean toggle
 * - Otherwise -> text input
 *
 * @param tupleValues Current map of field key -> value string
 * @param onFieldChange Called when a field value changes: (fieldKey, newValue)
 * @param onFieldAdd Called when a new field is added: (fieldKey)
 * @param onFieldRemove Called when a field is removed: (fieldKey)
 * @param modifier Modifier for the outer container
 */
@Composable
fun TupleEditor(
    tupleValues: Map<String, String>,
    onFieldChange: (field: String, value: String) -> Unit,
    onFieldAdd: (field: String) -> Unit,
    onFieldRemove: (field: String) -> Unit,
    knownFields: List<TupleFieldInfo> = emptyList(),
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(HyveSpacing.inputVPad),
    ) {
        // Existing fields
        for ((key, value) in tupleValues) {
            TupleFieldRow(
                fieldKey = key,
                fieldValue = value,
                onValueChange = { onFieldChange(key, it) },
                onRemove = { onFieldRemove(key) },
            )
        }

        // Add field row
        AddFieldRow(
            existingKeys = tupleValues.keys,
            onAdd = onFieldAdd,
            knownFields = knownFields,
        )
    }
}

@Composable
private fun TupleFieldRow(
    fieldKey: String,
    fieldValue: String,
    onValueChange: (String) -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = HyveThemeColors.colors

    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(HyveSpacing.xs),
    ) {
        // Field label
        Text(
            text = "$fieldKey:",
            color = colors.textSecondary,
            style = HyveTypography.caption.copy(
                fontWeight = FontWeight.Medium,
                fontFamily = FontFamily.Monospace,
            ),
            modifier = Modifier.widthIn(min = 90.dp),
        )

        // Type-adaptive value editor
        Box(modifier = Modifier.weight(1f)) {
            when {
                fieldValue.equals("true", ignoreCase = true) ||
                    fieldValue.equals("false", ignoreCase = true) ->
                    TupleBooleanField(
                        value = fieldValue,
                        onToggle = {
                            onValueChange(
                                if (fieldValue.equals("true", ignoreCase = true)) "false" else "true"
                            )
                        },
                    )

                fieldValue.startsWith("#") ->
                    TupleColorField(
                        value = fieldValue,
                        onValueChange = onValueChange,
                    )

                else ->
                    TupleTextField(
                        value = fieldValue,
                        onValueChange = onValueChange,
                    )
            }
        }

        // Remove button
        val removeInteraction = remember { MutableInteractionSource() }
        val isRemoveHovered by removeInteraction.collectIsHoveredAsState()

        Box(
            modifier = Modifier
                .clip(HyveShapes.input)
                .hoverable(removeInteraction)
                .clickable(onClick = onRemove)
                .padding(horizontal = HyveSpacing.inputVPad, vertical = 1.dp),
            contentAlignment = Alignment.Center,
        ) {
            HyveCloseIcon(
                color = if (isRemoveHovered) colors.error else colors.textDisabled,
                modifier = Modifier.size(8.dp),
            )
        }
    }
}

@Composable
private fun TupleTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = HyveThemeColors.colors
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val borderColor = if (isFocused) colors.honey else colors.slate

    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier
            .fillMaxWidth()
            .background(colors.midnight, HyveShapes.input)
            .border(1.dp, borderColor, HyveShapes.input)
            .padding(horizontal = HyveSpacing.smd, vertical = HyveSpacing.inputVPad),
        singleLine = true,
        interactionSource = interactionSource,
        textStyle = HyveTypography.caption.copy(
            color = colors.textPrimary,
            fontFamily = FontFamily.Monospace,
        ),
        cursorBrush = SolidColor(colors.honey),
        decorationBox = { innerTextField ->
            Box(contentAlignment = Alignment.CenterStart) {
                if (value.isEmpty()) {
                    Text(
                        text = "\u2014",
                        color = colors.textDisabled,
                        style = HyveTypography.badge,
                    )
                }
                innerTextField()
            }
        },
    )
}

@Composable
private fun TupleBooleanField(
    value: String,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = HyveThemeColors.colors
    val isTrue = value.equals("true", ignoreCase = true)
    val indicatorColor = if (isTrue) colors.success else colors.error

    Row(
        modifier = modifier
            .clip(HyveShapes.input)
            .background(colors.textPrimary.copy(alpha = HyveOpacity.faint), HyveShapes.input)
            .clickable(onClick = onToggle)
            .padding(horizontal = HyveSpacing.smd, vertical = HyveSpacing.inputVPad),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(HyveSpacing.xs),
    ) {
        if (isTrue) {
            HyveCheckmarkIcon(color = indicatorColor, modifier = Modifier.size(10.dp))
        } else {
            HyveCrossMarkIcon(color = indicatorColor, modifier = Modifier.size(10.dp))
        }
        Text(
            text = value,
            color = colors.textPrimary,
            style = HyveTypography.caption.copy(fontFamily = FontFamily.Monospace),
        )
    }
}

@Composable
private fun TupleColorField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = HyveThemeColors.colors
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val borderColor = if (isFocused) colors.honey else colors.slate
    val parsedColor = remember(value) { parseTupleHexColor(value) }
    var showColorPicker by remember { mutableStateOf(false) }

    Column(modifier = modifier) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(HyveSpacing.xs),
        ) {
            // Color swatch — clickable to toggle picker
            Box(
                modifier = Modifier
                    .size(18.dp)
                    .clip(HyveShapes.input)
                    .background(parsedColor ?: Color.Black)
                    .border(1.dp, colors.slate, HyveShapes.input)
                    .clickable { showColorPicker = !showColorPicker }
            )

            // Hex input
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier
                    .weight(1f)
                    .background(colors.midnight, HyveShapes.input)
                    .border(1.dp, borderColor, HyveShapes.input)
                    .padding(horizontal = HyveSpacing.smd, vertical = HyveSpacing.inputVPad),
                singleLine = true,
                interactionSource = interactionSource,
                textStyle = HyveTypography.caption.copy(
                    color = colors.textPrimary,
                    fontFamily = FontFamily.Monospace,
                ),
                cursorBrush = SolidColor(colors.honey),
            )
        }

        if (showColorPicker) {
            ColorPicker(
                currentColor = value,
                onColorChanged = onValueChange,
                onDismiss = { showColorPicker = false },
                modifier = Modifier
                    .widthIn(max = 280.dp)
                    .padding(top = HyveSpacing.xs)
            )
        }
    }
}

@Composable
private fun AddFieldRow(
    existingKeys: Set<String>,
    onAdd: (String) -> Unit,
    knownFields: List<TupleFieldInfo> = emptyList(),
    modifier: Modifier = Modifier,
) {
    val colors = HyveThemeColors.colors
    var showDropdown by remember { mutableStateOf(false) }
    var showCustomInput by remember { mutableStateOf(false) }
    var newFieldName by remember { mutableStateOf("") }
    val focusManager = LocalFocusManager.current
    val focusRequester = remember { FocusRequester() }

    val suggestions = remember(knownFields, existingKeys) {
        knownFields
            .filter { it.name !in existingKeys }
            .sortedByDescending { it.occurrences }
            .take(15)
    }

    Box(modifier = modifier.fillMaxWidth()) {
        // The trigger button row
        if (showCustomInput) {
            // Custom field name text input mode
            val interactionSource = remember { MutableInteractionSource() }
            BasicTextField(
                value = newFieldName,
                onValueChange = { newFieldName = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.midnight, HyveShapes.input)
                    .border(1.dp, colors.honey, HyveShapes.input)
                    .padding(horizontal = HyveSpacing.smd, vertical = HyveSpacing.inputVPad)
                    .focusRequester(focusRequester)
                    .onPreviewKeyEvent { event ->
                        if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                        when (event.key) {
                            Key.Enter -> {
                                val trimmed = newFieldName.trim()
                                if (trimmed.isNotEmpty() && trimmed !in existingKeys) {
                                    onAdd(trimmed)
                                }
                                newFieldName = ""
                                showCustomInput = false
                                focusManager.clearFocus()
                                true
                            }
                            Key.Escape -> {
                                newFieldName = ""
                                showCustomInput = false
                                focusManager.clearFocus()
                                true
                            }
                            else -> false
                        }
                    },
                singleLine = true,
                interactionSource = interactionSource,
                textStyle = HyveTypography.caption.copy(
                    color = colors.textPrimary,
                    fontFamily = FontFamily.Monospace,
                ),
                cursorBrush = SolidColor(colors.honey),
                decorationBox = { innerTextField ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (newFieldName.isEmpty()) {
                            Text(
                                text = "Field name...",
                                color = colors.textDisabled,
                                style = HyveTypography.badge.copy(fontFamily = FontFamily.Monospace),
                            )
                        }
                        innerTextField()
                    }
                },
            )
            LaunchedEffect(Unit) { focusRequester.requestFocus() }
        } else {
            // "+ Add field" button
            val addBtnInteraction = remember { MutableInteractionSource() }
            val isAddBtnHovered by addBtnInteraction.collectIsHoveredAsState()

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(HyveShapes.input)
                    .background(
                        if (isAddBtnHovered) colors.textPrimary.copy(alpha = HyveOpacity.faint) else Color.Transparent,
                        HyveShapes.input,
                    )
                    .hoverable(addBtnInteraction)
                    .clickable {
                        showDropdown = true
                    }
                    .padding(horizontal = HyveSpacing.xs, vertical = HyveSpacing.inputVPad),
            ) {
                Text(
                    text = "+ Add field",
                    color = if (isAddBtnHovered) colors.honey else colors.textDisabled,
                    style = HyveTypography.badge.copy(fontFamily = FontFamily.Monospace),
                )
            }
        }

        // Floating dropdown with known field suggestions
        if (showDropdown) {
            Popup(
                alignment = Alignment.TopStart,
                onDismissRequest = { showDropdown = false },
                properties = PopupProperties(focusable = true),
            ) {
                Column(
                    modifier = Modifier
                        .widthIn(min = 180.dp, max = 280.dp)
                        .shadow(8.dp, HyveShapes.dialog)
                        .background(colors.midnight, HyveShapes.dialog)
                        .border(1.dp, colors.slate, HyveShapes.dialog)
                        .padding(HyveSpacing.xs)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(1.dp),
                ) {
                    if (suggestions.isNotEmpty()) {
                        // Header
                        Text(
                            text = "Known fields",
                            color = colors.textDisabled,
                            style = HyveTypography.micro.copy(fontWeight = FontWeight.Medium),
                            modifier = Modifier.padding(horizontal = HyveSpacing.smd, vertical = HyveSpacing.xxs),
                        )

                        // Suggestion rows
                        for (field in suggestions) {
                            KnownFieldSuggestion(
                                field = field,
                                onClick = {
                                    onAdd(field.name)
                                    showDropdown = false
                                },
                            )
                        }

                        // Divider
                        Spacer(modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = HyveSpacing.smd, vertical = HyveSpacing.xxs)
                            .height(1.dp)
                            .background(colors.slate.copy(alpha = HyveOpacity.moderate)))
                    }

                    // "Custom..." option at the bottom
                    val customInteraction = remember { MutableInteractionSource() }
                    val customHovered by customInteraction.collectIsHoveredAsState()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(HyveShapes.input)
                            .background(
                                if (customHovered) colors.honey.copy(alpha = HyveOpacity.subtle) else Color.Transparent,
                                HyveShapes.input,
                            )
                            .hoverable(customInteraction)
                            .clickable {
                                showDropdown = false
                                showCustomInput = true
                            }
                            .padding(horizontal = HyveSpacing.smd, vertical = HyveSpacing.xs),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(HyveSpacing.smd),
                    ) {
                        Text(
                            text = "+",
                            color = colors.textDisabled,
                            style = HyveTypography.caption.copy(fontWeight = FontWeight.Bold),
                        )
                        Text(
                            text = "Custom field...",
                            color = if (customHovered) colors.textPrimary else colors.textSecondary,
                            style = HyveTypography.caption.copy(fontFamily = FontFamily.Monospace),
                        )
                    }
                }
            }
        }
    }
}

/**
 * A single clickable suggestion row for a known tuple field.
 * Shows a type-color dot, field name, and occurrence count.
 */
@Composable
private fun KnownFieldSuggestion(
    field: TupleFieldInfo,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = HyveThemeColors.colors
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(HyveShapes.input)
            .background(
                if (isHovered) colors.honey.copy(alpha = HyveOpacity.subtle) else Color.Transparent,
                HyveShapes.input,
            )
            .hoverable(interactionSource)
            .clickable(onClick = onClick)
            .padding(horizontal = HyveSpacing.smd, vertical = HyveSpacing.inputVPad),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(HyveSpacing.smd),
    ) {
        // Type-color dot
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(androidx.compose.foundation.shape.CircleShape)
                .background(tupleFieldTypeColor(field.inferredType))
        )

        // Field name
        Text(
            text = field.name,
            color = if (isHovered) colors.textPrimary else colors.textSecondary,
            style = HyveTypography.caption.copy(fontFamily = FontFamily.Monospace),
            modifier = Modifier.weight(1f),
        )

        // Occurrence count
        Text(
            text = "${field.occurrences}",
            color = colors.textDisabled,
            style = HyveTypography.micro,
        )
    }
}

/** Map tuple field inferred type to a dot color. */
@Composable
private fun tupleFieldTypeColor(type: String): Color {
    val colors = HyveThemeColors.colors
    return when (type.uppercase()) {
        "NUMBER", "PERCENT" -> colors.info
        "TEXT" -> colors.success
        "BOOLEAN" -> colors.warning
        "COLOR" -> colors.error
        "TUPLE" -> colors.honey
        else -> colors.textDisabled
    }
}

private fun parseTupleHexColor(hex: String): Color? =
    com.hyve.ui.components.colorpicker.parseHexColorOrNull(hex)
