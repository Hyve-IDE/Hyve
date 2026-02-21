// Copyright 2026 Hyve. All rights reserved.
package com.hyve.knowledge.extraction

import com.hyve.knowledge.core.extraction.GameDataChunk
import com.hyve.knowledge.core.extraction.GameDataType
import kotlinx.serialization.json.*

/**
 * Builds human-readable embedding text for each GameDataChunk.
 *
 * Each type-specific builder produces a dense plain-text summary that maximises
 * semantic retrieval quality. All helpers accept vararg keys and try each in order
 * (PascalCase first, then camelCase fallbacks) so that both Hytale's actual JSON
 * format and the legacy TypeScript-ported field names are covered.
 */
object GameDataTextBuilder {

    /** Bump this whenever builder logic changes to force a full re-index. */
    const val TEXT_BUILDER_VERSION = 7

    fun buildText(chunk: GameDataChunk, obj: JsonObject): String {
        return when (chunk.type) {
            GameDataType.ITEM -> buildItemText(chunk, obj)
            GameDataType.RECIPE -> buildRecipeText(chunk, obj)
            GameDataType.BLOCK -> buildBlockText(chunk, obj)
            GameDataType.INTERACTION -> buildInteractionText(chunk, obj)
            GameDataType.DROP -> buildDropText(chunk, obj)
            GameDataType.NPC -> buildNpcText(chunk, obj)
            GameDataType.NPC_GROUP -> buildNpcGroupText(chunk, obj)
            GameDataType.NPC_AI -> buildNpcAiText(chunk, obj)
            GameDataType.ENTITY -> buildEntityText(chunk, obj)
            GameDataType.PROJECTILE -> buildProjectileText(chunk, obj)
            GameDataType.FARMING -> buildFarmingText(chunk, obj)
            GameDataType.SHOP -> buildShopText(chunk, obj)
            GameDataType.ENVIRONMENT -> buildEnvironmentText(chunk, obj)
            GameDataType.WEATHER -> buildWeatherText(chunk, obj)
            GameDataType.BIOME -> buildBiomeText(chunk, obj)
            GameDataType.WORLDGEN -> buildWorldgenText(chunk, obj)
            GameDataType.CAMERA -> buildCameraText(chunk, obj)
            GameDataType.OBJECTIVE -> buildObjectiveText(chunk, obj)
            GameDataType.GAMEPLAY -> buildGameplayText(chunk, obj)
            GameDataType.LOCALIZATION -> buildLocalizationText(chunk, obj)
            GameDataType.ZONE -> buildZoneText(chunk, obj)
            GameDataType.TERRAIN_LAYER -> buildTerrainLayerText(chunk, obj)
            GameDataType.CAVE -> buildCaveText(chunk, obj)
            GameDataType.PREFAB -> buildPrefabText(chunk, obj)
        }
    }

    // ── Item ─────────────────────────────────────────────────────

    private fun buildItemText(chunk: GameDataChunk, obj: JsonObject): String {
        val sb = StringBuilder()
        sb.appendLine("Item: ${chunk.name}")

        // Display name from TranslationProperties.Name or legacy displayName
        obj.obj("TranslationProperties")?.str("Name")?.let {
            sb.appendLine("Display name key: $it")
        } ?: obj.str("displayName", "DisplayName")?.let { sb.appendLine("Display name: $it") }

        obj.str("description", "Description")?.let { sb.appendLine("Description: $it") }

        // Categories array
        obj.arr("Categories", "categories")?.let { arr ->
            val cats = arr.mapNotNull { it.contentOrNull() }
            if (cats.isNotEmpty()) sb.appendLine("Categories: ${cats.joinToString(".")}")
        } ?: obj.str("category", "Category")?.let { sb.appendLine("Category: $it") }

        obj.str("Quality", "quality", "rarity")?.let { sb.appendLine("Quality: $it") }
        obj.int("MaxStackSize", "stackSize")?.let { sb.appendLine("Stack size: $it") }
        obj.int("MaxDurability")?.let { sb.appendLine("Max durability: $it") }
        obj.str("Set")?.let { sb.appendLine("Set: $it") }

        // Tags (object form with sub-arrays or flat array)
        val tags = extractTagsFlexible(obj)
        if (tags.isNotEmpty()) sb.appendLine("Tags: ${tags.joinToString(", ")}")

        // BlockType summary
        obj.obj("BlockType")?.let { bt ->
            val parts = mutableListOf<String>()
            bt.str("DrawType")?.let { parts.add("draw=$it") }
            bt.str("Material")?.let { parts.add("material=$it") }
            bt.str("Light")?.let { parts.add("light=$it") }
            if (parts.isNotEmpty()) sb.appendLine("Block type: ${parts.joinToString(", ")}")
        }

        // ResourceTypes
        obj.arr("ResourceTypes")?.let { arr ->
            val types = arr.mapNotNull {
                it.jsonObjectOrNull()?.str("ResourceType") ?: it.contentOrNull()
            }
            if (types.isNotEmpty()) sb.appendLine("Resource types: ${types.joinToString(", ")}")
        }

        obj.bool("Tradeable", "tradeable")?.let { sb.appendLine("Tradeable: $it") }
        obj.bool("Consumable", "consumable")?.let { sb.appendLine("Consumable: $it") }
        obj.bool("Equippable", "equippable")?.let { sb.appendLine("Equippable: $it") }

        obj["Stats"]?.jsonObjectOrNull()?.let { stats ->
            sb.appendLine("Stats:")
            for ((k, v) in stats) {
                sb.appendLine("  $k: ${v.contentOrNull()}")
            }
        } ?: obj["stats"]?.jsonObjectOrNull()?.let { stats ->
            sb.appendLine("Stats:")
            for ((k, v) in stats) {
                sb.appendLine("  $k: ${v.contentOrNull()}")
            }
        }

        obj.obj("Recipe")?.let { recipe ->
            sb.appendLine("Crafting recipe:")

            recipe.arr("BenchRequirement")?.let { benches ->
                val names = benches.mapNotNull { it.contentOrNull() }
                if (names.isNotEmpty()) sb.appendLine("  Bench: ${names.joinToString(", ")}")
            } ?: recipe.str("BenchRequirement")?.let { bench ->
                sb.appendLine("  Bench: $bench")
            }

            val inputKeys = listOf("Input", "inputs", "ingredients")
            var ingredientCount = 0
            outer@ for (key in inputKeys) {
                recipe[key]?.jsonArrayOrNull()?.let { inputs ->
                    for (input in inputs.take(5)) {
                        val o = input.jsonObjectOrNull() ?: continue
                        val itemId = o.str("ItemId", "item", "id") ?: continue
                        val qty = o.int("Quantity", "quantity", "amount") ?: 1
                        sb.appendLine("  Ingredient: $itemId x$qty")
                        ingredientCount++
                    }
                    if (ingredientCount > 0) break@outer
                }
            }

            recipe.int("TimeSeconds")?.let { sb.appendLine("  Crafting time: ${it}s") }

            recipe.obj("PrimaryOutput")?.let { po ->
                val item = po.str("ItemId", "item", "id")
                val qty = po.int("Quantity", "quantity") ?: 1
                if (item != null) sb.appendLine("  Output: $item x$qty")
            } ?: recipe.int("OutputQuantity")?.let { qty ->
                sb.appendLine("  Output quantity: $qty")
            }

            sb.appendLine("Keywords: crafting recipe ingredients how to make craft")
        }

        obj.str("Icon", "icon")?.let { sb.appendLine("Icon: $it") }
        sb.appendLine("File: ${chunk.filePath}")
        return sb.toString().trim()
    }

