package com.hyve.prefab.domain

@JvmInline
value class EntityId(val value: Long)

@JvmInline
value class ComponentTypeKey(val value: String)

data class BlockPos(val x: Int, val y: Int, val z: Int) {
    override fun toString(): String = "($x, $y, $z)"
}

data class Vector3d(val x: Double, val y: Double, val z: Double) {
    override fun toString(): String = "(%.2f, %.2f, %.2f)".format(x, y, z)
}

/**
 * Origin metadata for blocks in the blocks array that carry component data
 * (e.g., furniture chests with inventory/loot components).
 */
data class BlockOrigin(val blockName: String, val blockPos: BlockPos)
