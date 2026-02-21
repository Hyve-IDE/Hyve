// Copyright 2026 Hyve. All rights reserved.
package com.hyve.knowledge.index

import com.hyve.knowledge.core.extraction.GameDataChunk
import com.hyve.knowledge.core.extraction.GameDataType
import kotlinx.serialization.json.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

/**
 * Tests the typed edge extraction functions in GameDataIndexerTask.
 * Uses a mock stem lookup to verify edge creation without a database.
 */
class GameDataEdgeExtractionTest {

    // Instantiate via a helper that doesn't require a real Project
    private val task = createTestTask()

    /** Stem lookup: lowercase name → list of node IDs. */
    private val stemLookup = mapOf(
        "ingredient_bar_copper" to listOf("gamedata:Server:Item:Items:Ingredient_Bar_Copper.json"),
        "resource_wood_stick" to listOf("gamedata:Server:Item:Items:Resource_Wood_Stick.json"),
        "torch" to listOf("gamedata:Server:Item:Items:Torch.json"),
        "sword_iron" to listOf("gamedata:Server:Item:Items:Sword_Iron.json"),
        "drop_goblin_warrior" to listOf("gamedata:Server:Drops:Drop_Goblin_Warrior.json"),
        "gold_coin" to listOf("gamedata:Server:Item:Items:Gold_Coin.json"),
        "npc_goblin_warrior" to listOf("gamedata:Server:NPC:Roles:NPC_Goblin_Warrior.json"),
        "npc_goblin_archer" to listOf("gamedata:Server:NPC:Roles:NPC_Goblin_Archer.json"),
        // Multi-match scenario
        "goblinraidingparty" to listOf("gamedata:Server:NPC:Groups:GoblinRaidingParty.json"),
        "monstergroup_a" to listOf("gamedata:Server:NPC:Groups:MonsterGroup_A.json"),
        "workbench" to listOf("gamedata:Server:Crafting:Bench_Workbench.json"),
        "entity_wolf" to listOf("gamedata:Server:Entity:Entity_Wolf.json"),
        "block_stone" to listOf("gamedata:Server:Block:Block_Stone.json"),
        "drop_wolf" to listOf("gamedata:Server:Drops:Drop_Wolf.json"),
        "default" to listOf("gamedata:Server:NPC:Roles:Default.json", "gamedata:Server:Camera:Default.json"),
    )

    private fun chunk(id: String, type: GameDataType, name: String = "Test"): GameDataChunk {
        return GameDataChunk(
            id = id, type = type, name = name, filePath = "test.json",
            fileHash = "hash", rawJson = "{}", tags = emptyList(),
            relatedIds = emptyList(), textForEmbedding = "",
        )
    }

    // ── extractItemEdges ──────────────────────────────────────

    @Test
    fun `extractItemEdges creates REQUIRES_ITEM from inline recipe`() {
        val json = buildJsonObject {
            putJsonObject("Recipe") {
                putJsonArray("Input") {
                    addJsonObject { put("ItemId", "Ingredient_Bar_Copper"); put("Quantity", 3) }
                    addJsonObject { put("ItemId", "Resource_Wood_Stick"); put("Quantity", 1) }
                }
            }
        }

        val c = chunk("gamedata:Server:Item:Items:Torch.json", GameDataType.ITEM)
        val edges = task.extractItemEdges(c, json, stemLookup)

        assertEquals(2, edges.size, "Should create 2 REQUIRES_ITEM edges")
        assertTrue(edges.all { it.edgeType == "REQUIRES_ITEM" })
        assertTrue(edges.any { it.targetId.contains("Ingredient_Bar_Copper") })
        assertTrue(edges.any { it.targetId.contains("Resource_Wood_Stick") })
    }

    @Test
    fun `extractItemEdges creates virtual reference for ResourceTypeId`() {
        val json = buildJsonObject {
            putJsonObject("Recipe") {
                putJsonArray("Input") {
                    addJsonObject { put("ResourceTypeId", "Wood_Trunk") }
                }
            }
        }

        val c = chunk("gamedata:test:item", GameDataType.ITEM)
        val edges = task.extractItemEdges(c, json, stemLookup)

        assertEquals(1, edges.size)
        assertEquals("virtual:resource:Wood_Trunk", edges[0].targetId)
        assertFalse(edges[0].targetResolved, "Virtual reference should be unresolved")
    }

