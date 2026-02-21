// Copyright 2026 Hyve. All rights reserved.
package com.hyve.knowledge.mcp.standalone

import com.hyve.knowledge.core.config.KnowledgeConfig
import com.hyve.knowledge.core.db.KnowledgeDatabase
import com.hyve.knowledge.core.index.CorpusIndexManager
import com.hyve.knowledge.core.search.KnowledgeSearchService
import io.modelcontextprotocol.kotlin.sdk.types.CallToolRequest
import io.modelcontextprotocol.kotlin.sdk.types.CallToolRequestParams
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.nio.file.Files

class HytaleKnowledgeServerTest {

    private lateinit var server: HytaleKnowledgeServer
    private lateinit var db: KnowledgeDatabase
    private val json = Json { ignoreUnknownKeys = true }

    @BeforeEach
    fun setUp() {
        val tempFile = Files.createTempFile("mcp_server_test_", ".db").toFile()
        tempFile.deleteOnExit()
        db = KnowledgeDatabase.forFile(tempFile)
        val config = KnowledgeConfig()
        val indexManager = CorpusIndexManager(config)
        val searchService = KnowledgeSearchService(db, indexManager)
        server = HytaleKnowledgeServer(searchService)
    }

    @AfterEach
    fun tearDown() {
        db.close()
    }

    // ── Tool registration ─────────────────────────────────────

    @Test
    fun `createServer registers exactly 8 tools`() {
        val mcpServer = server.createServer()
        assertEquals(8, mcpServer.tools.size)
    }

    @Test
    fun `createServer registers all expected tool names`() {
        val mcpServer = server.createServer()
        val names = mcpServer.tools.keys
        assertAll(
            { assertTrue("search_hytale_code" in names) },
            { assertTrue("search_hytale_client_code" in names) },
            { assertTrue("search_hytale_gamedata" in names) },
            { assertTrue("search_hytale_docs" in names) },
            { assertTrue("hytale_code_stats" in names) },
            { assertTrue("hytale_client_code_stats" in names) },
            { assertTrue("hytale_gamedata_stats" in names) },
            { assertTrue("hytale_docs_stats" in names) },
        )
    }

    @Test
    fun `search tools have required query parameter`() {
        val mcpServer = server.createServer()
        val searchTools = listOf(
            "search_hytale_code",
            "search_hytale_client_code",
            "search_hytale_gamedata",
            "search_hytale_docs",
        )
        for (name in searchTools) {
            val tool = mcpServer.tools[name]!!.tool
            assertTrue(
                tool.inputSchema.required?.contains("query") == true,
                "$name should require 'query' parameter"
            )
        }
    }

    @Test
    fun `stats tools have no required parameters`() {
        val mcpServer = server.createServer()
        val statsTools = listOf(
            "hytale_code_stats",
            "hytale_client_code_stats",
            "hytale_gamedata_stats",
            "hytale_docs_stats",
        )
        for (name in statsTools) {
            val tool = mcpServer.tools[name]!!.tool
            assertTrue(
                tool.inputSchema.required.isNullOrEmpty(),
                "$name should have no required parameters"
            )
        }
    }

    // ── Search tool handlers ──────────────────────────────────

    @Test
    fun `search_hytale_code returns error when query is missing`() = runBlocking {
        val mcpServer = server.createServer()
        val tool = mcpServer.tools["search_hytale_code"]!!
        val result = tool.handler(callToolRequest("search_hytale_code"))

        assertTrue(result.isError == true)
        val text = (result.content.first() as io.modelcontextprotocol.kotlin.sdk.types.TextContent).text
        assertTrue(text.contains("Missing 'query' parameter"))
    }

