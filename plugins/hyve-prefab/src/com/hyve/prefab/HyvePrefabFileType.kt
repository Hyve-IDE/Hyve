package com.hyve.prefab

import com.intellij.json.JsonLanguage
import com.intellij.openapi.fileTypes.LanguageFileType
import com.intellij.openapi.fileTypes.ex.FileTypeIdentifiableByVirtualFile
import com.intellij.openapi.util.IconLoader
import com.intellij.openapi.vfs.VirtualFile
import javax.swing.Icon

/**
 * File type for Hytale .prefab.json files.
 *
 * Uses JSON language for syntax highlighting in the text tab.
 * Matches files ending in .prefab.json via [isMyFileType].
 */
object HyvePrefabFileType : LanguageFileType(JsonLanguage.INSTANCE), FileTypeIdentifiableByVirtualFile {

    override fun getName(): String = "Hytale Prefab"

    override fun getDescription(): String = "Hytale prefab file"

    override fun getDefaultExtension(): String = "prefab.json"

    override fun getIcon(): Icon = IconLoader.getIcon("/icons/prefab_file.svg", HyvePrefabFileType::class.java)

    override fun isMyFileType(file: VirtualFile): Boolean {
        return file.name.endsWith(".prefab.json", ignoreCase = true)
    }
}
