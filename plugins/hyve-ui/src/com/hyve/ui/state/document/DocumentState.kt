package com.hyve.ui.state.document

import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import com.hyve.ui.core.domain.UIDocument
import com.hyve.ui.core.domain.elements.UIElement
import com.hyve.common.undo.UndoManager
import com.hyve.ui.state.command.DocumentCommand
import java.io.File
import java.util.UUID

/**
 * Represents the state of an open document in the editor.
 *
 * Holds the UIDocument along with editor-specific metadata:
 * - File path (null for new unsaved documents)
 * - Dirty flag (has unsaved changes)
 * - Undo/redo history
 * - Save point tracking (for dirty detection)
 *
 * This is a mutable state container that follows Compose state management patterns.
 */
class DocumentState private constructor(
    initialDocument: UIDocument,
    val filePath: File?,
    private val customName: String? = null,
    val undoManager: UndoManager<UIElement> = UndoManager(),
    /** Warnings from parsing this document (e.g., undefined variables, unresolved imports) */
    val parseWarnings: List<String> = emptyList()
) {
    /** Unique identifier for this document state (for tab management) */
    val id: String = UUID.randomUUID().toString()

    private val _document = mutableStateOf(initialDocument)
    val document: State<UIDocument> = _document

    private val _isDirty = mutableStateOf(false)
    val isDirty: State<Boolean> = _isDirty

    /** Tracks the undo stack size at last save, for dirty detection */
    private var savePointUndoCount: Int = undoManager.undoCount()

    /**
     * Get the display name for this document.
     * Uses filename if saved, custom name if provided, or "Untitled" for new documents.
     */
    val displayName: String
        get() = filePath?.name ?: customName ?: "Untitled"

    /**
     * Get the root element of the document.
     */
    val rootElement: UIElement
        get() = _document.value.root

    /**
     * Update the document's root element.
     * This should be called after executing commands that modify the tree.
     */
    fun updateRoot(newRoot: UIElement) {
        _document.value = _document.value.updateRoot(newRoot)
        updateDirtyState()
    }

    /**
     * Execute a command with undo support.
     * @param command The command to execute
     * @param allowMerge Whether to allow merging with previous command
     * @return The new root element if successful, null otherwise
     */
    fun executeCommand(command: DocumentCommand, allowMerge: Boolean = true): UIElement? {
        val root = _document.value.root
        val newRoot = undoManager.execute(command, root, allowMerge)
        if (newRoot != null) {
            _document.value = _document.value.updateRoot(newRoot)
            updateDirtyState()
        }
        return newRoot
    }

    /**
     * Undo the last command.
     * @return The new root element if successful, null otherwise
     */
    fun undo(): UIElement? {
        val root = _document.value.root
        val newRoot = undoManager.undo(root)
        if (newRoot != null) {
            _document.value = _document.value.updateRoot(newRoot)
            updateDirtyState()
        }
        return newRoot
    }

    /**
     * Redo the last undone command.
     * @return The new root element if successful, null otherwise
     */
    fun redo(): UIElement? {
        val root = _document.value.root
        val newRoot = undoManager.redo(root)
        if (newRoot != null) {
            _document.value = _document.value.updateRoot(newRoot)
            updateDirtyState()
        }
        return newRoot
    }

    /**
     * Mark the document as saved at the current state.
     * This updates the save point for dirty detection.
     */
    fun markSaved() {
        savePointUndoCount = undoManager.undoCount()
        updateDirtyState()
    }

    /**
     * Update the dirty state based on whether we're at the save point.
     * A document is dirty if the undo stack differs from the save point,
     * or if there are pending changes in the redo stack that were undone
     * past the save point.
     */
    private fun updateDirtyState() {
        // If undo count equals save point, we're clean
        // Otherwise, we have changes since last save
        _isDirty.value = undoManager.undoCount() != savePointUndoCount
    }

    /**
     * Replace the entire document (e.g., after reload).
     * This clears the undo history.
     */
    fun replaceDocument(newDocument: UIDocument) {
        _document.value = newDocument
        undoManager.clear()
        savePointUndoCount = 0
        _isDirty.value = false
    }

    companion object {
        /**
         * Create a new DocumentState for an existing file.
         *
         * @param document The parsed UIDocument
         * @param file The source file
         * @param parseWarnings Warnings from parsing (optional)
         */
        fun fromFile(document: UIDocument, file: File, parseWarnings: List<String> = emptyList()): DocumentState {
            return DocumentState(
                initialDocument = document,
                filePath = file,
                parseWarnings = parseWarnings
            )
        }

        /**
         * Create a new DocumentState for an unsaved document.
         *
         * @param document The UIDocument (typically empty or from template)
         * @param name The display name for the new document
         */
        fun newDocument(document: UIDocument = UIDocument.empty(), name: String = "Untitled"): DocumentState {
            return DocumentState(
                initialDocument = document,
                filePath = null,
                customName = name
            ).also {
                // New documents start dirty since they've never been saved
                it._isDirty.value = true
            }
        }
    }
}

/**
 * Types of UI documents that can be created.
 */
enum class DocumentType(val displayName: String, val description: String) {
    /** Full-screen page UI (replaces game view) */
    PAGE("Page", "Full-screen UI that replaces the game view when open"),

    /** HUD overlay UI (shown over game view) */
    HUD("HUD", "Overlay UI displayed over the game view")
}
