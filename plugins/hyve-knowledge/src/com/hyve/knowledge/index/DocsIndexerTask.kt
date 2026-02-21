// Copyright 2026 Hyve. All rights reserved.
package com.hyve.knowledge.index

import com.hyve.knowledge.core.db.Corpus
import com.hyve.knowledge.core.index.HnswIndex
import com.hyve.knowledge.bridge.EmbeddingProviderFactory
import com.hyve.knowledge.bridge.KnowledgeDatabaseFactory
import com.hyve.knowledge.core.db.KnowledgeDatabase
import com.hyve.knowledge.extraction.DocsParser
import com.hyve.knowledge.settings.KnowledgeSettings
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import kotlinx.coroutines.runBlocking
import java.nio.file.Paths

/**
 * Background task that builds the modding docs knowledge index:
 * 1. Fetch docs from GitHub and parse into chunks
 * 2. Detect changes via content hashing (incremental)
 * 3. If no changes, skip embedding (only fetch is needed to check)
 * 4. Embed new/changed chunks via configured text embedding provider
 * 5. Store nodes in SQLite with corpus='docs'
 * 6. Build/update HNSW vector index
 */
class DocsIndexerTask(
    project: Project,
) : Task.Backgroundable(project, "Building Docs Index...", true) {

    private val log = Logger.getInstance(DocsIndexerTask::class.java)
    private var indexedCount = 0
    private var skipped = false

    override fun run(indicator: ProgressIndicator) {
        indicator.isIndeterminate = false

        val settings = KnowledgeSettings.getInstance()
        val db = KnowledgeDatabaseFactory.getInstance()
        val hashTracker = FileHashTracker(db)
        val indexDir = settings.resolvedIndexPath()
        indexDir.mkdirs()

        // ── Phase 1: Fetch + Parse ──────────────────────────────
        // (Docs must always be fetched from GitHub to detect changes)
        indicator.text = "Fetching docs from GitHub..."
        indicator.fraction = 0.0

        val parseResult = DocsParser.parseDocs(
            onProgress = { current, total, name ->
                indicator.text2 = name
                indicator.fraction = 0.3 * current / total.coerceAtLeast(1)
            },
        )

        if (parseResult.errors.isNotEmpty()) {
            log.warn("Docs parse errors (${parseResult.errors.size}): ${parseResult.errors.take(5).joinToString("; ")}")
        }
        log.info("Fetched ${parseResult.chunks.size} docs chunks")

        if (indicator.isCanceled) return

        // ── Phase 2: Detect changes ─────────────────────────────
        indicator.text = "Detecting changes..."
        indicator.fraction = 0.3

        val currentHashes = parseResult.chunks.associate { it.relativePath to it.fileHash }
        val changes = hashTracker.computeChangesFromMap(currentHashes, Corpus.DOCS.id)

        log.info("Changes: +${changes.added.size} ~${changes.changed.size} -${changes.deleted.size} =${changes.unchanged.size}")

        if (!changes.hasChanges && changes.unchanged.isNotEmpty()) {
            log.info("No docs changes detected, skipping indexing")
            skipped = true
            indicator.fraction = 1.0
            return
        }

        val changedPaths = changes.added + changes.changed
        val chunksToEmbed = parseResult.chunks.filter { chunk -> chunk.relativePath in changedPaths }

        if (indicator.isCanceled) return

        // ── Phase 3: Embed ──────────────────────────────────────
        val embeddings: List<FloatArray>
        if (chunksToEmbed.isNotEmpty()) {
            indicator.text = "Embedding ${chunksToEmbed.size} docs..."
            indicator.fraction = 0.35

            val provider = EmbeddingProviderFactory.fromSettings(Corpus.DOCS.embeddingPurpose)
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
                    indicator.fraction = 0.35 + (0.45 * batchIdx / batches.size.coerceAtLeast(1))
                    allEmbeddings.addAll(provider.embed(batch))
                }
            }
            embeddings = allEmbeddings
        } else {
            embeddings = emptyList()
        }

        if (indicator.isCanceled) return

        // ── Phase 4: Write index ────────────────────────────────
        indicator.text = "Writing index..."
        indicator.fraction = 0.8

        val stalePaths = changes.changed + changes.deleted
        if (stalePaths.isNotEmpty()) {
            hashTracker.removeHashes(stalePaths)
            db.inTransaction { conn ->
                val ps = conn.prepareStatement("DELETE FROM nodes WHERE owning_file = ? AND corpus = ?")
                for (path in stalePaths) {
                    ps.setString(1, path)
                    ps.setString(2, Corpus.DOCS.id)
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
                    ps.setString(2, "DocsPage")
                    ps.setString(3, chunk.title)
                    ps.setString(4, chunk.filePath)
                    ps.setNull(5, java.sql.Types.INTEGER)
                    ps.setNull(6, java.sql.Types.INTEGER)
                    ps.setString(7, chunk.content)
                    ps.setString(8, chunk.textForEmbedding)
                    ps.setInt(9, idx)
                    ps.setString(10, chunk.relativePath)
                    ps.setString(11, Corpus.DOCS.id)
                    ps.setString(12, chunk.type.id)
                    ps.addBatch()
                }
                ps.executeBatch()
            }
        }

        // Save hashes for ALL fetched docs (not just those that produced chunks)
        if (changes.currentHashes.isNotEmpty()) {
            hashTracker.updateHashes(changes.currentHashes, Corpus.DOCS.id)
        }

        // ── Phase 5: Build HNSW ─────────────────────────────────
        indicator.text = "Building vector index..."
        indicator.fraction = 0.85

        if (embeddings.isNotEmpty()) {
            val hnswDimension = embeddings.first().size
            val hnsw = HnswIndex(hnswDimension)
            hnsw.build(embeddings)
            val hnswPath = Paths.get(indexDir.absolutePath, "hnsw", Corpus.DOCS.hnswFileName)
            hnsw.save(hnswPath)
            hnsw.close()
            indexedCount = embeddings.size
        }

        if (indicator.isCanceled) return

        // ── Phase 6: Build DOCS_REFERENCES edges ────────────────
        indicator.text = "Building docs reference edges..."
        indicator.fraction = 0.9

        buildDocsReferenceEdges(db, parseResult.chunks)

        indicator.fraction = 1.0
        log.info("Docs index built: $indexedCount pages indexed")
    }

    // ── DOCS_REFERENCES edge extraction ─────────────────────────

    private val BACKTICK_PATTERN = Regex("""`([A-Za-z]\w+)`""")
    private val PASCAL_CASE_PATTERN = Regex("""\b([A-Z][a-z]+(?:[A-Z][a-z]+)+)\b""")

    /**
     * Scan docs markdown for code/gamedata references and create DOCS_REFERENCES edges.
     * Matches backtick spans and PascalCase words against existing node display_names.
     */
    private fun buildDocsReferenceEdges(db: KnowledgeDatabase, chunks: List<com.hyve.knowledge.extraction.DocsChunk>) {
        if (chunks.isEmpty()) return

        // Delete stale docs reference edges
        db.execute("DELETE FROM edges WHERE edge_type = 'DOCS_REFERENCES' AND source_id LIKE 'docs:%'")

        // Build display_name → node ID lookup from code and gamedata corpora
        val nameLookup = db.query(
            "SELECT id, display_name FROM nodes WHERE corpus IN ('code', 'gamedata') AND display_name IS NOT NULL"
        ) { rs -> rs.getString("display_name") to rs.getString("id") }
            .groupBy({ it.first }, { it.second })

        if (nameLookup.isEmpty()) {
            log.info("No code/gamedata nodes for DOCS_REFERENCES — skipping (index code and gamedata first)")
            return
        }

        val allEdges = mutableListOf<Triple<String, String, Int>>() // sourceId, targetId, targetResolved

        for (chunk in chunks) {
            val candidates = mutableSetOf<String>()

            // Extract backtick spans
            BACKTICK_PATTERN.findAll(chunk.content).forEach { match ->
                candidates.add(match.groupValues[1])
            }

            // Extract PascalCase words
            PASCAL_CASE_PATTERN.findAll(chunk.content).forEach { match ->
                candidates.add(match.groupValues[1])
            }

            // Cross-reference against node display_names (exact match)
            for (candidate in candidates) {
                val targets = nameLookup[candidate] ?: continue
                for (targetId in targets) {
                    allEdges.add(Triple(chunk.id, targetId, 1))
                }
            }
        }

        if (allEdges.isNotEmpty()) {
            db.inTransaction { conn ->
                val ps = conn.prepareStatement(
                    "INSERT OR IGNORE INTO edges (source_id, target_id, edge_type, target_resolved) VALUES (?, ?, 'DOCS_REFERENCES', ?)"
                )
                for ((sourceId, targetId, resolved) in allEdges) {
                    ps.setString(1, sourceId)
                    ps.setString(2, targetId)
                    ps.setInt(3, resolved)
                    ps.addBatch()
                }
                ps.executeBatch()
            }
        }

        log.info("DOCS_REFERENCES: ${allEdges.size} edges from ${chunks.size} docs")
    }

    override fun onSuccess() {
        val message = if (skipped) "No changes detected" else "$indexedCount pages indexed"
        NotificationGroupManager.getInstance()
            .getNotificationGroup("Hyve Knowledge")
            .createNotification(
                "Docs index: ${if (skipped) "up to date" else "built"}",
                message,
                NotificationType.INFORMATION,
            )
            .notify(project)
    }

    override fun onThrowable(error: Throwable) {
        log.error("Docs index build failed", error)
        NotificationGroupManager.getInstance()
            .getNotificationGroup("Hyve Knowledge")
            .createNotification(
                "Docs index build failed",
                error.message ?: "Unknown error",
                NotificationType.ERROR,
            )
            .notify(project)
    }
}
