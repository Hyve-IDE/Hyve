// Copyright 2026 Hyve. All rights reserved.
package com.hyve.common.compose

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorLocation
import com.intellij.openapi.fileEditor.FileEditorState
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.UserDataHolderBase
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.jewel.bridge.JewelComposePanel
import org.jetbrains.jewel.foundation.ExperimentalJewelApi
import org.jetbrains.jewel.foundation.enableNewSwingCompositing
import com.intellij.openapi.diagnostic.Logger
import java.beans.PropertyChangeListener
import javax.swing.JComponent

/**
 * Base class for Hyve file editors that use Compose for their UI.
 *
 * This provides a simplified API for creating custom file editors with:
 * - Automatic theme bridging via HyveTheme
 * - Proper lifecycle management
 * - Access to Hyve color palette
 *
 * Usage:
 * ```kotlin
 * class MyFileEditor(
 *     private val project: Project,
 *     private val file: VirtualFile,
 * ) : HyveComposeFileEditor() {
 *
 *     override fun getName(): String = "My Editor"
 *
 *     @Composable
 *     override fun EditorContent() {
 *         Column {
 *             Text("Editing: ${file.name}")
 *             // Your editor UI here
 *         }
 *     }
 * }
 * ```
 *
 * Then create a FileEditorProvider:
 * ```kotlin
 * class MyEditorProvider : FileEditorProvider {
 *     override fun accept(project: Project, file: VirtualFile): Boolean {
 *         return file.extension == "myext"
 *     }
 *
 *     override fun createEditor(project: Project, file: VirtualFile): FileEditor {
 *         return MyFileEditor(project, file)
 *     }
 *
 *     override fun getEditorTypeId(): String = "my-editor"
 *     override fun getPolicy(): FileEditorPolicy = FileEditorPolicy.PLACE_BEFORE_DEFAULT_EDITOR
 * }
 * ```
 */
@OptIn(ExperimentalJewelApi::class)
abstract class HyveComposeFileEditor : UserDataHolderBase(), FileEditor {

    private var composePanel: JComponent? = null

    /**
     * Creates the Compose panel lazily to ensure proper initialization.
     * Falls back to a Swing error panel if Compose/Jewel init fails.
     */
    private fun getOrCreateComposePanel(): JComponent {
        return composePanel ?: try {
            enableNewSwingCompositing()
            JewelComposePanel(focusOnClickInside = true) {
                HyveTheme {
                    EditorContent()
                }
            }.also { composePanel = it }
        } catch (e: Throwable) {
            LOG.error("Failed to create Compose editor panel", e)
            createErrorFallbackPanel(e).also { composePanel = it }
        }
    }

    private fun createErrorFallbackPanel(error: Throwable): JComponent {
        val panel = javax.swing.JPanel(java.awt.BorderLayout())
        panel.background = java.awt.Color(43, 43, 43)

        val message = buildString {
            appendLine("Failed to initialize visual editor")
            appendLine()
            appendLine("${error.javaClass.simpleName}: ${error.message}")
            appendLine()
            appendLine("Check Help > Show Log in Explorer for full details.")
            appendLine()
            appendLine("Stack trace:")
            val sw = java.io.StringWriter()
            error.printStackTrace(java.io.PrintWriter(sw))
            append(sw.toString().take(2000))
        }

        val textArea = javax.swing.JTextArea(message).apply {
            isEditable = false
            foreground = java.awt.Color(187, 187, 187)
            background = java.awt.Color(43, 43, 43)
            font = java.awt.Font(java.awt.Font.MONOSPACED, java.awt.Font.PLAIN, 12)
            margin = java.awt.Insets(16, 16, 16, 16)
        }
        panel.add(javax.swing.JScrollPane(textArea), java.awt.BorderLayout.CENTER)
        return panel
    }

    /**
     * Override this to provide your editor content.
     * The content is wrapped in HyveTheme, so you can access:
     * - Jewel theme colors via JewelTheme
     * - Hyve colors via HyveThemeColors.colors
     */
    @Composable
    protected abstract fun EditorContent()

    // FileEditor implementation

    override fun getComponent(): JComponent = getOrCreateComposePanel()

    override fun getPreferredFocusedComponent(): JComponent = getOrCreateComposePanel()

    override fun isModified(): Boolean = false

    override fun isValid(): Boolean = true

    override fun addPropertyChangeListener(listener: PropertyChangeListener) {
        // Subclasses can override to support property change events
    }

    override fun removePropertyChangeListener(listener: PropertyChangeListener) {
        // Subclasses can override to support property change events
    }

    override fun getCurrentLocation(): FileEditorLocation? = null

    override fun setState(state: FileEditorState) {
        // Subclasses can override to restore state
    }

    override fun dispose() {
        composePanel = null
    }

    companion object {
        private val LOG = Logger.getInstance(HyveComposeFileEditor::class.java)
    }
}

/**
 * Extended file editor that provides access to project and file.
 */
@OptIn(ExperimentalJewelApi::class)
abstract class HyveComposeFileEditorWithContext(
    protected val project: Project,
    protected val virtualFile: VirtualFile,
) : HyveComposeFileEditor() {

    /**
     * Returns the file being edited.
     */
    override fun getFile(): VirtualFile = virtualFile

    /**
     * Default name based on file name.
     * Override to customize.
     */
    override fun getName(): String = virtualFile.nameWithoutExtension
}
