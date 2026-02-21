// Copyright 2026 Hyve. All rights reserved.
package com.hyve.common.util

import com.intellij.openapi.application.readAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.readText
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager

/**
 * Extension functions for VirtualFile to simplify common operations.
 */

/**
 * Checks if this file has one of the specified extensions.
 */
fun VirtualFile.hasExtension(vararg extensions: String): Boolean {
    val ext = extension?.lowercase() ?: return false
    return extensions.any { it.lowercase() == ext }
}

/**
 * Gets the file extension in lowercase.
 */
val VirtualFile.lowercaseExtension: String?
    get() = extension?.lowercase()

/**
 * Checks if this file is a Hyve-related file type.
 */
val VirtualFile.isHyveFile: Boolean
    get() = hasExtension("ui", "blueprint", "hylogic", "hyscript")

/**
 * Checks if this file is a JSON file.
 */
val VirtualFile.isJson: Boolean
    get() = hasExtension("json", "jsonc")

/**
 * Checks if this file is a YAML file.
 */
val VirtualFile.isYaml: Boolean
    get() = hasExtension("yaml", "yml")

/**
 * Checks if this file is an image file.
 */
val VirtualFile.isImage: Boolean
    get() = hasExtension("png", "jpg", "jpeg", "gif", "svg", "webp", "ico")

/**
 * Reads the file content as text.
 * This is a convenience wrapper around VirtualFile.readText().
 * The charset is determined automatically from the file's BOM or configured encoding.
 */
fun VirtualFile.readContent(): String {
    return readText()
}

/**
 * Gets the relative path from the project root, or the full path if not in project.
 */
fun VirtualFile.getRelativePath(project: Project): String {
    val basePath = project.basePath ?: return path
    return if (path.startsWith(basePath)) {
        path.removePrefix(basePath).removePrefix("/").removePrefix("\\")
    } else {
        path
    }
}

/**
 * Gets the PSI file for this virtual file.
 */
suspend fun VirtualFile.toPsiFile(project: Project): PsiFile? {
    return readAction {
        PsiManager.getInstance(project).findFile(this)
    }
}

/**
 * Gets the PSI file for this virtual file (blocking version).
 */
fun VirtualFile.toPsiFileSync(project: Project): PsiFile? {
    return PsiManager.getInstance(project).findFile(this)
}

/**
 * Checks if the file exists and is valid.
 */
val VirtualFile.isValidFile: Boolean
    get() = isValid && !isDirectory

/**
 * Checks if this is a directory and is valid.
 */
val VirtualFile.isValidDirectory: Boolean
    get() = isValid && isDirectory

/**
 * Gets the parent directory name.
 */
val VirtualFile.parentName: String?
    get() = parent?.name

/**
 * Gets the file size in a human-readable format.
 */
val VirtualFile.readableSize: String
    get() {
        val bytes = length
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "${bytes / 1024} KB"
            bytes < 1024 * 1024 * 1024 -> "${bytes / (1024 * 1024)} MB"
            else -> "${bytes / (1024 * 1024 * 1024)} GB"
        }
    }
