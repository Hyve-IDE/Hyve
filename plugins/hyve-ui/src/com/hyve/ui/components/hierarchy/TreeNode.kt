package com.hyve.ui.components.hierarchy

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.TooltipArea
import androidx.compose.foundation.TooltipPlacement
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.DpOffset
import kotlinx.coroutines.withTimeoutOrNull
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.IconButton
import org.jetbrains.jewel.ui.icons.AllIconsKeys
import com.hyve.ui.core.domain.elements.UIElement
import com.hyve.ui.core.id.ElementId
import com.hyve.ui.registry.ElementTypeRegistry
import com.hyve.ui.settings.TextInputFocusState
import com.hyve.common.compose.HyveOpacity
import com.hyve.common.compose.HyveShapes
import com.hyve.common.compose.HyveSpacing

/**
 * Drop position indicator for drag-and-drop reordering.
 */
enum class DropPosition {
    BEFORE,  // Insert before this node
    INTO,    // Insert as child of this node
    AFTER    // Insert after this node
}

/**
 * State for a single tree node, tracking expansion, hover, and rename mode.
 */
@Stable
class TreeNodeState(
    initialExpanded: Boolean = true
) {
    var isExpanded by mutableStateOf(initialExpanded)
    var dropPosition by mutableStateOf<DropPosition?>(null)
    var isRenaming by mutableStateOf(false)
    var renameText by mutableStateOf("")
}

/**
 * Remember tree node state.
 */
@Composable
fun rememberTreeNodeState(
    initialExpanded: Boolean = true
): TreeNodeState {
    return remember { TreeNodeState(initialExpanded) }
}

/**
 * A single node in the hierarchy tree.
 *
 * Displays:
 * - Expand/collapse arrow (if has children)
 * - Element type icon
 * - Element name (type #id or type_index if no id)
 * - Visibility toggle (eye icon)
 * - Lock toggle (padlock icon)
 *
 * Interactions:
 * - Click to select
 * - Double-click to expand/collapse
 * - Right-click for context menu
 * - Drag to reorder/reparent
 */
