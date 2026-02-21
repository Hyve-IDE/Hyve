// Copyright 2026 Hyve. All rights reserved.
package com.hyve.knowledge.index

import com.hyve.common.settings.HytaleInstallPath
import com.hyve.knowledge.core.db.Corpus
import com.hyve.knowledge.core.index.HnswIndex
import com.hyve.knowledge.bridge.EmbeddingProviderFactory
import com.hyve.knowledge.bridge.KnowledgeDatabaseFactory
import com.hyve.knowledge.core.db.KnowledgeDatabase
import com.hyve.knowledge.extraction.ClientUIChunk
import com.hyve.knowledge.extraction.ClientUIParser
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
 * Background task that builds the Client UI knowledge index:
 * 1. Detect changes via file hashing (fast scan)
 * 2. If no changes, skip everything (instant)
 * 3. Parse .xaml, .ui, and .json files from the Hytale client data directory
 * 4. Embed new/changed chunks via configured text embedding provider
 * 5. Store nodes in SQLite with corpus='client'
 * 6. Build/update HNSW vector index
 * 7. Build UI_BINDS_TO edges linking .ui nodes to gamedata entities
 */
class ClientUIIndexerTask(
    project: Project,
) : Task.Backgroundable(project, "Building Client UI Index...", true) {

    private val log = Logger.getInstance(ClientUIIndexerTask::class.java)
    private var indexedCount = 0
    private var skipped = false

    override fun run(indicator: ProgressIndicator) {
        indicator.isIndeterminate = false

        val clientPath = HytaleInstallPath.clientFolderPath()?.toFile()
            ?: return reportError("Hytale install path not configured.")
        val clientDataPath = File(clientPath, "Data")
        if (!clientDataPath.exists()) {
            return reportError("Client Data directory not found at:\n${clientDataPath.absolutePath}")
        }

        val settings = KnowledgeSettings.getInstance()
        val db = KnowledgeDatabaseFactory.getInstance()
        val hashTracker = FileHashTracker(db)
        val indexDir = settings.resolvedIndexPath()
        indexDir.mkdirs()

        // ── Phase 1: Detect changes (fast scan) ──────────────────
        indicator.text = "Detecting changes..."
        indicator.fraction = 0.0

        val changes = hashTracker.detectChanges(
            sourceDir = clientDataPath,
            corpusType = Corpus.CLIENT.id,
            extensionFilter = setOf("xaml", "ui", "json"),
        )

        log.info("Changes: +${changes.added.size} ~${changes.changed.size} -${changes.deleted.size} =${changes.unchanged.size}")

        if (!changes.hasChanges && changes.unchanged.isNotEmpty()) {
            log.info("No client UI changes detected, skipping indexing")
            skipped = true
            indicator.fraction = 1.0
            return
        }

        if (indicator.isCanceled) return

        // ── Phase 2: Parse ───────────────────────────────────────
        indicator.text = "Parsing client UI files..."
        indicator.fraction = 0.1

        val parseResult = ClientUIParser.parseClientData(
            clientDataPath = clientDataPath,
            onProgress = { current, total, name ->
                indicator.text2 = name
                indicator.fraction = 0.1 + (0.1 * current / total.coerceAtLeast(1))
            },
        )

        if (parseResult.errors.isNotEmpty()) {
            log.warn("Client UI parse errors (${parseResult.errors.size}): ${parseResult.errors.take(5).joinToString("; ")}")
        }
        log.info("Parsed ${parseResult.chunks.size} client UI chunks from ${clientDataPath.name}")

        if (indicator.isCanceled) return

        // ── Phase 3: Filter to changed files ─────────────────────
        val changedPaths = changes.added + changes.changed
        val chunksToEmbed = parseResult.chunks.filter { chunk -> chunk.relativePath in changedPaths }

        if (indicator.isCanceled) return

        // ── Phase 4: Embed ──────────────────────────────────────
        val embeddings: List<FloatArray>
        if (chunksToEmbed.isNotEmpty()) {
            indicator.text = "Embedding ${chunksToEmbed.size} chunks..."
            indicator.fraction = 0.2

            val provider = EmbeddingProviderFactory.fromSettings(Corpus.CLIENT.embeddingPurpose)
            runBlocking {
                provider.validate()
            }

            val texts = chunksToEmbed.map { it.textForEmbedding }
            val batchSize = 32
            val batches = texts.chunked(batchSize)
            val allEmbeddings = mutableListOf<FloatArray>()

            runBlocking {
                for ((batchIdx, batch) in batches.withIndex()) {
                    if (indicator.isCanceled) return@runBlocking
                    indicator.text2 = "Batch ${batchIdx + 1}/${batches.size}"
                    indicator.fraction = 0.2 + (0.5 * batchIdx / batches.size.coerceAtLeast(1))
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

        val stalePaths = changes.changed + changes.deleted
        if (stalePaths.isNotEmpty()) {
            hashTracker.removeHashes(stalePaths)
            db.inTransaction { conn ->
                val ps = conn.prepareStatement("DELETE FROM nodes WHERE owning_file = ? AND corpus = ?")
                for (path in stalePaths) {
                    ps.setString(1, path)
                    ps.setString(2, Corpus.CLIENT.id)
                    ps.addBatch()
                }
                ps.executeBatch()
            }
        }

        if (chunksToEmbed.isNotEmpty()) {
            db.inTransaction { conn ->
                val ps = conn.prepareStatement(
                    """INSERT OR REPLACE INTO nodes
                       (id, node_type, display_name, file_path, line_start, line_end, content, embedding_text, chunk_index, owning_file, corpus, data_type)
                       VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"""
                )
                for ((idx, chunk) in chunksToEmbed.withIndex()) {
                    ps.setString(1, chunk.id)
                    ps.setString(2, chunk.type.id)
                    ps.setString(3, chunk.name)
                    ps.setString(4, chunk.filePath)
                    ps.setNull(5, java.sql.Types.INTEGER)
                    ps.setNull(6, java.sql.Types.INTEGER)
                    ps.setString(7, chunk.content)
                    ps.setString(8, chunk.textForEmbedding)
                    ps.setInt(9, idx)
                    ps.setString(10, chunk.relativePath)
                    ps.setString(11, Corpus.CLIENT.id)
                    ps.setString(12, chunk.category)
                    ps.addBatch()
                }
                ps.executeBatch()
            }
        }

        // Save hashes for ALL scanned files (not just those that produced chunks)
        if (changes.currentHashes.isNotEmpty()) {
            hashTracker.updateHashes(changes.currentHashes, Corpus.CLIENT.id)
        }

        // ── Phase 6: Build HNSW ─────────────────────────────────
        indicator.text = "Building vector index..."
        indicator.fraction = 0.8

        if (embeddings.isNotEmpty()) {
            val hnswDimension = embeddings.first().size
            val hnsw = HnswIndex(hnswDimension)
            hnsw.build(embeddings)
            val hnswPath = Paths.get(indexDir.absolutePath, "hnsw", Corpus.CLIENT.hnswFileName)
            hnsw.save(hnswPath)
            hnsw.close()
            indexedCount = embeddings.size
        }

        if (indicator.isCanceled) return

        // ── Phase 7: Build UI_BINDS_TO edges ─────────────────────
        indicator.text = "Building UI binding edges..."
        indicator.fraction = 0.9
        buildUIBindsToEdges(db, parseResult.chunks)

        indicator.fraction = 1.0
        log.info("Client UI index built: $indexedCount files indexed")
    }

    private fun buildUIBindsToEdges(db: KnowledgeDatabase, chunks: List<ClientUIChunk>) {
        if (chunks.isEmpty()) return

        // Delete stale UI_BINDS_TO edges from previous runs
        db.execute("DELETE FROM edges WHERE edge_type = 'UI_BINDS_TO' AND source_id LIKE 'ui:%'")

        val extractor = UIBindingExtractor(db)
        val edges = extractor.extractEdges(chunks)
        if (edges.isEmpty()) {
            log.info("No UI_BINDS_TO edges extracted")
            return
        }

        db.inTransaction { conn ->
            val ps = conn.prepareStatement(
                "INSERT OR IGNORE INTO edges (source_id, target_id, edge_type, target_resolved, metadata) VALUES (?, ?, ?, ?, ?)",
            )
            for (edge in edges) {
                ps.setString(1, edge.sourceId)
                ps.setString(2, edge.targetId)
                ps.setString(3, edge.edgeType)
                ps.setInt(4, if (edge.targetResolved) 1 else 0)
                ps.setString(5, edge.metadata)
                ps.addBatch()
            }
            ps.executeBatch()
        }
        log.info("Inserted ${edges.size} UI_BINDS_TO edges")
    }

    private fun reportError(message: String) {
        log.warn("ClientUIIndexerTask: $message")
    }

    override fun onSuccess() {
        val message = if (skipped) "No changes detected" else "$indexedCount files indexed"
        NotificationGroupManager.getInstance()
            .getNotificationGroup("Hyve Knowledge")
            .createNotification(
                "Client UI index: ${if (skipped) "up to date" else "built"}",
                message,
                NotificationType.INFORMATION,
            )
            .notify(project)
    }

    override fun onThrowable(error: Throwable) {
        log.error("Client UI index build failed", error)
        NotificationGroupManager.getInstance()
            .getNotificationGroup("Hyve Knowledge")
            .createNotification(
                "Client UI index build failed",
                error.message ?: "Unknown error",
                NotificationType.ERROR,
            )
            .notify(project)
    }
}