    @Test
    fun `extractItemEdges returns empty for items without recipe`() {
        val json = buildJsonObject { put("Quality", "Common") }
        val c = chunk("gamedata:test:item", GameDataType.ITEM)
        val edges = task.extractItemEdges(c, json, stemLookup)
        assertTrue(edges.isEmpty())
    }

    // ── extractRecipeEdges ────────────────────────────────────

    @Test
    fun `extractRecipeEdges creates REQUIRES_ITEM and PRODUCES_ITEM`() {
        val json = buildJsonObject {
            putJsonArray("Input") {
                addJsonObject { put("ItemId", "Ingredient_Bar_Copper"); put("Quantity", 5) }
            }
            putJsonObject("PrimaryOutput") {
                put("ItemId", "Sword_Iron"); put("Quantity", 1)
            }
        }

        val c = chunk("gamedata:Server:Item:Recipes:Recipe_Sword_Iron.json", GameDataType.RECIPE)
        val edges = task.extractRecipeEdges(c, json, stemLookup)

        val requires = edges.filter { it.edgeType == "REQUIRES_ITEM" }
        val produces = edges.filter { it.edgeType == "PRODUCES_ITEM" }

        assertEquals(1, requires.size, "Should have 1 REQUIRES_ITEM")
        assertEquals(1, produces.size, "Should have 1 PRODUCES_ITEM")
        assertTrue(produces[0].metadata?.contains("primary") == true, "Primary output should have role metadata")
    }

    // ── extractDropEdges ──────────────────────────────────────

    @Test
    fun `extractDropEdges extracts from Container hierarchy`() {
        val json = buildJsonObject {
            putJsonObject("Container") {
                putJsonArray("Containers") {
                    addJsonObject {
                        putJsonObject("Item") {
                            put("ItemId", "Gold_Coin")
                            put("QuantityMin", 1)
                            put("QuantityMax", 5)
                        }
                    }
                    addJsonObject {
                        putJsonObject("Item") {
                            put("ItemId", "Sword_Iron")
                        }
                    }
                }
            }
        }

        val c = chunk("gamedata:Server:Drops:Drop_Goblin_Warrior.json", GameDataType.DROP)
        val edges = task.extractDropEdges(c, json, stemLookup)

        assertEquals(2, edges.size)
        assertTrue(edges.all { it.edgeType == "DROPS_ITEM" })
    }

    // ── extractNpcEdges ───────────────────────────────────────

    @Test
    fun `extractNpcEdges creates DROPS_ON_DEATH from DropList`() {
        val json = buildJsonObject {
            putJsonObject("Modify") {
                put("DropList", "Drop_Goblin_Warrior")
            }
        }

        val c = chunk("gamedata:Server:NPC:Roles:NPC_Goblin_Warrior.json", GameDataType.NPC)
        val edges = task.extractNpcEdges(c, json, stemLookup)

        assertEquals(1, edges.size)
        assertEquals("DROPS_ON_DEATH", edges[0].edgeType)
        assertTrue(edges[0].targetId.contains("Drop_Goblin_Warrior"))
    }

    // ── extractShopEdges ──────────────────────────────────────

    @Test
    fun `extractShopEdges extracts from TradeSlots`() {
        val json = buildJsonObject {
            putJsonArray("TradeSlots") {
                addJsonObject {
                    putJsonObject("Trade") {
                        putJsonObject("Output") { put("ItemId", "Sword_Iron") }
                    }
                }
                addJsonObject {
                    putJsonArray("Trades") {
                        addJsonObject {
                            putJsonObject("Output") { put("ItemId", "Gold_Coin") }
                        }
                    }
                }
            }
        }

        val c = chunk("gamedata:Server:BarterShops:Shop_Blacksmith.json", GameDataType.SHOP)
        val edges = task.extractShopEdges(c, json, stemLookup)

        assertEquals(2, edges.size)
        assertTrue(edges.all { it.edgeType == "OFFERED_IN_SHOP" })
    }

    // ── extractGroupEdges ─────────────────────────────────────

