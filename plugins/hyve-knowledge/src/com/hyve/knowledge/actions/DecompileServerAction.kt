// Copyright 2026 Hyve. All rights reserved.
package com.hyve.knowledge.actions

import com.hyve.common.settings.HytaleInstallPath
import com.hyve.knowledge.decompile.DecompileTask
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.ui.Messages

class DecompileServerAction : AnAction() {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return

        val serverJar = HytaleInstallPath.serverJarPath()?.toFile()
        if (serverJar == null || !serverJar.exists()) {
            val installPath = HytaleInstallPath.get()
            val detail = if (installPath == null) {
                "No Hytale install path configured.\nGo to Settings → Hyve to set it."
            } else {
                "HytaleServer.jar not found at:\n${installPath.resolve("Server/HytaleServer.jar")}"
            }
            Messages.showErrorDialog(project, detail, "Hytale Server Not Found")
            return
        }

        val result = Messages.showOkCancelDialog(
            project,
            "Decompile ${serverJar.name}?\n\nThis will use FernFlower to decompile the server JAR.\nIt may take 2-5 minutes.",
            "Decompile Hytale Server",
            "Decompile",
            "Cancel",
            Messages.getQuestionIcon(),
        )
        if (result != Messages.OK) return

        ProgressManager.getInstance().run(DecompileTask(project, serverJar))
    }

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = e.project != null
    }
}