@OptIn(ExperimentalComposeUiApi::class, ExperimentalFoundationApi::class)
@Composable
fun TreeNode(
    element: UIElement,
    depth: Int,
    index: Int,
    isSelected: Boolean,
    isRoot: Boolean = false,
    onSelect: (UIElement) -> Unit,
    onToggleVisibility: (UIElement) -> Unit,
    onToggleLock: (UIElement) -> Unit,
    onToggleExpand: (UIElement) -> Unit,
    onRename: (UIElement, String) -> Unit,
    onContextMenu: (UIElement, Offset) -> Unit,
    onDragStart: (UIElement) -> Unit,
    onDragEnd: () -> Unit,
    onDragPositionChange: (Float) -> Unit,
    onDropTargetChange: (UIElement?, DropPosition?) -> Unit,
    onDrop: (UIElement, DropPosition) -> Unit,
    isDragging: Boolean = false,
    draggedElement: UIElement? = null,
    dragPositionY: Float = 0f,
    state: TreeNodeState = rememberTreeNodeState(),
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit = {}
) {
    val hasChildren = element.children.isNotEmpty()
    val canHaveChildren = element.canHaveChildren()
    val isVisible = element.metadata.visible
    val isLocked = element.metadata.locked

    // Animation for expand arrow rotation
    val expandRotation by animateFloatAsState(
        targetValue = if (state.isExpanded) 90f else 0f
    )

    // Display name: user-defined ID, or fallback to Type_index if no ID
    val displayName = element.id?.value ?: "${element.type.value}_${index + 1}"

    // Tooltip shows the element type
    val tooltipText = element.type.value

    // Focus requester for rename text field
    val renameFocusRequester = remember { FocusRequester() }

    // Initialize rename text when entering rename mode
    LaunchedEffect(state.isRenaming) {
        if (state.isRenaming) {
            state.renameText = element.id?.value ?: ""
            renameFocusRequester.requestFocus()
        }
    }

    // Determine if this node can accept a drop
    // Root can only accept INTO drops (not BEFORE/AFTER since nothing can be outside root)
    val canAcceptDrop = draggedElement != null &&
            draggedElement != element &&
            !isDescendantOf(element, draggedElement)

    // Track node position for drop zone calculation
    var nodePositionY by remember { mutableStateOf(0f) }
    var nodeHeight by remember { mutableStateOf(32f) }

    // Dim the element if it's being dragged
    val isDraggedElement = draggedElement == element ||
            (draggedElement?.id != null && draggedElement.id == element.id)

    // Calculate drop position based on drag Y position relative to this node
    LaunchedEffect(isDragging, dragPositionY, nodePositionY, nodeHeight, canAcceptDrop, isRoot) {
        if (isDragging && canAcceptDrop && !isDraggedElement) {
            val relativeY = dragPositionY - nodePositionY
            val dropZone = when {
                relativeY < 0 || relativeY > nodeHeight -> null  // Not over this node
                // Root can only accept INTO - nothing can exist outside the root
                isRoot -> if (canHaveChildren) DropPosition.INTO else null
                relativeY < nodeHeight * 0.25f -> DropPosition.BEFORE
                relativeY > nodeHeight * 0.75f -> DropPosition.AFTER
                canHaveChildren -> DropPosition.INTO
                else -> if (relativeY < nodeHeight * 0.5f) DropPosition.BEFORE else DropPosition.AFTER
            }
            state.dropPosition = dropZone
            if (dropZone != null) {
                onDropTargetChange(element, dropZone)
            }
        } else {
            if (state.dropPosition != null) {
                state.dropPosition = null
            }
        }
    }

    // Hover state
    var isHovered by remember { mutableStateOf(false) }

    // Colors from JewelTheme
    val selectedBackground = JewelTheme.globalColors.outlines.focused.copy(alpha = HyveOpacity.medium)
    val hoverBackground = JewelTheme.globalColors.outlines.focused.copy(alpha = HyveOpacity.subtle)
    val dropTargetBackground = JewelTheme.globalColors.outlines.focused.copy(alpha = HyveOpacity.light)
    val rootBackground = JewelTheme.globalColors.panelBackground

    Column(modifier = modifier) {
        // Drop indicator above
        if (state.dropPosition == DropPosition.BEFORE && canAcceptDrop) {
            DropIndicatorLine()
        }

        // Main node row
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(32.dp)
                .onGloballyPositioned { coordinates ->
                    nodePositionY = coordinates.positionInWindow().y
                    nodeHeight = coordinates.size.height.toFloat()
                }
                .pointerHoverIcon(PointerIcon.Default)
                .onPointerEvent(PointerEventType.Enter) { isHovered = true }
                .onPointerEvent(PointerEventType.Exit) { isHovered = false }
                // Single pointerInput handles both click and drag to avoid event conflicts
                .pointerInput(element, state.isRenaming, isRoot) {
                    if (state.isRenaming) return@pointerInput

                    awaitPointerEventScope {
                        while (true) {
                            val down = awaitFirstDown(requireUnconsumed = false)

                            // Immediately select on pointer down for responsive feel
                            onSelect(element)

                            // Track for double-click detection
                            val downTime = System.currentTimeMillis()
                            val downPosition = down.position

                            // Wait to see if this becomes a drag or just a click
                            var isDragging = false
                            val dragThreshold = 4f  // pixels before considered a drag

                            while (down.pressed) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull() ?: break

                                if (!isDragging && !isRoot) {
                                    val dragDistance = (change.position - downPosition).getDistance()
                                    if (dragDistance > dragThreshold) {
                                        // Started dragging
                                        isDragging = true
                                        onDragStart(element)
                                        onDragPositionChange(nodePositionY + change.position.y)
                                    }
                                }

                                if (isDragging) {
                                    change.consume()
                                    onDragPositionChange(nodePositionY + change.position.y)
                                }

                                if (!change.pressed) {
                                    // Pointer released
                                    if (isDragging) {
                                        onDragEnd()
                                    } else {
                                        // Was a click - check for double click
                                        // Note: Selection already happened on down
                                        val clickDuration = System.currentTimeMillis() - downTime
                                        if (clickDuration < 300) {
                                            // This could be part of a double-click sequence
                                            // Wait briefly for second click
                                            val secondDown = withTimeoutOrNull(300L) {
                                                awaitFirstDown(requireUnconsumed = false)
                                            }
                                            if (secondDown != null && !isRoot) {
                                                // Double click - enter rename mode
                                                state.isRenaming = true
                                                // Consume the second down to prevent it starting a new sequence
                                                secondDown.consume()
                                            }
                                        }
                                    }
                                    break
                                }
                            }
                        }
                    }
                }
                .alpha(if (isDraggedElement) 0.5f else 1f)
                .background(
                    color = when {
                        isSelected -> selectedBackground
                        state.dropPosition == DropPosition.INTO && canAcceptDrop -> dropTargetBackground
                        isHovered && !isRoot -> hoverBackground
                        // Root gets a subtle header-like background to distinguish it
                        isRoot -> rootBackground
                        else -> Color.Transparent
                    },
                    shape = HyveShapes.card
                )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = (depth * 16 + 4).dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Expand/collapse arrow
                if (hasChildren) {
                    IconButton(
                        onClick = {
                            state.isExpanded = !state.isExpanded
                            onToggleExpand(element)
                        },
                        modifier = Modifier.size(20.dp)
                    ) {
                        Icon(
                            key = AllIconsKeys.General.ChevronRight,
                            contentDescription = if (state.isExpanded) "Collapse" else "Expand",
                            modifier = Modifier
                                .size(16.dp)
                                .rotate(expandRotation),
                            tint = JewelTheme.globalColors.text.info
                        )
                    }
                } else {
                    Spacer(modifier = Modifier.width(20.dp))
                }

                // Element type icon
                Icon(
                    key = getElementIconKey(element.type.value),
                    contentDescription = element.type.value,
                    modifier = Modifier.size(16.dp),
                    tint = if (isSelected)
                        JewelTheme.globalColors.outlines.focused
                    else
                        JewelTheme.globalColors.text.info
                )

                Spacer(modifier = Modifier.width(HyveSpacing.sm))

                // Element name (or rename text field)
                if (state.isRenaming) {
                    // Inline rename text field
                    BasicRenameTextField(
                        value = state.renameText,
                        onValueChange = { state.renameText = it },
                        onConfirm = {
                            val newName = state.renameText.trim()
                            if (newName.isNotEmpty()) {
                                onRename(element, newName)
                            }
                            state.isRenaming = false
                        },
                        onCancel = {
                            state.isRenaming = false
                        },
                        focusRequester = renameFocusRequester,
                        modifier = Modifier.weight(1f)
                    )
                } else {
                    TooltipArea(
                        tooltip = {
                            Box(
                                modifier = Modifier
                                    .background(
                                        JewelTheme.globalColors.panelBackground,
                                        HyveShapes.small
                                    )
                                    .padding(horizontal = HyveSpacing.sm, vertical = HyveSpacing.xs)
                            ) {
                                Text(
                                    text = tooltipText,
                                    color = JewelTheme.globalColors.text.normal
                                )
                            }
                        },
                        delayMillis = 1000,
                        tooltipPlacement = TooltipPlacement.CursorPoint(
                            offset = DpOffset(0.dp, 16.dp)
                        ),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(
                            text = displayName,
                            color = when {
                                !isVisible -> JewelTheme.globalColors.text.disabled
                                isSelected -> JewelTheme.globalColors.text.normal
                                else -> JewelTheme.globalColors.text.normal
                            },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.fillMaxWidth().alpha(if (isVisible) 1f else 0.5f)
                        )
                    }
                }

                // Visibility toggle
                IconButton(
                    onClick = { onToggleVisibility(element) },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        key = if (isVisible) AllIconsKeys.Actions.Show else AllIconsKeys.General.HideToolWindow,
                        contentDescription = if (isVisible) "Hide" else "Show",
                        modifier = Modifier.size(14.dp),
                        tint = if (isVisible)
                            JewelTheme.globalColors.text.info
                        else
                            JewelTheme.globalColors.text.disabled
                    )
                }

                // Lock toggle
                IconButton(
                    onClick = { onToggleLock(element) },
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        key = AllIconsKeys.Diff.Lock,
                        contentDescription = if (isLocked) "Unlock" else "Lock",
                        modifier = Modifier.size(14.dp),
                        tint = if (isLocked)
                            JewelTheme.globalColors.outlines.focused
                        else
                            JewelTheme.globalColors.text.info
                    )
                }
            }
        }

        // Drop indicator below (for dropping after - only when no children or collapsed)
        if (state.dropPosition == DropPosition.AFTER && canAcceptDrop && (!hasChildren || !state.isExpanded)) {
            DropIndicatorLine()
        }

        // Children (animated expand/collapse)
        AnimatedVisibility(
            visible = state.isExpanded && hasChildren,
            enter = expandVertically(),
            exit = shrinkVertically()
        ) {
            Column {
                content()
            }
        }

        // Drop indicator after children (for dropping after expanded parent)
        if (state.dropPosition == DropPosition.AFTER && canAcceptDrop && hasChildren && state.isExpanded) {
            Box(modifier = Modifier.padding(start = ((depth + 1) * 16).dp)) {
                DropIndicatorLine()
            }
        }
    }
}

