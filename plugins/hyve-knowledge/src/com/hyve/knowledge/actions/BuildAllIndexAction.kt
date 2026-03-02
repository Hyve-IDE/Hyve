// Copyright 2026 Hyve. All rights reserved.
package com.hyve.knowledge.actions

import com.hyve.common.settings.HytaleInstallPath
import com.hyve.common.settings.HytaleVersionDetector
import com.hyve.knowledge.bridge.KnowledgeDatabaseFactory
import com.hyve.knowledge.decompile.DecompileService
import com.hyve.knowledge.bridge.toConfig
import com.hyve.knowledge.core.config.KnowledgeConfig
import com.hyve.knowledge.index.ClientUIIndexerTask
import com.hyve.knowledge.index.DocsIndexerTask
import com.hyve.knowledge.index.GameDataIndexerTask
import com.hyve.knowledge.index.IndexerTask
import com.hyve.knowledge.settings.KnowledgeSettings
import com.intellij.notification.Notification
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import kotlinx.serialization.json.Json
import java.io.File
import java.nio.file.Path
import java.time.Instant

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

            // ── Version detection ────────────────────────────────
            indicator.text = "Detecting game version..."
            val versionInfo = HytaleVersionDetector.detectFromInstall()
            if (versionInfo != null) {
                val slug = versionInfo.slug
                log.info("Detected Hytale version: ${versionInfo.displayName} (slug=$slug)")

                // Register in known versions
                val knownSet = parseKnownVersions(settings.state.knownVersions).toMutableSet()
                knownSet.add(slug)
                settings.state.knownVersions = Json.encodeToString(knownSet.toList())

                // Set as active version (creates version-specific paths)
                settings.state.activeVersion = slug
                KnowledgeDatabaseFactory.resetInstance()

                // Ensure version directory exists
                val versionDir = settings.resolvedIndexPath()
                versionDir.mkdirs()

                results.add("Version: ${versionInfo.displayName}")
            } else {
                log.info("Could not detect game version, using current active version")
                if (settings.state.activeVersion.isBlank()) {
                    results.add("Version: legacy (unversioned)")
                } else {
                    results.add("Version: ${settings.state.activeVersion}")
                }
            }

            // Write MCP config so standalone server picks up activeVersion
            KnowledgeConfig.writeToFile(settings.toConfig())

            // ── Code corpus (0-25%) ─────────────────────────────
            val serverJar = HytaleInstallPath.serverJarPath()?.toFile()
            val decompileDir = settings.resolvedDecompilePath()

            // Auto-decompile if JAR exists but decompiled source is missing or stale
            if (serverJar != null && serverJar.exists() && DecompileService.isStale(serverJar, decompileDir)) {
                indicator.text = "Decompiling server code..."
                try {
                    DecompileService.decompile(serverJar, decompileDir, indicator)
                    DecompileService.writeDecompileMeta(serverJar, decompileDir)
                } catch (e: Exception) {
                    log.warn("Auto-decompilation failed", e)
                    results.add("Code: decompilation failed (${e.message})")
                }
            }

            if (decompileDir.exists() && decompileDir.listFiles()?.any { it.name != "decompile_meta.json" } == true) {
                indicator.text = "Indexing server code..."
                indicator.isIndeterminate = false
                indicator.fraction = 0.0
                try {
                    val task = IndexerTask(project)
                    task.run(indicator)
                    results.add("Code: indexed")
                } catch (e: Exception) {
                    log.warn("Code indexing failed", e)
                    results.add("Code: failed (${e.message})")
                }
            } else if (!results.any { it.startsWith("Code:") }) {
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

            // ── Write version metadata ────────────────────────────
            if (versionInfo != null) {
                writeVersionMeta(settings.resolvedIndexPath(), versionInfo)
            }

            indicator.fraction = 1.0
        }

        private fun writeVersionMeta(
            versionDir: File,
            info: HytaleVersionDetector.HytaleVersionInfo,
        ) {
            val meta = buildString {
                appendLine("{")
                appendLine("""  "patchline": "${info.patchline}",""")
                appendLine("""  "version": "${info.rawVersion}",""")
                appendLine("""  "date": "${info.date}",""")
                appendLine("""  "shortHash": "${info.shortHash}",""")
                appendLine("""  "fullRevision": "${info.fullRevision}",""")
                appendLine("""  "slug": "${info.slug}",""")
                appendLine("""  "indexedAt": "${Instant.now()}" """)
                appendLine("}")
            }
            File(versionDir, "version_meta.json").writeText(meta)
        }

        private fun parseKnownVersions(json: String): List<String> {
            if (json.isBlank()) return emptyList()
            return try {
                Json.decodeFromString<List<String>>(json)
            } catch (_: Exception) {
                emptyList()
            }
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

            // Check for un-indexed sibling patchlines (e.g. pre-release alongside release)
            checkForSiblingPatchlines()
        }

        private fun checkForSiblingPatchlines() {
            val siblings = HytaleVersionDetector.discoverSiblingPatchlines()
            if (siblings.isEmpty()) return

            val settings = KnowledgeSettings.getInstance()
            val knownVersions = parseKnownVersions(settings.state.knownVersions).toSet()

            val unindexed = siblings.filter { it.versionInfo.slug !in knownVersions }
            if (unindexed.isEmpty()) return

            for (sibling in unindexed) {
                val info = sibling.versionInfo
                NotificationGroupManager.getInstance()
                    .getNotificationGroup("Hyve Knowledge")
                    .createNotification(
                        "${info.patchline} build detected",
                        "${info.displayName} is installed but not yet indexed.",
                        NotificationType.INFORMATION,
                    )
                    .addAction(object : NotificationAction("Index ${info.patchline}") {
                        override fun actionPerformed(e: AnActionEvent, notification: Notification) {
                            notification.expire()
                            indexSiblingPatchline(sibling.installPath, info)
                        }
                    })
                    .notify(project)
            }
        }

        private fun indexSiblingPatchline(
            siblingInstallPath: Path,
            siblingInfo: HytaleVersionDetector.HytaleVersionInfo,
        ) {
            val proj = project ?: return
            ProgressManager.getInstance().run(SiblingIndexTask(proj, siblingInstallPath, siblingInfo))
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

    /**
     * Indexes a sibling patchline by temporarily swapping the install path.
     * After completion (success or failure), restores the original path and active version.
     */
    private class SiblingIndexTask(
        project: com.intellij.openapi.project.Project,
        private val siblingInstallPath: Path,
        private val siblingInfo: HytaleVersionDetector.HytaleVersionInfo,
    ) : Task.Backgroundable(project, "Indexing ${siblingInfo.displayName}...", true) {

        private val log = Logger.getInstance(SiblingIndexTask::class.java)
        private val results = mutableListOf<String>()
        private var originalInstallPath: Path? = null
        private var originalActiveVersion: String = ""

        override fun run(indicator: ProgressIndicator) {
            indicator.isIndeterminate = false
            val settings = KnowledgeSettings.getInstance()

            // Save original state
            originalInstallPath = HytaleInstallPath.get()
            originalActiveVersion = settings.state.activeVersion

            // Swap to sibling install path
            HytaleInstallPath.set(siblingInstallPath)

            val slug = siblingInfo.slug
            log.info("Indexing sibling patchline: ${siblingInfo.displayName} (slug=$slug)")

            // Register in known versions
            val knownSet = parseKnownVersions(settings.state.knownVersions).toMutableSet()
            knownSet.add(slug)
            settings.state.knownVersions = Json.encodeToString(knownSet.toList())

            // Set as active version for this indexing run
            settings.state.activeVersion = slug
            KnowledgeDatabaseFactory.resetInstance()

            val versionDir = settings.resolvedIndexPath()
            versionDir.mkdirs()
            results.add("Version: ${siblingInfo.displayName}")

            KnowledgeConfig.writeToFile(settings.toConfig())

            // ── Code corpus (0-25%) ─────────────────────────────
            val serverJar = HytaleInstallPath.serverJarPath()?.toFile()
            val decompileDir = settings.resolvedDecompilePath()

            // Auto-decompile if JAR exists but decompiled source is missing or stale
            if (serverJar != null && serverJar.exists() && DecompileService.isStale(serverJar, decompileDir)) {
                indicator.text = "Decompiling ${siblingInfo.patchline} server code..."
                try {
                    DecompileService.decompile(serverJar, decompileDir, indicator)
                    DecompileService.writeDecompileMeta(serverJar, decompileDir)
                } catch (e: Exception) {
                    log.warn("Auto-decompilation failed for ${siblingInfo.displayName}", e)
                    results.add("Code: decompilation failed (${e.message})")
                }
            }

            if (decompileDir.exists() && decompileDir.listFiles()?.any { it.name != "decompile_meta.json" } == true) {
                indicator.text = "Indexing ${siblingInfo.patchline} server code..."
                indicator.isIndeterminate = false
                indicator.fraction = 0.0
                try {
                    IndexerTask(project).run(indicator)
                    results.add("Code: indexed")
                } catch (e: Exception) {
                    log.warn("Code indexing failed for ${siblingInfo.displayName}", e)
                    results.add("Code: failed (${e.message})")
                }
            } else if (!results.any { it.startsWith("Code:") }) {
                results.add("Code: skipped (no decompiled source)")
            }

            if (indicator.isCanceled) return

            // ── Game Data corpus (25-50%) ────────────────────────
            val assetsZip = HytaleInstallPath.assetsZipPath()
            if (assetsZip != null && assetsZip.toFile().exists()) {
                indicator.text = "Indexing ${siblingInfo.patchline} game data..."
                indicator.fraction = 0.25
                try {
                    GameDataIndexerTask(project).run(indicator)
                    results.add("Game Data: indexed")
                } catch (e: Exception) {
                    log.warn("Game data indexing failed for ${siblingInfo.displayName}", e)
                    results.add("Game Data: failed (${e.message})")
                }
            } else {
                results.add("Game Data: skipped (Assets.zip not found)")
            }

            if (indicator.isCanceled) return

            // ── Client UI corpus (50-75%) ────────────────────────
            val clientFolder = HytaleInstallPath.clientFolderPath()
            if (clientFolder != null && clientFolder.toFile().exists()) {
                indicator.text = "Indexing ${siblingInfo.patchline} client UI..."
                indicator.fraction = 0.50
                try {
                    ClientUIIndexerTask(project).run(indicator)
                    results.add("Client UI: indexed")
                } catch (e: Exception) {
                    log.warn("Client UI indexing failed for ${siblingInfo.displayName}", e)
                    results.add("Client UI: failed (${e.message})")
                }
            } else {
                results.add("Client UI: skipped (Client folder not found)")
            }

            if (indicator.isCanceled) return

            // ── Docs corpus (75-100%) ────────────────────────────
            indicator.text = "Indexing ${siblingInfo.patchline} modding docs..."
            indicator.fraction = 0.75
            try {
                DocsIndexerTask(project).run(indicator)
                results.add("Docs: indexed")
            } catch (e: Exception) {
                log.warn("Docs indexing failed for ${siblingInfo.displayName}", e)
                results.add("Docs: failed (${e.message})")
            }

            // Write version metadata
            writeVersionMeta(versionDir, siblingInfo)
            indicator.fraction = 1.0
        }

        override fun onFinished() {
            // Always restore original state, even on failure/cancel
            val settings = KnowledgeSettings.getInstance()
            originalInstallPath?.let { HytaleInstallPath.set(it) }
            settings.state.activeVersion = originalActiveVersion
            KnowledgeDatabaseFactory.resetInstance()
            KnowledgeConfig.writeToFile(settings.toConfig())
        }

        override fun onSuccess() {
            NotificationGroupManager.getInstance()
                .getNotificationGroup("Hyve Knowledge")
                .createNotification(
                    "${siblingInfo.patchline} indices built",
                    results.joinToString("\n"),
                    NotificationType.INFORMATION,
                )
                .notify(project)
        }

        override fun onThrowable(error: Throwable) {
            log.error("Sibling indexing failed for ${siblingInfo.displayName}", error)
            NotificationGroupManager.getInstance()
                .getNotificationGroup("Hyve Knowledge")
                .createNotification(
                    "${siblingInfo.patchline} indexing failed",
                    error.message ?: "Unknown error",
                    NotificationType.ERROR,
                )
                .notify(project)
        }

        private fun writeVersionMeta(versionDir: File, info: HytaleVersionDetector.HytaleVersionInfo) {
            val meta = buildString {
                appendLine("{")
                appendLine("""  "patchline": "${info.patchline}",""")
                appendLine("""  "version": "${info.rawVersion}",""")
                appendLine("""  "date": "${info.date}",""")
                appendLine("""  "shortHash": "${info.shortHash}",""")
                appendLine("""  "fullRevision": "${info.fullRevision}",""")
                appendLine("""  "slug": "${info.slug}",""")
                appendLine("""  "indexedAt": "${Instant.now()}" """)
                appendLine("}")
            }
            File(versionDir, "version_meta.json").writeText(meta)
        }

        private fun parseKnownVersions(json: String): List<String> {
            if (json.isBlank()) return emptyList()
            return try { Json.decodeFromString<List<String>>(json) } catch (_: Exception) { emptyList() }
        }
    }
}
