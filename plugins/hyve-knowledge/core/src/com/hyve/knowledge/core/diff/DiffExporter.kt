// Copyright 2026 Hyve. All rights reserved.
package com.hyve.knowledge.core.diff

/**
 * Exports a [VersionDiff] to Markdown format.
 */
object DiffExporter {

    fun toMarkdown(diff: VersionDiff): String = buildString {
        appendLine("# Version Diff: ${diff.versionA} vs ${diff.versionB}")
        appendLine()
        appendLine("Computed: ${diff.computedAt}")
        appendLine()

        // Summary
        appendLine("## Summary")
        appendLine()
        appendLine("| | Added | Removed | Changed |")
        appendLine("|---|---|---|---|")
        appendLine("| **Total** | ${diff.summary.totalAdded} | ${diff.summary.totalRemoved} | ${diff.summary.totalChanged} |")
        for ((corpus, stats) in diff.summary.byCorpus) {
            appendLine("| $corpus | ${stats.added} | ${stats.removed} | ${stats.changed} |")
        }
        if (diff.summary.skippedCorpora.isNotEmpty()) {
            appendLine("**Skipped corpora:**")
            for ((corpus, reason) in diff.summary.skippedCorpora) {
                appendLine("- $corpus: $reason")
            }
            appendLine()
        }

        // Group entries by corpus, then by change type
        val byCorpus = diff.entries.groupBy { it.corpus }
        for ((corpus, entries) in byCorpus) {
            appendLine("## $corpus")
            appendLine()

            val grouped = entries.groupBy { it.changeType }
            for (changeType in listOf(ChangeType.ADDED, ChangeType.REMOVED, ChangeType.CHANGED)) {
                val group = grouped[changeType] ?: continue
                appendLine("### ${changeType.name} (${group.size})")
                appendLine()

                for (entry in group) {
                    appendLine("- **${entry.displayName}** (`${entry.nodeType}`)")
                    if (entry.filePath != null) {
                        appendLine("  - File: `${entry.filePath}`")
                    }

                    when (val detail = entry.detail) {
                        is DiffDetail.Code -> {
                            if (detail.signatureChanged) {
                                appendLine("  - Signature changed:")
                                detail.oldSignature?.let { appendLine("    - Old: `$it`") }
                                detail.newSignature?.let { appendLine("    - New: `$it`") }
                            }
                            if (detail.bodyChanged && !detail.signatureChanged) {
                                appendLine("  - Body changed")
                            }
                        }
                        is DiffDetail.GameData -> {
                            if (detail.fieldChanges.isNotEmpty()) {
                                appendLine("  - Field changes:")
                                for (fc in detail.fieldChanges.take(10)) {
                                    val desc = when (fc.changeType) {
                                        ChangeType.ADDED -> "+ `${fc.field}` = `${fc.newValue}`"
                                        ChangeType.REMOVED -> "- `${fc.field}` (was `${fc.oldValue}`)"
                                        ChangeType.CHANGED -> "~ `${fc.field}`: `${fc.oldValue}` -> `${fc.newValue}`"
                                    }
                                    appendLine("    - $desc")
                                }
                                if (detail.fieldChanges.size > 10) {
                                    appendLine("    - ... and ${detail.fieldChanges.size - 10} more")
                                }
                            }
                        }
                        is DiffDetail.Client -> {
                            appendLine("  - Content changed")
                        }
                        null -> {}
                    }
                }
                appendLine()
            }
        }
    }
}
