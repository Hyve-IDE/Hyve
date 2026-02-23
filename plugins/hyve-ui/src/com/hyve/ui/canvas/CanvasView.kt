// Copyright 2026 Hyve. All rights reserved.
package com.hyve.ui.canvas

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ContextMenuArea
import androidx.compose.foundation.TooltipArea
import androidx.compose.foundation.ContextMenuItem
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Text
import com.hyve.common.compose.HyveShapes
import com.hyve.common.compose.HyveSpacing
import com.hyve.common.compose.HyveTypography
import com.hyve.common.compose.HyveThemeColors
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hyve.ui.core.domain.elements.UIElement
import com.hyve.ui.core.domain.properties.PropertyValue
import com.hyve.ui.core.id.ElementId
import com.hyve.ui.rendering.layout.ElementBounds
import com.hyve.ui.rendering.painter.CanvasPainter
import com.hyve.ui.services.assets.AssetLoader
import com.hyve.ui.services.items.ItemRegistry
import com.hyve.ui.settings.TextInputFocusState
import com.hyve.ui.components.validation.ValidationPanelState
import java.awt.Cursor
import java.awt.Point
import java.awt.Toolkit
import java.awt.image.BufferedImage

/**
 * Main canvas view for rendering and interacting with UI elements.
 *
 * Features:
 * - Renders UI element tree using CanvasPainter
 * - Zoom controls: Ctrl+MouseWheel, Ctrl+Plus/Minus
 * - Pan controls: Space+Drag, Middle-mouse drag
 * - Grid overlay: Toggle with 'G' key
 * - Element selection: Click to select
 *
 * Input handling:
 * - Mouse wheel: Zoom in/out
 * - Space + Drag: Pan canvas
 * - Middle mouse + Drag: Pan canvas
 * - G key: Toggle grid
 * - Ctrl + 0: Reset zoom to 100%
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun CanvasView(
    state: CanvasState = rememberCanvasState(),
    validationState: ValidationPanelState? = null,
    assetLoader: AssetLoader? = null,
    itemRegistry: ItemRegistry? = null,
    onOpenComposer: ((UIElement) -> Unit)? = null,
    composerOpen: Boolean = false,
    onToggleHotkeyReference: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val textMeasurer = rememberTextMeasurer()

    // Track texture loading to trigger recomposition
    var textureLoadCounter by remember { mutableStateOf(0) }

    val painter = remember(textMeasurer, validationState, assetLoader, itemRegistry) {
        CanvasPainter(
            textMeasurer = textMeasurer,
            validationState = validationState,
            assetLoader = assetLoader,
            itemRegistry = itemRegistry,
            onTextureLoaded = { textureLoadCounter++ }
        )
    }

    // Read the counter to establish dependency (triggers recomposition when textures load)
    @Suppress("UNUSED_VARIABLE")
    val textureRecomposeKey = textureLoadCounter
    val focusRequester = remember { FocusRequester() }

    // Space/Shift are tracked at the editor level (HyveUIEditorContent.onPreviewKeyEvent)
    // so they work regardless of which panel has focus
    val spacePressed by state.spacePressed
    var isPanning by remember { mutableStateOf(false) }
    val shiftPressed by state.shiftPressed

    // Track last mouse position for context menu hit-testing
    var lastMousePosition by remember { mutableStateOf(Offset.Zero) }

    // Track element dragging
    var draggedElement by remember { mutableStateOf<UIElement?>(null) }
    var dragStartOffset by remember { mutableStateOf(Offset.Zero) }

    // Track resize handle dragging
    var resizeState by remember { mutableStateOf<Pair<UIElement, CanvasState.ResizeHandle>?>(null) }

    // Track double-click timing for text editing
    var lastClickTime by remember { mutableStateOf(0L) }
    var lastClickElementId by remember { mutableStateOf<ElementId?>(null) }
    val doubleClickThreshold = 300L // milliseconds

    // Text editing state
    val textEditingElement = state.textEditingElement.value
    var editingText by remember { mutableStateOf(TextFieldValue("")) }
    val textEditFocusRequester = remember { FocusRequester() }
    var textFieldHadFocus by remember(textEditingElement) { mutableStateOf(false) }

    // Request focus when the canvas is first composed
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    // Focus the text field when editing starts
    LaunchedEffect(textEditingElement) {
        if (textEditingElement != null) {
            val currentText = (textEditingElement.getProperty("Text") as? PropertyValue.Text)?.value ?: ""
            editingText = TextFieldValue(currentText, TextRange(0, currentText.length))
            textEditFocusRequester.requestFocus()
        }
    }

    // Read ONLY element-tree state at composable level (triggers recomposition when tree changes)
    val rootElement = state.rootElement.value
    val calculatedLayout = state.calculatedLayout.value
    val showGrid = state.showGrid.value
    val darkCanvas = state.darkCanvas.value
    val showScreenshot = state.showScreenshot.value
    val screenshotOpacity = state.screenshotOpacity.value
    val screenshotMode = state.screenshotMode.value

    // Read drag state to trigger recomposition during drag (needed for smooth visual feedback)
    val isDragging = state.isDragging.value
    val dragOffset = state.dragOffset.value

    // Read resize state to trigger recomposition during resize
    val isResizing = state.isResizing.value
    val resizeDragDelta = state.resizeDragDelta.value

    // Text editing element read separately (needed for overlay positioning)
    val textEditingElementForOverlay = state.textEditingElement.value

    // Track cursor based on what's under the mouse
    // Using Cursor objects (not int types) so we can include custom cursors
    val defaultCursor = remember { Cursor(Cursor.DEFAULT_CURSOR) }
    val moveCursor = remember { Cursor(Cursor.MOVE_CURSOR) }
    val nwResizeCursor = remember { Cursor(Cursor.NW_RESIZE_CURSOR) }
    val neResizeCursor = remember { Cursor(Cursor.NE_RESIZE_CURSOR) }
    val nResizeCursor = remember { Cursor(Cursor.N_RESIZE_CURSOR) }
    val eResizeCursor = remember { Cursor(Cursor.E_RESIZE_CURSOR) }
    // Not-allowed cursor for immovable elements (Center+Middle, locked)
    val notAllowedCursor = remember {
        try {
            Cursor.getSystemCustomCursor("Invalid.32x32")
        } catch (_: Exception) {
            try {
                val tk = Toolkit.getDefaultToolkit()
                val size = tk.getBestCursorSize(24, 24)
                val w = size.width.coerceAtLeast(1)
                val h = size.height.coerceAtLeast(1)
                val img = BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB)
                val g = img.createGraphics()
                g.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON)
                val cx = w / 2; val cy = h / 2; val r = minOf(cx, cy) - 2
                g.color = java.awt.Color(220, 60, 60)
                g.stroke = java.awt.BasicStroke(2.5f)
                g.drawOval(cx - r, cy - r, r * 2, r * 2)
                val dx = (r * 0.707f).toInt()
                g.drawLine(cx - dx, cy - dx, cx + dx, cy + dx)
                g.dispose()
                tk.createCustomCursor(img, Point(cx, cy), "NotAllowed")
            } catch (_: Exception) { Cursor(Cursor.CROSSHAIR_CURSOR) }
        }
    }

    var currentCursorObj by remember { mutableStateOf(defaultCursor) }

    Box(modifier = modifier.fillMaxSize()) {
        // Context menu area for right-click "Edit in Composer" (spec 10 FR-4)
        ContextMenuArea(items = {
            if (composerOpen) return@ContextMenuArea emptyList()
            if (onOpenComposer == null) return@ContextMenuArea emptyList()
            val clickedElement = state.findElementAt(lastMousePosition.x, lastMousePosition.y)
            if (clickedElement == null || clickedElement == state.rootElement.value) {
                return@ContextMenuArea emptyList()
            }
            if (!state.isSelected(clickedElement)) {
                state.selectElement(clickedElement)
            }
            listOf(ContextMenuItem("Edit in Composer") { onOpenComposer.invoke(clickedElement) })
        }) {
        // Use Spacer with drawWithCache for more efficient pan/zoom updates
        // All keyboard handling (hotkeys, Space/Shift tracking, Enter, Escape) is done
        // at the editor level via onPreviewKeyEvent in HyveUIEditorContent. The canvas
        // Spacer no longer needs to be focusable for keyboard input — only for text
        // editing focus return via focusRequester.
        Spacer(
        modifier = Modifier
            .fillMaxSize()
            .focusRequester(focusRequester)
            .focusable()
            .pointerInput(spacePressed, composerOpen) {
                // Track mouse position for cursor changes
                var lastCursorUpdateTime = 0L
                val cursorUpdateThrottle = 16L // ~60fps max for cursor updates

                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Main)
                        if (event.type == PointerEventType.Move || event.type == PointerEventType.Enter) {
                            // Throttle cursor updates to reduce hit-testing overhead
                            val now = System.currentTimeMillis()
                            if (now - lastCursorUpdateTime < cursorUpdateThrottle) continue
                            lastCursorUpdateTime = now

                            val position = event.changes.firstOrNull()?.position ?: continue
                            lastMousePosition = position

                            // When composer is open, always show default cursor
                            if (composerOpen) {
                                currentCursorObj = defaultCursor
                                continue
                            }

                            // Determine what's under the cursor
                            val newCursor = when {
                                spacePressed -> moveCursor
                                else -> {
                                    // Only check resize handles if we have a selection (cheap check first)
                                    val hasSelection = state.selectedElements.value.isNotEmpty()
                                    val handleHit = if (hasSelection) {
                                        state.findResizeHandleAt(position.x, position.y)
                                    } else null

                                    if (handleHit != null) {
                                        // Set cursor based on handle position
                                        when (handleHit.second) {
                                            CanvasState.ResizeHandle.TOP_LEFT,
                                            CanvasState.ResizeHandle.BOTTOM_RIGHT -> nwResizeCursor
                                            CanvasState.ResizeHandle.TOP_RIGHT,
                                            CanvasState.ResizeHandle.BOTTOM_LEFT -> neResizeCursor
                                            CanvasState.ResizeHandle.TOP_CENTER,
                                            CanvasState.ResizeHandle.BOTTOM_CENTER -> nResizeCursor
                                            CanvasState.ResizeHandle.LEFT_CENTER,
                                            CanvasState.ResizeHandle.RIGHT_CENTER -> eResizeCursor
                                        }
                                    } else if (hasSelection) {
                                        // Only do element hit test if we have selection (for move cursor)
                                        // Note: findElementAt never returns root (root is not canvas-selectable)
                                        val elementHit = state.findElementAt(position.x, position.y)
                                        // Resolve to selected ancestor if the hit element is inside a selected group
                                        val cursorAncestor = if (elementHit != null && !state.isSelected(elementHit))
                                            state.findSelectedAncestor(elementHit) else null
                                        val cursorTarget = cursorAncestor ?: elementHit
                                        if (cursorTarget != null && state.isSelected(cursorTarget)) {
                                            val (canH, canV) = state.getDragAxes(cursorTarget)
                                            when {
                                                !canH && !canV -> notAllowedCursor
                                                else -> moveCursor
                                            }
                                        } else if (elementHit != null && state.isLayoutManaged(elementHit)) {
                                            // Layout-managed child: check if its draggable ancestor is actually movable
                                            val ancestor = state.findDraggableAncestor(elementHit)
                                            if (ancestor != null) {
                                                val (ancestorH, ancestorV) = state.getDragAxes(ancestor)
                                                if (ancestorH || ancestorV) moveCursor else defaultCursor
                                            } else defaultCursor
                                        } else {
                                            defaultCursor
                                        }
                                    } else {
                                        defaultCursor
                                    }
                                }
                            }
                            currentCursorObj = newCursor
                        } else if (event.type == PointerEventType.Exit) {
                            currentCursorObj = defaultCursor
                        }
                    }
                }
            }
            .pointerHoverIcon(PointerIcon(currentCursorObj))
            .onPointerEvent(PointerEventType.Scroll) { event ->
                // Handle mouse wheel for zoom
                val scrollDelta = event.changes.first().scrollDelta.y
                if (scrollDelta != 0f) {
                    val zoomDelta = -scrollDelta * CanvasState.ZOOM_STEP
                    state.setZoom(state.zoom.value + zoomDelta)
                    event.changes.forEach { it.consume() }
                }
            }
            .pointerInput(textEditingElementForOverlay, composerOpen) {
                // Combined gesture handler - handles both selection and drag/resize
                awaitPointerEventScope {
                    while (true) {
                        // Wait for pointer down event to check which button was pressed
                        val downEvent = awaitPointerEvent(PointerEventPass.Main)
                        val down = downEvent.changes.firstOrNull { it.changedToDown() } ?: continue
                        val startPosition = down.position

                        // Ensure the canvas has focus so keyboard events reach the
                        // editor's onPreviewKeyEvent handler. Without this, clicking
                        // the canvas wouldn't route key events to the focus tree.
                        focusRequester.requestFocus()

                        // When composer is open, block all canvas interactions
                        // (selection, drag, resize, context menu) — only zoom/pan remain active
                        if (composerOpen) {
                            continue
                        }

                        // Skip pointer processing while text editing (let the text field handle it)
                        if (state.isTextEditing()) {
                            continue
                        }

                        // Skip right-click — handled by ContextMenuArea (spec 10 FR-4)
                        if (downEvent.button == PointerButton.Secondary) {
                            continue
                        }

                        // Check if middle mouse button was pressed (for panning)
                        val isMiddleMouseDown = downEvent.button == PointerButton.Tertiary
                        val shouldPan = spacePressed || isMiddleMouseDown

                        // Determine what we clicked on
                        val handleHit = if (!shouldPan) state.findResizeHandleAt(startPosition.x, startPosition.y) else null
                        val clickedElement = if (!shouldPan && handleHit == null) state.findElementAt(startPosition.x, startPosition.y) else null

                        // If clicked element is a child of an already-selected element,
                        // use the selected ancestor for dragging (but tap will drill down).
                        val selectedAncestor = if (clickedElement != null && !state.isSelected(clickedElement))
                            state.findSelectedAncestor(clickedElement) else null
                        val effectiveElement = selectedAncestor ?: clickedElement

                        // Handle selection on press (before drag starts).
                        // If we have a selected ancestor, don't change selection on press —
                        // we'll either drag the ancestor, or drill down on tap.
                        if (!shouldPan && handleHit == null && clickedElement != null && selectedAncestor == null) {
                            if (!state.isSelected(clickedElement)) {
                                if (!shiftPressed) {
                                    state.selectElement(clickedElement)
                                } else {
                                    state.addToSelection(clickedElement)
                                }
                            }
                        }

                        // Set up drag state
                        var isDragging = false
                        var currentDraggedElement: UIElement? = null
                        var currentResizeState: Pair<UIElement, CanvasState.ResizeHandle>? = null
                        var currentlyPanning = false

                        // Track for tap detection
                        var totalDragDistance = 0f
                        val tapThreshold = 5f // pixels

                        // Track accumulated drag for undo
                        var accumulatedDrag = Offset.Zero
                        var dragStartElement: UIElement? = null
                        var dragStartResizeHandle: CanvasState.ResizeHandle? = null

                        // Track axis constraint for layout-managed elements
                        var dragAxisConstraint: String? = null // "horizOnly" or "vertOnly"

                        // Process drag
                        drag(down.id) { change ->
                            val dragAmount = change.positionChange()
                            totalDragDistance += kotlin.math.abs(dragAmount.x) + kotlin.math.abs(dragAmount.y)

                            // Initialize drag state on first significant movement
                            if (!isDragging && totalDragDistance > tapThreshold) {
                                isDragging = true
                                // Note: effectiveElement is never root (findElementAt skips root)
                                if (shouldPan) {
                                    currentlyPanning = true
                                } else if (handleHit != null) {
                                    currentResizeState = handleHit
                                    dragStartElement = handleHit.first
                                    dragStartResizeHandle = handleHit.second
                                } else if (effectiveElement != null && state.isSelected(effectiveElement)) {
                                    // Determine drag axis constraint from anchor/layout
                                    val (canH, canV) = state.getDragAxes(effectiveElement)
                                    if (canH || canV) {
                                        dragAxisConstraint = when {
                                            canH && canV -> null  // free movement
                                            canH -> "horizOnly"
                                            canV -> "vertOnly"
                                            else -> null
                                        }
                                        currentDraggedElement = effectiveElement
                                        dragStartElement = effectiveElement
                                    }
                                } else if (effectiveElement != null && state.isLayoutManaged(effectiveElement)) {
                                    // Unselected layout-managed element: bubble up to draggable ancestor
                                    val ancestor = state.findDraggableAncestor(effectiveElement)
                                    if (ancestor != null) {
                                        state.selectElement(ancestor)
                                        val (canH, canV) = state.getDragAxes(ancestor)
                                        dragAxisConstraint = when {
                                            canH && canV -> null
                                            canH -> "horizOnly"
                                            canV -> "vertOnly"
                                            else -> null
                                        }
                                        currentDraggedElement = ancestor
                                        dragStartElement = ancestor
                                    }
                                }
                                accumulatedDrag = Offset.Zero
                            }

                            // Apply drag
                            if (isDragging) {
                                val resizeInfo = currentResizeState
                                var scaledDrag = Offset(
                                    dragAmount.x / state.zoom.value,
                                    dragAmount.y / state.zoom.value
                                )
                                // Constrain drag to allowed axes
                                if (dragAxisConstraint != null) {
                                    scaledDrag = when (dragAxisConstraint) {
                                        "horizOnly" -> Offset(scaledDrag.x, 0f)
                                        "vertOnly" -> Offset(0f, scaledDrag.y)
                                        else -> scaledDrag
                                    }
                                }
                                accumulatedDrag += scaledDrag

                                when {
                                    currentlyPanning -> {
                                        state.pan(dragAmount)
                                    }
                                    resizeInfo != null -> {
                                        // Disable undo during drag for performance
                                        val newElement = state.resizeSelectedElement(
                                            resizeInfo.first,
                                            resizeInfo.second,
                                            scaledDrag,
                                            recordUndo = false
                                        )
                                        if (newElement != null) {
                                            currentResizeState = newElement to resizeInfo.second
                                        }
                                    }
                                    currentDraggedElement != null -> {
                                        // Disable undo during drag for performance
                                        state.moveSelectedElements(scaledDrag, recordUndo = false)
                                    }
                                }
                                change.consume()
                            }
                        }

                        // After drag ends, commit changes and record undo
                        if (isDragging && !currentlyPanning && accumulatedDrag != Offset.Zero) {
                            if (currentDraggedElement != null) {
                                // For move: commit the drag (applies offset to tree) then record undo
                                state.commitDrag()
                                state.recordMoveUndo(accumulatedDrag)
                            } else {
                                // For resize: commit the resize (applies to tree) then record undo
                                val element = dragStartElement
                                val handle = dragStartResizeHandle
                                if (element != null && handle != null) {
                                    state.commitResize()
                                    state.recordResizeUndo(element, handle, accumulatedDrag)
                                }
                            }
                        } else if (isDragging && !currentlyPanning) {
                            // Drag ended but no movement - just cancel
                            state.cancelDrag()
                            state.cancelResize()
                        }

                        // Handle tap (click without significant drag)
                        if (!isDragging && !shouldPan) {
                            val currentTime = System.currentTimeMillis()

                            if (clickedElement == null) {
                                // Clicked empty space - clear selection and reset double-click tracking
                                if (!shiftPressed) {
                                    state.clearSelection()
                                }
                                lastClickElementId = null
                            } else {
                                // Tap drills down: if we deferred selection because a
                                // selected ancestor existed, now select the child.
                                if (selectedAncestor != null) {
                                    state.selectElement(clickedElement)
                                }

                                // Check for double-click on same element (compare by ID)
                                val clickedId = clickedElement.id
                                val timeSinceLastClick = currentTime - lastClickTime
                                val isSameElement = clickedId != null && clickedId == lastClickElementId
                                val isDoubleClick = isSameElement && timeSinceLastClick < doubleClickThreshold

                                if (isDoubleClick && state.hasEditableText(clickedElement)) {
                                    // Double-click on text-editable element - start text editing
                                    state.startTextEditing(clickedElement)
                                    lastClickElementId = null
                                } else if (shiftPressed && state.isSelected(clickedElement)) {
                                    // Shift+click on already selected - toggle off
                                    state.removeFromSelection(clickedElement)
                                }

                                // Update tracking for next potential double-click
                                lastClickTime = currentTime
                                lastClickElementId = clickedId
                            }
                        }
                    }
                }
            }
            .drawWithCache {
                // Read state INSIDE drawWithCache to invalidate cache when they change
                // drawWithCache only re-executes when State objects read here change
                val currentDragOffset = state.dragOffset.value
                val currentResizeDelta = state.resizeDragDelta.value
                val currentIsDragging = state.isDragging.value
                val currentIsResizing = state.isResizing.value
                // Tree version: invalidates on any property/structure change
                val currentTreeVersion = state.treeVersion.value
                // Texture load counter: invalidates when async textures finish loading
                val currentTextureVersion = textureLoadCounter

                onDrawBehind {
                    // Draw the canvas using the painter
                    with(painter) {
                        drawCanvas(
                            state = state,
                            showGrid = showGrid,
                            darkCanvas = darkCanvas,
                            showScreenshot = showScreenshot,
                            screenshotOpacity = screenshotOpacity,
                            screenshotMode = screenshotMode
                        )
                    }
                }
            }
        )
        } // end ContextMenuArea

        // Top-right toolbar: Day/Night toggle + Hotkey reference
        val toggleBorderColor = if (darkCanvas) Color(0xFF3E3E58) else Color(0xFFCCCCCC)
        Row(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(HyveSpacing.sm),
            horizontalArrangement = Arrangement.spacedBy(HyveSpacing.sm)
        ) {
            // Hotkey reference "?" button
            if (onToggleHotkeyReference != null) {
                val hotkeyInteraction = remember { MutableInteractionSource() }
                val hotkeyHovered by hotkeyInteraction.collectIsHoveredAsState()
                val hotkeyBg = when {
                    darkCanvas && hotkeyHovered -> Color(0xFF363650)
                    darkCanvas -> Color(0xFF2A2A3E)
                    hotkeyHovered -> Color(0xFFE8E8E8)
                    else -> Color.White.copy(alpha = 0.85f)
                }
                val hotkeyFg = if (darkCanvas) Color(0xFFCCCCDD) else Color(0xFF555555)
                TooltipArea(
                    tooltip = {
                        Box(
                            modifier = Modifier
                                .background(
                                    color = if (darkCanvas) Color(0xFF2A2A3E) else Color(0xFFF5F5F5),
                                    shape = HyveShapes.card
                                )
                                .border(1.dp, toggleBorderColor, HyveShapes.card)
                                .padding(horizontal = HyveSpacing.sm, vertical = HyveSpacing.xs)
                        ) {
                            Text(
                                text = "Keyboard shortcuts (Ctrl+/)",
                                color = hotkeyFg,
                                style = TextStyle(fontSize = 11.sp)
                            )
                        }
                    },
                    delayMillis = 400
                ) {
                    Box(
                        modifier = Modifier
                            .hoverable(hotkeyInteraction)
                            .background(color = hotkeyBg, shape = HyveShapes.card)
                            .border(1.dp, toggleBorderColor, HyveShapes.card)
                            .pointerInput(Unit) {
                                awaitPointerEventScope {
                                    while (true) {
                                        val event = awaitPointerEvent(PointerEventPass.Main)
                                        if (event.type == PointerEventType.Press) {
                                            onToggleHotkeyReference()
                                            event.changes.forEach { it.consume() }
                                        }
                                    }
                                }
                            }
                            .padding(horizontal = HyveSpacing.sm, vertical = HyveSpacing.xs),
                        contentAlignment = Alignment.Center
                    ) {
                        // Match 14dp height of the Sun/Moon icon in the day/night toggle
                        Box(
                            modifier = Modifier.size(14.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "?",
                                color = hotkeyFg,
                                style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            )
                        }
                    }
                }
            }

            // Day/Night canvas mode toggle
            val toggleInteraction = remember { MutableInteractionSource() }
            val toggleHovered by toggleInteraction.collectIsHoveredAsState()
            val toggleBg = when {
                darkCanvas && toggleHovered -> Color(0xFF363650)
                darkCanvas -> Color(0xFF2A2A3E)
                toggleHovered -> Color(0xFFE8E8E8)
                else -> Color.White.copy(alpha = 0.85f)
            }
            val toggleFg = if (darkCanvas) Color(0xFFCCCCDD) else Color(0xFF555555)
            Box(
                modifier = Modifier
                    .hoverable(toggleInteraction)
                    .background(color = toggleBg, shape = HyveShapes.card)
                    .border(1.dp, toggleBorderColor, HyveShapes.card)
                    .pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent(PointerEventPass.Main)
                                if (event.type == PointerEventType.Press) {
                                    state.toggleDarkCanvas()
                                    event.changes.forEach { it.consume() }
                                }
                            }
                        }
                    }
                    .padding(horizontal = HyveSpacing.sm, vertical = HyveSpacing.xs),
                contentAlignment = Alignment.Center
            ) {
                // Sun/Moon icon drawn on a small canvas
                Canvas(modifier = Modifier.size(14.dp)) {
                    if (darkCanvas) {
                        // Moon: crescent shape
                        drawCircle(color = toggleFg, radius = size.minDimension / 2.4f)
                        drawCircle(
                            color = toggleBg,
                            radius = size.minDimension / 3f,
                            center = Offset(size.width * 0.6f, size.height * 0.35f)
                        )
                    } else {
                        // Sun: circle with rays
                        val center = Offset(size.width / 2, size.height / 2)
                        val coreRadius = size.minDimension / 4f
                        drawCircle(color = toggleFg, radius = coreRadius, center = center)
                        val rayLength = size.minDimension / 2.4f
                        for (i in 0 until 8) {
                            val angle = Math.toRadians(i * 45.0)
                            val cos = kotlin.math.cos(angle).toFloat()
                            val sin = kotlin.math.sin(angle).toFloat()
                            drawLine(
                                color = toggleFg,
                                start = Offset(center.x + cos * coreRadius * 1.4f, center.y + sin * coreRadius * 1.4f),
                                end = Offset(center.x + cos * rayLength, center.y + sin * rayLength),
                                strokeWidth = 1.2f
                            )
                        }
                    }
                }
            }
        }

        // Scale indicator and zoom level in bottom-right corner
        val colors = HyveThemeColors.colors
        val zoomPercent = (state.zoom.value * 100).toInt()
        val scaleScreenPx = CanvasPainter.GRID_SIZE * state.zoom.value * CanvasPainter.GRID_MAJOR_INTERVAL
        val scaleValue = (CanvasPainter.GRID_SIZE * CanvasPainter.GRID_MAJOR_INTERVAL).toInt()
        val density = LocalDensity.current

        Row(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(HyveSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(HyveSpacing.sm)
        ) {
            // Scale reference indicator
            val scaleWidthDp = with(density) { scaleScreenPx.toDp() }
            val scaleColor = Color(0xFF999999)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(HyveSpacing.xs)
            ) {
                Canvas(modifier = Modifier.size(width = scaleWidthDp, height = 12.dp)) {
                    val y = size.height / 2
                    drawLine(scaleColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 2f)
                    drawLine(scaleColor, Offset(0f, 0f), Offset(0f, size.height), strokeWidth = 2f)
                    drawLine(scaleColor, Offset(size.width, 0f), Offset(size.width, size.height), strokeWidth = 2f)
                }
                Text(
                    text = "${scaleValue}px",
                    color = scaleColor,
                    style = HyveTypography.caption
                )
            }

            // Zoom percentage
            Box(
                modifier = Modifier
                    .background(
                        color = colors.deepNight.copy(alpha = 0.6f),
                        shape = HyveShapes.card
                    )
                    .padding(horizontal = HyveSpacing.sm, vertical = HyveSpacing.xs)
            ) {
                Text(
                    text = "${zoomPercent}%",
                    color = Color.White
                )
            }
        }

        // Inline text editing overlay - positioned exactly over the element
        val textEditingElement = textEditingElementForOverlay
        if (textEditingElement != null) {
            val overlayZoom = state.zoom.value
            val overlayPanOffset = state.panOffset.value

            // Find bounds by ID since element references change
            val elementBounds = state.calculatedLayout.value.entries.find {
                it.key.id == textEditingElement.id
            }?.value
            if (elementBounds != null) {
                // Convert element bounds to screen coordinates
                val screenX = (elementBounds.x * overlayZoom + overlayPanOffset.x).dp
                val screenY = (elementBounds.y * overlayZoom + overlayPanOffset.y).dp
                val screenWidth = (elementBounds.width * overlayZoom).dp
                val screenHeight = (elementBounds.height * overlayZoom).dp

                // Determine text style and alignment based on element type
                val elementType = textEditingElement.type.value
                val isButton = elementType == "Button"
                val isLabel = elementType == "Label"
                val isTextField = elementType == "TextField" || elementType == "MultilineTextField"

                // Text color based on element type
                val textColor = when {
                    isButton -> Color.White
                    else -> Color.Black
                }

                // Font size scaled by zoom
                val fontSize = (14 * overlayZoom).sp

                // Alignment based on element type
                val textAlign = when {
                    isButton -> androidx.compose.ui.text.style.TextAlign.Center
                    else -> androidx.compose.ui.text.style.TextAlign.Start
                }

                val contentAlignment = when {
                    isButton -> Alignment.Center
                    else -> Alignment.CenterStart
                }

                // Padding for text fields
                val horizontalPadding = if (isTextField) (4 * overlayZoom).dp else 0.dp

                Box(
                    modifier = Modifier
                        .offset(x = screenX, y = screenY)
                        .size(width = screenWidth, height = screenHeight),
                    contentAlignment = contentAlignment
                ) {
                    BasicTextField(
                        value = editingText,
                        onValueChange = { editingText = it },
                        modifier = Modifier
                            .then(
                                if (isButton) Modifier.fillMaxWidth()
                                else Modifier.fillMaxSize()
                            )
                            .padding(horizontal = horizontalPadding)
                            .focusRequester(textEditFocusRequester)
                            .onFocusChanged { focusState ->
                                // Update global focus state for hotkey suppression
                                TextInputFocusState.setFocused(focusState.isFocused)

                                if (focusState.isFocused) {
                                    // Mark that we've gained focus
                                    textFieldHadFocus = true
                                } else if (textFieldHadFocus && state.isTextEditing()) {
                                    // Commit when focus is lost (clicking elsewhere)
                                    state.commitTextEditing(editingText.text)
                                }
                            }
                            .onPreviewKeyEvent { event ->
                                when {
                                    // Commit on Enter
                                    event.key == Key.Enter && event.type == KeyEventType.KeyDown -> {
                                        state.commitTextEditing(editingText.text)
                                        focusRequester.requestFocus()
                                        true
                                    }
                                    // Cancel on Escape
                                    event.key == Key.Escape && event.type == KeyEventType.KeyDown -> {
                                        state.cancelTextEditing()
                                        focusRequester.requestFocus()
                                        true
                                    }
                                    else -> false
                                }
                            },
                        textStyle = TextStyle(
                            color = textColor,
                            fontSize = fontSize,
                            textAlign = textAlign
                        ),
                        cursorBrush = SolidColor(if (isButton) Color.White else JewelTheme.globalColors.outlines.focused),
                        singleLine = elementType != "MultilineTextField"
                    )
                }
            }
        }
    }
}

/**
 * Simple preview/demo composable showing the canvas with a sample UI element
 */
