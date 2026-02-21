// Copyright 2026 Hyve. All rights reserved.
package com.hyve.knowledge.index

import com.hyve.knowledge.bridge.EmbeddingProviderFactory
import com.hyve.knowledge.bridge.KnowledgeDatabaseFactory
import com.hyve.knowledge.core.db.EmbeddingPurpose
import com.hyve.knowledge.core.index.HnswIndex
import com.hyve.knowledge.extraction.JavaChunker
import com.hyve.knowledge.extraction.JavaExtractor
import com.hyve.knowledge.settings.KnowledgeSettings
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import kotlinx.coroutines.runBlocking
import java.io.File
import java.nio.file.Paths

/**
 * Background task that builds the knowledge index:
 * 1. Detect changes via file hashing (fast filesystem scan)
 * 2. If no changes, skip everything (instant)
 * 3. Parse only changed Java files into method-level chunks
 * 4. Embed new/changed chunks via configured embedding provider
 * 5. Build/update HNSW vector index
 * 6. Extract graph nodes + edges into SQLite
 */
class IndexerTask(
    project: Project,
) : Task.Backgroundable(project, "Building Knowledge Index...", true) {

    private val log = Logger.getInstance(IndexerTask::class.java)
    private var indexedCount = 0
    private var skipped = false

    override fun run(indicator: ProgressIndicator) {
        indicator.isIndeterminate = false
        val settings = KnowledgeSettings.getInstance()
        val db = KnowledgeDatabaseFactory.getInstance()
        val hashTracker = FileHashTracker(db)
        val decompileDir = settings.resolvedDecompilePath()
        val indexDir = settings.resolvedIndexPath()
        indexDir.mkdirs()

        // ── Phase 1: Detect changes (fast scan) ──────────────────
        indicator.text = "Detecting changes..."
        indicator.fraction = 0.0

        val changes = hashTracker.detectChanges(
            sourceDir = decompileDir,
            corpusType = "java",
            fileFilter = { it.startsWith("com/hypixel/hytale/") },
        )

        log.info("Changes: +${changes.added.size} ~${changes.changed.size} -${changes.deleted.size} =${changes.unchanged.size}")

        if (!changes.hasChanges && changes.unchanged.isNotEmpty()) {
            log.info("No code changes detected, skipping indexing")
            skipped = true
            indicator.fraction = 1.0
            return
        }

        if (indicator.isCanceled) return

        // ── Phase 2: Parse ──────────────────────────────────────
        indicator.text = "Parsing decompiled Java files..."
        indicator.fraction = 0.05

        val allChunks = JavaChunker.chunkDirectory(
            dir = decompileDir,
            pathFilter = { it.startsWith("com/hypixel/hytale/") },
            onProgress = { name, idx, total ->
                indicator.text2 = name
                indicator.fraction = 0.05 + (0.1 * idx / total.coerceAtLeast(1))
            },
        )
        log.info("Parsed ${allChunks.size} method chunks from ${decompileDir.name}")

        if (indicator.isCanceled) return

        // ── Phase 3: Filter to changed files ─────────────────────
        // On first run all files are "added", so changedPaths contains everything.
        // On incremental runs only truly changed files are included.
        val changedPaths = changes.added + changes.changed
        val chunksToEmbed = allChunks.filter { chunk ->
            val relPath = File(chunk.filePath).relativeTo(decompileDir).path.replace('\\', '/')
            relPath in changedPaths
        }

        if (indicator.isCanceled) return

        // ── Phase 4: Embed ──────────────────────────────────────
        val embeddings: List<FloatArray>
        if (chunksToEmbed.isNotEmpty()) {
            indicator.text = "Embedding ${chunksToEmbed.size} chunks..."
            indicator.fraction = 0.15

            val provider = EmbeddingProviderFactory.fromSettings(EmbeddingPurpose.CODE)
            runBlocking {
                provider.validate()
            }

            val texts = chunksToEmbed.map { it.embeddingText }
            val batchSize = 32
            val batches = texts.chunked(batchSize)
            val allEmbeddings = mutableListOf<FloatArray>()

            runBlocking {
                for ((batchIdx, batch) in batches.withIndex()) {
                    if (indicator.isCanceled) return@runBlocking
                    indicator.text2 = "Batch ${batchIdx + 1}/${batches.size}"
                    indicator.fraction = 0.15 + (0.55 * batchIdx / batches.size.coerceAtLeast(1))
                    allEmbeddings.addAll(provider.embed(batch))
                }
            }
            embeddings = allEmbeddings
        } else {
            embeddings = emptyList()
        }

        if (indicator.isCanceled) return

        // ── Phase 5: Write index ────────────────────────────────
        indicator.text = "Writing index..."
        indicator.fraction = 0.7

        // Delete stale data for changed/deleted files
        val stalePaths = changes.changed + changes.deleted
        if (stalePaths.isNotEmpty()) {
            hashTracker.removeHashes(stalePaths)
            db.inTransaction { conn ->
                val ps = conn.prepareStatement("DELETE FROM nodes WHERE owning_file = ?")
                for (path in stalePaths) {
                    ps.setString(1, path)
                    ps.addBatch()
                }
                ps.executeBatch()
            }
            db.inTransaction { conn ->
                val ps = conn.prepareStatement("DELETE FROM edges WHERE owning_file_id = ?")
                for (path in stalePaths) {
                    ps.setString(1, path)
                    ps.addBatch()
                }
                ps.executeBatch()
            }
        }

        // Insert new chunks as nodes
        if (chunksToEmbed.isNotEmpty()) {
            db.inTransaction { conn ->
                val ps = conn.prepareStatement(
                    """INSERT OR REPLACE INTO nodes
                       (id, node_type, display_name, file_path, line_start, line_end, content, embedding_text, chunk_index, owning_file, corpus, data_type)
                       VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'code', NULL)"""
                )
                for ((idx, chunk) in chunksToEmbed.withIndex()) {
                    ps.setString(1, chunk.id)
                    ps.setString(2, "JavaMethod")
                    ps.setString(3, "${chunk.className.substringAfterLast('.')}#${chunk.methodName}")
                    ps.setString(4, chunk.filePath)
                    ps.setInt(5, chunk.lineStart)
                    ps.setInt(6, chunk.lineEnd)
                    ps.setString(7, chunk.content)
                    ps.setString(8, chunk.embeddingText)
                    ps.setInt(9, idx)
                    val relPath = File(chunk.filePath).relativeTo(decompileDir).path.replace('\\', '/')
                    ps.setString(10, relPath)
                    ps.addBatch()
                }
                ps.executeBatch()
            }
        }

        // Save hashes for ALL scanned files (not just those that produced chunks).
        // This prevents no-chunk files (interfaces, parse failures) from appearing as "added" on every run.
        if (changes.currentHashes.isNotEmpty()) {
            hashTracker.updateHashes(changes.currentHashes)
        }

        // Build HNSW index
        indicator.text = "Building vector index..."
        indicator.fraction = 0.8

        if (embeddings.isNotEmpty()) {
            val hnswDimension = embeddings.first().size
            val hnsw = HnswIndex(hnswDimension)
            hnsw.build(embeddings)
            val hnswPath = Paths.get(indexDir.absolutePath, "hnsw", "code.hnsw")
            hnsw.save(hnswPath)
            hnsw.close()
            indexedCount = embeddings.size
        }

        // ── Phase 6: Extract graph ──────────────────────────────
        indicator.text = "Extracting graph structure..."
        indicator.fraction = 0.9

        JavaExtractor.extractAndStore(allChunks, db, decompileDir)

        indicator.fraction = 1.0
        log.info("Knowledge index built: $indexedCount methods indexed")
    }

    override fun onSuccess() {
        val message = if (skipped) "No changes detected" else "$indexedCount methods indexed"
        NotificationGroupManager.getInstance()
            .getNotificationGroup("Hyve Knowledge")
            .createNotification(
                "Code index: ${if (skipped) "up to date" else "built"}",
                message,
                NotificationType.INFORMATION,
            )
            .notify(project)
    }

    override fun onThrowable(error: Throwable) {
        log.error("Index build failed", error)
        NotificationGroupManager.getInstance()
            .getNotificationGroup("Hyve Knowledge")
            .createNotification(
                "Index build failed",
                error.message ?: "Unknown error",
                NotificationType.ERROR,
            )
            .notify(project)
    }
}
