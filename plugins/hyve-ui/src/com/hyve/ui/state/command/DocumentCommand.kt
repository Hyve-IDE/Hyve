package com.hyve.ui.state.command

import com.hyve.common.undo.UndoableCommand
import com.hyve.ui.core.domain.elements.UIElement
import com.hyve.ui.core.domain.properties.PropertyValue
import com.hyve.ui.core.id.ElementId
import com.hyve.ui.core.id.PropertyName

/**
 * Command interface for UI document undo/redo support.
 *
 * Extends the generic [UndoableCommand] with [UIElement] as the state type.
 * Commands encapsulate changes to the document state.
 * Each command can execute (apply) and undo (revert) its changes.
 *
 * Commands are immutable and must capture all state needed to
 * execute and undo themselves.
 */
sealed interface DocumentCommand : UndoableCommand<UIElement>

/**
 * Command to set a property on an element.
 *
 * @param elementId The ID of the element to modify (null for root element)
 * @param elementMatcher Predicate to find the element if it has no ID
 * @param propertyName The name of the property to set
 * @param oldValue The previous property value (for undo)
 * @param newValue The new property value (for execute)
 */
data class SetPropertyCommand(
    val elementId: ElementId?,
    val elementMatcher: ((UIElement) -> Boolean)?,
    val propertyName: String,
    val oldValue: PropertyValue?,
    val newValue: PropertyValue,
    private val elementDescription: String = "element"
) : DocumentCommand {

    override val description: String
        get() = "Set $propertyName on $elementDescription"

    override fun execute(state: UIElement): UIElement? {
        return updateElement(state) { element ->
            element.setProperty(propertyName, newValue)
        }
    }

    override fun undo(state: UIElement): UIElement? {
        return updateElement(state) { element ->
            if (oldValue != null) {
                element.setProperty(propertyName, oldValue)
            } else {
                element.removeProperty(PropertyName(propertyName))
            }
        }
    }

    private fun updateElement(root: UIElement, transform: (UIElement) -> UIElement): UIElement? {
        return root.mapDescendants { element ->
            if (matchesElement(element)) {
                transform(element)
            } else {
                element
            }
        }
    }

    private fun matchesElement(element: UIElement): Boolean {
        // First try to match by ID
        if (elementId != null && element.id == elementId) return true
        // Then try the matcher predicate
        if (elementMatcher != null && elementMatcher.invoke(element)) return true
        return false
    }

    override fun canMergeWith(other: UndoableCommand<UIElement>): Boolean {
        if (other !is SetPropertyCommand) return false
        // Can merge if same element and same property
        return (elementId != null && elementId == other.elementId && propertyName == other.propertyName)
    }

    override fun mergeWith(other: UndoableCommand<UIElement>): UndoableCommand<UIElement>? {
        if (!canMergeWith(other)) return null
        val otherSet = other as SetPropertyCommand
        // Keep the original oldValue, take the new newValue
        return copy(newValue = otherSet.newValue)
    }

    companion object {
        /**
         * Create a SetPropertyCommand for a specific element.
         */
        fun forElement(
            element: UIElement,
            propertyName: String,
            oldValue: PropertyValue?,
            newValue: PropertyValue
        ): SetPropertyCommand {
            return SetPropertyCommand(
                elementId = element.id,
                // Fallback matcher using object identity (only works within same session)
                elementMatcher = if (element.id == null) { el -> el == element } else null,
                propertyName = propertyName,
                oldValue = oldValue,
                newValue = newValue,
                elementDescription = element.displayName()
            )
        }
    }
}

/**
 * Command to move an element (update its Anchor position).
 * This is a specialized SetPropertyCommand for better descriptions.
 */