    // ── Recipe ───────────────────────────────────────────────────

    private fun buildRecipeText(chunk: GameDataChunk, obj: JsonObject): String {
        val sb = StringBuilder()
        sb.appendLine("Recipe: ${chunk.name}")

        obj.str("Type", "type")?.let { sb.appendLine("Crafting type: $it") }

        // Hytale uses BenchRequirement (string) or station
        obj.str("BenchRequirement", "station")?.let { sb.appendLine("Bench: $it") }

        // Input array (Hytale: Input[]{ItemId, Quantity})
        val inputKeys = listOf("Input", "inputs", "ingredients")
        for (key in inputKeys) {
            obj[key]?.jsonArrayOrNull()?.let { inputs ->
                if (inputs.isNotEmpty()) {
                    sb.appendLine("Inputs:")
                    for (input in inputs) {
                        val o = input.jsonObjectOrNull() ?: continue
                        val item = o.str("ItemId", "item", "id") ?: continue
                        val qty = o.int("Quantity", "quantity", "amount") ?: 1
                        sb.appendLine("  $item x$qty")
                    }
                    return@let
                }
            }
        }

        // PrimaryOutput (Hytale: single object {ItemId, Quantity})
        obj.obj("PrimaryOutput")?.let { po ->
            val item = po.str("ItemId", "item", "id") ?: return@let
            val qty = po.int("Quantity", "quantity") ?: 1
            sb.appendLine("Primary output: $item x$qty")
        }

        // Output array (Hytale: Output[]{ItemId, Quantity})
        val outputKeys = listOf("Output", "outputs")
        for (key in outputKeys) {
            obj[key]?.jsonArrayOrNull()?.let { outputs ->
                if (outputs.isNotEmpty()) {
                    sb.appendLine("Additional outputs:")
                    for (out in outputs) {
                        val o = out.jsonObjectOrNull() ?: continue
                        val item = o.str("ItemId", "item", "id") ?: continue
                        val qty = o.int("Quantity", "quantity", "amount") ?: 1
                        sb.appendLine("  $item x$qty")
                    }
                    return@let
                }
            }
        }

        obj["result"]?.jsonObjectOrNull()?.let { result ->
            val item = result.str("ItemId", "item", "id") ?: ""
            val qty = result.int("Quantity", "quantity", "amount") ?: 1
            if (item.isNotBlank()) sb.appendLine("Result: $item x$qty")
        }

        // TimeSeconds (Hytale) or craftingTime (legacy)
        obj.int("TimeSeconds")?.let { sb.appendLine("Crafting time: ${it}s") }
            ?: obj.int("craftingTime")?.let { sb.appendLine("Crafting time: ${it}ms") }

        obj.str("RequiredSkill", "requiredSkill")?.let { sb.appendLine("Required skill: $it") }
        obj.int("RequiredLevel", "requiredLevel")?.let { sb.appendLine("Required level: $it") }

        sb.appendLine("Keywords: crafting recipe how to make ${chunk.name} ingredients")

        sb.appendLine("File: ${chunk.filePath}")
        return sb.toString().trim()
    }

    // ── Block ────────────────────────────────────────────────────

    private fun buildBlockText(chunk: GameDataChunk, obj: JsonObject): String {
        val sb = StringBuilder()
        sb.appendLine("Block: ${chunk.name}")

        obj.str("DisplayName", "displayName")?.let { sb.appendLine("Display name: $it") }
        obj.str("Material", "material")?.let { sb.appendLine("Material: $it") }
        obj.str("DrawType", "type")?.let { sb.appendLine("Draw type: $it") }
        obj.int("Hardness", "hardness")?.let { sb.appendLine("Hardness: $it") }
        obj.str("Tool", "tool")?.let { sb.appendLine("Required tool: $it") }
        obj.bool("Solid", "solid")?.let { sb.appendLine("Solid: $it") }
        obj.bool("Transparent", "transparent")?.let { sb.appendLine("Transparent: $it") }
        obj.bool("Collidable", "collidable")?.let { sb.appendLine("Collidable: $it") }
        obj.int("LightLevel", "lightLevel")?.let { sb.appendLine("Light level: $it") }
        obj.str("Light")?.let { sb.appendLine("Light: $it") }

        val drops = extractBlocksDeep(obj["Drops"] ?: obj["drops"] ?: obj["lootTable"])
        if (drops.isNotEmpty()) sb.appendLine("Drops: ${drops.joinToString(", ")}")

        val tags = extractTagsFlexible(obj)
        if (tags.isNotEmpty()) sb.appendLine("Tags: ${tags.joinToString(", ")}")

        sb.appendLine("File: ${chunk.filePath}")
        return sb.toString().trim()
    }

    // ── Interaction ──────────────────────────────────────────────

    private fun buildInteractionText(chunk: GameDataChunk, obj: JsonObject): String {
        val sb = StringBuilder()
        sb.appendLine("Interaction: ${chunk.name}")

        obj.str("Type", "type")?.let { sb.appendLine("Type: $it") }
        obj.str("TargetType", "targetType")?.let { sb.appendLine("Target type: $it") }
        obj.str("Target", "target")?.let { sb.appendLine("Target: $it") }
        obj.str("Action", "action")?.let { sb.appendLine("Action: $it") }
        obj.str("Trigger", "trigger")?.let { sb.appendLine("Trigger: $it") }
        obj.str("Result", "result")?.let { sb.appendLine("Result: $it") }
        obj.str("Animation", "animation")?.let { sb.appendLine("Animation: $it") }
        obj.str("Sound", "sound")?.let { sb.appendLine("Sound: $it") }
        obj.int("RunTime")?.let { sb.appendLine("Run time: $it") }
        obj.bool("RequiresTool", "requiresTool")?.let { sb.appendLine("Requires tool: $it") }

        for (key in listOf("Conditions", "conditions")) {
            obj[key]?.jsonArrayOrNull()?.let { arr ->
                val conds = arr.mapNotNull { it.jsonObjectOrNull()?.str("Type", "type") ?: it.contentOrNull() }
                if (conds.isNotEmpty()) sb.appendLine("Conditions: ${conds.joinToString(", ")}")
                return@let
            }
        }

        for (key in listOf("Effects", "effects")) {
            obj[key]?.jsonArrayOrNull()?.let { arr ->
                val effects = arr.mapNotNull { it.jsonObjectOrNull()?.str("Type", "type") ?: it.contentOrNull() }
                if (effects.isNotEmpty()) sb.appendLine("Effects: ${effects.joinToString(", ")}")
                return@let
            }
        }

        sb.appendLine("File: ${chunk.filePath}")
        return sb.toString().trim()
    }

    // ── Drop ─────────────────────────────────────────────────────

