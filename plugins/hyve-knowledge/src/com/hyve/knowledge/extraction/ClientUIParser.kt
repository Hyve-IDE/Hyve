// Copyright 2026 Hyve. All rights reserved.
package com.hyve.knowledge.extraction

import java.io.File
import java.security.MessageDigest

enum class ClientUIType(val id: String) {
    XAML("xaml"),
    UI("ui"),
    NODE_SCHEMA("node_schema"),
    CONFIG("config");
}

data class ClientUIChunk(
    val id: String,
    val type: ClientUIType,
    val name: String,
    val filePath: String,
    val relativePath: String,
    val fileHash: String,
    val content: String,
    val category: String?,
    val textForEmbedding: String,
)

data class ClientUIParseResult(
    val chunks: List<ClientUIChunk>,
    val errors: List<String>,
)

object ClientUIParser {

    private val KNOWN_CATEGORIES = setOf(
        "DesignSystem", "InGame", "MainMenu", "Common", "Services",
        "GameLoading", "DevTools", "Editor", "Shared",
    )

    fun getUIType(filePath: String): ClientUIType? {
        val ext = filePath.substringAfterLast('.').lowercase()
        return when {
            ext == "xaml" -> ClientUIType.XAML
            ext == "ui" -> ClientUIType.UI
            ext == "json" && filePath.contains("NodeEditor") -> ClientUIType.NODE_SCHEMA
            ext == "json" -> ClientUIType.CONFIG
            else -> null
        }
    }

    fun extractCategory(relativePath: String): String? {
        val parts = relativePath.replace('\\', '/').split('/')
        for (part in parts) {
            if (part in KNOWN_CATEGORIES) return part
        }
        return parts.firstOrNull()
    }

    /** Max content chars to include in embedding text. XML/XAML tokenizes ~3-4x denser than prose. */
    private const val MAX_CONTENT_CHARS = 1200

    /**
     * Derive a human-readable purpose from PascalCase filename splitting.
     * "HotbarSlot" → "hotbar slot", "MainMenu" → "main menu"
     */
    fun derivePurpose(name: String): String? {
        val words = name.replace(Regex("([a-z])([A-Z])"), "$1 $2")
            .replace(Regex("([A-Z]+)([A-Z][a-z])"), "$1 $2")
            .split(' ')
            .filter { it.isNotBlank() }
        return if (words.size >= 2) words.joinToString(" ") { it.lowercase() } else null
    }

    fun buildEmbeddingText(chunk: ClientUIChunk): String {
        val content = chunk.content.take(MAX_CONTENT_CHARS)
        val purpose = derivePurpose(chunk.name)
        val purposeLine = if (purpose != null) "Purpose: $purpose\n" else ""
        return when (chunk.type) {
            ClientUIType.XAML ->
                "XAML UI Template: ${chunk.name}\n${purposeLine}Category: ${chunk.category ?: "General"}\nPath: ${chunk.relativePath}\n\nThis is a Noesis GUI XAML template that defines UI styling and layout.\n\n$content"
            ClientUIType.UI ->
                "Hytale UI Component: ${chunk.name}\n${purposeLine}Category: ${chunk.category ?: "General"}\nPath: ${chunk.relativePath}\n\nThis is a Hytale .ui file that defines UI component layout and behavior.\n\n$content"
            ClientUIType.NODE_SCHEMA ->
                "NodeEditor Definition: ${chunk.name}\n${purposeLine}Path: ${chunk.relativePath}\n\nThis defines a visual scripting node for the Hytale NodeEditor.\n\n$content"
            ClientUIType.CONFIG ->
                "Client Config: ${chunk.name}\n${purposeLine}Category: ${chunk.category ?: "General"}\nPath: ${chunk.relativePath}\n\n$content"
        }
    }

    fun parseClientData(
        clientDataPath: File,
        onProgress: ((current: Int, total: Int, file: String) -> Unit)? = null,
    ): ClientUIParseResult {
        val chunks = mutableListOf<ClientUIChunk>()
        val errors = mutableListOf<String>()

        val files = clientDataPath.walkTopDown()
            .filter { it.isFile && it.extension.lowercase() in setOf("xaml", "ui", "json") }
            .toList()

        for ((idx, file) in files.withIndex()) {
            onProgress?.invoke(idx + 1, files.size, file.name)
            try {
                val type = getUIType(file.absolutePath) ?: continue
                val content = file.readText(Charsets.UTF_8)
                if (content.isBlank()) continue

                val relativePath = file.relativeTo(clientDataPath).path.replace('\\', '/')
                val name = file.nameWithoutExtension
                val category = extractCategory(relativePath)
                val hash = computeSHA256(content.toByteArray())

                val chunk = ClientUIChunk(
                    id = "${type.id}:$relativePath",
                    type = type,
                    name = name,
                    filePath = file.absolutePath,
                    relativePath = relativePath,
                    fileHash = hash,
                    content = content,
                    category = category,
                    textForEmbedding = "",
                )
                chunks.add(chunk.copy(textForEmbedding = buildEmbeddingText(chunk)))
            } catch (e: Exception) {
                errors.add("${file.absolutePath}: ${e.message}")
            }
        }
        return ClientUIParseResult(chunks, errors)
    }

    private fun computeSHA256(data: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(data).joinToString("") { "%02x".format(it) }
    }
}
