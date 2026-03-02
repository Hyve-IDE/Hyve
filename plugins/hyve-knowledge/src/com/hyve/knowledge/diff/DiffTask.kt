// Copyright 2026 Hyve. All rights reserved.
package com.hyve.knowledge.diff

import com.hyve.knowledge.core.diff.DiffCache
import com.hyve.knowledge.core.diff.DiffEngine
import com.hyve.knowledge.core.diff.VersionDiff
import com.hyve.knowledge.settings.KnowledgeSettings
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import java.io.File

/**
 * Background task that computes a version diff.
 */
class DiffTask(
    project: Project,
    private val versionA: String,
    private val versionB: String,
    private val onComplete: (VersionDiff) -> Unit,
) : Task.Backgroundable(project, "Computing Version Diff...", true) {

    private val log = Logger.getInstance(DiffTask::class.java)
    private var result: VersionDiff? = null

    override fun run(indicator: ProgressIndicator) {
        indicator.isIndeterminate = true
        indicator.text = "Comparing $versionA vs $versionB..."

        val settings = KnowledgeSettings.getInstance()
        val basePath = settings.resolvedBasePath()

        val dbFileA = File(basePath, "versions/$versionA/knowledge.db")
        val dbFileB = File(basePath, "versions/$versionB/knowledge.db")

        if (!dbFileA.exists()) throw IllegalStateException("Version A database not found: ${dbFileA.absolutePath}")
        if (!dbFileB.exists()) throw IllegalStateException("Version B database not found: ${dbFileB.absolutePath}")

        // Check cache first
        val cache = DiffCache(basePath)
        val cached = cache.get(versionA, versionB)
        if (cached != null) {
            log.info("Using cached diff for $versionA vs $versionB")
            result = cached
            return
        }

        indicator.text = "Computing diff (this may take a moment)..."
        val engine = DiffEngine()
        val diff = engine.computeDiff(
            versionA = versionA,
            versionB = versionB,
            dbFileA = dbFileA,
            dbFileB = dbFileB,
        )

        cache.put(diff)
        result = diff
    }

    override fun onSuccess() {
        result?.let(onComplete)
    }

    override fun onThrowable(error: Throwable) {
        log.error("Diff computation failed", error)
        NotificationGroupManager.getInstance()
            .getNotificationGroup("Hyve Knowledge")
            .createNotification(
                "Version diff failed",
                error.message ?: "Unknown error",
                NotificationType.ERROR,
            )
            .notify(project)
    }

    companion object {
        fun run(project: Project, versionA: String, versionB: String, onComplete: (VersionDiff) -> Unit) {
            ProgressManager.getInstance().run(DiffTask(project, versionA, versionB, onComplete))
        }
    }
}
