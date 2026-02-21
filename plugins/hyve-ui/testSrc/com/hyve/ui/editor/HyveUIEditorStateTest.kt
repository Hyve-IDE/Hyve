// Copyright 2026 Hyve. All rights reserved.
package com.hyve.ui.editor

import com.intellij.testFramework.LightVirtualFile
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

class HyveUIEditorStateTest {

    private fun createState(): HyveUIEditorState {
        return HyveUIEditorState(LightVirtualFile("test.ui", ""))
    }

    // --- Initial state ---

    @Test
    fun `should start in loading state`() {
        val state = createState()
        assertThat(state.isLoading.value).isTrue()
        assertThat(state.content.value).isNull()
        assertThat(state.error.value).isNull()
        assertThat(state.isModified.value).isFalse()
    }

    // --- setContent ---

    @Test
    fun `setContent should clear loading and error`() {
        val state = createState()
        state.setError("something went wrong")

        state.setContent("Group { }")

        assertThat(state.content.value).isEqualTo("Group { }")
        assertThat(state.isLoading.value).isFalse()
        assertThat(state.error.value).isNull()
    }

    @Test
    fun `setContent should not mark as modified`() {
        val state = createState()
        state.setContent("Group { }")
        assertThat(state.isModified.value).isFalse()
    }

    // --- updateContent ---

    @Test
    fun `updateContent should update content and mark modified`() {
        val state = createState()
        state.setContent("Group { }")

        state.updateContent("Group #NewId { }")

        assertThat(state.content.value).isEqualTo("Group #NewId { }")
        assertThat(state.isModified.value).isTrue()
    }

    // --- Dirty tracking (modify/save cycle) ---

    @Test
    fun `markModified should set modified flag`() {
        val state = createState()
        assertThat(state.isModified.value).isFalse()

        state.markModified()
        assertThat(state.isModified.value).isTrue()
    }

    @Test
    fun `markSaved should clear modified flag`() {
        val state = createState()
        state.markModified()
        assertThat(state.isModified.value).isTrue()

        state.markSaved()
        assertThat(state.isModified.value).isFalse()
    }

    @Test
    fun `full edit-save cycle should reset modified`() {
        val state = createState()
        state.setContent("Group { }")

        // Edit
        state.updateContent("Group #Edited { }")
        assertThat(state.isModified.value).isTrue()

        // Save
        state.markSaved()
        assertThat(state.isModified.value).isFalse()

        // Edit again
        state.updateContent("Group #EditedAgain { }")
        assertThat(state.isModified.value).isTrue()
    }

    @Test
    fun `multiple edits without save should stay modified`() {
        val state = createState()
        state.setContent("Group { }")

        state.updateContent("edit 1")
        state.updateContent("edit 2")
        state.updateContent("edit 3")

        assertThat(state.isModified.value).isTrue()
        assertThat(state.content.value).isEqualTo("edit 3")
    }

    // --- Error state ---

    @Test
    fun `setError should clear loading`() {
        val state = createState()
        assertThat(state.isLoading.value).isTrue()

        state.setError("Parse error at line 5")

        assertThat(state.error.value).isEqualTo("Parse error at line 5")
        assertThat(state.isLoading.value).isFalse()
    }

    @Test
    fun `clearError should remove error`() {
        val state = createState()
        state.setError("some error")
        assertThat(state.error.value).isNotNull()

        state.clearError()
        assertThat(state.error.value).isNull()
    }

    @Test
    fun `setContent after error should clear error`() {
        val state = createState()
        state.setError("parse failed")

        state.setContent("Group { }")

        assertThat(state.error.value).isNull()
        assertThat(state.content.value).isEqualTo("Group { }")
    }

    // --- Loading state ---

    @Test
    fun `setLoading should update loading flag`() {
        val state = createState()
        assertThat(state.isLoading.value).isTrue()

        state.setLoading(false)
        assertThat(state.isLoading.value).isFalse()

        state.setLoading(true)
        assertThat(state.isLoading.value).isTrue()
    }

    // --- Undo/redo state ---

    @Test
    fun `undo redo should start as unavailable`() {
        val state = createState()
        assertThat(state.canUndo.value).isFalse()
        assertThat(state.canRedo.value).isFalse()
    }

    @Test
    fun `updateUndoRedoState should set flags`() {
        val state = createState()
        state.updateUndoRedoState(canUndo = true, canRedo = false)
        assertThat(state.canUndo.value).isTrue()
        assertThat(state.canRedo.value).isFalse()

        state.updateUndoRedoState(canUndo = true, canRedo = true)
        assertThat(state.canUndo.value).isTrue()
        assertThat(state.canRedo.value).isTrue()
    }
}