    @Test
    fun `extractGroupEdges creates HAS_MEMBER and BELONGS_TO_GROUP`() {
        val json = buildJsonObject {
            putJsonArray("Members") {
                addJsonObject { put("NPC", "NPC_Goblin_Warrior"); put("Count", 3) }
                addJsonObject { put("NPC", "NPC_Goblin_Archer"); put("Count", 2) }
            }
        }

        val c = chunk("gamedata:Server:NPC:Groups:GoblinRaidingParty.json", GameDataType.NPC_GROUP)
        val edges = task.extractGroupEdges(c, json, stemLookup)

        val hasMember = edges.filter { it.edgeType == "HAS_MEMBER" }
        val belongsTo = edges.filter { it.edgeType == "BELONGS_TO_GROUP" }

        assertEquals(2, hasMember.size, "Should have 2 HAS_MEMBER edges")
        assertEquals(2, belongsTo.size, "Should have 2 BELONGS_TO_GROUP edges (reverse)")

        // Verify bidirectional: HAS_MEMBER source=group, BELONGS_TO_GROUP source=npc
        assertTrue(hasMember.all { it.sourceId.contains("GoblinRaidingParty") })
        assertTrue(belongsTo.all { it.targetId.contains("GoblinRaidingParty") })
    }

    // ── Multi-match + self-reference ──────────────────────────

    @Test
    fun `resolveStem skips self-references`() {
        val json = buildJsonObject {
            putJsonObject("Recipe") {
                putJsonArray("Input") {
                    addJsonObject { put("ItemId", "Torch"); put("Quantity", 1) }
                }
            }
        }

        // Source ID is the same as the Torch node — should not create self-edge
        val c = chunk("gamedata:Server:Item:Items:Torch.json", GameDataType.ITEM)
        val edges = task.extractItemEdges(c, json, stemLookup)
        assertTrue(edges.isEmpty(), "Should skip self-reference edge")
    }

    @Test
    fun `resolveStem flags multi-match with metadata`() {
        val json = buildJsonObject {
            putJsonObject("Modify") {
                put("DropList", "Default")
            }
        }

        val c = chunk("gamedata:test:npc", GameDataType.NPC)
        val edges = task.extractNpcEdges(c, json, stemLookup)

        assertEquals(2, edges.size, "Should create edges to both Default matches")
        assertTrue(edges.all { it.metadata?.contains("multi_match") == true }, "Should flag multi-match")
    }

    @Test
    fun `extractItemEdges creates REQUIRES_BENCH virtual edge from string BenchRequirement`() {
        val json = buildJsonObject {
            putJsonObject("Recipe") {
                put("BenchRequirement", "Workbench")
                putJsonArray("Input") {
                    addJsonObject { put("ItemId", "Wood_Log"); put("Quantity", 2) }
                }
            }
        }
        val c = chunk("gamedata:test:item", GameDataType.ITEM)
        val edges = task.extractItemEdges(c, json, stemLookup)
        val benchEdges = edges.filter { it.edgeType == "REQUIRES_BENCH" }
        assertEquals(1, benchEdges.size, "Should have 1 REQUIRES_BENCH edge")
        assertEquals("virtual:bench:Workbench", benchEdges[0].targetId)
        assertFalse(benchEdges[0].targetResolved, "Virtual bench reference should be unresolved")
    }

    @Test
    fun `extractRecipeEdges creates REQUIRES_BENCH from standalone recipe`() {
        val json = buildJsonObject {
            putJsonArray("Input") {
                addJsonObject { put("ItemId", "Ingredient_Bar_Copper"); put("Quantity", 5) }
            }
            put("BenchRequirement", "Forge")
            putJsonObject("PrimaryOutput") { put("ItemId", "Sword_Iron"); put("Quantity", 1) }
        }
        val c = chunk("gamedata:Server:Recipes:Recipe_Sword.json", GameDataType.RECIPE)
        val edges = task.extractRecipeEdges(c, json, stemLookup)
        val benchEdges = edges.filter { it.edgeType == "REQUIRES_BENCH" }
        assertEquals(1, benchEdges.size, "Should have 1 REQUIRES_BENCH edge")
        assertEquals("virtual:bench:Forge", benchEdges[0].targetId)
    }

