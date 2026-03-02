// Copyright 2026 Hyve. All rights reserved.
@file:Suppress("FunctionName", "unused")

package com.hyve.knowledge.mcp

import com.hyve.knowledge.bridge.IntelliJLogProvider
import com.hyve.knowledge.bridge.toConfig
import com.hyve.knowledge.core.db.Corpus
import com.hyve.knowledge.core.db.KnowledgeDatabase
import com.hyve.knowledge.core.diff.DiffCache
import com.hyve.knowledge.core.diff.DiffEngine
import com.hyve.knowledge.core.diff.DiffExporter
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

    // ── Version Diff ──────────────────────────────────────────────

    @McpTool
    @McpDescription("""
        |Compare two indexed Hytale game versions to find what changed.
        |Shows added, removed, and changed nodes across code, game data, and client UI.
        |For game data changes, includes field-level diffs showing exactly what values changed.
    """)
    suspend fun diff_hytale_versions(
        @McpDescription("Version slug of the old version (e.g., release_2026.02.19-1a311a592)") versionA: String,
        @McpDescription("Version slug of the new version (e.g., pre-release_2026.02.26-7681d338c)") versionB: String,
        @McpDescription("Filter to a specific corpus: code, gamedata, client, or all (default: all)") corpus: String = "all",
        @McpDescription("Filter by change type: ADDED, REMOVED, CHANGED, or all (default: all)") changeType: String = "all",
        @McpDescription("Filter by data type (e.g., item, recipe, npc)") dataType: String? = null,
        @McpDescription("Maximum entries to return (default 50, max 200)") limit: Int = 50,
    ): String {
        val log = IntelliJLogProvider(HytaleKnowledgeToolset::class.java)
        val settings = KnowledgeSettings.getInstance()
        val basePath = settings.resolvedBasePath()

        val dbFileA = java.io.File(basePath, "versions/$versionA/knowledge.db")
        val dbFileB = java.io.File(basePath, "versions/$versionB/knowledge.db")

        if (!dbFileA.exists()) return "Error: Version A database not found at ${dbFileA.absolutePath}"
        if (!dbFileB.exists()) return "Error: Version B database not found at ${dbFileB.absolutePath}"

        val cache = DiffCache(basePath, log)
        val cached = cache.get(versionA, versionB)
        val diff = if (cached != null) {
            cached
        } else {
            val engine = DiffEngine(log)
            val result = engine.computeDiff(
                versionA = versionA,
                versionB = versionB,
                dbFileA = dbFileA,
                dbFileB = dbFileB,
                corpusFilter = if (corpus != "all") corpus else null,
                changeTypeFilter = if (changeType != "all") changeType else null,
                dataTypeFilter = dataType,
                limit = limit.coerceIn(1, 200),
            )
            cache.put(result)
            result
        }

        return DiffExporter.toMarkdown(diff)
    }

    // ── File Path Lookup ───────────────────────────────────────

    @McpTool
    @McpDescription("""
        |Resolve a class name, method name, or file path fragment to the absolute file path
        |of the decompiled Hytale source file on disk. Use this when you already know what
        |you're looking for and want to read the full file directly instead of doing a
        |semantic search. Returns file paths that you can then read with your standard file tools.
    """)
    suspend fun get_hytale_file_path(
        @McpDescription("Class name, method name, or file path fragment to look up (e.g., 'PlayerEntity', 'ItemRegistry', 'combat/Damage')") query: String,
        @McpDescription("Maximum results to return (default 10, max 50)") limit: Int = 10,
    ): String {
        val results = searchService.lookupFilePaths(query, limit.coerceIn(1, 50))
        val obj = buildJsonObject {
            put("query", query)
            put("resultCount", results.size)
            put("results", buildJsonArray {
                for (r in results) {
                    add(buildJsonObject {
                        put("displayName", r.displayName)
                        put("filePath", r.filePath)
                        put("nodeType", r.nodeType)
                    })
                }
            })
            if (results.isEmpty()) {
                put("hint", "No matches found. Try a shorter or different name fragment.")
            }
        }
        return json.encodeToString(obj)
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
