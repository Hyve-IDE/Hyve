// Copyright 2026 Hyve. All rights reserved.
package com.hyve.ui.services.localization

import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path

/**
 * Parser for Hytale .lang files.
 *
 * Per UI_EDITOR_KNOWLEDGE.md, .lang files are plain text key-value pairs:
 * ```
 * # Comments start with #
 * simple_key = Simple value
 * quoted_key = "Value with spaces or special chars"
 * multiline_key = "This continues \
 * on the next line"
 * escape_test = "Newline: \\n Tab: \\t"
 * ```
 */
object LangFileParser {

    /**
     * Parse a .lang file from a Path.
     *
     * @param path Path to the .lang file
     * @return Map of key to value
     * @throws LangParseException if the file has syntax errors
     */
    fun parse(path: Path): Map<String, String> {
        return Files.newBufferedReader(path, StandardCharsets.UTF_8).use { reader ->
            parseReader(reader, path.toString())
        }
    }

    /**
     * Parse a .lang file from an InputStream.
     *
     * @param inputStream Input stream to read from
     * @param sourceName Name for error messages
     * @return Map of key to value
     */
    fun parse(inputStream: InputStream, sourceName: String = "input"): Map<String, String> {
        return BufferedReader(InputStreamReader(inputStream, StandardCharsets.UTF_8)).use { reader ->
            parseReader(reader, sourceName)
        }
    }

    /**
     * Parse a .lang file from raw text content.
     *
     * @param content The .lang file content
     * @param sourceName Name for error messages
     * @return Map of key to value
     */
    fun parseContent(content: String, sourceName: String = "input"): Map<String, String> {
        return parseReader(content.reader().buffered(), sourceName)
    }

    private fun parseReader(reader: BufferedReader, sourceName: String): Map<String, String> {
        val result = mutableMapOf<String, String>()
        var lineNumber = 0
        var pendingKey: String? = null
        val pendingValue = StringBuilder()
        var inMultiline = false

        reader.forEachLine { rawLine ->
            lineNumber++
            val line = rawLine.trimEnd()

            // Handle multiline continuation
            if (inMultiline) {
                if (line.endsWith("\\")) {
                    // Continue multiline
                    pendingValue.append(line.dropLast(1))
                } else {
                    // End of multiline
                    pendingValue.append(line.removeSuffix("\""))
                    result[pendingKey!!] = unescapeValue(pendingValue.toString())
                    pendingKey = null
                    pendingValue.clear()
                    inMultiline = false
                }
                return@forEachLine
            }

            // Skip empty lines and comments
            val trimmed = line.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                return@forEachLine
            }

            // Parse key = value
            val equalsIndex = line.indexOf('=')
            if (equalsIndex == -1) {
                throw LangParseException(sourceName, lineNumber, "Expected '=' in key-value pair: $line")
            }

            val key = line.substring(0, equalsIndex).trim()
            var value = line.substring(equalsIndex + 1).trim()

            // Validate key
            if (key.isEmpty()) {
                throw LangParseException(sourceName, lineNumber, "Empty key")
            }

            // Check for duplicate keys
            if (result.containsKey(key)) {
                throw LangParseException(sourceName, lineNumber, "Duplicate key: $key")
            }

            // Handle quoted values
            if (value.startsWith("\"")) {
                if (value.endsWith("\\")) {
                    // Start of multiline
                    pendingKey = key
                    pendingValue.append(value.drop(1).dropLast(1))
                    inMultiline = true
                    return@forEachLine
                } else if (value.endsWith("\"") && value.length > 1) {
                    // Complete quoted value
                    value = unescapeValue(value.drop(1).dropLast(1))
                } else {
                    throw LangParseException(sourceName, lineNumber, "Unterminated quoted string")
                }
            }

            result[key] = value
        }

        // Check for unclosed multiline
        if (inMultiline) {
            throw LangParseException(sourceName, lineNumber, "Unterminated multiline string for key: $pendingKey")
        }

        return result
    }

    /**
     * Unescape common escape sequences in a value.
     */
    private fun unescapeValue(value: String): String {
        return value
            .replace("\\n", "\n")
            .replace("\\t", "\t")
            .replace("\\r", "\r")
            .replace("\\\\", "\\")
            .replace("\\\"", "\"")
    }
}

/**
 * Exception thrown when a .lang file has syntax errors.
 */
class LangParseException(
    val sourceName: String,
    val lineNumber: Int,
    message: String
) : Exception("$sourceName:$lineNumber: $message")
