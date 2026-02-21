package com.hyve.ui.components.toolbox

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hyve.ui.core.id.ElementType
import com.hyve.ui.registry.ElementTypeRegistry
import com.hyve.ui.settings.TextInputFocusState
import com.hyve.ui.canvas.CanvasState
import com.hyve.ui.schema.ElementCategory
import com.hyve.ui.schema.ElementSchema
import com.hyve.ui.schema.RuntimeSchemaRegistry
import com.hyve.ui.schema.SchemaRegistry
import com.hyve.common.compose.HyveOpacity
import com.hyve.common.compose.HyveSpacing
import com.hyve.common.compose.HyveShapes
import com.hyve.common.compose.HyveTypography
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.TextField
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.IconButton
import org.jetbrains.jewel.ui.component.Divider
import org.jetbrains.jewel.ui.component.VerticalScrollbar
import org.jetbrains.jewel.ui.Orientation
import org.jetbrains.jewel.ui.icons.AllIconsKeys
import kotlin.math.roundToInt

/**
 * Callback interface for handling element drops.
 */
fun interface DropHandler {
    /**
     * Called when an element is dropped.
     * @param schema The schema of the dropped element
     * @param windowPosition The drop position in window coordinates
     */
    fun onDrop(schema: ElementSchema, windowPosition: Offset)
}

/**
 * State for the element toolbox.
 * Tracks dragging state for drag-to-canvas element creation,
 * and hover state for tooltip display.
 */
class ElementToolboxState {
    // Drag state
    private val _isDragging = mutableStateOf(false)
    val isDragging: State<Boolean> = _isDragging

    private val _dragSchema = mutableStateOf<ElementSchema?>(null)
    val dragSchema: State<ElementSchema?> = _dragSchema

    // Current drag position in window coordinates
    private val _dragPosition = mutableStateOf(Offset.Zero)
    val dragPosition: State<Offset> = _dragPosition

    // Hover state for tooltip
    private val _hoveredSchema = mutableStateOf<ElementSchema?>(null)
    val hoveredSchema: State<ElementSchema?> = _hoveredSchema

    private val _hoverPosition = mutableStateOf(Offset.Zero)
    val hoverPosition: State<Offset> = _hoverPosition

    // Drop handler - called when drag ends
    var dropHandler: DropHandler? = null

    /**
     * Start dragging an element from the toolbox.
     */
    fun startDrag(schema: ElementSchema, windowPosition: Offset) {
        _dragSchema.value = schema
        _isDragging.value = true
        _dragPosition.value = windowPosition
        // Clear hover when starting drag
        _hoveredSchema.value = null
    }

    /**
     * Update the drag position during dragging.
     */
    fun updateDragPosition(windowPosition: Offset) {
        _dragPosition.value = windowPosition
    }

    /**
     * End the drag operation and notify the drop handler.
     */
    fun endDrag() {
        val schema = _dragSchema.value
        val position = _dragPosition.value
        if (schema != null) {
            dropHandler?.onDrop(schema, position)
        }
        _isDragging.value = false
        _dragSchema.value = null
        _dragPosition.value = Offset.Zero
    }

    /**
     * Cancel the drag operation.
     */
    fun cancelDrag() {
        _isDragging.value = false
        _dragSchema.value = null
        _dragPosition.value = Offset.Zero
    }

    /**
     * Set the currently hovered element for tooltip display.
     */
    fun setHoveredElement(schema: ElementSchema, windowPosition: Offset) {
        _hoveredSchema.value = schema
        _hoverPosition.value = windowPosition
    }

    /**
     * Clear the hovered element.
     */
    fun clearHoveredElement() {
        _hoveredSchema.value = null
        _hoverPosition.value = Offset.Zero
    }
}

/**
 * Remember toolbox state across recompositions.
 */
@Composable
fun rememberElementToolboxState(): ElementToolboxState {
    return remember { ElementToolboxState() }
}

