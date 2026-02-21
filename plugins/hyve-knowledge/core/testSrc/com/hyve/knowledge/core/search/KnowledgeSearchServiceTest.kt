// Copyright 2026 Hyve. All rights reserved.
package com.hyve.knowledge.core.search

import com.hyve.knowledge.core.config.KnowledgeConfig
import com.hyve.knowledge.core.db.KnowledgeDatabase
import com.hyve.knowledge.core.index.CorpusIndexManager
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File
import java.nio.file.Files

class KnowledgeSearchServiceTest {

    private lateinit var service: KnowledgeSearchService

    @BeforeEach
    fun setUp() {
        val tempFile = Files.createTempFile("search_service_test_", ".db").toFile()
        tempFile.deleteOnExit()
        val db = KnowledgeDatabase.forFile(tempFile)
        val config = KnowledgeConfig()
        val indexManager = CorpusIndexManager(config)
        service = KnowledgeSearchService(db, indexManager)
    }

    // ── detectGamedataIntent ─────────────────────────────────

    @Test
    fun `detectGamedataIntent returns recipe and item for crafting queries`() {
        val result = service.detectGamedataIntent("how to craft a torch")
        assertNotNull(result)
        assertTrue(result!!.contains("recipe"))
        assertTrue(result.contains("item"))
    }

    @Test
    fun `detectGamedataIntent returns drop and npc for loot queries`() {
        val result = service.detectGamedataIntent("what drops from goblin warrior")
        assertNotNull(result)
        assertTrue(result!!.contains("drop"))
        assertTrue(result.contains("npc"))
    }

    @Test
    fun `detectGamedataIntent returns shop for merchant queries`() {
        val result = service.detectGamedataIntent("where can I buy leather")
        assertNotNull(result)
        assertTrue(result!!.contains("shop"))
    }

    @Test
    fun `detectGamedataIntent returns null for generic queries`() {
        val result = service.detectGamedataIntent("tell me about the world of Orbis")
        assertNull(result)
    }

    @Test
    fun `detectGamedataIntent returns objective for quest queries`() {
        val result = service.detectGamedataIntent("how to complete the goblin quest")
        assertNotNull(result)
        assertTrue(result!!.contains("objective"))
    }

    @Test
    fun `detectGamedataIntent accumulates multiple intents`() {
        val result = service.detectGamedataIntent("which hostile mobs spawn in which biomes")
        assertNotNull(result)
        assertTrue(result!!.contains("npc"))
        assertTrue(result.contains("npc_group"))
        assertTrue(result.contains("biome"))
    }

    // ── deduplicateResults ───────────────────────────────────

    private fun result(nodeId: String, score: Double, bridgedFrom: String? = null, bridgeEdgeType: String? = null): SearchResult {
        return SearchResult(
            nodeId = nodeId,
            displayName = nodeId.substringAfterLast(':'),
            snippet = "test",
            filePath = "test.json",
            lineStart = 0,
            score = score,
            source = ResultSource.VECTOR,
            bridgedFrom = bridgedFrom,
            bridgeEdgeType = bridgeEdgeType,
        )
    }

    @Test
    fun `deduplicateResults keeps higher-scored version`() {
        val results = listOf(
            result("node:A", 0.9),
            result("node:B", 0.8),
            result("node:A", 0.5),
        )
        val deduped = service.deduplicateResults(results)
        assertEquals(2, deduped.size)
        val nodeA = deduped.first { it.nodeId == "node:A" }
        assertEquals(0.9, nodeA.score)
    }

    @Test
    fun `deduplicateResults merges provenance from lower-scored duplicate`() {
        val results = listOf(
            result("node:X", 0.9),
            result("node:X", 0.4, bridgedFrom = "Torch", bridgeEdgeType = "IMPLEMENTED_BY"),
        )
        val deduped = service.deduplicateResults(results)
        assertEquals(1, deduped.size)
        val node = deduped[0]
        assertEquals(0.9, node.score)
        assertEquals("Torch", node.bridgedFrom)
        assertEquals("IMPLEMENTED_BY", node.bridgeEdgeType)
    }

    @Test
    fun `deduplicateResults preserves insertion order`() {
        val results = listOf(
            result("node:First", 0.8),
            result("node:Second", 0.9),
            result("node:Third", 0.7),
        )
        val deduped = service.deduplicateResults(results)
        assertEquals(3, deduped.size)
        assertEquals("node:First", deduped[0].nodeId)
        assertEquals("node:Second", deduped[1].nodeId)
        assertEquals("node:Third", deduped[2].nodeId)
    }
}
