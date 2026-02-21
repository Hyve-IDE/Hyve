// Copyright 2026 Hyve. All rights reserved.
package com.hyve.ui.actions

import com.hyve.common.action.HyveFileAction
import com.hyve.ui.editor.HyveUIEditorProvider
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

/**
 * Action to open a .ui file in the visual HyveUI editor.
 *
 * This is useful when the file is currently open in a text editor
 * and the user wants to switch to the visual editor.
 */
class OpenInVisualEditorAction : HyveFileAction(
    "Open in Visual Editor",
    "Open the .ui file in the visual HyveUI editor",
    null
) {
    override fun isApplicable(file: VirtualFile): Boolean {
        return file.extension?.equals("ui", ignoreCase = true) == true
    }

    override fun performAction(project: Project, file: VirtualFile, e: AnActionEvent) {
        // Close existing editors and open with visual editor
        val fileEditorManager = FileEditorManager.getInstance(project)

        // Open the file - the HyveUIEditorProvider will handle creating the visual editor
        fileEditorManager.openFile(file, true)
    }
}