data class MoveElementCommand(
    val elementId: ElementId?,
    val elementMatcher: ((UIElement) -> Boolean)?,
    val oldAnchor: PropertyValue.Anchor,
    val newAnchor: PropertyValue.Anchor,
    private val elementDescription: String = "element"
) : DocumentCommand {

    override val description: String
        get() = "Move $elementDescription"

    override fun execute(state: UIElement): UIElement? {
        return updateElement(state) { element ->
            element.setProperty("Anchor", newAnchor)
        }
    }

    override fun undo(state: UIElement): UIElement? {
        return updateElement(state) { element ->
            element.setProperty("Anchor", oldAnchor)
        }
    }

    private fun updateElement(root: UIElement, transform: (UIElement) -> UIElement): UIElement? {
        return root.mapDescendants { element ->
            if (matchesElement(element)) {
                transform(element)
            } else {
                element
            }
        }
    }

    private fun matchesElement(element: UIElement): Boolean {
        if (elementId != null && element.id == elementId) return true
        if (elementMatcher != null && elementMatcher.invoke(element)) return true
        return false
    }

    override fun canMergeWith(other: UndoableCommand<UIElement>): Boolean {
        if (other !is MoveElementCommand) return false
        return elementId != null && elementId == other.elementId
    }

    override fun mergeWith(other: UndoableCommand<UIElement>): UndoableCommand<UIElement>? {
        if (!canMergeWith(other)) return null
        val otherMove = other as MoveElementCommand
        // Keep original oldAnchor, take new newAnchor
        return copy(newAnchor = otherMove.newAnchor)
    }

    companion object {
        fun forElement(
            element: UIElement,
            oldAnchor: PropertyValue.Anchor,
            newAnchor: PropertyValue.Anchor
        ): MoveElementCommand {
            return MoveElementCommand(
                elementId = element.id,
                elementMatcher = if (element.id == null) { el -> el == element } else null,
                oldAnchor = oldAnchor,
                newAnchor = newAnchor,
                elementDescription = element.displayName()
            )
        }
    }
}

/**
 * Command to resize an element (update its Anchor dimensions).
 */
data class ResizeElementCommand(
    val elementId: ElementId?,
    val elementMatcher: ((UIElement) -> Boolean)?,
    val oldAnchor: PropertyValue.Anchor,
    val newAnchor: PropertyValue.Anchor,
    private val elementDescription: String = "element"
) : DocumentCommand {

    override val description: String
        get() = "Resize $elementDescription"

    override fun execute(state: UIElement): UIElement? {
        return updateElement(state) { element ->
            element.setProperty("Anchor", newAnchor)
        }
    }

    override fun undo(state: UIElement): UIElement? {
        return updateElement(state) { element ->
            element.setProperty("Anchor", oldAnchor)
        }
    }

    private fun updateElement(root: UIElement, transform: (UIElement) -> UIElement): UIElement? {
        return root.mapDescendants { element ->
            if (matchesElement(element)) {
                transform(element)
            } else {
                element
            }
        }
    }

    private fun matchesElement(element: UIElement): Boolean {
        if (elementId != null && element.id == elementId) return true
        if (elementMatcher != null && elementMatcher.invoke(element)) return true
        return false
    }

    override fun canMergeWith(other: UndoableCommand<UIElement>): Boolean {
        if (other !is ResizeElementCommand) return false
        return elementId != null && elementId == other.elementId
    }

    override fun mergeWith(other: UndoableCommand<UIElement>): UndoableCommand<UIElement>? {
        if (!canMergeWith(other)) return null
        val otherResize = other as ResizeElementCommand
        return copy(newAnchor = otherResize.newAnchor)
    }

    companion object {
        fun forElement(
            element: UIElement,
            oldAnchor: PropertyValue.Anchor,
            newAnchor: PropertyValue.Anchor
        ): ResizeElementCommand {
            return ResizeElementCommand(
                elementId = element.id,
                elementMatcher = if (element.id == null) { el -> el == element } else null,
                oldAnchor = oldAnchor,
                newAnchor = newAnchor,
                elementDescription = element.displayName()
            )
        }
    }
}

/**
 * Command to remove a property from an element.
 *
 * @param elementId The ID of the element to modify (null for root element)
 * @param elementMatcher Predicate to find the element if it has no ID
 * @param propertyName The name of the property to remove
 * @param oldValue The previous property value (for undo)
 */
