// Copyright 2026 Hyve. All rights reserved.
package com.hyve.knowledge.core.search

import kotlinx.serialization.Serializable

@Serializable
data class SearchResult(
    val nodeId: String,
    val displayName: String,
    val snippet: String,
    val filePath: String,
    val lineStart: Int,
    val score: Double,
    val source: ResultSource,
    val dataType: String? = null,
    val corpus: String = "code",
    val bridgedFrom: String? = null,
    val bridgeEdgeType: String? = null,
    val connectedNodeIds: List<String> = emptyList(),
    @kotlinx.serialization.Transient
    val expandedFromNodeId: String? = null,
)

@Serializable
enum class ResultSource { VECTOR, GRAPH, HYBRID }

@Serializable
data class IndexStats(
    val corpus: String = "code",
    val nodeCount: Int = 0,
    val typeBreakdown: Map<String, Int> = emptyMap(),
    val edgeCount: Int = 0,
    val vectorIndexLoaded: Boolean = false,
) {
    val methodCount: Int get() = typeBreakdown["JavaMethod"] ?: 0
    val classCount: Int get() = typeBreakdown["JavaClass"] ?: 0
}

enum class QueryStrategy { VECTOR, GRAPH, HYBRID }

data class RouteResult(
    val strategy: QueryStrategy,
    val entityName: String? = null,
    val relation: String? = null,
)
