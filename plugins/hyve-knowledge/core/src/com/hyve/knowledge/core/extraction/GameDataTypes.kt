// Copyright 2026 Hyve. All rights reserved.
package com.hyve.knowledge.core.extraction

enum class GameDataType(val id: String, val displayName: String) {
    ITEM("item", "Item"),
    RECIPE("recipe", "Recipe"),
    BLOCK("block", "Block"),
    INTERACTION("interaction", "Interaction"),
    DROP("drop", "Drop"),
    NPC("npc", "NPC"),
    NPC_GROUP("npc_group", "NPC Group"),
    NPC_AI("npc_ai", "NPC AI"),
    ENTITY("entity", "Entity"),
    PROJECTILE("projectile", "Projectile"),
    FARMING("farming", "Farming"),
    SHOP("shop", "Shop"),
    ENVIRONMENT("environment", "Environment"),
    WEATHER("weather", "Weather"),
    BIOME("biome", "Biome"),
    WORLDGEN("worldgen", "World Gen"),
    CAMERA("camera", "Camera"),
    OBJECTIVE("objective", "Objective"),
    GAMEPLAY("gameplay", "Gameplay"),
    LOCALIZATION("localization", "Localization"),
    ZONE("zone", "Zone"),
    TERRAIN_LAYER("terrain_layer", "Terrain Layer"),
    CAVE("cave", "Cave"),
    PREFAB("prefab", "Prefab");

    companion object {
        fun fromId(id: String): GameDataType? = entries.find { it.id == id }
    }
}

data class GameDataChunk(
    val id: String,
    val type: GameDataType,
    val name: String,
    val filePath: String,
    val fileHash: String,
    val rawJson: String,
    val tags: List<String>,
    val relatedIds: List<String>,
    val textForEmbedding: String,
)
