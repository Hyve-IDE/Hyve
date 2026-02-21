package com.hyve.prefab.domain

/**
 * Immutable representation of a parsed .prefab.json file.
 *
 * Uses copy-on-write semantics — entity modifications return new instances.
 * Block and fluid data are preserved as raw bytes via [entitiesByteRange],
 * allowing byte-splice export that only re-serializes the entities array.
 */
data class PrefabDocument(
    val version: Int,
    val blockIdVersion: Int,
    val anchor: BlockPos,
    val entities: List<PrefabEntity>,
    val fluidSummary: PrefabFluidSummary,
    val blockData: BlockDataAccessor,
    val rawBytes: ByteArray,
    /** Byte offset range of the entities JSON array value (including [ and ]) in [rawBytes]. */
    val entitiesByteRange: IntRange,
    val entityIdCounter: Long,
    /** Blocks from the blocks array that carry component data (e.g., chests with inventory). */
    val componentBlocks: List<PrefabEntity> = emptyList(),
) {
    /** All displayable entities: entities array + component blocks. */
    val allEntities: List<PrefabEntity> get() = entities + componentBlocks

    fun findEntityById(id: EntityId): PrefabEntity? =
        entities.find { it.id == id } ?: componentBlocks.find { it.id == id }

    fun updateEntity(updated: PrefabEntity): PrefabDocument {
        val newEntities = entities.map { if (it.id == updated.id) updated else it }
        return copy(entities = newEntities)
    }

    fun addEntity(entity: PrefabEntity): PrefabDocument =
        copy(
            entities = entities + entity,
            entityIdCounter = entityIdCounter + 1,
        )

    fun removeEntity(id: EntityId): PrefabDocument =
        copy(entities = entities.filter { it.id != id })

    fun nextEntityId(): EntityId = EntityId(entityIdCounter)

    // ByteArray doesn't have structural equals/hashCode, override for correctness
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PrefabDocument) return false
        return version == other.version &&
            blockIdVersion == other.blockIdVersion &&
            anchor == other.anchor &&
            entities == other.entities &&
            componentBlocks == other.componentBlocks &&
            entitiesByteRange == other.entitiesByteRange &&
            entityIdCounter == other.entityIdCounter
    }

    override fun hashCode(): Int {
        var result = version
        result = 31 * result + blockIdVersion
        result = 31 * result + anchor.hashCode()
        result = 31 * result + entities.hashCode()
        result = 31 * result + componentBlocks.hashCode()
        result = 31 * result + entitiesByteRange.hashCode()
        result = 31 * result + entityIdCounter.hashCode()
        return result
    }
}
