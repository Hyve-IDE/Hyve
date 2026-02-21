// Copyright 2026 Hyve. All rights reserved.
package com.hyve.knowledge.index

import com.hyve.knowledge.core.db.KnowledgeDatabase
import com.hyve.knowledge.extraction.ClientUIChunk
import com.hyve.knowledge.extraction.ClientUIType

/**
 * Produces UI_BINDS_TO edges between client UI (.ui) nodes and gamedata entities.
 * Reuses the proven strategies from [UIContentAnalyzer] and resolves candidates
 * against the gamedata corpus display_name index.
 *
 * Only processes .ui chunks — XAML files are excluded since they are not moddable.
 */
class UIBindingExtractor(private val db: KnowledgeDatabase) {

    private val analyzer = UIContentAnalyzer()

    data class EdgeRow(
        val sourceId: String,
        val targetId: String,
        val edgeType: String,
        val metadata: String? = null,
        val targetResolved: Boolean = true,
    )

    /**
     * Extract UI_BINDS_TO edges from the given client UI chunks.
     *
     * @param chunks All parsed client UI chunks (will be filtered to .ui only)
     * @return Deduplicated list of edge rows, keeping highest confidence per (source, target) pair
     */
    fun extractEdges(chunks: List<ClientUIChunk>): List<EdgeRow> {
        val uiChunks = chunks.filter { it.type == ClientUIType.UI }
        if (uiChunks.isEmpty()) return emptyList()

        // Build display_name -> node_id lookup from gamedata corpus
        val gamedataLookup = db.query(
            "SELECT id, display_name FROM nodes WHERE corpus = 'gamedata' AND display_name IS NOT NULL",
        ) { rs -> rs.getString("display_name") to rs.getString("id") }
            .groupBy({ it.first.lowercase() }, { it.second })

        // Run analysis strategies on each .ui chunk and resolve against gamedata
        val edgeMap = mutableMapOf<Pair<String, String>, EdgeRow>()

        for (chunk in uiChunks) {
            if (chunk.content.isBlank()) continue

            val candidates = analyzer.analyze(chunk.content, chunk.id)
            for (candidate in candidates) {
                val targetIds = gamedataLookup[candidate.candidateText.lowercase()] ?: continue
                for (targetId in targetIds) {
                    val key = candidate.clientNodeId to targetId
                    val existing = edgeMap[key]
                    // Keep highest confidence for each (source, target) pair
                    if (existing == null || candidate.confidence > parseConfidence(existing.metadata)) {
                        edgeMap[key] = EdgeRow(
                            sourceId = candidate.clientNodeId,
                            targetId = targetId,
                            edgeType = "UI_BINDS_TO",
                            metadata = """{"strategy":"${candidate.strategy}","confidence":${candidate.confidence}}""",
                            targetResolved = true,
                        )
                    }
                }
            }
        }

        return edgeMap.values.toList()
    }

    private fun parseConfidence(metadata: String?): Float {
        if (metadata == null) return 0f
        val match = Regex(""""confidence":\s*([0-9.]+)""").find(metadata)
        return match?.groupValues?.get(1)?.toFloatOrNull() ?: 0f
    }
}
