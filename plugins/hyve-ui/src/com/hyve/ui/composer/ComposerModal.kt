// Copyright 2026 Hyve. All rights reserved.
package com.hyve.ui.composer

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.unit.dp
import com.hyve.common.compose.HyveOpacity
import com.hyve.common.compose.HyveShapes
import com.hyve.common.compose.HyveThemeColors
import com.hyve.ui.composer.model.ImportableFile
import com.hyve.ui.composer.model.PopoverKind
import com.hyve.ui.core.domain.elements.UIElement
import com.hyve.ui.composer.model.WordBankItem
import com.hyve.ui.composer.model.canDrop
import com.hyve.ui.composer.model.fillModeForDrop
import com.hyve.ui.composer.model.validate
import com.hyve.ui.composer.validation.ProblemsPanel
import com.hyve.ui.composer.popover.AddImportPopover
import com.hyve.ui.composer.popover.AddStylePopover
import com.hyve.ui.composer.popover.AddVariablePopover
import com.hyve.ui.composer.codegen.CodeGenPanel
import com.hyve.ui.composer.preview.PreviewPanel
import com.hyve.ui.services.assets.AssetLoader
import com.hyve.ui.composer.propertyform.PropertyFormPanel
import com.hyve.ui.composer.styleeditor.StyleEditorPanel
import com.hyve.ui.composer.wordbank.WordBankPanel
import com.hyve.ui.composer.wordbank.WordBankState
import com.hyve.ui.editor.LocalEditorDependencies
import com.hyve.ui.schema.discovery.TupleFieldInfo
import org.jetbrains.jewel.ui.Orientation
import org.jetbrains.jewel.ui.component.Divider

/**
 * The Property Composer modal — the outermost shell.
 *
 * Renders a semi-transparent overlay covering the editor, with a centered
 * modal window containing the header bar, optional tab bar, three-panel body,
 * and keyboard shortcuts.
 *
 * ## Spec Reference
 * - FR-1: Modal Overlay
 * - FR-2: Modal Window
 * - FR-5: Three-Panel Body Layout
 * - FR-8: Keyboard Navigation
 *
 * ## Panel Slots
 * The left panel (Word Bank) is implemented via [WordBankPanel].
 * The center panel (Madlibs Form) is implemented via [PropertyFormPanel] for the
 * element tab; style editor tabs use [StyleEditorPanel] (spec 04).
 * The right panel (Preview) is implemented via [PreviewPanel] (spec 05).
 */
