// Copyright 2026 Hyve. All rights reserved.
package com.hyve.common.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Hyve color palette — dark theme constants.
 * These colors are designed for Hytale game modding with honey gold accents.
 */
object HyveColors {
    // Primary Honey Gold Colors
    val Honey = Color(0xFFF7A800)
    val HoneyLight = Color(0xFFFFB824)
    val HoneyDark = Color(0xFFD99000)
    val HoneySubtle = Color(0xFF4A3800)

    // Background Colors
    val DeepNight = Color(0xFF0D0D14)
    val Midnight = Color(0xFF1A1A2E)
    val Twilight = Color(0xFF16213E)
    val Slate = Color(0xFF2D2D44)
    val SlateLight = Color(0xFF3D3D54)

    // Text Colors
    val TextPrimary = Color(0xFFEAEAEA)
    val TextSecondary = Color(0xFF8A8A9A)
    val TextDisabled = Color(0xFF4A4A5A)

    // Semantic Colors
    val Success = Color(0xFF4ADE80)
    val SuccessDark = Color(0xFF3AAF6A)
    val Warning = Color(0xFFFBBF24)
    val Error = Color(0xFFF87171)
    val ErrorDark = Color(0xFFC45555)
    val Info = Color(0xFF60A5FA)
    val Accent = Color(0xFF9D8CFF)

    // Word Bank Kind Colors
    val KindVariable = Color(0xFFF87171)  // red — variables (@)
    val KindStyle = Color(0xFF60A5FA)     // blue — styles (palette)
    val KindImport = Color(0xFFA78BFA)    // purple — imports ($)
    val KindLocalization = Color(0xFFFBBF24) // yellow — localization (%)
    val KindAsset = Color(0xFF4ADE80)     // green — assets (image)

    // Property Type Kind Colors
    val KindColor = Color(0xFFF472B6)     // pink — color properties
    val KindAnchor = Color(0xFF22D3EE)    // cyan — anchor properties
    val KindTuple = Color(0xFFC084FC)     // violet — tuple properties

    // Canvas Colors
    val CanvasBg = Color(0xFF0A0A12)
    val CanvasBorder = Color(0xFF2D2D44)
    val CanvasGrid = Color(0xFFFFFFFF)  // apply with HyveOpacity.subtle
}

/**
 * Hyve color palette — light theme constants.
 */
object HyveColorsLight {
    // Primary Honey Gold Colors (darkened for white-bg contrast)
    val Honey = Color(0xFFD98E00)
    val HoneyLight = Color(0xFFF7A800)
    val HoneyDark = Color(0xFFB87800)
    val HoneySubtle = Color(0xFFFFF3D6)

    // Background Colors (light grays)
    val DeepNight = Color(0xFFF5F5F7)
    val Midnight = Color(0xFFEBEBF0)
    val Twilight = Color(0xFFE0E0E8)
    val Slate = Color(0xFFD5D5DD)
    val SlateLight = Color(0xFFC5C5D0)

    // Text Colors (dark-on-light)
    val TextPrimary = Color(0xFF1A1A2E)
    val TextSecondary = Color(0xFF5A5A6E)
    val TextDisabled = Color(0xFF9A9AAA)

    // Semantic Colors (darkened for readability on light)
    val Success = Color(0xFF2D9F57)
    val SuccessDark = Color(0xFF238A49)
    val Warning = Color(0xFFD99E00)
    val Error = Color(0xFFDC4444)
    val ErrorDark = Color(0xFFB83333)
    val Info = Color(0xFF3B82F6)
    val Accent = Color(0xFF7C6BDB)

    // Word Bank Kind Colors (darkened for light backgrounds)
    val KindVariable = Color(0xFFDC4444)
    val KindStyle = Color(0xFF3B82F6)
    val KindImport = Color(0xFF7C6BDB)
    val KindLocalization = Color(0xFFD99E00)
    val KindAsset = Color(0xFF2D9F57)

