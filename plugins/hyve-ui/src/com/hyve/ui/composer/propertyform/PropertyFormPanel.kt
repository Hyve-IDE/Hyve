// Copyright 2026 Hyve. All rights reserved.
package com.hyve.ui.composer.propertyform

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.hyve.common.compose.HyveSpacing
import com.hyve.common.compose.HyveThemeColors
import com.hyve.ui.composer.model.ElementDefinition
import com.hyve.ui.composer.model.FillMode
import com.hyve.ui.composer.model.WordBankItem
import com.hyve.ui.composer.model.canDrop
import com.hyve.ui.schema.discovery.TupleFieldInfo

/**
 * The center panel property form for the Composer.
 *
 * Renders the element's property slots as a scrollable, categorized list
 * of property lines. Only shows properties that have values or have been
 * explicitly revealed via the "+" button. Each line adapts its editor UI
 * to the property's type and fill mode.
 *
 * ## Spec Reference
 * - FR-1: Category Grouping
 * - FR-2 through FR-10: Property line behavior
 *
 * @param element The element definition with all property slots
 * @param collapsedCategories Set of collapsed category display names
 * @param onToggleCategory Called when a category header is clicked
 * @param onUpdateSlot Called to update a slot's fill mode and value: (slotName, fillMode, value)
 * @param onUpdateSlotValue Called to update just a slot's value: (slotName, value)
 * @param onUpdateAnchorField Called for anchor field changes: (slotName, fieldKey, newValue)
 * @param onClearSlot Called to clear a slot: (slotName)
 * @param onStyleNavigate Called when a style navigation arrow is clicked: (styleName)
 * @param revealedSlots Slot names the user has explicitly revealed via "+"
 * @param onRevealSlot Called when a user picks a property from the "+" popup
 * @param isDragging Whether a drag operation is currently in progress
 * @param dragItem The item currently being dragged, or null
 * @param hoveredSlot The slot key currently under the cursor, or null
 * @param onHoverSlot Called when a slot is entered or exited during drag: (slotKey?)
 * @param modifier Modifier for the outer container
 */
@Composable
fun PropertyFormPanel(
    element: ElementDefinition,
    collapsedCategories: Set<String>,
    onToggleCategory: (String) -> Unit,
    onUpdateSlot: (slotName: String, fillMode: FillMode, value: String) -> Unit,
    onUpdateSlotValue: (slotName: String, value: String) -> Unit,
    onUpdateAnchorField: (slotName: String, field: String, value: String) -> Unit,
    onUpdateTupleField: (slotName: String, field: String, value: String) -> Unit = { _, _, _ -> },
    onAddTupleField: (slotName: String, field: String) -> Unit = { _, _ -> },
    onRemoveTupleField: (slotName: String, field: String) -> Unit = { _, _ -> },
    onClearSlot: (slotName: String) -> Unit,
    onStyleNavigate: (styleName: String) -> Unit,
    revealedSlots: Set<String> = emptySet(),
    onRevealSlot: (String) -> Unit = {},
    knownFieldsProvider: (slotName: String) -> List<TupleFieldInfo> = { emptyList() },
    isDragging: Boolean = false,
    dragItem: WordBankItem? = null,
    hoveredSlot: String? = null,
    onHoverSlot: (String?) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val colors = HyveThemeColors.colors

    Column(
        modifier = modifier
            .background(colors.midnight)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = HyveSpacing.lg, vertical = HyveSpacing.sm),
    ) {
        for ((category, slots) in element.slotsByCategory) {
            if (slots.isEmpty()) continue

            val visibleSlots = slots.filter { it.fillMode != FillMode.EMPTY || it.name in revealedSlots }
            val unsetSlots = slots.filter { it.fillMode == FillMode.EMPTY && it.name !in revealedSlots }
            val filledCount = slots.count { it.fillMode != FillMode.EMPTY }
            val isCollapsed = category.displayName in collapsedCategories

            // Show header if there are visible slots OR unset slots (so "+" is accessible)
            if (visibleSlots.isEmpty() && unsetSlots.isEmpty()) continue

            CategoryHeader(
                category = category,
                filledCount = filledCount,
                totalCount = slots.size,
                isCollapsed = isCollapsed,
                onToggle = { onToggleCategory(category.displayName) },
                unsetSlots = unsetSlots,
                onRevealSlot = onRevealSlot,
            )

            if (!isCollapsed) {
                Column(
                    modifier = Modifier.padding(start = HyveSpacing.sm, bottom = HyveSpacing.sm),
                ) {
                    for (slot in visibleSlots) {
                        val compatible = dragItem != null && canDrop(dragItem, slot)
                        PropertyLine(
                            slot = slot,
                            onUpdateValue = { value -> onUpdateSlotValue(slot.name, value) },
                            onUpdateFillMode = { mode, value -> onUpdateSlot(slot.name, mode, value) },
                            onAnchorFieldChange = { field, value -> onUpdateAnchorField(slot.name, field, value) },
                            onTupleFieldChange = { field, value -> onUpdateTupleField(slot.name, field, value) },
                            onTupleFieldAdd = { field -> onAddTupleField(slot.name, field) },
                            onTupleFieldRemove = { field -> onRemoveTupleField(slot.name, field) },
                            knownFields = knownFieldsProvider(slot.name),
                            onClear = { onClearSlot(slot.name) },
                            onStyleNavigate = onStyleNavigate,
                            isDropTarget = hoveredSlot == slot.name && compatible,
                            isInvalidDrop = hoveredSlot == slot.name && dragItem != null && !compatible,
                            isDragging = isDragging,
                            isDropHint = compatible
                                && slot.fillMode == FillMode.EMPTY
                                && hoveredSlot != slot.name,
                            onDragEnter = { onHoverSlot(slot.name) },
                            onDragExit = { onHoverSlot(null) },
                        )
                    }
                }
            }
        }
    }
}
