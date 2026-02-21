// Copyright 2026 Hyve. All rights reserved.
package com.hyve.ui.canvas

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import com.hyve.ui.core.domain.elements.UIElement

/**
 * Manages the set of selected elements on the canvas.
 *
 * Owns the selection state and provides operations for selecting,
 * deselecting, and querying selection. Extracted from CanvasState
 * to isolate selection logic for testability.
 */
class SelectionManager {

    private val _selectedElements = mutableStateOf<Set<UIElement>>(emptySet())
    val selectedElements: State<Set<UIElement>> = _selectedElements

    /**
     * Select a single element, replacing any current selection.
     */
    fun select(element: UIElement) {
        _selectedElements.value = setOf(element)
    }

    /**
     * Add an element to the current selection (multi-select).
     */
    fun addToSelection(element: UIElement) {
        _selectedElements.value = _selectedElements.value + element
    }

    /**
     * Remove an element from the current selection.
     */
    fun removeFromSelection(element: UIElement) {
        _selectedElements.value = _selectedElements.value - element
    }

    /**
     * Clear all selection.
     */
    fun clearSelection() {
        _selectedElements.value = emptySet()
    }

    /**
     * Replace the entire selection set (used after tree updates).
     */
    fun setSelection(elements: Set<UIElement>) {
        _selectedElements.value = elements
    }

    /**
     * Check if an element is currently selected.
     * Matches by reference first, then by ID for tree-rebuilt elements.
     */
    fun isSelected(element: UIElement): Boolean {
        val selectedSet = _selectedElements.value
        if (element in selectedSet) return true
        val elementId = element.id ?: return false
        return selectedSet.any { it.id == elementId }
    }

    /**
     * Check if an element is locked.
     */
    fun isLocked(element: UIElement): Boolean = element.metadata.locked

    /**
     * Update selection references after a tree change.
     * Re-finds selected elements in the new tree by ID,
     * dropping any that no longer exist.
     */
    fun updateAfterTreeChange(root: UIElement) {
        val currentSelection = _selectedElements.value
        if (currentSelection.isEmpty()) return

        val newSelection = mutableSetOf<UIElement>()
        for (selected in currentSelection) {
            val id = selected.id
            if (id != null) {
                // Try to find by ID in the new tree
                val found = root.findDescendantById(id)
                if (found != null) {
                    newSelection.add(found)
                }
            } else {
                // No ID — check if still in tree by reference
                root.visitDescendants { el ->
                    if (el === selected) {
                        newSelection.add(el)
                    }
                }
            }
        }
        _selectedElements.value = newSelection
    }

    /**
     * Find the nearest selected ancestor of an element.
     * Returns null if no ancestor is selected.
     */
    fun findSelectedAncestor(element: UIElement, findParent: (UIElement) -> UIElement?): UIElement? {
        var current = element
        while (true) {
            val parent = findParent(current) ?: return null
            if (isSelected(parent)) return parent
            current = parent
        }
    }
}
