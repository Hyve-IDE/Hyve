// Copyright 2026 Hyve. All rights reserved.
package com.hyve.knowledge.ui

import androidx.compose.foundation.ContextMenuArea
import androidx.compose.foundation.ContextMenuItem
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.hyve.common.compose.*
import com.hyve.common.compose.components.SectionHeader
import com.hyve.knowledge.core.search.SearchResult
import com.hyve.knowledge.docs.OfflineDocResolver
import org.jetbrains.jewel.ui.component.Text

/**
 * Groups search results by corpus in canonical order (CODE, GAMEDATA, CLIENT, DOCS).
 * Each group gets a collapsible [SectionHeader] followed by [SearchResultCard] items.
 *
 * Direct results (from vector/graph search) are shown immediately.
 * Expanded results (pulled in via graph edge traversal) are tucked into a
 * collapsed-by-default "Related connections" sub-section at the bottom of each group.
 */
@Composable
fun GroupedResultsList(
    results: List<SearchResult>,
    onResultClick: (SearchResult) -> Unit,
    onOpenFullDocument: ((SearchResult) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val grouped = results.groupBy { it.corpus }
    var expandedSections by remember { mutableStateOf(CorpusVisual.ordered.map { it.corpusId }.toSet()) }
    var expandedExpansions by remember { mutableStateOf(emptySet<String>()) }

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = HyveSpacing.sm, vertical = HyveSpacing.xs),
        verticalArrangement = Arrangement.spacedBy(HyveSpacing.xs),
    ) {
        for (cv in CorpusVisual.ordered) {
            val corpusResults = grouped[cv.corpusId] ?: continue
            val directResults = corpusResults.filter { it.expandedFromNodeId == null }
            val expandedResults = corpusResults.filter { it.expandedFromNodeId != null }
            // Skip corpus entirely when it has no direct hits — expansion-only
            // groups are noise (e.g. a random NPC node bridged from a code seed)
            if (directResults.isEmpty() && expandedResults.isNotEmpty()) continue
            val isExpanded = cv.corpusId in expandedSections

            item(key = "header:${cv.corpusId}") {
                SectionHeader(
                    title = cv.displayName,
                    accentColor = CorpusColors.forCorpus(cv.corpusId),
                    isExpanded = isExpanded,
                    onToggle = {
                        expandedSections = if (isExpanded) {
                            expandedSections - cv.corpusId
                        } else {
                            expandedSections + cv.corpusId
                        }
                    },
                    count = directResults.size,
                    modifier = Modifier.padding(top = HyveSpacing.xs),
                )
            }

            if (isExpanded) {
                // Direct results — shown immediately
                items(directResults, key = { "result:${it.nodeId}" }) { result ->
                    DocsContextMenuWrapper(result, onOpenFullDocument) {
                        SearchResultCard(
                            result = result,
                            onClick = { onResultClick(result) },
                            modifier = Modifier.padding(start = 6.dp),
                        )
                    }
                }

                // Expanded results — collapsed sub-section
                if (expandedResults.isNotEmpty()) {
                    val expansionKey = "expansion:${cv.corpusId}"
                    val isExpansionOpen = expansionKey in expandedExpansions

                    item(key = expansionKey) {
                        ExpansionToggle(
                            count = expandedResults.size,
                            isExpanded = isExpansionOpen,
                            onToggle = {
                                expandedExpansions = if (isExpansionOpen) {
                                    expandedExpansions - expansionKey
                                } else {
                                    expandedExpansions + expansionKey
                                }
                            },
                        )
                    }

                    if (isExpansionOpen) {
                        items(expandedResults, key = { "expanded:${it.nodeId}" }) { result ->
                            DocsContextMenuWrapper(result, onOpenFullDocument) {
                                SearchResultCard(
                                    result = result,
                                    onClick = { onResultClick(result) },
                                    modifier = Modifier.padding(start = 12.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Wraps a search result card in a right-click context menu for DOCS corpus results.
 * Shows "Open Full Document" if the offline file exists, or "(not synced)" if not.
 * Non-docs results pass through unwrapped.
 */
@Composable
private fun DocsContextMenuWrapper(
    result: SearchResult,
    onOpenFullDocument: ((SearchResult) -> Unit)?,
    content: @Composable () -> Unit,
) {
    if (result.corpus == "docs" && onOpenFullDocument != null) {
        val relPath = result.nodeId.removePrefix("docs:")
        val offlineFile = OfflineDocResolver.resolve(relPath)
        ContextMenuArea(
            items = {
                if (offlineFile != null) {
                    listOf(ContextMenuItem("Open Full Document") { onOpenFullDocument(result) })
                } else {
                    listOf(ContextMenuItem("Open Full Document (not synced)") { })
                }
            },
        ) {
            content()
        }
    } else {
        content()
    }
}

/**
 * Toggle header for the graph-expanded results sub-section.
 * Shows "▸ Related connections (N)" when collapsed, "▾ Related connections (N)" when expanded.
 */
@Composable
private fun ExpansionToggle(
    count: Int,
    isExpanded: Boolean,
    onToggle: () -> Unit,
) {
    val colors = HyveThemeColors.colors
    val arrow = if (isExpanded) "\u25BE" else "\u25B8"

    Row(
        modifier = Modifier
            .padding(start = 6.dp, top = HyveSpacing.xs)
            .clickable(onClick = onToggle),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(HyveSpacing.xs),
    ) {
        Text(
            text = "$arrow Related connections ($count)",
            style = HyveTypography.badge.copy(color = colors.textSecondary),
        )
    }
}
