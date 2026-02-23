@file:Suppress("DEPRECATION")

package com.hyve.ui.components.validation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.unit.dp
import com.hyve.common.compose.HyveShapes
import com.hyve.common.compose.HyveSpacing
import com.hyve.common.compose.HyveThemeColors
import com.hyve.ui.core.domain.elements.UIElement
import com.hyve.ui.canvas.CanvasState
import com.hyve.ui.core.id.ElementId
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.Divider
import org.jetbrains.jewel.ui.component.PopupMenu
import org.jetbrains.jewel.ui.Orientation
import org.jetbrains.jewel.ui.component.TabStrip
import org.jetbrains.jewel.ui.component.TabData
import org.jetbrains.jewel.ui.theme.defaultTabStyle
import org.jetbrains.jewel.ui.icons.AllIconsKeys

/**
 * Collapsible validation panel that displays asset loading status.
 *
 * Shows:
 * - Texture loading status (loaded, loading, failed, not found)
 * - Variable resolution status
 * - Import resolution status
 *
 * Clicking on an item selects the associated element on the canvas.
 */
@Suppress("DEPRECATION")
@Composable
fun ValidationPanel(
    state: ValidationPanelState,
    canvasState: CanvasState,
    modifier: Modifier = Modifier
) {
    val colors = HyveThemeColors.colors
    var isExpanded by remember { mutableStateOf(false) }
    var showHeaderContextMenu by remember { mutableStateOf(false) }
    val clipboardManager = LocalClipboardManager.current

    // Count issues
    val failedTextures = state.failedTextureCount
    val unresolvedVars = state.unresolvedVariableCount
    val failedImports = state.failedImportCount
    val totalIssues = failedTextures + unresolvedVars + failedImports

    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(JewelTheme.globalColors.panelBackground)
    ) {
        Column {
            // Header bar - always visible, with context menu for copy all
            Box {
                ValidationPanelHeader(
                    isExpanded = isExpanded,
                    onToggleExpand = { isExpanded = !isExpanded },
                    onRightClick = { showHeaderContextMenu = true },
                    totalTextures = state.totalTextureCount,
                    loadedTextures = state.textureCountByStatus[AssetLoadStatus.LOADED] ?: 0,
                    failedTextures = failedTextures,
                    unresolvedVariables = unresolvedVars,
                    failedImports = failedImports
                )

                // Context menu for header - copy all
                if (showHeaderContextMenu) {
                    PopupMenu(
                        onDismissRequest = {
                            showHeaderContextMenu = false
                            true
                        },
                        horizontalAlignment = Alignment.Start
                    ) {
                        selectableItem(
                            selected = false,
                            onClick = {
                                clipboardManager.setText(AnnotatedString(state.toFullClipboardText()))
                                showHeaderContextMenu = false
                            }
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(HyveSpacing.sm),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    key = AllIconsKeys.Actions.Copy,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text("Copy All Validation Data")
                            }
                        }
                    }
                }
            }

            // Expandable content
            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                ValidationPanelContent(
                    state = state,
                    canvasState = canvasState
                )
            }
        }
    }
}

/**
 * Header bar showing summary and expand/collapse toggle.
 */
