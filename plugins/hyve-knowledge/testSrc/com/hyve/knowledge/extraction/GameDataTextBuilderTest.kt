// Copyright 2026 Hyve. All rights reserved.
package com.hyve.knowledge.extraction

import com.hyve.knowledge.core.extraction.GameDataChunk
import com.hyve.knowledge.core.extraction.GameDataType
import kotlinx.serialization.json.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class GameDataTextBuilderTest {

    @Test
    fun `buildItemText includes inline recipe when present`() {
        val json = buildJsonObject {
            put("Quality", "Rare")
            putJsonObject("Recipe") {
                putJsonArray("Input") {
                    addJsonObject {
                        put("ItemId", "Ingredient_Bar_Copper")
                        put("Quantity", 3)
                    }
                    addJsonObject {
                        put("ItemId", "Resource_Wood_Stick")
                        put("Quantity", 1)
                    }
                }
                put("TimeSeconds", 10)
            }
        }

        val chunk = GameDataChunk(
            id = "gamedata:Server:Item:Items:Torch.json",
            type = GameDataType.ITEM,
            name = "Torch",
            filePath = "Server/Item/Items/Torch.json",
            fileHash = "abc",
            rawJson = json.toString(),
            tags = listOf("item"),
            relatedIds = emptyList(),
            textForEmbedding = "",
        )

        val text = GameDataTextBuilder.buildText(chunk, json)
        assertTrue(text.contains("Crafting recipe:"), "Should include crafting recipe header")
        assertTrue(text.contains("Ingredient_Bar_Copper x3"), "Should list first ingredient")
        assertTrue(text.contains("Resource_Wood_Stick x1"), "Should list second ingredient")
        assertTrue(text.contains("Crafting time: 10s"), "Should include crafting time")
        assertTrue(text.contains("crafting recipe ingredients"), "Should include keywords")
    }

    @Test
    fun `buildItemText omits recipe section when no Recipe field`() {
        val json = buildJsonObject {
            put("Quality", "Common")
            put("MaxStackSize", 64)
        }

        val chunk = GameDataChunk(
            id = "gamedata:Server:Item:Items:Stone.json",
            type = GameDataType.ITEM,
            name = "Stone",
            filePath = "Server/Item/Items/Stone.json",
            fileHash = "def",
            rawJson = json.toString(),
            tags = listOf("item"),
            relatedIds = emptyList(),
            textForEmbedding = "",
        )

        val text = GameDataTextBuilder.buildText(chunk, json)
        assertFalse(text.contains("Crafting recipe:"), "Should not include recipe section")
        assertFalse(text.contains("Ingredient:"), "Should not include ingredients")
    }

    @Test
    fun `buildItemText caps ingredients at 5`() {
        val json = buildJsonObject {
            putJsonObject("Recipe") {
                putJsonArray("Input") {
                    repeat(8) { i ->
                        addJsonObject {
                            put("ItemId", "Item_$i")
                            put("Quantity", 1)
                        }
                    }
                }
            }
        }

        val chunk = GameDataChunk(
            id = "gamedata:Server:Item:Items:Complex.json",
            type = GameDataType.ITEM,
            name = "Complex",
            filePath = "Server/Item/Items/Complex.json",
            fileHash = "ghi",
            rawJson = json.toString(),
            tags = listOf("item"),
            relatedIds = emptyList(),
            textForEmbedding = "",
        )

        val text = GameDataTextBuilder.buildText(chunk, json)
        val ingredientLines = text.lines().count { it.trimStart().startsWith("Ingredient:") }
        assertEquals(5, ingredientLines, "Should cap at 5 ingredients")
    }

    @Test
    fun `buildItemText handles bench requirement as array and string`() {
        // Array form
        val jsonArr = buildJsonObject {
            putJsonObject("Recipe") {
                putJsonArray("BenchRequirement") {
                    add("Workbench")
                    add("Forge")
                }
                putJsonArray("Input") {
                    addJsonObject { put("ItemId", "Bar_Iron"); put("Quantity", 2) }
                }
            }
        }

        val chunkArr = GameDataChunk(
            id = "test:arr", type = GameDataType.ITEM, name = "Test",
            filePath = "test.json", fileHash = "x", rawJson = jsonArr.toString(),
            tags = emptyList(), relatedIds = emptyList(), textForEmbedding = "",
        )

        val textArr = GameDataTextBuilder.buildText(chunkArr, jsonArr)
        assertTrue(textArr.contains("Bench: Workbench, Forge"), "Should list array bench requirements")

        // String form
        val jsonStr = buildJsonObject {
            putJsonObject("Recipe") {
                put("BenchRequirement", "Anvil")
                putJsonArray("Input") {
                    addJsonObject { put("ItemId", "Bar_Iron"); put("Quantity", 1) }
                }
            }
        }

        val chunkStr = GameDataChunk(
            id = "test:str", type = GameDataType.ITEM, name = "Test2",
            filePath = "test2.json", fileHash = "y", rawJson = jsonStr.toString(),
            tags = emptyList(), relatedIds = emptyList(), textForEmbedding = "",
        )

        val textStr = GameDataTextBuilder.buildText(chunkStr, jsonStr)
        assertTrue(textStr.contains("Bench: Anvil"), "Should handle string bench requirement")
    }

    @Test
    fun `buildProjectileText extracts damage, velocity, particles, and keywords`() {
        val json = buildJsonObject {
            put("Type", "Arrow")
            putJsonObject("Damage") { put("Value", 25) }
            put("MuzzleVelocity", 50.0)
            put("TimeToLive", 5)
            put("Gravity", 9.8)
            put("Bounciness", 0.3)
            putJsonObject("HitParticles") { put("SystemId", "Arrow_Hit") }
            putJsonObject("DeathParticles") { put("SystemId", "Arrow_Death") }
        }
        val chunk = GameDataChunk(
            id = "test:projectile", type = GameDataType.PROJECTILE, name = "Arrow",
            filePath = "test.json", fileHash = "x", rawJson = json.toString(),
            tags = emptyList(), relatedIds = emptyList(), textForEmbedding = "",
        )
        val text = GameDataTextBuilder.buildText(chunk, json)
        assertTrue(text.contains("Projectile: Arrow"))
        assertTrue(text.contains("Damage: 25"))
        assertTrue(text.contains("Muzzle velocity: 50.0"))
        assertTrue(text.contains("Hit particle: Arrow_Hit"))
        assertTrue(text.contains("Death particle: Arrow_Death"))
        assertTrue(text.contains("Keywords: arrow bullet projectile ranged attack trajectory"))
    }

    @Test
    fun `buildWeatherText extracts precipitation, particle system, tags, and keywords`() {
        val json = buildJsonObject {
            put("PrecipitationType", "Rain")
            putJsonObject("Particle") { put("SystemId", "Rain_Drops") }
            putJsonObject("Tags") {
                putJsonArray("Zone") { add("Zone3") }
                putJsonArray("Intensity") { add("Heavy") }
            }
        }
        val chunk = GameDataChunk(
            id = "test:weather", type = GameDataType.WEATHER, name = "HeavyRain",
            filePath = "test.json", fileHash = "x", rawJson = json.toString(),
            tags = emptyList(), relatedIds = emptyList(), textForEmbedding = "",
        )
        val text = GameDataTextBuilder.buildText(chunk, json)
        assertTrue(text.contains("Weather: HeavyRain"))
        assertTrue(text.contains("Precipitation: Rain"))
        assertTrue(text.contains("Particle system: Rain_Drops"))
        assertTrue(text.contains("Zone:Zone3"))
        assertTrue(text.contains("Keywords: weather precipitation rain snow blizzard fog storm"))
    }

    @Test
    fun `buildObjectiveText extracts task types, tasks, completions, and keywords`() {
        val json = buildJsonObject {
            putJsonArray("TaskSets") {
                addJsonObject {
                    putJsonArray("Tasks") {
                        addJsonObject {
                            put("Type", "KillSpawnMarker")
                            put("NpcId", "NPC_Goblin_Warrior")
                            put("Count", 5)
                        }
                        addJsonObject {
                            put("Type", "Reach")
                            put("LocationId", "Location_Cave_Entrance")
                        }
                    }
                }
            }
            putJsonArray("Completions") {
                addJsonObject { put("Type", "TurnIn") }
            }
        }
        val chunk = GameDataChunk(
            id = "test:objective", type = GameDataType.OBJECTIVE, name = "SlayGoblins",
            filePath = "test.json", fileHash = "x", rawJson = json.toString(),
            tags = emptyList(), relatedIds = emptyList(), textForEmbedding = "",
        )
        val text = GameDataTextBuilder.buildText(chunk, json)
        assertTrue(text.contains("Objective: SlayGoblins"))
        assertTrue(text.contains("NPC_Goblin_Warrior"))
        assertTrue(text.contains("Location_Cave_Entrance"))
        assertTrue(text.contains("TurnIn"))
        assertTrue(text.contains("Keywords: objective quest mission task kill bounty"))
    }

    @Test
    fun `buildEnvironmentText extracts gravity, air resistance, ambient light, particle systems, and keywords`() {
        val json = buildJsonObject {
            putJsonObject("Physics") {
                put("Gravity", 9.81)
                put("AirResistance", 0.1)
            }
            putJsonObject("Lighting") { put("AmbientLight", 0.8) }
            putJsonArray("Particles") {
                addJsonObject { put("SystemId", "Dust_Ambient") }
            }
        }
        val chunk = GameDataChunk(
            id = "test:env", type = GameDataType.ENVIRONMENT, name = "DefaultEnv",
            filePath = "test.json", fileHash = "x", rawJson = json.toString(),
            tags = emptyList(), relatedIds = emptyList(), textForEmbedding = "",
        )
        val text = GameDataTextBuilder.buildText(chunk, json)
        assertTrue(text.contains("Environment: DefaultEnv"))
        assertTrue(text.contains("Gravity: 9.81"))
        assertTrue(text.contains("Air resistance: 0.1"))
        assertTrue(text.contains("Ambient light: 0.8"))
        assertTrue(text.contains("Particle systems: Dust_Ambient"))
        assertTrue(text.contains("Keywords: physics gravity lighting ambient environment"))
    }

    @Test
    fun `buildCameraText extracts mode, FOV, offset, zoom, and keywords`() {
        val json = buildJsonObject {
            put("Mode", "ThirdPerson")
            put("FOV", 90.0)
            putJsonObject("Offset") {
                put("X", 0.0)
                put("Y", 1.5)
                put("Z", -3.0)
            }
            put("MinZoom", 2.0)
            put("MaxZoom", 15.0)
        }
        val chunk = GameDataChunk(
            id = "test:camera", type = GameDataType.CAMERA, name = "DefaultCamera",
            filePath = "test.json", fileHash = "x", rawJson = json.toString(),
            tags = emptyList(), relatedIds = emptyList(), textForEmbedding = "",
        )
        val text = GameDataTextBuilder.buildText(chunk, json)
        assertTrue(text.contains("Camera: DefaultCamera"))
        assertTrue(text.contains("Mode: ThirdPerson"))
        assertTrue(text.contains("Field of view: 90.0"))
        assertTrue(text.contains("y=1.5"))
        assertTrue(text.contains("Min zoom: 2.0"))
        assertTrue(text.contains("Keywords: camera view perspective zoom offset FOV"))
    }

    @Test
    fun `buildWorldgenText extracts type, noise, fractal mode, and keywords`() {
        val json = buildJsonObject {
            put("Type", "Noise2D")
            putJsonObject("Noise") {
                put("Type", "Simplex")
                put("FractalMode", "FBM")
                put("Octaves", 6)
                put("Scale", 0.005)
                put("Threshold", 0.3)
            }
        }
        val chunk = GameDataChunk(
            id = "test:worldgen", type = GameDataType.WORLDGEN, name = "TerrainNoise",
            filePath = "test.json", fileHash = "x", rawJson = json.toString(),
            tags = emptyList(), relatedIds = emptyList(), textForEmbedding = "",
        )
        val text = GameDataTextBuilder.buildText(chunk, json)
        assertTrue(text.contains("World gen: TerrainNoise"))
        assertTrue(text.contains("Noise type: Simplex"))
        assertTrue(text.contains("Fractal mode: FBM"))
        assertTrue(text.contains("Octaves: 6"))
        assertTrue(text.contains("Keywords: world generation noise terrain fractal procedural"))
    }

    @Test
    fun `buildGameplayText descends one level into nested objects`() {
        val json = buildJsonObject {
            put("MaxPlayers", 64)
            putJsonObject("Combat") {
                put("PvPEnabled", "true")
                put("FriendlyFire", "false")
                put("RespawnTime", 10)
            }
        }
        val chunk = GameDataChunk(
            id = "test:gameplay", type = GameDataType.GAMEPLAY, name = "GameplayConfig",
            filePath = "test.json", fileHash = "x", rawJson = json.toString(),
            tags = emptyList(), relatedIds = emptyList(), textForEmbedding = "",
        )
        val text = GameDataTextBuilder.buildText(chunk, json)
        assertTrue(text.contains("Gameplay config: GameplayConfig"))
        assertTrue(text.contains("MaxPlayers: 64"))
        assertTrue(text.contains("Combat:"))
        assertTrue(text.contains("PvPEnabled: true"))
        assertTrue(text.contains("Keywords: gameplay config balance setting parameter"))
    }

    @Test
    fun `buildRecipeText includes Keywords line`() {
        val json = buildJsonObject {
            put("BenchRequirement", "Workbench")
            putJsonArray("Input") {
                addJsonObject { put("ItemId", "Wood_Log"); put("Quantity", 2) }
            }
            putJsonObject("PrimaryOutput") {
                put("ItemId", "Wood_Plank"); put("Quantity", 4)
            }
        }
        val chunk = GameDataChunk(
            id = "test:recipe", type = GameDataType.RECIPE, name = "Recipe_Wood_Plank",
            filePath = "test.json", fileHash = "x", rawJson = json.toString(),
            tags = emptyList(), relatedIds = emptyList(), textForEmbedding = "",
        )
        val text = GameDataTextBuilder.buildText(chunk, json)
        assertTrue(text.contains("Keywords: crafting recipe how to make Recipe_Wood_Plank ingredients"))
    }

    @Test
    fun `buildNpcText includes merchant and hostile labels conditionally`() {
        val merchantJson = buildJsonObject { put("Merchant", true) }
        val merchantChunk = GameDataChunk(
            id = "test:npc:merchant", type = GameDataType.NPC, name = "Shopkeeper",
            filePath = "test.json", fileHash = "x", rawJson = merchantJson.toString(),
            tags = emptyList(), relatedIds = emptyList(), textForEmbedding = "",
        )
        val merchantText = GameDataTextBuilder.buildText(merchantChunk, merchantJson)
        assertTrue(merchantText.contains("(merchant, sells items)"))
        assertTrue(merchantText.contains("Keywords: enemy mob creature NPC character"))

        val hostileJson = buildJsonObject { put("Hostile", true) }
        val hostileChunk = GameDataChunk(
            id = "test:npc:hostile", type = GameDataType.NPC, name = "Goblin",
            filePath = "test.json", fileHash = "x", rawJson = hostileJson.toString(),
            tags = emptyList(), relatedIds = emptyList(), textForEmbedding = "",
        )
        val hostileText = GameDataTextBuilder.buildText(hostileChunk, hostileJson)
        assertTrue(hostileText.contains("(hostile enemy)"))
        assertTrue(hostileText.contains("Keywords: enemy mob creature NPC character"))
    }

    @Test
    fun `buildShopText includes Keywords line`() {
        val json = buildJsonObject {
            put("Type", "Barter")
            putJsonArray("Items") {
                addJsonObject { put("ItemId", "Sword_Iron"); put("Price", 50) }
            }
        }
        val chunk = GameDataChunk(
            id = "test:shop", type = GameDataType.SHOP, name = "Blacksmith",
            filePath = "test.json", fileHash = "x", rawJson = json.toString(),
            tags = emptyList(), relatedIds = emptyList(), textForEmbedding = "",
        )
        val text = GameDataTextBuilder.buildText(chunk, json)
        assertTrue(text.contains("Keywords: vendor trade exchange merchant buy sell barter"))
    }

    // ── Missing builder coverage ──────────────────────────────

    private fun makeChunk(type: GameDataType, name: String, json: JsonObject) = GameDataChunk(
        id = "test:${type.id}:$name", type = type, name = name,
        filePath = "Server/${type.displayName}/$name.json", fileHash = "h",
        rawJson = json.toString(), tags = emptyList(), relatedIds = emptyList(), textForEmbedding = "",
    )

    @Test
    fun `buildBlockText extracts material, hardness, drops, tags`() {
        val json = buildJsonObject {
            put("Material", "Stone")
            put("Hardness", 5)
            put("DrawType", "Opaque")
            put("lootTable", "Stone_Rubble")
            putJsonObject("Tags") {
                putJsonArray("Type") { add("Natural") }
            }
        }
        val text = GameDataTextBuilder.buildText(makeChunk(GameDataType.BLOCK, "Granite", json), json)
        assertTrue(text.contains("Block: Granite"))
        assertTrue(text.contains("Material: Stone"))
        assertTrue(text.contains("Hardness: 5"))
        assertTrue(text.contains("Drops: Stone_Rubble"))
        assertTrue(text.contains("Type:Natural"))
    }

    @Test
    fun `buildInteractionText extracts type, action, effects, conditions`() {
        val json = buildJsonObject {
            put("Type", "Use")
            put("Action", "Harvest")
            put("Target", "Crop_Wheat")
            putJsonArray("Effects") { addJsonObject { put("Type", "GainXP") } }
            putJsonArray("Conditions") { addJsonObject { put("Type", "HasTool") } }
        }
        val text = GameDataTextBuilder.buildText(makeChunk(GameDataType.INTERACTION, "HarvestWheat", json), json)
        assertTrue(text.contains("Interaction: HarvestWheat"))
        assertTrue(text.contains("Type: Use"))
        assertTrue(text.contains("Action: Harvest"))
        assertTrue(text.contains("Effects: GainXP"))
        assertTrue(text.contains("Conditions: HasTool"))
    }

    @Test
    fun `buildDropText extracts Container hierarchy and flat drops`() {
        // Container hierarchy
        val containerJson = buildJsonObject {
            putJsonObject("Container") {
                putJsonArray("Containers") {
                    addJsonObject {
                        putJsonObject("Item") { put("ItemId", "Gold_Coin"); put("QuantityMin", 1); put("QuantityMax", 3) }
                    }
                }
            }
        }
        val containerText = GameDataTextBuilder.buildText(makeChunk(GameDataType.DROP, "GoblinDrop", containerJson), containerJson)
        assertTrue(containerText.contains("Drop table: GoblinDrop"))
        assertTrue(containerText.contains("Gold_Coin x1-3"))

        // Flat drops
        val flatJson = buildJsonObject {
            putJsonArray("Drops") {
                addJsonObject { put("ItemId", "Arrow"); put("QuantityMin", 5); put("QuantityMax", 10) }
            }
        }
        val flatText = GameDataTextBuilder.buildText(makeChunk(GameDataType.DROP, "ArcherDrop", flatJson), flatJson)
        assertTrue(flatText.contains("Arrow x5-10"))
    }

    @Test
    fun `buildNpcText extracts health, attitude, template, parameters`() {
        val json = buildJsonObject {
            put("Reference", "Template_Goblin")
            putJsonObject("Modify") {
                put("DefaultPlayerAttitude", "Hostile")
                put("MaxHealth", 100)
                put("DropList", "Drop_Goblin")
            }
            putJsonObject("Parameters") {
                putJsonObject("NameTranslationKey") { put("Value", "npc.goblin.name") }
                putJsonObject("Scale") { put("Value", "1.2") }
            }
        }
        val text = GameDataTextBuilder.buildText(makeChunk(GameDataType.NPC, "Goblin_Warrior", json), json)
        assertTrue(text.contains("NPC: Goblin_Warrior"))
        assertTrue(text.contains("Template: Template_Goblin"))
        assertTrue(text.contains("Player attitude: Hostile"))
        assertTrue(text.contains("Health: 100"))
        assertTrue(text.contains("Name key: npc.goblin.name"))
        assertTrue(text.contains("Parameters: Scale=1.2"))
    }

    @Test
    fun `buildNpcGroupText extracts members with counts and faction`() {
        val json = buildJsonObject {
            put("Faction", "GoblinTribe")
            putJsonArray("Members") {
                addJsonObject { put("NPC", "Goblin_Scout"); put("Count", 4) }
                addJsonObject { put("NPC", "Goblin_Chief"); put("Count", 1) }
            }
        }
        val text = GameDataTextBuilder.buildText(makeChunk(GameDataType.NPC_GROUP, "GoblinPatrol", json), json)
        assertTrue(text.contains("NPC Group: GoblinPatrol"))
        assertTrue(text.contains("Faction: GoblinTribe"))
        assertTrue(text.contains("Goblin_Scout x4"))
        assertTrue(text.contains("Goblin_Chief x1"))
    }

    @Test
    fun `buildNpcAiText extracts states, transitions, actions`() {
        val json = buildJsonObject {
            put("Type", "PatrolBehavior")
            putJsonArray("States") {
                addJsonObject { put("Name", "Idle") }
                addJsonObject { put("Name", "Patrol") }
                addJsonObject { put("Name", "Chase") }
            }
            putJsonArray("Transitions") {
                addJsonObject { put("From", "Idle"); put("To", "Patrol"); put("Condition", "TimerExpired") }
            }
            putJsonArray("Actions") {
                addJsonObject { put("Type", "MoveTo") }
                addJsonObject { put("Type", "Attack") }
            }
        }
        val text = GameDataTextBuilder.buildText(makeChunk(GameDataType.NPC_AI, "GoblinAI", json), json)
        assertTrue(text.contains("NPC AI: GoblinAI"))
        assertTrue(text.contains("States: Idle, Patrol, Chase"))
        assertTrue(text.contains("Idle -> Patrol on TimerExpired"))
        assertTrue(text.contains("Actions: MoveTo, Attack"))
    }

    @Test
    fun `buildEntityText extracts type, health, components, drops`() {
        val json = buildJsonObject {
            put("Type", "Animal")
            put("Health", 50)
            put("Speed", 3.5)
            put("Hostile", false)
            putJsonObject("Components") {
                putJsonObject("PhysicsBody") {}
                putJsonObject("AiController") {}
            }
            put("Drops", "Leather")
        }
        val text = GameDataTextBuilder.buildText(makeChunk(GameDataType.ENTITY, "Deer", json), json)
        assertTrue(text.contains("Entity: Deer"))
        assertTrue(text.contains("Type: Animal"))
        assertTrue(text.contains("Health: 50"))
        assertTrue(text.contains("Speed: 3.5"))
        assertTrue(text.contains("Hostile: false"))
        assertTrue(text.contains("Components: PhysicsBody, AiController"))
        assertTrue(text.contains("Drops: Leather"))
    }

    @Test
    fun `buildFarmingText extracts crop, growth stages, yields`() {
        val json = buildJsonObject {
            put("Crop", "Wheat")
            put("Seed", "Wheat_Seed")
            put("GrowthStages", 4)
            put("GrowthTime", 300)
            put("Soil", "Tilled_Dirt")
            putJsonArray("Yields") {
                addJsonObject { put("ItemId", "Wheat_Bundle"); put("QuantityMin", 1); put("QuantityMax", 3) }
            }
        }
        val text = GameDataTextBuilder.buildText(makeChunk(GameDataType.FARMING, "WheatFarm", json), json)
        assertTrue(text.contains("Farming: WheatFarm"))
        assertTrue(text.contains("Crop: Wheat"))
        assertTrue(text.contains("Growth stages: 4"))
        assertTrue(text.contains("Growth time: 300s"))
        assertTrue(text.contains("Wheat_Bundle x1-3"))
    }

    @Test
    fun `buildBiomeText extracts climate, features, flora, fauna`() {
        val json = buildJsonObject {
            put("Climate", "Temperate")
            put("Temperature", 0.6)
            put("Humidity", 0.5)
            putJsonArray("Features") { addJsonObject { put("Type", "OakTree") }; add("Bush") }
            putJsonArray("Flora") { add("Daisy"); add("Fern") }
            putJsonArray("Fauna") { add("Deer"); add("Rabbit") }
        }
        val text = GameDataTextBuilder.buildText(makeChunk(GameDataType.BIOME, "Forest", json), json)
        assertTrue(text.contains("Biome: Forest"))
        assertTrue(text.contains("Climate: Temperate"))
        assertTrue(text.contains("Features: OakTree, Bush"))
        assertTrue(text.contains("Flora: Daisy, Fern"))
        assertTrue(text.contains("Fauna: Deer, Rabbit"))
    }

    @Test
    fun `buildLocalizationText extracts key-value pairs with cap`() {
        val json = buildJsonObject {
            repeat(25) { i -> put("key_$i", "value_$i") }
        }
        val text = GameDataTextBuilder.buildText(makeChunk(GameDataType.LOCALIZATION, "en_US", json), json)
        assertTrue(text.contains("Localization file:"))
        assertTrue(text.contains("key_0 = value_0"))
        assertTrue(text.contains("... and 5 more keys"), "Should cap at 20 and show remainder")
    }

    @Test
    fun `buildZoneText extracts biome, layers, features`() {
        val json = buildJsonObject {
            put("Biome", "Desert")
            put("Region", "Howling_Sands")
            putJsonArray("Layers") {
                addJsonObject { put("Name", "Surface") }
                addJsonObject { put("Name", "Subsurface") }
            }
            putJsonArray("Features") { addJsonObject { put("Type", "Cactus") } }
        }
        val text = GameDataTextBuilder.buildText(makeChunk(GameDataType.ZONE, "DesertZone", json), json)
        assertTrue(text.contains("Zone: DesertZone"))
        assertTrue(text.contains("Biome: Desert"))
        assertTrue(text.contains("Region: Howling_Sands"))
        assertTrue(text.contains("Layers: Surface, Subsurface"))
        assertTrue(text.contains("Features: Cactus"))
    }

    @Test
    fun `buildTerrainLayerText extracts blocks, noise type`() {
        val json = buildJsonObject {
            put("Type", "Surface")
            put("Depth", 3)
            put("TopBlock", "Grass")
            putJsonObject("Noise") { put("Type", "Perlin"); put("Scale", 0.01) }
        }
        val text = GameDataTextBuilder.buildText(makeChunk(GameDataType.TERRAIN_LAYER, "GrassLayer", json), json)
        assertTrue(text.contains("Terrain layer: GrassLayer"))
        assertTrue(text.contains("Blocks: Grass"))
        assertTrue(text.contains("Noise type: Perlin"))
        assertTrue(text.contains("Depth: 3"))
    }

    @Test
    fun `buildCaveText extracts depth, blocks, ores, enemies`() {
        val json = buildJsonObject {
            put("Biome", "Underground")
            put("MinDepth", 20)
            put("MaxDepth", 60)
            putJsonArray("Blocks") { addJsonObject { put("Id", "Cave_Stone") } }
            putJsonArray("Ores") { addJsonObject { put("Id", "Iron_Ore") } }
            putJsonArray("Enemies") { addJsonObject { put("Id", "Cave_Spider") } }
        }
        val text = GameDataTextBuilder.buildText(makeChunk(GameDataType.CAVE, "DeepCave", json), json)
        assertTrue(text.contains("Cave: DeepCave"))
        assertTrue(text.contains("Min depth: 20"))
        assertTrue(text.contains("Max depth: 60"))
        assertTrue(text.contains("Blocks: Cave_Stone"))
        assertTrue(text.contains("Ores: Iron_Ore"))
        assertTrue(text.contains("Enemies: Cave_Spider"))
    }

    @Test
    fun `buildPrefabText extracts block palette, entities, bounding box`() {
        val json = buildJsonObject {
            putJsonArray("blocks") {
                addJsonObject { put("name", "Stone"); put("x", 0); put("y", 0); put("z", 0) }
                addJsonObject { put("name", "Stone"); put("x", 1); put("y", 0); put("z", 0) }
                addJsonObject { put("name", "Wood"); put("x", 0); put("y", 1); put("z", 0) }
            }
            putJsonArray("entities") {
                addJsonObject { put("EntityType", "Torch_Standing") }
            }
        }
        val text = GameDataTextBuilder.buildText(makeChunk(GameDataType.PREFAB, "SmallHouse", json), json)
        assertTrue(text.contains("Prefab: SmallHouse"))
        assertTrue(text.contains("Total blocks: 3"))
        assertTrue(text.contains("Bounding box: 2x2x1"))
        assertTrue(text.contains("Stone x2"))
        assertTrue(text.contains("Wood x1"))
        assertTrue(text.contains("Entities (1): Torch_Standing"))
    }
}
