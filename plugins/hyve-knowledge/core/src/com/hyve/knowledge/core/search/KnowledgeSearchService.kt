// Copyright 2026 Hyve. All rights reserved.
package com.hyve.knowledge.core.search

import com.hyve.knowledge.core.db.Corpus
import com.hyve.knowledge.core.db.KnowledgeDatabase
import com.hyve.knowledge.core.index.CorpusIndexManager
import com.hyve.knowledge.core.logging.LogProvider
import com.hyve.knowledge.core.logging.StdoutLogProvider
import kotlinx.coroutines.runBlocking

class KnowledgeSearchService(
    private val db: KnowledgeDatabase,
    private val indexManager: CorpusIndexManager,
    private val log: LogProvider = StdoutLogProvider,
) {

    // ── Code corpus search (original behavior) ──────────────────

    fun search(query: String, limit: Int = 10): List<SearchResult> {
        val router = QueryRouter(db)
        val routeResult = router.route(query)

        return when (routeResult.strategy) {
            QueryStrategy.GRAPH -> graphSearch(query, routeResult, db, limit)
            QueryStrategy.VECTOR -> vectorSearch(query, db, limit)
            QueryStrategy.HYBRID -> hybridSearch(query, routeResult, db, limit)
        }
    }

    fun searchCode(query: String, classFilter: String? = null, limit: Int = 10): List<SearchResult> {
        val results = search(query, limit * 2)
        if (classFilter == null) return results.take(limit)
        return results.filter { result ->
            result.displayName.contains(classFilter, ignoreCase = true) ||
                result.filePath.contains(classFilter, ignoreCase = true)
        }.take(limit)
    }

    fun vectorSearch(query: String, db: KnowledgeDatabase, limit: Int): List<SearchResult> {
        val hnsw = indexManager.getIndex(Corpus.CODE) ?: return emptyList()
        val provider = indexManager.getProvider(Corpus.CODE)

        val queryVec = runBlocking { provider.embedQuery(query) }
        val results = hnsw.query(queryVec, limit)

        return results.mapNotNull { (ordinal, score) ->
            nodeFromChunkIndex(db, ordinal, score.toDouble(), ResultSource.VECTOR, Corpus.CODE)
        }
    }

    // ── Per-corpus search ───────────────────────────────────────

    fun searchCorpus(
        query: String,
        corpus: Corpus,
        limit: Int = 10,
        dataTypeFilter: String? = null,
    ): List<SearchResult> {
        val filters: Set<String>? = dataTypeFilter?.let { setOf(it) }
        return searchCorpusFiltered(query, corpus, limit, filters)
    }

    fun searchCorpus(
        query: String,
        corpus: Corpus,
        limit: Int = 10,
        dataTypeFilters: Set<String>?,
    ): List<SearchResult> = searchCorpusFiltered(query, corpus, limit, dataTypeFilters)

    private fun searchCorpusFiltered(
        query: String,
        corpus: Corpus,
        limit: Int,
        dataTypeFilters: Set<String>?,
    ): List<SearchResult> {
        if (corpus == Corpus.CODE) return searchCode(query, dataTypeFilters?.firstOrNull(), limit)

        val hnsw = indexManager.getIndex(corpus) ?: return emptyList()
        val provider = indexManager.getProvider(corpus)

        val queryVec = runBlocking { provider.embedQuery(query) }
        val fetchLimit = if (dataTypeFilters != null) limit * 5 else limit
        val results = hnsw.query(queryVec, fetchLimit)

        val mapped = results.mapNotNull { (ordinal, score) ->
            nodeFromChunkIndex(db, ordinal, score.toDouble(), ResultSource.VECTOR, corpus)
        }

        if (dataTypeFilters == null) return mapped.take(limit)
        val filtered = mapped.filter { it.dataType in dataTypeFilters }
        return filtered.take(limit)
    }

    // ── Graph-expanded search ─────────────────────────────────

    fun searchWithExpansion(
        query: String,
        corpora: List<Corpus>,
        perCorpus: Int = 10,
        expansionLimit: Int = 5,
    ): List<SearchResult> {
        val gamedataTypeHint = detectGamedataIntent(query)
        val directResults = corpora.flatMap { corpus ->
            try {
                val typeFilters = if (corpus == Corpus.GAMEDATA) gamedataTypeHint else null
                val results = searchCorpus(query, corpus, perCorpus, typeFilters)
                if (corpus == Corpus.GAMEDATA && gamedataTypeHint == null) {
                    results.filter { it.score >= GAMEDATA_UNINTENT_SCORE_FLOOR }
                } else {
                    results
                }
            } catch (e: Exception) {
                log.warn("Search failed for corpus ${corpus.id}: ${e.message}")
                emptyList()
            }
        }

        val expanded = expandCrossCorpus(directResults, corpora, expansionLimit)
            .filter { it.score >= MIN_EXPANSION_RESULT_SCORE }
        log.info("Graph expansion: ${directResults.size} direct → ${expanded.size} expanded results")

        val seedToExpanded = expanded.groupBy { it.expandedFromNodeId ?: "" }
            .filterKeys { it.isNotEmpty() }
            .mapValues { (_, results) -> results.map { it.nodeId } }

        val annotatedDirect = directResults.map { result ->
            val connections = seedToExpanded[result.nodeId]
            if (connections != null) result.copy(connectedNodeIds = connections)
            else result
        }

        return deduplicateResults(annotatedDirect + expanded)
    }

    private companion object {
        const val EXPANSION_DISCOUNT = 0.4
        const val MIN_EXPANSION_SEED_SCORE = 0.5
        const val PER_SEED_EXPANSION_CAP = 3
        const val MIN_EXPANSION_RESULT_SCORE = 0.35
        const val GAMEDATA_UNINTENT_SCORE_FLOOR = 0.70

        private val GAMEDATA_INTENT_RULES = listOf(
            Regex("\\b(craft|recipe|crafting|bench|smelt|cook|brew)s?\\b", RegexOption.IGNORE_CASE) to setOf("recipe", "item"),
            Regex("\\b(drop|loot)s?\\s+from\\b", RegexOption.IGNORE_CASE) to setOf("drop", "npc"),
            Regex("\\b(npc|mob|creature|enem(?:y|ies)|trork|kweebec|feran)s?\\b", RegexOption.IGNORE_CASE) to setOf("npc", "npc_group"),
            Regex("\\b(block|ore|stone|wood|plank)s?\\b", RegexOption.IGNORE_CASE) to setOf("block"),
            Regex("\\b(farm|farming|crop|grow|plant|seed|harvest)s?\\b", RegexOption.IGNORE_CASE) to setOf("farming", "item"),
            Regex("\\b(shop|merchant|vendor|buy|sell|trade)s?\\b", RegexOption.IGNORE_CASE) to setOf("shop"),
            Regex("\\b(biome|zone|climate)s?\\b", RegexOption.IGNORE_CASE) to setOf("biome"),
            Regex("\\b(weather|rain|snow|storm)s?\\b", RegexOption.IGNORE_CASE) to setOf("weather"),
            Regex("\\b(objective|quest|mission|task|bount(?:y|ies))s?\\b", RegexOption.IGNORE_CASE) to setOf("objective"),
        )
    }

    internal fun detectGamedataIntent(query: String): Set<String>? {
        val matched = mutableSetOf<String>()
        for ((pattern, dataTypes) in GAMEDATA_INTENT_RULES) {
            if (pattern.containsMatchIn(query)) matched.addAll(dataTypes)
        }
        return matched.ifEmpty { null }
    }

    private fun expandCrossCorpus(
        seeds: List<SearchResult>,
        enabledCorpora: List<Corpus>,
        limit: Int,
    ): List<SearchResult> {
        val traversal = GraphTraversal(db)
        val expanded = mutableListOf<SearchResult>()
        val seen = mutableSetOf<String>()

        val codeEnabled = enabledCorpora.any { it == Corpus.CODE }
        val gamedataEnabled = enabledCorpora.any { it == Corpus.GAMEDATA }
        val clientEnabled = enabledCorpora.any { it == Corpus.CLIENT }

        for (seed in seeds) {
            if (seed.nodeId in seen) continue
            seen.add(seed.nodeId)

            if (seed.score < MIN_EXPANSION_SEED_SCORE) continue

            val seedLabel = seed.displayName
                .substringAfterLast('#')
                .substringAfterLast('.')

            var seedExpanded = 0

            when (seed.corpus) {
                "gamedata" -> {
                    if (codeEnabled) {
                        traversal.findImplementingCode(seed.nodeId, limit)
                            .filter { it.nodeId !in seen }
                            .take(PER_SEED_EXPANSION_CAP - seedExpanded)
                            .forEach { result ->
                                seen.add(result.nodeId)
                                seedExpanded++
                                expanded.add(result.copy(
                                    score = seed.score * EXPANSION_DISCOUNT,
                                    bridgedFrom = seedLabel,
                                    bridgeEdgeType = "IMPLEMENTED_BY",
                                    expandedFromNodeId = seed.nodeId,
                                ))
                            }
                    }
                    if (clientEnabled && seedExpanded < PER_SEED_EXPANSION_CAP) {
                        traversal.findUIForGamedata(seed.nodeId, limit)
                            .filter { it.nodeId !in seen }
                            .take(PER_SEED_EXPANSION_CAP - seedExpanded)
                            .forEach { result ->
                                seen.add(result.nodeId)
                                seedExpanded++
                                expanded.add(result.copy(
                                    score = seed.score * EXPANSION_DISCOUNT,
                                    bridgedFrom = seedLabel,
                                    bridgeEdgeType = "UI_BINDS_TO",
                                    expandedFromNodeId = seed.nodeId,
                                ))
                            }
                    }
                }
                "code" -> if (gamedataEnabled) {
                    traversal.findGamedataForCode(seed.nodeId, limit)
                        .filter { it.nodeId !in seen }
                        .take(PER_SEED_EXPANSION_CAP)
                        .forEach { result ->
                            seen.add(result.nodeId)
                            expanded.add(result.copy(
                                score = seed.score * EXPANSION_DISCOUNT,
                                bridgedFrom = seedLabel,
                                bridgeEdgeType = "IMPLEMENTED_BY",
                                expandedFromNodeId = seed.nodeId,
                            ))
                        }
                }
                "client" -> if (gamedataEnabled) {
                    traversal.findUIBindings(seed.nodeId, limit)
                        .filter { it.nodeId !in seen }
                        .take(PER_SEED_EXPANSION_CAP)
                        .forEach { result ->
                            seen.add(result.nodeId)
                            expanded.add(result.copy(
                                score = seed.score * EXPANSION_DISCOUNT,
                                bridgedFrom = seedLabel,
                                bridgeEdgeType = "UI_BINDS_TO",
                                expandedFromNodeId = seed.nodeId,
                            ))
                        }
                }
                "docs" -> {
                    traversal.findDocsReferences(seed.nodeId, limit)
                        .filter { it.nodeId !in seen }
                        .filter {
                            (it.corpus == "code" && codeEnabled) ||
                            (it.corpus == "gamedata" && gamedataEnabled)
                        }
                        .take(PER_SEED_EXPANSION_CAP)
                        .forEach { result ->
                            seen.add(result.nodeId)
                            expanded.add(result.copy(
                                score = seed.score * EXPANSION_DISCOUNT,
                                bridgedFrom = seedLabel,
                                bridgeEdgeType = "DOCS_REFERENCES",
                                expandedFromNodeId = seed.nodeId,
                            ))
                        }
                }
            }
        }
        if (expanded.isNotEmpty()) {
            log.info("Graph expansion found ${expanded.size} cross-corpus results from ${seen.size} seeds")
        }
        return expanded
    }

    internal fun deduplicateResults(results: List<SearchResult>): List<SearchResult> {
        val best = linkedMapOf<String, SearchResult>()
        for (result in results) {
            val existing = best[result.nodeId]
            if (existing == null || result.score > existing.score) {
                val merged = if (existing != null) {
                    result.copy(
                        bridgedFrom = result.bridgedFrom ?: existing.bridgedFrom,
                        bridgeEdgeType = result.bridgeEdgeType ?: existing.bridgeEdgeType,
                        connectedNodeIds = result.connectedNodeIds.ifEmpty { existing.connectedNodeIds },
                    )
                } else result
                best[result.nodeId] = merged
            } else {
                val merged = existing.copy(
                    bridgedFrom = existing.bridgedFrom ?: result.bridgedFrom,
                    bridgeEdgeType = existing.bridgeEdgeType ?: result.bridgeEdgeType,
                    connectedNodeIds = existing.connectedNodeIds.ifEmpty { result.connectedNodeIds },
                )
                best[result.nodeId] = merged
            }
        }
        return best.values.toList()
    }

    // ── Stats ───────────────────────────────────────────────────

    fun getStats(): IndexStats = getCorpusStats(Corpus.CODE)

    fun getCorpusStats(corpus: Corpus): IndexStats {
        val nodeCount = db.query(
            "SELECT COUNT(*) FROM nodes WHERE corpus = ?", corpus.id
        ) { it.getInt(1) }.firstOrNull() ?: 0

        val typeBreakdown = db.query(
            "SELECT COALESCE(data_type, node_type) AS t, COUNT(*) FROM nodes WHERE corpus = ? GROUP BY t",
            corpus.id,
        ) { rs -> rs.getString(1) to rs.getInt(2) }.toMap()

        val edgeCount = if (corpus == Corpus.CODE) {
            db.query("SELECT COUNT(*) FROM edges") { it.getInt(1) }.firstOrNull() ?: 0
        } else 0

        val vectorIndexLoaded = indexManager.getIndex(corpus)?.isLoaded() == true

        return IndexStats(
            corpus = corpus.id,
            nodeCount = nodeCount,
            typeBreakdown = typeBreakdown,
            edgeCount = edgeCount,
            vectorIndexLoaded = vectorIndexLoaded,
        )
    }

    // ── Internal ────────────────────────────────────────────────

    private fun graphSearch(
        query: String,
        routeResult: RouteResult,
        db: KnowledgeDatabase,
        limit: Int,
    ): List<SearchResult> {
        val traversal = GraphTraversal(db)
        if (routeResult.entityName != null && routeResult.relation != null) {
            val nodeIds = db.query(
                "SELECT id FROM nodes WHERE LOWER(display_name) = LOWER(?) AND corpus = 'gamedata' LIMIT 5",
                routeResult.entityName,
            ) { it.getString("id") }
            val primaryId = nodeIds.firstOrNull()
            val gamedataResult = when (routeResult.relation) {
                "REQUIRES_ITEM" -> if (primaryId != null) traversal.findRecipeInputs(primaryId, limit) else emptyList()
                "DROPS_ON_DEATH" -> if (primaryId != null) traversal.findDropsFrom(primaryId, limit) else emptyList()
                "OFFERED_IN_SHOP" -> if (primaryId != null) traversal.findShopsSellingItem(primaryId, limit) else emptyList()
                "HAS_MEMBER" -> if (primaryId != null) traversal.findGroupMembers(primaryId, limit) else emptyList()
                "UI_BINDS_TO" -> if (primaryId != null) traversal.findUIForGamedata(primaryId, limit) else emptyList()
                else -> null
            }
            if (gamedataResult != null) return gamedataResult
            return traversal.findByRelation(routeResult.entityName, routeResult.relation, limit)
        }
        return if (routeResult.entityName != null) {
            traversal.findByName(routeResult.entityName, limit)
        } else {
            vectorSearch(query, db, limit)
        }
    }

    private fun hybridSearch(
        query: String,
        routeResult: RouteResult,
        db: KnowledgeDatabase,
        limit: Int,
    ): List<SearchResult> {
        val vectorResults = vectorSearch(query, db, limit)
        val graphResults = graphSearch(query, routeResult, db, limit)
        return HybridScorer.mergeRRF(vectorResults, graphResults, limit = limit)
    }

    private fun nodeFromChunkIndex(
        db: KnowledgeDatabase,
        chunkIndex: Int,
        score: Double,
        source: ResultSource,
        corpus: Corpus,
    ): SearchResult? {
        val results = db.query(
            """SELECT id, display_name, content, embedding_text, file_path, line_start, data_type
               FROM nodes WHERE chunk_index = ? AND corpus = ? LIMIT 1""",
            chunkIndex, corpus.id,
        ) { rs ->
            val snippet = if (corpus != Corpus.CODE) {
                rs.getString("embedding_text")?.take(500)
                    ?: rs.getString("content")?.take(500) ?: ""
            } else {
                rs.getString("content")?.take(500) ?: ""
            }
            SearchResult(
                nodeId = rs.getString("id"),
                displayName = rs.getString("display_name"),
                snippet = snippet,
                filePath = rs.getString("file_path") ?: "",
                lineStart = rs.getInt("line_start"),
                score = score,
                source = source,
                dataType = rs.getString("data_type"),
                corpus = corpus.id,
            )
        }
        return results.firstOrNull()
    }

    fun close() {
        indexManager.closeAll()
    }
}