    private fun buildDropText(chunk: GameDataChunk, obj: JsonObject): String {
        val sb = StringBuilder()
        sb.appendLine("Drop table: ${chunk.name}")

        obj.str("Type", "type")?.let { sb.appendLine("Type: $it") }
        obj.str("Source", "source")?.let { sb.appendLine("Source: $it") }

        // Hytale uses hierarchical Container structure
        val containerItems = mutableListOf<String>()
        obj.obj("Container")?.let { extractContainerItems(it, containerItems) }

        if (containerItems.isNotEmpty()) {
            sb.appendLine("Drops:")
            containerItems.forEach { sb.appendLine("  $it") }
        } else {
            // Fallback: flat array scanning (legacy format)
            val allDrops = mutableListOf<String>()
            for (key in listOf("Drops", "drops", "entries", "items", "loot")) {
                obj[key]?.jsonArrayOrNull()?.forEach { el ->
                    val o = el.jsonObjectOrNull()
                    val item = o?.str("ItemId", "item", "id") ?: el.contentOrNull() ?: return@forEach
                    val minQ = o?.int("QuantityMin", "minQuantity", "min") ?: 1
                    val maxQ = o?.int("QuantityMax", "maxQuantity", "max") ?: minQ
                    val chance = o?.double("Chance", "chance", "probability")
                    val entry = if (chance != null) "$item x$minQ-$maxQ (${(chance * 100).toInt()}%)" else "$item x$minQ-$maxQ"
                    allDrops.add(entry)
                }
            }
            if (allDrops.isNotEmpty()) {
                sb.appendLine("Drops:")
                allDrops.forEach { sb.appendLine("  $it") }
            }
        }

        obj.str("Condition", "condition")?.let { sb.appendLine("Condition: $it") }
        sb.appendLine("File: ${chunk.filePath}")
        return sb.toString().trim()
    }

    // ── NPC ──────────────────────────────────────────────────────

    private fun buildNpcText(chunk: GameDataChunk, obj: JsonObject): String {
        val sb = StringBuilder()
        sb.appendLine("NPC: ${chunk.name}")

        // Hytale NPCs use Type: "Variant", Reference: "Template_...", Modify: { actual data }
        obj.str("Reference")?.let { sb.appendLine("Template: $it") }

        // The real NPC stats are often inside a "Modify" sub-object
        val modify = obj.obj("Modify")
        val data = modify ?: obj  // fall back to root if no Modify block

        // Name translation key — can be in Parameters as {Value: "..."} or directly
        val nameKey = obj.obj("Parameters")?.obj("NameTranslationKey")?.str("Value")
            ?: data.str("NameTranslationKey")
        nameKey?.let { sb.appendLine("Name key: $it") }

        data.str("displayName", "DisplayName")?.let { sb.appendLine("Display name: $it") }
        data.str("DefaultPlayerAttitude")?.let { sb.appendLine("Player attitude: $it") }
        data.str("DefaultNPCAttitude")?.let { sb.appendLine("NPC attitude: $it") }
        data.str("attitude")?.let { sb.appendLine("Attitude: $it") }
        data.str("role", "Role")?.let { sb.appendLine("Role: $it") }
        data.str("faction", "Faction")?.let { sb.appendLine("Faction: $it") }
        data.str("StartState")?.let { sb.appendLine("Start state: $it") }
        data.str("Appearance")?.let { sb.appendLine("Appearance: $it") }

        // MaxHealth can be int or {Compute: ...} object
        val maxHealthEl = data["MaxHealth"] ?: data["health"]
        when (maxHealthEl) {
            is JsonPrimitive -> maxHealthEl.content.toIntOrNull()?.let { sb.appendLine("Health: $it") }
            is JsonObject -> {
                maxHealthEl.str("Compute")?.let { sb.appendLine("Health: computed ($it)") }
                    ?: sb.appendLine("Health: computed")
            }
            else -> {}
        }

        data.int("HearingRange")?.let { sb.appendLine("Hearing range: $it") }
        data.int("ViewSector")?.let { sb.appendLine("View sector: $it") }
        data.int("AttackDistance")?.let { sb.appendLine("Attack distance: $it") }
        data.int("BlockProbability")?.let { sb.appendLine("Block probability: $it%") }
        data.bool("BreathesInWater")?.let { if (it) sb.appendLine("Breathes in water: true") }
        data.str("DropList")?.let { sb.appendLine("Drop list: $it") }

        data.int("level", "Level")?.let { sb.appendLine("Level: $it") }
        data.bool("hostile", "Hostile")?.let { sb.appendLine("Hostile: $it") }
        data.bool("merchant", "Merchant")?.let { sb.appendLine("Merchant: $it") }

        // Vocabulary injection: conditional labels aid retrieval for merchant and hostile queries
        data.bool("merchant", "Merchant")?.let { if (it) sb.appendLine("(merchant, sells items)") }
        data.bool("hostile", "Hostile")?.let { if (it) sb.appendLine("(hostile enemy)") }
        sb.appendLine("Keywords: enemy mob creature NPC character")

        // MotionControllerList
        data.arr("MotionControllerList")?.let { arr ->
            val controllers = arr.mapNotNull { it.jsonObjectOrNull()?.str("Type") ?: it.contentOrNull() }
            if (controllers.isNotEmpty()) sb.appendLine("Motion controllers: ${controllers.joinToString(", ")}")
        }

        // Parameters summary (extract Value from each param object)
        obj.obj("Parameters")?.let { params ->
            val parts = mutableListOf<String>()
            for ((k, v) in params) {
                if (k == "NameTranslationKey") continue  // already handled above
                val s = when (v) {
                    is JsonObject -> v.str("Value")
                    is JsonPrimitive -> v.content.takeIf { it.isNotBlank() }
                    else -> null
                }
                if (s != null) parts.add("$k=$s")
            }
            if (parts.isNotEmpty()) sb.appendLine("Parameters: ${parts.take(10).joinToString(", ")}")
        }

        data.str("aiType", "AiType")?.let { sb.appendLine("AI type: $it") }

        for (key in listOf("Drops", "drops")) {
            (data[key] ?: obj[key])?.let { drops ->
                val names = extractBlocksDeep(drops)
                if (names.isNotEmpty()) sb.appendLine("Drops: ${names.joinToString(", ")}")
                return@let
            }
        }

        data.str("model", "Model")?.let { sb.appendLine("Model: $it") }

        sb.appendLine("File: ${chunk.filePath}")
        return sb.toString().trim()
    }

    // ── NPC Group ────────────────────────────────────────────────

    private fun buildNpcGroupText(chunk: GameDataChunk, obj: JsonObject): String {
        val sb = StringBuilder()
        sb.appendLine("NPC Group: ${chunk.name}")

        obj.str("Type", "type")?.let { sb.appendLine("Type: $it") }
        obj.str("Faction", "faction")?.let { sb.appendLine("Faction: $it") }
        obj.str("Attitude", "attitude")?.let { sb.appendLine("Attitude: $it") }

        for (key in listOf("Members", "members")) {
            obj[key]?.jsonArrayOrNull()?.let { arr ->
                sb.appendLine("Members:")
                for (member in arr) {
                    val o = member.jsonObjectOrNull()
                    val npc = o?.str("NPC", "npc", "id") ?: member.contentOrNull() ?: continue
                    val count = o?.int("Count", "count") ?: 1
                    sb.appendLine("  $npc x$count")
                }
                return@let
            }
        }

        for (key in listOf("NPCs", "npcs")) {
            obj[key]?.jsonArrayOrNull()?.let { arr ->
                sb.appendLine("NPCs:")
                for (npc in arr) {
                    val o = npc.jsonObjectOrNull()
                    val id = o?.str("Id", "id") ?: npc.contentOrNull() ?: continue
                    sb.appendLine("  $id")
                }
                return@let
            }
        }

        obj.str("SpawnCondition", "spawnCondition")?.let { sb.appendLine("Spawn condition: $it") }
        obj.int("MinSize", "minSize")?.let { sb.appendLine("Min size: $it") }
        obj.int("MaxSize", "maxSize")?.let { sb.appendLine("Max size: $it") }

        sb.appendLine("File: ${chunk.filePath}")
        return sb.toString().trim()
    }

    // ── NPC AI ───────────────────────────────────────────────────

