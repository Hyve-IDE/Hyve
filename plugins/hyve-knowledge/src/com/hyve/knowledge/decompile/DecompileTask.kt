// Copyright 2026 Hyve. All rights reserved.
package com.hyve.knowledge.decompile

import com.hyve.knowledge.settings.KnowledgeSettings
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import org.jetbrains.java.decompiler.main.decompiler.BaseDecompiler
import org.jetbrains.java.decompiler.main.extern.IFernflowerLogger
import org.jetbrains.java.decompiler.main.extern.IFernflowerPreferences
import java.io.File

/**
 * Background task that decompiles HytaleServer.jar using FernFlower,
 * then applies post-processing fixes to the output.
 */
class DecompileTask(
    project: Project,
    private val serverJar: File,
) : Task.Backgroundable(project, "Decompiling Hytale Server...", true) {

    private val log = Logger.getInstance(DecompileTask::class.java)

    override fun run(indicator: ProgressIndicator) {
        indicator.isIndeterminate = true
        indicator.text = "Preparing decompilation..."

        val settings = KnowledgeSettings.getInstance()
        val outputDir = settings.resolvedDecompilePath()
        outputDir.mkdirs()

        log.info("Decompiling ${serverJar.name} → $outputDir")

        // Phase 1: Decompile with FernFlower (no granular progress — single blocking call)
        indicator.text = "Decompiling ${serverJar.name}..."

        val provider = JarBytecodeProvider(serverJar)
        val saver = DirectoryResultSaver(outputDir)
        val logger = IndicatorLogger(indicator)

        val options = HashMap<String, Any>(IFernflowerPreferences.DEFAULTS).apply {
            put(IFernflowerPreferences.BYTECODE_SOURCE_MAPPING, "1")
            put(IFernflowerPreferences.DECOMPILE_GENERIC_SIGNATURES, "1")
            put(IFernflowerPreferences.REMOVE_BRIDGE, "1")
            put(IFernflowerPreferences.REMOVE_SYNTHETIC, "1")
            put(IFernflowerPreferences.DECOMPILE_INNER, "1")
            put(IFernflowerPreferences.DECOMPILE_ENUM, "1")
            put(IFernflowerPreferences.USE_DEBUG_VAR_NAMES, "1")
            put(IFernflowerPreferences.LOG_LEVEL, IFernflowerLogger.Severity.INFO.name)
            put(IFernflowerPreferences.INDENT_STRING, "    ")
        }

        val decompiler = BaseDecompiler(provider, saver, options, logger)
        decompiler.addSource(serverJar)

        try {
            decompiler.decompileContext()
        } finally {
            provider.close()
        }

        if (indicator.isCanceled) return

        log.info("FernFlower wrote ${saver.filesWritten} Hytale files, skipped ${saver.filesSkipped} dependency files")

        // Phase 2: Apply decompilation fixes (now we know the file count)
        indicator.isIndeterminate = false
        indicator.fraction = 0.0
        indicator.text = "Applying decompilation fixes..."

        val javaFiles = outputDir.walkTopDown()
            .filter { it.extension == "java" }
            .toList()

        var fixedCount = 0
        javaFiles.forEachIndexed { idx, file ->
            if (indicator.isCanceled) return
            indicator.text2 = file.name
            indicator.fraction = idx.toDouble() / javaFiles.size.coerceAtLeast(1)
            if (DecompilationFixes.applyToFile(file)) {
                fixedCount++
            }
        }

        log.info("Applied fixes to $fixedCount / ${javaFiles.size} files")
        indicator.fraction = 1.0
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

    /**
     * FernFlower logger that updates the progress indicator and logs via IntelliJ.
     */
    private class IndicatorLogger(
        private val indicator: ProgressIndicator,
    ) : IFernflowerLogger() {

        private val log = Logger.getInstance(DecompileTask::class.java)
        private var currentClass: String = ""

        private fun isHytaleClass(): Boolean =
            currentClass.startsWith("com/hypixel/hytale/")

        override fun writeMessage(message: String, severity: Severity) {
            when (severity) {
                Severity.TRACE -> log.trace(message)
                Severity.INFO -> log.info(message)
                Severity.WARN -> log.warn(message)
                Severity.ERROR -> {
                    // Errors in Hytale classes matter (RAG depends on them).
                    // Errors in dependencies are expected (obfuscated bytecode) — just warn.
                    if (isHytaleClass()) log.error(message) else log.warn(message)
                }
            }
        }

        override fun writeMessage(message: String, severity: Severity, t: Throwable) {
            if (isHytaleClass()) log.error(message, t) else log.warn(message, t)
        }

        override fun startClass(className: String) {
            currentClass = className
            indicator.text2 = className
        }
    }
}