    @Test
    fun `extractNpcEdges creates TARGETS_GROUP from TargetGroups array`() {
        val json = buildJsonObject {
            putJsonArray("TargetGroups") {
                add("GoblinRaidingParty")
            }
            putJsonObject("Modify") {
                put("DropList", "Drop_Goblin_Warrior")
            }
        }
        val c = chunk("gamedata:Server:NPC:Roles:NPC_Goblin_Warrior.json", GameDataType.NPC)
        val edges = task.extractNpcEdges(c, json, stemLookup)
        val targetGroupEdges = edges.filter { it.edgeType == "TARGETS_GROUP" }
        assertEquals(1, targetGroupEdges.size, "Should have 1 TARGETS_GROUP edge")
        assertTrue(targetGroupEdges[0].targetId.contains("GoblinRaidingParty"))
    }

    @Test
    fun `extractObjectiveEdges creates TARGETS_GROUP from NPCGroupId in tasks`() {
        val json = buildJsonObject {
            putJsonArray("TaskSets") {
                addJsonObject {
                    putJsonArray("Tasks") {
                        addJsonObject {
                            put("Type", "KillSpawnMarker")
                            put("NPCGroupId", "GoblinRaidingParty")
                            put("Count", 3)
                        }
                    }
                }
            }
        }
        val c = chunk("gamedata:test:objective", GameDataType.OBJECTIVE)
        val edges = task.extractObjectiveEdges(c, json, stemLookup)
        assertEquals(1, edges.size, "Should have 1 TARGETS_GROUP edge")
        assertEquals("TARGETS_GROUP", edges[0].edgeType)
        assertTrue(edges[0].targetId.contains("GoblinRaidingParty"))
    }

    @Test
    fun `extractEntityEdges creates DROPS_ON_DEATH from Drops string`() {
        val json = buildJsonObject { put("Drops", "Drop_Wolf") }
        val c = chunk("gamedata:Server:Entity:Entity_Wolf.json", GameDataType.ENTITY)
        val edges = task.extractEntityEdges(c, json, stemLookup)
        assertEquals(1, edges.size, "Should have 1 DROPS_ON_DEATH edge")
        assertEquals("DROPS_ON_DEATH", edges[0].edgeType)
        assertTrue(edges[0].targetId.contains("Drop_Wolf"))
    }

    @Test
    fun `extractBlockEdges creates DROPS_ON_DEATH from lootTable string`() {
        val json = buildJsonObject { put("lootTable", "Drop_Wolf") }
        val c = chunk("gamedata:Server:Block:Block_Stone.json", GameDataType.BLOCK)
        val edges = task.extractBlockEdges(c, json, stemLookup)
        assertEquals(1, edges.size, "Should have 1 DROPS_ON_DEATH edge")
        assertEquals("DROPS_ON_DEATH", edges[0].edgeType)
    }

    @Test
    fun `extractFarmingEdges creates TARGETS_GROUP from AcceptedNpcGroups`() {
        val json = buildJsonObject {
            putJsonArray("AcceptedNpcGroups") {
                add("MonsterGroup_A")
            }
        }
        val c = chunk("gamedata:test:farming", GameDataType.FARMING)
        val edges = task.extractFarmingEdges(c, json, stemLookup)
        assertEquals(1, edges.size, "Should have 1 TARGETS_GROUP edge")
        assertEquals("TARGETS_GROUP", edges[0].edgeType)
        assertTrue(edges[0].targetId.contains("MonsterGroup_A"))
    }

    // ── SPAWNS_PARTICLE edges ────────────────────────────────

    @Test
    fun `extractProjectileEdges creates SPAWNS_PARTICLE from HitParticles and DeathParticles`() {
        val json = buildJsonObject {
            putJsonObject("HitParticles") { put("SystemId", "Arrow_Hit") }
            putJsonObject("DeathParticles") { put("SystemId", "Arrow_Death") }
        }
        val c = chunk("gamedata:test:projectile", GameDataType.PROJECTILE)
        val edges = task.extractProjectileEdges(c, json)
        assertEquals(2, edges.size, "Should create 2 SPAWNS_PARTICLE edges")
        assertTrue(edges.all { it.edgeType == "SPAWNS_PARTICLE" })
        assertTrue(edges.any { it.targetId == "virtual:particle:Arrow_Hit" && it.metadata?.contains("hit") == true })
        assertTrue(edges.any { it.targetId == "virtual:particle:Arrow_Death" && it.metadata?.contains("death") == true })
        assertTrue(edges.all { !it.targetResolved }, "Particle edges should be unresolved")
    }

