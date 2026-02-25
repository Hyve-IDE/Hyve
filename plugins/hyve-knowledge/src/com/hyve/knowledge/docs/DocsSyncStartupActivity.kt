// Copyright 2026 Hyve. All rights reserved.
package com.hyve.knowledge.docs

import com.hyve.knowledge.settings.KnowledgeSettings
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity

/**
 * Syncs Hytale documentation on IDE startup when the user has enabled it.
 *
 * Runs as a coroutine on a background thread (ProjectActivity contract),
 * so no EDT blocking occurs.
 */
class DocsSyncStartupActivity : ProjectActivity {

    private val log = Logger.getInstance(DocsSyncStartupActivity::class.java)

    override suspend fun execute(project: Project) {
        val settings = KnowledgeSettings.getInstance()
        if (!settings.state.syncOfflineDocsOnStart) return

        log.info("Starting automatic documentation sync")

        val result = DocsSyncService.getInstance().sync { current, total, file ->
            log.debug("Syncing docs: $current/$total — $file")
        }

        when (result) {
            is SyncResult.Success -> {
                if (result.downloaded > 0 || result.deleted > 0) {
                    notify(
                        project,
                        "Documentation Updated",
                        "Synced ${result.downloaded} docs (${result.total} total).",
                        NotificationType.INFORMATION
                    )
                }
                // Silent when nothing changed
            }
            is SyncResult.Error -> {
                notify(
                    project,
                    "Documentation Sync",
                    result.message,
                    NotificationType.WARNING
                )
            }
            is SyncResult.AlreadyRunning -> {
                // Another sync is in progress — silently skip
            }
        }
    }

    private fun notify(project: Project, title: String, content: String, type: NotificationType) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup("Hyve Knowledge")
            .createNotification(title, content, type)
            .notify(project)
    }
}
