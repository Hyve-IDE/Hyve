// Copyright 2026 Hyve. All rights reserved.
package com.hyve.ui.editor

import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.fileEditor.TextEditorWithPreview
import com.intellij.openapi.fileEditor.TextEditorWithPreviewProvider
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

/**
 * Provider for .ui file editor with text/visual toggle via TextEditorWithPreview.
 *
 * Creates a TextEditorWithPreview that wraps:
 * - Text editor (standard IntelliJ text editor for .ui files)
 * - Preview editor (HyveUIEditor visual editor via [HyveUIEditorProvider])
 *
 * The toolbar provides 3-mode toggle: text-only, split, visual-only.
 * Defaults to visual-only (SHOW_PREVIEW).
 */
class HyveUITextEditorWithPreviewProvider : TextEditorWithPreviewProvider(
    HyveUIEditorProvider()
), DumbAware {

    override fun createSplitEditor(
        firstEditor: TextEditor,
        secondEditor: FileEditor
    ): TextEditorWithPreview {
        return TextEditorWithPreview(
            firstEditor,
            secondEditor,
            "Hyve UI Editor",
            TextEditorWithPreview.Layout.SHOW_PREVIEW
        )
    }

    override fun accept(project: Project, file: VirtualFile): Boolean {
        return file.extension?.equals("ui", ignoreCase = true) == true
    }
}
