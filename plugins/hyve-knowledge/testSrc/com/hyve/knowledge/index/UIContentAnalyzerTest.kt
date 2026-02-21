// Copyright 2026 Hyve. All rights reserved.
package com.hyve.knowledge.index

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class UIContentAnalyzerTest {

    private val analyzer = UIContentAnalyzer()

    // ── Resource path strategy ────────────────────────────────

    @Test
    fun `resource_path extracts stem from crafting screen icon path`() {
        val content = """{
            "type": "CraftingPanel",
            "icon": "Icons/Crafting/Workbench",
            "background": "UI/Panels/CraftingBg"
        }"""
        val candidates = analyzer.analyze(content, "ui:InGame/CraftingScreen.ui")
        val resourcePaths = candidates.filter { it.strategy == "resource_path" }

        assertTrue(resourcePaths.any { it.candidateText == "Workbench" }, "Should extract Workbench from asset path")
        assertTrue(resourcePaths.any { it.candidateText == "CraftingBg" }, "Should extract CraftingBg (won't resolve, but is a candidate)")
        assertTrue(resourcePaths.all { it.confidence == 0.8f })
    }

    @Test
    fun `resource_path handles deeply nested asset paths`() {
        val content = """{ "portrait": "Icons/NPCs/Hostile/Trork_Warrior" }"""
        val candidates = analyzer.analyze(content, "ui:InGame/NpcDialog.ui")
        val resourcePaths = candidates.filter { it.strategy == "resource_path" }

        assertTrue(resourcePaths.any { it.candidateText == "Trork_Warrior" })
    }

    @Test
    fun `resource_path matches single-quoted paths`() {
        val content = "icon = 'Items/Sword_Iron'"
        val candidates = analyzer.analyze(content, "ui:InGame/EquipmentSlot.ui")
        val resourcePaths = candidates.filter { it.strategy == "resource_path" }

        assertTrue(resourcePaths.any { it.candidateText == "Sword_Iron" })
    }

    // ── PascalCase strategy ───────────────────────────────────

    @Test
    fun `pascal_case matches compound identifiers in quest tracker template`() {
        val content = "Binding: Track active objectives for TrorkWarrior encounters near KweebecVillage"
        val candidates = analyzer.analyze(content, "ui:InGame/QuestTracker.ui")
        val pascalCases = candidates.filter { it.strategy == "pascal_case" }

        assertTrue(pascalCases.any { it.candidateText == "TrorkWarrior" })
        assertTrue(pascalCases.any { it.candidateText == "KweebecVillage" })
        assertTrue(pascalCases.all { it.confidence == 0.5f })
    }

    @Test
    fun `pascal_case excludes known UI framework terms`() {
        val content = "Uses DataTemplate and StackPanel with ScrollViewer layout"
        val candidates = analyzer.analyze(content, "ui:InGame/GenericList.ui")
        val pascalCases = candidates.filter { it.strategy == "pascal_case" }

        assertFalse(pascalCases.any { it.candidateText == "DataTemplate" })
        assertFalse(pascalCases.any { it.candidateText == "StackPanel" })
        assertFalse(pascalCases.any { it.candidateText == "ScrollViewer" })
    }

    @Test
    fun `pascal_case deduplicates repeated identifiers`() {
        val content = "TrorkWarrior health bar. TrorkWarrior name label. TrorkWarrior portrait."
        val candidates = analyzer.analyze(content, "ui:InGame/EnemyInfo.ui")
        val pascalCases = candidates.filter { it.strategy == "pascal_case" }

        assertEquals(1, pascalCases.size)
    }

    @Test
    fun `pascal_case requires at least two words — single PascalCase word ignored`() {
        val content = "Sword Torch Resin Gold"
        val candidates = analyzer.analyze(content, "ui:InGame/Tooltip.ui")
        val pascalCases = candidates.filter { it.strategy == "pascal_case" }

        assertTrue(pascalCases.isEmpty())
    }

    // ── Filename stem strategy ────────────────────────────────

    @Test
    fun `filename_stem extracts UI screen name from nodeId`() {
        val candidates = analyzer.analyze("some content", "ui:InGame/CraftingScreen.ui")
        val stems = candidates.filter { it.strategy == "filename_stem" }

        assertEquals(1, stems.size)
        assertEquals("CraftingScreen", stems[0].candidateText)
        assertEquals(0.4f, stems[0].confidence)
    }

    @Test
    fun `filename_stem handles nodeId without subdirectory`() {
        val candidates = analyzer.analyze("some content", "ui:MainMenu.ui")
        val stems = candidates.filter { it.strategy == "filename_stem" }

        assertEquals(1, stems.size)
        assertEquals("MainMenu", stems[0].candidateText)
    }

    // ── JSON key reference strategy ───────────────────────────

    @Test
    fun `json_key matches shop panel data bindings`() {
        val content = """{
            "type": "ShopGrid",
            "item": "Gold_Coin",
            "npc": "NPC_Blacksmith"
        }"""
        val candidates = analyzer.analyze(content, "ui:InGame/ShopPanel.ui")
        val jsonKeys = candidates.filter { it.strategy == "json_key" }

        assertTrue(jsonKeys.any { it.candidateText == "Gold_Coin" })
        assertTrue(jsonKeys.any { it.candidateText == "NPC_Blacksmith" })
        assertTrue(jsonKeys.all { it.confidence == 0.6f })
    }

    @Test
    fun `json_key matches various gamedata key types`() {
        val content = """{ "block": "Block_Stone", "entity": "NPC_Merchant", "weapon": "Sword_Iron" }"""
        val candidates = analyzer.analyze(content, "ui:InGame/InteractionPanel.ui")
        val jsonKeys = candidates.filter { it.strategy == "json_key" }

        assertTrue(jsonKeys.any { it.candidateText == "Block_Stone" })
        assertTrue(jsonKeys.any { it.candidateText == "NPC_Merchant" })
        assertTrue(jsonKeys.any { it.candidateText == "Sword_Iron" })
    }

    @Test
    fun `json_key does not match non-gamedata keys`() {
        // "benchIcon" is not in the key whitelist, so this should not trigger json_key
        val content = """{ "benchIcon": "Icons/Crafting/Workbench", "color": "#FF0000" }"""
        val candidates = analyzer.analyze(content, "ui:InGame/CraftingScreen.ui")
        val jsonKeys = candidates.filter { it.strategy == "json_key" }

        assertTrue(jsonKeys.isEmpty(), "Non-whitelisted keys should not produce json_key candidates")
    }

    // ── All strategies combined ───────────────────────────────

    @Test
    fun `analyze returns candidates from all four strategies`() {
        // resource_path: "Icons/Items/Sword_Iron" → Sword_Iron
        // json_key: "item": "Gold_Coin" → Gold_Coin
        // pascal_case: TrorkWarrior → TrorkWarrior
        // filename_stem: InventoryGrid → InventoryGrid
        val content = """
            { "item": "Gold_Coin", "icon": "Icons/Items/Sword_Iron" }
            Display TrorkWarrior loot drops here.
        """.trimIndent()
        val candidates = analyzer.analyze(content, "ui:InGame/InventoryGrid.ui")
        val strategies = candidates.map { it.strategy }.toSet()

        assertTrue("resource_path" in strategies)
        assertTrue("pascal_case" in strategies)
        assertTrue("filename_stem" in strategies)
        assertTrue("json_key" in strategies)
    }

    @Test
    fun `all candidates carry the correct clientNodeId`() {
        val nodeId = "ui:InGame/ShopPanel.ui"
        val candidates = analyzer.analyze("""{ "item": "Gold_Coin" }""", nodeId)

        assertTrue(candidates.isNotEmpty())
        assertTrue(candidates.all { it.clientNodeId == nodeId })
    }

    @Test
    fun `empty content returns only filename_stem candidate`() {
        val candidates = analyzer.analyze("", "ui:InGame/HealthBar.ui")

        assertEquals(1, candidates.size)
        assertEquals("filename_stem", candidates[0].strategy)
        assertEquals("HealthBar", candidates[0].candidateText)
    }
}
