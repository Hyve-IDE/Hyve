package com.hyve.ui.exporter.formatter

/**
 * Configuration for formatting exported .ui files
 */
data class FormatterConfig(
    val indentSize: Int = 4,
    val useSpaces: Boolean = true,
    val addTrailingSemicolons: Boolean = true,
    val maxLineLength: Int = 120,
    val addBlankLineBetweenElements: Boolean = false,
    val addBlankLineAfterImports: Boolean = true,
    val addBlankLineAfterStyles: Boolean = true
) {
    companion object {
        val DEFAULT = FormatterConfig()
        val COMPACT = FormatterConfig(
            indentSize = 2,
            addBlankLineBetweenElements = false,
            addBlankLineAfterImports = false,
            addBlankLineAfterStyles = false
        )
    }
}

/**
 * Handles indentation and text formatting for .ui file export
 */
class Formatter(private val config: FormatterConfig = FormatterConfig.DEFAULT) {
    private val indentChar = if (config.useSpaces) " " else "\t"
    private val indentUnit = indentChar.repeat(config.indentSize)

    /**
     * Current indentation level
     */
    private var currentIndentLevel = 0

    /**
     * StringBuilder for building output
     */
    private val output = StringBuilder()

    /**
     * Get the formatted output
     */
    fun getOutput(): String = output.toString()

    /**
     * Clear the formatter state (for reuse)
     */
    fun clear() {
        output.clear()
        currentIndentLevel = 0
    }

    /**
     * Append text at current indentation level
     */
    fun append(text: String) {
        output.append(text)
    }

    /**
     * Append text with newline at current indentation level
     */
    fun appendLine(text: String = "") {
        if (text.isNotEmpty()) {
            output.append(currentIndent()).append(text)
        }
        output.append("\n")
    }

    /**
     * Append text without any indentation
     */
    fun appendRaw(text: String) {
        output.append(text)
    }

    /**
     * Append a blank line
     */
    fun appendBlankLine() {
        output.append("\n")
    }

    /**
     * Get current indentation string
     */
    fun currentIndent(): String = indentUnit.repeat(currentIndentLevel)

    /**
     * Increase indentation level
     */
    fun indent() {
        currentIndentLevel++
    }

    /**
     * Decrease indentation level
     */
    fun dedent() {
        require(currentIndentLevel > 0) { "Cannot dedent below level 0" }
        currentIndentLevel--
    }

    /**
     * Execute block with increased indentation, then restore level
     */
    inline fun <T> indented(block: () -> T): T {
        indent()
        try {
            return block()
        } finally {
            dedent()
        }
    }

    /**
     * Format a property name-value pair
     * Example: "Text: \"Hello\""
     */
    fun formatProperty(name: String, value: String, addSemicolon: Boolean = config.addTrailingSemicolons): String {
        val semicolon = if (addSemicolon) ";" else ""
        return "$name: $value$semicolon"
    }

    /**
     * Format an import statement
     * Example: "$Common = \"path/to/Common.ui\";"
     */
    fun formatImport(alias: String, path: String): String {
        val semicolon = if (config.addTrailingSemicolons) ";" else ""
        return "$alias = \"$path\"$semicolon"
    }

    /**
     * Format a style definition
     * Example: "@MyStyle = (FontSize: 14, RenderBold: true);"
     */
    fun formatStyleDefinition(name: String, properties: String): String {
        val semicolon = if (config.addTrailingSemicolons) ";" else ""
        return "$name = $properties$semicolon"
    }

    /**
     * Format element opening
     * Example: "Group #MyId {"
     */
    fun formatElementOpening(type: String, id: String?): String {
        return if (id != null) {
            "$type $id {"
        } else {
            "$type {"
        }
    }

    /**
     * Format element closing
     */
    fun formatElementClosing(): String = "}"

    /**
     * Escape string value for .ui format
     */
    fun escapeString(value: String): String {
        return value
            .replace("\\", "\\\\")  // Backslash
            .replace("\"", "\\\"")  // Quote
            .replace("\n", "\\n")   // Newline
            .replace("\r", "\\r")   // Carriage return
            .replace("\t", "\\t")   // Tab
    }

    /**
     * Quote a string value
     */
    fun quoteString(value: String): String {
        return "\"${escapeString(value)}\""
    }
}
