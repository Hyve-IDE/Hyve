// Copyright 2026 Hyve. All rights reserved.
package com.hyve.ui.actions

import com.hyve.common.action.HyveProjectAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiManager

/**
 * Action to create a new Hytale UI file.
 *
 * Creates a new .ui file with a basic template and opens it
 * in the HyveUI visual editor.
 */
class NewUIFileAction : HyveProjectAction(
    "New UI File",
    "Create a new Hytale UI file",
    null
) {
    override fun performAction(project: Project, e: AnActionEvent) {
        // Get the directory where the file should be created
        val directory = getTargetDirectory(e, project) ?: run {
            Messages.showErrorDialog(
                project,
                "Please select a directory in the project view.",
                "Cannot Create UI File"
            )
            return
        }

        // Prompt for file name
        val fileName = Messages.showInputDialog(
            project,
            "Enter the name for the new UI file:",
            "New Hytale UI File",
            null,
            "NewUI",
            null
        ) ?: return

        // Ensure .ui extension
        val fullFileName = if (fileName.endsWith(".ui")) fileName else "$fileName.ui"

        // Create the file in a write action, then open it outside the write
        // action so editor initialization can commit PSI in a write-safe context.
        val created = runWriteAction {
            try {
                val file = directory.createChildData(this, fullFileName)
                file.setBinaryContent(DEFAULT_UI_TEMPLATE.toByteArray())
                file
            } catch (ex: Exception) {
                Messages.showErrorDialog(
                    project,
                    "Failed to create file: ${ex.message}",
                    "Error Creating UI File"
                )
                null
            }
        }
        if (created != null) {
            FileEditorManager.getInstance(project).openFile(created, true)
        }
    }

    private fun getTargetDirectory(e: AnActionEvent, project: Project): VirtualFile? {
        // Try to get from current selection
        val virtualFile = e.getData(CommonDataKeys.VIRTUAL_FILE)
        if (virtualFile != null) {
            return if (virtualFile.isDirectory) virtualFile else virtualFile.parent
        }

        // Fall back to project base path
        return project.baseDir
    }

    companion object {
        /**
         * Default template for a new .ui file.
         */
        private val DEFAULT_UI_TEMPLATE = """
            // New Hytale UI File
            // Created with Hyve IDE

            Group #Root {
                Anchor: (Left: 0, Top: 0, Width: 100%, Height: 100%);

                // Add your UI elements here
            }
        """.trimIndent()
    }
}
