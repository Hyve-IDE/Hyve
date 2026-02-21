// Copyright 2026 Hyve. All rights reserved.
package com.hyve.knowledge.index

/**
 * A candidate binding between a client UI node and a gamedata entity,
 * discovered by pattern matching against .ui file content.
 */
data class BindingCandidate(
    val clientNodeId: String,
    val candidateText: String,
    val strategy: String,
    val confidence: Float,
)

/**
 * Regex-based pattern detectors that run against stored content from .ui corpus nodes.
 * Each strategy returns candidate matches with varying confidence levels.
 *
 * Used by [InspectClientUIContentAction] for diagnostic validation (Phase A)
 * and by [UIBindingExtractor] for edge production (Phase B).
 */
class UIContentAnalyzer {

    companion object {
        /** Matches resource path stems like "Items/Sword", "Blocks/Stone", "NPCs/Trork" */
        private val RESOURCE_PATH_PATTERN = Regex(
            """["'](\w+/[\w/]+)["']""",
        )

        /** Matches PascalCase identifiers (3+ chars, at least two words) like ItemStack, HotbarSlot */
        private val PASCAL_CASE_PATTERN = Regex(
            """\b([A-Z][a-z]+(?:[A-Z][a-z]+)+)\b""",
        )

        /** Matches JSON keys near gamedata-like values: "item", "recipe", "block", etc. */
        private val JSON_KEY_PATTERN = Regex(
            """"(item|recipe|block|npc|entity|slot|inventory|equipment|weapon|armor|tool|resource|prefab)"\s*:\s*"([^"]+)"""",
            RegexOption.IGNORE_CASE,
        )

        /** Common words to exclude from PascalCase matching (UI framework terms, not gamedata) */
        private val PASCAL_CASE_EXCLUSIONS = setOf(
            "DataContext", "DataTemplate", "StackPanel", "DockPanel", "GridPanel",
            "TextBlock", "TextBox", "ScrollViewer", "ContentControl", "UserControl",
            "ItemsControl", "ResourceDictionary", "SolidColorBrush", "LinearGradientBrush",
            "ColumnDefinition", "RowDefinition", "ContentPresenter", "TemplateBinding",
            "StaticResource", "DynamicResource", "EventTrigger", "DataTrigger",
            "MultiBinding", "RelativeSource", "TargetType", "BasedOn",
            "HorizontalAlignment", "VerticalAlignment", "BorderThickness",
        )
    }

    /**
     * Analyze .ui file content and return binding candidates.
     *
     * @param content The raw file content of the .ui node
     * @param nodeId  The database node ID (e.g. "ui:InGame/HotbarSlot.ui")
     * @return List of candidate bindings discovered by all strategies
     */
    fun analyze(content: String, nodeId: String): List<BindingCandidate> {
        val candidates = mutableListOf<BindingCandidate>()

        candidates += extractResourcePaths(content, nodeId)
        candidates += extractPascalCaseIdentifiers(content, nodeId)
        candidates += extractFilenameStem(content, nodeId)
        candidates += extractJsonKeyReferences(content, nodeId)

        return candidates
    }

    /**
     * Strategy: Resource path stems.
     * Matches string literals like "Items/Sword" that correspond to gamedata file paths.
     * Confidence: 0.8
     */
    private fun extractResourcePaths(content: String, nodeId: String): List<BindingCandidate> {
        return RESOURCE_PATH_PATTERN.findAll(content)
            .map { match ->
                val path = match.groupValues[1]
                // Extract the leaf name (last segment) as the candidate
                val stem = path.substringAfterLast('/')
                BindingCandidate(
                    clientNodeId = nodeId,
                    candidateText = stem,
                    strategy = "resource_path",
                    confidence = 0.8f,
                )
            }
            .toList()
    }

    /**
     * Strategy: PascalCase identifiers.
     * Matches compound PascalCase words like ItemStack, HotbarSlot against gamedata display_names.
     * Confidence: 0.5
     */
    private fun extractPascalCaseIdentifiers(content: String, nodeId: String): List<BindingCandidate> {
        return PASCAL_CASE_PATTERN.findAll(content)
            .map { it.groupValues[1] }
            .distinct()
            .filter { it !in PASCAL_CASE_EXCLUSIONS }
            .map { identifier ->
                BindingCandidate(
                    clientNodeId = nodeId,
                    candidateText = identifier,
                    strategy = "pascal_case",
                    confidence = 0.5f,
                )
            }
            .toList()
    }

    /**
     * Strategy: Filename stem match.
     * Uses the .ui filename itself (extracted from nodeId) as a candidate.
     * Confidence: 0.4
     */
    private fun extractFilenameStem(content: String, nodeId: String): List<BindingCandidate> {
        // nodeId format: "ui:Category/FileName.ui" or "ui:FileName.ui"
        val withoutPrefix = nodeId.substringAfter(':')
        val filename = withoutPrefix.substringAfterLast('/').substringBeforeLast('.')
        if (filename.isBlank()) return emptyList()

        return listOf(
            BindingCandidate(
                clientNodeId = nodeId,
                candidateText = filename,
                strategy = "filename_stem",
                confidence = 0.4f,
            ),
        )
    }

    /**
     * Strategy: JSON key references.
     * Matches keys like "item", "recipe", "block" paired with gamedata-like string values.
     * Confidence: 0.6
     */
    private fun extractJsonKeyReferences(content: String, nodeId: String): List<BindingCandidate> {
        return JSON_KEY_PATTERN.findAll(content)
            .map { match ->
                val value = match.groupValues[2]
                // Use the value (not the key) as the candidate text
                val stem = value.substringAfterLast('/').substringBeforeLast('.')
                BindingCandidate(
                    clientNodeId = nodeId,
                    candidateText = stem,
                    strategy = "json_key",
                    confidence = 0.6f,
                )
            }
            .toList()
    }
}
