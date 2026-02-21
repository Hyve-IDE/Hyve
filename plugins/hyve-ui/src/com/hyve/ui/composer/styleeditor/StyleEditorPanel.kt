// Copyright 2026 Hyve. All rights reserved.
package com.hyve.ui.composer.styleeditor

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.hyve.common.compose.HyveSpacing
import com.hyve.common.compose.HyveThemeColors
import com.hyve.ui.composer.model.FillMode
import com.hyve.ui.composer.model.StyleTab
import com.hyve.ui.composer.model.WordBankItem
import com.hyve.ui.composer.model.canDrop
import com.hyve.ui.composer.propertyform.PropertyLine
import org.jetbrains.jewel.ui.component.Text

/**
 * The center panel style editor for the Composer (spec 04).
 *
 * Replaces the element property form when a style tab is active. Renders
 * the style's visual states as collapsible sections, each containing
 * PropertyLine components for slot editing. Includes a footer for adding
 * custom states.
 *
 * ## Spec Reference
 * - FR-1: Style Tab Content
 * - FR-3: Visual State Sections
 * - FR-4: State Slot Editing
 * - FR-5: Adding Custom States
 *
 * @param styleName The style name (e.g. "@ButtonStyle")
 * @param styleTab The style data, or null if not found in the data store
 * @param collapsedStates Set of collapsed composite keys ("styleName:stateName")
 * @param onToggleState Called with composite key when a state header is clicked
 * @param onUpdateSlotValue Called to update a slot value: (stateName, slotName, value)
 * @param onUpdateSlotFillMode Called to update fill mode: (stateName, slotName, fillMode, value)
 * @param onUpdateAnchorField Called for anchor edits: (stateName, slotName, field, value)
 * @param onClearSlot Called to clear a slot: (stateName, slotName)
 * @param onStyleNavigate Called when a style navigation arrow is clicked
 * @param onAddState Called with new state name when a custom state is added
 * @param isDragging Whether a drag operation is in progress
 * @param dragItem The item currently being dragged, or null
 * @param hoveredSlot The slot key currently under the cursor, or null
 * @param onHoverSlot Called when a slot is entered or exited during drag: (slotKey?)
 * @param modifier Modifier for the outer container
 */
@Composable
fun StyleEditorPanel(
    styleName: String,
    styleTab: StyleTab?,
    collapsedStates: Set<String>,
    onToggleState: (compositeKey: String) -> Unit,
    onUpdateSlotValue: (stateName: String, slotName: String, value: String) -> Unit,
    onUpdateSlotFillMode: (stateName: String, slotName: String, fillMode: FillMode, value: String) -> Unit,
    onUpdateAnchorField: (stateName: String, slotName: String, field: String, value: String) -> Unit,
    onUpdateTupleField: (stateName: String, slotName: String, field: String, value: String) -> Unit = { _, _, _, _ -> },
    onAddTupleField: (stateName: String, slotName: String, field: String) -> Unit = { _, _, _ -> },
    onRemoveTupleField: (stateName: String, slotName: String, field: String) -> Unit = { _, _, _ -> },
    onClearSlot: (stateName: String, slotName: String) -> Unit,
    onStyleNavigate: (styleName: String) -> Unit,
    onAddState: (newStateName: String) -> Unit,
    isDragging: Boolean = false,
    dragItem: WordBankItem? = null,
    hoveredSlot: String? = null,
    onHoverSlot: (String?) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val colors = HyveThemeColors.colors

    if (styleTab == null) {
        // FR-1 fallback: style data not found
        Box(
            modifier = modifier.background(colors.midnight),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "Style definition not found for $styleName",
                color = colors.textDisabled,
            )
        }
        return
    }

    Column(
        modifier = modifier
            .background(colors.midnight)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = HyveSpacing.lg, vertical = HyveSpacing.sm),
    ) {
        // FR-1: Style header
        StyleTabHeader(
            styleType = styleTab.styleType,
            styleName = styleTab.name,
            stateCount = styleTab.stateCount,
        )

        // FR-3: Visual state sections
        for (state in styleTab.states) {
            val compositeKey = "${styleTab.name}:${state.name}"
            val isCollapsed = compositeKey in collapsedStates

            StateHeader(
                stateName = state.name,
                filledCount = state.filledCount,
                totalCount = state.slots.size,
                isCollapsed = isCollapsed,
                onToggle = { onToggleState(compositeKey) },
            )

            // FR-4: Property slots (when expanded)
            if (!isCollapsed) {
                Column(
                    modifier = Modifier.padding(start = HyveSpacing.sm, bottom = HyveSpacing.sm),
                ) {
                    for (slot in state.slots) {
                        val slotKey = "${styleTab.name}:${state.name}:${slot.name}"
                        val compatible = dragItem != null && canDrop(dragItem, slot)
                        PropertyLine(
                            slot = slot,
                            onUpdateValue = { value ->
                                onUpdateSlotValue(state.name, slot.name, value)
                            },
                            onUpdateFillMode = { mode, value ->
                                onUpdateSlotFillMode(state.name, slot.name, mode, value)
                            },
                            onAnchorFieldChange = { field, value ->
                                onUpdateAnchorField(state.name, slot.name, field, value)
                            },
                            onTupleFieldChange = { field, value ->
                                onUpdateTupleField(state.name, slot.name, field, value)
                            },
                            onTupleFieldAdd = { field ->
                                onAddTupleField(state.name, slot.name, field)
                            },
                            onTupleFieldRemove = { field ->
                                onRemoveTupleField(state.name, slot.name, field)
                            },
                            onClear = { onClearSlot(state.name, slot.name) },
                            onStyleNavigate = onStyleNavigate,
                            isDropTarget = hoveredSlot == slotKey && compatible,
                            isInvalidDrop = hoveredSlot == slotKey && dragItem != null && !compatible,
                            isDragging = isDragging,
                            isDropHint = compatible
                                && slot.fillMode == FillMode.EMPTY
                                && hoveredSlot != slotKey,
                            onDragEnter = { onHoverSlot(slotKey) },
                            onDragExit = { onHoverSlot(null) },
                        )
                    }
                }
            }
        }

        // FR-5: Add custom state
        AddStateSection(
            existingStateNames = styleTab.states.map { it.name }.toSet(),
            onAddState = onAddState,
        )
    }
}
