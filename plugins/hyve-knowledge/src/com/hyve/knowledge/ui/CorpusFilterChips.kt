// Copyright 2026 Hyve. All rights reserved.
package com.hyve.knowledge.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.hyve.common.compose.CorpusColors
import com.hyve.common.compose.CorpusVisual
import com.hyve.common.compose.HyveSpacing
import com.hyve.common.compose.components.FilterChip
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.Tooltip

/**
 * Horizontal row of filter chips, one per corpus.
 */
@Composable
fun CorpusFilterChips(
    enabledCorpora: Set<String>,
    onToggle: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(HyveSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        for (cv in CorpusVisual.ordered) {
            Tooltip(tooltip = { Text(cv.tooltipText) }) {
                FilterChip(
                    label = cv.shortLabel,
                    isActive = cv.corpusId in enabledCorpora,
                    onClick = { onToggle(cv.corpusId) },
                    activeColor = CorpusColors.forCorpus(cv.corpusId),
                )
            }
        }
    }
}
