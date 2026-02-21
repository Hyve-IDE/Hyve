// Copyright 2026 Hyve. All rights reserved.
@file:Suppress("FunctionName", "unused")

package com.hyve.knowledge.mcp

import com.hyve.knowledge.bridge.IntelliJLogProvider
import com.hyve.knowledge.bridge.toConfig
import com.hyve.knowledge.core.db.Corpus
import com.hyve.knowledge.core.db.KnowledgeDatabase
import com.hyve.knowledge.core.index.CorpusIndexManager
import com.hyve.knowledge.core.search.IndexStats
import com.hyve.knowledge.core.search.KnowledgeSearchService
import com.hyve.knowledge.core.search.SearchResult
import com.hyve.knowledge.settings.KnowledgeSettings
import com.intellij.mcpserver.McpToolset
import com.intellij.mcpserver.annotations.McpDescription
import com.intellij.mcpserver.annotations.McpTool
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.File

class HytaleKnowledgeToolset : McpToolset {

    private val json = Json { prettyPrint = true; encodeDefaults = true }

    private val searchService: KnowledgeSearchService by lazy {
        val log = IntelliJLogProvider(HytaleKnowledgeToolset::class.java)
        val settings = KnowledgeSettings.getInstance()
        val config = settings.toConfig()
        val dbFile = File(config.resolvedIndexPath(), "knowledge.db")
        val db = KnowledgeDatabase.forFile(dbFile, log)
        val indexManager = CorpusIndexManager(config, log)
        KnowledgeSearchService(db, indexManager, log)
    }

    // ── Code Corpus ─────────────────────────────────────────────

    @McpTool
    @McpDescription("""
        |Search the decompiled Hytale codebase using semantic search.
        |Use this to find methods, classes, or functionality by describing what you're looking for.
        |Returns relevant Java methods with their full source code.
    """)
    suspend fun search_hytale_code(
        @McpDescription("Natural language description of what you're looking for") query: String,
        @McpDescription("Filter results to a specific class name") classFilter: String? = null,
        @McpDescription("Number of results to return (default 5, max 20)") limit: Int = 5,
        @McpDescription("Enable graph expansion to find related game data, UI, and docs (default false)") expand: Boolean = false,
    ): String {
        if (expand) {
            val results = searchService.searchWithExpansion(query, Corpus.entries, limit.coerceIn(1, 20))
            return encodeSearchResults(query, results)
        }
        val results = searchService.searchCode(query, classFilter, limit.coerceIn(1, 20))
        return encodeSearchResults(query, results)
    }

    @McpTool
    @McpDescription("""
        |Get statistics about the indexed Hytale codebase.
    """)
    suspend fun hytale_code_stats(): IndexStats {
        return searchService.getCorpusStats(Corpus.CODE)
    }

    // ── Client UI Corpus ────────────────────────────────────────

    @McpTool
    @McpDescription("""
        |Search Hytale client UI files using semantic search.
        |Use this to find UI templates (.xaml), UI components (.ui), and NodeEditor definitions.
        |Useful for modifying game UI appearance like inventory layout, hotbar, health bars, etc.
    """)
    suspend fun search_hytale_client_code(
        @McpDescription("Natural language description of what UI element you're looking for") query: String,
        @McpDescription("Filter results to a specific category (e.g., DesignSystem, InGame, MainMenu)") classFilter: String? = null,
        @McpDescription("Number of results to return (default 5, max 20)") limit: Int = 5,
        @McpDescription("Enable graph expansion to find related game data and code (default false)") expand: Boolean = false,
    ): String {
        if (expand) {
            val results = searchService.searchWithExpansion(query, listOf(Corpus.CLIENT, Corpus.GAMEDATA, Corpus.CODE), limit.coerceIn(1, 20))
            return encodeSearchResults(query, results)
        }
        val results = searchService.searchCorpus(query, Corpus.CLIENT, limit.coerceIn(1, 20), classFilter)
        return encodeSearchResults(query, results)
    }

