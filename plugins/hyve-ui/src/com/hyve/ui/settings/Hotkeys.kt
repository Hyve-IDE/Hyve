// Copyright 2026 Hyve. All rights reserved.
package com.hyve.ui.settings

import androidx.compose.ui.input.key.Key

/**
 * Centralized hotkey configuration for the Hyve UI Editor.
 *
 * All hotkeys are defined here for easy management and future configurability.
 * When we add a settings page, users will be able to remap these hotkeys.
 */
object Hotkeys {

    /**
     * Represents a hotkey binding with modifiers.
     */
    data class HotkeyBinding(
        val key: Key,
        val ctrl: Boolean = false,
        val shift: Boolean = false,
        val alt: Boolean = false,
        val description: String = ""
    ) {
        /** Whether this hotkey uses any modifier keys. */
        val hasModifier: Boolean get() = ctrl || shift || alt

        /**
         * Returns a human-readable string for this hotkey (e.g., "Ctrl+S", "G")
         */
        fun displayString(): String {
            val parts = mutableListOf<String>()
            if (ctrl) parts.add("Ctrl")
            if (shift) parts.add("Shift")
            if (alt) parts.add("Alt")
            parts.add(keyToString(key))
            return parts.joinToString("+")
        }

        private fun keyToString(key: Key): String = when (key) {
            Key.A -> "A"; Key.B -> "B"; Key.C -> "C"; Key.D -> "D"
            Key.E -> "E"; Key.F -> "F"; Key.G -> "G"; Key.H -> "H"
            Key.I -> "I"; Key.J -> "J"; Key.K -> "K"; Key.L -> "L"
            Key.M -> "M"; Key.N -> "N"; Key.O -> "O"; Key.P -> "P"
            Key.Q -> "Q"; Key.R -> "R"; Key.S -> "S"; Key.T -> "T"
            Key.U -> "U"; Key.V -> "V"; Key.W -> "W"; Key.X -> "X"
            Key.Y -> "Y"; Key.Z -> "Z"
            Key.Zero -> "0"; Key.One -> "1"; Key.Two -> "2"; Key.Three -> "3"
            Key.Four -> "4"; Key.Five -> "5"; Key.Six -> "6"; Key.Seven -> "7"
            Key.Eight -> "8"; Key.Nine -> "9"
            Key.Plus -> "+"; Key.Minus -> "-"; Key.Equals -> "="
            Key.Delete -> "Delete"; Key.Backspace -> "Backspace"
            Key.Enter -> "Enter"; Key.Escape -> "Escape"
            Key.Spacebar -> "Space"
            Key.DirectionUp -> "\u2191"; Key.DirectionDown -> "\u2193"
            Key.DirectionLeft -> "\u2190"; Key.DirectionRight -> "\u2192"
            Key.LeftBracket -> "["; Key.RightBracket -> "]"
            Key.Tab -> "Tab"; Key.Slash -> "/"
            else -> key.toString().removePrefix("Key: ")
        }
    }

    // ==================== FILE OPERATIONS ====================

    /** Save the current file */
    var save = HotkeyBinding(Key.S, ctrl = true, description = "Save")

    // ==================== EDIT OPERATIONS ====================

    /** Undo the last action */
    var undo = HotkeyBinding(Key.Z, ctrl = true, description = "Undo")

    /** Redo the last undone action */
    var redo = HotkeyBinding(Key.Y, ctrl = true, description = "Redo")

    /** Alternative redo binding */
    var redoAlt = HotkeyBinding(Key.Z, ctrl = true, shift = true, description = "Redo (alternative)")

    /** Delete selected elements */
    var delete = HotkeyBinding(Key.Delete, description = "Delete selected")

    /** Alternative delete binding */
    var deleteAlt = HotkeyBinding(Key.Backspace, description = "Delete selected (alternative)")

    // ==================== VIEW OPERATIONS ====================

    /** Toggle grid visibility */
    var toggleGrid = HotkeyBinding(Key.G, description = "Toggle grid")

    /** Toggle screenshot overlay visibility */
    var toggleScreenshot = HotkeyBinding(Key.B, description = "Toggle screenshot overlay")

    /** Cycle screenshot mode (HUD / No HUD) */
    var cycleScreenshotMode = HotkeyBinding(Key.B, shift = true, description = "Cycle screenshot mode")