@Composable
fun ComposerModal(
    state: ComposerModalState,
    wordBankState: WordBankState,
    sourceElement: UIElement? = null,
    importableFiles: List<ImportableFile> = emptyList(),
    assetLoader: AssetLoader? = null,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = HyveThemeColors.colors
    val element by state.element
    val openStyleTabs by state.openStyleTabs
    val activeTab by state.activeTab
    val showCode by state.showCode
    val editingId by state.editingId
    val collapsedCategories by state.collapsedCategories
    val revealedSlots by state.revealedSlots
    val styleData by state.styleData
    val collapsedStates by state.collapsedStates

    // ID editing state — local text buffer so we can revert on cancel
    var idBuffer by remember(element.id) { mutableStateOf(element.id) }

    // Drag-and-drop state (spec 07, FR-1)
    var dragItem by remember { mutableStateOf<WordBankItem?>(null) }
    var hoveredSlot by remember { mutableStateOf<String?>(null) }
    val isDragging = dragItem != null

    // Validation panel collapse state (spec 09, FR-1)
    var problemsExpanded by remember { mutableStateOf(true) }

    // Tuple field lookup — build once from the schema registry
    val deps = LocalEditorDependencies.current
    val tupleFieldLookup = remember(deps) {
        val registry = deps.schemaProvider.getOrDiscoverSchema()
        val map = mutableMapOf<String, List<TupleFieldInfo>>()
        registry.getAllElementTypes().forEach { type ->
            registry.getPropertiesForElement(type).forEach { prop ->
                if (prop.tupleFields.isNotEmpty() && prop.name !in map) {
                    map[prop.name] = prop.tupleFields
                }
            }
        }
        map
    }

    // Drop handler: when drag ends, check if hoveredSlot is set and compatible.
    // If so, apply the fill mode. Always clears drag state afterward.
    val handleDragEnd: () -> Unit = {
        val item = dragItem
        val slotKey = hoveredSlot
        if (item != null && slotKey != null) {
            val parts = slotKey.split(":")
            if (parts.size == 3) {
                // Style slot: "styleName:stateName:slotName"
                val (sName, stateName, slotName) = parts
                val tab = state.styleData.value[sName]
                val targetSlot = tab?.states
                    ?.find { it.name == stateName }
                    ?.slots?.find { it.name == slotName }
                if (targetSlot != null && canDrop(item, targetSlot)) {
                    val (fillMode, value) = fillModeForDrop(item)
                    state.updateStyleSlot(sName, stateName, slotName, fillMode = fillMode, value = value)
                }
            } else {
                // Element slot: key is just the slot name
                val targetSlot = element.slots.find { it.name == slotKey }
                if (targetSlot != null && canDrop(item, targetSlot)) {
                    val (fillMode, value) = fillModeForDrop(item)
                    state.updateSlot(slotKey, fillMode = fillMode, value = value)
                }
            }
        }
        dragItem = null
        hoveredSlot = null
    }

    // Entrance animation
    var appeared by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { appeared = true }

    val animatedAlpha by animateFloatAsState(
        targetValue = if (appeared) 1f else 0f,
        animationSpec = tween(durationMillis = 200)
    )
    val animatedScale by animateFloatAsState(
        targetValue = if (appeared) 1f else 0.97f,
        animationSpec = tween(durationMillis = 200)
    )

    // Full-viewport overlay
    Box(
        modifier = modifier
            .fillMaxSize()
            .alpha(animatedAlpha)
            .background(colors.deepNight.copy(alpha = HyveOpacity.strong))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) {
                // FR-1: Clicking overlay closes modal
                onClose()
            }
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when {
                    // FR-8: Escape — cancel drag, then close popover, then cancel ID edit, then close modal
                    event.key == Key.Escape -> {
                        when {
                            dragItem != null -> {
                                dragItem = null
                                hoveredSlot = null
                            }
                            wordBankState.activePopover.value != null -> {
                                wordBankState.closePopover()
                            }
                            editingId -> {
                                state.cancelEditingId()
                                idBuffer = element.id
                            }
                            else -> onClose()
                        }
                        true
                    }
                    // FR-8: Ctrl+Shift+C toggles code preview
                    event.key == Key.C
                        && event.isCtrlPressed
                        && event.isShiftPressed -> {
                        state.toggleCode()
                        true
                    }
                    else -> false
                }
            },
        contentAlignment = Alignment.Center
    ) {
        // Modal window — FR-2
        Box(
            modifier = Modifier
                .scale(animatedScale)
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.85f)
                .widthIn(max = 1200.dp)
                .heightIn(max = 800.dp)
                .clip(HyveShapes.chip)
                .background(colors.deepNight)
                .border(1.dp, colors.slate, HyveShapes.chip)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    // FR-2: Clicking inside modal does NOT close it
                }
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header bar — FR-3, FR-4
                ComposerHeader(
                    elementType = element.type.value,
                    elementId = if (editingId) idBuffer else element.id,
                    filledCount = element.filledCount,
                    totalSlots = element.slots.size,
                    editingId = editingId,
                    onIdClick = {
                        idBuffer = element.id
                        state.startEditingId()
                    },
                    onIdChange = { idBuffer = it },
                    onIdCommit = { state.commitId(idBuffer) },
                    onIdCancel = {
                        idBuffer = element.id
                        state.cancelEditingId()
                    },
                    onClose = onClose
                )

                Divider(orientation = Orientation.Horizontal, color = colors.slate)

                // Body — FR-5: Three-panel layout
                Row(modifier = Modifier.fillMaxSize()) {
                    // Left panel: Word Bank (240dp)
                    WordBankPanel(
                        state = wordBankState,
                        onDragStart = { item -> dragItem = item },
                        onDragEnd = handleDragEnd,
                        dragItem = dragItem,
                        onStyleEdit = { styleName -> state.openStyleTab(styleName) },
                        onAddVariable = { wordBankState.openPopover(PopoverKind.ADD_VARIABLE) },
                        onAddStyle = { wordBankState.openPopover(PopoverKind.ADD_STYLE) },
                        onAddImport = { wordBankState.openPopover(PopoverKind.ADD_IMPORT) },
                    )

                    Divider(orientation = Orientation.Vertical, color = colors.slate)

                    // Center panel: Madlibs Form (flex)
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    ) {
                        // Tab bar — FR-6 (only when style tabs are open)
                        if (openStyleTabs.isNotEmpty()) {
                            ComposerTabBar(
                                elementType = element.type.value,
                                elementId = element.id,
                                openStyleTabs = openStyleTabs,
                                activeTab = activeTab,
                                onTabSelect = { state.setActiveTab(it) },
                                onTabClose = { state.closeStyleTab(it) }
                            )
                            Divider(orientation = Orientation.Horizontal, color = colors.slate)
                        }

                        // Center content — property form or style editor
                        if (activeTab == null) {
                            PropertyFormPanel(
                                element = element,
                                collapsedCategories = collapsedCategories,
                                onToggleCategory = { state.toggleCategory(it) },
                                knownFieldsProvider = { slotName -> tupleFieldLookup[slotName] ?: emptyList() },
                                onUpdateSlot = { name, mode, value ->
                                    state.updateSlot(name, fillMode = mode, value = value)
                                },
                                onUpdateSlotValue = { name, value ->
                                    state.updateSlot(name, value = value)
                                },
                                onUpdateAnchorField = { name, field, value ->
                                    state.updateSlotAnchorValues(name, field, value)
                                },
                                onUpdateTupleField = { name, field, value ->
                                    state.updateSlotTupleValues(name, field, value)
                                },
                                onAddTupleField = { name, field ->
                                    state.addSlotTupleField(name, field)
                                },
                                onRemoveTupleField = { name, field ->
                                    state.removeSlotTupleField(name, field)
                                },
                                onClearSlot = { state.clearSlot(it) },
                                onStyleNavigate = { state.openStyleTab(it) },
                                revealedSlots = revealedSlots,
                                onRevealSlot = { state.revealSlot(it) },
                                isDragging = isDragging,
                                dragItem = dragItem,
                                hoveredSlot = hoveredSlot,
                                onHoverSlot = { hoveredSlot = it },
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                            )

                            // Problems Panel (spec 09) — below property form, element tab only
                            val problems = validate(element, wordBankState.items.value)
                            if (problems.isNotEmpty()) {
                                Divider(orientation = Orientation.Horizontal, color = colors.slate)
                                ProblemsPanel(
                                    problems = problems,
                                    expanded = problemsExpanded,
                                    onToggleExpanded = { problemsExpanded = !problemsExpanded },
                                )
                            }
                        } else {
                            StyleEditorPanel(
                                styleName = activeTab ?: "",
                                styleTab = activeTab?.let { styleData[it] },
                                collapsedStates = collapsedStates,
                                onToggleState = { state.toggleStateCollapse(it) },
                                onUpdateSlotValue = { stateName, slotName, value ->
                                    state.updateStyleSlot(activeTab!!, stateName, slotName, value = value)
                                },
                                onUpdateSlotFillMode = { stateName, slotName, fillMode, value ->
                                    state.updateStyleSlot(activeTab!!, stateName, slotName, fillMode = fillMode, value = value)
                                },
                                onUpdateAnchorField = { stateName, slotName, field, value ->
                                    state.updateStyleSlotAnchorValues(activeTab!!, stateName, slotName, field, value)
                                },
                                onUpdateTupleField = { stateName, slotName, field, value ->
                                    state.updateStyleSlotTupleValues(activeTab!!, stateName, slotName, field, value)
                                },
                                onAddTupleField = { stateName, slotName, field ->
                                    state.addStyleSlotTupleField(activeTab!!, stateName, slotName, field)
                                },
                                onRemoveTupleField = { stateName, slotName, field ->
                                    state.removeStyleSlotTupleField(activeTab!!, stateName, slotName, field)
                                },
                                onClearSlot = { stateName, slotName ->
                                    state.clearStyleSlot(activeTab!!, stateName, slotName)
                                },
                                onStyleNavigate = { state.openStyleTab(it) },
                                onAddState = { newStateName ->
                                    state.addCustomState(activeTab!!, newStateName)
                                },
                                isDragging = isDragging,
                                dragItem = dragItem,
                                hoveredSlot = hoveredSlot,
                                onHoverSlot = { hoveredSlot = it },
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                            )
                        }
                    }

                    Divider(orientation = Orientation.Vertical, color = colors.slate)

                    // Right panel (260dp) — Preview + optional Code Preview
                    Column(
                        modifier = Modifier
                            .width(260.dp)
                            .fillMaxHeight()
                            .background(colors.midnight)
                    ) {
                        // Element preview (fills available height) — spec 05
                        PreviewPanel(
                            element = element,
                            sourceElement = sourceElement,
                            assetLoader = assetLoader,
                            showCode = showCode,
                            onCodeToggle = { state.toggleCode() },
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                        )

                        // Code preview (conditional, max 40%) — spec 08
                        if (showCode) {
                            Divider(orientation = Orientation.Horizontal, color = colors.slate)
                            CodeGenPanel(
                                element = element,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .fillMaxHeight(0.4f)
                            )
                        }
                    }
                }
            }

            // Popover overlay — spec 06
            val activePopover by wordBankState.activePopover
            activePopover?.let { kind ->
                when (kind) {
                    PopoverKind.ADD_VARIABLE -> AddVariablePopover(
                        onConfirm = { item ->
                            wordBankState.addItem(item)
                            wordBankState.closePopover()
                        },
                        onDismiss = { wordBankState.closePopover() },
                    )
                    PopoverKind.ADD_STYLE -> AddStylePopover(
                        onConfirm = { item, styleTab ->
                            wordBankState.addItem(item)
                            state.setStyleData(item.name, styleTab)
                            wordBankState.closePopover()
                        },
                        onDismiss = { wordBankState.closePopover() },
                    )
                    PopoverKind.ADD_IMPORT -> AddImportPopover(
                        importableFiles = importableFiles,
                        onConfirm = { items ->
                            items.forEach { wordBankState.addItem(it) }
                            wordBankState.closePopover()
                        },
                        onDismiss = { wordBankState.closePopover() },
                    )
                }
            }
        }
    }
}