    private fun buildNpcAiText(chunk: GameDataChunk, obj: JsonObject): String {
        val sb = StringBuilder()
        sb.appendLine("NPC AI: ${chunk.name}")

        obj.str("Type", "type")?.let { sb.appendLine("Type: $it") }
        obj.str("Behavior", "behavior")?.let { sb.appendLine("Behavior: $it") }

        for (key in listOf("States", "states")) {
            obj[key]?.jsonArrayOrNull()?.let { arr ->
                val states = arr.mapNotNull {
                    it.jsonObjectOrNull()?.str("Name", "name") ?: it.contentOrNull()
                }
                if (states.isNotEmpty()) sb.appendLine("States: ${states.joinToString(", ")}")
                return@let
            }
        }

        for (key in listOf("Transitions", "transitions")) {
            obj[key]?.jsonArrayOrNull()?.let { arr ->
                sb.appendLine("Transitions:")
                for (t in arr) {
                    val o = t.jsonObjectOrNull() ?: continue
                    val from = o.str("From", "from") ?: continue
                    val to = o.str("To", "to") ?: continue
                    val cond = o.str("Condition", "condition") ?: ""
                    sb.appendLine("  $from -> $to${if (cond.isNotBlank()) " on $cond" else ""}")
                }
                return@let
            }
        }

        for (key in listOf("Actions", "actions")) {
            obj[key]?.jsonArrayOrNull()?.let { arr ->
                val actions = arr.mapNotNull {
                    it.jsonObjectOrNull()?.str("Type", "type") ?: it.contentOrNull()
                }
                if (actions.isNotEmpty()) sb.appendLine("Actions: ${actions.joinToString(", ")}")
                return@let
            }
        }

        for (key in listOf("Goals", "goals")) {
            obj[key]?.jsonArrayOrNull()?.let { arr ->
                val goals = arr.mapNotNull {
                    it.jsonObjectOrNull()?.str("Type", "type") ?: it.contentOrNull()
                }
                if (goals.isNotEmpty()) sb.appendLine("Goals: ${goals.joinToString(", ")}")
                return@let
            }
        }

        sb.appendLine("File: ${chunk.filePath}")
        return sb.toString().trim()
    }

    // ── Entity ───────────────────────────────────────────────────

    private fun buildEntityText(chunk: GameDataChunk, obj: JsonObject): String {
        val sb = StringBuilder()
        sb.appendLine("Entity: ${chunk.name}")

        obj.str("Type", "type")?.let { sb.appendLine("Type: $it") }
        obj.str("Category", "category")?.let { sb.appendLine("Category: $it") }
        obj.int("Health", "health")?.let { sb.appendLine("Health: $it") }
        obj.double("Speed", "speed")?.let { sb.appendLine("Speed: $it") }
        obj.bool("Hostile", "hostile")?.let { sb.appendLine("Hostile: $it") }
        obj.bool("Passive", "passive")?.let { sb.appendLine("Passive: $it") }
        obj.bool("Tameable", "tameable")?.let { sb.appendLine("Tameable: $it") }
        obj.str("Model", "model")?.let { sb.appendLine("Model: $it") }
        obj.str("Texture", "texture")?.let { sb.appendLine("Texture: $it") }

        // Components can be array or object (Hytale uses object with component names as keys)
        for (key in listOf("Components", "components")) {
            val el = obj[key] ?: continue
            when (el) {
                is JsonArray -> {
                    val comps = el.mapNotNull {
                        it.jsonObjectOrNull()?.str("Type", "type") ?: it.contentOrNull()
                    }
                    if (comps.isNotEmpty()) sb.appendLine("Components: ${comps.joinToString(", ")}")
                }
                is JsonObject -> {
                    val keys = el.keys.toList()
                    if (keys.isNotEmpty()) sb.appendLine("Components: ${keys.joinToString(", ")}")
                }
                else -> {}
            }
            break
        }

        for (key in listOf("Drops", "drops")) {
            obj[key]?.let { drops ->
                val names = extractBlocksDeep(drops)
                if (names.isNotEmpty()) sb.appendLine("Drops: ${names.joinToString(", ")}")
                return@let
            }
        }

        sb.appendLine("File: ${chunk.filePath}")
        return sb.toString().trim()
    }

    // ── Farming ──────────────────────────────────────────────────

    private fun buildFarmingText(chunk: GameDataChunk, obj: JsonObject): String {
        val sb = StringBuilder()
        sb.appendLine("Farming: ${chunk.name}")

        obj.str("Type", "type")?.let { sb.appendLine("Type: $it") }
        obj.str("Modifier", "modifier")?.let { sb.appendLine("Modifier: $it") }
        obj.str("Crop", "crop")?.let { sb.appendLine("Crop: $it") }
        obj.str("Seed", "seed")?.let { sb.appendLine("Seed: $it") }
        obj.int("GrowthStages", "growthStages")?.let { sb.appendLine("Growth stages: $it") }
        obj.int("GrowthTime", "growthTime")?.let { sb.appendLine("Growth time: ${it}s") }
        obj.str("Soil", "soil")?.let { sb.appendLine("Required soil: $it") }
        obj.str("Tool", "tool")?.let { sb.appendLine("Harvest tool: $it") }

        for (key in listOf("Yields", "yields")) {
            obj[key]?.jsonArrayOrNull()?.let { arr ->
                sb.appendLine("Yields:")
                for (y in arr) {
                    val o = y.jsonObjectOrNull()
                    val item = o?.str("ItemId", "item", "id") ?: y.contentOrNull() ?: continue
                    val min = o?.int("QuantityMin", "minQuantity", "min") ?: 1
                    val max = o?.int("QuantityMax", "maxQuantity", "max") ?: min
                    sb.appendLine("  $item x$min-$max")
                }
                return@let
            }
        }

        for (key in listOf("Drops", "drops")) {
            obj[key]?.let { drops ->
                val names = extractBlocksDeep(drops)
                if (names.isNotEmpty()) sb.appendLine("Drops: ${names.joinToString(", ")}")
                return@let
            }
        }

        obj.str("Season", "season")?.let { sb.appendLine("Season: $it") }
        for (key in listOf("Seasons", "seasons")) {
            obj[key]?.jsonArrayOrNull()?.let { arr ->
                val seasons = arr.mapNotNull { it.contentOrNull() }
                if (seasons.isNotEmpty()) sb.appendLine("Seasons: ${seasons.joinToString(", ")}")
                return@let
            }
        }

        sb.appendLine("File: ${chunk.filePath}")
        return sb.toString().trim()
    }

    // ── Shop ─────────────────────────────────────────────────────

    private fun buildShopText(chunk: GameDataChunk, obj: JsonObject): String {
        val sb = StringBuilder()
        sb.appendLine("Barter shop: ${chunk.name}")

        obj.str("Shopkeeper", "shopkeeper")?.let { sb.appendLine("Shopkeeper: $it") }
        obj.str("Type", "type")?.let { sb.appendLine("Type: $it") }
        obj.str("Currency", "currency")?.let { sb.appendLine("Currency: $it") }

        for (key in listOf("Trades", "trades")) {
            obj[key]?.jsonArrayOrNull()?.let { arr ->
                sb.appendLine("Trades:")
                for (trade in arr) {
                    val o = trade.jsonObjectOrNull() ?: continue
                    val offer = o.str("Offer", "offer", "item") ?: continue
                    val cost = o.obj("Cost") ?: o.obj("cost")
                    val costStr = if (cost != null) {
                        val item = cost.str("ItemId", "item") ?: ""
                        val qty = cost.int("Quantity", "quantity") ?: 1
                        "$item x$qty"
                    } else ""
                    sb.appendLine("  $offer${if (costStr.isNotBlank()) " for $costStr" else ""}")
                }
                return@let
            }
        }

        for (key in listOf("Items", "items")) {
            obj[key]?.jsonArrayOrNull()?.let { arr ->
                sb.appendLine("Items:")
                for (item in arr) {
                    val o = item.jsonObjectOrNull()
                    val id = o?.str("ItemId", "item", "id") ?: item.contentOrNull() ?: continue
                    val price = o?.int("Price", "price", "cost")
                    sb.appendLine("  $id${if (price != null) " ($price)" else ""}")
                }
                return@let
            }
        }

        sb.appendLine("Keywords: vendor trade exchange merchant buy sell barter")

        sb.appendLine("File: ${chunk.filePath}")
        return sb.toString().trim()
    }