    /** Toggle snap alignment guides */
    var toggleSnapGuides = HotkeyBinding(Key.S, description = "Toggle snap guides")

    /** Reset zoom to 100% */
    var resetZoom = HotkeyBinding(Key.Zero, ctrl = true, description = "Reset zoom")

    /** Zoom in */
    var zoomIn = HotkeyBinding(Key.Plus, ctrl = true, description = "Zoom in")

    /** Zoom out */
    var zoomOut = HotkeyBinding(Key.Minus, ctrl = true, description = "Zoom out")

    // ==================== CANVAS OPERATIONS ====================

    /** Open selected element in Composer */
    var openComposer = HotkeyBinding(Key.Enter, description = "Open in Composer")

    /** Clear selection / cancel */
    var clearSelection = HotkeyBinding(Key.Escape, description = "Clear selection")

    // ==================== ELEMENT OPERATIONS ====================

    /** Nudge selected elements up by 1px */
    var nudgeUp = HotkeyBinding(Key.DirectionUp, description = "Nudge up 1px")
    /** Nudge selected elements down by 1px */
    var nudgeDown = HotkeyBinding(Key.DirectionDown, description = "Nudge down 1px")
    /** Nudge selected elements left by 1px */
    var nudgeLeft = HotkeyBinding(Key.DirectionLeft, description = "Nudge left 1px")
    /** Nudge selected elements right by 1px */
    var nudgeRight = HotkeyBinding(Key.DirectionRight, description = "Nudge right 1px")

    /** Nudge selected elements up by 10px */
    var nudgeUpLarge = HotkeyBinding(Key.DirectionUp, shift = true, description = "Nudge up 10px")
    /** Nudge selected elements down by 10px */
    var nudgeDownLarge = HotkeyBinding(Key.DirectionDown, shift = true, description = "Nudge down 10px")
    /** Nudge selected elements left by 10px */
    var nudgeLeftLarge = HotkeyBinding(Key.DirectionLeft, shift = true, description = "Nudge left 10px")
    /** Nudge selected elements right by 10px */
    var nudgeRightLarge = HotkeyBinding(Key.DirectionRight, shift = true, description = "Nudge right 10px")

    /** Resize selected element: grow right/down by 1px */
    var resizeRight = HotkeyBinding(Key.DirectionRight, alt = true, description = "Resize +1px right")
    /** Resize selected element: shrink from right by 1px */
    var resizeLeft = HotkeyBinding(Key.DirectionLeft, alt = true, description = "Resize -1px right")
    /** Resize selected element: grow down by 1px */
    var resizeDown = HotkeyBinding(Key.DirectionDown, alt = true, description = "Resize +1px down")
    /** Resize selected element: shrink from bottom by 1px */
    var resizeUp = HotkeyBinding(Key.DirectionUp, alt = true, description = "Resize -1px down")

    /** Resize selected element: grow right/down by 10px */
    var resizeRightLarge = HotkeyBinding(Key.DirectionRight, shift = true, alt = true, description = "Resize +10px right")
    /** Resize selected element: shrink from right by 10px */
    var resizeLeftLarge = HotkeyBinding(Key.DirectionLeft, shift = true, alt = true, description = "Resize -10px right")
    /** Resize selected element: grow down by 10px */
    var resizeDownLarge = HotkeyBinding(Key.DirectionDown, shift = true, alt = true, description = "Resize +10px down")
    /** Resize selected element: shrink from bottom by 10px */
    var resizeUpLarge = HotkeyBinding(Key.DirectionUp, shift = true, alt = true, description = "Resize -10px down")

    /** Bring selected element forward one step (z-order) */
    var bringForward = HotkeyBinding(Key.RightBracket, ctrl = true, description = "Bring forward")
    /** Send selected element backward one step (z-order) */
    var sendBackward = HotkeyBinding(Key.LeftBracket, ctrl = true, description = "Send backward")
    /** Bring selected element to front (z-order) */
    var bringToFront = HotkeyBinding(Key.RightBracket, ctrl = true, shift = true, description = "Bring to front")
    /** Send selected element to back (z-order) */
    var sendToBack = HotkeyBinding(Key.LeftBracket, ctrl = true, shift = true, description = "Send to back")

    // ==================== SELECTION OPERATIONS ====================