@OptIn(ExperimentalFoundationApi::class, androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
private fun ValidationPanelHeader(
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onRightClick: () -> Unit,
    totalTextures: Int,
    loadedTextures: Int,
    failedTextures: Int,
    unresolvedVariables: Int,
    failedImports: Int
) {
    val colors = HyveThemeColors.colors
    val rotation by animateFloatAsState(if (isExpanded) 180f else 0f)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onToggleExpand,
                onLongClick = onRightClick
            )
            .onPointerEvent(PointerEventType.Press) { event ->
                if (event.button == PointerButton.Secondary) {
                    onRightClick()
                }
            }
            .padding(horizontal = HyveSpacing.sm, vertical = HyveSpacing.xs)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(HyveSpacing.lg),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Title
                Text(
                    text = "Validation",
                    fontWeight = FontWeight.SemiBold,
                    color = JewelTheme.globalColors.text.info
                )

                // Status badges
                Row(
                    horizontalArrangement = Arrangement.spacedBy(HyveSpacing.sm),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Texture status
                    if (totalTextures > 0) {
                        StatusBadge(
                            iconKey = AllIconsKeys.FileTypes.Image,
                            count = loadedTextures,
                            total = totalTextures,
                            isError = failedTextures > 0,
                            label = "Textures"
                        )
                    }

                    // Variable issues
                    if (unresolvedVariables > 0) {
                        IssueBadge(
                            iconKey = AllIconsKeys.Nodes.Variable,
                            count = unresolvedVariables,
                            label = "Variables"
                        )
                    }

                    // Import issues
                    if (failedImports > 0) {
                        IssueBadge(
                            iconKey = AllIconsKeys.FileTypes.Any_type,
                            count = failedImports,
                            label = "Imports"
                        )
                    }

                    // All good indicator
                    if (totalTextures == 0 && unresolvedVariables == 0 && failedImports == 0) {
                        Text(
                            text = "No assets referenced",
                            color = JewelTheme.globalColors.text.info.copy(alpha = 0.6f)
                        )
                    } else if (failedTextures == 0 && unresolvedVariables == 0 && failedImports == 0 && totalTextures > 0) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(HyveSpacing.xs),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                key = AllIconsKeys.Actions.Checked,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = colors.success
                            )
                            Text(
                                text = "All assets loaded",
                                color = colors.success
                            )
                        }
                    }
                }
            }

            // Expand/collapse arrow
            Icon(
                key = AllIconsKeys.General.ArrowUp,
                contentDescription = if (isExpanded) "Collapse" else "Expand",
                modifier = Modifier
                    .size(20.dp)
                    .rotate(rotation),
                tint = JewelTheme.globalColors.text.info
            )
        }
    }
}

/**
 * Status badge showing loaded/total count.
 */
