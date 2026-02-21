// Copyright 2026 Hyve. All rights reserved.
package com.hyve.common.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import org.jetbrains.jewel.bridge.theme.SwingBridgeTheme
import org.jetbrains.jewel.foundation.ExperimentalJewelApi
import org.jetbrains.jewel.foundation.theme.JewelTheme

/**
 * Hyve theme wrapper that combines Jewel's SwingBridgeTheme with Hyve-specific colors.
 *
 * This theme:
 * 1. Uses Jewel's SwingBridgeTheme to sync IntelliJ LAF colors to Compose
 * 2. Provides additional Hyve-specific colors via LocalHyveColors
 * 3. Automatically updates when the user changes the IntelliJ theme
 *
 * Usage:
 * ```kotlin
 * HyveTheme {
 *     // Access Jewel theme colors via JewelTheme
 *     val background = JewelTheme.globalColors.paneBackground
 *
 *     // Access Hyve-specific colors
 *     val honey = HyveThemeColors.colors.honey
 *
 *     Surface(color = background) {
 *         Text("Hello", color = honey)
 *     }
 * }
 * ```
 */
@OptIn(ExperimentalJewelApi::class)
@Composable
fun HyveTheme(
    colors: HyveExtendedColors? = null,
    content: @Composable () -> Unit,
) {
    SwingBridgeTheme {
        val resolvedColors = colors ?: if (JewelTheme.isDark) darkColors() else lightColors()
        CompositionLocalProvider(
            LocalHyveColors provides resolvedColors,
        ) {
            content()
        }
    }
}

/**
 * Extension point interface for providing custom color overrides.
 * Plugins can register implementations to customize Hyve colors dynamically.
 */
interface HyveColorOverride {
    /**
     * Returns custom colors or null to use defaults.
     */
    fun getColors(): HyveExtendedColors?
}
