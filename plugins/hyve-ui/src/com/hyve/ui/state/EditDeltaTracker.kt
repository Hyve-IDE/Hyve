package com.hyve.ui.state

import com.hyve.ui.core.domain.UIDocument
import com.hyve.ui.core.domain.elements.UIElement
import com.hyve.ui.core.domain.properties.PropertyValue
import com.hyve.ui.core.id.ElementId
import com.hyve.ui.core.id.PropertyName

/**
 * Tracks user edits as deltas to be applied to the raw document at export time.
 *
 * This supports the dual-document architecture where rendering uses a fully-resolved tree
 * from VariableAwareParser, but export needs to preserve @refs and other raw syntax.
 *
 * User edits (drag, resize, property changes) are recorded as deltas here and applied
 * to the raw UIParser document at export, ensuring round-trip fidelity.
 */
class EditDeltaTracker {
    sealed class EditDelta {
        data class SetProperty(
            val elementId: ElementId,
            val propertyName: String,
            val value: PropertyValue
        ) : EditDelta()

        data class RemoveProperty(
            val elementId: ElementId,
            val propertyName: String
        ) : EditDelta()

        data class AddElement(
            val parentId: ElementId?,
            val index: Int,
            val element: UIElement
        ) : EditDelta()

        data class DeleteElement(
            val elementId: ElementId
        ) : EditDelta()

        data class MoveElement(
            val elementId: ElementId,
            val newAnchor: PropertyValue.Anchor
        ) : EditDelta()

        data class RenameElement(
            val oldId: ElementId,
            val newId: ElementId
        ) : EditDelta()

        data class ReorderElement(
            val parentId: ElementId?,
            val elementId: ElementId,
            val fromIndex: Int,
            val toIndex: Int
        ) : EditDelta()
    }

    private val deltas = mutableListOf<EditDelta>()

    fun record(delta: EditDelta) {
        deltas.add(delta)
    }

    /**
     * Remove a specific property delta (for undo integration).
     */
    fun removeDelta(elementId: ElementId, propertyName: String) {
        deltas.removeAll { delta ->
            when (delta) {
                is EditDelta.SetProperty -> delta.elementId == elementId && delta.propertyName == propertyName
                is EditDelta.RemoveProperty -> delta.elementId == elementId && delta.propertyName == propertyName
                is EditDelta.MoveElement -> delta.elementId == elementId && propertyName == "Anchor"
                else -> false
            }
        }
    }

    /**
     * Remove structural deltas for an element (for structural undo).
     */
    fun removeStructuralDelta(elementId: ElementId) {
        deltas.removeAll { delta ->
            when (delta) {
                is EditDelta.AddElement -> delta.element.id == elementId
                is EditDelta.DeleteElement -> delta.elementId == elementId
                is EditDelta.ReorderElement -> delta.elementId == elementId
                else -> false
            }
        }
    }

    fun hasDelta(elementId: ElementId, propertyName: String): Boolean {
        return deltas.any { delta ->
            when (delta) {
                is EditDelta.SetProperty -> delta.elementId == elementId && delta.propertyName == propertyName
                is EditDelta.RemoveProperty -> delta.elementId == elementId && delta.propertyName == propertyName
                is EditDelta.MoveElement -> delta.elementId == elementId && propertyName == "Anchor"
                else -> false
            }
        }
    }

    fun clear() {
        deltas.clear()
    }

    /**
     * Check if any deltas have been recorded since the last clear().
     * Used to guard the export pipeline from firing on initial parse.
     */
    fun hasChanges(): Boolean = deltas.isNotEmpty()

