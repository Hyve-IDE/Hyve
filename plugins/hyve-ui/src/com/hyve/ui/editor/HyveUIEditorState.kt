// Copyright 2026 Hyve. All rights reserved.
package com.hyve.ui.editor

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import com.intellij.openapi.vfs.VirtualFile

/**
 * Manages the state of a HyveUI editor instance.
 *
 * This state class holds the parsed UI document and manages:
 * - Loading state
 * - Error state
 * - Dirty tracking (via UndoManager integration)
 * - Undo/redo history
 */
class HyveUIEditorState(
    val file: VirtualFile
) {
    // Raw file content - loaded from VirtualFile
    private val _content = mutableStateOf<String?>(null)
    val content: State<String?> = _content

    // Loading state
    private val _isLoading = mutableStateOf(true)
    val isLoading: State<Boolean> = _isLoading

    // Error state (parse errors, etc.)
    private val _error = mutableStateOf<String?>(null)
    val error: State<String?> = _error

    // Whether the document has been modified since last save
    private val _isModified = mutableStateOf(false)
    val isModified: State<Boolean> = _isModified

    // Undo/redo state
    private val _canUndo = mutableStateOf(false)
    val canUndo: State<Boolean> = _canUndo

    private val _canRedo = mutableStateOf(false)
    val canRedo: State<Boolean> = _canRedo

    /**
     * Set the raw file content after loading.
     */
    fun setContent(content: String) {
        _content.value = content
        _error.value = null
        _isLoading.value = false
    }

    /**
     * Update content (marks as modified).
     */
    fun updateContent(content: String) {
        _content.value = content
        _isModified.value = true
    }

    /**
     * Set loading state.
     */
    fun setLoading(loading: Boolean) {
        _isLoading.value = loading
    }

    /**
     * Set error state.
     */
    fun setError(error: String) {
        _error.value = error
        _isLoading.value = false
    }

    /**
     * Clear error state.
     */
    fun clearError() {
        _error.value = null
    }

    /**
     * Mark the document as saved (clears modified flag).
     */
    fun markSaved() {
        _isModified.value = false
    }

    /**
     * Mark the document as modified.
     */
    fun markModified() {
        _isModified.value = true
    }

    /**
     * Update undo/redo availability.
     */
    fun updateUndoRedoState(canUndo: Boolean, canRedo: Boolean) {
        _canUndo.value = canUndo
        _canRedo.value = canRedo
    }
}
