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
        assertThat(bindings.keys).containsExactlyInAnyOrder(
            "File", "Edit", "View", "Canvas", "Element", "Selection", "Composer", "Help"
        )
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

    @Test
    fun `toggleSnapGuides should be S without modifiers`() {
        assertThat(Hotkeys.toggleSnapGuides.key).isEqualTo(Key.S)
        assertThat(Hotkeys.toggleSnapGuides.hasModifier).isFalse()
    }

    @Test
    fun `toggleSnapGuides should not conflict with save`() {
        // Save is Ctrl+S, toggleSnapGuides is just S — no conflict
        assertThat(Hotkeys.toggleSnapGuides.ctrl).isFalse()
        assertThat(Hotkeys.save.ctrl).isTrue()
        assertThat(Hotkeys.toggleSnapGuides.key).isEqualTo(Hotkeys.save.key)
        // Same key but different modifiers — no conflict
    }

    @Test
    fun `toggleSnapGuides display string should be S`() {
        assertThat(Hotkeys.toggleSnapGuides.displayString()).isEqualTo("S")
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

    // --- Nudge bindings ---

    @Test
    fun `nudge arrow keys should have no modifiers`() {
        assertThat(Hotkeys.nudgeUp.key).isEqualTo(Key.DirectionUp)
        assertThat(Hotkeys.nudgeUp.hasModifier).isFalse()
        assertThat(Hotkeys.nudgeDown.key).isEqualTo(Key.DirectionDown)
        assertThat(Hotkeys.nudgeLeft.key).isEqualTo(Key.DirectionLeft)
        assertThat(Hotkeys.nudgeRight.key).isEqualTo(Key.DirectionRight)
    }

    @Test
    fun `large nudge should use Shift modifier`() {
        assertThat(Hotkeys.nudgeUpLarge.key).isEqualTo(Key.DirectionUp)
        assertThat(Hotkeys.nudgeUpLarge.shift).isTrue()
        assertThat(Hotkeys.nudgeUpLarge.ctrl).isFalse()
        assertThat(Hotkeys.nudgeUpLarge.alt).isFalse()
    }

    @Test
    fun `nudge display strings should use arrow symbols`() {
        assertThat(Hotkeys.nudgeUp.displayString()).isEqualTo("\u2191")
        assertThat(Hotkeys.nudgeDownLarge.displayString()).isEqualTo("Shift+\u2193")
    }

    // --- Resize bindings ---

    @Test
    fun `resize should use Alt modifier`() {
        assertThat(Hotkeys.resizeRight.key).isEqualTo(Key.DirectionRight)
        assertThat(Hotkeys.resizeRight.alt).isTrue()
        assertThat(Hotkeys.resizeRight.ctrl).isFalse()
        assertThat(Hotkeys.resizeRight.shift).isFalse()
    }

    @Test
    fun `large resize should use Alt+Shift modifiers`() {
        assertThat(Hotkeys.resizeRightLarge.key).isEqualTo(Key.DirectionRight)
        assertThat(Hotkeys.resizeRightLarge.alt).isTrue()
        assertThat(Hotkeys.resizeRightLarge.shift).isTrue()
    }

    @Test
    fun `resize display strings should include Alt`() {
        assertThat(Hotkeys.resizeRight.displayString()).isEqualTo("Alt+\u2192")
        assertThat(Hotkeys.resizeDownLarge.displayString()).isEqualTo("Shift+Alt+\u2193")
    }

    // --- Z-order bindings ---

    @Test
    fun `bring forward should be Ctrl+RightBracket`() {
        assertThat(Hotkeys.bringForward.key).isEqualTo(Key.RightBracket)
        assertThat(Hotkeys.bringForward.ctrl).isTrue()
        assertThat(Hotkeys.bringForward.shift).isFalse()
    }

    @Test
    fun `send backward should be Ctrl+LeftBracket`() {
        assertThat(Hotkeys.sendBackward.key).isEqualTo(Key.LeftBracket)
        assertThat(Hotkeys.sendBackward.ctrl).isTrue()
        assertThat(Hotkeys.sendBackward.shift).isFalse()
    }

    @Test
    fun `bring to front should be Ctrl+Shift+RightBracket`() {
        assertThat(Hotkeys.bringToFront.key).isEqualTo(Key.RightBracket)
        assertThat(Hotkeys.bringToFront.ctrl).isTrue()
        assertThat(Hotkeys.bringToFront.shift).isTrue()
    }

    @Test
    fun `send to back should be Ctrl+Shift+LeftBracket`() {
        assertThat(Hotkeys.sendToBack.key).isEqualTo(Key.LeftBracket)
        assertThat(Hotkeys.sendToBack.ctrl).isTrue()
        assertThat(Hotkeys.sendToBack.shift).isTrue()
    }

    @Test
    fun `z-order display strings should show bracket symbols`() {
        assertThat(Hotkeys.bringForward.displayString()).isEqualTo("Ctrl+]")
        assertThat(Hotkeys.sendBackward.displayString()).isEqualTo("Ctrl+[")
        assertThat(Hotkeys.bringToFront.displayString()).isEqualTo("Ctrl+Shift+]")
        assertThat(Hotkeys.sendToBack.displayString()).isEqualTo("Ctrl+Shift+[")
    }

    // --- Selection cycling bindings ---

    @Test
    fun `select next sibling should be Tab`() {
        assertThat(Hotkeys.selectNextSibling.key).isEqualTo(Key.Tab)
        assertThat(Hotkeys.selectNextSibling.hasModifier).isFalse()
    }

    @Test
    fun `select prev sibling should be Shift+Tab`() {
        assertThat(Hotkeys.selectPrevSibling.key).isEqualTo(Key.Tab)
        assertThat(Hotkeys.selectPrevSibling.shift).isTrue()
    }

    @Test
    fun `tab bindings should be suppressed during text input`() {
        TextInputFocusState.isTextFieldFocused.value = true
        try {
            assertThat(
                Hotkeys.matchesWithFocusCheck(Hotkeys.selectNextSibling, Key.Tab, isCtrlPressed = false, isShiftPressed = false)
            ).isFalse()
        } finally {
            TextInputFocusState.isTextFieldFocused.value = false
        }
    }

    // --- Help bindings ---

    @Test
    fun `toggle hotkey reference should be Ctrl+Slash`() {
        assertThat(Hotkeys.toggleHotkeyReference.key).isEqualTo(Key.Slash)
        assertThat(Hotkeys.toggleHotkeyReference.ctrl).isTrue()
    }

    @Test
    fun `hotkey reference display string should be Ctrl+Slash`() {
        assertThat(Hotkeys.toggleHotkeyReference.displayString()).isEqualTo("Ctrl+/")
    }

    // --- getAllBindings completeness ---

    @Test
    fun `getAllBindings View category should include snap guides`() {
        val bindings = Hotkeys.getAllBindings()
        val viewBindings = bindings["View"]!!
        assertThat(viewBindings).anyMatch { it.first == "Toggle Snap Guides" && it.second == Hotkeys.toggleSnapGuides }
    }

    @Test
    fun `getAllBindings Element category should include nudge and z-order`() {
        val bindings = Hotkeys.getAllBindings()
        val elementBindings = bindings["Element"]!!
        assertThat(elementBindings.map { it.first }).contains(
            "Nudge Up", "Nudge Right", "Nudge Up 10px",
            "Resize Right", "Resize Down 10px",
            "Bring Forward", "Send Backward", "Bring to Front", "Send to Back"
        )
    }

    @Test
    fun `getAllBindings Selection category should include tab cycling`() {
        val bindings = Hotkeys.getAllBindings()
        val selectionBindings = bindings["Selection"]!!
        assertThat(selectionBindings.map { it.first }).containsExactly(
            "Select Next Sibling", "Select Previous Sibling"
        )
    }

    @Test
    fun `getAllBindings Help category should include keyboard shortcuts`() {
        val bindings = Hotkeys.getAllBindings()
        val helpBindings = bindings["Help"]!!
        assertThat(helpBindings).anyMatch { it.first == "Keyboard Shortcuts" }
    }

    // --- No conflicts among new bindings ---

    @Test
    fun `nudge and resize should not conflict due to different modifiers`() {
        // Nudge Up: Arrow Up, no modifier. Resize Up: Arrow Up + Alt.
        assertThat(Hotkeys.nudgeUp.alt).isFalse()
        assertThat(Hotkeys.resizeUp.alt).isTrue()
        assertThat(Hotkeys.nudgeUp.key).isEqualTo(Hotkeys.resizeUp.key) // Same key...
        // ...but different modifiers, so no conflict
        assertThat(Hotkeys.nudgeUp.alt).isNotEqualTo(Hotkeys.resizeUp.alt)
    }

    @Test
    fun `z-order bindings should not conflict with each other`() {
        // Forward vs To Front: same key, different shift
        assertThat(Hotkeys.bringForward.shift).isFalse()
        assertThat(Hotkeys.bringToFront.shift).isTrue()
        // Backward vs To Back: same key, different shift
        assertThat(Hotkeys.sendBackward.shift).isFalse()
        assertThat(Hotkeys.sendToBack.shift).isTrue()
    }
}
