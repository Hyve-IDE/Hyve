// Copyright 2026 Hyve. All rights reserved.
package com.hyve.knowledge.mcp.standalone

import com.hyve.knowledge.core.db.Corpus
import com.hyve.knowledge.core.search.KnowledgeSearchService
import com.hyve.knowledge.core.search.SearchResult
import io.modelcontextprotocol.kotlin.sdk.server.ServerOptions
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.server.RegisteredTool
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.server.StdioServerTransport
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.ServerCapabilities
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import io.modelcontextprotocol.kotlin.sdk.types.Tool
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import kotlinx.io.asSink
import kotlinx.io.asSource
import kotlinx.io.buffered
import kotlinx.serialization.json.*

class HytaleKnowledgeServer(
    private val searchService: KnowledgeSearchService,
) {
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true; encodeDefaults = true }

    fun createServer(): Server {
        val server = Server(
            Implementation(name = "hyve-knowledge", version = "1.0.0"),
            ServerOptions(capabilities = ServerCapabilities(
                tools = ServerCapabilities.Tools(listChanged = false),
                logging = null,
                experimental = null,
                prompts = null,
                resources = null,
            ))
        )

        server.addTools(listOf(
            searchCodeTool(),
            searchClientTool(),
            searchGamedataTool(),
            searchDocsTool(),
            codeStatsTool(),
            clientStatsTool(),
            gamedataStatsTool(),
            docsStatsTool(),
        ))

        return server
    }

    suspend fun run() {
        val server = createServer()
        val transport = StdioServerTransport(
            System.`in`.asSource().buffered(),
            System.out.asSink().buffered(),
        )
        val done = kotlinx.coroutines.CompletableDeferred<Unit>()
        transport.onClose { done.complete(Unit) }
        server.createSession(transport)
        System.err.println("[INFO] Hyve Knowledge MCP server running on stdio")
        done.await()
    }

    // ── Tool definitions ─────────────────────────────────────────

    private fun searchCodeTool(): RegisteredTool {
        val tool = Tool(
            name = "search_hytale_code",
            description = "Search the decompiled Hytale server codebase using semantic search. " +
                "Returns relevant Java methods with their full source code.",
            inputSchema = toolSchema(
                "query" to propString("Natural language description of what you're looking for"),
                "classFilter" to propString("Filter results to a specific class name"),
                "limit" to propInt("Number of results to return (default 5, max 20)"),
                "expand" to propBool("Enable graph expansion to find related game data, UI, and docs (default false)"),
                required = listOf("query"),
            ),
        )
        return RegisteredTool(tool) { request ->
            val query = request.arguments?.getString("query") ?: return@RegisteredTool errorResult("Missing 'query' parameter")
            val classFilter = request.arguments?.getString("classFilter")
            val limit = request.arguments?.getInt("limit") ?: 5
            val expand = request.arguments?.getBool("expand") ?: false
            try {
                val results = if (expand) {
                    searchService.searchWithExpansion(query, Corpus.entries, limit.coerceIn(1, 20))
                } else {
                    searchService.searchCode(query, classFilter, limit.coerceIn(1, 20))
                }
                successResult(encodeSearchResults(query, results))
            } catch (e: Exception) {
                errorResult("Search failed: ${e.message}")
            }
        }
    }

    private fun searchClientTool(): RegisteredTool {
        val tool = Tool(
            name = "search_hytale_client_code",
            description = "Search Hytale client UI files (.xaml, .ui, .json) using semantic search. " +
                "Useful for modifying game UI appearance like inventory layout, hotbar, health bars.",
            inputSchema = toolSchema(
                "query" to propString("Natural language description of what UI element you're looking for"),
                "classFilter" to propString("Filter results to a specific category (e.g., DesignSystem, InGame, MainMenu)"),
                "limit" to propInt("Number of results to return (default 5, max 20)"),
                "expand" to propBool("Enable graph expansion to find related game data and code (default false)"),
                required = listOf("query"),
            ),
        )
        return RegisteredTool(tool) { request ->
            val query = request.arguments?.getString("query") ?: return@RegisteredTool errorResult("Missing 'query' parameter")
            val classFilter = request.arguments?.getString("classFilter")
            val limit = request.arguments?.getInt("limit") ?: 5
            val expand = request.arguments?.getBool("expand") ?: false
            try {
                val results = if (expand) {
                    searchService.searchWithExpansion(query, listOf(Corpus.CLIENT, Corpus.GAMEDATA, Corpus.CODE), limit.coerceIn(1, 20))
                } else {
                    searchService.searchCorpus(query, Corpus.CLIENT, limit.coerceIn(1, 20), classFilter)
                }
                successResult(encodeSearchResults(query, results))
            } catch (e: Exception) {
                errorResult("Search failed: ${e.message}")
            }
        }
    }

    private fun searchGamedataTool(): RegisteredTool {
        val tool = Tool(
            name = "search_hytale_gamedata",
            description = "Search vanilla Hytale game data including items, recipes, NPCs, drops, blocks, and more. " +
                "Use this for modding questions like 'how to craft X', 'what drops Y', 'NPC behavior for Z'.",
            inputSchema = toolSchema(
                "query" to propString("Natural language question about Hytale game data"),
                "type" to propString("Filter by data type (e.g., item, recipe, npc, block, drop, shop)"),
                "limit" to propInt("Number of results to return (default 5, max 20)"),
                "expand" to propBool("Enable graph expansion to find related code and UI (default false)"),
                required = listOf("query"),
            ),
        )
        return RegisteredTool(tool) { request ->
            val query = request.arguments?.getString("query") ?: return@RegisteredTool errorResult("Missing 'query' parameter")
            val type = request.arguments?.getString("type")
            val limit = request.arguments?.getInt("limit") ?: 5
            val expand = request.arguments?.getBool("expand") ?: false
            try {
                val results = if (expand) {
                    searchService.searchWithExpansion(query, listOf(Corpus.GAMEDATA, Corpus.CODE, Corpus.CLIENT), limit.coerceIn(1, 20))
                } else {
                    searchService.searchCorpus(query, Corpus.GAMEDATA, limit.coerceIn(1, 20), type)
                }
                successResult(encodeSearchResults(query, results))
            } catch (e: Exception) {
                errorResult("Search failed: ${e.message}")
            }
        }
    }

    private fun searchDocsTool(): RegisteredTool {
        val tool = Tool(
            name = "search_hytale_docs",
            description = "Search HytaleModding.dev documentation using semantic search. " +
                "Find modding guides, tutorials, and reference documentation covering plugin development, ECS, blocks, commands, events.",
            inputSchema = toolSchema(
                "query" to propString("Natural language question about Hytale modding"),
                "type" to propString("Filter by documentation type"),
                "limit" to propInt("Number of results to return (default 5, max 20)"),
                "expand" to propBool("Enable graph expansion to find related code and game data (default false)"),
                required = listOf("query"),
            ),
        )
        return RegisteredTool(tool) { request ->
            val query = request.arguments?.getString("query") ?: return@RegisteredTool errorResult("Missing 'query' parameter")
            val type = request.arguments?.getString("type")
            val limit = request.arguments?.getInt("limit") ?: 5
            val expand = request.arguments?.getBool("expand") ?: false
            try {
                val results = if (expand) {
                    searchService.searchWithExpansion(query, listOf(Corpus.DOCS, Corpus.CODE, Corpus.GAMEDATA), limit.coerceIn(1, 20))
                } else {
                    searchService.searchCorpus(query, Corpus.DOCS, limit.coerceIn(1, 20), type)
                }
                successResult(encodeSearchResults(query, results))
            } catch (e: Exception) {
                errorResult("Search failed: ${e.message}")
            }
        }
    }

    private fun codeStatsTool(): RegisteredTool {
        val tool = Tool(
            name = "hytale_code_stats",
            description = "Get statistics about the indexed Hytale server codebase.",
            inputSchema = toolSchema(),
        )
        return RegisteredTool(tool) { _ ->
            try {
                val stats = searchService.getCorpusStats(Corpus.CODE)
                successResult(json.encodeToString(com.hyve.knowledge.core.search.IndexStats.serializer(), stats))
            } catch (e: Exception) {
                errorResult("Failed to get stats: ${e.message}")
            }
        }
    }

    private fun clientStatsTool(): RegisteredTool {
        val tool = Tool(
            name = "hytale_client_code_stats",
            description = "Get statistics about the indexed Hytale client UI files.",
            inputSchema = toolSchema(),
        )
        return RegisteredTool(tool) { _ ->
            try {
                val stats = searchService.getCorpusStats(Corpus.CLIENT)
                successResult(json.encodeToString(com.hyve.knowledge.core.search.IndexStats.serializer(), stats))
            } catch (e: Exception) {
                errorResult("Failed to get stats: ${e.message}")
            }
        }
    }

    private fun gamedataStatsTool(): RegisteredTool {
        val tool = Tool(
            name = "hytale_gamedata_stats",
            description = "Get statistics about the indexed Hytale game data.",
            inputSchema = toolSchema(),
        )
        return RegisteredTool(tool) { _ ->
            try {
                val stats = searchService.getCorpusStats(Corpus.GAMEDATA)
                successResult(json.encodeToString(com.hyve.knowledge.core.search.IndexStats.serializer(), stats))
            } catch (e: Exception) {
                errorResult("Failed to get stats: ${e.message}")
            }
        }
    }

    private fun docsStatsTool(): RegisteredTool {
        val tool = Tool(
            name = "hytale_docs_stats",
            description = "Get statistics about the indexed HytaleModding.dev documentation.",
            inputSchema = toolSchema(),
        )
        return RegisteredTool(tool) { _ ->
            try {
                val stats = searchService.getCorpusStats(Corpus.DOCS)
                successResult(json.encodeToString(com.hyve.knowledge.core.search.IndexStats.serializer(), stats))
            } catch (e: Exception) {
                errorResult("Failed to get stats: ${e.message}")
            }
        }
    }

    // ── Helpers ───────────────────────────────────────────────────

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
                                for (id in r.connectedNodeIds) add(JsonPrimitive(id))
                            })
                        }
                    })
                }
            })
        }
        return json.encodeToString(obj)
    }

    private fun successResult(jsonText: String): CallToolResult {
        return CallToolResult(content = listOf(TextContent(text = jsonText)))
    }

    private fun errorResult(message: String): CallToolResult {
        return CallToolResult(content = listOf(TextContent(text = message)), isError = true)
    }

    private fun toolSchema(
        vararg properties: Pair<String, JsonObject>,
        required: List<String> = emptyList(),
    ): ToolSchema {
        val props = buildJsonObject {
            for ((name, schema) in properties) {
                put(name, schema)
            }
        }
        return ToolSchema(
            properties = props,
            required = required,
        )
    }

    private fun propString(description: String): JsonObject = buildJsonObject {
        put("type", "string")
        put("description", description)
    }

    private fun propInt(description: String): JsonObject = buildJsonObject {
        put("type", "integer")
        put("description", description)
    }

    private fun propBool(description: String): JsonObject = buildJsonObject {
        put("type", "boolean")
        put("description", description)
    }

    private fun Map<String, JsonElement>.getString(key: String): String? =
        (this[key] as? JsonPrimitive)?.contentOrNull

    private fun Map<String, JsonElement>.getInt(key: String): Int? =
        (this[key] as? JsonPrimitive)?.intOrNull

    private fun Map<String, JsonElement>.getBool(key: String): Boolean? =
        (this[key] as? JsonPrimitive)?.booleanOrNull
}
