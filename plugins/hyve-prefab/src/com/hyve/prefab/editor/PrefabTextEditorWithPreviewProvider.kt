package com.hyve.prefab.editor

import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.fileEditor.TextEditorWithPreview
import com.intellij.openapi.fileEditor.TextEditorWithPreviewProvider
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

/**
 * Provider for .prefab.json file editor with text/visual toggle.
 *
 * Creates a TextEditorWithPreview that wraps:
 * - Text editor (standard JSON editor for raw editing)
 * - Preview editor (PrefabEditor visual editor via [PrefabEditorProvider])
 *
 * Defaults to visual-only (SHOW_PREVIEW).
 */
class PrefabTextEditorWithPreviewProvider : TextEditorWithPreviewProvider(
    PrefabEditorProvider()
), DumbAware {

    override fun createSplitEditor(
        firstEditor: TextEditor,
        secondEditor: FileEditor,
    ): TextEditorWithPreview {
        return TextEditorWithPreview(
            firstEditor,
            secondEditor,
            "Hyve Prefab Editor",
            TextEditorWithPreview.Layout.SHOW_PREVIEW,
        )
    }

    override fun accept(project: Project, file: VirtualFile): Boolean =
        file.name.endsWith(".prefab.json", ignoreCase = true)
}
