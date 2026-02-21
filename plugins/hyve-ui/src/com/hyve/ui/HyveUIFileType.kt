// Copyright 2026 Hyve. All rights reserved.
package com.hyve.ui

import com.intellij.openapi.fileTypes.LanguageFileType
import com.intellij.openapi.fileTypes.ex.FileTypeIdentifiableByVirtualFile
import com.intellij.openapi.util.IconLoader
import com.intellij.openapi.vfs.VirtualFile
import javax.swing.Icon

/**
 * File type for Hytale .ui files.
 *
 * Registers .ui extension as a custom file type that opens in the
 * visual HyveUI editor by default.
 */
object HyveUIFileType : LanguageFileType(HyveUILanguage.INSTANCE), FileTypeIdentifiableByVirtualFile {

    override fun getName(): String = "Hytale UI"

    override fun getDescription(): String = "Hytale UI file"

    override fun getDefaultExtension(): String = "ui"

    override fun getIcon(): Icon = IconLoader.getIcon("/icons/ui_file.svg", HyveUIFileType::class.java)

    override fun isMyFileType(file: VirtualFile): Boolean {
        return file.extension?.equals("ui", ignoreCase = true) == true
    }
}
