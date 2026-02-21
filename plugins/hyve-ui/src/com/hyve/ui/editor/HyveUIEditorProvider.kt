// Copyright 2026 Hyve. All rights reserved.
package com.hyve.ui.editor

import com.hyve.ui.HyveUIFileType
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorPolicy
import com.intellij.openapi.fileEditor.FileEditorProvider
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

/**
 * Provides the HyveUI visual editor for .ui files.
 *
 * This provider creates [HyveUIEditor] instances as the preview component
 * for [HyveUITextEditorWithPreviewProvider]. It is not registered in plugin.xml
 * separately -- only the wrapper provider is registered.
 */
class HyveUIEditorProvider : FileEditorProvider, DumbAware {

    override fun accept(project: Project, file: VirtualFile): Boolean {
        return file.extension?.equals("ui", ignoreCase = true) == true
    }

    override fun createEditor(project: Project, file: VirtualFile): FileEditor {
        return HyveUIEditor(project, file)
    }

    override fun getEditorTypeId(): String = EDITOR_TYPE_ID

    override fun getPolicy(): FileEditorPolicy = FileEditorPolicy.HIDE_DEFAULT_EDITOR

    companion object {
        const val EDITOR_TYPE_ID = "hyve-ui-visual-editor"
    }
}
