// Copyright 2026 Hyve. All rights reserved.
package com.hyve.knowledge.index

import com.hyve.knowledge.core.db.KnowledgeDatabase
import com.hyve.knowledge.extraction.ClientUIChunk
import com.hyve.knowledge.extraction.ClientUIType
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File
import java.nio.file.Files

class UIBindingExtractorTest {

    private lateinit var db: KnowledgeDatabase
    private lateinit var extractor: UIBindingExtractor
    private lateinit var tempFile: File

    @BeforeEach
    fun setUp() {
        tempFile = Files.createTempFile("ui_binding_test_", ".db").toFile()
        tempFile.deleteOnExit()
        db = KnowledgeDatabase.forFile(tempFile)

        // Insert realistic gamedata nodes to resolve against
        for ((id, name, type) in listOf(
            Triple("gamedata:item:workbench", "Workbench", "item"),
            Triple("gamedata:item:gold_coin", "Gold_Coin", "item"),
            Triple("gamedata:item:sword_iron", "Sword_Iron", "item"),
            Triple("gamedata:npc:blacksmith", "NPC_Blacksmith", "npc"),
            Triple("gamedata:npc:merchant", "NPC_Merchant", "npc"),
            Triple("gamedata:npc:trork_warrior", "TrorkWarrior", "npc"),
        )) {
            db.execute(
                "INSERT OR IGNORE INTO nodes (id, node_type, display_name, file_path, data_type, corpus) VALUES (?, 'GameData', ?, 'test.json', ?, 'gamedata')",
                id, name, type,
            )
        }

        extractor = UIBindingExtractor(db)
    }

    @AfterEach
    fun tearDown() {
        db.close()
        tempFile.delete()
    }

    private fun uiChunk(
        id: String,
        name: String,
        content: String,
    ): ClientUIChunk = ClientUIChunk(
        id = id,
        type = ClientUIType.UI,
        name = name,
        filePath = "test/$name.ui",
        relativePath = "InGame/$name.ui",
        fileHash = "abc",
        content = content,
        category = "InGame",
        textForEmbedding = "",
    )

    private fun xamlChunk(
        id: String,
        name: String,
        content: String,
    ): ClientUIChunk = ClientUIChunk(
        id = id,
        type = ClientUIType.XAML,
        name = name,
        filePath = "test/$name.xaml",
        relativePath = "InGame/$name.xaml",
        fileHash = "abc",
        content = content,
        category = "InGame",
        textForEmbedding = "",
    )

    // ── Strategy-specific resolution ──────────────────────────

    @Test
    fun `resource_path resolves crafting screen bench icon to Workbench gamedata`() {
        val chunk = uiChunk(
            "ui:InGame/CraftingScreen.ui", "CraftingScreen",
            """{ "icon": "Icons/Crafting/Workbench", "background": "UI/Panels/CraftingBg" }""",
        )
        val edges = extractor.extractEdges(listOf(chunk))

        assertTrue(edges.any { it.targetId == "gamedata:item:workbench" }, "Should resolve Workbench via resource_path")
        assertTrue(edges.none { it.metadata?.contains("CraftingBg") == true }, "CraftingBg has no gamedata match")
    }

    @Test
    fun `json_key resolves shop panel bindings to Gold_Coin and NPC_Blacksmith`() {
        val chunk = uiChunk(
            "ui:InGame/ShopPanel.ui", "ShopPanel",
            """{ "item": "Gold_Coin", "npc": "NPC_Blacksmith" }""",
        )
        val edges = extractor.extractEdges(listOf(chunk))

        assertTrue(edges.any { it.targetId == "gamedata:item:gold_coin" })
        assertTrue(edges.any { it.targetId == "gamedata:npc:blacksmith" })
    }

    @Test
    fun `pascal_case resolves TrorkWarrior from quest tracker content`() {
        val chunk = uiChunk(
            "ui:InGame/QuestTracker.ui", "QuestTracker",
            "Binding: Track active objectives for TrorkWarrior encounters",
        )
        val edges = extractor.extractEdges(listOf(chunk))

        assertTrue(edges.any { it.targetId == "gamedata:npc:trork_warrior" })
    }

    // ── XAML filtering ────────────────────────────────────────

