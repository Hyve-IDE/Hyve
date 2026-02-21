package com.hyve.prefab.editor

import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorPolicy
import com.intellij.openapi.fileEditor.FileEditorProvider
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

/**
 * Provides the Prefab visual editor for .prefab.json files.
 *
 * Used as the preview component by [PrefabTextEditorWithPreviewProvider].
 * Not registered in plugin.xml separately.
 */
class PrefabEditorProvider : FileEditorProvider, DumbAware {

    override fun accept(project: Project, file: VirtualFile): Boolean =
        file.name.endsWith(".prefab.json", ignoreCase = true)

    override fun createEditor(project: Project, file: VirtualFile): FileEditor =
        PrefabEditor(project, file)

    override fun getEditorTypeId(): String = EDITOR_TYPE_ID

    override fun getPolicy(): FileEditorPolicy = FileEditorPolicy.HIDE_DEFAULT_EDITOR

    companion object {
        const val EDITOR_TYPE_ID = "hyve-prefab-visual-editor"
    }
}