    @Test
    fun `search_hytale_code returns empty results for query on empty db`() = runBlocking {
        val mcpServer = server.createServer()
        val tool = mcpServer.tools["search_hytale_code"]!!
        val result = tool.handler(callToolRequest("search_hytale_code", buildJsonObject {
            put("query", "how do items drop")
        }))

        assertNull(result.isError)
        val text = (result.content.first() as io.modelcontextprotocol.kotlin.sdk.types.TextContent).text
        val parsed = json.parseToJsonElement(text).jsonObject
        assertEquals("how do items drop", parsed["query"]?.jsonPrimitive?.content)
        assertEquals(0, parsed["resultCount"]?.jsonPrimitive?.content?.toInt())
        assertTrue(parsed["results"]?.jsonArray?.isEmpty() == true)
    }

    @Test
    fun `search_hytale_gamedata returns error when query is missing`() = runBlocking {
        val mcpServer = server.createServer()
        val tool = mcpServer.tools["search_hytale_gamedata"]!!
        val result = tool.handler(callToolRequest("search_hytale_gamedata"))

        assertTrue(result.isError == true)
    }

    @Test
    fun `search_hytale_docs returns empty results on empty db`() = runBlocking {
        val mcpServer = server.createServer()
        val tool = mcpServer.tools["search_hytale_docs"]!!
        val result = tool.handler(callToolRequest("search_hytale_docs", buildJsonObject {
            put("query", "how to create a mod")
        }))

        assertNull(result.isError)
        val text = (result.content.first() as io.modelcontextprotocol.kotlin.sdk.types.TextContent).text
        val parsed = json.parseToJsonElement(text).jsonObject
        assertEquals(0, parsed["resultCount"]?.jsonPrimitive?.content?.toInt())
    }

    @Test
    fun `search tool respects limit parameter`() = runBlocking {
        val mcpServer = server.createServer()
        val tool = mcpServer.tools["search_hytale_client_code"]!!
        val result = tool.handler(callToolRequest("search_hytale_client_code", buildJsonObject {
            put("query", "inventory layout")
            put("limit", 3)
        }))

        assertNull(result.isError)
        val text = (result.content.first() as io.modelcontextprotocol.kotlin.sdk.types.TextContent).text
        val parsed = json.parseToJsonElement(text).jsonObject
        assertEquals("inventory layout", parsed["query"]?.jsonPrimitive?.content)
    }

    // ── Stats tool handlers ───────────────────────────────────

    @Test
    fun `hytale_code_stats returns valid stats on empty db`() = runBlocking {
        val mcpServer = server.createServer()
        val tool = mcpServer.tools["hytale_code_stats"]!!
        val result = tool.handler(callToolRequest("hytale_code_stats"))

        assertNull(result.isError)
        val text = (result.content.first() as io.modelcontextprotocol.kotlin.sdk.types.TextContent).text
        val parsed = json.parseToJsonElement(text).jsonObject
        assertEquals(0, parsed["nodeCount"]?.jsonPrimitive?.content?.toInt())
    }

    @Test
    fun `hytale_gamedata_stats returns valid stats on empty db`() = runBlocking {
        val mcpServer = server.createServer()
        val tool = mcpServer.tools["hytale_gamedata_stats"]!!
        val result = tool.handler(callToolRequest("hytale_gamedata_stats"))

        assertNull(result.isError)
        val text = (result.content.first() as io.modelcontextprotocol.kotlin.sdk.types.TextContent).text
        val parsed = json.parseToJsonElement(text).jsonObject
        assertNotNull(parsed["nodeCount"])
    }

    @Test
    fun `all stats tools return without error`() = runBlocking {
        val mcpServer = server.createServer()
        val statsTools = listOf(
            "hytale_code_stats",
            "hytale_client_code_stats",
            "hytale_gamedata_stats",
            "hytale_docs_stats",
        )
        for (name in statsTools) {
            val tool = mcpServer.tools[name]!!
            val result = tool.handler(callToolRequest(name))
            assertNull(result.isError, "$name returned an error")
        }
    }

    // ── Helpers ───────────────────────────────────────────────

    private fun callToolRequest(
        name: String,
        arguments: JsonObject? = null,
    ): CallToolRequest = CallToolRequest(
        params = CallToolRequestParams(
            name = name,
            arguments = arguments,
        )
    )
}
