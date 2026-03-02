// Copyright 2026 Hyve. All rights reserved.
package com.hyve.knowledge.decompile

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProgressIndicator
import org.jetbrains.java.decompiler.main.decompiler.BaseDecompiler
import org.jetbrains.java.decompiler.main.extern.IFernflowerLogger
import org.jetbrains.java.decompiler.main.extern.IFernflowerPreferences
import java.io.File
import java.security.MessageDigest
import java.time.Instant

/**
 * Stateless decompilation service. Owns FernFlower invocation, post-processing
 * fixes, and JAR staleness detection via decompile_meta.json.
 *
 * Called by both DecompileTask (manual action) and BuildAllIndexAction (auto).
 */
object DecompileService {

    private val log = Logger.getInstance(DecompileService::class.java)
    private const val META_FILE = "decompile_meta.json"

    /**
     * Decompiles [serverJar] into [outputDir] using FernFlower, then applies
     * post-processing fixes. Progress updates are sent to [indicator].
     */
    fun decompile(serverJar: File, outputDir: File, indicator: ProgressIndicator) {
        outputDir.mkdirs()
        log.info("Decompiling ${serverJar.name} -> $outputDir")

        // Phase 1: FernFlower decompilation
        indicator.text = "Decompiling ${serverJar.name}..."
        indicator.isIndeterminate = true

        val provider = JarBytecodeProvider(serverJar)
        val saver = DirectoryResultSaver(outputDir)
        val logger = IndicatorLogger(indicator)

        val options = HashMap<String, Any>(IFernflowerPreferences.DEFAULTS).apply {
            put(IFernflowerPreferences.BYTECODE_SOURCE_MAPPING, "1")
            put(IFernflowerPreferences.DECOMPILE_GENERIC_SIGNATURES, "1")
            put(IFernflowerPreferences.REMOVE_BRIDGE, "1")
            put(IFernflowerPreferences.REMOVE_SYNTHETIC, "1")
            put(IFernflowerPreferences.DECOMPILE_INNER, "1")
            put(IFernflowerPreferences.DECOMPILE_ENUM, "1")
            put(IFernflowerPreferences.USE_DEBUG_VAR_NAMES, "1")
            put(IFernflowerPreferences.LOG_LEVEL, IFernflowerLogger.Severity.INFO.name)
            put(IFernflowerPreferences.INDENT_STRING, "    ")
        }

        val decompiler = BaseDecompiler(provider, saver, options, logger)
        decompiler.addSource(serverJar)

        try {
            decompiler.decompileContext()
        } finally {
            provider.close()
        }

        if (indicator.isCanceled) return

        log.info("FernFlower wrote ${saver.filesWritten} Hytale files, skipped ${saver.filesSkipped} dependency files")

        // Phase 2: Apply decompilation fixes
        indicator.isIndeterminate = false
        indicator.fraction = 0.0
        indicator.text = "Applying decompilation fixes..."

        val javaFiles = outputDir.walkTopDown()
            .filter { it.extension == "java" }
            .toList()

        var fixedCount = 0
        javaFiles.forEachIndexed { idx, file ->
            if (indicator.isCanceled) return
            indicator.text2 = file.name
            indicator.fraction = idx.toDouble() / javaFiles.size.coerceAtLeast(1)
            if (DecompilationFixes.applyToFile(file)) {
                fixedCount++
            }
        }

        log.info("Applied fixes to $fixedCount / ${javaFiles.size} files")
    }

    /**
     * Returns true if [outputDir] needs (re-)decompilation:
     * - outputDir doesn't exist or is empty
     * - decompile_meta.json is missing
     * - stored JAR hash doesn't match current JAR
     */
    fun isStale(serverJar: File, outputDir: File): Boolean {
        if (!outputDir.exists()) return true
        val files = outputDir.listFiles() ?: return true
        if (files.none { it.name != META_FILE }) return true

        val metaFile = File(outputDir, META_FILE)
        if (!metaFile.exists()) return true

        val storedHash = extractHash(metaFile.readText()) ?: return true
        val currentHash = sha256(serverJar)
        return storedHash != currentHash
    }

    /**
     * Writes decompile_meta.json with the SHA-256 hash of [serverJar].
     */
    fun writeDecompileMeta(serverJar: File, outputDir: File) {
        outputDir.mkdirs()
        val hash = sha256(serverJar)
        val meta = """
            |{
            |  "jarHash": "$hash",
            |  "decompiledAt": "${Instant.now()}"
            |}
        """.trimMargin()
        File(outputDir, META_FILE).writeText(meta)
    }

    // ── Internal ─────────────────────────────────────────────────

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(8192)
            var bytesRead: Int
            while (input.read(buffer).also { bytesRead = it } != -1) {
                digest.update(buffer, 0, bytesRead)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun extractHash(metaJson: String): String? =
        Regex("\"jarHash\"\\s*:\\s*\"([^\"]+)\"").find(metaJson)?.groupValues?.get(1)

    /**
     * FernFlower logger that updates the progress indicator.
     */
    private class IndicatorLogger(
        private val indicator: ProgressIndicator,
    ) : IFernflowerLogger() {

        private var currentClass: String = ""

        private fun isHytaleClass(): Boolean =
            currentClass.startsWith("com/hypixel/hytale/")

        override fun writeMessage(message: String, severity: Severity) {
            when (severity) {
                Severity.TRACE -> log.trace(message)
                Severity.INFO -> log.info(message)
                Severity.WARN -> log.warn(message)
                Severity.ERROR -> {
                    if (isHytaleClass()) log.error(message) else log.warn(message)
                }
            }
        }

        override fun writeMessage(message: String, severity: Severity, t: Throwable) {
            if (isHytaleClass()) log.error(message, t) else log.warn(message, t)
        }

        override fun startClass(className: String) {
            currentClass = className
            indicator.text2 = className
        }
    }
}
