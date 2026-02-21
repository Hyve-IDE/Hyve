// Copyright 2026 Hyve. All rights reserved.
package com.hyve.ui.composer.styleeditor

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hyve.common.compose.HyveSpacing
import com.hyve.common.compose.HyveThemeColors
import com.hyve.common.compose.HyveTypography
import org.jetbrains.jewel.ui.component.Text

/**
 * Header strip at the top of the style editor (FR-1).
 *
 * Shows: style type name (blue) | style name (monospace) | "N states" badge.
 *
 * @param styleType The style type name (e.g. "TextButtonStyle")
 * @param styleName The style name (e.g. "@ButtonStyle")
 * @param stateCount Number of visual states in the style
 * @param modifier Modifier for the outer row
 */
@Composable
fun StyleTabHeader(
    styleType: String,
    styleName: String,
    stateCount: Int,
    modifier: Modifier = Modifier,
) {
    val colors = HyveThemeColors.colors

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = HyveSpacing.lg, vertical = HyveSpacing.mld),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(HyveSpacing.sm),
    ) {
        // Style type name in blue
        Text(
            text = styleType,
            color = colors.info,
            style = HyveTypography.sectionHeader.copy(fontWeight = FontWeight.SemiBold),
        )

        // Style name in monospace
        Text(
            text = styleName,
            color = colors.textPrimary,
            style = HyveTypography.sectionHeader.copy(fontFamily = FontFamily.Monospace),
        )

        Spacer(modifier = Modifier.weight(1f))

        // States count badge
        Text(
            text = "$stateCount states",
            color = colors.textSecondary,
            style = HyveTypography.caption,
        )
    }
}
