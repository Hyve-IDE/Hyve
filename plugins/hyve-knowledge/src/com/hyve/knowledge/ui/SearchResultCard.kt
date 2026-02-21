// Copyright 2026 Hyve. All rights reserved.
package com.hyve.knowledge.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.hyve.common.compose.*
import com.hyve.knowledge.core.search.SearchResult
import org.jetbrains.jewel.ui.component.Text

/**
 * Card for a single search result with left accent bar (corpus color),
 * display name, relevance score, snippet, and file path.
 */
@Composable
fun SearchResultCard(
    result: SearchResult,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = HyveThemeColors.colors
    val corpusColor = CorpusColors.forCorpus(result.corpus)
    val tier = RelevanceTier.fromScore(result.score)
    val tierColor = RelevanceColors.forTier(tier)

    // Accent bar opacity scales with relevance
    val accentAlpha = when (tier) {
        RelevanceTier.EXACT -> 1.0f
        RelevanceTier.STRONG -> 0.80f
        RelevanceTier.GOOD -> 0.55f
        RelevanceTier.WEAK -> 0.35f
    }

    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val bgColor = if (isHovered) colors.twilight else colors.midnight

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .clip(HyveShapes.card)
            .hoverable(interactionSource)
            .background(bgColor)
            .clickable(onClick = onClick),
    ) {
        // Left accent bar — opacity reflects relevance
        Box(
            Modifier
                .width(3.dp)
                .fillMaxHeight()
                .clip(HyveShapes.accentBar)
                .background(corpusColor.copy(alpha = accentAlpha))
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = HyveSpacing.sm, vertical = HyveSpacing.xs),
            verticalArrangement = Arrangement.spacedBy(1.dp),
        ) {
            // Top row: display name + relevance dot + score
            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Display name with dim class / bold method
                Text(
                    text = formatDisplayName(result.displayName, colors),
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(HyveSpacing.sm))
                // Relevance: colored dot + numeric score
                Box(
                    Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(tierColor)
                )
                Spacer(Modifier.width(HyveSpacing.xs))
                Text(
                    text = String.format("%.2f", result.score),
                    style = HyveTypography.badge.copy(color = tierColor),
                )
            }

            // Snippet (3 lines max, monospace for code corpus)
            if (result.snippet.isNotBlank()) {
                val snippetStyle = if (result.corpus == "code") {
                    HyveTypography.caption.copy(fontFamily = FontFamily.Monospace)
                } else {
                    HyveTypography.caption
                }
                Text(
                    text = result.snippet.lines().take(3).joinToString("\n"),
                    style = snippetStyle,
                    maxLines = 3,
                )
            }

            // Bottom row: connection indicators or file path
            Row(
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (result.bridgedFrom != null) {
                    // Expanded result — show backward link to seed with edge-type label
                    val crossCorpusColor = crossCorpusColor(result.corpus)
                    val edgeLabel = bridgeLabel(result.bridgeEdgeType)
                    Text(
                        text = "\u2190 $edgeLabel: ${result.bridgedFrom}",
                        style = HyveTypography.badge.copy(color = crossCorpusColor),
                    )
                } else {
                    // File path (default for pure vector and seed results)
                    val fileLabel = trimFilePath(result.filePath, result.corpus).let {
                        if (result.lineStart > 0) "$it:${result.lineStart}" else it
                    }
                    Text(
                        text = fileLabel,
                        style = HyveTypography.badge.copy(color = colors.info),
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                    )
                    // Seed result — show forward link count
                    if (result.connectedNodeIds.isNotEmpty()) {
                        Spacer(Modifier.width(HyveSpacing.sm))
                        val n = result.connectedNodeIds.size
                        val crossCorpusColor = crossCorpusColor(result.corpus)
                        Text(
                            text = "\u2192 $n node${if (n != 1) "s" else ""}",
                            style = HyveTypography.badge.copy(color = crossCorpusColor),
                        )
                    }
                }
            }
        }
    }
}

/**
 * Formats "Class#method" display names: dim class portion, bold method portion.
 * Non-code display names (no # separator) render as-is with item title styling.
 */
@Composable
private fun formatDisplayName(
    name: String,
    colors: HyveExtendedColors,
): androidx.compose.ui.text.AnnotatedString {
    val fontSize = HyveTypography.itemTitle.fontSize
    return buildAnnotatedString {
        val hashIndex = name.indexOf('#')
        if (hashIndex > 0 && hashIndex < name.length - 1) {
            // Class portion — dimmed
            withStyle(SpanStyle(
                color = colors.textSecondary,
                fontSize = fontSize,
                fontWeight = FontWeight.Normal,
            )) {
                append(name.substring(0, hashIndex))
            }
            // Separator + method — bold primary
            withStyle(SpanStyle(
                color = colors.textPrimary,
                fontSize = fontSize,
                fontWeight = FontWeight.Bold,
            )) {
                append(name.substring(hashIndex))
            }
        } else {
            // No separator — full bold
            withStyle(SpanStyle(
                color = colors.textPrimary,
                fontSize = fontSize,
                fontWeight = FontWeight.Bold,
            )) {
                append(name)
            }
        }
    }
}

/**
 * Human-readable label for the edge type that bridged this result.
 */
private fun bridgeLabel(edgeType: String?): String = when (edgeType) {
    "IMPLEMENTED_BY" -> "impl"
    "DOCS_REFERENCES" -> "docs"
    "UI_BINDS_TO" -> "ui"
    else -> "linked"
}

/**
 * Returns the color of the *source* corpus for cross-corpus indicators.
 * Uses the opposite corpus color so the indicator visually points back to where it came from.
 */
@Composable
@androidx.compose.runtime.ReadOnlyComposable
private fun crossCorpusColor(corpus: String): androidx.compose.ui.graphics.Color = when (corpus) {
    "code" -> CorpusColors.gameData      // code result was bridged from gamedata
    "gamedata" -> CorpusColors.code      // gamedata result was bridged from code
    "docs" -> CorpusColors.docs          // docs result was bridged from code/gamedata
    "client" -> CorpusColors.clientUi    // client result was bridged from gamedata
    else -> HyveThemeColors.colors.textSecondary
}

/**
 * Trims long file paths for display. Client UI paths get trimmed after "Interface/",
 * others just show the filename.
 */
private fun trimFilePath(filePath: String, corpus: String): String {
    val normalized = filePath.replace('\\', '/')
    if (corpus == "client") {
        // Trim to after "Interface/" for client UI paths
        val interfaceIdx = normalized.indexOf("/Interface/")
        if (interfaceIdx >= 0) {
            return normalized.substring(interfaceIdx + "/Interface/".length)
        }
        // Fallback: trim to after "Client/Data/"
        val clientIdx = normalized.indexOf("/Client/Data/")
        if (clientIdx >= 0) {
            return normalized.substring(clientIdx + "/Client/Data/".length)
        }
    }
    // Default: just the filename
    return normalized.substringAfterLast('/')
}