    @Test
    fun `XAML chunks produce zero edges even with matching content`() {
        val xaml = xamlChunk(
            "xaml:InGame/ShopStyle.xaml", "ShopStyle",
            """{ "item": "Gold_Coin", "npc": "NPC_Blacksmith" }""",
        )
        val edges = extractor.extractEdges(listOf(xaml))

        assertTrue(edges.isEmpty(), "XAML chunks should not produce edges")
    }

    // ── Deduplication ─────────────────────────────────────────

    @Test
    fun `deduplicates same source-target pair keeping highest confidence`() {
        // Workbench appears via both resource_path (0.8) and json_key (0.6 — "item": "Workbench")
        val chunk = uiChunk(
            "ui:InGame/CraftingScreen.ui", "CraftingScreen",
            """{ "icon": "Icons/Crafting/Workbench", "item": "Workbench" }""",
        )
        val edges = extractor.extractEdges(listOf(chunk))

        val workbenchEdges = edges.filter { it.targetId == "gamedata:item:workbench" }
        assertEquals(1, workbenchEdges.size, "Should deduplicate to single edge per source-target pair")
        assertTrue(
            workbenchEdges[0].metadata?.contains("resource_path") == true,
            "Should keep resource_path (0.8) over json_key (0.6)",
        )
    }

    // ── Edge properties ───────────────────────────────────────

    @Test
    fun `all edges have UI_BINDS_TO type`() {
        val chunk = uiChunk(
            "ui:InGame/ShopPanel.ui", "ShopPanel",
            """{ "item": "Gold_Coin", "entity": "NPC_Merchant" }""",
        )
        val edges = extractor.extractEdges(listOf(chunk))

        assertTrue(edges.isNotEmpty())
        assertTrue(edges.all { it.edgeType == "UI_BINDS_TO" })
    }

    @Test
    fun `all edges include strategy and confidence in metadata`() {
        val chunk = uiChunk(
            "ui:InGame/ShopPanel.ui", "ShopPanel",
            """{ "item": "Gold_Coin" }""",
        )
        val edges = extractor.extractEdges(listOf(chunk))

        assertTrue(edges.isNotEmpty())
        assertTrue(edges.all { it.metadata?.contains("strategy") == true })
        assertTrue(edges.all { it.metadata?.contains("confidence") == true })
    }

    @Test
    fun `all edges are marked as target resolved`() {
        val chunk = uiChunk(
            "ui:InGame/ShopPanel.ui", "ShopPanel",
            """{ "item": "Gold_Coin" }""",
        )
        val edges = extractor.extractEdges(listOf(chunk))

        assertTrue(edges.isNotEmpty())
        assertTrue(edges.all { it.targetResolved })
    }

    // ── Multi-chunk processing ────────────────────────────────

    @Test
    fun `processes multiple UI chunks independently`() {
        val chunks = listOf(
            uiChunk("ui:InGame/CraftingScreen.ui", "CraftingScreen", """{ "icon": "Icons/Crafting/Workbench" }"""),
            uiChunk("ui:InGame/ShopPanel.ui", "ShopPanel", """{ "npc": "NPC_Blacksmith" }"""),
        )
        val edges = extractor.extractEdges(chunks)

        assertTrue(edges.any { it.sourceId == "ui:InGame/CraftingScreen.ui" && it.targetId == "gamedata:item:workbench" })
        assertTrue(edges.any { it.sourceId == "ui:InGame/ShopPanel.ui" && it.targetId == "gamedata:npc:blacksmith" })
    }

    // ── Negative cases ────────────────────────────────────────

    @Test
    fun `returns empty for UI content with no gamedata matches`() {
        val chunk = uiChunk(
            "ui:InGame/LoadingScreen.ui", "LoadingScreen",
            """{ "color": "#FF0000", "animation": "UI/Spinners/LoadingWheel" }""",
        )
        val edges = extractor.extractEdges(listOf(chunk))

        assertTrue(edges.isEmpty())
    }

    @Test
    fun `skips chunks with blank content`() {
        val chunk = uiChunk("ui:InGame/Spacer.ui", "Spacer", "")
        val edges = extractor.extractEdges(listOf(chunk))

        assertTrue(edges.isEmpty())
    }
}