    @Test
    fun `extractWeatherEdges creates SPAWNS_PARTICLE from Particle SystemId`() {
        val json = buildJsonObject {
            putJsonObject("Particle") { put("SystemId", "Rain_Drops") }
        }
        val c = chunk("gamedata:test:weather", GameDataType.WEATHER)
        val edges = task.extractWeatherEdges(c, json)
        assertEquals(1, edges.size)
        assertEquals("SPAWNS_PARTICLE", edges[0].edgeType)
        assertEquals("virtual:particle:Rain_Drops", edges[0].targetId)
        assertTrue(edges[0].metadata?.contains("ambient") == true)
        assertFalse(edges[0].targetResolved)
    }

    @Test
    fun `extractBlockEdges creates SPAWNS_PARTICLE from Particles events`() {
        val json = buildJsonObject {
            putJsonObject("Particles") {
                putJsonObject("OnBreak") { put("SystemId", "Block_Break_Dust") }
                putJsonObject("OnPlace") { put("SystemId", "Block_Place_Puff") }
            }
        }
        val c = chunk("gamedata:test:block", GameDataType.BLOCK)
        val edges = task.extractBlockEdges(c, json, stemLookup)
        val particleEdges = edges.filter { it.edgeType == "SPAWNS_PARTICLE" }
        assertEquals(2, particleEdges.size, "Should create SPAWNS_PARTICLE for each event")
        assertTrue(particleEdges.any { it.targetId == "virtual:particle:Block_Break_Dust" && it.metadata?.contains("OnBreak") == true })
        assertTrue(particleEdges.any { it.targetId == "virtual:particle:Block_Place_Puff" && it.metadata?.contains("OnPlace") == true })
    }

    @Test
    fun `extractEntityEdges creates SPAWNS_PARTICLE from ApplicationEffects Particles`() {
        val json = buildJsonObject {
            putJsonObject("ApplicationEffects") {
                putJsonArray("Particles") {
                    addJsonObject { put("SystemId", "Wolf_Howl_Effect") }
                }
            }
        }
        val c = chunk("gamedata:test:entity", GameDataType.ENTITY)
        val edges = task.extractEntityEdges(c, json, stemLookup)
        val particleEdges = edges.filter { it.edgeType == "SPAWNS_PARTICLE" }
        assertEquals(1, particleEdges.size)
        assertEquals("virtual:particle:Wolf_Howl_Effect", particleEdges[0].targetId)
        assertFalse(particleEdges[0].targetResolved)
    }

    @Test
    fun `extractNpcEdges creates SPAWNS_PARTICLE from ApplicationEffects Particles`() {
        val json = buildJsonObject {
            putJsonObject("ApplicationEffects") {
                putJsonArray("Particles") {
                    addJsonObject { put("SystemId", "NPC_Rage_Aura") }
                }
            }
        }
        val c = chunk("gamedata:test:npc", GameDataType.NPC)
        val edges = task.extractNpcEdges(c, json, stemLookup)
        val particleEdges = edges.filter { it.edgeType == "SPAWNS_PARTICLE" }
        assertEquals(1, particleEdges.size)
        assertEquals("virtual:particle:NPC_Rage_Aura", particleEdges[0].targetId)
    }

    @Test
    fun `extractItemEdges creates SPAWNS_PARTICLE from BlockType Particles`() {
        val json = buildJsonObject {
            putJsonObject("BlockType") {
                putJsonArray("Particles") {
                    addJsonObject { put("SystemId", "Torch_Flame") }
                }
            }
        }
        val c = chunk("gamedata:test:item", GameDataType.ITEM)
        val edges = task.extractItemEdges(c, json, stemLookup)
        val particleEdges = edges.filter { it.edgeType == "SPAWNS_PARTICLE" }
        assertEquals(1, particleEdges.size)
        assertEquals("virtual:particle:Torch_Flame", particleEdges[0].targetId)
        assertTrue(particleEdges[0].metadata?.contains("block_state") == true)
    }

    // ── Recipe edge variants ─────────────────────────────────