    /**
     * Apply all recorded deltas to the raw document.
     *
     * Structural deltas (AddElement/DeleteElement) are applied first,
     * then property deltas are overlaid.
     *
     * Property deltas for the same (elementId, propertyName) collapse to
     * the latest value (last-write-wins).
     */
    fun applyTo(document: UIDocument): UIDocument {
        if (deltas.isEmpty()) return document

        // Phase 1: Apply structural deltas in order
        var currentRoot = document.root
        // Build a rename map so property deltas on renamed elements still match
        val renameMap = mutableMapOf<ElementId, ElementId>() // old -> new
        for (delta in deltas) {
            when (delta) {
                is EditDelta.AddElement -> {
                    currentRoot = applyAddElement(currentRoot, delta)
                }
                is EditDelta.DeleteElement -> {
                    currentRoot = applyDeleteElement(currentRoot, delta)
                }
                is EditDelta.RenameElement -> {
                    currentRoot = applyRenameElement(currentRoot, delta)
                    renameMap[delta.oldId] = delta.newId
                }
                is EditDelta.ReorderElement -> {
                    currentRoot = applyReorderElement(currentRoot, delta)
                }
                else -> {} // handled in phase 2
            }
        }

        // Phase 2: Collapse property deltas to latest value per (elementId, propertyName)
        // Resolve element IDs through the rename map so deltas recorded against old IDs still apply
        fun resolveId(id: ElementId): ElementId = renameMap[id] ?: id
        val collapsedDeltas = mutableMapOf<Pair<ElementId, String>, EditDelta>()
        for (delta in deltas) {
            val key = when (delta) {
                is EditDelta.SetProperty -> resolveId(delta.elementId) to delta.propertyName
                is EditDelta.RemoveProperty -> resolveId(delta.elementId) to delta.propertyName
                is EditDelta.MoveElement -> resolveId(delta.elementId) to "Anchor"
                else -> continue
            }
            // Remap the delta's elementId if it was renamed
            val remapped = when (delta) {
                is EditDelta.SetProperty -> delta.copy(elementId = resolveId(delta.elementId))
                is EditDelta.RemoveProperty -> delta.copy(elementId = resolveId(delta.elementId))
                is EditDelta.MoveElement -> delta.copy(elementId = resolveId(delta.elementId))
                else -> delta
            }
            collapsedDeltas[key] = remapped
        }

        // Apply collapsed property deltas
        for (delta in collapsedDeltas.values) {
            currentRoot = when (delta) {
                is EditDelta.SetProperty -> applySetProperty(currentRoot, delta)
                is EditDelta.RemoveProperty -> applyRemoveProperty(currentRoot, delta)
                is EditDelta.MoveElement -> applyMoveElement(currentRoot, delta)
                else -> currentRoot
            }
        }

        return document.copy(root = currentRoot)
    }

    // Property edits intentionally overwrite @refs with concrete values --
    // user explicit edit takes precedence over style reference (DL-002)
    private fun applySetProperty(root: UIElement, delta: EditDelta.SetProperty): UIElement {
        return root.mapDescendants { el ->
            if (el.id == delta.elementId) {
                el.setProperty(delta.propertyName, delta.value)
            } else {
                el
            }
        }
    }

    private fun applyRemoveProperty(root: UIElement, delta: EditDelta.RemoveProperty): UIElement {
        return root.mapDescendants { el ->
            if (el.id == delta.elementId) {
                el.removeProperty(PropertyName(delta.propertyName))
            } else {
                el
            }
        }
    }

    private fun applyMoveElement(root: UIElement, delta: EditDelta.MoveElement): UIElement {
        return root.mapDescendants { el ->
            if (el.id == delta.elementId) {
                el.setProperty("Anchor", delta.newAnchor)
            } else {
                el
            }
        }
    }

    private fun applyAddElement(root: UIElement, delta: EditDelta.AddElement): UIElement {
        if (delta.parentId == null) {
            // Adding at root level -- insert as child of root
            val newChildren = root.children.toMutableList()
            val insertIndex = delta.index.coerceIn(0, newChildren.size)
            newChildren.add(insertIndex, delta.element)
            return root.copy(children = newChildren)
        }

        return root.mapDescendants { el ->
            if (el.id == delta.parentId) {
                val newChildren = el.children.toMutableList()
                val insertIndex = delta.index.coerceIn(0, newChildren.size)
                newChildren.add(insertIndex, delta.element)
                el.copy(children = newChildren)
            } else {
                el
            }
        }
    }

    private fun applyRenameElement(root: UIElement, delta: EditDelta.RenameElement): UIElement {
        return root.mapDescendants { el ->
            if (el.id == delta.oldId) {
                el.copy(id = delta.newId)
            } else {
                el
            }
        }
    }

    private fun applyReorderElement(root: UIElement, delta: EditDelta.ReorderElement): UIElement {
        fun reorderChildren(parent: UIElement): UIElement {
            val children = parent.children.toMutableList()
            val fromIndex = delta.fromIndex
            val toIndex = delta.toIndex
            if (fromIndex !in children.indices || toIndex !in children.indices) return parent
            val child = children.removeAt(fromIndex)
            children.add(toIndex, child)
            return parent.copy(children = children)
        }

        if (delta.parentId == null) {
            return reorderChildren(root)
        }

        return root.mapDescendants { el ->
            if (el.id == delta.parentId) {
                reorderChildren(el)
            } else {
                el
            }
        }
    }

    private fun applyDeleteElement(root: UIElement, delta: EditDelta.DeleteElement): UIElement {
        return root.mapDescendants { el ->
            val filteredChildren = el.children.filterNot { child ->
                child.id == delta.elementId
            }
            if (filteredChildren.size != el.children.size) {
                el.copy(children = filteredChildren)
            } else {
                el
            }
        }
    }
}
