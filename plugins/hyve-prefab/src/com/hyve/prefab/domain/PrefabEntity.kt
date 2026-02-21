package com.hyve.prefab.domain

import kotlinx.serialization.json.*

/**
 * A single entity in a prefab file.
 *
 * Each entity has an EntityType and a set of components (Transform, BlockEntity, LootTable, etc.).
 * The [rawJson] preserves the full original JSON for round-trip safety.
 */
data class PrefabEntity(
    val id: EntityId,
    val entityType: String,
    val components: LinkedHashMap<ComponentTypeKey, JsonObject>,
    val rawJson: JsonObject,
    /** True if the original JSON uses the {"Components": {...}} wrapper format. */
    val usesComponentsWrapper: Boolean = false,
    /** Non-null for blocks in the blocks array that carry component data. */
    val blockOrigin: BlockOrigin? = null,
    /** Byte range of this entity's JSON object `{...}` in the original file. Null for component blocks. */
    val sourceByteRange: IntRange? = null,
) {
    /** True if this entity originated from a block with component data, not the entities array. */
    val isComponentBlock: Boolean get() = blockOrigin != null
    /** Display name, computed once at construction — stable across all calls. */
    val displayName: String = computeDisplayName()

    /** Position, computed once at construction — stable across all calls. */
    val position: Vector3d? = computePosition()

    private fun computePosition(): Vector3d? {
        if (blockOrigin != null) {
            val bp = blockOrigin.blockPos
            return Vector3d(bp.x.toDouble(), bp.y.toDouble(), bp.z.toDouble())
        }
        val transform = components[ComponentTypeKey("Transform")] ?: return null
        val position = transform["Position"]?.jsonObject ?: return null
        return try {
            Vector3d(
                x = (position["X"] ?: position["x"])?.jsonPrimitive?.double ?: 0.0,
                y = (position["Y"] ?: position["y"])?.jsonPrimitive?.double ?: 0.0,
                z = (position["Z"] ?: position["z"])?.jsonPrimitive?.double ?: 0.0,
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun computeDisplayName(): String {
        // Component blocks use their block name directly
        if (blockOrigin != null) return blockOrigin.blockName

        // Flat format with explicit EntityType
        if (entityType != "Unknown") {
            if (entityType == "BlockEntity") {
                val blockEntity = components[ComponentTypeKey("BlockEntity")]
                val blockType = (blockEntity?.get("BlockTypeKey") ?: blockEntity?.get("blockTypeKey"))
                    ?.jsonPrimitive?.contentOrNull
                if (blockType != null) return "BlockEntity: $blockType"
            }
            return entityType
        }

        // Wrapped format — derive from component data in priority order

        // 1. Nameplate.Text — best name for spawn markers (e.g., "Skeleton_Frost_Knight")
        val nameplate = components[ComponentTypeKey("Nameplate")]
        val nameplateText = nameplate?.get("Text")?.jsonPrimitive?.contentOrNull
        if (nameplateText != null) return nameplateText

        // 2. BlockEntity.BlockTypeKey — identity for block/prop entities
        val blockEntity = components[ComponentTypeKey("BlockEntity")]
        val blockType = (blockEntity?.get("BlockTypeKey") ?: blockEntity?.get("blockTypeKey"))
            ?.jsonPrimitive?.contentOrNull
        if (blockType != null) return blockType

        // 3. Item.Item.Id — identity for item entities
        val item = components[ComponentTypeKey("Item")]
        val itemObj = item?.get("Item")
        if (itemObj is JsonObject) {
            val itemId = itemObj["Id"]?.jsonPrimitive?.contentOrNull
            if (itemId != null) return itemId
        }

        // 4. Model.Model.Id — identity for model-based entities
        val model = components[ComponentTypeKey("Model")]
        val modelObj = model?.get("Model")
        if (modelObj is JsonObject) {
            val modelId = modelObj["Id"]?.jsonPrimitive?.contentOrNull
            if (modelId != null) return modelId
        }

        // 5. SpawnMarkerComponent.SpawnMarker — fallback for markers without nameplate
        val spawnMarker = components[ComponentTypeKey("SpawnMarkerComponent")]
        val spawnName = spawnMarker?.get("SpawnMarker")?.jsonPrimitive?.contentOrNull
        if (spawnName != null) return spawnName

        // 6. First meaningful component key
        val meaningfulKeys = components.keys.map { it.value }
            .filter { it !in NOISE_COMPONENT_KEYS }
        if (meaningfulKeys.isNotEmpty()) return meaningfulKeys.first()

        return "Entity"
    }

    /**
     * Reconstruct the JSON object from the components map,
     * preserving the original wrapper format.
     */
    fun toJsonObject(): JsonObject = buildJsonObject {
        if (usesComponentsWrapper) {
            put("Components", buildJsonObject {
                for ((key, value) in components) {
                    put(key.value, value)
                }
            })
        } else {
            put("EntityType", JsonPrimitive(entityType))
            for ((key, value) in components) {
                put(key.value, value)
            }
        }
    }

    companion object {
        /** Component keys that are utility/noise and shouldn't be used as display names. */
        private val NOISE_COMPONENT_KEYS = setOf(
            "Transform", "UUID", "PrefabCopyable", "HeadRotation",
            "Intangible", "HiddenFromAdventurePlayer", "PreventPickup",
            "PreventItemMerging", "Prop", "WorldGenId", "RefId",
            "EntityScale",
        )

        /**
         * Parse a JSON object into a PrefabEntity.
         * Supports both formats:
         * - Flat: {"EntityType": "...", "Transform": {...}, ...}
         * - Wrapped: {"Components": {"Transform": {...}, ...}}
         */
        fun fromJsonObject(json: JsonObject, id: EntityId): PrefabEntity {
            val componentsObj = json["Components"]
            if (componentsObj is JsonObject) {
                // Wrapped format: {"Components": {"Transform": {...}, ...}}
                val components = LinkedHashMap<ComponentTypeKey, JsonObject>()
                for ((key, value) in componentsObj) {
                    if (value is JsonObject) {
                        components[ComponentTypeKey(key)] = value
                    }
                }
                return PrefabEntity(
                    id = id,
                    entityType = "Unknown",
                    components = components,
                    rawJson = json,
                    usesComponentsWrapper = true,
                )
            }

            // Flat format: {"EntityType": "...", "Transform": {...}, ...}
            val entityType = json["EntityType"]?.jsonPrimitive?.contentOrNull ?: "Unknown"
            val components = LinkedHashMap<ComponentTypeKey, JsonObject>()
            for ((key, value) in json) {
                if (key == "EntityType") continue
                if (value is JsonObject) {
                    components[ComponentTypeKey(key)] = value
                }
            }
            return PrefabEntity(
                id = id,
                entityType = entityType,
                components = components,
                rawJson = json,
            )
        }
    }
}
