// Copyright 2026 Hyve. All rights reserved.
package com.hyve.knowledge.core.diff

import com.hyve.knowledge.core.db.KnowledgeDatabase
import com.hyve.knowledge.core.logging.LogProvider
import com.hyve.knowledge.core.logging.StdoutLogProvider
import java.io.File
import java.time.Instant

/**
 * Cross-DB diff computation engine.
 *
 * Compares two version snapshots by opening both version databases
 * and computing structural differences in nodes and their content.
 */
class DiffEngine(
    private val log: LogProvider = StdoutLogProvider,
) {

    /**
     * Compute diff between two version databases.
     *
     * @param versionA slug of the "old" version
     * @param versionB slug of the "new" version
     * @param dbFileA path to version A's knowledge.db
     * @param dbFileB path to version B's knowledge.db
     * @param corpusFilter optional corpus to restrict comparison (null = all)
     * @param changeTypeFilter optional change type filter
     * @param dataTypeFilter optional data type filter
     * @param limit max entries to return
     */
    fun computeDiff(
        versionA: String,
        versionB: String,
        dbFileA: File,
        dbFileB: File,
        corpusFilter: String? = null,
        changeTypeFilter: String? = null,
        dataTypeFilter: String? = null,
        limit: Int = Int.MAX_VALUE,
    ): VersionDiff {
        val dbA = KnowledgeDatabase.forFile(dbFileA, log)
        val dbB = KnowledgeDatabase.forFile(dbFileB, log)

        try {
            // 1. Load file hashes from both versions for fast-path skipping
            val hashesA = loadFileHashes(dbA)
            val hashesB = loadFileHashes(dbB)
            val unchangedFiles = hashesA.entries
                .filter { (path, hash) -> hashesB[path] == hash }
                .map { it.key }
                .toSet()

            // 2. Load node summaries from both versions
            val nodesA = loadNodeSummaries(dbA, corpusFilter)
            val nodesB = loadNodeSummaries(dbB, corpusFilter)

            val nodeIdsA = nodesA.keys
            val nodeIdsB = nodesB.keys

            // 2b. Detect one-sided corpora (one version has nodes, the other has zero)
            val corporaA = nodesA.values.map { it.corpus }.toSet() - "docs"
            val corporaB = nodesB.values.map { it.corpus }.toSet() - "docs"
            val skippedCorpora = mutableMapOf<String, String>()

            for (corpus in (corporaB - corporaA)) {
                val count = nodesB.values.count { it.corpus == corpus }
                skippedCorpora[corpus] = "not indexed in ${versionA.substringBefore("_")} ($count nodes only in ${versionB.substringBefore("_")})"
                log.info("Skipping corpus '$corpus': not present in version A ($versionA)")
            }
            for (corpus in (corporaA - corporaB)) {
                val count = nodesA.values.count { it.corpus == corpus }
                skippedCorpora[corpus] = "not indexed in ${versionB.substringBefore("_")} ($count nodes only in ${versionA.substringBefore("_")})"
                log.info("Skipping corpus '$corpus': not present in version B ($versionB)")
            }

            val entries = mutableListOf<DiffEntry>()

            // 3. Added nodes: in B but not A (skip docs and one-sided corpora)
            for (id in nodeIdsB - nodeIdsA) {
                val node = nodesB[id] ?: continue
                if (node.corpus == "docs") continue
                if (node.corpus in skippedCorpora) continue
                entries.add(DiffEntry(
                    nodeId = id,
                    displayName = node.displayName,
                    corpus = node.corpus,
                    dataType = node.dataType,
                    nodeType = node.nodeType,
                    changeType = ChangeType.ADDED,
                    filePath = node.filePath,
                ))
            }

            // 4. Removed nodes: in A but not B
            for (id in nodeIdsA - nodeIdsB) {
                val node = nodesA[id] ?: continue
                if (node.corpus == "docs") continue
                if (node.corpus in skippedCorpora) continue
                entries.add(DiffEntry(
                    nodeId = id,
                    displayName = node.displayName,
                    corpus = node.corpus,
                    dataType = node.dataType,
                    nodeType = node.nodeType,
                    changeType = ChangeType.REMOVED,
                    filePath = node.filePath,
                ))
            }

            // 5. Changed nodes: same ID, owning_file not in unchanged set, content differs
            for (id in nodeIdsA.intersect(nodeIdsB)) {
                val nodeA = nodesA[id] ?: continue
                val nodeB = nodesB[id] ?: continue
                if (nodeA.corpus == "docs") continue
                if (nodeA.corpus in skippedCorpora) continue

                // Fast path: if owning file is unchanged, skip
                val owningFile = nodeA.owningFile
                if (owningFile != null && owningFile in unchangedFiles) continue

                // Compare content
                if (nodeA.contentHash == nodeB.contentHash) continue

                val detail = computeDetail(nodeA, nodeB, dbA, dbB)
                entries.add(DiffEntry(
                    nodeId = id,
                    displayName = nodeB.displayName,
                    corpus = nodeB.corpus,
                    dataType = nodeB.dataType,
                    nodeType = nodeB.nodeType,
                    changeType = ChangeType.CHANGED,
                    filePath = nodeB.filePath,
                    detail = detail,
                ))
            }

            // 6. Apply filters
            var filtered = entries.asSequence()
            if (changeTypeFilter != null) {
                val ct = try { ChangeType.valueOf(changeTypeFilter.uppercase()) } catch (_: Exception) { null }
                if (ct != null) filtered = filtered.filter { it.changeType == ct }
            }
            if (dataTypeFilter != null) {
                filtered = filtered.filter { it.dataType == dataTypeFilter }
            }

            val resultEntries = filtered.take(limit).toList()

            // 7. Build summary
            val summary = buildSummary(entries, skippedCorpora)

            return VersionDiff(
                versionA = versionA,
                versionB = versionB,
                computedAt = Instant.now().toString(),
                summary = summary,
                entries = resultEntries,
            )
        } finally {
            dbA.close()
            dbB.close()
        }
    }

    private data class NodeSummary(
        val id: String,
        val displayName: String,
        val corpus: String,
        val dataType: String?,
        val nodeType: String,
        val filePath: String?,
        val owningFile: String?,
        val contentHash: String?,
    )

    private fun loadNodeSummaries(db: KnowledgeDatabase, corpusFilter: String?): Map<String, NodeSummary> {
        val sql = buildString {
            append("SELECT id, display_name, corpus, data_type, node_type, file_path, owning_file, content FROM nodes")
            if (corpusFilter != null) append(" WHERE corpus = ?")
        }
        val params = if (corpusFilter != null) arrayOf(corpusFilter) else emptyArray()
        return db.query(sql, *params) { rs ->
            val content = rs.getString("content") ?: ""
            NodeSummary(
                id = rs.getString("id"),
                displayName = rs.getString("display_name"),
                corpus = rs.getString("corpus"),
                dataType = rs.getString("data_type"),
                nodeType = rs.getString("node_type"),
                filePath = rs.getString("file_path"),
                owningFile = rs.getString("owning_file"),
                contentHash = content.hashCode().toString(),
            )
        }.associateBy { it.id }
    }

    private fun loadFileHashes(db: KnowledgeDatabase): Map<String, String> {
        return db.query("SELECT file_path, file_hash FROM file_hashes") { rs ->
            rs.getString("file_path") to rs.getString("file_hash")
        }.toMap()
    }

    private fun computeDetail(
        nodeA: NodeSummary,
        nodeB: NodeSummary,
        dbA: KnowledgeDatabase,
        dbB: KnowledgeDatabase,
    ): DiffDetail? {
        return when (nodeA.corpus) {
            "code" -> computeCodeDetail(nodeA, nodeB, dbA, dbB)
            "gamedata" -> computeGameDataDetail(nodeA, nodeB, dbA, dbB)
            "client" -> DiffDetail.Client(contentChanged = true)
            else -> null
        }
    }

    private fun computeCodeDetail(
        nodeA: NodeSummary,
        nodeB: NodeSummary,
        dbA: KnowledgeDatabase,
        dbB: KnowledgeDatabase,
    ): DiffDetail.Code {
        val contentA = loadContent(dbA, nodeA.id)
        val contentB = loadContent(dbB, nodeB.id)

        val sigA = extractSignature(contentA)
        val sigB = extractSignature(contentB)

        return DiffDetail.Code(
            signatureChanged = sigA != sigB,
            oldSignature = if (sigA != sigB) sigA else null,
            newSignature = if (sigA != sigB) sigB else null,
            bodyChanged = contentA != contentB,
        )
    }

    private fun computeGameDataDetail(
        nodeA: NodeSummary,
        nodeB: NodeSummary,
        dbA: KnowledgeDatabase,
        dbB: KnowledgeDatabase,
    ): DiffDetail.GameData {
        val contentA = loadContent(dbA, nodeA.id)
        val contentB = loadContent(dbB, nodeB.id)

        val fieldsA = JsonDiffer.flatten(contentA)
        val fieldsB = JsonDiffer.flatten(contentB)
        val fieldChanges = JsonDiffer.diffFlat(fieldsA, fieldsB)

        return DiffDetail.GameData(fieldChanges = fieldChanges)
    }

    private fun loadContent(db: KnowledgeDatabase, nodeId: String): String {
        return db.query("SELECT content FROM nodes WHERE id = ?", nodeId) {
            it.getString("content") ?: ""
        }.firstOrNull() ?: ""
    }

    private fun extractSignature(content: String): String? {
        // First non-annotation, non-comment line containing a method/class declaration
        return content.lineSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() && !it.startsWith("//") && !it.startsWith("@") && !it.startsWith("*") && !it.startsWith("/*") }
            .firstOrNull()
    }

    private fun buildSummary(entries: List<DiffEntry>, skippedCorpora: Map<String, String>): DiffSummary {
        val byCorpus = entries.groupBy { it.corpus }
        val corpusSummaries = byCorpus.mapValues { (_, entries) ->
            CorpusDiffSummary(
                added = entries.count { it.changeType == ChangeType.ADDED },
                removed = entries.count { it.changeType == ChangeType.REMOVED },
                changed = entries.count { it.changeType == ChangeType.CHANGED },
            )
        }

        return DiffSummary(
            totalAdded = entries.count { it.changeType == ChangeType.ADDED },
            totalRemoved = entries.count { it.changeType == ChangeType.REMOVED },
            totalChanged = entries.count { it.changeType == ChangeType.CHANGED },
            byCorpus = corpusSummaries,
            skippedCorpora = skippedCorpora,
        )
    }
}
