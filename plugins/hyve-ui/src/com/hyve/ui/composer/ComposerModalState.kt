// Copyright 2026 Hyve. All rights reserved.
package com.hyve.ui.composer

import androidx.compose.runtime.*
import com.hyve.ui.composer.model.ElementDefinition
import com.hyve.ui.composer.model.FillMode
import com.hyve.ui.composer.model.PropertySlot
import com.hyve.ui.composer.model.StyleState
import com.hyve.ui.composer.model.StyleTab
import com.hyve.ui.core.domain.anchor.AnchorValue

/**
 * State holder for the Property Composer modal.
 *
 * Manages all modal-level state: the element being edited, tab navigation,
 * UI toggles, and category collapse state. Created via [rememberComposerModalState].
 *
 * Follows the same pattern as [com.hyve.ui.canvas.CanvasState] — a plain class
 * with [MutableState] fields, no Android ViewModel dependency.
 */
class ComposerModalState(
    initialElement: ElementDefinition,
    initialCollapsedCategories: Set<String> = emptySet(),
) {

    // -- Core element state --

    private val _element = mutableStateOf(initialElement)
    val element: State<ElementDefinition> get() = _element

    // -- Tab state --

    private val _openStyleTabs = mutableStateOf<List<String>>(emptyList())
    val openStyleTabs: State<List<String>> get() = _openStyleTabs

    private val _activeTab = mutableStateOf<String?>(null)
    val activeTab: State<String?> get() = _activeTab

    // -- UI toggles --

    private val _showCode = mutableStateOf(true)
    val showCode: State<Boolean> get() = _showCode

    private val _editingId = mutableStateOf(false)
    val editingId: State<Boolean> get() = _editingId

    private val _collapsedCategories = mutableStateOf(initialCollapsedCategories)
    val collapsedCategories: State<Set<String>> get() = _collapsedCategories

    private val _revealedSlots = mutableStateOf<Set<String>>(emptySet())
    val revealedSlots: State<Set<String>> get() = _revealedSlots

    // -- Style editor data (spec 04, FR-6) --

    private val _styleData = mutableStateOf<Map<String, StyleTab>>(emptyMap())
    val styleData: State<Map<String, StyleTab>> get() = _styleData

    private val _collapsedStates = mutableStateOf<Set<String>>(emptySet())
    val collapsedStates: State<Set<String>> get() = _collapsedStates

    // -- Element mutations --

    fun updateElement(element: ElementDefinition) {
        _element.value = element
    }

    /**
     * Update a single slot by name, merging the provided fields.
     */
    fun updateSlot(slotName: String, fillMode: FillMode? = null, value: String? = null) {
        val current = _element.value
        _element.value = current.copy(
            slots = current.slots.map { slot ->
                if (slot.name == slotName) {
                    slot.copy(
                        fillMode = fillMode ?: slot.fillMode,
                        value = value ?: slot.value
                    )
                } else {
                    slot
                }
            }
        )
    }

    /**
     * Update a single anchor axis value on a slot.
     *
     * Sets the fill mode to LITERAL and updates the named field in anchorValues.
     * Blank values remove the field. The slot's main [PropertySlot.value] is
     * synthesized from all anchor fields for display/export.
     */
    fun updateSlotAnchorValues(slotName: String, field: String, fieldValue: String) {
        val current = _element.value
        _element.value = current.copy(
            slots = current.slots.map { slot ->
                if (slot.name == slotName) {
                    val newAnchorValues = slot.anchorValues.toMutableMap().apply {
                        if (fieldValue.isBlank()) remove(field) else put(field, fieldValue)
                    }
                    slot.copy(
                        fillMode = FillMode.LITERAL,
                        value = synthesizeAnchorValue(newAnchorValues),
                        anchorValues = newAnchorValues,
                    )
                } else {
                    slot
                }
            }
        )
    }

    /**
     * Replace all anchor fields on a slot from an [AnchorValue] object.
     * Used by the constraint editor to apply mode changes.
     */
    fun replaceSlotAnchor(slotName: String, anchor: AnchorValue) {
        val newAnchorValues = mutableMapOf<String, String>()
        anchor.left?.let { newAnchorValues["left"] = it.toString() }
        anchor.top?.let { newAnchorValues["top"] = it.toString() }
        anchor.right?.let { newAnchorValues["right"] = it.toString() }
        anchor.bottom?.let { newAnchorValues["bottom"] = it.toString() }
        anchor.width?.let { newAnchorValues["width"] = it.toString() }
        anchor.height?.let { newAnchorValues["height"] = it.toString() }

        val current = _element.value
        _element.value = current.copy(
            slots = current.slots.map { slot ->
                if (slot.name == slotName) {
                    slot.copy(
                        fillMode = FillMode.LITERAL,
                        value = synthesizeAnchorValue(newAnchorValues),
                        anchorValues = newAnchorValues,
                    )
                } else {
                    slot
                }
            }
        )
    }

    /**
     * Update a single tuple field value on a slot.
     *
     * Sets the fill mode to LITERAL and updates the named field in tupleValues.
     * The slot's main [PropertySlot.value] is synthesized from all tuple fields.
     */
    fun updateSlotTupleValues(slotName: String, fieldKey: String, fieldValue: String) {
        val current = _element.value
        _element.value = current.copy(
            slots = current.slots.map { slot ->
                if (slot.name == slotName) {
                    val newTupleValues = slot.tupleValues.toMutableMap().apply {
                        put(fieldKey, fieldValue)
                    }
                    slot.copy(
                        fillMode = FillMode.LITERAL,
                        value = synthesizeTupleValue(newTupleValues),
                        tupleValues = newTupleValues,
                    )
                } else {
                    slot
                }
            }
        )
    }

    /**
     * Add a new field to a tuple slot with an empty value.
     */
    fun addSlotTupleField(slotName: String, fieldKey: String) {
        val current = _element.value
        _element.value = current.copy(
            slots = current.slots.map { slot ->
                if (slot.name == slotName) {
                    val newTupleValues = slot.tupleValues.toMutableMap().apply {
                        if (!containsKey(fieldKey)) put(fieldKey, "")
                    }
                    slot.copy(
                        fillMode = FillMode.LITERAL,
                        value = synthesizeTupleValue(newTupleValues),
                        tupleValues = newTupleValues,
                    )
                } else {
                    slot
                }
            }
        )
    }

    /**
     * Remove a field from a tuple slot.
     */
    fun removeSlotTupleField(slotName: String, fieldKey: String) {
        val current = _element.value
        _element.value = current.copy(
            slots = current.slots.map { slot ->
                if (slot.name == slotName) {
                    val newTupleValues = slot.tupleValues.toMutableMap().apply {
                        remove(fieldKey)
                    }
                    slot.copy(
                        fillMode = if (newTupleValues.isEmpty()) FillMode.EMPTY else FillMode.LITERAL,
                        value = synthesizeTupleValue(newTupleValues),
                        tupleValues = newTupleValues,
                    )
                } else {
                    slot
                }
            }
        )
    }

    /**
     * Clear a slot back to empty, including anchor values.
     */
    fun clearSlot(slotName: String) {
        val current = _element.value
        _element.value = current.copy(
            slots = current.slots.map { slot ->
                if (slot.name == slotName) {
                    slot.copy(
                        fillMode = FillMode.EMPTY,
                        value = "",
                        anchorValues = emptyMap(),
                        tupleValues = emptyMap(),
                    )
                } else {
                    slot
                }
            }
        )
    }

    // -- Tab management (FR-6, FR-7) --

    /**
     * Open a style tab. If already open, just activate it.
     * Does not create duplicate tabs.
     */
    fun openStyleTab(styleName: String) {
        if (styleName !in _openStyleTabs.value) {
            _openStyleTabs.value = _openStyleTabs.value + styleName
        }
        _activeTab.value = styleName
    }

    /**
     * Close a style tab. If it was the active tab, revert to the element tab.
     */
    fun closeStyleTab(styleName: String) {
        _openStyleTabs.value = _openStyleTabs.value - styleName
        if (_activeTab.value == styleName) {
            _activeTab.value = null
        }
    }

    /**
     * Switch to a tab. Pass null for the element tab.
     */
    fun setActiveTab(tab: String?) {
        _activeTab.value = tab
    }

    // -- UI toggles --

    fun toggleCode() {
        _showCode.value = !_showCode.value
    }

    fun startEditingId() {
        _editingId.value = true
    }

    fun commitId(newId: String) {
        _element.value = _element.value.copy(id = newId.trim())
        _editingId.value = false
    }

    fun cancelEditingId() {
        _editingId.value = false
    }

    fun toggleCategory(category: String) {
        val current = _collapsedCategories.value
        _collapsedCategories.value = if (category in current) {
            current - category
        } else {
            current + category
        }
    }

    fun revealSlot(slotName: String) {
        _revealedSlots.value = _revealedSlots.value + slotName
    }

    fun hideRevealedSlot(slotName: String) {
        _revealedSlots.value = _revealedSlots.value - slotName
    }

    // -- Style editor mutations (spec 04, FR-6) --

    /**
     * Set or replace style data for a given style name.
     */
    fun setStyleData(styleName: String, tab: StyleTab) {
        _styleData.value = _styleData.value + (styleName to tab)
    }

    /**
     * Remove style data for a given style name.
     */
    fun removeStyleData(styleName: String) {
        _styleData.value = _styleData.value - styleName
    }

    /**
     * Update a single slot within a style state, merging the provided fields.
     */
    fun updateStyleSlot(
        styleName: String,
        stateName: String,
        slotName: String,
        fillMode: FillMode? = null,
        value: String? = null,
    ) {
        val tab = _styleData.value[styleName] ?: return
        val newStates = tab.states.map { state ->
            if (state.name == stateName) {
                state.copy(slots = state.slots.map { slot ->
                    if (slot.name == slotName) {
                        slot.copy(
                            fillMode = fillMode ?: slot.fillMode,
                            value = value ?: slot.value,
                        )
                    } else slot
                })
            } else state
        }
        _styleData.value = _styleData.value + (styleName to tab.copy(states = newStates))
    }

    /**
     * Update a single anchor axis value on a slot within a style state.
     */
    fun updateStyleSlotAnchorValues(
        styleName: String,
        stateName: String,
        slotName: String,
        field: String,
        fieldValue: String,
    ) {
        val tab = _styleData.value[styleName] ?: return
        val newStates = tab.states.map { state ->
            if (state.name == stateName) {
                state.copy(slots = state.slots.map { slot ->
                    if (slot.name == slotName) {
                        val newAnchorValues = slot.anchorValues.toMutableMap().apply {
                            if (fieldValue.isBlank()) remove(field) else put(field, fieldValue)
                        }
                        slot.copy(
                            fillMode = FillMode.LITERAL,
                            value = synthesizeAnchorValue(newAnchorValues),
                            anchorValues = newAnchorValues,
                        )
                    } else slot
                })
            } else state
        }
        _styleData.value = _styleData.value + (styleName to tab.copy(states = newStates))
    }

    /**
     * Update a single tuple field value on a slot within a style state.
     */
    fun updateStyleSlotTupleValues(
        styleName: String,
        stateName: String,
        slotName: String,
        fieldKey: String,
        fieldValue: String,
    ) {
        val tab = _styleData.value[styleName] ?: return
        val newStates = tab.states.map { state ->
            if (state.name == stateName) {
                state.copy(slots = state.slots.map { slot ->
                    if (slot.name == slotName) {
                        val newTupleValues = slot.tupleValues.toMutableMap().apply {
                            put(fieldKey, fieldValue)
                        }
                        slot.copy(
                            fillMode = FillMode.LITERAL,
                            value = synthesizeTupleValue(newTupleValues),
                            tupleValues = newTupleValues,
                        )
                    } else slot
                })
            } else state
        }
        _styleData.value = _styleData.value + (styleName to tab.copy(states = newStates))
    }

    /**
     * Add a new field to a tuple slot within a style state.
     */
    fun addStyleSlotTupleField(
        styleName: String,
        stateName: String,
        slotName: String,
        fieldKey: String,
    ) {
        val tab = _styleData.value[styleName] ?: return
        val newStates = tab.states.map { state ->
            if (state.name == stateName) {
                state.copy(slots = state.slots.map { slot ->
                    if (slot.name == slotName) {
                        val newTupleValues = slot.tupleValues.toMutableMap().apply {
                            if (!containsKey(fieldKey)) put(fieldKey, "")
                        }
                        slot.copy(
                            fillMode = FillMode.LITERAL,
                            value = synthesizeTupleValue(newTupleValues),
                            tupleValues = newTupleValues,
                        )
                    } else slot
                })
            } else state
        }
        _styleData.value = _styleData.value + (styleName to tab.copy(states = newStates))
    }

    /**
     * Remove a field from a tuple slot within a style state.
     */
    fun removeStyleSlotTupleField(
        styleName: String,
        stateName: String,
        slotName: String,
        fieldKey: String,
    ) {
        val tab = _styleData.value[styleName] ?: return
        val newStates = tab.states.map { state ->
            if (state.name == stateName) {
                state.copy(slots = state.slots.map { slot ->
                    if (slot.name == slotName) {
                        val newTupleValues = slot.tupleValues.toMutableMap().apply {
                            remove(fieldKey)
                        }
                        slot.copy(
                            fillMode = if (newTupleValues.isEmpty()) FillMode.EMPTY else FillMode.LITERAL,
                            value = synthesizeTupleValue(newTupleValues),
                            tupleValues = newTupleValues,
                        )
                    } else slot
                })
            } else state
        }
        _styleData.value = _styleData.value + (styleName to tab.copy(states = newStates))
    }

    /**
     * Clear a slot within a style state back to empty.
     */
    fun clearStyleSlot(styleName: String, stateName: String, slotName: String) {
        val tab = _styleData.value[styleName] ?: return
        val newStates = tab.states.map { state ->
            if (state.name == stateName) {
                state.copy(slots = state.slots.map { slot ->
                    if (slot.name == slotName) {
                        slot.copy(fillMode = FillMode.EMPTY, value = "", anchorValues = emptyMap(), tupleValues = emptyMap())
                    } else slot
                })
            } else state
        }
        _styleData.value = _styleData.value + (styleName to tab.copy(states = newStates))
    }

    /**
     * Add a custom visual state to a style.
     *
     * The new state's slot structure is cloned from the first existing state,
     * with all slots set to [FillMode.EMPTY].
     */
    fun addCustomState(styleName: String, newStateName: String) {
        val tab = _styleData.value[styleName] ?: return
        val templateSlots = tab.states.firstOrNull()?.slots ?: return
        val emptySlots = templateSlots.map {
            it.copy(fillMode = FillMode.EMPTY, value = "", anchorValues = emptyMap(), tupleValues = emptyMap())
        }
        val newState = StyleState(name = newStateName, slots = emptySlots)
        _styleData.value = _styleData.value + (
            styleName to tab.copy(states = tab.states + newState)
        )
    }

    /**
     * Toggle collapse state for a style state section.
     *
     * Uses composite keys like `"@ButtonStyle:Default"` so collapse state
     * is tracked independently per style per state.
     */
    fun toggleStateCollapse(compositeKey: String) {
        val current = _collapsedStates.value
        _collapsedStates.value = if (compositeKey in current) {
            current - compositeKey
        } else {
            current + compositeKey
        }
    }

    companion object {
        private val ANCHOR_FIELD_ORDER = listOf("left", "top", "right", "bottom", "width", "height")

        internal fun synthesizeAnchorValue(anchorValues: Map<String, String>): String {
            if (anchorValues.isEmpty()) return ""
            return ANCHOR_FIELD_ORDER
                .mapNotNull { key -> anchorValues[key]?.let { "$key:$it" } }
                .joinToString(", ")
        }

        internal fun synthesizeTupleValue(tupleValues: Map<String, String>): String {
            if (tupleValues.isEmpty()) return ""
            return tupleValues.entries.joinToString(", ") { (k, v) -> "$k: $v" }
        }
    }
}

/**
 * Remember a [ComposerModalState] scoped to the composition.
 * The state is keyed on the element's type + id so it resets when a
 * different element is opened.
 */
@Composable
fun rememberComposerModalState(element: ElementDefinition): ComposerModalState {
    return remember(element.type, element.id) {
        // Auto-collapse categories where all slots are empty
        val emptyCategories = element.slots
            .groupBy { it.category.displayName }
            .filter { (_, slots) -> slots.all { it.fillMode == FillMode.EMPTY } }
            .keys
        ComposerModalState(element, initialCollapsedCategories = emptyCategories)
    }
}