@Composable
private fun StatusBadge(
    iconKey: org.jetbrains.jewel.ui.icon.IconKey,
    count: Int,
    total: Int,
    isError: Boolean,
    label: String
) {
    val colors = HyveThemeColors.colors

    val color = when {
        isError -> JewelTheme.globalColors.text.error
        count == total -> colors.success
        else -> JewelTheme.globalColors.outlines.focused
    }

    Row(
        horizontalArrangement = Arrangement.spacedBy(HyveSpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .background(
                color = color.copy(alpha = 0.1f),
                shape = HyveShapes.card
            )
            .padding(horizontal = HyveSpacing.smd, vertical = HyveSpacing.xxs)
    ) {
        Icon(
            key = iconKey,
            contentDescription = label,
            modifier = Modifier.size(12.dp),
            tint = color
        )
        Text(
            text = "$count/$total",
            color = color,
            fontWeight = FontWeight.Medium
        )
    }
}

/**
 * Issue badge showing error count.
 */
@Composable
private fun IssueBadge(
    iconKey: org.jetbrains.jewel.ui.icon.IconKey,
    count: Int,
    label: String
) {
    val errorColor = JewelTheme.globalColors.text.error

    Row(
        horizontalArrangement = Arrangement.spacedBy(HyveSpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .background(
                color = errorColor.copy(alpha = 0.1f),
                shape = HyveShapes.card
            )
            .padding(horizontal = HyveSpacing.smd, vertical = HyveSpacing.xxs)
    ) {
        Icon(
            key = iconKey,
            contentDescription = label,
            modifier = Modifier.size(12.dp),
            tint = errorColor
        )
        Text(
            text = "$count",
            color = errorColor,
            fontWeight = FontWeight.Medium
        )
    }
}

/**
 * Expandable content showing detailed status lists.
 */
@OptIn(ExperimentalFoundationApi::class, androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
private fun ValidationPanelContent(
    state: ValidationPanelState,
    canvasState: CanvasState
) {
    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf("Textures", "Variables", "Imports", "Warnings")
    var showTabContextMenu by remember { mutableStateOf(false) }
    var contextMenuTab by remember { mutableStateOf(0) }
    val clipboardManager = LocalClipboardManager.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 250.dp)
    ) {
        // Tab row using Jewel TabStrip
        val tabData = tabs.mapIndexed { index, title ->
            val count = when (index) {
                0 -> state.totalTextureCount
                1 -> state.variableStatuses.size
                2 -> state.importStatuses.size
                3 -> state.parseWarnings.size
                else -> 0
            }
            TabData.Default(
                selected = selectedTab == index,
                content = { tabState ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(HyveSpacing.xs),
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .combinedClickable(
                                onClick = { selectedTab = index },
                                onLongClick = {
                                    contextMenuTab = index
                                    showTabContextMenu = true
                                }
                            )
                            .onPointerEvent(PointerEventType.Press) { event ->
                                if (event.button == PointerButton.Secondary) {
                                    contextMenuTab = index
                                    showTabContextMenu = true
                                }
                            }
                    ) {
                        Text(text = title)
                        if (count > 0) {
                            Text(
                                text = "($count)",
                                color = JewelTheme.globalColors.text.info.copy(alpha = 0.6f)
                            )
                        }
                    }
                },
                closable = false,
                onClick = { selectedTab = index }
            )
        }

        TabStrip(tabs = tabData, style = JewelTheme.defaultTabStyle)

        // Context menu for tabs
        if (showTabContextMenu) {
            PopupMenu(
                onDismissRequest = {
                    showTabContextMenu = false
                    true
                },
                horizontalAlignment = Alignment.Start
            ) {
                selectableItem(
                    selected = false,
                    onClick = {
                        val text = when (contextMenuTab) {
                            0 -> state.texturesToClipboardText()
                            1 -> state.variablesToClipboardText()
                            2 -> state.importsToClipboardText()
                            3 -> state.warningsToClipboardText()
                            else -> ""
                        }
                        clipboardManager.setText(AnnotatedString(text))
                        showTabContextMenu = false
                    }
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(HyveSpacing.sm),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            key = AllIconsKeys.Actions.Copy,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Text("Copy ${tabs[contextMenuTab]}")
                    }
                }
            }
        }

        Divider(orientation = Orientation.Horizontal, color = JewelTheme.globalColors.borders.normal.copy(alpha = 0.5f))

        // Tab content
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            when (selectedTab) {
                0 -> TextureStatusList(state, canvasState)
                1 -> VariableStatusList(state)
                2 -> ImportStatusList(state)
                3 -> WarningsList(state)
            }
        }
    }
}

/**
 * List of texture loading statuses.
 */
@Composable
private fun TextureStatusList(
    state: ValidationPanelState,
    canvasState: CanvasState
) {
    val textures = state.textureStatuses.values.toList()

    if (textures.isEmpty()) {
        EmptyStateMessage("No textures referenced in this document")
        return
    }

    // Sort: failed first, then loading, then loaded
    val sorted = textures.sortedWith(
        compareBy(
            { when (it.status) {
                AssetLoadStatus.FAILED -> 0
                AssetLoadStatus.NOT_FOUND -> 1
                AssetLoadStatus.LOADING -> 2
                AssetLoadStatus.LOADED -> 3
            }},
            { it.originalPath }
        )
    )

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(HyveSpacing.sm),
        verticalArrangement = Arrangement.spacedBy(HyveSpacing.xs)
    ) {
        items(sorted, key = { it.resolvedPath }) { texture ->
            TextureStatusRow(
                texture = texture,
                onClick = {
                    // Find and select the element that uses this texture
                    texture.elementId?.let { elementId ->
                        val root = canvasState.rootElement.value
                        root?.findDescendantById(ElementId(elementId))?.let { element ->
                            canvasState.selectElement(element)
                        }
                    }
                }
            )
        }
    }
}