    // ── Biome ────────────────────────────────────────────────────

    private fun buildBiomeText(chunk: GameDataChunk, obj: JsonObject): String {
        val sb = StringBuilder()
        sb.appendLine("Biome: ${chunk.name}")

        obj.str("DisplayName", "displayName")?.let { sb.appendLine("Display name: $it") }
        obj.str("Type", "type")?.let { sb.appendLine("Type: $it") }
        obj.str("Climate", "climate")?.let { sb.appendLine("Climate: $it") }
        obj.double("Temperature", "temperature")?.let { sb.appendLine("Temperature: $it") }
        obj.double("Humidity", "humidity")?.let { sb.appendLine("Humidity: $it") }

        for (key in listOf("Features", "features")) {
            obj[key]?.jsonArrayOrNull()?.let { arr ->
                val features = arr.mapNotNull {
                    it.jsonObjectOrNull()?.str("Type", "type") ?: it.contentOrNull()
                }
                if (features.isNotEmpty()) sb.appendLine("Features: ${features.joinToString(", ")}")
                return@let
            }
        }

        for (key in listOf("Blocks", "blocks")) {
            obj[key]?.jsonArrayOrNull()?.let { arr ->
                val blocks = arr.mapNotNull {
                    it.jsonObjectOrNull()?.str("Id", "id") ?: it.contentOrNull()
                }
                if (blocks.isNotEmpty()) sb.appendLine("Blocks: ${blocks.joinToString(", ")}")
                return@let
            }
        }

        for (key in listOf("Flora", "flora")) {
            obj[key]?.jsonArrayOrNull()?.let { arr ->
                val flora = arr.mapNotNull { it.contentOrNull() }
                if (flora.isNotEmpty()) sb.appendLine("Flora: ${flora.joinToString(", ")}")
                return@let
            }
        }

        for (key in listOf("Fauna", "fauna")) {
            obj[key]?.jsonArrayOrNull()?.let { arr ->
                val fauna = arr.mapNotNull { it.contentOrNull() }
                if (fauna.isNotEmpty()) sb.appendLine("Fauna: ${fauna.joinToString(", ")}")
                return@let
            }
        }

        obj.str("Music", "music")?.let { sb.appendLine("Music: $it") }
        obj.str("Ambience", "ambience")?.let { sb.appendLine("Ambience: $it") }
        obj.str("SkyColor", "skyColor")?.let { sb.appendLine("Sky color: $it") }
        obj.str("FogColor", "fogColor")?.let { sb.appendLine("Fog color: $it") }

        sb.appendLine("File: ${chunk.filePath}")
        return sb.toString().trim()
    }

    // ── Localization ─────────────────────────────────────────────

    private fun buildLocalizationText(chunk: GameDataChunk, obj: JsonObject): String {
        val sb = StringBuilder()
        val lang = chunk.filePath.substringAfterLast('/').removeSuffix(".json")
        sb.appendLine("Localization file: $lang")
        sb.appendLine("File: ${chunk.filePath}")

        var count = 0
        for ((key, value) in obj) {
            if (count >= 20) break
            val text = value.contentOrNull() ?: continue
            if (text.isNotBlank()) {
                sb.appendLine("  $key = $text")
                count++
            }
        }

        val total = obj.size
        if (total > 20) sb.appendLine("  ... and ${total - 20} more keys")
        return sb.toString().trim()
    }

    // ── Zone ─────────────────────────────────────────────────────

    private fun buildZoneText(chunk: GameDataChunk, obj: JsonObject): String {
        val sb = StringBuilder()
        sb.appendLine("Zone: ${chunk.name}")

        obj.str("Type", "type")?.let { sb.appendLine("Type: $it") }
        obj.str("Biome", "biome")?.let { sb.appendLine("Biome: $it") }
        obj.str("Region", "region")?.let { sb.appendLine("Region: $it") }
        obj.str("Climate", "climate")?.let { sb.appendLine("Climate: $it") }
        obj.str("Tileset", "tileset")?.let { sb.appendLine("Tileset: $it") }

        for (key in listOf("Layers", "layers")) {
            obj[key]?.jsonArrayOrNull()?.let { arr ->
                val layers = arr.mapNotNull {
                    it.jsonObjectOrNull()?.str("Name", "name") ?: it.contentOrNull()
                }
                if (layers.isNotEmpty()) sb.appendLine("Layers: ${layers.joinToString(", ")}")
                return@let
            }
        }

        for (key in listOf("Features", "features")) {
            obj[key]?.jsonArrayOrNull()?.let { arr ->
                val features = arr.mapNotNull {
                    it.jsonObjectOrNull()?.str("Type", "type") ?: it.contentOrNull()
                }
                if (features.isNotEmpty()) sb.appendLine("Features: ${features.joinToString(", ")}")
                return@let
            }
        }

        obj.str("Ambience", "ambience")?.let { sb.appendLine("Ambience: $it") }
        obj.str("Music", "music")?.let { sb.appendLine("Music: $it") }

        sb.appendLine("File: ${chunk.filePath}")
        return sb.toString().trim()
    }

    // ── Terrain Layer ────────────────────────────────────────────

    private fun buildTerrainLayerText(chunk: GameDataChunk, obj: JsonObject): String {
        val sb = StringBuilder()
        sb.appendLine("Terrain layer: ${chunk.name}")

        val blocks = extractBlocksFromLayerDef(obj)
        if (blocks.isNotEmpty()) sb.appendLine("Blocks: ${blocks.joinToString(", ")}")

        obj.str("Type", "type")?.let { sb.appendLine("Type: $it") }
        obj.int("Depth", "depth")?.let { sb.appendLine("Depth: $it") }
        obj.double("Density", "density")?.let { sb.appendLine("Density: $it") }
        obj.str("Material", "material")?.let { sb.appendLine("Material: $it") }

        val noise = obj.obj("Noise") ?: obj.obj("noise")
        noise?.let {
            it.str("Type", "type")?.let { t -> sb.appendLine("Noise type: $t") }
            it.double("Scale", "scale")?.let { s -> sb.appendLine("Noise scale: $s") }
        }

        sb.appendLine("File: ${chunk.filePath}")
        return sb.toString().trim()
    }

    // ── Cave ─────────────────────────────────────────────────────

