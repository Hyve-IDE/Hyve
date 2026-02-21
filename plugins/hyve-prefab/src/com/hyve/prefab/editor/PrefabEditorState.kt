package com.hyve.prefab.editor

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import com.hyve.common.undo.UndoManager
import com.hyve.common.undo.UndoableCommand
import com.hyve.prefab.domain.EntityId
import com.hyve.prefab.domain.PrefabDocument

/**
 * Observable state for the Prefab Editor.
 *
 * Holds the current document, loading/error state, selection, and filter text.
 * Integrates with [UndoManager] for undo/redo support.
 */
class PrefabEditorState {

    private val _isLoading = mutableStateOf(true)
    val isLoading: State<Boolean> = _isLoading

    private val _document = mutableStateOf<PrefabDocument?>(null)
    val document: State<PrefabDocument?> = _document

    private val _error = mutableStateOf<String?>(null)
    val error: State<String?> = _error

    private val _selectedEntityId = mutableStateOf<EntityId?>(null)
    val selectedEntityId: State<EntityId?> = _selectedEntityId

    private val _filterText = mutableStateOf("")
    val filterText: State<String> = _filterText

    val undoManager = UndoManager<PrefabDocument>()

    val canUndo: State<Boolean> = undoManager.canUndo
    val canRedo: State<Boolean> = undoManager.canRedo
    val isDirty: State<Boolean> = undoManager.isDirty

    fun setLoading(loading: Boolean) {
        _isLoading.value = loading
    }

    fun setDocument(doc: PrefabDocument) {
        _document.value = doc
        _isLoading.value = false
        _error.value = null
        undoManager.clear()
    }

    fun setError(message: String) {
        _error.value = message
        _isLoading.value = false
    }

    fun selectEntity(id: EntityId?) {
        _selectedEntityId.value = id
    }

    fun setFilterText(text: String) {
        _filterText.value = text
    }

    /**
     * Execute a command on the document and update the state.
     */
    fun executeCommand(command: UndoableCommand<PrefabDocument>) {
        val doc = _document.value ?: return
        val newDoc = undoManager.execute(command, doc)
        if (newDoc != null) {
            _document.value = newDoc
        }
    }

    fun undo() {
        val doc = _document.value ?: return
        val newDoc = undoManager.undo(doc)
        if (newDoc != null) {
            _document.value = newDoc
            // If selected entity was removed, deselect
            val selectedId = _selectedEntityId.value
            if (selectedId != null && newDoc.findEntityById(selectedId) == null) {
                _selectedEntityId.value = null
            }
        }
    }

    fun redo() {
        val doc = _document.value ?: return
        val newDoc = undoManager.redo(doc)
        if (newDoc != null) {
            _document.value = newDoc
        }
    }

    fun markSaved() {
        undoManager.markSaved()
    }
}
