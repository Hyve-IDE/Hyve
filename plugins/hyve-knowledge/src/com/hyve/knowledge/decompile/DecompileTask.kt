// Copyright 2026 Hyve. All rights reserved.
package com.hyve.knowledge.decompile

import com.hyve.knowledge.settings.KnowledgeSettings
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import java.io.File

/**
 * Background task for the manual "Decompile Hytale Server" action.
 * Delegates to [DecompileService] for the actual work.
 */
class DecompileTask(
    project: Project,
    private val serverJar: File,
) : Task.Backgroundable(project, "Decompiling Hytale Server...", true) {

    private val log = Logger.getInstance(DecompileTask::class.java)

    override fun run(indicator: ProgressIndicator) {
        val settings = KnowledgeSettings.getInstance()
        val outputDir = settings.resolvedDecompilePath()

        DecompileService.decompile(serverJar, outputDir, indicator)
        DecompileService.writeDecompileMeta(serverJar, outputDir)
    }

    override fun onSuccess() {
        val settings = KnowledgeSettings.getInstance()
        val outputDir = settings.resolvedDecompilePath()
        val count = outputDir.walkTopDown().count { it.extension == "java" }

        NotificationGroupManager.getInstance()
            .getNotificationGroup("Hyve Knowledge")
            .createNotification(
                "Decompilation complete",
                "$count Java files written to ${outputDir.absolutePath}",
                NotificationType.INFORMATION,
            )
            .notify(project)
    }

    override fun onThrowable(error: Throwable) {
        log.error("Decompilation failed", error)
        NotificationGroupManager.getInstance()
            .getNotificationGroup("Hyve Knowledge")
            .createNotification(
                "Decompilation failed",
                error.message ?: "Unknown error",
                NotificationType.ERROR,
            )
            .notify(project)
    }
}