    /** Select next sibling element */
    var selectNextSibling = HotkeyBinding(Key.Tab, description = "Select next sibling")
    /** Select previous sibling element */
    var selectPrevSibling = HotkeyBinding(Key.Tab, shift = true, description = "Select previous sibling")

    // ==================== COMPOSER OPERATIONS ====================

    /** Toggle code preview in Composer */
    var toggleCode = HotkeyBinding(Key.C, ctrl = true, shift = true, description = "Toggle code preview")

    // ==================== HELP OPERATIONS ====================

    /** Toggle hotkey reference panel */
    var toggleHotkeyReference = HotkeyBinding(Key.Slash, ctrl = true, description = "Keyboard shortcuts")

    // ==================== UTILITY ====================

    /**
     * Check if a key event matches a hotkey binding.
     * Does NOT check for text field focus — use [matchesWithFocusCheck] for that.
     */
    fun matches(
        binding: HotkeyBinding,
        key: Key,
        isCtrlPressed: Boolean,
        isShiftPressed: Boolean,
        isAltPressed: Boolean = false
    ): Boolean {
        return binding.key == key &&
                binding.ctrl == isCtrlPressed &&
                binding.shift == isShiftPressed &&
                binding.alt == isAltPressed
    }

    /**
     * Check if a key event matches a hotkey binding, with text field focus awareness.
     *
     * When a text field has focus:
     * - Hotkeys WITHOUT modifiers (like G, B, Delete) are suppressed to allow typing
     * - Hotkeys WITH modifiers (like Ctrl+S, Ctrl+Z) still work
     */
    fun matchesWithFocusCheck(
        binding: HotkeyBinding,
        key: Key,
        isCtrlPressed: Boolean,
        isShiftPressed: Boolean,
        isAltPressed: Boolean = false
    ): Boolean {
        if (!matches(binding, key, isCtrlPressed, isShiftPressed, isAltPressed)) {
            return false
        }

        // Suppress non-modifier hotkeys during text input
        if (TextInputFocusState.isTextFieldFocused.value && !binding.hasModifier) {
            return false
        }

        return true
    }

    /**
     * Get all hotkey bindings for display in settings.
     * Returns a map of category -> list of (name, binding) pairs.
     */
    fun getAllBindings(): Map<String, List<Pair<String, HotkeyBinding>>> = linkedMapOf(
        "File" to listOf(
            "Save" to save
        ),
        "Edit" to listOf(
            "Undo" to undo,
            "Redo" to redo,
            "Delete" to delete
        ),
        "View" to listOf(
            "Toggle Grid" to toggleGrid,
            "Toggle Snap Guides" to toggleSnapGuides,
            "Toggle Screenshot" to toggleScreenshot,
            "Cycle Screenshot Mode" to cycleScreenshotMode,
            "Reset Zoom" to resetZoom,
            "Zoom In" to zoomIn,
            "Zoom Out" to zoomOut
        ),
        "Canvas" to listOf(
            "Open in Composer" to openComposer,
            "Clear Selection" to clearSelection
        ),
        "Element" to listOf(
            "Nudge Up" to nudgeUp,
            "Nudge Down" to nudgeDown,
            "Nudge Left" to nudgeLeft,
            "Nudge Right" to nudgeRight,
            "Nudge Up 10px" to nudgeUpLarge,
            "Nudge Down 10px" to nudgeDownLarge,
            "Nudge Left 10px" to nudgeLeftLarge,
            "Nudge Right 10px" to nudgeRightLarge,
            "Resize Right" to resizeRight,
            "Resize Left" to resizeLeft,
            "Resize Down" to resizeDown,
            "Resize Up" to resizeUp,
            "Resize Right 10px" to resizeRightLarge,
            "Resize Left 10px" to resizeLeftLarge,
            "Resize Down 10px" to resizeDownLarge,
            "Resize Up 10px" to resizeUpLarge,
            "Bring Forward" to bringForward,
            "Send Backward" to sendBackward,
            "Bring to Front" to bringToFront,
            "Send to Back" to sendToBack
        ),
        "Selection" to listOf(
            "Select Next Sibling" to selectNextSibling,
            "Select Previous Sibling" to selectPrevSibling
        ),
        "Composer" to listOf(
            "Toggle Code" to toggleCode
        ),
        "Help" to listOf(
            "Keyboard Shortcuts" to toggleHotkeyReference
        )
    )
}
