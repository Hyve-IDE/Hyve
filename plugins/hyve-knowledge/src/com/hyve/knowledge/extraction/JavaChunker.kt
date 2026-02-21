// Copyright 2026 Hyve. All rights reserved.
package com.hyve.knowledge.extraction

import com.github.javaparser.ParseProblemException
import com.github.javaparser.StaticJavaParser
import com.github.javaparser.ast.CompilationUnit
import com.github.javaparser.ast.body.*
import com.github.javaparser.ast.visitor.VoidVisitorAdapter
import com.hyve.knowledge.decompile.DecompilationFixes
import com.intellij.openapi.diagnostic.Logger
import java.io.File
import java.security.MessageDigest

/**
 * Method-level chunker for decompiled Java source files.
 * Uses JavaParser to extract method/constructor bodies with full context.
 *
 * Ported from TypeScript `parser.ts` extractMethods() / parseJavaFile().
 */
object JavaChunker {

    private val log = Logger.getInstance(JavaChunker::class.java)

    /**
     * Parse a single Java file into method-level chunks.
     */
    fun chunkFile(file: File): List<MethodChunk> {
        if (file.name == "package-info.java") return emptyList()

        val source = file.readText()
        val fileHash = sha256(source)
        val fixed = DecompilationFixes.applyAll(source)

        val cu: CompilationUnit = try {
            StaticJavaParser.parse(fixed)
        } catch (e: ParseProblemException) {
            log.debug("Parse error in ${file.name}: ${e.message}")
            return emptyList()
        }

        val packageName = cu.packageDeclaration.map { it.nameAsString }.orElse("")
        val imports = cu.imports.map { it.toString().trim() }

        val chunks = mutableListOf<MethodChunk>()

        cu.accept(object : VoidVisitorAdapter<Void>() {
            override fun visit(n: ClassOrInterfaceDeclaration, arg: Void?) {
                extractFromType(n, packageName, imports, file, fileHash, fixed, chunks)
                super.visit(n, arg)
            }

            override fun visit(n: EnumDeclaration, arg: Void?) {
                extractFromEnum(n, packageName, imports, file, fileHash, fixed, chunks)
                super.visit(n, arg)
            }
        }, null)

        return chunks
    }

    /**
     * Parse all Java files in a directory into chunks.
     * @param pathFilter Optional filter on relative paths (e.g. "com/hypixel/hytale/")
     * @param onProgress Callback for progress updates (currentFile, fileIndex, totalFiles)
     */
    fun chunkDirectory(
        dir: File,
        pathFilter: ((String) -> Boolean)? = null,
        onProgress: ((String, Int, Int) -> Unit)? = null,
    ): List<MethodChunk> {
        val javaFiles = dir.walkTopDown()
            .filter { it.isFile && it.extension == "java" }
            .filter { file ->
                if (pathFilter == null) true
                else pathFilter(file.relativeTo(dir).path.replace('\\', '/'))
            }
            .toList()

        val allChunks = mutableListOf<MethodChunk>()
        for ((idx, file) in javaFiles.withIndex()) {
            onProgress?.invoke(file.name, idx, javaFiles.size)
            try {
                allChunks.addAll(chunkFile(file))
            } catch (e: Exception) {
                log.warn("Failed to chunk ${file.name}: ${e.message}")
            }
        }
        return allChunks
    }

    private fun extractFromType(
        typeDecl: ClassOrInterfaceDeclaration,
        packageName: String,
        imports: List<String>,
        file: File,
        fileHash: String,
        source: String,
        chunks: MutableList<MethodChunk>,
    ) {
        val className = typeDecl.fullyQualifiedName.orElse(
            if (packageName.isNotEmpty()) "$packageName.${typeDecl.nameAsString}"
            else typeDecl.nameAsString
        )

        // Collect field declarations (up to 20) for context
        val fields = typeDecl.fields.take(20).map { field ->
            field.toString().trim().let { s ->
                // Truncate initializers for brevity
                val eqIdx = s.indexOf('=')
                if (eqIdx > 0 && s.length - eqIdx > 50) s.substring(0, eqIdx + 50) + "..." else s
            }
        }

        // Extract methods
        for (method in typeDecl.methods) {
            val chunk = buildMethodChunk(
                className, packageName, method.nameAsString,
                method.declarationAsString, method.toString(),
                method.begin.map { it.line }.orElse(0),
                method.end.map { it.line }.orElse(0),
                imports, fields, file, fileHash,
            )
            chunks.add(chunk)
        }

        // Extract constructors
        for (ctor in typeDecl.constructors) {
            val chunk = buildMethodChunk(
                className, packageName, "<init>",
                ctor.declarationAsString, ctor.toString(),
                ctor.begin.map { it.line }.orElse(0),
                ctor.end.map { it.line }.orElse(0),
                imports, fields, file, fileHash,
            )
            chunks.add(chunk)
        }
    }

    private fun extractFromEnum(
        enumDecl: EnumDeclaration,
        packageName: String,
        imports: List<String>,
        file: File,
        fileHash: String,
        source: String,
        chunks: MutableList<MethodChunk>,
    ) {
        val className = if (packageName.isNotEmpty()) "$packageName.${enumDecl.nameAsString}"
        else enumDecl.nameAsString

        val fields = enumDecl.entries.take(20).map { it.nameAsString }

        for (method in enumDecl.methods) {
            val chunk = buildMethodChunk(
                className, packageName, method.nameAsString,
                method.declarationAsString, method.toString(),
                method.begin.map { it.line }.orElse(0),
                method.end.map { it.line }.orElse(0),
                imports, fields, file, fileHash,
            )
            chunks.add(chunk)
        }

        for (ctor in enumDecl.constructors) {
            val chunk = buildMethodChunk(
                className, packageName, "<init>",
                ctor.declarationAsString, ctor.toString(),
                ctor.begin.map { it.line }.orElse(0),
                ctor.end.map { it.line }.orElse(0),
                imports, fields, file, fileHash,
            )
            chunks.add(chunk)
        }
    }

    private fun buildMethodChunk(
        className: String,
        packageName: String,
        methodName: String,
        signature: String,
        content: String,
        lineStart: Int,
        lineEnd: Int,
        imports: List<String>,
        fields: List<String>,
        file: File,
        fileHash: String,
    ): MethodChunk {
        val simpleClass = className.substringAfterLast('.')
        val id = "$className#$methodName"

        val shortPackage = packageName.removePrefix("com.hypixel.hytale.")

        val embeddingText = buildString {
            appendLine("// Package: $shortPackage")
            appendLine("// Class: $simpleClass")
            if (fields.isNotEmpty()) {
                appendLine("// Fields: ${fields.take(8).joinToString(", ")}")
            }
            appendLine("// Method: $methodName")
            appendLine()
            appendLine(signature)
            appendLine()
            append(content)
        }

        return MethodChunk(
            id = id,
            className = className,
            packageName = packageName,
            methodName = methodName,
            methodSignature = signature,
            content = content,
            filePath = file.path.replace('\\', '/'),
            fileHash = fileHash,
            lineStart = lineStart,
            lineEnd = lineEnd,
            imports = imports,
            fields = fields,
            embeddingText = embeddingText,
        )
    }

    private fun sha256(text: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(text.toByteArray()).joinToString("") { "%02x".format(it) }
    }
}

data class MethodChunk(
    val id: String,
    val className: String,
    val packageName: String,
    val methodName: String,
    val methodSignature: String,
    val content: String,
    val filePath: String,
    val fileHash: String,
    val lineStart: Int,
    val lineEnd: Int,
    val imports: List<String>,
    val fields: List<String>,
    val embeddingText: String,
)
