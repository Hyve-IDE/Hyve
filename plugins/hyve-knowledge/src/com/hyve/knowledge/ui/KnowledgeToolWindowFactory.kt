// Copyright 2026 Hyve. All rights reserved.
package com.hyve.knowledge.ui

import androidx.compose.runtime.Composable
import com.hyve.common.compose.SimpleHyveToolWindowFactory
import com.hyve.knowledge.settings.KnowledgeSettingsConfigurable
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow

class KnowledgeToolWindowFactory : SimpleHyveToolWindowFactory() {

    override val tabDisplayName: String? = null

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        toolWindow.setAdditionalGearActions(createGearActions(project))
        super.createToolWindowContent(project, toolWindow)
    }

    @Composable
    override fun Content(project: Project) {
        KnowledgeSearchPanel(project)
    }

    private fun createGearActions(project: Project): ActionGroup {
        return DefaultActionGroup().apply {
            // Decompile
            add(ActionManager.getInstance().getAction("HyveDecompileServer"))
            addSeparator()
            // Index actions
            add(ActionManager.getInstance().getAction("HyveBuildIndex"))
            add(ActionManager.getInstance().getAction("HyveIndexGameData"))
            add(ActionManager.getInstance().getAction("HyveIndexClientUI"))
            add(ActionManager.getInstance().getAction("HyveIndexDocs"))
            addSeparator()
            add(ActionManager.getInstance().getAction("HyveBuildAllIndices"))
            addSeparator()
            // Settings
            add(object : DumbAwareAction("Hyve Knowledge Settings\u2026") {
                override fun actionPerformed(e: AnActionEvent) {
                    ShowSettingsUtil.getInstance()
                        .showSettingsDialog(project, KnowledgeSettingsConfigurable::class.java)
                }
            })
        }
    }
}
