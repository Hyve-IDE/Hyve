// Copyright 2026 Hyve. All rights reserved.
package com.hyve.knowledge.actions

import com.hyve.knowledge.index.IndexerTask
import com.hyve.knowledge.settings.KnowledgeSettings
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.ui.Messages

class BuildIndexAction : AnAction() {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val settings = KnowledgeSettings.getInstance()
        val decompileDir = settings.resolvedDecompilePath()

        if (!decompileDir.exists() || decompileDir.listFiles()?.isEmpty() != false) {
            Messages.showErrorDialog(
                project,
                "No decompiled source found at:\n${decompileDir.absolutePath}\n\nRun 'Decompile Hytale Server' first.",
                "No Decompiled Source",
            )
            return
        }

        ProgressManager.getInstance().run(IndexerTask(project))
    }

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = e.project != null
    }
}
