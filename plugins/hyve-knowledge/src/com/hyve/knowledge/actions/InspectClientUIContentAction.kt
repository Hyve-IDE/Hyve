// Copyright 2026 Hyve. All rights reserved.
package com.hyve.knowledge.actions

import com.hyve.knowledge.bridge.KnowledgeDatabaseFactory
import com.hyve.knowledge.index.UIContentAnalyzer
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.diagnostic.Logger

/**
 * Diagnostic IDE action that inspects client UI (.ui) content from the indexed DB,
 * runs [UIContentAnalyzer] strategies, cross-references candidates against gamedata
 * display_names, and logs a summary report.
 *
 * Used during Phase A to validate extraction strategies before committing to edge production.
 * Can be kept as a dev tool or removed later.
 */
class InspectClientUIContentAction : AnAction() {

    private val log = Logger.getInstance(InspectClientUIContentAction::class.java)

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val db = KnowledgeDatabaseFactory.getInstance()
        val analyzer = UIContentAnalyzer()

        // 1. Query all client corpus nodes where node_type = 'ui'
        val uiNodes = db.query(
            "SELECT id, display_name, content FROM nodes WHERE corpus = 'client' AND node_type = 'ui'",
        ) { rs ->
            Triple(
                rs.getString("id"),
                rs.getString("display_name") ?: "",
                rs.getString("content") ?: "",
            )
        }

        if (uiNodes.isEmpty()) {
            log.info("InspectClientUI: No .ui nodes found in client corpus. Run 'Index Client UI' first.")
            return
        }

        // 2. Build gamedata display_name lookup
        val gamedataNames = db.query(
            "SELECT DISTINCT display_name FROM nodes WHERE corpus = 'gamedata' AND display_name IS NOT NULL",
        ) { rs -> rs.getString("display_name") }
            .toSet()

        val gamedataNamesLower = gamedataNames.associateBy { it.lowercase() }

        // 3. Run analyzer on each .ui node and cross-reference
        var totalCandidates = 0
        var totalMatches = 0
        val matchesByStrategy = mutableMapOf<String, Int>()
        val candidatesByStrategy = mutableMapOf<String, Int>()
        val sampleMatches = mutableListOf<String>()

        for ((nodeId, displayName, content) in uiNodes) {
            if (content.isBlank()) continue

            val candidates = analyzer.analyze(content, nodeId)
            totalCandidates += candidates.size

            for (candidate in candidates) {
                candidatesByStrategy.merge(candidate.strategy, 1, Int::plus)

                // Cross-reference against gamedata display_names (case-insensitive)
                val matched = gamedataNamesLower[candidate.candidateText.lowercase()]
                if (matched != null) {
                    totalMatches++
                    matchesByStrategy.merge(candidate.strategy, 1, Int::plus)
                    if (sampleMatches.size < 20) {
                        sampleMatches.add(
                            "[${candidate.strategy}] $displayName -> $matched (conf=${candidate.confidence})",
                        )
                    }
                }
            }
        }

        // 4. Log summary report
        val report = buildString {
            appendLine("=== Client UI Content Inspection Report ===")
            appendLine("UI nodes scanned: ${uiNodes.size}")
            appendLine("Gamedata names available: ${gamedataNames.size}")
            appendLine("Total candidates extracted: $totalCandidates")
            appendLine("Total matches against gamedata: $totalMatches")
            appendLine()
            appendLine("--- Candidates per strategy ---")
            for ((strategy, count) in candidatesByStrategy.entries.sortedByDescending { it.value }) {
                val matches = matchesByStrategy[strategy] ?: 0
                val hitRate = if (count > 0) "%.1f%%".format(100.0 * matches / count) else "N/A"
                appendLine("  $strategy: $count candidates, $matches matches ($hitRate hit rate)")
            }
            appendLine()
            appendLine("--- Sample matches (up to 20) ---")
            for (sample in sampleMatches) {
                appendLine("  $sample")
            }
            if (sampleMatches.isEmpty()) {
                appendLine("  (no matches found)")
            }
        }

        log.info(report)
    }

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = e.project != null
    }
}
