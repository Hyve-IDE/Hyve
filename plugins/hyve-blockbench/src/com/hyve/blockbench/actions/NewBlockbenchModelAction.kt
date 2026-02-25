// Copyright 2026 Hyve. All rights reserved.
package com.hyve.blockbench.actions

import com.hyve.common.action.HyveProjectAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vfs.VirtualFile

/**
 * Action to create a new Blockbench model file (.blockymodel).
 */
class NewBlockbenchModelAction : HyveProjectAction(
    "Blockbench Model",
    "Create a new Blockbench model file",
    null
) {
    override fun performAction(project: Project, e: AnActionEvent) {
        val directory = getTargetDirectory(e, project) ?: run {
            Messages.showErrorDialog(project, "Please select a directory in the project view.", "Cannot Create File")
            return
        }

        val fileName = Messages.showInputDialog(
            project,
            "Enter the name for the new model file:",
            "New Blockbench Model",
            null,
            "new_model",
            null
        ) ?: return

        val fullFileName = if (fileName.endsWith(".blockymodel")) fileName else "$fileName.blockymodel"

        val created = runWriteAction {
            try {
                val file = directory.createChildData(this, fullFileName)
                file.setBinaryContent(DEFAULT_MODEL_TEMPLATE.toByteArray())
                file
            } catch (ex: Exception) {
                Messages.showErrorDialog(project, "Failed to create file: ${ex.message}", "Error Creating File")
                null
            }
        }
        if (created != null) {
            FileEditorManager.getInstance(project).openFile(created, true)
        }
    }

    private fun getTargetDirectory(e: AnActionEvent, project: Project): VirtualFile? {
        val virtualFile = e.getData(CommonDataKeys.VIRTUAL_FILE)
        if (virtualFile != null) {
            return if (virtualFile.isDirectory) virtualFile else virtualFile.parent
        }
        val basePath = project.basePath ?: return null
        return com.intellij.openapi.vfs.LocalFileSystem.getInstance().findFileByPath(basePath)
    }

    companion object {
        private val DEFAULT_MODEL_TEMPLATE = """
            {
              "meta": {
                "format_version": "4.10",
                "model_format": "free",
                "box_uv": false
              },
              "name": "",
              "resolution": {
                "width": 16,
                "height": 16
              },
              "elements": [],
              "outliner": [],
              "textures": []
            }
        """.trimIndent()
    }
}
