// Copyright 2026 Hyve. All rights reserved.
package com.hyve.knowledge.actions

import com.hyve.common.settings.HytaleInstallPath
import com.hyve.knowledge.index.ClientUIIndexerTask
import com.hyve.knowledge.index.DocsIndexerTask
import com.hyve.knowledge.index.GameDataIndexerTask
import com.hyve.knowledge.index.IndexerTask
import com.hyve.knowledge.settings.KnowledgeSettings
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task

/**
 * Runs all 4 corpus indexers sequentially in one background task.
 * Progress: 0-25% code, 25-50% game data, 50-75% client, 75-100% docs.
 *
 * Ordering is intentional — edge-building phases depend on prior corpora:
 *  1. Code corpus (nodes + code edges — no cross-corpus deps)
 *  2. Gamedata corpus (IMPLEMENTED_BY edges target JavaClass nodes from step 1)
 *  3. Client corpus (future UI_BINDS_TO edges target gamedata nodes from step 2)
 *  4. Docs corpus (DOCS_REFERENCES edges target both code and gamedata nodes)
 */
class BuildAllIndexAction : AnAction() {

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        if (!MemoryCheckUtil.checkHeapAndWarn(project)) return
        ProgressManager.getInstance().run(BuildAllTask(project))
    }

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = e.project != null
    }

    private class BuildAllTask(
        project: com.intellij.openapi.project.Project,
    ) : Task.Backgroundable(project, "Building All Knowledge Indices...", true) {

        private val log = Logger.getInstance(BuildAllTask::class.java)
        private val results = mutableListOf<String>()

        override fun run(indicator: ProgressIndicator) {
            indicator.isIndeterminate = false
            val settings = KnowledgeSettings.getInstance()

            // ── Code corpus (0-25%) ─────────────────────────────
            val decompileDir = settings.resolvedDecompilePath()
            if (decompileDir.exists() && decompileDir.listFiles()?.isNotEmpty() == true) {
                indicator.text = "Indexing server code..."
                indicator.fraction = 0.0
                try {
                    val task = IndexerTask(project)
                    task.run(indicator)
                    results.add("Code: indexed")
                } catch (e: Exception) {
                    log.warn("Code indexing failed", e)
                    results.add("Code: failed (${e.message})")
                }
            } else {
                results.add("Code: skipped (no decompiled source)")
            }

            if (indicator.isCanceled) return

            // ── Game Data corpus (25-50%) ────────────────────────
            val assetsZip = HytaleInstallPath.assetsZipPath()
            if (assetsZip != null && assetsZip.toFile().exists()) {
                indicator.text = "Indexing game data..."
                indicator.fraction = 0.25
                try {
                    val task = GameDataIndexerTask(project)
                    task.run(indicator)
                    results.add("Game Data: indexed")
                } catch (e: Exception) {
                    log.warn("Game data indexing failed", e)
                    results.add("Game Data: failed (${e.message})")
                }
            } else {
                results.add("Game Data: skipped (Assets.zip not found)")
            }

            if (indicator.isCanceled) return

            // ── Client UI corpus (50-75%) ────────────────────────
            val clientFolder = HytaleInstallPath.clientFolderPath()
            if (clientFolder != null && clientFolder.toFile().exists()) {
                indicator.text = "Indexing client UI..."
                indicator.fraction = 0.50
                try {
                    val task = ClientUIIndexerTask(project)
                    task.run(indicator)
                    results.add("Client UI: indexed")
                } catch (e: Exception) {
                    log.warn("Client UI indexing failed", e)
                    results.add("Client UI: failed (${e.message})")
                }
            } else {
                results.add("Client UI: skipped (Client folder not found)")
            }

            if (indicator.isCanceled) return

            // ── Docs corpus (75-100%) ────────────────────────────
            indicator.text = "Indexing modding docs..."
            indicator.fraction = 0.75
            try {
                val task = DocsIndexerTask(project)
                task.run(indicator)
                results.add("Docs: indexed")
            } catch (e: Exception) {
                log.warn("Docs indexing failed", e)
                results.add("Docs: failed (${e.message})")
            }

            indicator.fraction = 1.0
        }

        override fun onSuccess() {
            NotificationGroupManager.getInstance()
                .getNotificationGroup("Hyve Knowledge")
                .createNotification(
                    "All knowledge indices built",
                    results.joinToString("\n"),
                    NotificationType.INFORMATION,
                )
                .notify(project)
        }

        override fun onThrowable(error: Throwable) {
            log.error("Build all indices failed", error)
            NotificationGroupManager.getInstance()
                .getNotificationGroup("Hyve Knowledge")
                .createNotification(
                    "Build all indices failed",
                    error.message ?: "Unknown error",
                    NotificationType.ERROR,
                )
                .notify(project)
        }
    }
}