    private fun buildCaveText(chunk: GameDataChunk, obj: JsonObject): String {
        val sb = StringBuilder()
        sb.appendLine("Cave: ${chunk.name}")

        obj.str("Type", "type")?.let { sb.appendLine("Type: $it") }
        obj.str("Biome", "biome")?.let { sb.appendLine("Biome: $it") }
        obj.int("MinDepth", "minDepth")?.let { sb.appendLine("Min depth: $it") }
        obj.int("MaxDepth", "maxDepth")?.let { sb.appendLine("Max depth: $it") }
        obj.double("Frequency", "frequency")?.let { sb.appendLine("Frequency: $it") }

        for (key in listOf("Blocks", "blocks")) {
            obj[key]?.jsonArrayOrNull()?.let { arr ->
                val blocks = arr.mapNotNull {
                    it.jsonObjectOrNull()?.str("Id", "id", "Block") ?: it.contentOrNull()
                }
                if (blocks.isNotEmpty()) sb.appendLine("Blocks: ${blocks.joinToString(", ")}")
                return@let
            }
        }

        for (key in listOf("Features", "features")) {
            obj[key]?.jsonArrayOrNull()?.let { arr ->
                val features = arr.mapNotNull {
                    it.jsonObjectOrNull()?.str("Type", "type") ?: it.contentOrNull()
                }
                if (features.isNotEmpty()) sb.appendLine("Features: ${features.joinToString(", ")}")
                return@let
            }
        }

        for (key in listOf("Ores", "ores")) {
            obj[key]?.jsonArrayOrNull()?.let { arr ->
                val ores = arr.mapNotNull {
                    it.jsonObjectOrNull()?.str("Id", "id") ?: it.contentOrNull()
                }
                if (ores.isNotEmpty()) sb.appendLine("Ores: ${ores.joinToString(", ")}")
                return@let
            }
        }

        for (key in listOf("Enemies", "enemies")) {
            obj[key]?.jsonArrayOrNull()?.let { arr ->
                val enemies = arr.mapNotNull {
                    it.jsonObjectOrNull()?.str("Id", "id") ?: it.contentOrNull()
                }
                if (enemies.isNotEmpty()) sb.appendLine("Enemies: ${enemies.joinToString(", ")}")
                return@let
            }
        }

        sb.appendLine("File: ${chunk.filePath}")
        return sb.toString().trim()
    }

    // ── Prefab (complete rewrite) ────────────────────────────────

    private fun buildPrefabText(chunk: GameDataChunk, obj: JsonObject): String {
        val sb = StringBuilder()
        sb.appendLine("Prefab: ${chunk.name}")

        // Iterate blocks[] array: count unique block names, track bounding box
        val blocksArr = obj.arr("blocks")
        if (blocksArr != null && blocksArr.isNotEmpty()) {
            val palette = mutableMapOf<String, Int>()
            var minX = Int.MAX_VALUE; var minY = Int.MAX_VALUE; var minZ = Int.MAX_VALUE
            var maxX = Int.MIN_VALUE; var maxY = Int.MIN_VALUE; var maxZ = Int.MIN_VALUE
            var totalBlocks = 0

            for (block in blocksArr) {
                val bo = block.jsonObjectOrNull() ?: continue
                totalBlocks++
                val name = bo.str("name") ?: "unknown"
                palette[name] = (palette[name] ?: 0) + 1

                bo.int("x")?.let { x -> if (x < minX) minX = x; if (x > maxX) maxX = x }
                bo.int("y")?.let { y -> if (y < minY) minY = y; if (y > maxY) maxY = y }
                bo.int("z")?.let { z -> if (z < minZ) minZ = z; if (z > maxZ) maxZ = z }
            }

            sb.appendLine("Total blocks: ${"%,d".format(totalBlocks)}")

            if (minX != Int.MAX_VALUE) {
                val w = maxX - minX + 1
                val h = maxY - minY + 1
                val d = maxZ - minZ + 1
                sb.appendLine("Bounding box: ${w}x${h}x${d}")
            }

            // Top 15 block types by count
            val sorted = palette.entries.sortedByDescending { it.value }
            val top = sorted.take(15).joinToString(", ") { "${it.key} x${"%,d".format(it.value)}" }
            sb.appendLine("Block palette: $top")
            if (sorted.size > 15) sb.appendLine("  ... and ${sorted.size - 15} more block types")
        }

        // Entities
        val entitiesArr = obj.arr("entities")
        if (entitiesArr != null && entitiesArr.isNotEmpty()) {
            val entityNames = mutableListOf<String>()
            for (entity in entitiesArr) {
                val eo = entity.jsonObjectOrNull() ?: continue
                // Try Components.Nameplate.Text
                val name = eo.obj("Components")?.let { comps ->
                    comps.obj("Nameplate")?.str("Text")
                        ?: comps.obj("SpawnMarkerComponent")?.str("SpawnMarker")
                        ?: comps.obj("NpcComponent")?.str("npcId")
                } ?: eo.str("EntityType", "type")
                ?: eo.str("name")
                if (name != null) entityNames.add(name)
            }
            if (entityNames.isNotEmpty()) {
                sb.appendLine("Entities (${entityNames.size}): ${entityNames.joinToString(", ")}")
            }
        }

        // Fluids
        val fluidsArr = obj.arr("fluids")
        if (fluidsArr != null && fluidsArr.isNotEmpty()) {
            val fluidCounts = mutableMapOf<String, Int>()
            for (fluid in fluidsArr) {
                val fo = fluid.jsonObjectOrNull() ?: continue
                val name = fo.str("name") ?: "unknown"
                fluidCounts[name] = (fluidCounts[name] ?: 0) + 1
            }
            if (fluidCounts.isNotEmpty()) {
                val fluidStr = fluidCounts.entries.joinToString(", ") { "${it.key} x${it.value}" }
                sb.appendLine("Fluids: $fluidStr")
            }
        }

        val tags = extractTagsFlexible(obj)
        if (tags.isNotEmpty()) sb.appendLine("Tags: ${tags.joinToString(", ")}")

        sb.appendLine("File: ${chunk.filePath}")
        return sb.toString().trim()
    }

    // ── Generic fallback ─────────────────────────────────────────

    private fun buildGenericText(chunk: GameDataChunk, obj: JsonObject): String {
        val sb = StringBuilder()
        sb.appendLine("${chunk.type.displayName}: ${chunk.name}")

        for ((key, value) in obj) {
            val text = when {
                value is JsonPrimitive -> value.content
                value is JsonArray -> value.mapNotNull { it.contentOrNull() }.joinToString(", ")
                else -> null
            }
            if (!text.isNullOrBlank() && key !in listOf("id", "name", "type", "Id", "Name", "Type")) {
                sb.appendLine("$key: $text")
            }
        }

        sb.appendLine("File: ${chunk.filePath}")
        return sb.toString().trim()
    }

    // ── Helpers ──────────────────────────────────────────────────

    private fun buildProjectileText(chunk: GameDataChunk, obj: JsonObject): String {
        val sb = StringBuilder()
        sb.appendLine("Projectile: ${chunk.name}")
        obj.str("Type", "type")?.let { sb.appendLine("Type: $it") }
        val damageEl = obj["Damage"]
        when (damageEl) {
            is JsonPrimitive -> damageEl.content.toIntOrNull()?.let { sb.appendLine("Damage: $it") }
            is JsonObject -> damageEl.int("Value", "value")?.let { sb.appendLine("Damage: $it") }
            else -> {}
        }
        obj.double("MuzzleVelocity", "muzzleVelocity")?.let { sb.appendLine("Muzzle velocity: $it") }
        obj.int("TimeToLive", "timeToLive")?.let { sb.appendLine("Time to live: ${it}s") }
        obj.double("Gravity", "gravity")?.let { sb.appendLine("Gravity: $it") }
        obj.double("Bounciness", "bounciness")?.let { sb.appendLine("Bounciness: $it") }
        obj.obj("HitParticles")?.str("SystemId", "systemId")?.let { sb.appendLine("Hit particle: $it") }
        obj.obj("DeathParticles")?.str("SystemId", "systemId")?.let { sb.appendLine("Death particle: $it") }
        sb.appendLine("Keywords: arrow bullet projectile ranged attack trajectory")
        sb.appendLine("File: ${chunk.filePath}")
        return sb.toString().trim()
    }

