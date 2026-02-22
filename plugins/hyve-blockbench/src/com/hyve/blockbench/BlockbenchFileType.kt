package com.hyve.blockbench

import com.intellij.json.JsonLanguage
import com.intellij.openapi.fileTypes.LanguageFileType
import com.intellij.openapi.fileTypes.ex.FileTypeIdentifiableByVirtualFile
import com.intellij.openapi.util.IconLoader
import com.intellij.openapi.vfs.VirtualFile
import javax.swing.Icon

object BlockbenchFileType : LanguageFileType(JsonLanguage.INSTANCE), FileTypeIdentifiableByVirtualFile {

    override fun getName(): String = "Hytale Model"

    override fun getDisplayName(): String = "Hytale Model"

    override fun getDescription(): String = "Hytale model or animation file"

    override fun getDefaultExtension(): String = "blockymodel"

    override fun getIcon(): Icon = IconLoader.getIcon("/icons/model_file.svg", BlockbenchFileType::class.java)

    override fun isMyFileType(file: VirtualFile): Boolean {
        val ext = file.extension?.lowercase() ?: return false
        return ext == "blockymodel" || ext == "blockyanim"
    }
}
