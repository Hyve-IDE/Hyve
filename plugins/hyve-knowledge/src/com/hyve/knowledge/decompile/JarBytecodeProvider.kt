// Copyright 2026 Hyve. All rights reserved.
package com.hyve.knowledge.decompile

import org.jetbrains.java.decompiler.main.extern.IBytecodeProvider
import java.io.File
import java.io.IOException
import java.util.jar.JarFile

/**
 * FernFlower IBytecodeProvider that reads .class entries from a JAR file.
 */
class JarBytecodeProvider(private val jarPath: File) : IBytecodeProvider {

    private val jarFile: JarFile by lazy { JarFile(jarPath) }

    @Throws(IOException::class)
    override fun getBytecode(externalPath: String, internalPath: String?): ByteArray {
        if (internalPath == null) {
            // Reading the JAR file itself — not an internal entry
            return jarPath.readBytes()
        }
        val entry = jarFile.getJarEntry(internalPath)
            ?: throw IOException("Entry not found in JAR: $internalPath")
        return jarFile.getInputStream(entry).use { it.readBytes() }
    }

    fun close() {
        try {
            jarFile.close()
        } catch (_: IOException) {
        }
    }
}