    @Test
    fun `extractRecipeEdges creates secondary PRODUCES_ITEM from Output array`() {
        val json = buildJsonObject {
            putJsonArray("Input") {
                addJsonObject { put("ItemId", "Ingredient_Bar_Copper"); put("Quantity", 1) }
            }
            putJsonObject("PrimaryOutput") { put("ItemId", "Sword_Iron"); put("Quantity", 1) }
            putJsonArray("Output") {
                addJsonObject { put("ItemId", "Gold_Coin"); put("Quantity", 2) }
            }
        }
        val c = chunk("gamedata:test:recipe", GameDataType.RECIPE)
        val edges = task.extractRecipeEdges(c, json, stemLookup)
        val secondary = edges.filter { it.edgeType == "PRODUCES_ITEM" && it.metadata?.contains("secondary") == true }
        assertEquals(1, secondary.size, "Should have 1 secondary PRODUCES_ITEM")
        assertTrue(secondary[0].targetId.contains("Gold_Coin"))
    }

    @Test
    fun `extractRecipeEdges creates PRODUCES_ITEM from legacy result field`() {
        val json = buildJsonObject {
            putJsonArray("Input") {
                addJsonObject { put("ItemId", "Ingredient_Bar_Copper"); put("Quantity", 2) }
            }
            putJsonObject("result") { put("ItemId", "Torch"); put("Quantity", 4) }
        }
        val c = chunk("gamedata:test:recipe", GameDataType.RECIPE)
        val edges = task.extractRecipeEdges(c, json, stemLookup)
        val produces = edges.filter { it.edgeType == "PRODUCES_ITEM" }
        assertEquals(1, produces.size, "Should create PRODUCES_ITEM from legacy result")
        assertTrue(produces[0].targetId.contains("Torch"))
    }

    @Test
    fun `extractRecipeEdges creates REQUIRES_ITEM with ResourceTypeId virtual ref`() {
        val json = buildJsonObject {
            putJsonArray("Input") {
                addJsonObject { put("ResourceTypeId", "Metal_Ingot") }
            }
            putJsonObject("PrimaryOutput") { put("ItemId", "Sword_Iron"); put("Quantity", 1) }
        }
        val c = chunk("gamedata:test:recipe", GameDataType.RECIPE)
        val edges = task.extractRecipeEdges(c, json, stemLookup)
        val requires = edges.filter { it.edgeType == "REQUIRES_ITEM" }
        assertEquals(1, requires.size)
        assertEquals("virtual:resource:Metal_Ingot", requires[0].targetId)
        assertFalse(requires[0].targetResolved)
    }

    // ── Drop edge variants ───────────────────────────────────

    @Test
    fun `extractDropEdges extracts from flat Drops array fallback`() {
        val json = buildJsonObject {
            putJsonArray("Drops") {
                addJsonObject { put("ItemId", "Gold_Coin") }
                addJsonObject { put("ItemId", "Sword_Iron") }
            }
        }
        val c = chunk("gamedata:test:drop", GameDataType.DROP)
        val edges = task.extractDropEdges(c, json, stemLookup)
        assertEquals(2, edges.size, "Should create 2 DROPS_ITEM edges from flat array")
        assertTrue(edges.all { it.edgeType == "DROPS_ITEM" })
        assertTrue(edges.any { it.targetId.contains("Gold_Coin") })
        assertTrue(edges.any { it.targetId.contains("Sword_Iron") })
    }

    // ── NPC edge variants ────────────────────────────────────

    @Test
    fun `extractNpcEdges creates DROPS_ON_DEATH from root DropList`() {
        val json = buildJsonObject {
            put("DropList", "Drop_Goblin_Warrior")
        }
        val c = chunk("gamedata:test:npc", GameDataType.NPC)
        val edges = task.extractNpcEdges(c, json, stemLookup)
        val dropEdges = edges.filter { it.edgeType == "DROPS_ON_DEATH" }
        assertEquals(1, dropEdges.size, "Should create DROPS_ON_DEATH from root DropList")
        assertTrue(dropEdges[0].targetId.contains("Drop_Goblin_Warrior"))
    }

