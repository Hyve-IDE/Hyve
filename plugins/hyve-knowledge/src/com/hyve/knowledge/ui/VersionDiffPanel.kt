// Copyright 2026 Hyve. All rights reserved.
package com.hyve.knowledge.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.hyve.common.compose.HyveSpacing
import com.hyve.common.compose.HyveThemeColors
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

    Column(
        modifier = modifier.fillMaxSize().padding(horizontal = HyveSpacing.sm),
    ) {
        Spacer(Modifier.height(HyveSpacing.sm))

        // Compact top bar — version selectors + actions
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(HyveSpacing.sm),
        ) {
            Text("Old:", color = colors.textSecondary)
            VersionDropdown(
                selected = selectedA,
                options = allVersions,
                onSelect = onSelectA,
                modifier = Modifier.weight(2f),
                placeholder = "Select\u2026",
            )

            Text("New:", color = colors.textSecondary)
            VersionDropdown(
                selected = selectedB,
                options = allVersions,
                onSelect = onSelectB,
                modifier = Modifier.weight(2f),
                placeholder = "Select\u2026",
            )

            HyveButton(
                text = if (isComputing) "Computing\u2026" else "Recompare",
                onClick = onCompute,
                enabled = selectedA.isNotBlank() && selectedB.isNotBlank() && selectedA != selectedB && !isComputing,
            )

            Spacer(Modifier.weight(1f))

            // Export + clear actions (only when diff is loaded)
            if (diff != null) {
                HyveButton(text = "Export", onClick = { exportMarkdown(project, settings, diff) })
                HyveButton(text = "Clear", onClick = onClear)
            }
        }

        Spacer(Modifier.height(HyveSpacing.sm))

        if (isComputing) {
            // Computing animation
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                AnimatedComputingState()
            }
        } else if (diff != null) {
            // Summary bar
            DiffSummaryBar(diff)

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

            Spacer(Modifier.height(HyveSpacing.sm))

            // Entries list grouped by corpus
            val byCorpus = diff.entries.groupBy { it.corpus }
            val expandedCorpora = remember { mutableStateMapOf<String, Boolean>() }

            LazyColumn(modifier = Modifier.fillMaxSize()) {
                for ((corpus, entries) in byCorpus) {
                    val isExpanded = expandedCorpora.getOrPut(corpus) { true }
                    val added = entries.count { it.changeType == ChangeType.ADDED }
                    val removed = entries.count { it.changeType == ChangeType.REMOVED }
                    val changed = entries.count { it.changeType == ChangeType.CHANGED }

                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth()
                                .clickable { expandedCorpora[corpus] = !isExpanded }
                                .padding(vertical = HyveSpacing.xs),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(if (isExpanded) "\u25BC " else "\u25B6 ")
                            Text("$corpus ")
                            Text("+$added", color = colors.success)
                            Text(" -$removed", color = colors.error)
                            Text(" ~$changed", color = colors.warning)
                        }
                        Divider(orientation = Orientation.Horizontal)
                    }

                    if (isExpanded) {
                        items(entries) { entry ->
                            DiffEntryRow(entry)
                        }
                    }
                }
            }
        }
    }
}

// ── Summary Bar ──

@Composable
private fun DiffSummaryBar(diff: VersionDiff) {
    val colors = HyveThemeColors.colors
    Row(
        modifier = Modifier.fillMaxWidth()
            .background(colors.slate.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
            .padding(HyveSpacing.sm),
        horizontalArrangement = Arrangement.spacedBy(HyveSpacing.lg),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("+${diff.summary.totalAdded} added", color = colors.success)
        Text("-${diff.summary.totalRemoved} removed", color = colors.error)
        Text("~${diff.summary.totalChanged} changed", color = colors.warning)
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
private fun DiffEntryRow(entry: DiffEntry) {
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

    Column(modifier = Modifier.fillMaxWidth().padding(vertical = HyveSpacing.xxs, horizontal = HyveSpacing.sm)) {
        Row(
            modifier = Modifier.fillMaxWidth().let {
                if (hasMeaningfulDetail) it.clickable { expanded = !expanded } else it
            },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("$icon ", color = color)
            Text(entry.displayName)
            entry.dataType?.let {
                Text(" ($it)", color = colors.textSecondary)
            }
            Spacer(Modifier.weight(1f))
            Text(entry.nodeType, color = colors.textSecondary)
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

        if (hasMeaningfulDetail) {
            Text(
                text = if (expanded) "Hide details" else "Show details",
                color = colors.honey,
                modifier = Modifier.clickable { expanded = !expanded }.padding(vertical = HyveSpacing.xxs),
            )
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
