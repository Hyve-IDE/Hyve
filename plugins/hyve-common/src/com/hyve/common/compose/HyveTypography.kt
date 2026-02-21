// Copyright 2026 Hyve. All rights reserved.
package com.hyve.common.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

object HyveTypography {

    /** 9sp Normal, textSecondary — arrows, tiny labels */
    val micro: TextStyle
        @Composable @ReadOnlyComposable
        get() = TextStyle(
            color = HyveThemeColors.colors.textSecondary,
            fontSize = 9.sp,
        )

    /** 14sp SemiBold, textPrimary — popover titles, close buttons */
    val title: TextStyle
        @Composable @ReadOnlyComposable
        get() = TextStyle(
            color = HyveThemeColors.colors.textPrimary,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
        )

    /** 13sp SemiBold, textPrimary — corpus group titles, panel headers */
    val sectionHeader: TextStyle
        @Composable @ReadOnlyComposable
        get() = TextStyle(
            color = HyveThemeColors.colors.textPrimary,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
        )

    /** 12sp Medium, textPrimary — result names, entity names */
    val itemTitle: TextStyle
        @Composable @ReadOnlyComposable
        get() = TextStyle(
            color = HyveThemeColors.colors.textPrimary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
        )

    /** 11sp Normal, textSecondary — file paths, metadata */
    val caption: TextStyle
        @Composable @ReadOnlyComposable
        get() = TextStyle(
            color = HyveThemeColors.colors.textSecondary,
            fontSize = 11.sp,
        )

    /** 11sp Normal, textSecondary — bottom bar text */
    val statusBar: TextStyle
        @Composable @ReadOnlyComposable
        get() = TextStyle(
            color = HyveThemeColors.colors.textSecondary,
            fontSize = 11.sp,
        )

    /** 10sp Medium, textSecondary — count pills, score text */
    val badge: TextStyle
        @Composable @ReadOnlyComposable
        get() = TextStyle(
            color = HyveThemeColors.colors.textSecondary,
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium,
        )

    /** 13sp Normal, textDisabled — empty states */
    val placeholder: TextStyle
        @Composable @ReadOnlyComposable
        get() = TextStyle(
            color = HyveThemeColors.colors.textDisabled,
            fontSize = 13.sp,
        )
}