/**
 * Element toolbox panel showing available element types as draggable tiles.
 *
 * Features:
 * - Elements displayed as distinct card/tile UI
 * - Grouped by category with collapsible sections
 * - Drag tiles onto canvas to create elements
 * - Search bar to filter elements by name
 * - Uses RuntimeSchemaRegistry for discovered element types when available
 */
@Composable
fun ElementToolbox(
    canvasState: CanvasState,
    toolboxState: ElementToolboxState,
    runtimeRegistry: RuntimeSchemaRegistry? = null,
    onCollapse: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    // If runtime registry is available and loaded, convert its schemas for display
    // Otherwise fall back to the hardcoded SchemaRegistry
    val schemasByCategory: Map<ElementCategory, List<ElementSchema>> = remember(runtimeRegistry) {
        if (runtimeRegistry != null && runtimeRegistry.isLoaded) {
            // Convert RuntimeElementSchema to ElementSchema for display
            runtimeRegistry.getAllElementTypes()
                .filter { !isToolboxBlacklisted(it) }
                .mapNotNull { typeName ->
                    val runtimeSchema = runtimeRegistry.getElementSchema(typeName) ?: return@mapNotNull null
                    val desc = ElementDisplayInfo.descriptionFor(typeName) ?: ""
                    val occurrences = runtimeSchema.occurrences
                    // Combine description + occurrence footnote into description field
                    val fullDescription = if (desc.isNotEmpty() && occurrences > 0) {
                        "$desc\n\nDiscovered from vanilla UI ($occurrences occurrences)"
                    } else if (occurrences > 0) {
                        "Discovered from vanilla UI ($occurrences occurrences)"
                    } else {
                        desc
                    }
                    ElementSchema(
                        type = ElementType(typeName),
                        category = runtimeSchema.category,
                        description = fullDescription,
                        canHaveChildren = runtimeSchema.canHaveChildren,
                        properties = emptyList() // Properties are handled by RuntimeSchemaRegistry
                    )
                }
                .groupBy { it.category }
        } else {
            // Fallback to hardcoded schemas
            SchemaRegistry.default().getSchemasByCategory()
        }
    }

    // Track collapsed categories
    val collapsedCategories = remember { mutableStateMapOf<ElementCategory, Boolean>() }

    // Search state using Jewel TextFieldState
    val searchTextState = rememberTextFieldState("")
    val searchQuery = searchTextState.text.toString()

    Box(
        modifier = modifier.background(JewelTheme.globalColors.panelBackground)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header with search
            val focusManager = LocalFocusManager.current
            ToolboxHeader(
                searchTextState = searchTextState,
                focusManager = focusManager,
                onCollapse = onCollapse
            )

            Divider(Orientation.Horizontal)

            // Element list grouped by category (with scrollbar)
            val lazyListState = rememberLazyListState()
            Box(modifier = Modifier.fillMaxSize()) {
                LazyColumn(
                    state = lazyListState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = HyveSpacing.sm,
                        end = HyveSpacing.sm,
                        top = HyveSpacing.xs,
                        bottom = HyveSpacing.sm
                    ),
                    verticalArrangement = Arrangement.spacedBy(HyveSpacing.xs)
                ) {
                    // Display categories in a specific order
                    val orderedCategories = listOf(
                        ElementCategory.CONTAINER,
                        ElementCategory.TEXT,
                        ElementCategory.INTERACTIVE,
                        ElementCategory.INPUT,
                        ElementCategory.MEDIA,
                        ElementCategory.LAYOUT,
                        ElementCategory.ADVANCED,
                        ElementCategory.OTHER
                    )

                    for (category in orderedCategories) {
                        val allSchemas = schemasByCategory[category] ?: continue
                        if (allSchemas.isEmpty()) continue

                        // Filter schemas by search query
                        val schemas = if (searchQuery.isBlank()) {
                            allSchemas
                        } else {
                            allSchemas.filter { schema ->
                                schema.type.value.contains(searchQuery, ignoreCase = true) ||
                                ElementDisplayInfo.displayNameFor(schema.type.value).contains(searchQuery, ignoreCase = true) ||
                                schema.description.contains(searchQuery, ignoreCase = true)
                            }
                        }

                        // Skip empty categories after filtering
                        if (schemas.isEmpty()) continue

                        val isCollapsed = collapsedCategories[category] ?: false

                        // Category header with count
                        item(key = "category_${category.name}") {
                            CategoryHeader(
                                category = category,
                                isCollapsed = isCollapsed,
                                count = schemas.size,
                                onClick = {
                                    collapsedCategories[category] = !isCollapsed
                                }
                            )
                        }

                        // Category items (if not collapsed)
                        if (!isCollapsed) {
                            items(
                                items = schemas,
                                key = { "element_${it.type.value}" }
                            ) { schema ->
                                ElementTile(
                                    schema = schema,
                                    toolboxState = toolboxState
                                )
                            }
                        }
                    }

                    // Show "no results" message if search yields nothing
                    if (searchQuery.isNotBlank()) {
                        val totalMatches = orderedCategories.sumOf { category ->
                            val allSchemas = schemasByCategory[category] ?: emptyList()
                            allSchemas.count { schema ->
                                schema.type.value.contains(searchQuery, ignoreCase = true) ||
                                schema.description.contains(searchQuery, ignoreCase = true)
                            }
                        }

                        if (totalMatches == 0) {
                            item {
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

                VerticalScrollbar(
                    scrollState = lazyListState,
                    modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight()
                )
            }
        }
    }
}

/**
 * Toolbox header with search bar.
 */
@Composable
private fun ToolboxHeader(
    searchTextState: androidx.compose.foundation.text.input.TextFieldState,
    focusManager: FocusManager,
    onCollapse: (() -> Unit)? = null
) {
    val searchQuery = searchTextState.text.toString()

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(JewelTheme.globalColors.panelBackground)
    ) {
        Column(
            modifier = Modifier.padding(HyveSpacing.md)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(HyveSpacing.xs)
            ) {
                if (onCollapse != null) {
                    IconButton(
                        onClick = onCollapse,
                        modifier = Modifier.size(20.dp)
                    ) {
                        Icon(
                            key = AllIconsKeys.General.ChevronLeft,
                            contentDescription = "Collapse",
                            modifier = Modifier.size(14.dp),
                            tint = JewelTheme.globalColors.text.info
                        )
                    }
                }
                Text(
                    text = "Elements",
                    fontWeight = FontWeight.Medium,
                    color = JewelTheme.globalColors.text.normal
                )
            }

            Spacer(modifier = Modifier.height(HyveSpacing.sm))

            // Compact search field
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(HyveSpacing.xs)
            ) {
                TextField(
                    state = searchTextState,
                    placeholder = { Text("Search...") },
                    leadingIcon = {
                        Icon(
                            key = AllIconsKeys.Actions.Search,
                            contentDescription = "Search",
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    trailingIcon = if (searchQuery.isNotBlank()) {
                        {
                            IconButton(
                                onClick = {
                                    searchTextState.setTextAndPlaceCursorAtEnd("")
                                    focusManager.clearFocus()
                                },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    key = AllIconsKeys.Actions.Close,
                                    contentDescription = "Clear",
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    } else null,
                    modifier = Modifier
                        .weight(1f)
                        .onPreviewKeyEvent { event ->
                            when {
                                event.key == Key.Escape && event.type == KeyEventType.KeyDown -> {
                                    searchTextState.setTextAndPlaceCursorAtEnd("")
                                    focusManager.clearFocus()
                                    true
                                }
                                event.key == Key.Enter && event.type == KeyEventType.KeyDown -> {
                                    focusManager.clearFocus()
                                    true
                                }
                                else -> false
                            }
                        }
                        .onFocusChanged { focusState ->
                            TextInputFocusState.setFocused(focusState.isFocused)
                        }
                )
            }
        }
    }
}

/**
 * Collapsible category header with element count.
 */
@Composable
private fun CategoryHeader(
    category: ElementCategory,
    isCollapsed: Boolean,
    count: Int = 0,
    onClick: () -> Unit
) {
    val dimmedText = JewelTheme.globalColors.text.info.copy(alpha = 0.6f)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(HyveShapes.card)
            .clickable(onClick = onClick)
            .padding(vertical = HyveSpacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(HyveSpacing.xs)
    ) {
        Icon(
            key = if (isCollapsed) AllIconsKeys.General.ChevronRight else AllIconsKeys.General.ChevronDown,
            contentDescription = if (isCollapsed) "Expand" else "Collapse",
            modifier = Modifier.size(14.dp),
            tint = dimmedText
        )
        Text(
            text = category.displayName.uppercase(),
            fontWeight = FontWeight.Medium,
            style = HyveTypography.caption,
            color = dimmedText
        )
        if (count > 0) {
            Text(
                text = "($count)",
                style = HyveTypography.caption,
                color = JewelTheme.globalColors.text.info.copy(alpha = HyveOpacity.strong)
            )
        }
    }
}

/**
 * A draggable element row — compact 32dp height matching TreeNode style.
 */
@Composable
private fun ElementTile(
    schema: ElementSchema,
    toolboxState: ElementToolboxState
) {
    val isDraggingThis = toolboxState.isDragging.value && toolboxState.dragSchema.value?.type == schema.type

    // Track hover state
    var isHovered by remember { mutableStateOf(false) }

    // Track mouse position for tooltip
    var mousePosition by remember { mutableStateOf(Offset.Zero) }

    // Track the position of this tile in window coordinates
    var tilePositionInWindow by remember { mutableStateOf(Offset.Zero) }

    // Update toolbox hover state for tooltip rendering at window level
    LaunchedEffect(isHovered, schema, mousePosition) {
        if (isHovered && schema.description.isNotBlank()) {
            toolboxState.setHoveredElement(schema, tilePositionInWindow + mousePosition)
        } else if (!isHovered && toolboxState.hoveredSchema.value == schema) {
            toolboxState.clearHoveredElement()
        }
    }

    val hoverColor = JewelTheme.globalColors.outlines.focused.copy(alpha = HyveOpacity.medium)
    val backgroundColor = when {
        isDraggingThis -> JewelTheme.globalColors.outlines.focused.copy(alpha = HyveOpacity.medium)
        isHovered -> hoverColor
        else -> Color.Transparent
    }

    val iconPurple = Color(0xFF9D8CFF)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(32.dp)
            .background(backgroundColor, HyveShapes.card)
            .onGloballyPositioned { coordinates ->
                tilePositionInWindow = coordinates.positionInWindow()
            }
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        when (event.type) {
                            PointerEventType.Enter -> isHovered = true
                            PointerEventType.Exit -> {
                                isHovered = false
                            }
                            PointerEventType.Move -> {
                                val position = event.changes.firstOrNull()?.position
                                if (position != null) {
                                    mousePosition = position
                                }
                            }
                        }
                    }
                }
            }
            .pointerInput(schema) {
                detectDragGestures(
                    onDragStart = { localOffset ->
                        val windowPos = tilePositionInWindow + localOffset
                        toolboxState.startDrag(schema, windowPos)
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        val currentPos = toolboxState.dragPosition.value
                        toolboxState.updateDragPosition(currentPos + dragAmount)
                    },
                    onDragEnd = {
                        toolboxState.endDrag()
                    },
                    onDragCancel = {
                        toolboxState.cancelDrag()
                    }
                )
            }
            .padding(horizontal = HyveSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(HyveSpacing.sm)
    ) {
        Icon(
            key = getIconKeyForElementType(schema.type.value),
            contentDescription = schema.type.value,
            modifier = Modifier.size(20.dp),
            tint = iconPurple
        )
        Text(
            text = ElementDisplayInfo.displayNameFor(schema.type.value),
            fontWeight = FontWeight.Medium,
            color = JewelTheme.globalColors.text.normal
        )
    }
}

