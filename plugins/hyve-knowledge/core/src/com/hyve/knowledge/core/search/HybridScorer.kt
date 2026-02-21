// Copyright 2026 Hyve. All rights reserved.
package com.hyve.knowledge.core.search

object HybridScorer {

    private const val K = 60

    fun mergeRRF(
        vararg resultLists: List<SearchResult>,
        limit: Int = 10,
    ): List<SearchResult> {
        val scores = mutableMapOf<String, Pair<Double, SearchResult>>()

        for (results in resultLists) {
            for ((rank, result) in results.withIndex()) {
                val rrfScore = 1.0 / (K + rank + 1)
                val existing = scores[result.nodeId]
                if (existing == null) {
                    scores[result.nodeId] = Pair(rrfScore, result.copy(source = ResultSource.HYBRID))
                } else {
                    val newScore = existing.first + rrfScore
                    scores[result.nodeId] = Pair(newScore, existing.second.copy(score = newScore))
                }
            }
        }

        return scores.values
            .sortedByDescending { it.first }
            .take(limit)
            .map { it.second.copy(score = it.first) }
    }
}
