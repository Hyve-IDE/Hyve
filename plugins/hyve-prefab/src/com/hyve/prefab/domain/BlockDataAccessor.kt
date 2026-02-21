package com.hyve.prefab.domain

/**
 * Opaque accessor for block data in a prefab.
 * In the MVP, we only track summary statistics — blocks are preserved as raw bytes.
 */
interface BlockDataAccessor {
    val blockCount: Int
    val blockTypeCounts: Map<String, Int>
}

/**
 * Block data summary gathered via streaming parse (no full materialization).
 */
class StreamedBlockData(
    override val blockCount: Int,
    override val blockTypeCounts: Map<String, Int>,
) : BlockDataAccessor