    private fun buildWeatherText(chunk: GameDataChunk, obj: JsonObject): String {
        val sb = StringBuilder()
        sb.appendLine("Weather: ${chunk.name}")
        val precipitation = obj.str("PrecipitationType") ?: obj.obj("Precipitation")?.str("Type", "type")
        precipitation?.let { sb.appendLine("Precipitation: $it") }
        obj.obj("Particle")?.str("SystemId", "systemId")?.let { sb.appendLine("Particle system: $it") }
        val tags = extractTagsFlexible(obj)
        if (tags.isNotEmpty()) sb.appendLine("Tags: ${tags.joinToString(", ")}")
        sb.appendLine("Keywords: weather precipitation rain snow blizzard fog storm")
        sb.appendLine("File: ${chunk.filePath}")
        return sb.toString().trim()
    }

    private fun buildWorldgenText(chunk: GameDataChunk, obj: JsonObject): String {
        val sb = StringBuilder()
        sb.appendLine("World gen: ${chunk.name}")
        obj.str("Type", "type")?.let { sb.appendLine("Type: $it") }
        val noise = obj.obj("Noise") ?: obj.obj("noise")
        val noiseType = noise?.str("Type", "type") ?: obj.str("NoiseType", "noiseType")
        noiseType?.let { sb.appendLine("Noise type: $it") }
        val fractalMode = noise?.str("FractalMode", "fractalMode") ?: obj.str("FractalMode", "fractalMode")
        fractalMode?.let { sb.appendLine("Fractal mode: $it") }
        val octaves = noise?.int("Octaves", "octaves") ?: obj.int("Octaves", "octaves")
        octaves?.let { sb.appendLine("Octaves: $it") }
        val scale = noise?.double("Scale", "scale") ?: obj.double("Scale", "scale")
        scale?.let { sb.appendLine("Scale: $it") }
        val threshold = noise?.double("Threshold", "threshold") ?: obj.double("Threshold", "threshold")
        threshold?.let { sb.appendLine("Threshold: $it") }
        for (key in listOf("Noises", "noises", "Inputs", "inputs")) {
            obj[key]?.jsonArrayOrNull()?.let { arr ->
                val subTypes = arr.mapNotNull { it.jsonObjectOrNull()?.str("Type", "type") }
                if (subTypes.isNotEmpty()) sb.appendLine("Sub-noise types: ${subTypes.joinToString(", ")}")
                return@let
            }
        }
        sb.appendLine("Keywords: world generation noise terrain fractal procedural")
        sb.appendLine("File: ${chunk.filePath}")
        return sb.toString().trim()
    }

    private fun buildObjectiveText(chunk: GameDataChunk, obj: JsonObject): String {
        val sb = StringBuilder()
        sb.appendLine("Objective: ${chunk.name}")
        val taskTypes = mutableSetOf<String>()
        for (key in listOf("TaskSets", "taskSets")) {
            obj[key]?.jsonArrayOrNull()?.forEach { setEl ->
                setEl.jsonObjectOrNull()?.let { taskSet ->
                    for (tKey in listOf("Tasks", "tasks")) {
                        taskSet[tKey]?.jsonArrayOrNull()?.forEach { taskEl ->
                            val task = taskEl.jsonObjectOrNull() ?: return@forEach
                            task.str("Type", "type")?.let { taskTypes.add(it) }
                            val npcId = task.str("NpcId", "npcId")
                            val locationId = task.str("LocationId", "locationId")
                            val count = task.int("Count", "count")
                            when {
                                npcId != null -> sb.appendLine("  Kill: $npcId${if (count != null) " x$count" else ""}")
                                locationId != null -> sb.appendLine("  Reach: $locationId")
                                else -> task.str("Type", "type")?.let { sb.appendLine("  Task: $it") }
                            }
                        }
                    }
                }
            }
        }
        if (taskTypes.isNotEmpty()) sb.appendLine("Task types: ${taskTypes.joinToString(", ")}")
        for (key in listOf("Completions", "completions")) {
            obj[key]?.jsonArrayOrNull()?.let { arr ->
                val types = arr.mapNotNull { it.jsonObjectOrNull()?.str("Type", "type") ?: it.contentOrNull() }
                if (types.isNotEmpty()) sb.appendLine("Completions: ${types.joinToString(", ")}")
                return@let
            }
        }
        sb.appendLine("Keywords: objective quest mission task kill bounty")
        sb.appendLine("File: ${chunk.filePath}")
        return sb.toString().trim()
    }

    private fun buildEnvironmentText(chunk: GameDataChunk, obj: JsonObject): String {
        val sb = StringBuilder()
        sb.appendLine("Environment: ${chunk.name}")
        obj.str("Type", "type")?.let { sb.appendLine("Type: $it") }
        val physics = obj.obj("Physics")
        val gravity = physics?.double("Gravity", "gravity") ?: obj.double("Gravity", "gravity")
        gravity?.let { sb.appendLine("Gravity: $it") }
        physics?.double("AirResistance", "airResistance")?.let { sb.appendLine("Air resistance: $it") }
        obj.obj("Lighting")?.double("AmbientLight", "ambientLight")?.let { sb.appendLine("Ambient light: $it") }
        for (key in listOf("Particles", "particles")) {
            obj[key]?.jsonArrayOrNull()?.let { arr ->
                val systems = arr.mapNotNull { it.jsonObjectOrNull()?.str("SystemId", "systemId") }
                if (systems.isNotEmpty()) sb.appendLine("Particle systems: ${systems.joinToString(", ")}")
                return@let
            }
        }
        sb.appendLine("Keywords: physics gravity lighting ambient environment")
        sb.appendLine("File: ${chunk.filePath}")
        return sb.toString().trim()
    }

    private fun buildCameraText(chunk: GameDataChunk, obj: JsonObject): String {
        val sb = StringBuilder()
        sb.appendLine("Camera: ${chunk.name}")
        obj.str("Mode", "CameraMode", "cameraMode")?.let { sb.appendLine("Mode: $it") }
        obj.double("FOV", "FieldOfView", "fieldOfView")?.let { sb.appendLine("Field of view: $it") }
        obj.obj("Offset")?.let { offset ->
            val x = offset.double("X", "x")
            val y = offset.double("Y", "y")
            val z = offset.double("Z", "z")
            if (x != null || y != null || z != null) {
                sb.appendLine("Offset: x=${x ?: 0}, y=${y ?: 0}, z=${z ?: 0}")
            }
        }
        obj.double("MinZoom", "minZoom")?.let { sb.appendLine("Min zoom: $it") }
        obj.double("MaxZoom", "maxZoom")?.let { sb.appendLine("Max zoom: $it") }
        sb.appendLine("Keywords: camera view perspective zoom offset FOV")
        sb.appendLine("File: ${chunk.filePath}")
        return sb.toString().trim()
    }

