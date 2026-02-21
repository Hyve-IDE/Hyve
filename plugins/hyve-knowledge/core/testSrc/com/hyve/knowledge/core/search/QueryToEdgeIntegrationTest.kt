// Copyright 2026 Hyve. All rights reserved.
package com.hyve.knowledge.core.search

import com.hyve.knowledge.core.db.KnowledgeDatabase
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File
import java.nio.file.Files

class QueryToEdgeIntegrationTest {

    private lateinit var db: KnowledgeDatabase
    private lateinit var router: QueryRouter
    private lateinit var traversal: GraphTraversal
    private lateinit var tempFile: File

    @BeforeEach
    fun setUp() {
        tempFile = Files.createTempFile("integration_test_", ".db").toFile()
        tempFile.deleteOnExit()
        db = KnowledgeDatabase.forFile(tempFile)
        router = QueryRouter(db)
        traversal = GraphTraversal(db)
    }

    @AfterEach
    fun tearDown() {
        db.close()
        tempFile.delete()
    }

    private fun insertNode(id: String, name: String, type: String = "GameData", dataType: String? = null, corpus: String = "gamedata") {
        db.execute(
            "INSERT OR IGNORE INTO nodes (id, node_type, display_name, file_path, data_type, corpus) VALUES (?, ?, ?, 'test.json', ?, ?)",
            id, type, name, dataType, corpus,
        )
    }

    private fun insertEdge(src: String, tgt: String, edgeType: String) {
        db.execute(
            "INSERT OR IGNORE INTO edges (source_id, target_id, edge_type) VALUES (?, ?, ?)",
            src, tgt, edgeType,
        )
    }

    @Test
    fun `'how to craft torch' routes to HYBRID and finds ingredient items via REQUIRES_ITEM edges`() {
        insertNode("gamedata:item:torch", "Torch", dataType = "item")
        insertNode("gamedata:item:wood", "Wood_Log", dataType = "item")
        insertNode("gamedata:item:resin", "Resin", dataType = "item")
        insertEdge("gamedata:item:torch", "gamedata:item:wood", "REQUIRES_ITEM")
        insertEdge("gamedata:item:torch", "gamedata:item:resin", "REQUIRES_ITEM")

        val route = router.route("how to craft torch")
        assertEquals(QueryStrategy.HYBRID, route.strategy)
        assertEquals("REQUIRES_ITEM", route.relation)
        assertEquals("torch", route.entityName)

        val nodeIds = db.query(
            "SELECT id FROM nodes WHERE LOWER(display_name) = LOWER(?) AND corpus = 'gamedata' LIMIT 1",
            route.entityName!!,
        ) { it.getString("id") }
        assertTrue(nodeIds.isNotEmpty())

        val inputs = traversal.findRecipeInputs(nodeIds.first())
        val inputIds = inputs.map { it.nodeId }
        assertTrue(inputIds.contains("gamedata:item:wood"))
        assertTrue(inputIds.contains("gamedata:item:resin"))
    }

    @Test
    fun `'what uses copper' routes to GRAPH and finds recipes requiring copper via reverse REQUIRES_ITEM`() {
        insertNode("gamedata:item:copper", "Copper", dataType = "item")
        insertNode("gamedata:recipe:sword", "Recipe_Sword", dataType = "recipe")
        insertEdge("gamedata:recipe:sword", "gamedata:item:copper", "REQUIRES_ITEM")

        val route = router.route("what uses copper")
        assertEquals(QueryStrategy.GRAPH, route.strategy)
        assertEquals("REQUIRES_ITEM", route.relation)

        val nodeIds = db.query(
            "SELECT id FROM nodes WHERE LOWER(display_name) = LOWER(?) AND corpus = 'gamedata' LIMIT 1",
            route.entityName!!,
        ) { it.getString("id") }
        assertTrue(nodeIds.isNotEmpty())

        val results = traversal.findRecipeInputs(nodeIds.first())
        assertTrue(results.any { it.nodeId == "gamedata:recipe:sword" })
    }

