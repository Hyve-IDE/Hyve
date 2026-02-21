// Copyright 2026 Hyve. All rights reserved.
package com.hyve.ui.actions

import com.hyve.common.action.HyveFileAction
import com.hyve.ui.HyveUIFileType
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

/**
 * Action to open a .ui file in the text editor instead of the visual editor.
 *
 * This allows users to edit the raw .ui file content when needed.
 */
class OpenInTextEditorAction : HyveFileAction(
    "Open in Text Editor",
    "Open the current .ui file in text editor",
    null
) {
    override fun isApplicable(file: VirtualFile): Boolean {
        return file.extension?.equals("ui", ignoreCase = true) == true
    }

    override fun performAction(project: Project, file: VirtualFile, e: AnActionEvent) {
        // Open file with text editor provider explicitly
        val descriptor = OpenFileDescriptor(project, file)
        FileEditorManager.getInstance(project).openTextEditor(descriptor, true)
    }
}
