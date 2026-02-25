// Copyright 2026 Hyve. All rights reserved.
package com.hyve.knowledge.docs.ui

import com.hyve.knowledge.settings.KnowledgeSettings
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.EditorNotificationProvider
import java.util.function.Function
import javax.swing.JComponent

/**
 * Makes offline docs files read-only to prevent accidental edits.
 * No visible banner — the file is already shown in preview-only mode.
 */
class DocsEditorNotificationProvider : EditorNotificationProvider {

    override fun collectNotificationData(
        project: Project,
        file: VirtualFile
    ): Function<in FileEditor, out JComponent?>? {
        val docsCachePath = KnowledgeSettings.getInstance()
            .resolvedOfflineDocsPath().absolutePath.replace('\\', '/')

        val filePath = file.path.replace('\\', '/')
        if (!filePath.startsWith(docsCachePath)) return null

        // Make the file read-only
        if (file.isWritable) {
            try { file.isWritable = false } catch (_: Exception) {}
        }

        // No visible banner
        return null
    }
}