data class RemovePropertyCommand(
    val elementId: ElementId?,
    val elementMatcher: ((UIElement) -> Boolean)?,
    val propertyName: String,
    val oldValue: PropertyValue,
    private val elementDescription: String = "element"
) : DocumentCommand {

    override val description: String
        get() = "Remove $propertyName from $elementDescription"

    override fun execute(state: UIElement): UIElement? {
        return updateElement(state) { element ->
            element.removeProperty(PropertyName(propertyName))
        }
    }

    override fun undo(state: UIElement): UIElement? {
        return updateElement(state) { element ->
            element.setProperty(propertyName, oldValue)
        }
    }

    private fun updateElement(root: UIElement, transform: (UIElement) -> UIElement): UIElement? {
        return root.mapDescendants { element ->
            if (matchesElement(element)) {
                transform(element)
            } else {
                element
            }
        }
    }

    private fun matchesElement(element: UIElement): Boolean {
        if (elementId != null && element.id == elementId) return true
        if (elementMatcher != null && elementMatcher.invoke(element)) return true
        return false
    }

    companion object {
        /**
         * Create a RemovePropertyCommand for a specific element.
         */
        fun forElement(
            element: UIElement,
            propertyName: String,
            oldValue: PropertyValue
        ): RemovePropertyCommand {
            return RemovePropertyCommand(
                elementId = element.id,
                elementMatcher = if (element.id == null) { el -> el == element } else null,
                propertyName = propertyName,
                oldValue = oldValue,
                elementDescription = element.displayName()
            )
        }
    }
}

/**
 * Composite command that groups multiple commands into one undo/redo unit.
 */
data class CompositeCommand(
    val commands: List<DocumentCommand>,
    override val description: String
) : DocumentCommand {

    override fun execute(state: UIElement): UIElement? {
        var current: UIElement = state
        for (command in commands) {
            current = command.execute(current) ?: return null
        }
        return current
    }

    override fun undo(state: UIElement): UIElement? {
        var current: UIElement = state
        // Undo in reverse order
        for (command in commands.reversed()) {
            current = command.undo(current) ?: return null
        }
        return current
    }
}

/**
 * Command to add a new element to the document.
 *
 * @param parentId The ID of the parent element (null for root level)
 * @param parentMatcher Predicate to find the parent if it has no ID
 * @param element The element to add
 * @param index The position within parent's children (-1 for end)
 */
data class AddElementCommand(
    val parentId: ElementId?,
    val parentMatcher: ((UIElement) -> Boolean)?,
    val element: UIElement,
    val index: Int = -1,
    private val parentDescription: String = "element"
) : DocumentCommand {

    override val description: String
        get() = "Add ${element.type.value}${element.id?.let { " #${it.value}" } ?: ""}"

    override fun execute(state: UIElement): UIElement? {
        return updateParent(state) { parent ->
            val insertIndex = if (index < 0 || index > parent.children.size) {
                parent.children.size
            } else {
                index
            }
            parent.addChild(element, insertIndex)
        }
    }

    override fun undo(state: UIElement): UIElement? {
        return updateParent(state) { parent ->
            // Find the element by ID or by equality
            val childIndex = if (element.id != null) {
                parent.children.indexOfFirst { it.id == element.id }
            } else {
                // For elements without ID, find by type and position
                val matchingChildren = parent.children.withIndex()
                    .filter { (_, child) -> child.type == element.type }
                if (index >= 0 && index < matchingChildren.size) {
                    matchingChildren[index].index
                } else {
                    matchingChildren.lastOrNull()?.index ?: -1
                }
            }

            if (childIndex >= 0) {
                parent.removeChildAt(childIndex)
            } else {
                parent
            }
        }
    }

    private fun updateParent(root: UIElement, transform: (UIElement) -> UIElement): UIElement? {
        // If no parent specified, add to root
        if (parentId == null && parentMatcher == null) {
            return transform(root)
        }

        return root.mapDescendants { element ->
            if (matchesParent(element)) {
                transform(element)
            } else {
                element
            }
        }
    }

    private fun matchesParent(element: UIElement): Boolean {
        if (parentId != null && element.id == parentId) return true
        if (parentMatcher != null && parentMatcher.invoke(element)) return true
        return false
    }

    companion object {
        /**
         * Create an AddElementCommand to add to a specific parent element.
         */
        fun toParent(
            parent: UIElement,
            element: UIElement,
            index: Int = -1
        ): AddElementCommand {
            return AddElementCommand(
                parentId = parent.id,
                parentMatcher = if (parent.id == null) { el -> el == parent } else null,
                element = element,
                index = index,
                parentDescription = parent.displayName()
            )
        }

        /**
         * Create an AddElementCommand to add to root level.
         */
        fun toRoot(element: UIElement, index: Int = -1): AddElementCommand {
            return AddElementCommand(
                parentId = null,
                parentMatcher = null,
                element = element,
                index = index,
                parentDescription = "root"
            )
        }
    }
}

