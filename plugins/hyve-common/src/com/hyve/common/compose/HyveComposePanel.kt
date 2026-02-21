// Copyright 2026 Hyve. All rights reserved.
package com.hyve.common.compose

import androidx.compose.runtime.Composable
import org.jetbrains.jewel.bridge.JewelComposePanel
import org.jetbrains.jewel.foundation.ExperimentalJewelApi
import org.jetbrains.jewel.foundation.enableNewSwingCompositing
import javax.swing.JComponent

/**
 * Creates a Swing component that hosts Compose content with Hyve theming.
 *
 * This is a convenience wrapper around JewelComposePanel that automatically
 * applies the HyveTheme.
 *
 * Usage:
 * ```kotlin
 * val panel = hyveComposePanel {
 *     val honey = HyveThemeColors.colors.honey
 *     Text("Hello from Hyve!", color = honey)
 * }
 * mySwingContainer.add(panel)
 * ```
 *
 * @param focusOnClickInside If true, clicking inside the panel requests focus
 *                           even if the click doesn't hit a focusable element.
 * @param content The Composable content to display.
 * @return A JComponent that can be added to Swing containers.
 */
@OptIn(ExperimentalJewelApi::class)
fun hyveComposePanel(
    focusOnClickInside: Boolean = true,
    content: @Composable () -> Unit,
): JComponent {
    enableNewSwingCompositing()
    return JewelComposePanel(focusOnClickInside) {
        HyveTheme {
            content()
        }
    }
}

/**
 * Creates a Swing component that hosts Compose content without Hyve theming.
 *
 * Use this when you want to use a custom theme or the raw Jewel SwingBridgeTheme.
 *
 * @param focusOnClickInside If true, clicking inside the panel requests focus.
 * @param content The Composable content to display.
 * @return A JComponent that can be added to Swing containers.
 */
@OptIn(ExperimentalJewelApi::class)
fun jewelComposePanel(
    focusOnClickInside: Boolean = true,
    content: @Composable () -> Unit,
): JComponent {
    enableNewSwingCompositing()
    return JewelComposePanel(focusOnClickInside, content = content)
}
