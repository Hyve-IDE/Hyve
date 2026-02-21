// Copyright 2026 Hyve. All rights reserved.
package com.hyve.ui.composer.wordbank

import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Offset
import com.hyve.ui.composer.model.PopoverKind
import com.hyve.ui.composer.model.WordBankItem
import com.hyve.ui.composer.model.WordBankKind

/**
 * State holder for the Word Bank sidebar.
 *
 * Manages the item list, search filter, section collapse, and context menu state.
 * Follows the same plain-class-with-MutableState pattern as [com.hyve.ui.composer.ComposerModalState].
 *
 * ## Spec Reference
 * - FR-2: Section collapse
 * - FR-4: Search / filter
 * - FR-6: Context menu
 * - FR-8: Item management (add/remove)
 * - Spec 06: Popover open/close state
 */
class WordBankState(initialItems: List<WordBankItem> = emptyList()) {

    // -- Item list --

    private val _items = mutableStateOf(initialItems)
    val items: State<List<WordBankItem>> get() = _items

    // -- Search / filter (FR-4) --

    private val _searchQuery = mutableStateOf("")
    val searchQuery: State<String> get() = _searchQuery

    // -- Section collapse (FR-2) --

    private val _collapsedSections = mutableStateOf<Set<WordBankKind>>(emptySet())
    val collapsedSections: State<Set<WordBankKind>> get() = _collapsedSections

    // -- Context menu (FR-6) --

    private val _contextMenu = mutableStateOf<ContextMenuState?>(null)
    val contextMenu: State<ContextMenuState?> get() = _contextMenu

    // -- Derived: filtered items --

    /**
     * Items filtered by the current search query.
     * Matches case-insensitively against both name and value.
     */
    val filteredItems: List<WordBankItem>
        get() {
            val query = _searchQuery.value
            if (query.isBlank()) return _items.value
            val lower = query.lowercase()
            return _items.value.filter { item ->
                item.name.lowercase().contains(lower) ||
                    (item.value?.lowercase()?.contains(lower) == true)
            }
        }

    /**
     * Returns filtered items for a given section kind.
     */
    fun itemsForKind(kind: WordBankKind): List<WordBankItem> =
        filteredItems.filter { it.kind == kind }

    /**
     * Whether a section should be visible.
     * During active search, sections with 0 matching items are hidden entirely.
     * When no search is active, all sections are visible.
     */
    fun isSectionVisible(kind: WordBankKind): Boolean {
        if (_searchQuery.value.isBlank()) return true
        return itemsForKind(kind).isNotEmpty()
    }

    // -- Mutations --

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun clearSearch() {
        _searchQuery.value = ""
    }

    fun toggleSection(kind: WordBankKind) {
        val current = _collapsedSections.value
        _collapsedSections.value = if (kind in current) {
            current - kind
        } else {
            current + kind
        }
    }

    fun isSectionCollapsed(kind: WordBankKind): Boolean =
        kind in _collapsedSections.value

    /**
     * Add a new item to the word bank (FR-8).
     * The item appears immediately in the correct section.
     */
    fun addItem(item: WordBankItem) {
        _items.value = _items.value + item
    }

    /**
     * Remove an item by ID (FR-8).
     * If the context menu was open for this item, it is closed.
     */
    fun removeItem(itemId: String) {
        _items.value = _items.value.filter { it.id != itemId }
        if (_contextMenu.value?.item?.id == itemId) {
            _contextMenu.value = null
        }
    }

    // -- Context menu --

    fun showContextMenu(item: WordBankItem, position: Offset) {
        _contextMenu.value = ContextMenuState(item, position)
    }

    fun closeContextMenu() {
        _contextMenu.value = null
    }

    // -- Popover state (spec 06) --

    private val _activePopover = mutableStateOf<PopoverKind?>(null)
    val activePopover: State<PopoverKind?> get() = _activePopover

    /**
     * Open a popover dialog. Only one can be open at a time;
     * opening one implicitly closes any previously open popover.
     */
    fun openPopover(kind: PopoverKind) {
        _activePopover.value = kind
    }

    /**
     * Close the currently open popover.
     */
    fun closePopover() {
        _activePopover.value = null
    }
}

/**
 * Position and target of an open context menu.
 */
data class ContextMenuState(
    val item: WordBankItem,
    val position: Offset,
)

/**
 * Remember a [WordBankState] scoped to the composition.
 */
@Composable
fun rememberWordBankState(initialItems: List<WordBankItem> = emptyList()): WordBankState {
    return remember { WordBankState(initialItems) }
}
