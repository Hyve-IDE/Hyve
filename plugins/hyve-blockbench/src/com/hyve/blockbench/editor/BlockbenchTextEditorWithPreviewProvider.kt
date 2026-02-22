package com.hyve.blockbench.editor

import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.fileEditor.TextEditorWithPreview
import com.intellij.openapi.fileEditor.TextEditorWithPreviewProvider
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

class BlockbenchTextEditorWithPreviewProvider : TextEditorWithPreviewProvider(
    BlockbenchEditorProvider()
), DumbAware {

    override fun createSplitEditor(
        firstEditor: TextEditor,
        secondEditor: FileEditor,
    ): TextEditorWithPreview {
        return TextEditorWithPreview(
            firstEditor,
            secondEditor,
            "Hyve Blockbench Editor",
            TextEditorWithPreview.Layout.SHOW_PREVIEW,
        )
    }

    override fun accept(project: Project, file: VirtualFile): Boolean {
        val ext = file.extension?.lowercase() ?: return false
        return ext == "blockymodel" || ext == "blockyanim"
    }
}
