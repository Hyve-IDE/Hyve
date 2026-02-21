// Copyright 2026 Hyve. All rights reserved.
package com.hyve.ui.bridge

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.readAction
import com.intellij.openapi.application.writeAction
import com.intellij.openapi.vfs.VirtualFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.charset.StandardCharsets

/**
 * Bridge between IntelliJ VirtualFile and file operations.
 *
 * Provides thread-safe read/write operations that integrate properly
 * with IntelliJ's virtual file system.
 */
object VirtualFileBridge {

    /**
     * Read file content synchronously (must be called on appropriate thread).
     */
    fun readContentSync(file: VirtualFile): String {
        return String(file.contentsToByteArray(), StandardCharsets.UTF_8)
    }

    /**
     * Read file content asynchronously using coroutines.
     */
    suspend fun readContent(file: VirtualFile): String = readAction {
        String(file.contentsToByteArray(), StandardCharsets.UTF_8)
    }

    /**
     * Write content to file asynchronously.
     */
    suspend fun writeContent(file: VirtualFile, content: String) = writeAction {
        file.setBinaryContent(content.toByteArray(StandardCharsets.UTF_8))
    }

    /**
     * Write content to file synchronously (must be called from write action).
     */
    fun writeContentSync(file: VirtualFile, content: String) {
        ApplicationManager.getApplication().runWriteAction {
            file.setBinaryContent(content.toByteArray(StandardCharsets.UTF_8))
        }
    }

    /**
     * Get the file's parent directory path.
     */
    fun getParentPath(file: VirtualFile): String? {
        return file.parent?.path
    }

    /**
     * Check if file exists and is valid.
     */
    fun isValid(file: VirtualFile): Boolean {
        return file.isValid && file.exists()
    }
}
