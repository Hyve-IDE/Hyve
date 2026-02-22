// Copyright 2026 Hyve. All rights reserved.
package com.hyve.knowledge.actions

import com.hyve.common.settings.HytaleInstallPath
import com.hyve.knowledge.index.ClientUIIndexerTask
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.ui.Messages
import java.io.File

class IndexClientUIAction : AnAction() {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return

        val clientPath = HytaleInstallPath.clientFolderPath()?.toFile()
        if (clientPath == null || !clientPath.exists()) {
            val installPath = HytaleInstallPath.get()
            val detail = if (installPath == null) {
                "No Hytale install path configured.\nGo to Settings → Hyve to set it."
            } else {
                "Client folder not found at:\n${File(installPath.toFile(), "Client").absolutePath}"
            }
            Messages.showErrorDialog(project, detail, "Hytale Client Not Found")
            return
        }

        val clientDataPath = File(clientPath, "Data")
        if (!clientDataPath.exists()) {
            Messages.showErrorDialog(
                project,
                "Client Data directory not found at:\n${clientDataPath.absolutePath}",
                "Client Data Not Found",
            )
            return
        }

        if (!MemoryCheckUtil.checkHeapAndWarn(project)) return

        ProgressManager.getInstance().run(ClientUIIndexerTask(project))
    }

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = e.project != null
    }
}
