// Copyright 2026 Hyve. All rights reserved.
package com.hyve.knowledge.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.wm.ToolWindowManager

/**
 * Action that opens the Version Diff panel in the Hytale Knowledge tool window.
 */
class CompareVersionsAction : AnAction() {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val toolWindow = ToolWindowManager.getInstance(project).getToolWindow("Hytale Knowledge")
        if (toolWindow != null) {
            toolWindow.activate {
                // The tool window will show the diff tab if present
                val contentManager = toolWindow.contentManager
                val diffContent = contentManager.findContent("Version Diff")
                if (diffContent != null) {
                    contentManager.setSelectedContent(diffContent)
                }
            }
        }
    }

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = e.project != null
    }
}