    @Test
    fun `'what drops from goblin' routes to GRAPH and follows 2-hop DROPS_ON_DEATH to DROPS_ITEM`() {
        insertNode("gamedata:npc:goblin", "Goblin", dataType = "npc")
        insertNode("gamedata:drop:goblin_loot", "GoblinLoot", dataType = "drop")
        insertNode("gamedata:item:gold", "Gold_Coin", dataType = "item")
        insertEdge("gamedata:npc:goblin", "gamedata:drop:goblin_loot", "DROPS_ON_DEATH")
        insertEdge("gamedata:drop:goblin_loot", "gamedata:item:gold", "DROPS_ITEM")

        val route = router.route("what drops from goblin")
        assertEquals(QueryStrategy.GRAPH, route.strategy)
        assertEquals("DROPS_ON_DEATH", route.relation)

        val nodeIds = db.query(
            "SELECT id FROM nodes WHERE LOWER(display_name) = LOWER(?) AND corpus = 'gamedata' LIMIT 1",
            route.entityName!!,
        ) { it.getString("id") }
        assertTrue(nodeIds.isNotEmpty())

        val drops = traversal.findDropsFrom(nodeIds.first())
        assertEquals(1, drops.size)
        assertEquals("gamedata:item:gold", drops[0].nodeId)
    }

    @Test
    fun `'where to buy leather' routes to GRAPH and follows reverse OFFERED_IN_SHOP`() {
        insertNode("gamedata:item:leather", "Leather", dataType = "item")
        insertNode("gamedata:shop:tanner", "Tanner_Shop", dataType = "shop")
        insertEdge("gamedata:shop:tanner", "gamedata:item:leather", "OFFERED_IN_SHOP")

        val route = router.route("where to buy leather")
        assertEquals(QueryStrategy.GRAPH, route.strategy)
        assertEquals("OFFERED_IN_SHOP", route.relation)

        val nodeIds = db.query(
            "SELECT id FROM nodes WHERE LOWER(display_name) = LOWER(?) AND corpus = 'gamedata' LIMIT 1",
            route.entityName!!,
        ) { it.getString("id") }
        assertTrue(nodeIds.isNotEmpty())

        val shops = traversal.findShopsSellingItem(nodeIds.first())
        assertEquals(1, shops.size)
        assertEquals("gamedata:shop:tanner", shops[0].nodeId)
    }

    @Test
    fun `'which ui shows Workbench' routes to GRAPH and follows reverse UI_BINDS_TO`() {
        insertNode("gamedata:item:workbench", "Workbench", dataType = "item")
        insertNode("ui:CraftingScreen.ui", "CraftingScreen", type = "ui", corpus = "client")
        insertEdge("ui:CraftingScreen.ui", "gamedata:item:workbench", "UI_BINDS_TO")

        val route = router.route("which ui shows Workbench")
        assertEquals(QueryStrategy.GRAPH, route.strategy)
        assertEquals("UI_BINDS_TO", route.relation)
        assertEquals("Workbench", route.entityName)

        val nodeIds = db.query(
            "SELECT id FROM nodes WHERE LOWER(display_name) = LOWER(?) AND corpus = 'gamedata' LIMIT 1",
            route.entityName!!,
        ) { it.getString("id") }
        assertTrue(nodeIds.isNotEmpty())

        val screens = traversal.findUIForGamedata(nodeIds.first())
        assertEquals(1, screens.size)
        assertEquals("ui:CraftingScreen.ui", screens[0].nodeId)
    }

    @Test
    fun `findGroupMembers returns expected NPCs for goblin party`() {
        insertNode("gamedata:group:goblin_party", "GoblinParty", dataType = "npc_group")
        insertNode("gamedata:npc:goblin_scout", "Goblin_Scout", dataType = "npc")
        insertNode("gamedata:npc:goblin_chief", "Goblin_Chief", dataType = "npc")
        insertEdge("gamedata:group:goblin_party", "gamedata:npc:goblin_scout", "HAS_MEMBER")
        insertEdge("gamedata:group:goblin_party", "gamedata:npc:goblin_chief", "HAS_MEMBER")

        val members = traversal.findGroupMembers("gamedata:group:goblin_party")
        val ids = members.map { it.nodeId }.toSet()
        assertEquals(2, members.size)
        assertTrue(ids.contains("gamedata:npc:goblin_scout"))
        assertTrue(ids.contains("gamedata:npc:goblin_chief"))
    }
}
