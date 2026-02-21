// Copyright 2026 Hyve. All rights reserved.
package com.hyve.knowledge.decompile

import org.jetbrains.java.decompiler.main.extern.IResultSaver
import java.io.File
import java.util.jar.Manifest

/**
 * FernFlower IResultSaver that writes decompiled .java files to a directory,
 * organized by package path. Only saves Hytale game code (com/hypixel/hytale/),
 * skipping third-party dependencies. Archive operations are no-ops since we
 * extract directly, not into a JAR.
 */
class DirectoryResultSaver(private val outputDir: File) : IResultSaver {

    @Volatile
    var filesWritten: Int = 0
        private set

    @Volatile
    var filesSkipped: Int = 0
        private set

    private fun isHytalePath(path: String): Boolean =
        path.startsWith("com/hypixel/hytale/") || path.startsWith("com\\hypixel\\hytale\\")

    override fun saveFolder(path: String) {
        if (isHytalePath(path)) {
            File(outputDir, path).mkdirs()
        }
    }

    override fun copyFile(source: String, path: String, entryName: String) {
        // Not needed for JAR decompilation
    }

    override fun saveClassFile(
        path: String,
        qualifiedName: String,
        entryName: String,
        content: String,
        mapping: IntArray?,
    ) {
        if (content.isBlank()) return
        if (!isHytalePath(path)) {
            filesSkipped++
            return
        }
        val outFile = File(outputDir, "$path/$entryName")
        outFile.parentFile?.mkdirs()
        outFile.writeText(content)
        filesWritten++
    }

    // Archive operations — no-ops since we write individual files
    override fun createArchive(path: String, archiveName: String, manifest: Manifest?) {}
    override fun saveDirEntry(path: String, archiveName: String, entryName: String) {}
    override fun copyEntry(source: String, path: String, archiveName: String, entry: String) {}
    override fun saveClassEntry(
        path: String,
        archiveName: String,
        qualifiedName: String,
        entryName: String,
        content: String,
    ) {
        // FernFlower calls this for JAR entries. Here `path` is the archive's parent
        // directory, NOT the package path. Use `qualifiedName` for the Hytale filter
        // and output directory structure.
        if (content.isBlank()) return
        val packageDir = qualifiedName.substringBeforeLast('/', "")
        if (!isHytalePath(packageDir)) {
            filesSkipped++
            return
        }
        val outFile = File(outputDir, "$packageDir/$entryName")
        outFile.parentFile?.mkdirs()
        outFile.writeText(content)
        filesWritten++
    }

    override fun closeArchive(path: String, archiveName: String) {}
}
