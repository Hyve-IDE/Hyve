// Copyright 2026 Hyve. All rights reserved.
package com.hyve.knowledge.core.search

import com.hyve.knowledge.core.extraction.GameDataType
import com.hyve.knowledge.core.extraction.GameDataType.*
import kotlinx.serialization.Serializable

object SystemClassMapping {

    @Serializable
    data class SystemInfo(
        val classes: List<String>,
        val description: String,
    )

    private val SYSTEM_MAP: Map<GameDataType, SystemInfo> = mapOf(
        ITEM to SystemInfo(
            listOf("Item", "ItemCategory", "ItemArmor", "ItemGroup"),
            "Item definitions, categories, equipment stats, and grouping",
        ),
        RECIPE to SystemInfo(
            listOf("CraftingRecipe", "BenchRecipeRegistry", "CraftingConfig"),
            "Crafting recipe system: inputs, outputs, bench requirements, timing",
        ),
        DROP to SystemInfo(
            listOf("ItemDropContainer", "SingleItemDropContainer", "MultipleItemDropContainer",
                   "ChoiceItemDropContainer", "ItemDropList"),
            "Hierarchical loot drop system with weighted random selection",
        ),
        BLOCK to SystemInfo(
            listOf("BlockType"),
            "Block definitions: materials, hardness, light emission, farming data",
        ),
        NPC to SystemInfo(
            listOf("NPCConfig", "NPCEntity", "NPCGroup"),
            "NPC configuration, entity component, and group spawning",
        ),
        NPC_GROUP to SystemInfo(
            listOf("NPCGroup", "EntityFilterNPCGroup"),
            "NPC group spawning and faction configuration",
        ),
        NPC_AI to SystemInfo(
            listOf("MotionController"),
            "NPC AI state machine, motion control, and behavior",
        ),
        INTERACTION to SystemInfo(
            listOf("InteractionManager", "InteractionChain"),
            "Interaction execution: chains, conditions, effects",
        ),
        SHOP to SystemInfo(
            listOf("BarterShopAsset", "BarterTrade", "NPCShopPlugin"),
            "Barter shop system, trade slots, NPC shop behavior",
        ),
        FARMING to SystemInfo(
            listOf("FarmingData", "FarmingBlock", "FarmingStageData"),
            "Farming tick logic, growth stages, crop modifiers",
        ),
        BIOME to SystemInfo(
            listOf("BiomeAsset", "CustomBiomeGenerator"),
            "Biome configuration and terrain generation",
        ),
        PROJECTILE to SystemInfo(
            listOf("Projectile", "ProjectileConfig"),
            "Projectile physics and configuration",
        ),
        WEATHER to SystemInfo(
            listOf("Weather"),
            "Weather system",
        ),
        ENTITY to SystemInfo(
            listOf("Entity"),
            "Entity definitions and component system",
        ),
        CAVE to SystemInfo(
            listOf("CavePrefab", "CavePrefabConfig"),
            "Cave generation prefabs and configuration",
        ),
        ENVIRONMENT to SystemInfo(
            listOf("Environment"),
            "Environment lighting and atmosphere",
        ),
        WORLDGEN to SystemInfo(
            listOf("ChunkGenerator", "WorldGenType", "NStagedChunkGenerator"),
            "World generation pipeline: chunk generation, biome placement",
        ),
        CAMERA to SystemInfo(
            listOf("CameraShakeConfig", "CameraPlugin"),
            "Camera effects and configuration",
        ),
        PREFAB to SystemInfo(
            listOf("PrefabLoader", "RecursivePrefabLoader", "UniquePrefabConfiguration"),
            "Prefab loading and placement in world generation",
        ),
        OBJECTIVE to SystemInfo(
            listOf("KillObjectiveTaskAsset", "NPCObjectivesPlugin"),
            "Quest/objective task definitions",
        ),
        ZONE to SystemInfo(
            listOf("ZonesJsonLoader", "DiscoverZoneEvent"),
            "Zone definitions and discovery events",
        ),
    )

    fun forDataTypes(types: Set<String>): Map<String, SystemInfo> {
        return types.mapNotNull { typeId ->
            val type = GameDataType.fromId(typeId) ?: return@mapNotNull null
            val info = SYSTEM_MAP[type] ?: return@mapNotNull null
            typeId to info
        }.toMap()
    }
}
