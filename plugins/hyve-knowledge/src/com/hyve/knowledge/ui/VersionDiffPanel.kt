// Copyright 2026 Hyve. All rights reserved.
package com.hyve.knowledge.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.draw.clip
import com.hyve.common.compose.CorpusColors
import com.hyve.common.compose.HyveShapes
import com.hyve.common.compose.HyveSpacing
import com.hyve.common.compose.HyveThemeColors
import com.hyve.common.compose.HyveTypography
import com.hyve.knowledge.core.diff.*
import com.hyve.knowledge.diff.DiffTask
import com.hyve.knowledge.settings.KnowledgeSettings
import com.intellij.notification.Notification
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json
import org.jetbrains.jewel.ui.Orientation
import org.jetbrains.jewel.ui.component.*
import java.io.File
import java.io.FileWriter

@Composable
fun VersionDiffPanel(project: Project) {
    val settings = KnowledgeSettings.getInstance()
    val knownVersions = remember {
        val json = settings.state.knownVersions
        if (json.isBlank()) emptyList()
        else try { Json.decodeFromString<List<String>>(json) } catch (_: Exception) { emptyList() }
    }

    val allVersions = remember {
        val basePath = settings.resolvedBasePath()
        val versionsDir = File(basePath, "versions")
        val onDisk = if (versionsDir.isDirectory) {
            versionsDir.listFiles()?.filter { it.isDirectory && File(it, "knowledge.db").exists() }?.map { it.name } ?: emptyList()
        } else emptyList()
        (knownVersions + onDisk).distinct().sorted()
    }

    var selectedA by remember { mutableStateOf(allVersions.getOrNull(0) ?: "") }
    var selectedB by remember { mutableStateOf(allVersions.getOrNull(1) ?: "") }
    var diff by remember { mutableStateOf<VersionDiff?>(null) }
    var isComputing by remember { mutableStateOf(false) }

    val isIdle = diff == null && !isComputing

    val onCompute: () -> Unit = {
        if (selectedA.isNotBlank() && selectedB.isNotBlank() && selectedA != selectedB) {
            isComputing = true
            DiffTask.run(project, selectedA, selectedB) { result ->
                diff = result
                isComputing = false
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Divider(orientation = Orientation.Horizontal, modifier = Modifier.fillMaxWidth())

        if (isIdle) {
            // Centered landing page
            DiffIdleLanding(
                allVersions = allVersions,
                selectedA = selectedA,
                selectedB = selectedB,
                onSelectA = { selectedA = it },
                onSelectB = { selectedB = it },
                onCompute = onCompute,
                modifier = Modifier.weight(1f),
            )
        } else {
            // Active diff view — selectors pinned to top, results below
            DiffActiveView(
                project = project,
                settings = settings,
                allVersions = allVersions,
                selectedA = selectedA,
                selectedB = selectedB,
                onSelectA = { selectedA = it },
                onSelectB = { selectedB = it },
                onCompute = onCompute,
                diff = diff,
                isComputing = isComputing,
                onClear = { diff = null },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

// ── Idle Landing ──

@Composable
private fun DiffIdleLanding(
    allVersions: List<String>,
    selectedA: String,
    selectedB: String,
    onSelectA: (String) -> Unit,
    onSelectB: (String) -> Unit,
    onCompute: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = HyveThemeColors.colors

    Column(modifier = modifier.fillMaxSize()) {
        Spacer(Modifier.weight(1f))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = HyveSpacing.xl),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(HyveSpacing.lg),
        ) {
            Text(
                text = "Version Comparison",
                fontSize = 22.sp,
                fontWeight = FontWeight.SemiBold,
                color = colors.textPrimary,
            )

            Text(
                text = "Compare two indexed versions to see what changed in code, game data, and client UI.",
                color = colors.textSecondary,
            )

            // Version selector card
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.85f)
                    .background(colors.slate.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                    .padding(horizontal = HyveSpacing.md, vertical = HyveSpacing.sm),
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(HyveSpacing.sm),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    // Old version
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(HyveSpacing.sm),
                    ) {
                        Text("Old", color = colors.textSecondary, modifier = Modifier.width(36.dp))
                        VersionDropdown(
                            selected = selectedA,
                            options = allVersions,
                            onSelect = onSelectA,
                            modifier = Modifier.weight(1f),
                        )
                    }

                    // New version
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(HyveSpacing.sm),
                    ) {
                        Text("New", color = colors.textSecondary, modifier = Modifier.width(36.dp))
                        VersionDropdown(
                            selected = selectedB,
                            options = allVersions,
                            onSelect = onSelectB,
                            modifier = Modifier.weight(1f),
                        )
                    }

                    HyveButton(
                        text = "Compare Versions",
                        onClick = onCompute,
                        enabled = selectedA.isNotBlank() && selectedB.isNotBlank() && selectedA != selectedB,
                    )
                }
            }

            if (allVersions.size < 2) {
                Text(
                    text = "Index at least two versions to enable comparison.",
                    color = colors.textDisabled,
                )
            }
        }

        Spacer(Modifier.weight(1f))
    }
}

