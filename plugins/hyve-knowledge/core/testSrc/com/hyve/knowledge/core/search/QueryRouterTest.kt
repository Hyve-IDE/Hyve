// Copyright 2026 Hyve. All rights reserved.
package com.hyve.knowledge.core.search

import com.hyve.knowledge.core.db.KnowledgeDatabase
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File
import java.nio.file.Files

class QueryRouterTest {

    private lateinit var db: KnowledgeDatabase
    private lateinit var router: QueryRouter
    private lateinit var tempFile: File

    @BeforeEach
    fun setUp() {
        tempFile = Files.createTempFile("query_router_test_", ".db").toFile()
        tempFile.deleteOnExit()
        db = KnowledgeDatabase.forFile(tempFile)
        router = QueryRouter(db)
    }

    @AfterEach
    fun tearDown() {
        db.close()
        tempFile.delete()
    }

    @Test
    fun `EXTENDS pattern takes priority over gamedata patterns`() {
        val result = router.route("what extends BaseItem")
        assertEquals(QueryStrategy.GRAPH, result.strategy)
        assertEquals("EXTENDS", result.relation)
    }

    @Test
    fun `CRAFT_PATTERN matches 'how to craft torch'`() {
        val result = router.route("how to craft torch")
        assertEquals(QueryStrategy.HYBRID, result.strategy)
        assertEquals("torch", result.entityName)
        assertEquals("REQUIRES_ITEM", result.relation)
    }

    @Test
    fun `CRAFT_PATTERN matches 'what to make copper'`() {
        val result = router.route("what to make copper")
        assertEquals(QueryStrategy.HYBRID, result.strategy)
        assertEquals("REQUIRES_ITEM", result.relation)
    }

    @Test
    fun `DROP_FROM_PATTERN matches 'what drops from goblin'`() {
        val result = router.route("what drops from goblin")
        assertEquals(QueryStrategy.GRAPH, result.strategy)
        assertEquals("goblin", result.entityName)
        assertEquals("DROPS_ON_DEATH", result.relation)
    }

    @Test
    fun `USES_ITEM_PATTERN matches 'what uses copper'`() {
        val result = router.route("what uses copper")
        assertEquals(QueryStrategy.GRAPH, result.strategy)
        assertEquals("copper", result.entityName)
        assertEquals("REQUIRES_ITEM", result.relation)
    }

    @Test
    fun `BUY_PATTERN matches 'where to buy leather'`() {
        val result = router.route("where to buy leather")
        assertEquals(QueryStrategy.GRAPH, result.strategy)
        assertEquals("leather", result.entityName)
        assertEquals("OFFERED_IN_SHOP", result.relation)
    }

    @Test
    fun `BUY_PATTERN matches 'who sells torches'`() {
        val result = router.route("who sells torches")
        assertEquals(QueryStrategy.GRAPH, result.strategy)
        assertEquals("OFFERED_IN_SHOP", result.relation)
    }

    @Test
    fun `UI_SHOWS_PATTERN matches 'which ui shows Workbench'`() {
        val result = router.route("which ui shows Workbench")
        assertEquals(QueryStrategy.GRAPH, result.strategy)
        assertEquals("Workbench", result.entityName)
        assertEquals("UI_BINDS_TO", result.relation)
    }

    @Test
    fun `UI_SHOWS_PATTERN matches 'what screen displays Gold_Coin'`() {
        val result = router.route("what screen displays Gold_Coin")
        assertEquals(QueryStrategy.GRAPH, result.strategy)
        assertEquals("Gold_Coin", result.entityName)
        assertEquals("UI_BINDS_TO", result.relation)
    }

    @Test
    fun `UI_SHOWS_PATTERN matches 'which panel contains NPC_Blacksmith'`() {
        val result = router.route("which panel contains NPC_Blacksmith")
        assertEquals(QueryStrategy.GRAPH, result.strategy)
        assertEquals("NPC_Blacksmith", result.entityName)
        assertEquals("UI_BINDS_TO", result.relation)
    }

    @Test
    fun `UI_SHOWS_PATTERN matches 'which view for crafting'`() {
        val result = router.route("which view for crafting")
        assertEquals(QueryStrategy.GRAPH, result.strategy)
        assertEquals("crafting", result.entityName)
        assertEquals("UI_BINDS_TO", result.relation)
    }

    @Test
    fun `IMPLEMENTS_PATTERN matches 'what implements Serializable'`() {
        val result = router.route("what implements Serializable")
        assertEquals(QueryStrategy.GRAPH, result.strategy)
        assertEquals("Serializable", result.entityName)
        assertEquals("IMPLEMENTS", result.relation)
    }

    @Test
    fun `CALLS_PATTERN matches 'what calls processItem'`() {
        val result = router.route("what calls processItem")
        assertEquals(QueryStrategy.GRAPH, result.strategy)
        assertEquals("processItem", result.entityName)
        assertEquals("CALLS", result.relation)
    }

    @Test
    fun `METHODS_OF_PATTERN matches 'methods of DamageCalculator'`() {
        val result = router.route("methods of DamageCalculator")
        assertEquals(QueryStrategy.GRAPH, result.strategy)
        assertEquals("DamageCalculator", result.entityName)
        assertEquals("CONTAINS", result.relation)
    }

    @Test
    fun `FIND_CLASS_PATTERN with known entity routes to HYBRID`() {
        db.execute(
            "INSERT OR IGNORE INTO nodes (id, node_type, display_name, file_path, corpus) VALUES (?, 'JavaClass', ?, 'src/ItemRegistry.java', 'code')",
            "class:ItemRegistry", "ItemRegistry",
        )
        val result = router.route("find ItemRegistry")
        assertEquals(QueryStrategy.HYBRID, result.strategy)
        assertEquals("ItemRegistry", result.entityName)
    }

    @Test
    fun `unmatched query falls through to VECTOR`() {
        val result = router.route("tell me about the lore of orbis")
        assertEquals(QueryStrategy.VECTOR, result.strategy)
        assertNull(result.entityName)
    }
}
