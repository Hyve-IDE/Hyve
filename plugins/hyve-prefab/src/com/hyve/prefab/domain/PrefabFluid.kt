package com.hyve.prefab.domain

/**
 * Summary information about fluids in a prefab.
 * We don't parse individual fluids — just count and type information for the stats bar.
 */
data class PrefabFluidSummary(
    val count: Int,
    val typeNames: Set<String>,
)
