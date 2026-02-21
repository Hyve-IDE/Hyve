package com.hyve.prefab.editor

import androidx.compose.ui.input.key.*

/**
 * Hotkey bindings for the Prefab Editor.
 */
object PrefabHotkeys {

    val save = HotkeyBinding(Key.S, ctrl = true)
    val undo = HotkeyBinding(Key.Z, ctrl = true)
    val redo = HotkeyBinding(Key.Y, ctrl = true)
    val redoAlt = HotkeyBinding(Key.Z, ctrl = true, shift = true)

    /**
     * Handle a key event against all prefab hotkeys.
     * Returns the matched [Action], or null if no binding matched.
     */
    fun match(event: KeyEvent): Action? {
        if (event.type != KeyEventType.KeyDown) return null
        return when {
            save.matches(event) -> Action.SAVE
            undo.matches(event) -> Action.UNDO
            redo.matches(event) || redoAlt.matches(event) -> Action.REDO
            else -> null
        }
    }

    enum class Action { SAVE, UNDO, REDO }

    data class HotkeyBinding(
        val key: Key,
        val ctrl: Boolean = false,
        val shift: Boolean = false,
        val alt: Boolean = false,
    ) {
        fun matches(event: KeyEvent): Boolean =
            event.key == key &&
                event.isCtrlPressed == ctrl &&
                event.isShiftPressed == shift &&
                event.isAltPressed == alt
    }
}