    // Property Type Kind Colors (darkened for light backgrounds)
    val KindColor = Color(0xFFDB2777)     // pink
    val KindAnchor = Color(0xFF0891B2)    // cyan
    val KindTuple = Color(0xFF9333EA)     // violet

    // Canvas Colors (light mode)
    val CanvasBg = Color(0xFFE8E8EE)
    val CanvasBorder = Color(0xFFB8B8C8)
    val CanvasGrid = Color(0xFF000000)  // apply with HyveOpacity.subtle
}

/** Creates [HyveExtendedColors] for dark themes. */
fun darkColors() = HyveExtendedColors(
    honey = HyveColors.Honey,
    honeyLight = HyveColors.HoneyLight,
    honeyDark = HyveColors.HoneyDark,
    honeySubtle = HyveColors.HoneySubtle,
    deepNight = HyveColors.DeepNight,
    midnight = HyveColors.Midnight,
    twilight = HyveColors.Twilight,
    slate = HyveColors.Slate,
    slateLight = HyveColors.SlateLight,
    textPrimary = HyveColors.TextPrimary,
    textSecondary = HyveColors.TextSecondary,
    textDisabled = HyveColors.TextDisabled,
    success = HyveColors.Success,
    successDark = HyveColors.SuccessDark,
    warning = HyveColors.Warning,
    error = HyveColors.Error,
    errorDark = HyveColors.ErrorDark,
    info = HyveColors.Info,
    accent = HyveColors.Accent,
    kindVariable = HyveColors.KindVariable,
    kindStyle = HyveColors.KindStyle,
    kindImport = HyveColors.KindImport,
    kindLocalization = HyveColors.KindLocalization,
    kindAsset = HyveColors.KindAsset,
    kindColor = HyveColors.KindColor,
    kindAnchor = HyveColors.KindAnchor,
    kindTuple = HyveColors.KindTuple,
    canvasBg = HyveColors.CanvasBg,
    canvasBorder = HyveColors.CanvasBorder,
    canvasGrid = HyveColors.CanvasGrid,
)

/** Creates [HyveExtendedColors] for light themes. */
fun lightColors() = HyveExtendedColors(
    honey = HyveColorsLight.Honey,
    honeyLight = HyveColorsLight.HoneyLight,
    honeyDark = HyveColorsLight.HoneyDark,
    honeySubtle = HyveColorsLight.HoneySubtle,
    deepNight = HyveColorsLight.DeepNight,
    midnight = HyveColorsLight.Midnight,
    twilight = HyveColorsLight.Twilight,
    slate = HyveColorsLight.Slate,
    slateLight = HyveColorsLight.SlateLight,
    textPrimary = HyveColorsLight.TextPrimary,
    textSecondary = HyveColorsLight.TextSecondary,
    textDisabled = HyveColorsLight.TextDisabled,
    success = HyveColorsLight.Success,
    successDark = HyveColorsLight.SuccessDark,
    warning = HyveColorsLight.Warning,
    error = HyveColorsLight.Error,
    errorDark = HyveColorsLight.ErrorDark,
    info = HyveColorsLight.Info,
    accent = HyveColorsLight.Accent,
    kindVariable = HyveColorsLight.KindVariable,
    kindStyle = HyveColorsLight.KindStyle,
    kindImport = HyveColorsLight.KindImport,
    kindLocalization = HyveColorsLight.KindLocalization,
    kindAsset = HyveColorsLight.KindAsset,
    kindColor = HyveColorsLight.KindColor,
    kindAnchor = HyveColorsLight.KindAnchor,
    kindTuple = HyveColorsLight.KindTuple,
    canvasBg = HyveColorsLight.CanvasBg,
    canvasBorder = HyveColorsLight.CanvasBorder,
    canvasGrid = HyveColorsLight.CanvasGrid,
)

/**
 * Extended color palette for Hyve Compose components.
 * This provides additional colors beyond what Jewel's theme offers.
 */