    @McpTool
    @McpDescription("""
        |Get statistics about the indexed Hytale client UI files (.xaml, .ui, .json).
    """)
    suspend fun hytale_client_code_stats(): IndexStats {
        return searchService.getCorpusStats(Corpus.CLIENT)
    }

    // ── Game Data Corpus ────────────────────────────────────────

    @McpTool
    @McpDescription("""
        |Search vanilla Hytale game data including items, recipes, NPCs, drops, blocks, and more.
        |Use this for modding questions like 'how to craft X', 'what drops Y', 'NPC behavior for Z',
        |'what items use tag T', or 'how does the farming system work'.
    """)
    suspend fun search_hytale_gamedata(
        @McpDescription("Natural language question about Hytale game data") query: String,
        @McpDescription("Filter by data type (default: all)") type: String? = null,
        @McpDescription("Number of results (default 5, max 20)") limit: Int = 5,
        @McpDescription("Enable graph expansion to find related code and UI (default false)") expand: Boolean = false,
    ): String {
        if (expand) {
            val results = searchService.searchWithExpansion(query, listOf(Corpus.GAMEDATA, Corpus.CODE, Corpus.CLIENT), limit.coerceIn(1, 20))
            return encodeSearchResults(query, results)
        }
        val results = searchService.searchCorpus(query, Corpus.GAMEDATA, limit.coerceIn(1, 20), type)
        return encodeSearchResults(query, results)
    }

    @McpTool
    @McpDescription("""
        |Get statistics about the indexed Hytale game data.
    """)
    suspend fun hytale_gamedata_stats(): IndexStats {
        return searchService.getCorpusStats(Corpus.GAMEDATA)
    }

    // ── Docs Corpus ─────────────────────────────────────────────

    @McpTool
    @McpDescription("""
        |Search HytaleModding.dev documentation using semantic search.
        |Use this to find modding guides, tutorials, and reference documentation.
        |Covers topics like plugin development, ECS, block creation, commands, events, and more.
    """)
    suspend fun search_hytale_docs(
        @McpDescription("Natural language question about Hytale modding") query: String,
        @McpDescription("Filter by documentation type (default: all)") type: String? = null,
        @McpDescription("Number of results (default 5, max 20)") limit: Int = 5,
        @McpDescription("Enable graph expansion to find related code and game data (default false)") expand: Boolean = false,
    ): String {
        if (expand) {
            val results = searchService.searchWithExpansion(query, listOf(Corpus.DOCS, Corpus.CODE, Corpus.GAMEDATA), limit.coerceIn(1, 20))
            return encodeSearchResults(query, results)
        }
        val results = searchService.searchCorpus(query, Corpus.DOCS, limit.coerceIn(1, 20), type)
        return encodeSearchResults(query, results)
    }

    @McpTool
    @McpDescription("""
        |Get statistics about the indexed HytaleModding.dev documentation.
        |Shows total documents and breakdown by category and type.
    """)
    suspend fun hytale_docs_stats(): IndexStats {
        return searchService.getCorpusStats(Corpus.DOCS)
    }

    // ── Helpers ──────────────────────────────────────────────────

    private fun encodeSearchResults(query: String, results: List<SearchResult>): String {
        val obj = buildJsonObject {
            put("query", query)
            put("resultCount", results.size)
            put("results", buildJsonArray {
                for (r in results) {
                    add(buildJsonObject {
                        put("id", r.nodeId)
                        put("displayName", r.displayName)
                        put("snippet", r.snippet)
                        put("filePath", r.filePath)
                        put("lineStart", r.lineStart)
                        put("score", r.score)
                        put("source", r.source.name)
                        put("corpus", r.corpus)
                        r.dataType?.let { put("dataType", it) }
                        r.bridgedFrom?.let { put("bridgedFrom", it) }
                        r.bridgeEdgeType?.let { put("bridgeEdgeType", it) }
                        if (r.connectedNodeIds.isNotEmpty()) {
                            put("connectedNodeIds", buildJsonArray {
                                for (id in r.connectedNodeIds) add(kotlinx.serialization.json.JsonPrimitive(id))
                            })
                        }
                    })
                }
            })
        }
        return json.encodeToString(obj)
    }
}
