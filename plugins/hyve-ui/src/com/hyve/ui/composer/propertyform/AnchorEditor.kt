// Copyright 2026 Hyve. All rights reserved.
package com.hyve.ui.composer.propertyform

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight

import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hyve.common.compose.HyveShapes
import com.hyve.common.compose.HyveSpacing
import com.hyve.common.compose.HyveThemeColors
import com.hyve.common.compose.HyveTypography
import org.jetbrains.jewel.ui.component.Text

private data class AnchorField(val key: String, val label: String)

private val ANCHOR_FIELD_ROWS = listOf(
    listOf(AnchorField("left", "Left"), AnchorField("top", "Top")),
    listOf(AnchorField("right", "Right"), AnchorField("bottom", "Bottom")),
    listOf(AnchorField("width", "Width"), AnchorField("height", "Height")),
)

/**
 * Structured multi-field editor for anchor-type literal slots (FR-7).
 *
 * Six fields arranged in a 2-column, 3-row grid:
 *   Left: [____]   Top:    [____]
 *   Right: [____]  Bottom: [____]
 *   Width: [____]  Height: [____]
 *
 * @param anchorValues Current map of field key -> value string
 * @param onFieldChange Called when a single field changes: (fieldKey, newValue)
 * @param modifier Modifier for the outer container
 */
@Composable
fun AnchorEditor(
    anchorValues: Map<String, String>,
    onFieldChange: (field: String, value: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(HyveSpacing.xs),
    ) {
        for (row in ANCHOR_FIELD_ROWS) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(HyveSpacing.sm),
            ) {
                for (field in row) {
                    AnchorFieldInput(
                        label = field.label,
                        value = anchorValues[field.key] ?: "",
                        onValueChange = { onFieldChange(field.key, it) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun AnchorFieldInput(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = HyveThemeColors.colors
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val borderColor = if (isFocused) colors.honey else colors.slate

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(HyveSpacing.xs),
    ) {
        // Field label — fixed width for alignment
        Text(
            text = "$label:",
            color = colors.textSecondary,
            style = HyveTypography.caption.copy(
                fontWeight = FontWeight.Medium,
                fontFamily = FontFamily.Monospace,
            ),
            modifier = Modifier.width(52.dp),
        )

        // Field input — fills remaining space
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
            decorationBox = { innerTextField ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (value.isEmpty()) {
                        Text(
                            text = "\u2014", // —
                            color = colors.textDisabled,
                            style = HyveTypography.badge,
                        )
                    }
                    innerTextField()
                }
            },
        )
    }
}
