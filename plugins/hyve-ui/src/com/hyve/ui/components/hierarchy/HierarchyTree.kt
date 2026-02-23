package com.hyve.ui.components.hierarchy

import androidx.compose.foundation.ContextMenuArea
import androidx.compose.foundation.ContextMenuItem
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.TextField
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.IconButton
import org.jetbrains.jewel.ui.component.Divider
import org.jetbrains.jewel.ui.icons.AllIconsKeys
import org.jetbrains.jewel.ui.Orientation
import com.hyve.ui.core.domain.elements.UIElement
import com.hyve.ui.core.domain.elements.ElementMetadata
import com.hyve.ui.core.domain.properties.PropertyMap
import com.hyve.ui.core.domain.properties.PropertyValue
import com.hyve.ui.core.domain.anchor.AnchorValue
import com.hyve.ui.core.id.ElementId
import com.hyve.ui.core.id.ElementType
import com.hyve.ui.settings.TextInputFocusState
import com.hyve.ui.canvas.CanvasState
import com.hyve.common.compose.HyveOpacity
import com.hyve.common.compose.HyveSpacing
import com.hyve.ui.state.EditDeltaTracker
import com.hyve.ui.state.command.*
import com.hyve.ui.rendering.layout.AnchorCalculator
import com.hyve.ui.rendering.layout.Rect

/**
 * State for the hierarchy tree panel.
 */
@Stable
class HierarchyTreeState {
    var searchQuery by mutableStateOf("")
    var draggedElement by mutableStateOf<UIElement?>(null)
    var isDragging by mutableStateOf(false)

    // Track global drag position for drop zone calculation
    var dragPositionY by mutableStateOf(0f)

    // Track the current drop target and position
    var dropTarget by mutableStateOf<UIElement?>(null)
    var dropPosition by mutableStateOf<DropPosition?>(null)

    // Track which node is currently being renamed (to confirm on click-away)
    var activeRenameConfirm: (() -> Unit)? by mutableStateOf(null)

    // Track expanded state per element ID
    private val expandedStates = mutableStateMapOf<ElementId?, Boolean>()

    fun isExpanded(element: UIElement): Boolean {
        return expandedStates.getOrPut(element.id) { true }
    }

    fun setExpanded(element: UIElement, expanded: Boolean) {
        expandedStates[element.id] = expanded
    }

    fun toggleExpanded(element: UIElement) {
        expandedStates[element.id] = !isExpanded(element)
    }

    fun clearSearch() {
        searchQuery = ""
    }
}

/**
 * Remember hierarchy tree state.
 */
@Composable
fun rememberHierarchyTreeState(): HierarchyTreeState {
    return remember { HierarchyTreeState() }
}

/**
 * Hierarchy tree panel showing the document structure.
 *
 * Features:
 * - Tree view of parent/child structure
 * - Click to select (syncs with canvas)
 * - Visibility and lock toggles
 * - Right-click context menu
 * - Search/filter by name or type
 * - Drag to reorder/reparent (future)
 */
