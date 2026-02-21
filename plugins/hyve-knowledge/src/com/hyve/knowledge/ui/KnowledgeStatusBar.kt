// Copyright 2026 Hyve. All rights reserved.
package com.hyve.knowledge.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.hyve.common.compose.*
import com.hyve.common.compose.components.StatusBar
import com.hyve.knowledge.core.search.IndexStats
import org.jetbrains.jewel.ui.component.Text

/**
 * Bottom status bar showing per-corpus node counts (colored dots) and search status.
 */
@Composable
fun KnowledgeStatusBar(
    corpusStats: Map<String, IndexStats>,
    resultCount: Int,
    totalResultCount: Int = 0,
    modifier: Modifier = Modifier,
) {
    StatusBar(modifier = modifier) {
        // Per-corpus colored dot + count
        for (cv in CorpusVisual.ordered) {
            val stats = corpusStats[cv.corpusId]
            val count = stats?.nodeCount ?: 0
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(HyveSpacing.xs),
            ) {
                Box(
                    Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(CorpusColors.forCorpus(cv.corpusId))
                )
                Text(
                    text = "${formatCount(count)} ${cv.shortLabel.lowercase()}",
                    style = HyveTypography.statusBar,
                )
            }
        }

        Spacer(Modifier.weight(1f))

        // Result count (only shown when results are ready)
        val statusText = when {
            resultCount > 0 && totalResultCount > resultCount ->
                "$resultCount of $totalResultCount results"
            resultCount > 0 -> "$resultCount results"
            else -> ""
        }
        if (statusText.isNotEmpty()) {
            Text(
                text = statusText,
                style = HyveTypography.statusBar,
            )
        }
    }
}

private fun formatCount(count: Int): String = when {
    count >= 1000 -> String.format("%,d", count)
    else -> "$count"
}
