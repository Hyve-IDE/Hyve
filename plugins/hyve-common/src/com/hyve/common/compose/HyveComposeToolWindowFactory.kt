// Copyright 2026 Hyve. All rights reserved.
package com.hyve.common.compose

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import org.jetbrains.jewel.bridge.addComposeTab
import org.jetbrains.jewel.bridge.ToolWindowScope
import org.jetbrains.jewel.foundation.ExperimentalJewelApi
import org.jetbrains.jewel.foundation.enableNewSwingCompositing
import org.jetbrains.jewel.ui.component.styling.LocalDefaultTabStyle

/**
 * Base class for Hyve tool windows that use Compose for their UI.
 *
 * This provides a simplified API for creating tool windows with:
 * - Automatic theme bridging via HyveTheme
 * - Proper lifecycle management
 * - Access to Hyve color palette
 *
 * Usage:
 * ```kotlin
 * class MyToolWindowFactory : HyveComposeToolWindowFactory() {
 *
 *     override val tabDisplayName: String = "My Tool"
 *
 *     @Composable
 *     override fun ToolWindowScope.Content(project: Project) {
 *         val honey = HyveThemeColors.colors.honey
 *         Text("Hello from Hyve!", color = honey)
 *     }
 * }
 * ```
 *
 * Then register in plugin.xml:
 * ```xml
 * <toolWindow
 *     id="My Tool"
 *     anchor="right"
 *     factoryClass="com.example.MyToolWindowFactory"
 *     icon="AllIcons.Toolwindows.ToolWindowProject"/>
 * ```
 */
@OptIn(ExperimentalJewelApi::class)
abstract class HyveComposeToolWindowFactory : ToolWindowFactory {

    /**
     * Display name for the tool window tab.
     * Override to customize. Returns null to use the default (no title).
     */
    open val tabDisplayName: String? = null

    /**
     * Whether the tab can be locked.
     */
    open val isLockable: Boolean = true

    /**
     * Whether the tab can be closed.
     */
    open val isCloseable: Boolean = false

    /**
     * Whether clicking inside the compose panel should request focus,
     * even if the click doesn't hit a focusable element.
     */
    open val focusOnClickInside: Boolean = true

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        enableNewSwingCompositing()

        toolWindow.addComposeTab(
            tabDisplayName = tabDisplayName,
            isLockable = isLockable,
            isCloseable = isCloseable,
            focusOnClickInside = focusOnClickInside,
        ) {
            HyveTheme {
                ToolWindowContent(project)
            }
        }
    }

    /**
     * Override this to provide your tool window content.
     * The content is wrapped in HyveTheme, so you can access:
     * - Jewel theme colors via JewelTheme
     * - Hyve colors via HyveThemeColors.colors
     *
     * @param project The current project
     */
    @Composable
    protected abstract fun ToolWindowScope.ToolWindowContent(project: Project)
}

/**
 * Simplified version for tool windows that don't need ToolWindowScope access.
 */
@OptIn(ExperimentalJewelApi::class)
abstract class SimpleHyveToolWindowFactory : HyveComposeToolWindowFactory() {

    @Composable
    override fun ToolWindowScope.ToolWindowContent(project: Project) {
        Content(project)
    }

    /**
     * Override this to provide your tool window content.
     */
    @Composable
    protected abstract fun Content(project: Project)
}
