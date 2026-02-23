// Copyright 2026 Hyve. All rights reserved.
package com.hyve.ui.canvas

import androidx.compose.runtime.*
import androidx.compose.ui.geometry.Offset
import com.hyve.ui.core.domain.elements.ElementMetadata
import com.hyve.ui.core.domain.elements.UIElement
import com.hyve.ui.core.domain.anchor.AnchorValue
import com.hyve.ui.state.EditDeltaTracker
import com.hyve.ui.core.domain.properties.PropertyValue
import com.hyve.ui.core.id.ElementId
import com.hyve.ui.core.id.ElementType
import com.hyve.ui.core.id.PropertyName
import com.hyve.ui.registry.ElementCapability
import com.hyve.ui.registry.ElementTypeRegistry
import com.hyve.ui.rendering.layout.LayoutEngine
import com.hyve.ui.rendering.layout.Rect
import com.hyve.ui.rendering.layout.ElementBounds
import com.hyve.ui.rendering.painter.resolveStyleToTuple
import com.hyve.ui.rendering.painter.get
import com.hyve.common.undo.UndoManager
import com.hyve.ui.state.command.*
import com.hyve.ui.schema.SchemaRegistry
import com.hyve.ui.schema.ElementSchema
import com.hyve.ui.schema.RuntimeSchemaRegistry
import java.io.File

/**
 * State management for the canvas view.
 *
 * Handles:
 * - UI element tree for rendering
 * - Zoom level (0.1 to 5.0)
 * - Pan offset (camera position)
 * - Selected elements
 * - Grid visibility
 * - Layout calculation caching
 * - Undo/redo command history
 *
 * This is the single source of truth for canvas state,
 * following Compose best practices for state management.
 */