@Composable
fun CanvasViewDemo(modifier: Modifier = Modifier) {
    val state = rememberCanvasState()

    // Create a sample UI element tree for demo
    LaunchedEffect(Unit) {
        val sampleElement = createSampleElement()
        state.setRootElement(sampleElement)
    }

    CanvasView(state = state, modifier = modifier)
}

/**
 * Create a sample UI element tree for testing
 */
private fun createSampleElement(): UIElement {
    val buttonAnchor = com.hyve.ui.core.domain.anchor.AnchorValue(
        left = com.hyve.ui.core.domain.anchor.AnchorDimension.Absolute(100f),
        top = com.hyve.ui.core.domain.anchor.AnchorDimension.Absolute(100f),
        width = com.hyve.ui.core.domain.anchor.AnchorDimension.Absolute(200f),
        height = com.hyve.ui.core.domain.anchor.AnchorDimension.Absolute(50f)
    )

    val labelAnchor = com.hyve.ui.core.domain.anchor.AnchorValue(
        left = com.hyve.ui.core.domain.anchor.AnchorDimension.Absolute(100f),
        top = com.hyve.ui.core.domain.anchor.AnchorDimension.Absolute(200f),
        width = com.hyve.ui.core.domain.anchor.AnchorDimension.Absolute(200f),
        height = com.hyve.ui.core.domain.anchor.AnchorDimension.Absolute(30f)
    )

    return UIElement(
        type = com.hyve.ui.core.id.ElementType("Group"),
        id = com.hyve.ui.core.id.ElementId("Root"),
        properties = com.hyve.ui.core.domain.properties.PropertyMap.of(
            "Background" to com.hyve.ui.core.domain.properties.PropertyValue.Color("#ffffff", 1.0f)
        ),
        children = listOf(
            UIElement(
                type = com.hyve.ui.core.id.ElementType("Button"),
                id = com.hyve.ui.core.id.ElementId("SampleButton"),
                properties = com.hyve.ui.core.domain.properties.PropertyMap.of(
                    "Text" to com.hyve.ui.core.domain.properties.PropertyValue.Text("Click Me"),
                    "Anchor" to com.hyve.ui.core.domain.properties.PropertyValue.Anchor(buttonAnchor)
                )
            ),
            UIElement(
                type = com.hyve.ui.core.id.ElementType("Label"),
                id = com.hyve.ui.core.id.ElementId("SampleLabel"),
                properties = com.hyve.ui.core.domain.properties.PropertyMap.of(
                    "Text" to com.hyve.ui.core.domain.properties.PropertyValue.Text("Hello, HyveUI!"),
                    "Anchor" to com.hyve.ui.core.domain.properties.PropertyValue.Anchor(labelAnchor)
                )
            )
        )
    )
}
