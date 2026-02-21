// Copyright 2026 Hyve. All rights reserved.
package com.hyve.knowledge.actions

import com.hyve.common.settings.HytaleInstallPath
import com.hyve.knowledge.index.GameDataIndexerTask
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.ui.Messages

class IndexGameDataAction : AnAction() {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val zipPath = HytaleInstallPath.assetsZipPath()

        if (zipPath == null || !zipPath.toFile().exists()) {
            Messages.showErrorDialog(
                project,
                "Assets.zip not found at:\n${zipPath ?: "<not configured>"}\n\nConfigure the Hytale install path in Settings first.",
                "Assets.zip Not Found",
            )
            return
        }

        ProgressManager.getInstance().run(GameDataIndexerTask(project))
    }

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = e.project != null
    }
}