    @Test
    fun `extractNpcEdges creates DROPS_ON_DEATH from Drops string field`() {
        val json = buildJsonObject {
            putJsonObject("Modify") {
                put("Drops", "Drop_Wolf")
            }
        }
        val c = chunk("gamedata:test:npc", GameDataType.NPC)
        val edges = task.extractNpcEdges(c, json, stemLookup)
        val dropEdges = edges.filter { it.edgeType == "DROPS_ON_DEATH" }
        assertEquals(1, dropEdges.size, "Should create DROPS_ON_DEATH from Drops string")
        assertTrue(dropEdges[0].targetId.contains("Drop_Wolf"))
    }

    // ── Group edge variants ──────────────────────────────────

    @Test
    fun `extractGroupEdges handles NPCs array fallback format`() {
        val json = buildJsonObject {
            putJsonArray("NPCs") {
                addJsonObject { put("Id", "NPC_Goblin_Warrior") }
                addJsonObject { put("Id", "NPC_Goblin_Archer") }
            }
        }
        val c = chunk("gamedata:Server:NPC:Groups:GoblinRaidingParty.json", GameDataType.NPC_GROUP)
        val edges = task.extractGroupEdges(c, json, stemLookup)
        val hasMember = edges.filter { it.edgeType == "HAS_MEMBER" }
        val belongsTo = edges.filter { it.edgeType == "BELONGS_TO_GROUP" }
        assertEquals(2, hasMember.size, "Should find 2 members from NPCs[] fallback")
        assertEquals(2, belongsTo.size, "Should create reverse edges too")
    }

    // ── APPLIES_EFFECT edges (interaction) ───────────────────

    @Test
    fun `extractInteractionEdges creates APPLIES_EFFECT from Effects array`() {
        val json = buildJsonObject {
            putJsonArray("Effects") {
                addJsonObject { put("EffectId", "Poison_Slow") }
                addJsonObject { put("EffectId", "Damage_Over_Time") }
            }
        }
        val c = chunk("gamedata:test:interaction", GameDataType.INTERACTION)
        val edges = task.extractInteractionEdges(c, json)
        assertEquals(2, edges.size, "Should create 2 APPLIES_EFFECT edges")
        assertTrue(edges.all { it.edgeType == "APPLIES_EFFECT" })
        assertTrue(edges.any { it.targetId == "virtual:effect:Poison_Slow" })
        assertTrue(edges.any { it.targetId == "virtual:effect:Damage_Over_Time" })
        assertTrue(edges.all { !it.targetResolved })
    }

    @Test
    fun `extractInteractionEdges creates APPLIES_EFFECT from ApplyEffect action type`() {
        val json = buildJsonObject {
            put("Action", "ApplyEffect")
            put("EffectId", "Heal_Burst")
        }
        val c = chunk("gamedata:test:interaction", GameDataType.INTERACTION)
        val edges = task.extractInteractionEdges(c, json)
        assertEquals(1, edges.size)
        assertEquals("APPLIES_EFFECT", edges[0].edgeType)
        assertEquals("virtual:effect:Heal_Burst", edges[0].targetId)
    }

    // ── REFERENCES_WORLDGEN edges (zone) ─────────────────────

    @Test
    fun `extractZoneEdges creates REFERENCES_WORLDGEN from NoiseMask File`() {
        val json = buildJsonObject {
            putJsonObject("NoiseMask") { put("File", "Worldgen/ContinentMask.json") }
        }
        val c = chunk("gamedata:test:zone", GameDataType.ZONE)
        val edges = task.extractZoneEdges(c, json, stemLookup)
        assertEquals(1, edges.size)
        assertEquals("REFERENCES_WORLDGEN", edges[0].edgeType)
        assertEquals("virtual:worldgen:Worldgen/ContinentMask.json", edges[0].targetId)
        assertFalse(edges[0].targetResolved)
    }

    // ── Helper to create task without a real IntelliJ Project ──

    companion object {
        private fun createTestTask(): GameDataIndexerTask {
            // Allocate instance without calling the constructor to avoid Kotlin's
            // non-null check on the Project parameter. The extraction methods under
            // test don't use the project field.
            val factory = sun.reflect.ReflectionFactory.getReflectionFactory()
            val objCtor = Any::class.java.getDeclaredConstructor()
            val ctor = factory.newConstructorForSerialization(GameDataIndexerTask::class.java, objCtor)
            return ctor.newInstance() as GameDataIndexerTask
        }
    }
}