data class HyveExtendedColors(
    // Primary accent
    val honey: Color = HyveColors.Honey,
    val honeyLight: Color = HyveColors.HoneyLight,
    val honeyDark: Color = HyveColors.HoneyDark,
    val honeySubtle: Color = HyveColors.HoneySubtle,

    // Backgrounds
    val deepNight: Color = HyveColors.DeepNight,
    val midnight: Color = HyveColors.Midnight,
    val twilight: Color = HyveColors.Twilight,
    val slate: Color = HyveColors.Slate,
    val slateLight: Color = HyveColors.SlateLight,

    // Text
    val textPrimary: Color = HyveColors.TextPrimary,
    val textSecondary: Color = HyveColors.TextSecondary,
    val textDisabled: Color = HyveColors.TextDisabled,

    // Semantic
    val success: Color = HyveColors.Success,
    val successDark: Color = HyveColors.SuccessDark,
    val warning: Color = HyveColors.Warning,
    val error: Color = HyveColors.Error,
    val errorDark: Color = HyveColors.ErrorDark,
    val info: Color = HyveColors.Info,
    val accent: Color = HyveColors.Accent,

    // Word Bank Kind Colors
    val kindVariable: Color = HyveColors.KindVariable,
    val kindStyle: Color = HyveColors.KindStyle,
    val kindImport: Color = HyveColors.KindImport,
    val kindLocalization: Color = HyveColors.KindLocalization,
    val kindAsset: Color = HyveColors.KindAsset,

    // Property Type Kind Colors
    val kindColor: Color = HyveColors.KindColor,
    val kindAnchor: Color = HyveColors.KindAnchor,
    val kindTuple: Color = HyveColors.KindTuple,

    // Canvas Colors
    val canvasBg: Color = HyveColors.CanvasBg,
    val canvasBorder: Color = HyveColors.CanvasBorder,
    val canvasGrid: Color = HyveColors.CanvasGrid,
)

/**
 * Sun (brand gold) 11-step ramp for future UI work.
 * Current tokens reference values from this ramp.
 */
object HyveSunRamp {
    val s50 = Color(0xFFFFFDEA)
    val s100 = Color(0xFFFFF7C5)
    val s200 = Color(0xFFFFEC89)
    val s300 = Color(0xFFFFDB4D)
    val s400 = Color(0xFFF7A800)   // = HyveColors.Honey
    val s500 = Color(0xFFD99000)   // = HyveColors.HoneyDark
    val s600 = Color(0xFFB87800)
    val s700 = Color(0xFF926000)
    val s800 = Color(0xFF6E4800)
    val s900 = Color(0xFF4A3000)   // close to HyveColors.HoneySubtle
    val s950 = Color(0xFF2D1C00)
}

/**
 * Steel Gray (neutral) 11-step ramp for future UI work.
 * Current background tokens reference values from this ramp.
 */
object HyveSteelRamp {
    val s50 = Color(0xFFF1F5FC)
    val s100 = Color(0xFFE0E0E8)
    val s200 = Color(0xFFD5D5DD)
    val s300 = Color(0xFFC5C5D0)
    val s400 = Color(0xFF8A8A9A)   // = HyveColors.TextSecondary
    val s500 = Color(0xFF4A4A5A)   // = HyveColors.TextDisabled
    val s600 = Color(0xFF3D3D54)   // = HyveColors.SlateLight
    val s700 = Color(0xFF2D2D44)   // = HyveColors.Slate
    val s800 = Color(0xFF1A1A2E)   // = HyveColors.Midnight
    val s900 = Color(0xFF0D0D14)   // = HyveColors.DeepNight
    val s950 = Color(0xFF06060A)
}

/**
 * CompositionLocal for accessing Hyve extended colors.
 */
val LocalHyveColors = staticCompositionLocalOf { HyveExtendedColors() }

/**
 * Accessor for Hyve extended colors in Compose.
 *
 * Usage:
 * ```kotlin
 * val honeyColor = HyveTheme.colors.honey
 * ```
 */
object HyveThemeColors {
    val colors: HyveExtendedColors
        @Composable
        @ReadOnlyComposable
        get() = LocalHyveColors.current
}
