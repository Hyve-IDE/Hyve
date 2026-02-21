// Copyright 2026 Hyve. All rights reserved.
package com.hyve.common.action

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import javax.swing.Icon

/**
 * Base class for Hyve actions that provides common utilities.
 *
 * Features:
 * - Convenience accessors for project, file, editor
 * - Proper action update thread (BGT by default)
 * - DumbAware by default (works during indexing)
 *
 * Usage:
 * ```kotlin
 * class MyAction : HyveAction("My Action", "Description", AllIcons.Actions.Execute) {
 *
 *     override fun actionPerformed(e: AnActionEvent) {
 *         val project = e.project ?: return
 *         // Do something
 *     }
 *
 *     override fun update(e: AnActionEvent) {
 *         e.presentation.isEnabledAndVisible = e.project != null
 *     }
 * }
 * ```
 *
 * Then register in plugin.xml:
 * ```xml
 * <action id="Hyve.MyAction"
 *         class="com.example.MyAction"
 *         text="My Action"
 *         description="Description"
 *         icon="AllIcons.Actions.Execute">
 *     <keyboard-shortcut keymap="$default" first-keystroke="ctrl shift M"/>
 * </action>
 * ```
 */
abstract class HyveAction(
    text: String? = null,
    description: String? = null,
    icon: Icon? = null,
) : AnAction(text, description, icon), DumbAware {

    /**
     * Use background thread for update() by default.
     * Override to ActionUpdateThread.EDT if you need UI thread access.
     */
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
}

/**
 * Base class for actions that require a project to be open.
 */
abstract class HyveProjectAction(
    text: String? = null,
    description: String? = null,
    icon: Icon? = null,
) : HyveAction(text, description, icon) {

    /**
     * Called when the action is performed with a valid project.
     */
    abstract fun performAction(project: Project, e: AnActionEvent)

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        performAction(project, e)
    }

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = e.project != null
    }
}

/**
 * Base class for actions that operate on a file.
 */
abstract class HyveFileAction(
    text: String? = null,
    description: String? = null,
    icon: Icon? = null,
) : HyveAction(text, description, icon) {

    /**
     * Called when the action is performed with a valid project and file.
     */
    abstract fun performAction(project: Project, file: VirtualFile, e: AnActionEvent)

    /**
     * Override to filter which files this action applies to.
     */
    open fun isApplicable(file: VirtualFile): Boolean = true

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val file = e.getData(CommonDataKeys.VIRTUAL_FILE) ?: return
        if (isApplicable(file)) {
            performAction(project, file, e)
        }
    }

    override fun update(e: AnActionEvent) {
        val project = e.project
        val file = e.getData(CommonDataKeys.VIRTUAL_FILE)
        e.presentation.isEnabledAndVisible = project != null && file != null && isApplicable(file)
    }
}

/**
 * Base class for toggleable actions.
 */
abstract class HyveToggleAction(
    text: String? = null,
    description: String? = null,
    icon: Icon? = null,
) : HyveAction(text, description, icon) {

    /**
     * Returns whether the action is currently selected/enabled.
     */
    abstract fun isSelected(e: AnActionEvent): Boolean

    /**
     * Called when the action is toggled.
     */
    abstract fun setSelected(e: AnActionEvent, state: Boolean)

    override fun actionPerformed(e: AnActionEvent) {
        setSelected(e, !isSelected(e))
    }

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabled = true
        // The icon state will be updated automatically by IntelliJ
    }
}