/**
 * Floating drag preview and tooltip overlay.
 * This should be rendered at the window level to appear above all content.
 */
@Composable
fun DragPreviewOverlay(
    toolboxState: ElementToolboxState
) {
    val isDragging = toolboxState.isDragging.value
    val dragSchema = toolboxState.dragSchema.value
    val dragPosition = toolboxState.dragPosition.value

    // Hover tooltip state
    val hoveredSchema = toolboxState.hoveredSchema.value
    val hoverPosition = toolboxState.hoverPosition.value

    // Tooltip for hovered element (only when not dragging)
    if (!isDragging && hoveredSchema != null && hoveredSchema.description.isNotBlank()) {
        // Split description into main text and footnote (separated by double newline)
        val descParts = hoveredSchema.description.split("\n\n", limit = 2)
        val mainDescription = descParts[0]
        val footnote = descParts.getOrNull(1)

        Box(
            modifier = Modifier
                .offset {
                    IntOffset(
                        (hoverPosition.x + 12).roundToInt(),
                        (hoverPosition.y - 8).roundToInt()
                    )
                }
        ) {
            Box(
                modifier = Modifier
                    .widthIn(max = 240.dp)
                    .shadow(4.dp, HyveShapes.dialog)
                    .background(JewelTheme.globalColors.panelBackground, HyveShapes.dialog)
                    .border(1.dp, JewelTheme.globalColors.borders.normal, HyveShapes.dialog)
                    .padding(horizontal = HyveSpacing.mld, vertical = HyveSpacing.smd)
            ) {
                Column {
                    Text(
                        text = mainDescription,
                        color = JewelTheme.globalColors.text.normal
                    )
                    if (footnote != null) {
                        Spacer(modifier = Modifier.height(HyveSpacing.xs))
                        Text(
                            text = footnote,
                            color = JewelTheme.globalColors.text.info.copy(alpha = 0.6f),
                            style = HyveTypography.badge
                        )
                    }
                }
            }
        }
    }

    // Drag preview
    if (isDragging && dragSchema != null) {
        // Floating card that follows the cursor
        Box(
            modifier = Modifier
                .offset {
                    IntOffset(
                        (dragPosition.x - 80).roundToInt(), // Center the preview
                        (dragPosition.y - 25).roundToInt()
                    )
                }
        ) {
            Box(
                modifier = Modifier
                    .width(160.dp)
                    .shadow(8.dp, HyveShapes.dialog)
                    .background(JewelTheme.globalColors.outlines.focused.copy(alpha = 0.3f), HyveShapes.dialog)
                    .border(1.dp, JewelTheme.globalColors.outlines.focused, HyveShapes.dialog)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(HyveSpacing.md),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(HyveSpacing.sm)
                ) {
                    Icon(
                        key = getIconKeyForElementType(dragSchema.type.value),
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = JewelTheme.globalColors.text.normal
                    )
                    Text(
                        text = dragSchema.type.value,
                        fontWeight = FontWeight.Medium,
                        color = JewelTheme.globalColors.text.normal
                    )
                }
            }
        }
    }
}

internal fun isToolboxBlacklisted(typeName: String): Boolean {
    val info = ElementTypeRegistry[typeName] ?: return false
    return !info.isToolboxVisible
}

/**
 * Get an appropriate Jewel icon key for each element type.
 * Delegates to [ElementTypeRegistry] for the toolbox-specific icon.
 */
internal fun getIconKeyForElementType(typeName: String): org.jetbrains.jewel.ui.icon.IconKey {
    return ElementTypeRegistry.getOrDefault(typeName).toolboxIcon
}