/**
 * Row showing a single texture status.
 */
@OptIn(ExperimentalFoundationApi::class, androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
private fun TextureStatusRow(
    texture: TextureStatus,
    onClick: () -> Unit
) {
    val colors = HyveThemeColors.colors
    var showContextMenu by remember { mutableStateOf(false) }
    val clipboardManager = LocalClipboardManager.current

    val statusColor = when (texture.status) {
        AssetLoadStatus.LOADED -> colors.success
        AssetLoadStatus.LOADING -> JewelTheme.globalColors.outlines.focused
        AssetLoadStatus.NOT_FOUND -> JewelTheme.globalColors.text.error
        AssetLoadStatus.FAILED -> JewelTheme.globalColors.text.error
    }

    val statusIconKey = when (texture.status) {
        AssetLoadStatus.LOADED -> AllIconsKeys.Actions.Checked
        AssetLoadStatus.LOADING -> AllIconsKeys.Process.Step_1
        AssetLoadStatus.NOT_FOUND -> AllIconsKeys.General.Error
        AssetLoadStatus.FAILED -> AllIconsKeys.General.Error
    }

    Box {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = { showContextMenu = true }
                )
                .onPointerEvent(PointerEventType.Press) { event ->
                    if (event.button == PointerButton.Secondary) {
                        showContextMenu = true
                    }
                }
                .background(JewelTheme.globalColors.panelBackground, HyveShapes.card)
                .border(1.dp, JewelTheme.globalColors.borders.normal.copy(alpha = 0.3f), HyveShapes.card)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(HyveSpacing.sm),
                horizontalArrangement = Arrangement.spacedBy(HyveSpacing.sm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Status icon
                Icon(
                    key = statusIconKey,
                    contentDescription = texture.status.name,
                    modifier = Modifier.size(16.dp),
                    tint = statusColor
                )

                // Path info
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    // Original path
                    Text(
                        text = texture.originalPath,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    // Show resolved path if different
                    if (texture.resolvedPath != texture.originalPath) {
                        Text(
                            text = "-> ${texture.resolvedPath}",
                            fontFamily = FontFamily.Monospace,
                            color = JewelTheme.globalColors.text.info.copy(alpha = 0.6f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    // Error message
                    texture.errorMessage?.let { error ->
                        Text(
                            text = error,
                            color = JewelTheme.globalColors.text.error,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // Element info
                if (texture.elementId != null || texture.elementType != null) {
                    Box(
                        modifier = Modifier
                            .background(JewelTheme.globalColors.panelBackground, HyveShapes.card)
                            .border(1.dp, JewelTheme.globalColors.borders.normal, HyveShapes.card)
                    ) {
                        Text(
                            text = texture.elementId ?: texture.elementType ?: "",
                            color = JewelTheme.globalColors.text.info,
                            modifier = Modifier.padding(horizontal = HyveSpacing.smd, vertical = HyveSpacing.xxs)
                        )
                    }
                }
            }
        }

        // Context menu
        if (showContextMenu) {
            PopupMenu(
                onDismissRequest = {
                    showContextMenu = false
                    true
                },
                horizontalAlignment = Alignment.Start
            ) {
                selectableItem(
                    selected = false,
                    onClick = {
                        clipboardManager.setText(AnnotatedString(texture.toClipboardText()))
                        showContextMenu = false
                    }
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(HyveSpacing.sm),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            key = AllIconsKeys.Actions.Copy,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Text("Copy")
                    }
                }
            }
        }
    }
}

/**
 * List of variable resolution statuses.
 */
@Composable
private fun VariableStatusList(state: ValidationPanelState) {
    val variables = state.variableStatuses

    if (variables.isEmpty()) {
        EmptyStateMessage("No variable issues detected")
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(HyveSpacing.sm),
        verticalArrangement = Arrangement.spacedBy(HyveSpacing.xs)
    ) {
        items(variables, key = { it.name }) { variable ->
            VariableStatusRow(variable)
        }
    }
}

/**
 * Row showing a single variable status.
 */
@OptIn(ExperimentalFoundationApi::class, androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
private fun VariableStatusRow(variable: VariableStatus) {
    val colors = HyveThemeColors.colors
    var showContextMenu by remember { mutableStateOf(false) }
    val clipboardManager = LocalClipboardManager.current
    val isResolved = variable.isResolved
    val color = if (isResolved) colors.success else JewelTheme.globalColors.text.error

    Box {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = {},
                    onLongClick = { showContextMenu = true }
                )
                .onPointerEvent(PointerEventType.Press) { event ->
                    if (event.button == PointerButton.Secondary) {
                        showContextMenu = true
                    }
                }
                .background(JewelTheme.globalColors.panelBackground, HyveShapes.card)
                .border(1.dp, JewelTheme.globalColors.borders.normal.copy(alpha = 0.3f), HyveShapes.card)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(HyveSpacing.sm),
                horizontalArrangement = Arrangement.spacedBy(HyveSpacing.sm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    key = if (isResolved) AllIconsKeys.Actions.Checked else AllIconsKeys.General.Warning,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = color
                )

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = variable.name,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Medium
                    )

                    if (isResolved && variable.resolvedValue != null) {
                        Text(
                            text = "= ${variable.resolvedValue}",
                            fontFamily = FontFamily.Monospace,
                            color = JewelTheme.globalColors.text.info.copy(alpha = 0.7f)
                        )
                    }

                    variable.message?.let { msg ->
                        Text(
                            text = msg,
                            color = if (isResolved) JewelTheme.globalColors.text.info else JewelTheme.globalColors.text.error,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        // Context menu
        if (showContextMenu) {
            PopupMenu(
                onDismissRequest = {
                    showContextMenu = false
                    true
                },
                horizontalAlignment = Alignment.Start
            ) {
                selectableItem(
                    selected = false,
                    onClick = {
                        clipboardManager.setText(AnnotatedString(variable.toClipboardText()))
                        showContextMenu = false
                    }
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(HyveSpacing.sm),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            key = AllIconsKeys.Actions.Copy,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Text("Copy")
                    }
                }
            }
        }
    }
}

/**
 * List of import statuses.
 */
@Composable
private fun ImportStatusList(state: ValidationPanelState) {
    val imports = state.importStatuses

    if (imports.isEmpty()) {
        EmptyStateMessage("No import issues detected")
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(HyveSpacing.sm),
        verticalArrangement = Arrangement.spacedBy(HyveSpacing.xs)
    ) {
        items(imports, key = { it.alias }) { import ->
            ImportStatusRow(import)
        }
    }
}

/**
 * Row showing a single import status.
 */
@OptIn(ExperimentalFoundationApi::class, androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
private fun ImportStatusRow(import: ImportStatus) {
    val colors = HyveThemeColors.colors
    var showContextMenu by remember { mutableStateOf(false) }
    val clipboardManager = LocalClipboardManager.current
    val isResolved = import.isResolved
    val color = if (isResolved) colors.success else JewelTheme.globalColors.text.error

    Box {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = {},
                    onLongClick = { showContextMenu = true }
                )
                .onPointerEvent(PointerEventType.Press) { event ->
                    if (event.button == PointerButton.Secondary) {
                        showContextMenu = true
                    }
                }
                .background(JewelTheme.globalColors.panelBackground, HyveShapes.card)
                .border(1.dp, JewelTheme.globalColors.borders.normal.copy(alpha = 0.3f), HyveShapes.card)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(HyveSpacing.sm),
                horizontalArrangement = Arrangement.spacedBy(HyveSpacing.sm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    key = if (isResolved) AllIconsKeys.Actions.Checked else AllIconsKeys.General.Error,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = color
                )

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(HyveSpacing.sm),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = import.alias,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "= \"${import.path}\"",
                            fontFamily = FontFamily.Monospace,
                            color = JewelTheme.globalColors.text.info.copy(alpha = 0.7f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    if (isResolved && import.resolvedPath != null) {
                        Text(
                            text = "-> ${import.resolvedPath}",
                            fontFamily = FontFamily.Monospace,
                            color = JewelTheme.globalColors.text.info.copy(alpha = 0.6f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    import.errorMessage?.let { error ->
                        Text(
                            text = error,
                            color = JewelTheme.globalColors.text.error,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        // Context menu
        if (showContextMenu) {
            PopupMenu(
                onDismissRequest = {
                    showContextMenu = false
                    true
                },
                horizontalAlignment = Alignment.Start
            ) {
                selectableItem(
                    selected = false,
                    onClick = {
                        clipboardManager.setText(AnnotatedString(import.toClipboardText()))
                        showContextMenu = false
                    }
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(HyveSpacing.sm),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            key = AllIconsKeys.Actions.Copy,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Text("Copy")
                    }
                }
            }
        }
    }
}

/**
 * List of parse warnings.
 */
@Composable
private fun WarningsList(state: ValidationPanelState) {
    val warnings = state.parseWarnings

    if (warnings.isEmpty()) {
        EmptyStateMessage("No warnings from last parse")
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(HyveSpacing.sm),
        verticalArrangement = Arrangement.spacedBy(HyveSpacing.xs)
    ) {
        itemsIndexed(warnings, key = { index, _ -> index }) { _, warning ->
            WarningRow(warning)
        }
    }
}

/**
 * Row showing a single warning.
 */
@OptIn(ExperimentalFoundationApi::class, androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
private fun WarningRow(warning: String) {
    val colors = HyveThemeColors.colors
    var showContextMenu by remember { mutableStateOf(false) }
    val clipboardManager = LocalClipboardManager.current

    Box {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = {},
                    onLongClick = { showContextMenu = true }
                )
                .onPointerEvent(PointerEventType.Press) { event ->
                    if (event.button == PointerButton.Secondary) {
                        showContextMenu = true
                    }
                }
                .background(JewelTheme.globalColors.panelBackground, HyveShapes.card)
                .border(1.dp, JewelTheme.globalColors.borders.normal.copy(alpha = 0.3f), HyveShapes.card)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(HyveSpacing.sm),
                horizontalArrangement = Arrangement.spacedBy(HyveSpacing.sm),
                verticalAlignment = Alignment.Top
            ) {
                Icon(
                    key = AllIconsKeys.General.Warning,
                    contentDescription = "Warning",
                    modifier = Modifier.size(16.dp),
                    tint = colors.warning
                )
                Text(
                    text = warning,
                    color = JewelTheme.globalColors.text.normal,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // Context menu
        if (showContextMenu) {
            PopupMenu(
                onDismissRequest = {
                    showContextMenu = false
                    true
                },
                horizontalAlignment = Alignment.Start
            ) {
                selectableItem(
                    selected = false,
                    onClick = {
                        clipboardManager.setText(AnnotatedString(warning))
                        showContextMenu = false
                    }
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(HyveSpacing.sm),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            key = AllIconsKeys.Actions.Copy,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Text("Copy")
                    }
                }
            }
        }
    }
}

/**
 * Empty state message for lists with no items.
 */
@Composable
private fun EmptyStateMessage(message: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(HyveSpacing.lg),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = message,
            color = JewelTheme.globalColors.text.info.copy(alpha = 0.6f)
        )
    }
}

// ============== Clipboard Formatting Functions ==============

/**
 * Format a texture status for clipboard.
 */
private fun TextureStatus.toClipboardText(): String = buildString {
    append("[${status.name}] $originalPath")
    if (resolvedPath != originalPath) {
        append(" -> $resolvedPath")
    }
    if (elementId != null) {
        append(" (element: $elementId)")
    }
    errorMessage?.let { append(" ERROR: $it") }
}

/**
 * Format a variable status for clipboard.
 */
private fun VariableStatus.toClipboardText(): String = buildString {
    append(if (isResolved) "[RESOLVED]" else "[UNRESOLVED]")
    append(" $name")
    if (isResolved && resolvedValue != null) {
        append(" = $resolvedValue")
    }
    message?.let { append(" - $it") }
}

/**
 * Format an import status for clipboard.
 */
private fun ImportStatus.toClipboardText(): String = buildString {
    append(if (isResolved) "[RESOLVED]" else "[UNRESOLVED]")
    append(" $alias = \"$path\"")
    if (isResolved && resolvedPath != null) {
        append(" -> $resolvedPath")
    }
    errorMessage?.let { append(" ERROR: $it") }
}

/**
 * Format all validation data for clipboard.
 */
internal fun ValidationPanelState.toFullClipboardText(): String = buildString {
    appendLine("=== VALIDATION REPORT ===")
    appendLine()

    // Textures
    val textures = textureStatuses.values.toList()
    appendLine("--- Textures (${textures.size}) ---")
    if (textures.isEmpty()) {
        appendLine("No textures referenced")
    } else {
        textures.sortedBy { it.originalPath }.forEach { texture ->
            appendLine(texture.toClipboardText())
        }
    }
    appendLine()

    // Variables
    appendLine("--- Variables (${variableStatuses.size}) ---")
    if (variableStatuses.isEmpty()) {
        appendLine("No variable issues")
    } else {
        variableStatuses.forEach { variable ->
            appendLine(variable.toClipboardText())
        }
    }
    appendLine()

    // Imports
    appendLine("--- Imports (${importStatuses.size}) ---")
    if (importStatuses.isEmpty()) {
        appendLine("No import issues")
    } else {
        importStatuses.forEach { import ->
            appendLine(import.toClipboardText())
        }
    }
    appendLine()

    // Warnings
    appendLine("--- Warnings (${parseWarnings.size}) ---")
    if (parseWarnings.isEmpty()) {
        appendLine("No warnings")
    } else {
        parseWarnings.forEach { warning ->
            appendLine(warning)
        }
    }
}

/**
 * Format textures tab data for clipboard.
 */
internal fun ValidationPanelState.texturesToClipboardText(): String = buildString {
    appendLine("--- Textures ---")
    val textures = textureStatuses.values.toList()
    if (textures.isEmpty()) {
        appendLine("No textures referenced")
    } else {
        textures.sortedBy { it.originalPath }.forEach { texture ->
            appendLine(texture.toClipboardText())
        }
    }
}

/**
 * Format variables tab data for clipboard.
 */
internal fun ValidationPanelState.variablesToClipboardText(): String = buildString {
    appendLine("--- Variables ---")
    if (variableStatuses.isEmpty()) {
        appendLine("No variable issues")
    } else {
        variableStatuses.forEach { variable ->
            appendLine(variable.toClipboardText())
        }
    }
}

/**
 * Format imports tab data for clipboard.
 */
internal fun ValidationPanelState.importsToClipboardText(): String = buildString {
    appendLine("--- Imports ---")
    if (importStatuses.isEmpty()) {
        appendLine("No import issues")
    } else {
        importStatuses.forEach { import ->
            appendLine(import.toClipboardText())
        }
    }
}

/**
 * Format warnings tab data for clipboard.
 */
internal fun ValidationPanelState.warningsToClipboardText(): String = buildString {
    appendLine("--- Warnings ---")
    if (parseWarnings.isEmpty()) {
        appendLine("No warnings")
    } else {
        parseWarnings.forEach { warning ->
            appendLine(warning)
        }
    }
}
