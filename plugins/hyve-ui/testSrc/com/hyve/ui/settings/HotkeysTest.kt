// Copyright 2026 Hyve. All rights reserved.
package com.hyve.ui.settings

import androidx.compose.ui.input.key.Key
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

class HotkeysTest {

    // --- Save hotkey ---

    @Test
    fun `save hotkey should be Ctrl+S`() {
        assertThat(Hotkeys.save.key).isEqualTo(Key.S)
        assertThat(Hotkeys.save.ctrl).isTrue()
        assertThat(Hotkeys.save.shift).isFalse()
        assertThat(Hotkeys.save.alt).isFalse()
    }

    @Test
    fun `save hotkey should have modifier`() {
        assertThat(Hotkeys.save.hasModifier).isTrue()
    }

    @Test
    fun `save hotkey display string should be Ctrl+S`() {
        assertThat(Hotkeys.save.displayString()).isEqualTo("Ctrl+S")
    }

    // --- matches() ---

    @Test
    fun `matches should return true for exact match`() {
        assertThat(Hotkeys.matches(Hotkeys.save, Key.S, isCtrlPressed = true, isShiftPressed = false)).isTrue()
    }

    @Test
    fun `matches should return false when ctrl not pressed`() {
        assertThat(Hotkeys.matches(Hotkeys.save, Key.S, isCtrlPressed = false, isShiftPressed = false)).isFalse()
    }

    @Test
    fun `matches should return false for wrong key`() {
        assertThat(Hotkeys.matches(Hotkeys.save, Key.Z, isCtrlPressed = true, isShiftPressed = false)).isFalse()
    }

    @Test
    fun `matches should return false when extra modifier pressed`() {
        assertThat(Hotkeys.matches(Hotkeys.save, Key.S, isCtrlPressed = true, isShiftPressed = true)).isFalse()
    }

    // --- matchesWithFocusCheck for modifier hotkeys ---

    @Test
    fun `matchesWithFocusCheck should pass for save even during text input`() {
        // Save has Ctrl modifier, so it should work even when text field is focused
        TextInputFocusState.isTextFieldFocused.value = true
        try {
            assertThat(
                Hotkeys.matchesWithFocusCheck(Hotkeys.save, Key.S, isCtrlPressed = true, isShiftPressed = false)
            ).isTrue()
        } finally {
            TextInputFocusState.isTextFieldFocused.value = false
        }
    }

    @Test
    fun `matchesWithFocusCheck should block non-modifier hotkeys during text input`() {
        TextInputFocusState.isTextFieldFocused.value = true
        try {
            assertThat(
                Hotkeys.matchesWithFocusCheck(Hotkeys.toggleGrid, Key.G, isCtrlPressed = false, isShiftPressed = false)
            ).isFalse()
        } finally {
            TextInputFocusState.isTextFieldFocused.value = false
        }
    }

    @Test
    fun `matchesWithFocusCheck should allow non-modifier hotkeys when no text input`() {
        TextInputFocusState.isTextFieldFocused.value = false
        assertThat(
            Hotkeys.matchesWithFocusCheck(Hotkeys.toggleGrid, Key.G, isCtrlPressed = false, isShiftPressed = false)
        ).isTrue()
    }

    // --- getAllBindings includes save ---

    @Test
    fun `getAllBindings should include save in File category`() {
        val bindings = Hotkeys.getAllBindings()
        assertThat(bindings).containsKey("File")
        val fileBindings = bindings["File"]!!
        assertThat(fileBindings).anyMatch { it.first == "Save" && it.second == Hotkeys.save }
    }

    @Test
    fun `getAllBindings should have all expected categories`() {
        val bindings = Hotkeys.getAllBindings()
        assertThat(bindings.keys).containsExactlyInAnyOrder("File", "Edit", "View", "Canvas", "Composer")
    }

    // --- Other hotkey definitions ---

    @Test
    fun `undo should be Ctrl+Z`() {
        assertThat(Hotkeys.undo.key).isEqualTo(Key.Z)
        assertThat(Hotkeys.undo.ctrl).isTrue()
    }

    @Test
    fun `redo should be Ctrl+Y`() {
        assertThat(Hotkeys.redo.key).isEqualTo(Key.Y)
        assertThat(Hotkeys.redo.ctrl).isTrue()
    }

    @Test
    fun `redo alt should be Ctrl+Shift+Z`() {
        assertThat(Hotkeys.redoAlt.key).isEqualTo(Key.Z)
        assertThat(Hotkeys.redoAlt.ctrl).isTrue()
        assertThat(Hotkeys.redoAlt.shift).isTrue()
    }

    @Test
    fun `toggle code should be Ctrl+Shift+C`() {
        assertThat(Hotkeys.toggleCode.key).isEqualTo(Key.C)
        assertThat(Hotkeys.toggleCode.ctrl).isTrue()
        assertThat(Hotkeys.toggleCode.shift).isTrue()
    }

    @Test
    fun `delete should not have modifier`() {
        assertThat(Hotkeys.delete.hasModifier).isFalse()
    }

    @Test
    fun `toggleGrid should be G without modifiers`() {
        assertThat(Hotkeys.toggleGrid.key).isEqualTo(Key.G)
        assertThat(Hotkeys.toggleGrid.hasModifier).isFalse()
    }

    // --- HotkeyBinding display ---

    @Test
    fun `display string for redo alt should be Ctrl+Shift+Z`() {
        assertThat(Hotkeys.redoAlt.displayString()).isEqualTo("Ctrl+Shift+Z")
    }

    @Test
    fun `display string for non-modifier key should be just the key`() {
        assertThat(Hotkeys.toggleGrid.displayString()).isEqualTo("G")
    }

    // --- No duplicate key bindings ---

    @Test
    fun `save should not conflict with other Ctrl bindings`() {
        // Verify save (Ctrl+S) doesn't collide with undo (Ctrl+Z), redo (Ctrl+Y), etc.
        assertThat(Hotkeys.save.key).isNotEqualTo(Hotkeys.undo.key)
        assertThat(Hotkeys.save.key).isNotEqualTo(Hotkeys.redo.key)
        assertThat(Hotkeys.save.key).isNotEqualTo(Hotkeys.resetZoom.key)
    }
}
