// Copyright 2026 Hyve. All rights reserved.
package com.hyve.ui.editor

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.geometry.Offset
import com.hyve.ui.canvas.CanvasState
import com.hyve.ui.canvas.ReorderDirection
import com.hyve.ui.core.domain.elements.UIElement
import com.hyve.ui.settings.Hotkeys

/**
 * Handles keyboard events for the editor.
 *
 * Extracted from EditorMainContent to keep the hotkey dispatch
 * logic isolated and testable. Returns true if the event was consumed.
 */
internal fun handleEditorKeyEvent(
    event: KeyEvent,
    canvasState: CanvasState,
    composerOpen: Boolean,
    onSave: () -> Unit,
    onOpenComposer: (UIElement) -> Unit
): Boolean {
    val key = event.key
    val ctrl = event.isCtrlPressed
    val shift = event.isShiftPressed
    val alt = event.isAltPressed

    // Track Space and Shift on both KeyDown and KeyUp for canvas pan/multi-select.
    // These are tracked here (not in CanvasView) so they work regardless of
    // which panel has focus — clicking the toolbox or hierarchy shouldn't
    // break Space-to-pan or Shift-to-multiselect.
    val isTextFocused = com.hyve.ui.settings.TextInputFocusState.isTextFieldFocused.value
    when (key) {
        Key.Spacebar -> {
            if (event.type == KeyEventType.KeyUp) {
                // Always clear space on key-up (even if text field gained focus mid-press)
                canvasState.setSpacePressed(false)
            } else if (!isTextFocused) {
                // Only set space-pressed on key-down when no text field is focused
                canvasState.setSpacePressed(true)
                return true
            }
        }
        Key.ShiftLeft, Key.ShiftRight -> {
            canvasState.setShiftPressed(event.type == KeyEventType.KeyDown)
            // Don't consume — Shift is also used as a modifier for other hotkeys
            return false
        }
    }

    // All remaining hotkeys only fire on KeyDown
    if (event.type != KeyEventType.KeyDown) return false

    // When the Composer modal is open, suppress all canvas/hierarchy hotkeys.
    // Only allow global actions like Save and Undo/Redo through.
    return when {
        Hotkeys.matches(Hotkeys.save, key, ctrl, shift, alt) -> {
            onSave(); true
        }
        !composerOpen && Hotkeys.matchesWithFocusCheck(Hotkeys.toggleGrid, key, ctrl, shift, alt) -> {
            canvasState.toggleGrid(); true
        }
        !composerOpen && Hotkeys.matchesWithFocusCheck(Hotkeys.cycleScreenshotMode, key, ctrl, shift, alt) -> {
            canvasState.cycleScreenshotMode(); true
        }
        !composerOpen && Hotkeys.matchesWithFocusCheck(Hotkeys.toggleScreenshot, key, ctrl, shift, alt) -> {
            canvasState.toggleScreenshot(); true
        }
        Hotkeys.matches(Hotkeys.undo, key, ctrl, shift, alt) -> {
            canvasState.undo(); true
        }
        Hotkeys.matches(Hotkeys.redo, key, ctrl, shift, alt) ||
        Hotkeys.matches(Hotkeys.redoAlt, key, ctrl, shift, alt) -> {
            canvasState.redo(); true
        }
        !composerOpen && Hotkeys.matches(Hotkeys.resetZoom, key, ctrl, shift, alt) -> {
            canvasState.resetZoom(); true
        }
        !composerOpen && Hotkeys.matches(Hotkeys.zoomIn, key, ctrl, shift, alt) -> {
            canvasState.zoomIn(); true
        }
        !composerOpen && Hotkeys.matches(Hotkeys.zoomOut, key, ctrl, shift, alt) -> {
            canvasState.zoomOut(); true
        }
        !composerOpen && (Hotkeys.matchesWithFocusCheck(Hotkeys.delete, key, ctrl, shift, alt) ||
        Hotkeys.matchesWithFocusCheck(Hotkeys.deleteAlt, key, ctrl, shift, alt)) -> {
            if (canvasState.selectedElements.value.isNotEmpty()) {
                canvasState.deleteSelectedElements(); true
            } else false
        }
        // Canvas-local: Open Composer with Enter (unless text editing)
        !composerOpen && Hotkeys.matchesWithFocusCheck(Hotkeys.openComposer, key, ctrl, shift, alt) -> {
            if (!canvasState.isTextEditing()) {
                val selected = canvasState.selectedElements.value.firstOrNull()
                if (selected != null) {
                    onOpenComposer(selected)
                    true
                } else false
            } else false
        }
        // Canvas-local: Clear selection with Escape
        !composerOpen && Hotkeys.matchesWithFocusCheck(Hotkeys.clearSelection, key, ctrl, shift, alt) -> {
            if (canvasState.selectedElements.value.isNotEmpty()) {
                canvasState.clearSelection(); true
            } else false
        }

        // --- Element nudging: Arrow keys move selected elements ---
        !composerOpen && Hotkeys.matchesWithFocusCheck(Hotkeys.nudgeUp, key, ctrl, shift, alt) -> {
            nudgeSelected(canvasState, Offset(0f, -1f))
        }
        !composerOpen && Hotkeys.matchesWithFocusCheck(Hotkeys.nudgeDown, key, ctrl, shift, alt) -> {
            nudgeSelected(canvasState, Offset(0f, 1f))
        }
        !composerOpen && Hotkeys.matchesWithFocusCheck(Hotkeys.nudgeLeft, key, ctrl, shift, alt) -> {
            nudgeSelected(canvasState, Offset(-1f, 0f))
        }
        !composerOpen && Hotkeys.matchesWithFocusCheck(Hotkeys.nudgeRight, key, ctrl, shift, alt) -> {
            nudgeSelected(canvasState, Offset(1f, 0f))
        }
        !composerOpen && Hotkeys.matchesWithFocusCheck(Hotkeys.nudgeUpLarge, key, ctrl, shift, alt) -> {
            nudgeSelected(canvasState, Offset(0f, -10f))
        }
        !composerOpen && Hotkeys.matchesWithFocusCheck(Hotkeys.nudgeDownLarge, key, ctrl, shift, alt) -> {
            nudgeSelected(canvasState, Offset(0f, 10f))
        }
        !composerOpen && Hotkeys.matchesWithFocusCheck(Hotkeys.nudgeLeftLarge, key, ctrl, shift, alt) -> {
            nudgeSelected(canvasState, Offset(-10f, 0f))
        }
        !composerOpen && Hotkeys.matchesWithFocusCheck(Hotkeys.nudgeRightLarge, key, ctrl, shift, alt) -> {
            nudgeSelected(canvasState, Offset(10f, 0f))
        }

        // --- Element resize: Alt+Arrow keys resize selected element ---
        !composerOpen && Hotkeys.matches(Hotkeys.resizeRight, key, ctrl, shift, alt) -> {
            resizeSelected(canvasState, Offset(1f, 0f))
        }
        !composerOpen && Hotkeys.matches(Hotkeys.resizeLeft, key, ctrl, shift, alt) -> {
            resizeSelected(canvasState, Offset(-1f, 0f))
        }
        !composerOpen && Hotkeys.matches(Hotkeys.resizeDown, key, ctrl, shift, alt) -> {
            resizeSelected(canvasState, Offset(0f, 1f))
        }
        !composerOpen && Hotkeys.matches(Hotkeys.resizeUp, key, ctrl, shift, alt) -> {
            resizeSelected(canvasState, Offset(0f, -1f))
        }
        !composerOpen && Hotkeys.matches(Hotkeys.resizeRightLarge, key, ctrl, shift, alt) -> {
            resizeSelected(canvasState, Offset(10f, 0f))
        }
        !composerOpen && Hotkeys.matches(Hotkeys.resizeLeftLarge, key, ctrl, shift, alt) -> {
            resizeSelected(canvasState, Offset(-10f, 0f))
        }
        !composerOpen && Hotkeys.matches(Hotkeys.resizeDownLarge, key, ctrl, shift, alt) -> {
            resizeSelected(canvasState, Offset(0f, 10f))
        }
        !composerOpen && Hotkeys.matches(Hotkeys.resizeUpLarge, key, ctrl, shift, alt) -> {
            resizeSelected(canvasState, Offset(0f, -10f))
        }

        // --- Z-order: Ctrl+[/] reorder selected element ---
        !composerOpen && Hotkeys.matches(Hotkeys.bringForward, key, ctrl, shift, alt) -> {
            canvasState.reorderElement(ReorderDirection.FORWARD)
        }
        !composerOpen && Hotkeys.matches(Hotkeys.sendBackward, key, ctrl, shift, alt) -> {
            canvasState.reorderElement(ReorderDirection.BACKWARD)
        }
        !composerOpen && Hotkeys.matches(Hotkeys.bringToFront, key, ctrl, shift, alt) -> {
            canvasState.reorderElement(ReorderDirection.TO_FRONT)
        }
        !composerOpen && Hotkeys.matches(Hotkeys.sendToBack, key, ctrl, shift, alt) -> {
            canvasState.reorderElement(ReorderDirection.TO_BACK)
        }

        // --- Selection cycling: Tab / Shift+Tab ---
        !composerOpen && Hotkeys.matchesWithFocusCheck(Hotkeys.selectNextSibling, key, ctrl, shift, alt) -> {
            canvasState.selectNextSibling(); true
        }
        !composerOpen && Hotkeys.matchesWithFocusCheck(Hotkeys.selectPrevSibling, key, ctrl, shift, alt) -> {
            canvasState.selectPrevSibling(); true
        }

        else -> false
    }
}

/**
 * Nudge selected elements by delta with undo coalescing.
 */
private fun nudgeSelected(canvasState: CanvasState, delta: Offset): Boolean {
    if (canvasState.selectedElements.value.isEmpty()) return false
    canvasState.moveSelectedElements(delta, recordUndo = true, allowMerge = true)
    return true
}

/**
 * Resize the single selected element by delta with undo coalescing.
 * Only works with single selection.
 */
private fun resizeSelected(canvasState: CanvasState, delta: Offset): Boolean {
    val selected = canvasState.selectedElements.value
    if (selected.size != 1) return false
    val element = selected.first()
    canvasState.resizeSelectedElement(
        element,
        CanvasState.ResizeHandle.BOTTOM_RIGHT,
        delta,
        recordUndo = true,
        allowMerge = true
    )
    return true
}