// ── Active Diff View ──

@Composable
private fun DiffActiveView(
    project: Project,
    settings: KnowledgeSettings,
    allVersions: List<String>,
    selectedA: String,
    selectedB: String,
    onSelectA: (String) -> Unit,
    onSelectB: (String) -> Unit,
    onCompute: () -> Unit,
    diff: VersionDiff?,
    isComputing: Boolean,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = HyveThemeColors.colors
    val backgroundFocus = remember { FocusRequester() }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = HyveSpacing.sm)
            .focusRequester(backgroundFocus)
            .focusable()
            .pointerInput(Unit) { detectTapGestures { backgroundFocus.requestFocus() } },
    ) {
        // Filter state — declared here so it's accessible in both toolbar and results
        val filterState = rememberTextFieldState("")
        val filterText by remember { derivedStateOf { filterState.text.toString().trim().lowercase() } }

        Spacer(Modifier.height(HyveSpacing.sm))

        // Compact top bar — version selectors + filter + actions
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(HyveSpacing.xs),
        ) {
            Text("Old:", color = colors.textSecondary)
            VersionDropdown(
                selected = selectedA,
                options = allVersions,
                onSelect = onSelectA,
                modifier = Modifier.width(IntrinsicSize.Max),
                placeholder = "Select\u2026",
            )

            Text("New:", color = colors.textSecondary)
            VersionDropdown(
                selected = selectedB,
                options = allVersions,
                onSelect = onSelectB,
                modifier = Modifier.width(IntrinsicSize.Max),
                placeholder = "Select\u2026",
            )

            HyveIconButton(
                icon = if (isComputing) "\u23F3" else "\u21BB",
                onClick = onCompute,
                enabled = selectedA.isNotBlank() && selectedB.isNotBlank() && selectedA != selectedB && !isComputing,
            )

            // Inline filter field
            if (diff != null) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .background(colors.slate.copy(alpha = 0.3f), RoundedCornerShape(6.dp))
                        .border(1.dp, colors.slateLight.copy(alpha = 0.3f), RoundedCornerShape(6.dp))
                        .padding(horizontal = HyveSpacing.sm, vertical = HyveSpacing.xs),
                ) {
                    TextField(
                        state = filterState,
                        modifier = Modifier
                            .fillMaxWidth()
                            .onPreviewKeyEvent { event ->
                                if (event.type == KeyEventType.KeyDown &&
                                    (event.key == Key.Enter || event.key == Key.Escape)
                                ) {
                                    backgroundFocus.requestFocus(); true
                                } else false
                            },
                        undecorated = true,
                        placeholder = { Text("Filter\u2026", color = colors.textDisabled) },
                    )
                }
            } else {
                Spacer(Modifier.weight(1f))
            }

            // Overflow menu (only when diff is loaded)
            if (diff != null) {
                OverflowMenu(
                    items = listOf(
                        "Export Markdown" to { exportMarkdown(project, settings, diff) },
                        "Clear" to onClear,
                    ),
                )
            }
        }

        Spacer(Modifier.height(HyveSpacing.xs))

        if (isComputing) {
            // Computing animation
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                AnimatedComputingState()
            }
        } else if (diff != null) {
            // Skipped corpora warnings
            if (diff.summary.skippedCorpora.isNotEmpty()) {
                for ((corpus, reason) in diff.summary.skippedCorpora) {
                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .background(colors.slate)
                            .padding(horizontal = HyveSpacing.sm, vertical = HyveSpacing.xs),
                    ) {
                        Text("$corpus: $reason", color = colors.warning)
                    }
                }
            }

            // Entries list grouped by corpus, filtered by displayName
            val filteredEntries by remember(diff) {
                derivedStateOf {
                    if (filterText.isEmpty()) diff.entries
                    else diff.entries.filter { it.displayName.lowercase().contains(filterText) }
                }
            }
            val byCorpus = filteredEntries.groupBy { it.corpus }
            val expandedCorpora = remember { mutableStateMapOf<String, Boolean>() }
            val expandedGroups = remember { mutableStateMapOf<String, Boolean>() }

            LazyColumn(modifier = Modifier.fillMaxSize()) {
                for ((corpus, entries) in byCorpus) {
                    val isExpanded = expandedCorpora.getOrPut(corpus) { true }
                    val added = entries.count { it.changeType == ChangeType.ADDED }
                    val removed = entries.count { it.changeType == ChangeType.REMOVED }
                    val changed = entries.count { it.changeType == ChangeType.CHANGED }

                    item {
                        val corpusColor = CorpusColors.forCorpus(corpus)
                        DiffSectionHeader(
                            title = corpus,
                            accentColor = corpusColor,
                            isExpanded = isExpanded,
                            onToggle = { expandedCorpora[corpus] = !isExpanded },
                            added = added,
                            removed = removed,
                            changed = changed,
                            total = entries.size,
                            modifier = Modifier.padding(top = if (corpus == byCorpus.keys.first()) 0.dp else HyveSpacing.xs),
                        )
                    }

                    if (isExpanded) {
                        val grouped = groupByPrefix(entries)
                        var rowIndex = 0
                        for (groupedItem in grouped) {
                            when (groupedItem) {
                                is GroupedItem.Single -> {
                                    val idx = rowIndex++
                                    item(key = "entry-${groupedItem.entry.nodeId}") {
                                        DiffEntryRow(groupedItem.entry, zebra = idx % 2 == 1)
                                    }
                                }
                                is GroupedItem.Grouped -> {
                                    val groupKey = "$corpus::${groupedItem.prefix}"
                                    val isGroupExpanded = expandedGroups[groupKey] ?: false
                                    val idx = rowIndex++
                                    item(key = "group-$groupKey") {
                                        PrefixGroupRow(
                                            prefix = groupedItem.prefix,
                                            count = groupedItem.entries.size,
                                            isExpanded = isGroupExpanded,
                                            onToggle = { expandedGroups[groupKey] = !isGroupExpanded },
                                            zebra = idx % 2 == 1,
                                        )
                                    }
                                    if (isGroupExpanded) {
                                        for (entry in groupedItem.entries) {
                                            val childIdx = rowIndex++
                                            item(key = "entry-${entry.nodeId}") {
                                                DiffEntryRow(
                                                    entry,
                                                    zebra = childIdx % 2 == 1,
                                                    indented = true,
                                                    displayOverride = entry.displayName.removePrefix(groupedItem.prefix),
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ── Computing Animation ──

@Composable
private fun AnimatedComputingState() {
    var dotCount by remember { mutableStateOf(1) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(400)
            dotCount = dotCount % 3 + 1
        }
    }
    Text(
        text = "Computing diff" + ".".repeat(dotCount),
        color = HyveThemeColors.colors.textSecondary,
    )
}

// ── Entry Row ──

@Composable
private fun DiffEntryRow(entry: DiffEntry, zebra: Boolean = false, indented: Boolean = false, displayOverride: String? = null) {
    val colors = HyveThemeColors.colors
    val icon = when (entry.changeType) {
        ChangeType.ADDED -> "+"
        ChangeType.REMOVED -> "-"
        ChangeType.CHANGED -> "~"
    }
    val color = when (entry.changeType) {
        ChangeType.ADDED -> colors.success
        ChangeType.REMOVED -> colors.error
        ChangeType.CHANGED -> colors.warning
    }

    val hasMeaningfulDetail = entry.detail.hasMeaningfulDetail()
    var expanded by remember { mutableStateOf(false) }
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .hoverable(interaction)
            .background(
                when {
                    hovered -> colors.twilight
                    zebra -> colors.slate.copy(alpha = 0.2f)
                    else -> Color.Transparent
                }
            )
            .let { if (hasMeaningfulDetail) it.clickable { expanded = !expanded } else it }
            .padding(vertical = 1.dp, horizontal = HyveSpacing.sm)
            .then(if (indented) Modifier.padding(start = HyveSpacing.md) else Modifier),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Expand arrow for entries with detail, otherwise just spacing
            if (hasMeaningfulDetail) {
                Text(
                    if (expanded) "\u25BE" else "\u25B8",
                    color = colors.textPrimary,
                )
                Spacer(Modifier.width(4.dp))
            }
            Text("$icon ", color = color)
            Text(displayOverride ?: entry.displayName, modifier = Modifier.weight(1f))
            entry.dataType?.let {
                Text(it, color = colors.textDisabled, style = HyveTypography.badge)
            }
        }

        // Expandable detail for changed entries
        if (hasMeaningfulDetail && expanded) {
            @Suppress("REDUNDANT_ELSE_IN_WHEN")
            when (val detail = entry.detail!!) {
                is DiffDetail.Code -> {
                    if (detail.signatureChanged) {
                        detail.oldSignature?.let { Text("  Old: $it", color = colors.error) }
                        detail.newSignature?.let { Text("  New: $it", color = colors.success) }
                    }
                    if (detail.bodyChanged) Text("  Body changed", color = colors.textSecondary)
                }
                is DiffDetail.GameData -> {
                    for (fc in detail.fieldChanges.take(15)) {
                        val desc = when (fc.changeType) {
                            ChangeType.ADDED -> "+ ${fc.field} = ${fc.newValue}"
                            ChangeType.REMOVED -> "- ${fc.field} (was ${fc.oldValue})"
                            ChangeType.CHANGED -> "~ ${fc.field}: ${fc.oldValue} -> ${fc.newValue}"
                        }
                        Text("  $desc", color = when (fc.changeType) {
                            ChangeType.ADDED -> colors.success
                            ChangeType.REMOVED -> colors.error
                            ChangeType.CHANGED -> colors.warning
                        })
                    }
                    if (detail.fieldChanges.size > 15) {
                        Text("  ... and ${detail.fieldChanges.size - 15} more", color = colors.textSecondary)
                    }
                }
                is DiffDetail.Client -> {}
            }
        }
    }
}

/**
 * Returns true if this detail contains information worth expanding.
 * Client details ("content changed") and code-only-body-changed are trivially obvious
 * from the change type icon.
 */
private fun DiffDetail?.hasMeaningfulDetail(): Boolean = when (this) {
    is DiffDetail.Code -> signatureChanged
    is DiffDetail.GameData -> fieldChanges.isNotEmpty()
    is DiffDetail.Client -> false
    null -> false
}

// ── Prefix Grouping ──

private sealed class GroupedItem {
    data class Single(val entry: DiffEntry) : GroupedItem()
    data class Grouped(val prefix: String, val entries: List<DiffEntry>) : GroupedItem()
}

private val DELIMITER_REGEX = Regex("[._\\-]")

/**
 * Groups entries that share a common delimiter-boundary prefix.
 * Returns a mix of [GroupedItem.Single] (ungrouped) and [GroupedItem.Grouped] (collapsible).
 */
private fun groupByPrefix(entries: List<DiffEntry>, minGroupSize: Int = 4): List<GroupedItem> {
    if (entries.size < minGroupSize) return entries.map { GroupedItem.Single(it) }

    // For each name, find all delimiter-boundary prefixes
    fun prefixesOf(name: String): List<String> {
        val result = mutableListOf<String>()
        for (match in DELIMITER_REGEX.findAll(name)) {
            result.add(name.substring(0, match.range.last + 1))
        }
        return result
    }

    // Map prefix → entry indices
    val prefixToIndices = mutableMapOf<String, MutableList<Int>>()
    for ((i, entry) in entries.withIndex()) {
        for (prefix in prefixesOf(entry.displayName)) {
            prefixToIndices.getOrPut(prefix) { mutableListOf() }.add(i)
        }
    }

    // Greedy: longest prefix first, claim entries
    val claimed = mutableSetOf<Int>()
    val groups = mutableListOf<Pair<String, List<Int>>>()

    val candidates = prefixToIndices.entries
        .filter { it.value.size >= minGroupSize }
        .sortedByDescending { it.key.length }

    for ((prefix, indices) in candidates) {
        val unclaimed = indices.filter { it !in claimed }
        if (unclaimed.size >= minGroupSize) {
            groups.add(prefix to unclaimed)
            claimed.addAll(unclaimed)
        }
    }

    // Build result in original order
    val result = mutableListOf<GroupedItem>()
    val groupsByFirstIndex = groups.sortedBy { it.second.min() }
    val emittedPrefixes = mutableSetOf<String>()

    for ((i, entry) in entries.withIndex()) {
        if (i in claimed) {
            val group = groupsByFirstIndex.find { i in it.second && it.first !in emittedPrefixes }
            if (group != null) {
                result.add(GroupedItem.Grouped(group.first, group.second.map { entries[it] }))
                emittedPrefixes.add(group.first)
            }
        } else {
            result.add(GroupedItem.Single(entry))
        }
    }

    return result
}

@Composable
private fun PrefixGroupRow(
    prefix: String,
    count: Int,
    isExpanded: Boolean,
    onToggle: () -> Unit,
    zebra: Boolean = false,
) {
    val colors = HyveThemeColors.colors
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .hoverable(interaction)
            .background(
                when {
                    hovered -> colors.twilight
                    zebra -> colors.slate.copy(alpha = 0.2f)
                    else -> Color.Transparent
                }
            )
            .clickable { onToggle() }
            .padding(vertical = 2.dp, horizontal = HyveSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            if (isExpanded) "\u25BE" else "\u25B8",
            color = colors.textPrimary,
        )
        Spacer(Modifier.width(4.dp))
        Text(prefix, color = colors.textSecondary)
        Spacer(Modifier.width(HyveSpacing.xs))
        Box(
            modifier = Modifier
                .clip(HyveShapes.badge)
                .background(colors.slate)
                .padding(horizontal = 5.dp, vertical = 1.dp),
        ) {
            Text("$count", style = HyveTypography.badge, color = colors.textSecondary)
        }
    }
}

// ── Version Dropdown ──

/**
 * Custom dropdown without Jewel's platform focus ring.
 * Uses clickable + Popup positioned below the trigger.
 */
@Composable
private fun VersionDropdown(
    selected: String,
    options: List<String>,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Select version\u2026",
) {
    val colors = HyveThemeColors.colors
    var expanded by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(6.dp)
    val density = LocalDensity.current

    // Track trigger size for popup positioning
    var triggerHeightPx by remember { mutableStateOf(0) }
    var triggerWidthPx by remember { mutableStateOf(0) }

    val triggerInteraction = remember { MutableInteractionSource() }
    val triggerHovered by triggerInteraction.collectIsHoveredAsState()

    Box(modifier = modifier) {
        // Trigger
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .onGloballyPositioned { coords ->
                    triggerHeightPx = coords.size.height
                    triggerWidthPx = coords.size.width
                }
                .hoverable(triggerInteraction)
                .background(
                    if (triggerHovered) colors.slate.copy(alpha = 0.6f)
                    else colors.slate.copy(alpha = 0.4f),
                    shape,
                )
                .border(
                    1.dp,
                    if (triggerHovered) colors.slateLight.copy(alpha = 0.7f)
                    else colors.slateLight.copy(alpha = 0.4f),
                    shape,
                )
                .clickable { expanded = !expanded }
                .padding(horizontal = HyveSpacing.sm, vertical = HyveSpacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = selected.ifBlank { placeholder },
                color = if (selected.isBlank()) colors.textDisabled else colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(HyveSpacing.xs))
            Text(if (expanded) "\u25B4" else "\u25BE", color = colors.textSecondary)
        }

        // Menu popup — offset below trigger, matching width
        if (expanded) {
            val triggerWidthDp = with(density) { triggerWidthPx.toDp() }
            val gap = 4 // px gap between trigger and popup

            Popup(
                alignment = Alignment.TopStart,
                offset = IntOffset(0, triggerHeightPx + gap),
                onDismissRequest = { expanded = false },
                properties = PopupProperties(focusable = true),
            ) {
                Column(
                    modifier = Modifier
                        .width(IntrinsicSize.Max)
                        .widthIn(min = triggerWidthDp)
                        .shadow(8.dp, shape)
                        .background(colors.midnight, shape)
                        .border(1.dp, colors.slate, shape)
                        .verticalScroll(rememberScrollState()),
                ) {
                    for (option in options) {
                        val isSelected = option == selected
                        val interaction = remember { MutableInteractionSource() }
                        val hovered by interaction.collectIsHoveredAsState()

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .hoverable(interaction)
                                .clickable {
                                    onSelect(option)
                                    expanded = false
                                }
                                .background(
                                    when {
                                        isSelected -> colors.honey.copy(alpha = 0.12f)
                                        hovered -> colors.slate.copy(alpha = 0.5f)
                                        else -> Color.Transparent
                                    },
                                )
                                .padding(horizontal = HyveSpacing.sm, vertical = HyveSpacing.xs),
                        ) {
                            Text(
                                option,
                                color = if (isSelected) colors.honey else colors.textPrimary,
                            )
                        }
                    }
                }
            }
        }
    }
}

// ── Diff Section Header ──

/**
 * Corpus group header with accent bar, hover, and inline +added -removed ~changed counts.
 */
@Composable
private fun DiffSectionHeader(
    title: String,
    accentColor: Color,
    isExpanded: Boolean,
    onToggle: () -> Unit,
    added: Int,
    removed: Int,
    changed: Int,
    total: Int,
    modifier: Modifier = Modifier,
) {
    val colors = HyveThemeColors.colors
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val bg = if (hovered) colors.slateLight else colors.slate

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(HyveShapes.card)
            .hoverable(interaction)
            .background(bg)
            .clickable { onToggle() }
            .padding(start = HyveSpacing.xs, end = HyveSpacing.sm, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Left accent bar
        Box(
            Modifier
                .width(3.dp)
                .height(20.dp)
                .clip(HyveShapes.accentBar)
                .background(accentColor)
        )
        Spacer(Modifier.width(6.dp))
        // Expand arrow
        Text(
            text = if (isExpanded) "\u25BE" else "\u25B8",
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.width(HyveSpacing.xs))
        // Title
        Text(title, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.width(HyveSpacing.sm))
        // Inline change counts
        if (added > 0) Text("+$added", color = colors.success, style = HyveTypography.badge)
        if (added > 0 && (removed > 0 || changed > 0)) Spacer(Modifier.width(HyveSpacing.xs))
        if (removed > 0) Text("-$removed", color = colors.error, style = HyveTypography.badge)
        if (removed > 0 && changed > 0) Spacer(Modifier.width(HyveSpacing.xs))
        if (changed > 0) Text("~$changed", color = colors.warning, style = HyveTypography.badge)

        Spacer(Modifier.weight(1f))
        // Total count badge
        Box(
            modifier = Modifier
                .clip(HyveShapes.badge)
                .background(accentColor.copy(alpha = 0.2f))
                .padding(horizontal = 5.dp, vertical = 1.dp),
        ) {
            Text("$total", style = HyveTypography.badge)
        }
    }
}

// ── Button ──

/**
 * Custom button without Jewel's platform focus ring.
 * Honey border on hover, subtle slate background otherwise.
 */
@Composable
private fun HyveButton(
    text: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val colors = HyveThemeColors.colors
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val shape = RoundedCornerShape(6.dp)

    val bg = when {
        !enabled -> colors.slate.copy(alpha = 0.2f)
        hovered -> colors.honey.copy(alpha = 0.15f)
        else -> colors.slate.copy(alpha = 0.4f)
    }
    val border = when {
        !enabled -> colors.slateLight.copy(alpha = 0.2f)
        hovered -> colors.honey.copy(alpha = 0.6f)
        else -> colors.slateLight.copy(alpha = 0.4f)
    }
    val textColor = when {
        !enabled -> colors.textDisabled
        else -> colors.textPrimary
    }

    Box(
        modifier = modifier
            .hoverable(interaction)
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier)
            .background(bg, shape)
            .border(1.dp, border, shape)
            .padding(horizontal = HyveSpacing.md, vertical = HyveSpacing.xs),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = textColor)
    }
}

/**
 * Compact icon-only button — same hover style as HyveButton, square aspect.
 */
@Composable
private fun HyveIconButton(
    icon: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val colors = HyveThemeColors.colors
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val shape = RoundedCornerShape(6.dp)

    val bg = when {
        !enabled -> colors.slate.copy(alpha = 0.2f)
        hovered -> colors.honey.copy(alpha = 0.15f)
        else -> colors.slate.copy(alpha = 0.4f)
    }
    val border = when {
        !enabled -> colors.slateLight.copy(alpha = 0.2f)
        hovered -> colors.honey.copy(alpha = 0.6f)
        else -> colors.slateLight.copy(alpha = 0.4f)
    }
    val textColor = when {
        !enabled -> colors.textDisabled
        else -> colors.textPrimary
    }

    Box(
        modifier = modifier
            .hoverable(interaction)
            .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier)
            .background(bg, shape)
            .border(1.dp, border, shape)
            .padding(horizontal = HyveSpacing.xs, vertical = HyveSpacing.xs),
        contentAlignment = Alignment.Center,
    ) {
        Text(icon, color = textColor, fontSize = 16.sp)
    }
}

// ── Overflow Menu ──

@Composable
private fun OverflowMenu(
    items: List<Pair<String, () -> Unit>>,
) {
    val colors = HyveThemeColors.colors
    var expanded by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(6.dp)
    val density = LocalDensity.current
    var triggerHeightPx by remember { mutableStateOf(0) }
    var triggerWidthPx by remember { mutableStateOf(0) }
    val triggerInteraction = remember { MutableInteractionSource() }
    val triggerHovered by triggerInteraction.collectIsHoveredAsState()

    Box {
        Box(
            modifier = Modifier
                .onGloballyPositioned { coords ->
                    triggerHeightPx = coords.size.height
                    triggerWidthPx = coords.size.width
                }
                .hoverable(triggerInteraction)
                .background(
                    if (triggerHovered) colors.slate.copy(alpha = 0.5f)
                    else Color.Transparent, shape,
                )
                .clickable { expanded = !expanded }
                .padding(horizontal = HyveSpacing.xs, vertical = HyveSpacing.xs),
            contentAlignment = Alignment.Center,
        ) {
            Text("\u22EE", color = colors.textSecondary)
        }

        if (expanded) {
            val gap = 4
            Popup(
                alignment = Alignment.TopEnd,
                offset = IntOffset(0, triggerHeightPx + gap),
                onDismissRequest = { expanded = false },
                properties = PopupProperties(focusable = true),
            ) {
                Column(
                    modifier = Modifier
                        .width(IntrinsicSize.Max)
                        .shadow(8.dp, shape)
                        .background(colors.midnight, shape)
                        .border(1.dp, colors.slate, shape),
                ) {
                    for ((label, action) in items) {
                        val interaction = remember { MutableInteractionSource() }
                        val hovered by interaction.collectIsHoveredAsState()
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .hoverable(interaction)
                                .clickable { action(); expanded = false }
                                .background(
                                    if (hovered) colors.slate.copy(alpha = 0.5f)
                                    else Color.Transparent,
                                )
                                .padding(horizontal = HyveSpacing.md, vertical = HyveSpacing.xs),
                        ) {
                            Text(label, color = colors.textPrimary)
                        }
                    }
                }
            }
        }
    }
}

// ── Export ──

private fun exportMarkdown(project: Project, settings: KnowledgeSettings, diff: VersionDiff) {
    try {
        val markdown = DiffExporter.toMarkdown(diff)
        val file = File(settings.resolvedBasePath(), "diffs/${diff.versionA}--${diff.versionB}.md")
        file.parentFile?.mkdirs()
        FileWriter(file).use { it.write(markdown) }
        NotificationGroupManager.getInstance()
            .getNotificationGroup("Hyve Knowledge")
            .createNotification(
                "Diff exported",
                file.absolutePath,
                NotificationType.INFORMATION,
            )
            .addAction(object : NotificationAction("Open") {
                override fun actionPerformed(e: AnActionEvent, notification: Notification) {
                    notification.expire()
                    val vf = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(file)
                    if (vf != null) {
                        FileEditorManager.getInstance(project).openFile(vf, true)
                    }
                }
            })
            .notify(project)
    } catch (e: Exception) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup("Hyve Knowledge")
            .createNotification(
                "Export failed",
                e.message ?: "Unknown error",
                NotificationType.ERROR,
            )
            .notify(project)
    }
}