@Composable
fun HierarchyTree(
    canvasState: CanvasState,
    modifier: Modifier = Modifier,
    state: HierarchyTreeState = rememberHierarchyTreeState(),
    onOpenComposer: ((UIElement) -> Unit)? = null,
    onCollapse: (() -> Unit)? = null
) {
    val rootElement = canvasState.rootElement.value
    val selectedElements = canvasState.selectedElements.value
    val scrollState = rememberScrollState()

    Box(
        modifier = modifier.background(JewelTheme.globalColors.panelBackground)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header
            HierarchyHeader(
                searchQuery = state.searchQuery,
                onSearchQueryChange = { state.searchQuery = it },
                onClearSearch = { state.clearSearch() },
                onCollapse = onCollapse
            )

            Divider(Orientation.Horizontal)

            // Tree content
            if (rootElement == null) {
                // No document
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No document open",
                        color = JewelTheme.globalColors.text.info
                    )
                }
            } else {
                // Tree view
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .pointerInput(state.activeRenameConfirm) {
                            detectTapGestures {
                                // When clicking anywhere in the tree, confirm any active rename
                                state.activeRenameConfirm?.invoke()
                                state.activeRenameConfirm = null
                            }
                        }
                        .verticalScroll(scrollState)
                        .padding(HyveSpacing.xs)
                ) {
                    // Filter elements if search is active
                    val filteredRoot = if (state.searchQuery.isNotBlank()) {
                        filterElement(rootElement, state.searchQuery)
                    } else {
                        rootElement
                    }

                    if (filteredRoot != null) {
                        HierarchyTreeNode(
                            element = filteredRoot,
                            depth = 0,
                            index = 0,
                            isRoot = true,  // This is the actual root element
                            canvasState = canvasState,
                            treeState = state,
                            selectedElements = selectedElements,
                            onOpenComposer = onOpenComposer
                        )
                    } else if (state.searchQuery.isNotBlank()) {
                        // No matches
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(HyveSpacing.lg),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No matching elements",
                                color = JewelTheme.globalColors.text.info
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Header with search bar and title.
 */
@Composable
private fun HierarchyHeader(
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onClearSearch: () -> Unit,
    onCollapse: (() -> Unit)? = null
) {
    val searchTextState = rememberTextFieldState(searchQuery)

    // Sync TextFieldState changes back to the parent state
    LaunchedEffect(searchTextState.text) {
        val newValue = searchTextState.text.toString()
        if (newValue != searchQuery) {
            onSearchQueryChange(newValue)
        }
    }

    // Sync external changes (like clear) to the TextFieldState
    LaunchedEffect(searchQuery) {
        if (searchTextState.text.toString() != searchQuery) {
            searchTextState.setTextAndPlaceCursorAtEnd(searchQuery)
        }
    }

    Column(modifier = Modifier.fillMaxWidth().padding(HyveSpacing.md)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Hierarchy",
                color = JewelTheme.globalColors.text.normal,
                fontWeight = FontWeight.Medium
            )
            if (onCollapse != null) {
                IconButton(
                    onClick = onCollapse,
                    modifier = Modifier.size(20.dp)
                ) {
                    Icon(
                        key = AllIconsKeys.General.ChevronRight,
                        contentDescription = "Collapse",
                        modifier = Modifier.size(14.dp),
                        tint = JewelTheme.globalColors.text.info
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(HyveSpacing.sm))

        // Search field
        TextField(
            state = searchTextState,
            placeholder = {
                Text("Search...")
            },
            leadingIcon = {
                Icon(
                    key = AllIconsKeys.Actions.Search,
                    contentDescription = "Search",
                    modifier = Modifier.size(16.dp)
                )
            },
            trailingIcon = {
                if (searchQuery.isNotBlank()) {
                    IconButton(
                        onClick = onClearSearch,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            key = AllIconsKeys.Actions.Close,
                            contentDescription = "Clear",
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { focusState ->
                    TextInputFocusState.setFocused(focusState.isFocused)
                }
        )
    }
}

/**
 * Recursive tree node that renders an element and its children.
 *
 * @param element The UI element to render
 * @param depth The nesting depth (used for indentation)
 * @param index The index of this element among its siblings
 * @param isRoot Whether this is the root element (affects styling and behavior)
 * @param canvasState The canvas state for interactions
 * @param treeState The hierarchy tree state
 * @param selectedElements Currently selected elements
 */
@Composable
private fun HierarchyTreeNode(
    element: UIElement,
    depth: Int,
    index: Int,
    isRoot: Boolean = false,
    canvasState: CanvasState,
    treeState: HierarchyTreeState,
    selectedElements: Set<UIElement>,
    onOpenComposer: ((UIElement) -> Unit)? = null
) {
    val isSelected = element in selectedElements ||
            selectedElements.any { it.id == element.id && element.id != null }
    val nodeState = rememberTreeNodeState(initialExpanded = treeState.isExpanded(element))

    // Sync node state with tree state
    LaunchedEffect(nodeState.isExpanded) {
        treeState.setExpanded(element, nodeState.isExpanded)
    }

    // Register/unregister rename confirm callback when rename mode changes
    LaunchedEffect(nodeState.isRenaming) {
        if (nodeState.isRenaming) {
            treeState.activeRenameConfirm = {
                val newName = nodeState.renameText.trim()
                if (newName.isNotEmpty()) {
                    renameElement(element, newName, canvasState)
                }
                nodeState.isRenaming = false
            }
        } else {
            // Only clear if this node's callback is still the active one
            if (treeState.activeRenameConfirm != null) {
                treeState.activeRenameConfirm = null
            }
        }
    }

    // Context menu - build items fresh each time to avoid stale element references
    ContextMenuArea(items = { buildContextMenuItems(element, canvasState, nodeState, isRoot, onOpenComposer) }) {
        TreeNode(
            element = element,
            depth = depth,
            index = index,
            isSelected = isSelected,
            isRoot = isRoot,
            onSelect = { el ->
                // Confirm any active rename before selecting
                treeState.activeRenameConfirm?.invoke()
                treeState.activeRenameConfirm = null
                canvasState.selectElement(el)
            },
            onToggleVisibility = { el ->
                toggleElementVisibility(el, canvasState)
            },
            onToggleLock = { el ->
                toggleElementLock(el, canvasState)
            },
            onToggleExpand = { el ->
                treeState.toggleExpanded(el)
            },
            onRename = { el, newName ->
                treeState.activeRenameConfirm = null
                renameElement(el, newName, canvasState)
            },
            onContextMenu = { _, _ -> },
            onDragStart = { el ->
                treeState.draggedElement = el
                treeState.isDragging = true
            },
            onDragEnd = {
                // Execute drop if there's a valid target
                val target = treeState.dropTarget
                val position = treeState.dropPosition
                if (target != null && position != null) {
                    handleDrop(
                        draggedElement = treeState.draggedElement,
                        targetElement = target,
                        position = position,
                        canvasState = canvasState
                    )
                }
                treeState.draggedElement = null
                treeState.isDragging = false
                treeState.dragPositionY = 0f
                treeState.dropTarget = null
                treeState.dropPosition = null
            },
            onDragPositionChange = { y ->
                treeState.dragPositionY = y
            },
            onDropTargetChange = { target, position ->
                treeState.dropTarget = target
                treeState.dropPosition = position
            },
            onDrop = { targetElement, position ->
                handleDrop(
                    draggedElement = treeState.draggedElement,
                    targetElement = targetElement,
                    position = position,
                    canvasState = canvasState
                )
            },
            isDragging = treeState.isDragging,
            draggedElement = treeState.draggedElement,
            dragPositionY = treeState.dragPositionY,
            state = nodeState
        ) {
            // Render children recursively
            // Root's children start at depth 0 (no extra indentation since root is always visible)
            val childDepth = if (isRoot) 0 else depth + 1
            element.children.forEachIndexed { childIndex, child ->
                // Key by ID (or index for ID-less elements) so Compose tracks
                // each child's identity across recompositions — prevents flickering
                // when elements are duplicated or reordered.
                key(child.id?.value ?: "idx_$childIndex") {
                    HierarchyTreeNode(
                        element = child,
                        depth = childDepth,
                        index = childIndex,
                        isRoot = false,  // Only the actual root element is marked as root
                        canvasState = canvasState,
                        treeState = treeState,
                        selectedElements = selectedElements,
                        onOpenComposer = onOpenComposer
                    )
                }
            }
        }
    }
}

/**
 * Build context menu items for an element.
 * Root element has no context menu options (it's a fixed, immutable container).
 */
private fun buildContextMenuItems(
    element: UIElement,
    canvasState: CanvasState,
    nodeState: TreeNodeState,
    isRoot: Boolean,
    onOpenComposer: ((UIElement) -> Unit)? = null
): List<ContextMenuItem> {
    // Root has no context menu options - it's a fixed container
    if (isRoot) {
        return emptyList()
    }

    return buildList {
        if (onOpenComposer != null) {
            add(ContextMenuItem("Edit in Composer          Enter") {
                onOpenComposer(element)
            })
        }
        add(ContextMenuItem("Rename") {
            nodeState.isRenaming = true
        })
        add(ContextMenuItem("Delete") {
            deleteElement(element, canvasState)
        })
        add(ContextMenuItem("Duplicate") {
            duplicateElement(element, canvasState)
        })
        add(ContextMenuItem("Wrap in Group") {
            wrapInGroup(element, canvasState)
        })
    }
}

/** Reserved element names that cannot be used for renaming */
private val RESERVED_NAMES = setOf("Root", "root", "ROOT")

/**
 * Rename an element by changing its ID.
 * "Root" is a reserved name and cannot be used.
 */
private fun renameElement(element: UIElement, newName: String, canvasState: CanvasState) {
    // "Root" is reserved for the root element
    if (newName in RESERVED_NAMES) {
        return
    }

    val oldName = element.id?.value ?: element.type.value

    // Create new element with updated ID and execute via command system
    // so the rename is tracked as a delta for the export pipeline
    val newId = ElementId(newName)
    val newElement = element.copy(id = newId)
    val command = ReplaceElementCommand.forElement(element, newElement)
    canvasState.executeCommand(command)
    canvasState.reportStatus("Renamed $oldName to $newName")
}

/**
 * Toggle element visibility in metadata.
 * Uses [CanvasState.updateElementMetadata] which handles tree update,
 * selection sync, and sidecar persistence. Metadata is editor-only state
 * (not exported to .ui files) so undo is intentionally skipped.
 */
private fun toggleElementVisibility(element: UIElement, canvasState: CanvasState) {
    val newVisible = !element.metadata.visible
    canvasState.updateElementMetadata(element) { it.copy(visible = newVisible) }
    val name = element.id?.value ?: element.type.value
    canvasState.reportStatus(if (newVisible) "Show $name" else "Hide $name")
}

/**
 * Toggle element lock state in metadata.
 * Uses [CanvasState.updateElementMetadata] which handles tree update,
 * selection sync, and sidecar persistence. Metadata is editor-only state
 * (not exported to .ui files) so undo is intentionally skipped.
 */
private fun toggleElementLock(element: UIElement, canvasState: CanvasState) {
    val newLocked = !element.metadata.locked
    canvasState.updateElementMetadata(element) { it.copy(locked = newLocked) }
    val name = element.id?.value ?: element.type.value
    canvasState.reportStatus(if (newLocked) "Lock $name" else "Unlock $name")
}

/**
 * Delete an element from the document.
 */
private fun deleteElement(element: UIElement, canvasState: CanvasState) {
    val root = canvasState.rootElement.value ?: return

    // Don't allow deleting the root element
    if (element == root || (element.id != null && element.id == root.id)) {
        return
    }

    // Find the current version of this element in the tree.
    // For elements with IDs, look up by ID. For ID-less elements,
    // find by structural equality (the reference may be stale from
    // context menu caching or a search-filter copy).
    val elementId = element.id
    val currentElement = if (elementId != null) {
        root.findDescendantById(elementId) ?: element
    } else {
        var found: UIElement? = null
        root.visitDescendants { el ->
            if (found == null && el == element) {
                found = el
            }
        }
        found ?: element
    }

    // Find parent and index using the current element
    val parentInfo = findParentOfElement(root, currentElement) ?: return
    val (parent, childIndex) = parentInfo

    // Create and execute delete command
    val command = if (parent == root) {
        DeleteElementCommand.fromRoot(currentElement, root, childIndex)
    } else {
        DeleteElementCommand.forElement(currentElement, parent, childIndex)
    }

    if (canvasState.executeCommand(command, allowMerge = false)) {
        currentElement.id?.let { id ->
            canvasState.recordDelta(EditDeltaTracker.EditDelta.DeleteElement(
                elementId = id
            ))
        }
    }
    canvasState.clearSelection()
}

/**
 * Duplicate an element.
 */
private fun duplicateElement(element: UIElement, canvasState: CanvasState) {
    val root = canvasState.rootElement.value ?: return

    // Find parent and index
    val parentInfo = findParentOfElement(root, element) ?: return
    val (parent, childIndex) = parentInfo

    // Deep-clone with _copy suffix on all descendant IDs
    val duplicatedElement = element.mapDescendants { el ->
        if (el.id != null) {
            el.copy(id = ElementId("${el.id.value}_copy"))
        } else {
            el
        }
    }

    // Create command to add the duplicate
    val command = if (parent == root) {
        AddElementCommand.toRoot(duplicatedElement, childIndex + 1)
    } else {
        AddElementCommand.toParent(parent, duplicatedElement, childIndex + 1)
    }

    if (canvasState.executeCommand(command, allowMerge = false)) {
        // Record structural delta so the export pipeline persists the duplicate
        canvasState.recordDelta(EditDeltaTracker.EditDelta.AddElement(
            parentId = if (parent == root) null else parent.id,
            index = childIndex + 1,
            element = duplicatedElement
        ))
    }
}

/**
 * Wrap an element in a Group container.
 * Executed as a composite command (delete + add) so it's undoable.
 */
private fun wrapInGroup(element: UIElement, canvasState: CanvasState) {
    val root = canvasState.rootElement.value ?: return

    // Find parent and index
    val parentInfo = findParentOfElement(root, element) ?: return
    val (parent, childIndex) = parentInfo

    // Create a group containing the element
    val groupId = ElementId("Group_wrap_${System.currentTimeMillis()}")
    val group = UIElement(
        type = ElementType("Group"),
        id = groupId,
        properties = element.properties, // Copy anchor from wrapped element
        children = listOf(element.copy(
            properties = PropertyMap.of(
                "Anchor" to PropertyValue.Anchor(AnchorValue.fill())
            )
        ))
    )

    // Build undoable command: delete original element, then add group at same position
    val deleteCommand = if (parent == root) {
        DeleteElementCommand.fromRoot(element, root, childIndex)
    } else {
        DeleteElementCommand.forElement(element, parent, childIndex)
    }

    val addCommand = if (parent == root) {
        AddElementCommand.toRoot(group, childIndex)
    } else {
        AddElementCommand.toParent(parent, group, childIndex)
    }

    val command = CompositeCommand(
        listOf(deleteCommand, addCommand),
        "Wrap ${element.displayName()} in Group"
    )

    if (canvasState.executeCommand(command, allowMerge = false)) {
        element.id?.let { elementId ->
            canvasState.recordDelta(EditDeltaTracker.EditDelta.DeleteElement(
                elementId = elementId
            ))
        }
        canvasState.recordDelta(EditDeltaTracker.EditDelta.AddElement(
            parentId = if (parent == root) null else parent.id,
            index = childIndex,
            element = group
        ))
    }
}

/**
 * Handle dropping an element onto a target.
 */
private fun handleDrop(
    draggedElement: UIElement?,
    targetElement: UIElement,
    position: DropPosition,
    canvasState: CanvasState
) {
    val dragged = draggedElement ?: return
    val root = canvasState.rootElement.value ?: return

    // Find current parent and index of dragged element
    val draggedParentInfo = findParentOfElement(root, dragged) ?: return
    val (oldParent, oldIndex) = draggedParentInfo

    // Find target parent and index
    val targetParentInfo = findParentOfElement(root, targetElement)
    val (targetParent, targetIndex) = targetParentInfo ?: (root to root.children.indexOf(targetElement))

    when (position) {
        DropPosition.BEFORE -> {
            // Move before target
            moveElement(root, dragged, oldParent, oldIndex, targetParent, targetIndex, canvasState)
        }
        DropPosition.AFTER -> {
            // Move after target
            val newIndex = if (targetElement.children.isEmpty()) {
                targetIndex + 1
            } else {
                targetIndex + 1
            }
            moveElement(root, dragged, oldParent, oldIndex, targetParent, newIndex, canvasState)
        }
        DropPosition.INTO -> {
            // Move as child of target (if target can have children)
            if (targetElement.canHaveChildren()) {
                moveElement(root, dragged, oldParent, oldIndex, targetElement, targetElement.children.size, canvasState)
            }
        }
    }
}

/**
 * Move an element from one parent to another.
 * When reparenting (changing parent), the element's anchor is adjusted to preserve
 * its visual position on the canvas.
 */
private fun moveElement(
    root: UIElement,
    element: UIElement,
    oldParent: UIElement,
    oldIndex: Int,
    newParent: UIElement,
    newIndex: Int,
    canvasState: CanvasState
) {
    // Check if we're actually changing parents (reparenting) vs just reordering
    val isReparenting = oldParent != newParent &&
            (oldParent.id != newParent.id || oldParent.id == null)

    // If reparenting, compute new anchor to preserve visual position
    val elementToMove = if (isReparenting) {
        // Get current bounds of the element
        val elementBounds = canvasState.getBounds(element)
        // Get bounds of the new parent
        val newParentBounds = canvasState.getBounds(newParent)

        if (elementBounds != null && newParentBounds != null) {
            // Convert ElementBounds to Rect for the calculator
            val elementRect = Rect(
                elementBounds.x,
                elementBounds.y,
                elementBounds.width,
                elementBounds.height
            )
            val parentRect = Rect(
                newParentBounds.x,
                newParentBounds.y,
                newParentBounds.width,
                newParentBounds.height
            )

            // Compute new anchor that preserves visual position
            val newAnchor = AnchorCalculator.computeAnchorForBounds(elementRect, parentRect)

            // Update the element with the new anchor
            element.setProperty("Anchor", PropertyValue.Anchor(newAnchor))
        } else {
            // Fallback: use original element if we can't get bounds
            element
        }
    } else {
        // Just reordering within same parent - keep anchor as is
        element
    }

    // Build composite command: delete + add
    val deleteCommand = if (oldParent == root) {
        DeleteElementCommand.fromRoot(element, root, oldIndex)
    } else {
        DeleteElementCommand.forElement(element, oldParent, oldIndex)
    }

    // Adjust index if moving within same parent and after the removed position
    val adjustedIndex = if (oldParent == newParent && newIndex > oldIndex) {
        newIndex - 1
    } else {
        newIndex
    }

    val addCommand = if (newParent == root) {
        AddElementCommand.toRoot(elementToMove, adjustedIndex)
    } else {
        AddElementCommand.toParent(newParent, elementToMove, adjustedIndex)
    }

    val compositeCommand = CompositeCommand(
        listOf(deleteCommand, addCommand),
        "Move ${element.displayName()}"
    )

    if (canvasState.executeCommand(compositeCommand, allowMerge = false)) {
        element.id?.let { elementId ->
            canvasState.recordDelta(EditDeltaTracker.EditDelta.DeleteElement(
                elementId = elementId
            ))
            canvasState.recordDelta(EditDeltaTracker.EditDelta.AddElement(
                parentId = if (newParent == root) null else newParent.id,
                index = adjustedIndex,
                element = elementToMove
            ))
        }
    }
}

/**
 * Find the parent element and index of a child element.
 */
private fun findParentOfElement(root: UIElement, target: UIElement): Pair<UIElement, Int>? {
    // Check if target is a direct child of root
    val rootIndex = root.children.indexOfFirst { it == target || (it.id == target.id && target.id != null) }
    if (rootIndex >= 0) {
        return root to rootIndex
    }

    // Search descendants
    var result: Pair<UIElement, Int>? = null
    root.visitDescendants { element ->
        if (result == null) {
            val index = element.children.indexOfFirst { it == target || (it.id == target.id && target.id != null) }
            if (index >= 0) {
                result = element to index
            }
        }
    }
    return result
}

/**
 * Filter element tree to only include elements matching the search query.
 * Returns null if neither the element nor any descendant matches.
 */
private fun filterElement(element: UIElement, query: String): UIElement? {
    val queryLower = query.lowercase()

    // Check if this element matches
    val thisMatches = element.type.value.lowercase().contains(queryLower) ||
            (element.id?.value?.lowercase()?.contains(queryLower) == true)

    // Filter children recursively
    val filteredChildren = element.children.mapNotNull { filterElement(it, query) }

    // Include this element if it matches or has matching descendants
    return if (thisMatches || filteredChildren.isNotEmpty()) {
        element.copy(children = filteredChildren)
    } else {
        null
    }
}
