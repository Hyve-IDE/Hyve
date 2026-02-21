package com.hyve.prefab.state

import com.hyve.common.undo.UndoableCommand
import com.hyve.prefab.domain.*
import kotlinx.serialization.json.*

/**
 * Commands for modifying a [PrefabDocument] with undo/redo support.
 */
sealed interface PrefabCommand : UndoableCommand<PrefabDocument>

/**
 * Set a field value within an entity's component.
 *
 * Supports merge for rapid edits to the same field (e.g., typing in a text field).
 */
data class SetComponentFieldCommand(
    val entityId: EntityId,
    val componentKey: ComponentTypeKey,
    val fieldPath: String,
    val oldValue: JsonElement,
    val newValue: JsonElement,
) : PrefabCommand {

    override val description: String
        get() = "Set $fieldPath in ${componentKey.value}"

    override fun execute(state: PrefabDocument): PrefabDocument? {
        val entity = state.findEntityById(entityId) ?: return null
        val component = entity.components[componentKey] ?: return null
        val updatedComponent = setFieldInObject(component, fieldPath, newValue) ?: return null
        val newComponents = LinkedHashMap(entity.components)
        newComponents[componentKey] = updatedComponent
        val updatedEntity = entity.copy(
            components = newComponents,
            rawJson = entity.copy(components = newComponents).toJsonObject(),
        )
        return state.updateEntity(updatedEntity)
    }

    override fun undo(state: PrefabDocument): PrefabDocument? {
        val entity = state.findEntityById(entityId) ?: return null
        val component = entity.components[componentKey] ?: return null
        val updatedComponent = setFieldInObject(component, fieldPath, oldValue) ?: return null
        val newComponents = LinkedHashMap(entity.components)
        newComponents[componentKey] = updatedComponent
        val updatedEntity = entity.copy(
            components = newComponents,
            rawJson = entity.copy(components = newComponents).toJsonObject(),
        )
        return state.updateEntity(updatedEntity)
    }

    override fun canMergeWith(other: UndoableCommand<PrefabDocument>): Boolean {
        if (other !is SetComponentFieldCommand) return false
        return entityId == other.entityId &&
            componentKey == other.componentKey &&
            fieldPath == other.fieldPath
    }

    override fun mergeWith(other: UndoableCommand<PrefabDocument>): UndoableCommand<PrefabDocument>? {
        if (other !is SetComponentFieldCommand) return null
        // Keep old value from this command, new value from the other
        return copy(newValue = other.newValue)
    }

    companion object {
        /**
         * Set a field in a JsonObject, supporting dot-separated paths for nested fields.
         */
        fun setFieldInObject(obj: JsonObject, path: String, value: JsonElement): JsonObject? {
            val parts = path.split(".")
            if (parts.size == 1) {
                return buildJsonObject {
                    for ((key, v) in obj) {
                        if (key == path) {
                            put(key, value)
                        } else {
                            put(key, v)
                        }
                    }
                    if (path !in obj) {
                        put(path, value)
                    }
                }
            }

            // Nested path: recurse into sub-object
            val firstKey = parts[0]
            val restPath = parts.drop(1).joinToString(".")
            val subObj = obj[firstKey]
            if (subObj !is JsonObject) return null
            val updatedSub = setFieldInObject(subObj, restPath, value) ?: return null
            return buildJsonObject {
                for ((key, v) in obj) {
                    if (key == firstKey) {
                        put(key, updatedSub)
                    } else {
                        put(key, v)
                    }
                }
            }
        }
    }
}

/**
 * Add a new entity to the document.
 */
data class AddEntityCommand(
    val entity: PrefabEntity,
) : PrefabCommand {

    override val description: String
        get() = "Add ${entity.displayName}"

    override fun execute(state: PrefabDocument): PrefabDocument =
        state.addEntity(entity)

    override fun undo(state: PrefabDocument): PrefabDocument =
        state.removeEntity(entity.id)
}

/**
 * Delete an entity from the document.
 */
data class DeleteEntityCommand(
    val entity: PrefabEntity,
    val index: Int,
) : PrefabCommand {

    override val description: String
        get() = "Delete ${entity.displayName}"

    override fun execute(state: PrefabDocument): PrefabDocument =
        state.removeEntity(entity.id)

    override fun undo(state: PrefabDocument): PrefabDocument {
        val newEntities = state.entities.toMutableList()
        val insertAt = index.coerceIn(0, newEntities.size)
        newEntities.add(insertAt, entity)
        return state.copy(entities = newEntities)
    }
}