/**
 * Command to delete an element from the document.
 *
 * @param elementId The ID of the element to delete
 * @param elementMatcher Predicate to find the element if it has no ID
 * @param deletedElement The element that was deleted (for undo)
 * @param parentId The ID of the parent element
 * @param parentMatcher Predicate to find the parent if it has no ID
 * @param childIndex The index of the element within its parent's children
 */
data class DeleteElementCommand(
    val elementId: ElementId?,
    val elementMatcher: ((UIElement) -> Boolean)?,
    val deletedElement: UIElement,
    val parentId: ElementId?,
    val parentMatcher: ((UIElement) -> Boolean)?,
    val childIndex: Int,
    private val elementDescription: String = "element"
) : DocumentCommand {

    override val description: String
        get() = "Delete $elementDescription"

    override fun execute(state: UIElement): UIElement? {
        // If element is the root itself, we can't delete it
        if (matchesElement(state)) {
            return null
        }

        // Special case: deleting from root level (parentId and parentMatcher are null)
        if (parentId == null && parentMatcher == null) {
            val indexToRemove = if (childIndex >= 0 && childIndex < state.children.size) {
                childIndex
            } else {
                state.children.indexOfFirst { child -> matchesElementForDelete(child) }
            }

            return if (indexToRemove >= 0) {
                state.removeChildAt(indexToRemove)
            } else {
                state
            }
        }

        return state.mapDescendants { element ->
            if (matchesParent(element)) {
                // Remove the child at the specified index
                val indexToRemove = if (childIndex >= 0 && childIndex < element.children.size) {
                    childIndex
                } else {
                    element.children.indexOfFirst { child -> matchesElementForDelete(child) }
                }

                if (indexToRemove >= 0) {
                    element.removeChildAt(indexToRemove)
                } else {
                    element
                }
            } else {
                element
            }
        }
    }

    override fun undo(state: UIElement): UIElement? {
        // Restore the deleted element to its parent
        if (parentId == null && parentMatcher == null) {
            // Was a root-level element
            return state.addChild(deletedElement, childIndex.coerceAtMost(state.children.size))
        }

        return state.mapDescendants { element ->
            if (matchesParent(element)) {
                element.addChild(deletedElement, childIndex.coerceAtMost(element.children.size))
            } else {
                element
            }
        }
    }

    private fun matchesElement(element: UIElement): Boolean {
        if (elementId != null && element.id == elementId) return true
        if (elementMatcher != null && elementMatcher.invoke(element)) return true
        return false
    }

    private fun matchesElementForDelete(element: UIElement): Boolean {
        if (elementId != null && element.id == elementId) return true
        // For elements without ID, match by type and properties
        if (elementId == null && element.type == deletedElement.type) {
            return element.properties == deletedElement.properties
        }
        return false
    }

    private fun matchesParent(element: UIElement): Boolean {
        if (parentId != null && element.id == parentId) return true
        if (parentMatcher != null && parentMatcher.invoke(element)) return true
        return false
    }

    companion object {
        /**
         * Create a DeleteElementCommand for a specific element.
         * Requires knowing the parent and the element's index within the parent.
         */
        fun forElement(
            element: UIElement,
            parent: UIElement,
            childIndex: Int
        ): DeleteElementCommand {
            return DeleteElementCommand(
                elementId = element.id,
                elementMatcher = if (element.id == null) { el -> el == element } else null,
                deletedElement = element,
                parentId = parent.id,
                parentMatcher = if (parent.id == null) { el -> el == parent } else null,
                childIndex = childIndex,
                elementDescription = element.displayName()
            )
        }

        /**
         * Create a DeleteElementCommand for a root-level element.
         */
        fun fromRoot(
            element: UIElement,
            root: UIElement,
            childIndex: Int
        ): DeleteElementCommand {
            return DeleteElementCommand(
                elementId = element.id,
                elementMatcher = if (element.id == null) { el -> el == element } else null,
                deletedElement = element,
                parentId = null,
                parentMatcher = null,
                childIndex = childIndex,
                elementDescription = element.displayName()
            )
        }
    }
}

/**
 * Command to reorder an element within its parent's children list (z-order).
 *
 * @param parentId The ID of the parent element (null for root level)
 * @param parentMatcher Predicate to find the parent if it has no ID
 * @param elementId The ID of the element to move
 * @param elementMatcher Predicate to find the element if it has no ID
 * @param oldIndex The original index within the parent's children
 * @param newIndex The target index within the parent's children
 */