class CanvasState(
    private val layoutEngine: LayoutEngine = LayoutEngine(SchemaRegistry.default()),
    initialZoom: Float = 1.0f,
    initialPan: Offset = Offset.Zero,
    val undoManager: UndoManager<UIElement> = UndoManager(),
    private val onStatusChange: ((String) -> Unit)? = null
) {
    /** Manages the set of selected elements. */
    val selection = SelectionManager()

    /** Creates new elements with unique IDs and default properties. */
    val elementFactory = ElementFactory()
    companion object {
        const val MIN_ZOOM = 0.3f  // Don't allow zooming out too far
        const val MAX_ZOOM = 5.0f
        const val ZOOM_STEP = 0.1f

        // Resize handle configuration
        const val HANDLE_SIZE = 8f
        const val HANDLE_HIT_PADDING = 4f // Extra padding for easier clicking

        /**
         * Hytale UI reference viewport (1920x1080)
         * This is the "safe zone" where UI will be visible in-game
         */
        const val VIEWPORT_WIDTH = 1920f
        const val VIEWPORT_HEIGHT = 1080f

        /**
         * Canvas bounds - 1 screen of padding on each side
         * Canvas ranges from (-1920, -1080) to (3840, 2160)
         * The viewport (0,0)-(1920,1080) is centered in this space
         */
        const val CANVAS_MIN_X = -VIEWPORT_WIDTH
        const val CANVAS_MIN_Y = -VIEWPORT_HEIGHT
        const val CANVAS_MAX_X = VIEWPORT_WIDTH * 2
        const val CANVAS_MAX_Y = VIEWPORT_HEIGHT * 2
        const val CANVAS_WIDTH = CANVAS_MAX_X - CANVAS_MIN_X  // 5760
        const val CANVAS_HEIGHT = CANVAS_MAX_Y - CANVAS_MIN_Y // 3240

        /**
         * Default canvas viewport size (matches Hytale's 1920x1080)
         */
        val DEFAULT_VIEWPORT = Rect.screen()
    }

    /**
     * Represents a resize handle position
     */
    enum class ResizeHandle {
        TOP_LEFT, TOP_CENTER, TOP_RIGHT,
        LEFT_CENTER, RIGHT_CENTER,
        BOTTOM_LEFT, BOTTOM_CENTER, BOTTOM_RIGHT;

        /**
         * Check if this handle affects width
         */
        fun affectsWidth(): Boolean = this in setOf(
            TOP_LEFT, TOP_RIGHT, LEFT_CENTER, RIGHT_CENTER, BOTTOM_LEFT, BOTTOM_RIGHT
        )

        /**
         * Check if this handle affects height
         */
        fun affectsHeight(): Boolean = this in setOf(
            TOP_LEFT, TOP_CENTER, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_CENTER, BOTTOM_RIGHT
        )

        /**
         * Check if this handle affects left edge
         */
        fun affectsLeft(): Boolean = this in setOf(TOP_LEFT, LEFT_CENTER, BOTTOM_LEFT)

        /**
         * Check if this handle affects right edge
         */
        fun affectsRight(): Boolean = this in setOf(TOP_RIGHT, RIGHT_CENTER, BOTTOM_RIGHT)

        /**
         * Check if this handle affects top edge
         */
        fun affectsTop(): Boolean = this in setOf(TOP_LEFT, TOP_CENTER, TOP_RIGHT)

        /**
         * Check if this handle affects bottom edge
         */
        fun affectsBottom(): Boolean = this in setOf(BOTTOM_LEFT, BOTTOM_CENTER, BOTTOM_RIGHT)
    }

    // --- UI Element Tree ---

    private val _rootElement = mutableStateOf<UIElement?>(null)
    val rootElement: State<UIElement?> = _rootElement

    /** Incremented every time the tree is structurally modified (drag, resize, add, delete, etc.) */
    private val _treeVersion = mutableStateOf(0L)
    val treeVersion: State<Long> = _treeVersion

    /**
     * Set the root UI element to render on the canvas.
     * Bumps [treeVersion] to invalidate the drawWithCache render cache.
     */
    fun setRootElement(element: UIElement?) {
        _rootElement.value = element
        _treeVersion.value++
        invalidateLayout()
    }

    /**
     * Update the root element as the result of a user edit and bump [treeVersion].
     */
    private fun commitTreeEdit(newRoot: UIElement) {
        _rootElement.value = newRoot
        _treeVersion.value++
    }

    /**
     * Report a status message to the UI.
     * Use this for operations that don't go through executeCommand.
     */
    fun reportStatus(message: String) {
        onStatusChange?.invoke(message)
    }

    // --- Source File Path (for relative asset resolution) ---

    private val _sourceFilePath = mutableStateOf<File?>(null)
    val sourceFilePath: State<File?> = _sourceFilePath

    /**
     * Set the source file path for the current document.
     * This is used to resolve relative texture paths in .ui files.
     */
    fun setSourceFilePath(file: File?) {
        _sourceFilePath.value = file
    }

    /**
     * Get the base directory for resolving relative paths.
     * Returns the parent directory of the source file, or null if no source file is set.
     */
    val sourceBaseDir: File?
        get() = _sourceFilePath.value?.parentFile

    // --- Edit Delta Tracker (for dual-document export) ---

    private var _editDeltaTracker: EditDeltaTracker? = null

    /**
     * Set the edit delta tracker for recording user edits.
     */
    fun setEditDeltaTracker(tracker: EditDeltaTracker) {
        _editDeltaTracker = tracker
    }

    // --- Runtime Schema Registry (for schema-driven element defaults) ---

    private var _runtimeRegistry: RuntimeSchemaRegistry? = null

    /**
     * Set the runtime schema registry for schema-driven element creation.
     * When set, newly created elements will get default properties based on
     * discovered schema data from the game files.
     */
    fun setRuntimeRegistry(registry: RuntimeSchemaRegistry?) {
        _runtimeRegistry = registry
    }

    // --- Zoom and Pan ---

    private val _zoom = mutableStateOf(initialZoom.coerceIn(MIN_ZOOM, MAX_ZOOM))
    val zoom: State<Float> = _zoom

    private val _panOffset = mutableStateOf(initialPan)
    val panOffset: State<Offset> = _panOffset

    /**
     * Set zoom level (clamped between MIN_ZOOM and MAX_ZOOM)
     */
    fun setZoom(newZoom: Float) {
        _zoom.value = newZoom.coerceIn(MIN_ZOOM, MAX_ZOOM)
    }

    /**
     * Zoom in by ZOOM_STEP
     */
    fun zoomIn() {
        setZoom(_zoom.value + ZOOM_STEP)
    }

    /**
     * Zoom out by ZOOM_STEP
     */
    fun zoomOut() {
        setZoom(_zoom.value - ZOOM_STEP)
    }

    /**
     * Reset zoom to 1.0 (100%)
     */
    fun resetZoom() {
        _zoom.value = 1.0f
    }

    /**
     * Set pan offset (camera position), clamped to canvas bounds
     */
    fun setPanOffset(offset: Offset, screenWidth: Float = 1920f, screenHeight: Float = 1080f) {
        _panOffset.value = clampPanOffset(offset, screenWidth, screenHeight)
    }

    /**
     * Set zoom and pan without clamping. Used by the Composer preview panel
     * where the viewport is much smaller than the main canvas and needs
     * zoom values below [MIN_ZOOM] to fit content.
     */
    fun setViewportTransform(zoom: Float, pan: Offset) {
        _zoom.value = zoom
        _panOffset.value = pan
    }

    /**
     * Pan by delta offset, clamped to canvas bounds
     */
    fun pan(delta: Offset, screenWidth: Float = 1920f, screenHeight: Float = 1080f) {
        val newOffset = _panOffset.value + delta
        _panOffset.value = clampPanOffset(newOffset, screenWidth, screenHeight)
    }

    /**
     * Clamp pan offset to keep canvas bounds visible.
     * Ensures user can't pan so far that the entire canvas is off-screen.
     */
    private fun clampPanOffset(offset: Offset, screenWidth: Float, screenHeight: Float): Offset {
        val zoom = _zoom.value

        // Calculate pan limits to keep at least 20% of canvas visible on screen
        // This prevents the user from losing the canvas entirely

        // How much of the canvas (in screen pixels) must remain visible
        val minVisibleX = screenWidth * 0.2f
        val minVisibleY = screenHeight * 0.2f

        // Maximum pan (panning right/down): canvas left edge can go to (screen right - minVisible)
        val maxPanX = -CANVAS_MIN_X * zoom + screenWidth - minVisibleX
        val maxPanY = -CANVAS_MIN_Y * zoom + screenHeight - minVisibleY

        // Minimum pan (panning left/up): canvas right edge must stay at least minVisible from left
        val minPanX = -CANVAS_MAX_X * zoom + minVisibleX
        val minPanY = -CANVAS_MAX_Y * zoom + minVisibleY

        // When zoomed out far enough, the entire canvas fits on screen
        // In this case, min > max, so we need to handle this gracefully
        val clampedX = if (minPanX <= maxPanX) {
            offset.x.coerceIn(minPanX, maxPanX)
        } else {
            // Canvas fits entirely - center it or allow free movement
            offset.x.coerceIn(maxPanX, minPanX)
        }

        val clampedY = if (minPanY <= maxPanY) {
            offset.y.coerceIn(minPanY, maxPanY)
        } else {
            // Canvas fits entirely - center it or allow free movement
            offset.y.coerceIn(maxPanY, minPanY)
        }

        return Offset(clampedX, clampedY)
    }

    /**
     * Reset pan to origin
     */
    fun resetPan() {
        _panOffset.value = Offset.Zero
    }

    // --- Keyboard Modifier State ---
    // Tracked at the editor level (onPreviewKeyEvent) so they work regardless of focus

    private val _spacePressed = mutableStateOf(false)
    val spacePressed: State<Boolean> = _spacePressed

    private val _shiftPressed = mutableStateOf(false)
    val shiftPressed: State<Boolean> = _shiftPressed

    fun setSpacePressed(pressed: Boolean) {
        _spacePressed.value = pressed
    }

    fun setShiftPressed(pressed: Boolean) {
        _shiftPressed.value = pressed
    }

    private val _altPressed = mutableStateOf(false)
    val altPressed: State<Boolean> = _altPressed

    fun setAltPressed(pressed: Boolean) {
        _altPressed.value = pressed
    }

    // --- Grid ---

    private val _showGrid = mutableStateOf(true)
    val showGrid: State<Boolean> = _showGrid

    /**
     * Toggle grid visibility
     */
    fun toggleGrid() {
        _showGrid.value = !_showGrid.value
    }

    /**
     * Set grid visibility
     */
    fun setShowGrid(visible: Boolean) {
        _showGrid.value = visible
    }

    // --- Dark Canvas Mode ---

    private val _darkCanvas = mutableStateOf(false)
    val darkCanvas: State<Boolean> = _darkCanvas

    fun toggleDarkCanvas() {
        _darkCanvas.value = !_darkCanvas.value
    }

    fun setDarkCanvas(dark: Boolean) {
        _darkCanvas.value = dark
    }

    // --- Screenshot Reference Overlay ---

    private val _showScreenshot = mutableStateOf(false)
    val showScreenshot: State<Boolean> = _showScreenshot

    private val _screenshotOpacity = mutableStateOf(0.3f)
    val screenshotOpacity: State<Float> = _screenshotOpacity

    private val _screenshotMode = mutableStateOf(ScreenshotMode.NO_HUD)
    val screenshotMode: State<ScreenshotMode> = _screenshotMode

    /**
     * Toggle screenshot reference overlay visibility.
     * Hotkey: B
     */
    fun toggleScreenshot() {
        _showScreenshot.value = !_showScreenshot.value
    }

    fun setShowScreenshot(visible: Boolean) {
        _showScreenshot.value = visible
    }

    /**
     * Set screenshot overlay opacity (0.0 to 1.0).
     */
    fun setScreenshotOpacity(opacity: Float) {
        _screenshotOpacity.value = opacity.coerceIn(0f, 1f)
    }

    /**
     * Cycle to the next screenshot mode (HUD / NO_HUD).
     * Hotkey: Shift+B
     */
    fun cycleScreenshotMode() {
        _screenshotMode.value = _screenshotMode.value.next()
    }

    fun setScreenshotMode(mode: ScreenshotMode) {
        _screenshotMode.value = mode
    }

    // --- Snap Guides ---

    private val _snapGuidesEnabled = mutableStateOf(true)
    val snapGuidesEnabled: State<Boolean> = _snapGuidesEnabled

    private val _activeSnapGuides = mutableStateOf<List<SnapGuide>>(emptyList())
    val activeSnapGuides: State<List<SnapGuide>> = _activeSnapGuides

    /** Raw (un-snapped) drag offset — _dragOffset becomes the snapped value. */
    private val _rawDragOffset = mutableStateOf(Offset.Zero)

    /** Cached snap targets (computed once at drag start). */
    private var _snapTargetBounds: List<Rect> = emptyList()

    /**
     * Toggle snap alignment guides on/off.
     * Hotkey: S
     */
    fun toggleSnapGuides() {
        _snapGuidesEnabled.value = !_snapGuidesEnabled.value
        reportStatus(if (_snapGuidesEnabled.value) "Snap guides enabled" else "Snap guides disabled")
    }

    /**
     * Walk the tree once to collect bounds of snap-eligible elements.
     * Called at drag start to cache targets for the duration of the drag.
     * Excludes: root, selected elements, descendants of selected, hidden, locked.
     */
    fun prepareSnapTargets() {
        val root = _rootElement.value ?: return
        val selectedSet = selection.selectedElements.value
        val selectedIds = selectedSet.mapNotNull { it.id }.toSet()
        val descendantIds = selectedSet.flatMap { collectDescendantIds(it) }.toSet()

        val targets = mutableListOf<Rect>()
        root.visitDescendants { element ->
            val isRoot = element === root
            val isSelected = element in selectedSet ||
                    (element.id != null && element.id in selectedIds)
            val isDescendant = element.id?.value in descendantIds
            if (!isRoot && !isSelected && !isDescendant &&
                element.metadata.visible && !element.metadata.locked) {
                val bounds = _calculatedLayout.value[element]?.bounds
                if (bounds != null) {
                    targets.add(bounds)
                }
            }
        }
        _snapTargetBounds = targets
    }

    /**
     * Clear all snap-related transient state.
     */
    private fun clearSnapState() {
        _activeSnapGuides.value = emptyList()
        _rawDragOffset.value = Offset.Zero
        _snapTargetBounds = emptyList()
    }

    // --- Text Editing ---

    private val _textEditingElement = mutableStateOf<UIElement?>(null)
    val textEditingElement: State<UIElement?> = _textEditingElement

    /**
     * Check if an element supports inline text editing.
     */
    fun hasEditableText(element: UIElement): Boolean {
        val info = ElementTypeRegistry.getOrDefault(element.type.value)
        return ElementCapability.TEXT_EDITABLE in info.capabilities &&
                element.getProperty("Text") != null
    }

    /**
     * Start inline text editing for an element.
     * Returns the current text value if editing can start, null otherwise.
     */
    fun startTextEditing(element: UIElement): String? {
        if (!hasEditableText(element)) return null
        val currentText = (element.getProperty("Text") as? PropertyValue.Text)?.value ?: ""
        _textEditingElement.value = element
        return currentText
    }

    /**
     * Cancel text editing without saving changes.
     */
    fun cancelTextEditing() {
        _textEditingElement.value = null
    }

    /**
     * Commit text editing and update the element's text property.
     */
    fun commitTextEditing(newText: String) {
        val element = _textEditingElement.value ?: return
        _textEditingElement.value = null

        // Update the element's Text property
        updateElementProperty(element, "Text", PropertyValue.Text(newText))

        val name = element.id?.value ?: element.type.value
        reportStatus("Updated text on $name")
    }

    /**
     * Check if we're currently editing text.
     */
    fun isTextEditing(): Boolean = _textEditingElement.value != null

    // --- Undo/Redo ---

    /**
     * Execute a command with undo support.
     * @param command The command to execute
     * @param allowMerge Whether to allow merging with previous command
     * @return true if the command was executed successfully
     */
    fun executeCommand(command: DocumentCommand, allowMerge: Boolean = true): Boolean {
        val root = _rootElement.value ?: return false
        val newRoot = undoManager.execute(command, root, allowMerge)
        if (newRoot != null) {
            commitTreeEdit(newRoot)
            invalidateLayout()
            // Update selection to point to elements in new tree
            updateSelectionAfterTreeChange()
            // Record deltas for ReplaceElementCommand (Composer, ID changes)
            // Other command types record deltas at their call sites.
            if (command is ReplaceElementCommand) {
                recordDeltasForReplace(command)
            }
            // Report status
            onStatusChange?.invoke(command.description)
            return true
        }
        return false
    }

    /**
     * Diff old vs new element from a ReplaceElementCommand and record
     * individual property deltas so the export pipeline writes changes to file.
     */
    private fun recordDeltasForReplace(command: ReplaceElementCommand) {
        val tracker = _editDeltaTracker ?: return

        // Detect ID changes (rename) and record a RenameElement delta
        val oldId = command.oldElement.id
        val newId = command.newElement.id
        if (oldId != null && newId != null && oldId != newId) {
            tracker.record(EditDeltaTracker.EditDelta.RenameElement(oldId = oldId, newId = newId))
        }

        // Use the new element's ID for property deltas; fall back to old if new has none
        val elementId = newId ?: oldId ?: return

        val oldProps = command.oldElement.properties
        val newProps = command.newElement.properties

        // Record set/changed properties
        for ((name, value) in newProps.entries()) {
            val oldValue = oldProps[name]
            if (oldValue != value) {
                tracker.record(EditDeltaTracker.EditDelta.SetProperty(
                    elementId = elementId,
                    propertyName = name.value,
                    value = value
                ))
            }
        }

        // Record removed properties
        for (name in oldProps.keys()) {
            if (newProps[name] == null) {
                tracker.record(EditDeltaTracker.EditDelta.RemoveProperty(
                    elementId = elementId,
                    propertyName = name.value
                ))
            }
        }
    }

    /**
     * Undo the last command.
     * @return true if undo was successful
     */
    fun undo(): Boolean {
        val root = _rootElement.value ?: return false
        // Get description before undo (it will be moved to redo stack)
        val description = undoManager.undoDescription.value
        val newRoot = undoManager.undo(root)
        if (newRoot != null) {
            commitTreeEdit(newRoot)
            invalidateLayout()
            // Keep selection but update references to point to elements in new tree
            updateSelectionAfterTreeChange()
            // Report status
            description?.let { onStatusChange?.invoke("Undo: $it") }
            return true
        }
        return false
    }

    /**
     * Redo the last undone command.
     * @return true if redo was successful
     */
    fun redo(): Boolean {
        val root = _rootElement.value ?: return false
        // Get description before redo (it will be moved to undo stack)
        val description = undoManager.redoDescription.value
        val newRoot = undoManager.redo(root)
        if (newRoot != null) {
            commitTreeEdit(newRoot)
            invalidateLayout()
            // Keep selection but update references to point to elements in new tree
            updateSelectionAfterTreeChange()
            // Report status
            description?.let { onStatusChange?.invoke("Redo: $it") }
            return true
        }
        return false
    }

    private fun updateSelectionAfterTreeChange() {
        val root = _rootElement.value ?: return
        selection.updateAfterTreeChange(root)
    }

    // --- Selection (delegated to SelectionManager) ---

    val selectedElements: State<Set<UIElement>> get() = selection.selectedElements

    // --- Drag State (for performant dragging without tree recreation) ---

    /**
     * Offset applied to selected elements during active drag.
     * This avoids recreating the tree on every mouse movement.
     * Reset to Zero when drag ends and tree is committed.
     */
    private val _dragOffset = mutableStateOf(Offset.Zero)
    val dragOffset: State<Offset> = _dragOffset

    /**
     * Whether a drag operation is currently in progress.
     * When true, rendering applies dragOffset to selected elements.
     */
    private val _isDragging = mutableStateOf(false)
    val isDragging: State<Boolean> = _isDragging

    /**
     * Set of element IDs that are descendants of selected elements during drag.
     * Pre-computed once when drag starts or selection changes to avoid O(N^2) tree walks.
     */
    private val _dragDescendantIds: Set<String> by derivedStateOf {
        if (_isDragging.value) {
            selection.selectedElements.value.flatMap { collectDescendantIds(it) }.toSet()
        } else {
            emptySet()
        }
    }

    /**
     * Computed drag preview anchor for single-selected element during drag.
     * Returns null when not dragging or when multi-selecting.
     */
    val dragPreviewAnchor: State<AnchorValue?> = derivedStateOf {
        if (!_isDragging.value) return@derivedStateOf null
        val selected = selection.selectedElements.value
        if (selected.size != 1) return@derivedStateOf null

        val element = selected.first()
        val anchorProp = element.getProperty("Anchor") as? PropertyValue.Anchor
            ?: return@derivedStateOf null

        val parent = findParent(element)
        val parentBounds = parent?.let { getBounds(it)?.bounds }
        calculateMovedAnchor(anchorProp.anchor, _dragOffset.value, parentBounds)
    }

    // --- Resize Drag State (for performant resizing without tree recreation) ---

    /**
     * Accumulated resize delta during active resize drag.
     */
    private val _resizeDragDelta = mutableStateOf(Offset.Zero)
    val resizeDragDelta: State<Offset> = _resizeDragDelta

    /**
     * Whether a resize operation is currently in progress.
     */
    private val _isResizing = mutableStateOf(false)
    val isResizing: State<Boolean> = _isResizing

    /**
     * The resize handle being dragged (for calculating visual bounds).
     */
    private val _resizeHandle = mutableStateOf<ResizeHandle?>(null)
    val resizeHandle: State<ResizeHandle?> = _resizeHandle

    /**
     * The element being resized (for calculating visual bounds).
     */
    private val _resizeElement = mutableStateOf<UIElement?>(null)
    val resizeElement: State<UIElement?> = _resizeElement

    /**
     * Commit the current drag operation to the element tree.
     * Called when drag ends - applies the accumulated drag offset to the actual elements.
     * This is when tree recreation and layout recalculation happen (once, not per-frame).
     */
    fun commitDrag() {
        if (!_isDragging.value || _dragOffset.value == Offset.Zero) {
            cancelDrag()
            return
        }

        val totalDelta = _dragOffset.value

        // Apply the accumulated drag to the tree (this does the expensive tree recreation once)
        applyDragToTree(totalDelta)

        // Reset drag state
        _isDragging.value = false
        _dragOffset.value = Offset.Zero
        clearSnapState()
    }

    /**
     * Cancel the current drag operation without applying changes.
     */
    fun cancelDrag() {
        _isDragging.value = false
        _dragOffset.value = Offset.Zero
        clearSnapState()
    }

    /**
     * Commit the current resize operation to the element tree.
     * Called when resize drag ends - applies the accumulated resize delta.
     */
    fun commitResize(): UIElement? {
        if (!_isResizing.value || _resizeDragDelta.value == Offset.Zero) {
            cancelResize()
            return null
        }

        val element = _resizeElement.value ?: run { cancelResize(); return null }
        val handle = _resizeHandle.value ?: run { cancelResize(); return null }
        val totalDelta = _resizeDragDelta.value

        // Apply the accumulated resize to the tree
        val result = applyResizeToTree(element, handle, totalDelta)

        // Reset resize state
        cancelResize()

        return result
    }

    /**
     * Cancel the current resize operation without applying changes.
     */
    fun cancelResize() {
        _isResizing.value = false
        _resizeDragDelta.value = Offset.Zero
        _resizeHandle.value = null
        _resizeElement.value = null
    }

    /**
     * Apply resize to tree and recalculate layout (called once at end of resize drag).
     */
    private fun applyResizeToTree(element: UIElement, handle: ResizeHandle, delta: Offset): UIElement? {
        if (_rootElement.value == null) return null

        val anchorProp = element.getProperty("Anchor")
        if (anchorProp !is PropertyValue.Anchor) return null

        val newAnchor = calculateResizedAnchor(anchorProp.anchor, handle, delta)

        // Record delta for export (DL-002)
        element.id?.let { elementId ->
            _editDeltaTracker?.record(EditDeltaTracker.EditDelta.MoveElement(
                elementId = elementId,
                newAnchor = PropertyValue.Anchor(newAnchor)
            ))
        }

        var newElement: UIElement? = null

        val newRoot = _rootElement.value!!.mapDescendants { el ->
            if (el == element || el.id == element.id) {
                val updated = el.setProperty("Anchor", PropertyValue.Anchor(newAnchor))
                newElement = updated
                updated
            } else {
                el
            }
        }

        commitTreeEdit(newRoot)

        newElement?.let { updated ->
            selection.setSelection(
                selection.selectedElements.value
                    .filter { it != element && it.id != element.id }
                    .toSet() + updated
            )
        }

        invalidateLayout()
        return newElement
    }

    /**
     * Apply drag offset to tree and recalculate layout (called once at end of drag).
     * Skips locked and layout-managed elements.
     */
    private fun applyDragToTree(delta: Offset) {
        val selectedSet = selection.selectedElements.value
        if (selectedSet.isEmpty() || _rootElement.value == null) return

        val selectedIds = selectedSet.mapNotNull { it.id }.toSet()
        val newSelectedElements = mutableSetOf<UIElement>()

        val rootElement = _rootElement.value!!

        val newRoot = rootElement.mapDescendants { element ->
            val isSelected = element in selectedSet ||
                    (element.id != null && element.id in selectedIds)

            val isRoot = element === rootElement

            if (isSelected && !element.metadata.locked && !isRoot) {
                val anchorProp = element.getProperty("Anchor")
                if (anchorProp !is PropertyValue.Anchor) {
                    newSelectedElements.add(element)
                    return@mapDescendants element
                }

                val parent = findParent(element)
                val pBounds = if (parent != null) getBounds(parent)?.bounds else null
                val newAnchor = calculateMovedAnchor(anchorProp.anchor, delta, pBounds)

                // Record delta for export (DL-002)
                element.id?.let { elementId ->
                    _editDeltaTracker?.record(EditDeltaTracker.EditDelta.MoveElement(
                        elementId = elementId,
                        newAnchor = PropertyValue.Anchor(newAnchor)
                    ))
                }

                val newElement = element.setProperty("Anchor", PropertyValue.Anchor(newAnchor))
                newSelectedElements.add(newElement)
                newElement
            } else {
                if (isSelected) newSelectedElements.add(element)
                element
            }
        }

        commitTreeEdit(newRoot)
        selection.setSelection(newSelectedElements)
        invalidateLayout()
    }

    /**
     * Recursively collect all descendant element IDs (not including the element itself).
     */
    private fun collectDescendantIds(element: UIElement): Set<String> {
        val ids = mutableSetOf<String>()
        element.visitDescendants { descendant ->
            if (descendant !== element) {
                descendant.id?.let { ids.add(it.value) }
            }
        }
        return ids
    }

    fun selectElement(element: UIElement) = selection.select(element)
    fun addToSelection(element: UIElement) = selection.addToSelection(element)
    fun removeFromSelection(element: UIElement) = selection.removeFromSelection(element)
    fun clearSelection() = selection.clearSelection()


    fun isSelected(element: UIElement): Boolean = selection.isSelected(element)
    fun isLocked(element: UIElement): Boolean = selection.isLocked(element)

    /**
     * Move selected elements by delta offset with undo support.
     * Updates the Anchor property of each selected element.
     * Skips locked and layout-managed elements.
     *
     * @param delta The offset to move by
     * @param recordUndo Whether to record for undo (default: true)
     * @param allowMerge Whether to merge with previous move command (default: true)
     */
    fun moveSelectedElements(delta: Offset, recordUndo: Boolean = true, allowMerge: Boolean = true) {
        if (selection.selectedElements.value.isEmpty() || _rootElement.value == null) return

        if (recordUndo) {
            val root = _rootElement.value!!
            val selectedSet = selection.selectedElements.value
            // Build set of selected IDs for lookup
            val selectedIds = selectedSet.mapNotNull { it.id }.toSet()

            // Find the current version of selected elements in the tree
            // Excluding locked and root elements (layout-managed can move on free axis)
            val currentSelectedElements = mutableListOf<UIElement>()
            root.visitDescendants { element ->
                val isSelected = element in selectedSet ||
                        (element.id != null && element.id in selectedIds)
                val isRoot = element === root
                if (isSelected && !element.metadata.locked && !isRoot) {
                    currentSelectedElements.add(element)
                }
            }

            if (currentSelectedElements.isEmpty()) return

            // Build commands for all selected elements (using current tree references)
            val commands = currentSelectedElements.mapNotNull { element ->
                val anchorProp = element.getProperty("Anchor")
                if (anchorProp !is PropertyValue.Anchor) return@mapNotNull null
                val currentAnchor = anchorProp.anchor

                val parent = findParent(element)
                val pBounds = if (parent != null) getBounds(parent)?.bounds else null
                val newAnchor = calculateMovedAnchor(currentAnchor, delta, pBounds)
                MoveElementCommand.forElement(element, anchorProp, PropertyValue.Anchor(newAnchor))
            }

            if (commands.isEmpty()) return

            // Execute as composite command if multiple, otherwise single
            val command = if (commands.size == 1) {
                commands.first()
            } else {
                CompositeCommand(commands, "Move ${commands.size} elements")
            }

            executeCommand(command, allowMerge)
        } else {
            // During drag: check if ALL selected elements are locked
            val allLocked = selection.selectedElements.value.all { it.metadata.locked }
            if (allLocked) return

            // Update visual offset (no tree modifications) — O(1)
            _isDragging.value = true
            _rawDragOffset.value = _rawDragOffset.value + delta

            // Apply snap (if enabled and Alt not pressed)
            if (_snapGuidesEnabled.value && !_altPressed.value && _snapTargetBounds.isNotEmpty()) {
                val selectedBounds = selection.selectedElements.value.mapNotNull {
                    _calculatedLayout.value[it]?.bounds
                }
                val bbox = boundingBoxOf(selectedBounds)
                if (bbox != null) {
                    val movedBBox = bbox.offset(_rawDragOffset.value.x, _rawDragOffset.value.y)
                    val result = calculateSnap(movedBBox, _snapTargetBounds)
                    _activeSnapGuides.value = result.guides
                    _dragOffset.value = _rawDragOffset.value + result.snapDelta
                } else {
                    _activeSnapGuides.value = emptyList()
                    _dragOffset.value = _rawDragOffset.value
                }
            } else {
                _activeSnapGuides.value = emptyList()
                _dragOffset.value = _rawDragOffset.value
            }
        }
    }

    /**
     * Record the cumulative move operation for undo after drag ends.
     * The elements have already been moved directly - this just records the undo command.
     *
     * @param totalDelta The total movement that was applied during the drag
     */
    fun recordMoveUndo(totalDelta: Offset) {
        if (selection.selectedElements.value.isEmpty() || _rootElement.value == null) return
        if (totalDelta == Offset.Zero) return

        val root = _rootElement.value!!
        val selectedSet = selection.selectedElements.value
        val selectedIds = selectedSet.mapNotNull { it.id }.toSet()

        // Find current elements and calculate what their OLD anchors were (before the drag)
        val commands = mutableListOf<DocumentCommand>()
        root.visitDescendants { element ->
            val isSelected = element in selectedSet ||
                    (element.id != null && element.id in selectedIds)
            val isRoot = element === root
            if (isSelected && !element.metadata.locked && !isRoot) {
                val currentAnchorProp = element.getProperty("Anchor")
                if (currentAnchorProp is PropertyValue.Anchor) {
                    // Calculate what the anchor was BEFORE the move (reverse the delta)
                    val parent = findParent(element)
                    val pBounds = if (parent != null) getBounds(parent)?.bounds else null
                    val oldAnchor = calculateMovedAnchor(currentAnchorProp.anchor, Offset(-totalDelta.x, -totalDelta.y), pBounds)
                    commands.add(
                        MoveElementCommand.forElement(
                            element,
                            PropertyValue.Anchor(oldAnchor), // Old value
                            currentAnchorProp // New value (current)
                        )
                    )
                }
            }
        }

        if (commands.isEmpty()) return

        // Create the command - it won't re-apply (elements already moved)
        // but will be recorded for undo
        val command = if (commands.size == 1) {
            commands.first()
        } else {
            CompositeCommand(commands, "Move ${commands.size} elements")
        }

        // Push to undo stack without executing (elements already in correct position)
        undoManager.pushWithoutExecute(command)
        onStatusChange?.invoke(command.description)
    }

    /**
     * Record the cumulative resize operation for undo after drag ends.
     * The element has already been resized directly - this just records the undo command.
     *
     * @param originalElement The element reference from before the drag started
     * @param handle The resize handle that was dragged
     * @param totalDelta The total resize delta that was applied during the drag
     */
    fun recordResizeUndo(originalElement: UIElement, handle: ResizeHandle, totalDelta: Offset) {
        if (_rootElement.value == null) return
        if (totalDelta == Offset.Zero) return

        // Find the current element in the tree
        val currentElement = originalElement.id?.let { id ->
            _rootElement.value?.findDescendantById(id)
        } ?: return

        val currentAnchorProp = currentElement.getProperty("Anchor")
        if (currentAnchorProp !is PropertyValue.Anchor) return

        // Calculate what the anchor was BEFORE the resize (reverse the delta)
        val oldAnchor = calculateResizedAnchor(currentAnchorProp.anchor, handle, Offset(-totalDelta.x, -totalDelta.y))
        val command = ResizeElementCommand.forElement(
            currentElement,
            PropertyValue.Anchor(oldAnchor), // Old value
            currentAnchorProp // New value (current)
        )

        // Push to undo stack without executing (element already in correct position)
        undoManager.pushWithoutExecute(command)
        onStatusChange?.invoke(command.description)
    }

    // calculateMovedAnchor → see AnchorMath.kt (top-level function)

    /**
     * Update a property on a specific element with undo support.
     * Used by the property editor for real-time updates.
     * Returns the new element reference (since UIElement is immutable).
     *
     * @param element The element to update
     * @param propertyName The property to change
     * @param value The new value
     * @param recordUndo Whether to record this change for undo (default: true)
     * @param allowMerge Whether to allow merging with previous command (default: true)
     */
    fun updateElementProperty(
        element: UIElement,
        propertyName: String,
        value: PropertyValue,
        recordUndo: Boolean = true,
        allowMerge: Boolean = true
    ): UIElement? {
        if (_rootElement.value == null) return null

        val oldValue = element.getProperty(propertyName)

        // If recording undo, use command system
        if (recordUndo) {
            val command = SetPropertyCommand.forElement(element, propertyName, oldValue, value)
            if (!executeCommand(command, allowMerge)) {
                return null
            }

            // Record delta after successful command execution
            element.id?.let { elementId ->
                _editDeltaTracker?.record(EditDeltaTracker.EditDelta.SetProperty(
                    elementId = elementId,
                    propertyName = propertyName,
                    value = value
                ))
            }

            // Find the updated element in the new tree
            return findElementInTree(element)
        }

        // Direct update without undo tracking (for internal use)
        return updateElementPropertyDirect(element, propertyName, value)
    }

    /**
     * Update a property directly without undo tracking.
     * Used internally or for transient changes that shouldn't be undoable.
     */
    private fun updateElementPropertyDirect(
        element: UIElement,
        propertyName: String,
        value: PropertyValue
    ): UIElement? {
        if (_rootElement.value == null) return null

        var newElement: UIElement? = null

        // Update the root element tree
        val newRoot = _rootElement.value!!.mapDescendants { el ->
            if (el == element) {
                val updated = el.setProperty(propertyName, value)
                newElement = updated
                updated
            } else {
                el
            }
        }

        // Update root and selection
        commitTreeEdit(newRoot)

        // Update selection to point to new element instance
        newElement?.let { updated ->
            if (element in selection.selectedElements.value) {
                selection.setSelection(selection.selectedElements.value
                    .filter { it != element }
                    .toSet() + updated)
            }
        }

        invalidateLayout()
        return newElement
    }

    /**
     * Remove a property from a specific element with undo support.
     * Used by the schema-driven property inspector.
     * Returns the new element reference (since UIElement is immutable).
     *
     * @param element The element to update
     * @param propertyName The property to remove
     * @param recordUndo Whether to record this change for undo (default: true)
     */
    fun removeElementProperty(
        element: UIElement,
        propertyName: String,
        recordUndo: Boolean = true
    ): UIElement? {
        if (_rootElement.value == null) return null

        val oldValue = element.getProperty(propertyName) ?: return null // Nothing to remove

        // If recording undo, use command system
        if (recordUndo) {
            val command = RemovePropertyCommand.forElement(element, propertyName, oldValue)
            if (!executeCommand(command, allowMerge = false)) return null

            // Record delta after successful command execution
            element.id?.let { elementId ->
                _editDeltaTracker?.record(EditDeltaTracker.EditDelta.RemoveProperty(
                    elementId = elementId,
                    propertyName = propertyName
                ))
            }

            // Find the updated element in the new tree
            val name = element.id?.value ?: element.type.value
            reportStatus("Removed $propertyName from $name")
            return findElementInTree(element)
        }

        // Direct removal without undo tracking
        return removeElementPropertyDirect(element, propertyName)
    }

    /**
     * Remove a property directly without undo tracking.
     */
    private fun removeElementPropertyDirect(
        element: UIElement,
        propertyName: String
    ): UIElement? {
        if (_rootElement.value == null) return null

        var newElement: UIElement? = null

        val newRoot = _rootElement.value!!.mapDescendants { el ->
            if (el == element) {
                val updated = el.removeProperty(PropertyName(propertyName))
                newElement = updated
                updated
            } else {
                el
            }
        }

        commitTreeEdit(newRoot)

        newElement?.let { updated ->
            if (element in selection.selectedElements.value) {
                selection.setSelection(selection.selectedElements.value
                    .filter { it != element }
                    .toSet() + updated)
            }
        }

        invalidateLayout()
        return newElement
    }

    /**
     * Update the metadata of a specific element without undo tracking.
     * Metadata is editor-only state (not exported to .ui files),
     * so undo is intentionally skipped.
     *
     * @param element The element to update
     * @param transform Function to produce the new metadata from the current metadata
     * @return The updated element in the tree, or null if not found
     */
    fun updateElementMetadata(
        element: UIElement,
        transform: (ElementMetadata) -> ElementMetadata
    ): UIElement? {
        if (_rootElement.value == null) return null

        var newElement: UIElement? = null

        val newRoot = _rootElement.value!!.mapDescendants { el ->
            if (el == element || (element.id != null && el.id == element.id)) {
                val updated = el.copy(metadata = transform(el.metadata))
                newElement = updated
                updated
            } else {
                el
            }
        }

        if (newElement != null) {
            // Use commitTreeEdit so the sidecar save flow picks up the change
            commitTreeEdit(newRoot)

            // Update selection to point to new element instance
            val updated: UIElement = newElement
            if (element in selection.selectedElements.value || (element.id != null && selection.selectedElements.value.any { it.id == element.id })) {
                selection.setSelection(selection.selectedElements.value
                    .filter { it != element && it.id != element.id }
                    .toSet() + updated)
            }

            invalidateLayout()
        }

        return newElement
    }

    /**
     * Replace an element in the tree with a new version.
     * Used to apply changes from an external editor.
     *
     * @param oldElement The element to replace (matched by ID)
     * @param newElement The new element to replace with
     * @return The new element in the updated tree, or null if replacement failed
     */
    fun replaceElement(oldElement: UIElement, newElement: UIElement): UIElement? {
        if (_rootElement.value == null) return null

        // Match by ID if available, otherwise by reference
        val oldId = oldElement.id

        var result: UIElement? = null

        val newRoot = _rootElement.value!!.mapDescendants { el ->
            val isMatch = if (oldId != null) {
                el.id == oldId
            } else {
                el == oldElement
            }

            if (isMatch) {
                result = newElement
                newElement
            } else {
                el
            }
        }

        if (result != null) {
            commitTreeEdit(newRoot)

            // Update selection to point to new element
            if (oldElement in selection.selectedElements.value || (oldId != null && selection.selectedElements.value.any { it.id == oldId })) {
                selection.setSelection(selection.selectedElements.value
                    .filter { it != oldElement && it.id != oldId }
                    .toSet() + newElement)
            }

            invalidateLayout()
            reportStatus("Updated ${newElement.displayName()}")
        }

        return result
    }

    /**
     * Find an element in the current tree by ID.
     * Returns the element from the current tree that matches the given element's ID.
     */
    private fun findElementInTree(element: UIElement): UIElement? {
        val root = _rootElement.value ?: return null
        val id = element.id ?: return null
        return root.findDescendantById(id)
    }

    // --- Layout Calculation ---

    private val _calculatedLayout = mutableStateOf<Map<UIElement, ElementBounds>>(emptyMap())
    val calculatedLayout: State<Map<UIElement, ElementBounds>> = _calculatedLayout

    /**
     * Recalculate layout for the current root element.
     * Called automatically when root element changes.
     */
    private fun invalidateLayout() {
        val root = _rootElement.value
        if (root != null) {
            _calculatedLayout.value = layoutEngine.calculateLayout(root, DEFAULT_VIEWPORT)
        } else {
            _calculatedLayout.value = emptyMap()
        }
    }

    /**
     * Get bounds for a specific element (returns null if not in layout).
     * During drag operations, applies drag offset to selected elements.
     */
    fun getBounds(element: UIElement): ElementBounds? {
        val baseBounds = _calculatedLayout.value[element] ?: return null

        // During move drag, offset selected elements and their descendants visually
        if (_isDragging.value && (isSelected(element) || element.id?.value in _dragDescendantIds)) {
            val offset = _dragOffset.value
            return baseBounds.copy(
                bounds = baseBounds.bounds.offset(offset.x, offset.y)
            )
        }

        // During resize drag, calculate visual bounds based on resize handle and delta
        if (_isResizing.value) {
            val resizeEl = _resizeElement.value
            val handle = _resizeHandle.value
            val delta = _resizeDragDelta.value
            if (resizeEl != null && handle != null && (element == resizeEl || element.id == resizeEl.id)) {
                val adjustedBounds = calculateResizedBounds(baseBounds.bounds, handle, delta)
                return baseBounds.copy(bounds = adjustedBounds)
            }
        }

        return baseBounds
    }

    // calculateResizedBounds → see AnchorMath.kt (top-level function)

    // --- Coordinate Transformations ---

    /**
     * Convert screen coordinates to canvas coordinates (accounting for zoom and pan)
     */
    fun screenToCanvas(screenX: Float, screenY: Float): Offset {
        val canvasX = (screenX - _panOffset.value.x) / _zoom.value
        val canvasY = (screenY - _panOffset.value.y) / _zoom.value
        return Offset(canvasX, canvasY)
    }

    /**
     * Convert canvas coordinates to screen coordinates (accounting for zoom and pan)
     */
    fun canvasToScreen(canvasX: Float, canvasY: Float): Offset {
        val screenX = canvasX * _zoom.value + _panOffset.value.x
        val screenY = canvasY * _zoom.value + _panOffset.value.y
        return Offset(screenX, screenY)
    }

    /**
     * Find element at screen coordinates (for click detection)
     * Returns the topmost element at the given position
     */
    fun findElementAt(screenX: Float, screenY: Float): UIElement? {
        val canvasPos = screenToCanvas(screenX, screenY)
        val layout = _calculatedLayout.value
        val root = _rootElement.value ?: return null

        // Search in reverse depth-first order (children before parents, later siblings before earlier)
        // This ensures elements drawn on top are selected first
        return findElementAtRecursive(root, canvasPos.x, canvasPos.y, layout)
    }

    /**
     * Recursively find element at position, checking children first (reverse depth-first).
     * Returns the deepest (topmost visually) element that contains the point.
     * Skips locked elements - clicks pass through to elements behind them.
     */
    private fun findElementAtRecursive(
        element: UIElement,
        x: Float,
        y: Float,
        layout: Map<UIElement, ElementBounds>
    ): UIElement? {
        // First check children in reverse order (later children are drawn on top)
        for (i in element.children.indices.reversed()) {
            val child = element.children[i]
            val found = findElementAtRecursive(child, x, y, layout)
            if (found != null) return found
        }

        // Then check this element
        // Skip if locked (clicks pass through) or if root (not selectable on canvas)
        if (element.metadata.locked) return null
        if (element === _rootElement.value) return null

        val bounds = layout[element]
        if (bounds != null && bounds.contains(x, y)) {
            return element
        }

        return null
    }

    /**
     * Find which resize handle (if any) is at the given screen coordinates.
     * Returns null if no handle is hit, or if no element is selected.
     * Skips locked elements - they cannot be resized.
     */
    fun findResizeHandleAt(screenX: Float, screenY: Float): Pair<UIElement, ResizeHandle>? {
        val canvasPos = screenToCanvas(screenX, screenY)

        // Only check handles for selected elements (skip locked and root elements)
        for (element in selection.selectedElements.value) {
            if (element.metadata.locked) continue
            if (element === _rootElement.value) continue
            val bounds = getBounds(element) ?: continue

            // Filter handles by what's allowed for this element
            val allowed = allowedResizeHandles(element)

            // Get handle positions in canvas coordinates
            val allHandles = mapOf(
                ResizeHandle.TOP_LEFT to Offset(bounds.x, bounds.y),
                ResizeHandle.TOP_CENTER to Offset(bounds.x + bounds.width / 2, bounds.y),
                ResizeHandle.TOP_RIGHT to Offset(bounds.x + bounds.width, bounds.y),
                ResizeHandle.LEFT_CENTER to Offset(bounds.x, bounds.y + bounds.height / 2),
                ResizeHandle.RIGHT_CENTER to Offset(bounds.x + bounds.width, bounds.y + bounds.height / 2),
                ResizeHandle.BOTTOM_LEFT to Offset(bounds.x, bounds.y + bounds.height),
                ResizeHandle.BOTTOM_CENTER to Offset(bounds.x + bounds.width / 2, bounds.y + bounds.height),
                ResizeHandle.BOTTOM_RIGHT to Offset(bounds.x + bounds.width, bounds.y + bounds.height)
            )

            // Check if we're clicking on an allowed handle
            val handleHitRadius = (HANDLE_SIZE / 2 + HANDLE_HIT_PADDING) / _zoom.value

            for ((handle, handlePos) in allHandles) {
                if (handle !in allowed) continue
                val dx = canvasPos.x - handlePos.x
                val dy = canvasPos.y - handlePos.y
                val distance = kotlin.math.sqrt(dx * dx + dy * dy)

                if (distance <= handleHitRadius) {
                    return element to handle
                }
            }
        }

        return null
    }

    /**
     * Resize selected element by dragging a resize handle with undo support.
     * Updates the Anchor property based on which handle is being dragged.
     * Returns the new element reference (since UIElement is immutable).
     * Skips locked elements - they cannot be resized.
     *
     * @param element The element to resize
     * @param handle The resize handle being dragged
     * @param delta The drag delta
     * @param recordUndo Whether to record for undo (default: true)
     * @param allowMerge Whether to merge with previous resize command (default: true)
     */
    fun resizeSelectedElement(
        element: UIElement,
        handle: ResizeHandle,
        delta: Offset,
        recordUndo: Boolean = true,
        allowMerge: Boolean = true
    ): UIElement? {
        if (_rootElement.value == null) return null

        // Skip locked elements - they cannot be resized
        if (element.metadata.locked) return null

        val anchorProp = element.getProperty("Anchor")
        if (anchorProp !is PropertyValue.Anchor) return null

        if (recordUndo) {
            val newAnchor = calculateResizedAnchor(anchorProp.anchor, handle, delta)
            val command = ResizeElementCommand.forElement(element, anchorProp, PropertyValue.Anchor(newAnchor))
            if (!executeCommand(command, allowMerge)) return null
            return findElementInTree(element)
        }

        // During drag: just update visual offset (no tree modifications)
        // This is O(1) instead of O(n) tree recreation
        _isResizing.value = true
        _resizeElement.value = element
        _resizeHandle.value = handle
        _resizeDragDelta.value = _resizeDragDelta.value + delta
        return element // Return same element reference during drag
    }

    // calculateResizedAnchor → see AnchorMath.kt (top-level function)

    // --- Element Creation (delegated to ElementFactory) ---

    /**
     * Create a new element of the given type and add it to the canvas at the specified position.
     */
    fun addElement(
        schema: ElementSchema,
        canvasX: Float,
        canvasY: Float,
        parentElement: UIElement? = null
    ): UIElement? {
        val root = _rootElement.value ?: return null
        val parent = parentElement ?: root

        val newElement = elementFactory.createElement(schema, canvasX, canvasY, _runtimeRegistry)
        val elementId = newElement.id!!

        // Create and execute the command
        val command = if (parent == root) {
            AddElementCommand.toRoot(newElement)
        } else {
            AddElementCommand.toParent(parent, newElement)
        }

        if (executeCommand(command, allowMerge = false)) {
            // Record structural delta after successful command execution
            _editDeltaTracker?.record(EditDeltaTracker.EditDelta.AddElement(
                parentId = if (parent == root) null else parent.id,
                index = parent.children.size,
                element = newElement
            ))

            // Select the newly created element
            // Note: Must use findElementInTreeById since _rootElement.value has been updated
            // by executeCommand, but the local 'root' variable still points to old tree
            val createdElement = findElementInTreeById(elementId)
            if (createdElement != null) {
                selectElement(createdElement)
                return createdElement
            }
        }

        return null
    }

    /**
     * Create a new element at screen coordinates.
     * Converts screen position to canvas coordinates before adding.
     */
    fun addElementAtScreenPosition(
        schema: ElementSchema,
        screenX: Float,
        screenY: Float,
        parentElement: UIElement? = null
    ): UIElement? {
        val canvasPos = screenToCanvas(screenX, screenY)
        return addElement(schema, canvasPos.x, canvasPos.y, parentElement)
    }

    /**
     * Delete the selected elements from the canvas.
     * Skips locked elements - they cannot be deleted.
     * @return true if any elements were deleted
     */
    fun deleteSelectedElements(): Boolean {
        if (selection.selectedElements.value.isEmpty() || _rootElement.value == null) return false

        val root = _rootElement.value!!
        // Filter out locked elements - they cannot be deleted
        val toDelete = selection.selectedElements.value.filter { !it.metadata.locked }

        if (toDelete.isEmpty()) {
            // All selected elements are locked
            reportStatus("Cannot delete locked elements")
            return false
        }

        // Build delete commands for each selected element
        val commands = toDelete.mapNotNull { element ->
            findParentAndIndex(root, element)?.let { (parent, index) ->
                if (parent == root) {
                    DeleteElementCommand.fromRoot(element, root, index)
                } else {
                    DeleteElementCommand.forElement(element, parent, index)
                }
            }
        }

        if (commands.isEmpty()) return false

        // Execute as composite command
        val command = if (commands.size == 1) {
            commands.first()
        } else {
            CompositeCommand(commands, "Delete ${commands.size} elements")
        }

        val success = executeCommand(command, allowMerge = false)
        if (success) {
            // Record structural deltas after successful deletion
            toDelete.forEach { element ->
                element.id?.let { elementId ->
                    _editDeltaTracker?.record(EditDeltaTracker.EditDelta.DeleteElement(
                        elementId = elementId
                    ))
                }
            }
            clearSelection()
        }
        return success
    }

    /**
     * Find the parent element of a given element in the tree.
     * Returns null if the element is the root or not found.
     */
    fun findParent(element: UIElement): UIElement? {
        val root = _rootElement.value ?: return null
        if (element === root || (element.id != null && element.id == root.id)) return null
        return findParentAndIndex(root, element)?.first
    }

    /**
     * Check if an element's position is controlled by its parent's LayoutMode.
     *
     * Returns true when the parent has a stack LayoutMode (Top, Left, Right, Bottom),
     * meaning the layout engine overrides child positions. Middle/Center are NOT
     * considered layout-managed because they respect child anchor positioning.
     */
    fun isLayoutManaged(element: UIElement): Boolean {
        val parent = findParent(element) ?: return false
        val layoutMode = ((parent.getProperty("LayoutMode")
            ?: resolveStyleToTuple(parent.getProperty("Style"))?.get("LayoutMode"))
            as? PropertyValue.Text)?.value
        return layoutMode in LayoutEngine.STACK_MODES
    }

    /**
     * Get the parent's LayoutMode for an element, if it is layout-managed.
     * Returns "Top", "Left", "Right", "Bottom", or null.
     */
    fun getParentLayoutMode(element: UIElement): String? {
        val parent = findParent(element) ?: return null
        val layoutMode = ((parent.getProperty("LayoutMode")
            ?: resolveStyleToTuple(parent.getProperty("Style"))?.get("LayoutMode"))
            as? PropertyValue.Text)?.value
        return if (layoutMode in LayoutEngine.STACK_MODES) layoutMode else null
    }

    /**
     * Determine which axes an element can be dragged on.
     * Returns a pair of (canMoveHorizontally, canMoveVertically).
     *
     * Rules:
     * - Layout-managed by "Top": vertical position is fixed → horizontal only
     * - Layout-managed by "Left": horizontal position is fixed → vertical only
     * - Center constraint (only Width, no Left/Right): no horizontal anchor to move → vertical only
     * - Middle constraint (only Height, no Top/Bottom): no vertical anchor to move → horizontal only
     * - If a parent is immovable on an axis, the child is also immovable on that axis
     * - Otherwise: both axes
     */
    fun getDragAxes(element: UIElement): Pair<Boolean, Boolean> {
        var (canH, canV) = getOwnDragAxes(element)

        // Walk up the tree: if any ancestor is immovable on an axis, lock that axis
        val root = _rootElement.value
        var current = element
        while (canH || canV) {
            val parent = findParent(current) ?: break
            if (parent === root || (root != null && parent.id != null && parent.id == root.id)) break
            val (parentH, parentV) = getOwnDragAxes(parent)
            if (!parentH) canH = false
            if (!parentV) canV = false
            current = parent
        }

        return Pair(canH, canV)
    }

    /**
     * Get the drag axes for an element based only on its own constraints,
     * without considering parent restrictions.
     */
    private fun getOwnDragAxes(element: UIElement): Pair<Boolean, Boolean> {
        val layoutMode = getParentLayoutMode(element)
        val anchor = (element.getProperty("Anchor") as? PropertyValue.Anchor)?.anchor

        // Stack layouts: V position managed by stack, only cross-axis if element has positioning anchors
        if (layoutMode in setOf("Top", "Bottom", "TopScrolling", "BottomScrolling")) {
            val hasHPos = anchor?.left != null || anchor?.right != null
            return Pair(hasHPos, false)
        }
        if (layoutMode in setOf("Left", "Right", "LeftScrolling")) {
            val hasVPos = anchor?.top != null || anchor?.bottom != null
            return Pair(false, hasVPos)
        }

        if (anchor == null) return Pair(true, true) // no anchor at all → free

        val hasHPos = anchor.left != null || anchor.right != null
        val hasVPos = anchor.top != null || anchor.bottom != null

        // Each axis is moveable only if it has a position anchor.
        // Center (Width only) → can't move H. Middle (Height only) → can't move V.
        // Both missing → immovable (centered on both axes).
        return Pair(hasHPos, hasVPos)
    }

    /**
     * Get the set of allowed resize handles for an element.
     * Layout-managed elements only get size handles relevant to their layout direction.
     * Free elements get all 8 handles.
     */
    fun allowedResizeHandles(element: UIElement): Set<ResizeHandle> {
        val layoutMode = getParentLayoutMode(element)
        return when (layoutMode) {
            // Vertical stack layouts: can change Height and Width
            "Top", "Bottom", "TopScrolling", "BottomScrolling" ->
                setOf(ResizeHandle.BOTTOM_CENTER, ResizeHandle.BOTTOM_RIGHT, ResizeHandle.RIGHT_CENTER)
            // Horizontal stack layouts: can change Width and Height
            "Left", "Right", "LeftScrolling" ->
                setOf(ResizeHandle.RIGHT_CENTER, ResizeHandle.BOTTOM_RIGHT, ResizeHandle.BOTTOM_CENTER)
            // Free elements: all handles
            else -> ResizeHandle.entries.toSet()
        }
    }

    /**
     * Find the nearest draggable ancestor of a layout-managed element.
     * Walks up the tree until it finds an element that is:
     * - Not layout-managed (its parent doesn't have a stack LayoutMode)
     * - Not the root element
     * - Not locked
     * Returns null if no draggable ancestor exists.
     */
    fun findDraggableAncestor(element: UIElement): UIElement? {
        val root = _rootElement.value ?: return null
        var current = element
        while (true) {
            val parent = findParent(current) ?: return null
            if (parent == root || parent.id == root.id) return null
            if (!parent.metadata.locked && !isLayoutManaged(parent)) return parent
            current = parent
        }
    }

    /**
     * Find the nearest ancestor of [element] that is currently selected.
     * Returns null if no ancestor is selected (only checks proper ancestors, not the element itself).
     */
    fun findSelectedAncestor(element: UIElement): UIElement? {
        return selection.findSelectedAncestor(element) { findParent(it) }
    }

    /**
     * Find the parent element and index of a child element.
     */
    private fun findParentAndIndex(root: UIElement, target: UIElement): Pair<UIElement, Int>? {
        // Check if target is a direct child of root
        val rootIndex = root.children.indexOf(target)
        if (rootIndex >= 0) {
            return root to rootIndex
        }

        // Search descendants
        var result: Pair<UIElement, Int>? = null
        root.visitDescendants { element ->
            if (result == null) {
                val index = element.children.indexOf(target)
                if (index >= 0) {
                    result = element to index
                }
            }
        }
        return result
    }

    /**
     * Find an element in the current tree by ID.
     */
    private fun findElementInTreeById(id: ElementId): UIElement? {
        return _rootElement.value?.findDescendantById(id)
    }

    // --- Z-Order Reordering ---

    /**
     * Reorder the single selected element within its parent's children list.
     * Returns true if the reorder was applied, false if at boundary or no selection.
     */
    fun reorderElement(direction: ReorderDirection): Boolean {
        val selected = selection.selectedElements.value
        if (selected.size != 1) return false
        val element = selected.first()
        val root = _rootElement.value ?: return false

        // Can't reorder the root element
        if (element === root || (element.id != null && element.id == root.id)) return false

        val (parent, currentIndex) = findParentAndIndex(root, element) ?: return false
        val siblingCount = parent.children.size

        val newIndex = when (direction) {
            ReorderDirection.FORWARD -> currentIndex + 1
            ReorderDirection.BACKWARD -> currentIndex - 1
            ReorderDirection.TO_FRONT -> siblingCount - 1
            ReorderDirection.TO_BACK -> 0
        }

        // Already at boundary
        if (newIndex < 0 || newIndex >= siblingCount || newIndex == currentIndex) return false

        val command = if (parent === root || (parent.id != null && parent.id == root.id)) {
            ReorderElementCommand.forRootChild(element, currentIndex, newIndex)
        } else {
            ReorderElementCommand.forElement(parent, element, currentIndex, newIndex)
        }

        return executeCommand(command, allowMerge = false)
    }

    // --- Sibling Selection Cycling ---

    /**
     * Select the next sibling of the currently selected element (wraps around).
     * If nothing is selected, selects the first child of root.
     */
    fun selectNextSibling() {
        cycleSibling(forward = true)
    }

    /**
     * Select the previous sibling of the currently selected element (wraps around).
     * If nothing is selected, selects the last child of root.
     */
    fun selectPrevSibling() {
        cycleSibling(forward = false)
    }

    private fun cycleSibling(forward: Boolean) {
        val root = _rootElement.value ?: return

        val selected = selection.selectedElements.value
        if (selected.isEmpty()) {
            // Nothing selected: select first/last child of root
            if (root.children.isEmpty()) return
            val index = if (forward) 0 else root.children.lastIndex
            selectElement(root.children[index])
            return
        }

        val element = selected.first()
        val (parent, currentIndex) = findParentAndIndex(root, element) ?: return
        if (parent.children.size <= 1) return

        val newIndex = if (forward) {
            (currentIndex + 1) % parent.children.size
        } else {
            (currentIndex - 1 + parent.children.size) % parent.children.size
        }

        selectElement(parent.children[newIndex])
    }

    // getDefaultSizeForType, buildDefaultProperties → see ElementFactory.kt
}

/**
 * Direction for z-order reordering operations.
 */
enum class ReorderDirection {
    /** Move one step forward (higher index = drawn later = visually on top) */
    FORWARD,
    /** Move one step backward (lower index = drawn earlier = visually behind) */
    BACKWARD,
    /** Move to front (last index) */
    TO_FRONT,
    /** Move to back (index 0) */
    TO_BACK
}

/**
 * Remember canvas state across recompositions
 */
@Composable
fun rememberCanvasState(
    initialZoom: Float = 1.0f,
    initialPan: Offset = Offset.Zero,
    onStatusChange: ((String) -> Unit)? = null
): CanvasState {
    // Create layout engine inside remember to ensure stability
    return remember(onStatusChange) {
        val layoutEngine = LayoutEngine(SchemaRegistry.default())
        CanvasState(
            layoutEngine = layoutEngine,
            initialZoom = initialZoom,
            initialPan = initialPan,
            onStatusChange = onStatusChange
        )
    }
}
