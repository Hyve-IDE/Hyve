// Copyright 2026 Hyve. All rights reserved.
package com.hyve.knowledge.docs

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task

/**
 * Action to manually trigger documentation sync.
 * Available from the Hytale Knowledge gear menu and the Hytale Docs tool window.
 */
class SyncDocsAction : AnAction(
    "Sync Offline Documentation\u2026",
    "Download latest documentation from HytaleModding for offline browsing",
    null
) {
    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return

        ProgressManager.getInstance().run(object : Task.Backgroundable(
            project,
            "Syncing Hytale Documentation",
            true
        ) {
            override fun run(indicator: ProgressIndicator) {
                indicator.isIndeterminate = false

                val result = DocsSyncService.getInstance().sync { current, total, file ->
                    indicator.fraction = current.toDouble() / total
                    indicator.text2 = "$file ($current/$total)"
                }

                when (result) {
                    is SyncResult.Success -> {
                        NotificationGroupManager.getInstance()
                            .getNotificationGroup("Hyve Knowledge")
                            .createNotification(
                                "Documentation Synced",
                                "Downloaded ${result.downloaded} files (${result.total} total).",
                                NotificationType.INFORMATION
                            )
                            .notify(project)
                    }
                    is SyncResult.Error -> {
                        NotificationGroupManager.getInstance()
                            .getNotificationGroup("Hyve Knowledge")
                            .createNotification(
                                "Documentation Sync Failed",
                                result.message,
                                NotificationType.WARNING
                            )
                            .notify(project)
                    }
                    is SyncResult.AlreadyRunning -> {
                        NotificationGroupManager.getInstance()
                            .getNotificationGroup("Hyve Knowledge")
                            .createNotification(
                                "Documentation Sync",
                                "A sync is already in progress.",
                                NotificationType.INFORMATION
                            )
                            .notify(project)
                    }
                }
            }
        })
    }
}