    private fun buildGameplayText(chunk: GameDataChunk, obj: JsonObject): String {
        val sb = StringBuilder()
        sb.appendLine("Gameplay config: ${chunk.name}")
        for ((key, value) in obj) {
            when {
                value is JsonPrimitive -> {
                    val text = value.content
                    if (text.isNotBlank() && key !in listOf("id", "name", "type", "Id", "Name", "Type")) {
                        sb.appendLine("$key: $text")
                    }
                }
                value is JsonArray -> {
                    val flat = value.mapNotNull { it.contentOrNull() }
                    if (flat.isNotEmpty() && key !in listOf("id", "name", "type", "Id", "Name", "Type")) {
                        sb.appendLine("$key: ${flat.joinToString(", ")}")
                    }
                }
                value is JsonObject -> {
                    val subParts = mutableListOf<String>()
                    for ((subKey, subVal) in value) {
                        if (subParts.size >= 5) break
                        val subText = (subVal as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() }
                            ?: (subVal as? JsonArray)?.mapNotNull { it.contentOrNull() }?.joinToString(", ")
                        if (!subText.isNullOrBlank()) subParts.add("  $subKey: $subText")
                    }
                    if (subParts.isNotEmpty()) {
                        sb.appendLine("$key:")
                        subParts.forEach { sb.appendLine(it) }
                    }
                }
                else -> {}
            }
        }
        sb.appendLine("Keywords: gameplay config balance setting parameter")
        sb.appendLine("File: ${chunk.filePath}")
        return sb.toString().trim()
    }

    /**
     * Recursively extract item/block IDs from a nested drop/loot structure.
     */
    fun extractBlocksDeep(element: JsonElement?): List<String> {
        if (element == null) return emptyList()
        val results = mutableListOf<String>()
        extractBlocksDeepInto(element, results)
        return results
    }

    private fun extractBlocksDeepInto(element: JsonElement, out: MutableList<String>) {
        when (element) {
            is JsonPrimitive -> {
                val s = element.content
                if (s.isNotBlank()) out.add(s)
            }
            is JsonArray -> {
                for (child in element) extractBlocksDeepInto(child, out)
            }
            is JsonObject -> {
                val id = element.str("ItemId", "item", "id", "Id", "block", "Block")
                if (id != null) {
                    out.add(id)
                } else {
                    for ((_, v) in element) extractBlocksDeepInto(v, out)
                }
            }
        }
    }

    /**
     * Extract block IDs from a terrain layer definition.
     * Handles both flat arrays and nested layerDef structures.
     */
    fun extractBlocksFromLayerDef(obj: JsonObject): List<String> {
        val results = mutableListOf<String>()
        for (key in listOf("blocks", "Blocks", "topBlock", "TopBlock", "fillerBlock", "FillerBlock",
                           "underwaterBlock", "UnderwaterBlock", "block", "Block")) {
            obj[key]?.let { extractBlocksDeepInto(it, results) }
        }
        for (key in listOf("layers", "Layers")) {
            obj[key]?.jsonArrayOrNull()?.forEach { layer ->
                layer.jsonObjectOrNull()?.let { extractBlocksDeepInto(it, results) }
            }
        }
        return results.distinct()
    }

    /**
     * Extract tags flexibly: handles both flat array `["tag"]` and
     * Hytale's object form `{Type:["Furniture"], Family:["Human"]}`.
     * Flattens object form to `["Type:Furniture", "Family:Human"]`.
     */
    private fun extractTagsFlexible(obj: JsonObject): List<String> {
        val tags = mutableListOf<String>()
        val tagsEl = obj["Tags"] ?: obj["tags"] ?: return tags

        when (tagsEl) {
            is JsonArray -> {
                for (el in tagsEl) {
                    el.contentOrNull()?.let { tags.add(it) }
                }
            }
            is JsonObject -> {
                for ((category, values) in tagsEl) {
                    when (values) {
                        is JsonArray -> {
                            for (v in values) {
                                v.contentOrNull()?.let { tags.add("$category:$it") }
                            }
                        }
                        is JsonPrimitive -> {
                            values.content.takeIf { it.isNotBlank() }?.let { tags.add("$category:$it") }
                        }
                        else -> {}
                    }
                }
            }
            else -> {}
        }
        return tags
    }

    /**
     * Recursively traverse Hytale's hierarchical drop Container structure.
     * Container types: Multiple → Choice → Single → Item{ItemId, QuantityMin, QuantityMax}
     */
    private fun extractContainerItems(container: JsonObject, out: MutableList<String>) {
        // Direct item entry: Item: {ItemId: "...", QuantityMin, QuantityMax}
        val itemObj = container.obj("Item")
        if (itemObj != null) {
            itemObj.str("ItemId")?.let { itemId ->
                val min = itemObj.int("QuantityMin") ?: 1
                val max = itemObj.int("QuantityMax") ?: min
                val entry = if (min == max) "$itemId x$min" else "$itemId x$min-$max"
                out.add(entry)
                return
            }
        }

        // Top-level ItemId (alternative flat format)
        container.str("ItemId")?.let { itemId ->
            val min = container.int("QuantityMin") ?: 1
            val max = container.int("QuantityMax") ?: min
            val chance = container.double("Chance")
            val entry = if (min == max) "$itemId x$min" else "$itemId x$min-$max"
            out.add(if (chance != null) "$entry (${(chance * 100).toInt()}%)" else entry)
            return
        }

        // Recurse into sub-containers (Hytale uses "Containers" array)
        for (key in listOf("Containers", "Multiple", "Choice", "Single", "Items", "Entries")) {
            val sub = container[key]
            when (sub) {
                is JsonArray -> {
                    for (el in sub) {
                        el.jsonObjectOrNull()?.let { extractContainerItems(it, out) }
                    }
                }
                is JsonObject -> extractContainerItems(sub, out)
                else -> {}
            }
        }

        // Also check nested Container sub-object
        container.obj("Container")?.let { extractContainerItems(it, out) }
    }

    // ── JsonObject extension helpers (variadic) ──────────────────

    private fun JsonObject.str(vararg keys: String): String? {
        for (key in keys) {
            val v = this[key]?.jsonPrimitiveOrNull()?.takeIf { it.isString }?.content?.takeIf { it.isNotBlank() }
            if (v != null) return v
        }
        return null
    }

    private fun JsonObject.int(vararg keys: String): Int? {
        for (key in keys) {
            val v = this[key]?.jsonPrimitiveOrNull()?.content?.toIntOrNull()
            if (v != null) return v
        }
        return null
    }

    private fun JsonObject.double(vararg keys: String): Double? {
        for (key in keys) {
            val v = this[key]?.jsonPrimitiveOrNull()?.content?.toDoubleOrNull()
            if (v != null) return v
        }
        return null
    }

    private fun JsonObject.bool(vararg keys: String): Boolean? {
        for (key in keys) {
            val v = this[key]?.jsonPrimitiveOrNull()?.content?.toBooleanStrictOrNull()
            if (v != null) return v
        }
        return null
    }

    private fun JsonObject.arr(vararg keys: String): JsonArray? {
        for (key in keys) {
            val v = this[key]?.jsonArrayOrNull()
            if (v != null) return v
        }
        return null
    }

    private fun JsonObject.obj(vararg keys: String): JsonObject? {
        for (key in keys) {
            val v = this[key]?.jsonObjectOrNull()
            if (v != null) return v
        }
        return null
    }

    private fun JsonElement.jsonPrimitiveOrNull(): JsonPrimitive? = this as? JsonPrimitive

    private fun JsonElement.jsonObjectOrNull(): JsonObject? = this as? JsonObject

    private fun JsonElement.jsonArrayOrNull(): JsonArray? = this as? JsonArray

    private fun JsonElement.contentOrNull(): String? =
        (this as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() }
}