data class ReorderElementCommand(
    val parentId: ElementId?,
    val parentMatcher: ((UIElement) -> Boolean)?,
    val elementId: ElementId?,
    val elementMatcher: ((UIElement) -> Boolean)?,
    val oldIndex: Int,
    val newIndex: Int,
    private val elementDescription: String = "element"
) : DocumentCommand {

    override val description: String
        get() = "Reorder $elementDescription"

    override fun execute(state: UIElement): UIElement? {
        return moveChild(state, oldIndex, newIndex)
    }

    override fun undo(state: UIElement): UIElement? {
        return moveChild(state, newIndex, oldIndex)
    }

    private fun moveChild(root: UIElement, fromIndex: Int, toIndex: Int): UIElement? {
        // If parent is root (null parent ID and matcher), operate on root directly
        if (parentId == null && parentMatcher == null) {
            return reorderChild(root, fromIndex, toIndex)
        }

        return root.mapDescendants { element ->
            if (matchesParent(element)) {
                reorderChild(element, fromIndex, toIndex) ?: element
            } else {
                element
            }
        }
    }

    private fun reorderChild(parent: UIElement, fromIndex: Int, toIndex: Int): UIElement? {
        if (fromIndex < 0 || fromIndex >= parent.children.size) return null
        if (toIndex < 0 || toIndex >= parent.children.size) return null
        if (fromIndex == toIndex) return parent

        val children = parent.children.toMutableList()
        val child = children.removeAt(fromIndex)
        children.add(toIndex, child)
        return parent.copy(children = children)
    }

    private fun matchesParent(element: UIElement): Boolean {
        if (parentId != null && element.id == parentId) return true
        if (parentMatcher != null && parentMatcher.invoke(element)) return true
        return false
    }

    companion object {
        fun forElement(
            parent: UIElement,
            element: UIElement,
            oldIndex: Int,
            newIndex: Int
        ): ReorderElementCommand {
            return ReorderElementCommand(
                parentId = parent.id,
                parentMatcher = if (parent.id == null) { el -> el == parent } else null,
                elementId = element.id,
                elementMatcher = if (element.id == null) { el -> el == element } else null,
                oldIndex = oldIndex,
                newIndex = newIndex,
                elementDescription = element.displayName()
            )
        }

        fun forRootChild(
            element: UIElement,
            oldIndex: Int,
            newIndex: Int
        ): ReorderElementCommand {
            return ReorderElementCommand(
                parentId = null,
                parentMatcher = null,
                elementId = element.id,
                elementMatcher = if (element.id == null) { el -> el == element } else null,
                oldIndex = oldIndex,
                newIndex = newIndex,
                elementDescription = element.displayName()
            )
        }
    }
}

/**
 * Command to replace an element with a modified version.
 * Used when the Composer modal applies bulk changes back to an element.
 *
 * Tracks both old and new element IDs separately because the user can
 * change the element's ID inside the Composer. Execute matches by old ID,
 * undo matches by new ID.
 */
data class ReplaceElementCommand(
    val oldElementId: ElementId?,
    val newElementId: ElementId?,
    val elementMatcher: ((UIElement) -> Boolean)?,
    val oldElement: UIElement,
    val newElement: UIElement,
    private val elementDescription: String = "element"
) : DocumentCommand {

    override val description: String
        get() = "Edit $elementDescription in Composer"

    override fun execute(state: UIElement): UIElement? {
        return state.mapDescendants { element ->
            if (matchesOldElement(element)) newElement else element
        }
    }

    override fun undo(state: UIElement): UIElement? {
        return state.mapDescendants { element ->
            if (matchesNewElement(element)) oldElement else element
        }
    }

    private fun matchesOldElement(element: UIElement): Boolean {
        if (oldElementId != null && element.id == oldElementId) return true
        if (elementMatcher != null && elementMatcher.invoke(element)) return true
        return false
    }

    private fun matchesNewElement(element: UIElement): Boolean {
        if (newElementId != null && element.id == newElementId) return true
        if (newElementId == null && element == newElement) return true
        return false
    }

    companion object {
        fun forElement(
            oldElement: UIElement,
            newElement: UIElement
        ): ReplaceElementCommand {
            return ReplaceElementCommand(
                oldElementId = oldElement.id,
                newElementId = newElement.id,
                elementMatcher = if (oldElement.id == null) { el -> el == oldElement } else null,
                oldElement = oldElement,
                newElement = newElement,
                elementDescription = oldElement.displayName()
            )
        }
    }
}