/**
 * Visual indicator line for drop position.
 */
@Composable
private fun DropIndicatorLine() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(2.dp)
            .background(JewelTheme.globalColors.outlines.focused)
    )
}

/**
 * Get appropriate icon key for element type.
 * Delegates to [ElementTypeRegistry] for the hierarchy-specific icon.
 */
fun getElementIconKey(typeName: String): org.jetbrains.jewel.ui.icon.IconKey {
    return ElementTypeRegistry.getOrDefault(typeName).hierarchyIcon
}

/**
 * Check if an element is a descendant of another.
 * Used to prevent dropping a parent into its own child.
 */
private fun isDescendantOf(potentialDescendant: UIElement, potentialAncestor: UIElement): Boolean {
    if (potentialDescendant == potentialAncestor) return false

    var found = false
    potentialAncestor.visitDescendants { descendant ->
        if (descendant == potentialDescendant) {
            found = true
        }
    }
    return found
}

/**
 * Inline text field for renaming elements.
 * Supports Enter to confirm, Escape to cancel, and click-away to confirm.
 */
@Composable
private fun BasicRenameTextField(
    value: String,
    onValueChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    focusRequester: FocusRequester,
    modifier: Modifier = Modifier
) {
    // Only set initial selection once, not on every value change
    var textFieldValue by remember {
        mutableStateOf(TextFieldValue(value, selection = TextRange(0, value.length)))
    }

    // Track if we've gained focus at least once (to avoid triggering on initial render)
    var hasFocused by remember { mutableStateOf(false) }

    androidx.compose.foundation.text.BasicTextField(
        value = textFieldValue,
        onValueChange = { newValue ->
            textFieldValue = newValue
            onValueChange(newValue.text)
        },
        singleLine = true,
        textStyle = androidx.compose.ui.text.TextStyle(
            color = JewelTheme.globalColors.text.normal
        ),
        modifier = modifier
            .focusRequester(focusRequester)
            .onFocusChanged { focusState ->
                // Update global focus state for hotkey suppression
                TextInputFocusState.setFocused(focusState.isFocused)

                if (focusState.isFocused) {
                    hasFocused = true
                } else if (hasFocused) {
                    // Lost focus after having it - confirm the rename
                    onConfirm()
                }
            }
            .background(
                JewelTheme.globalColors.panelBackground,
                HyveShapes.small
            )
            .padding(horizontal = HyveSpacing.xs, vertical = HyveSpacing.xxs)
            .onPreviewKeyEvent { event ->
                when {
                    event.key == Key.Enter && event.type == KeyEventType.KeyDown -> {
                        onConfirm()
                        true
                    }
                    event.key == Key.Escape && event.type == KeyEventType.KeyDown -> {
                        onCancel()
                        true
                    }
                    else -> false
                }
            }
    )
}
