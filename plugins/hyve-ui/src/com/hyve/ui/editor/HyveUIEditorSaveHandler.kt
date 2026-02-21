// Copyright 2026 Hyve. All rights reserved.
package com.hyve.ui.editor

import com.intellij.openapi.fileEditor.FileDocumentManagerListener
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.TextEditorWithPreview
import com.intellij.openapi.project.ProjectManager

/**
 * Hooks into IntelliJ's "Save All" (Ctrl+S) to save modified HyveUIEditors.
 *
 * Custom FileEditors that are not backed by an IntelliJ [com.intellij.openapi.editor.Document]
 * are invisible to the default save mechanism. This listener bridges that gap by finding
 * all open [HyveUIEditor] instances and calling [HyveUIEditor.saveDocument] when IntelliJ
 * triggers a save-all operation.
 *
 * Handles both direct HyveUIEditor instances and those wrapped inside
 * [TextEditorWithPreview] by [HyveUITextEditorWithPreviewProvider].
 */
class HyveUIEditorSaveHandler : FileDocumentManagerListener {

    override fun beforeAllDocumentsSaving() {
        for (project in ProjectManager.getInstance().openProjects) {
            if (project.isDisposed) continue
            val fem = FileEditorManager.getInstance(project)
            for (editor in fem.allEditors) {
                val hyveEditor = when {
                    editor is HyveUIEditor -> editor
                    editor is TextEditorWithPreview && editor.previewEditor is HyveUIEditor ->
                        editor.previewEditor as HyveUIEditor
                    else -> null
                }

                if (hyveEditor != null && hyveEditor.isModified) {
                    hyveEditor.saveDocument()
                }
            }
        }
    }
}
