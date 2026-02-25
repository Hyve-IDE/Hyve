// Copyright 2026 Hyve. All rights reserved.
package com.hyve.ui.editor

import androidx.compose.runtime.*
import androidx.compose.runtime.CompositionLocalProvider
import com.hyve.common.compose.HyveComposeFileEditorWithContext
import com.hyve.ui.bridge.VirtualFileBridge
import java.nio.file.Path
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.undo.UndoManager as PlatformUndoManager
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import java.beans.PropertyChangeListener
import java.beans.PropertyChangeSupport

/**
 * IntelliJ FileEditor that provides a visual editor for Hytale .ui files.
 *
 * This editor embeds the HyveUI visual editor using Compose Desktop,
 * leveraging the HyveComposeFileEditorWithContext base class from hyve-common.
 *
 * Features:
 * - Visual canvas for editing UI layouts
 * - Property inspector for element configuration
 * - Undo/redo integration with IDE
 * - Dirty state tracking
 * - Theme integration via HyveTheme
 */
class HyveUIEditor(
    project: Project,
    file: VirtualFile
) : HyveComposeFileEditorWithContext(project, file) {

    private val editorState = HyveUIEditorState(virtualFile)
    private val propertyChangeSupport = PropertyChangeSupport(this)

    init {
        loadDocument()
        listenForDocumentChanges()
    }

    override fun getName(): String = "Hyve UI Editor"

    @Composable
    override fun EditorContent() {
        val deps = remember {
            createDefaultEditorDependencies(
                projectResourcesPath = project.basePath?.let { Path.of(it) }
                    ?.resolve("resources")
                    ?.takeIf { it.toFile().exists() }
            )
        }
        CompositionLocalProvider(LocalEditorDependencies provides deps) {
            HyveUIEditorContent(
                state = editorState,
                project = project,
                file = virtualFile,
                onModified = { markModified() },
                onSave = { saveDocument() }
            )
        }
    }

    /**
     * Load the document content from the file.
     *
     * When the file is empty (e.g. created manually via New File → "name.ui"
     * instead of the Ctrl+Shift+U action), initializes it with a default
     * template so the editor has a proper Group #Root element to work with.
     * Without this, the parser creates a synthetic Root wrapper that the
     * exporter strips on save, causing the root element to vanish.
     */
    private fun loadDocument() {
        editorState.setLoading(true)

        ApplicationManager.getApplication().executeOnPooledThread {
            try {
                val content = VirtualFileBridge.readContentSync(virtualFile)

                if (content.isBlank()) {
                    // Empty file — seed with default template and persist it
                    val template = DEFAULT_UI_TEMPLATE
                    ApplicationManager.getApplication().invokeLater {
                        ApplicationManager.getApplication().runWriteAction {
                            virtualFile.setBinaryContent(template.toByteArray(Charsets.UTF_8))
                        }
                        editorState.setContent(template)
                    }
                } else {
                    ApplicationManager.getApplication().invokeLater {
                        editorState.setContent(content)
                    }
                }
            } catch (e: Exception) {
                ApplicationManager.getApplication().invokeLater {
                    editorState.setError(e.message ?: "Failed to load file")
                }
            }
        }
    }

    /**
     * Listen for changes to the underlying Document (external edits, text editor changes, VCS).
     * When the Document content changes, sync it to the visual editor so the canvas re-parses.
     */
    private fun listenForDocumentChanges() {
        ApplicationManager.getApplication().invokeLater {
            val document = FileDocumentManager.getInstance().getDocument(virtualFile) ?: return@invokeLater
            document.addDocumentListener(object : DocumentListener {
                override fun documentChanged(event: DocumentEvent) {
                    val newContent = event.document.text
                    // Only update if content actually differs (avoids re-parse loops)
                    if (newContent != editorState.content.value) {
                        editorState.setContent(newContent)
                    }
                }
            }, this@HyveUIEditor)
        }
    }

    /**
     * Mark the document as modified and fire property change.
     */
    private fun markModified() {
        val wasModified = editorState.isModified.value
        editorState.markModified()
        if (!wasModified) {
            propertyChangeSupport.firePropertyChange(PROP_MODIFIED, false, true)
        }
    }

    /**
     * Save the document to disk.
     *
     * Uses [invokeLater] to ensure the write happens in a write-safe modality
     * context. Compose's FlushCoroutineDispatcher runs on the EDT but in
     * NON_MODAL state, which TransactionGuard rejects for write actions that
     * trigger document reloads.
     */
    fun saveDocument() {
        val content = editorState.content.value ?: return

        ApplicationManager.getApplication().invokeLater {
            if (!virtualFile.isValid) return@invokeLater
            try {
                ApplicationManager.getApplication().runWriteAction {
                    virtualFile.setBinaryContent(content.toByteArray(Charsets.UTF_8))
                }
                editorState.markSaved()
                propertyChangeSupport.firePropertyChange(PROP_MODIFIED, true, false)
            } catch (e: Exception) {
                com.intellij.notification.Notifications.Bus.notify(
                    com.intellij.notification.Notification(
                        "Hyve UI Editor",
                        "Save Failed",
                        "Failed to save ${virtualFile.name}: ${e.message}",
                        com.intellij.notification.NotificationType.ERROR
                    ),
                    project
                )
            }
        }
    }

    // FileEditor overrides

    override fun isModified(): Boolean = editorState.isModified.value

    override fun isValid(): Boolean = virtualFile.isValid

    override fun addPropertyChangeListener(listener: PropertyChangeListener) {
        propertyChangeSupport.addPropertyChangeListener(listener)
    }

    override fun removePropertyChangeListener(listener: PropertyChangeListener) {
        propertyChangeSupport.removePropertyChangeListener(listener)
    }

    // Undo/Redo support
    fun canUndo(): Boolean = editorState.canUndo.value
    fun canRedo(): Boolean = editorState.canRedo.value

    fun undo() {
        PlatformUndoManager.getInstance(project).undo(this)
    }

    fun redo() {
        PlatformUndoManager.getInstance(project).redo(this)
    }

    companion object {
        private const val PROP_MODIFIED = "modified"

        /**
         * Default template for empty .ui files, matching NewUIFileAction.
         */
        private val DEFAULT_UI_TEMPLATE = """
            Group #Root {
                Anchor: (Left: 0, Top: 0, Width: 100%, Height: 100%);
            }
        """.trimIndent()
    }
}
