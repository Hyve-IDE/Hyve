package com.hyve.ui.exporter

import com.hyve.ui.core.domain.CommentPosition
import com.hyve.ui.core.domain.UIDocument
import com.hyve.ui.core.domain.elements.UIElement
import com.hyve.ui.core.domain.properties.PropertyMap
import com.hyve.ui.core.domain.properties.PropertyValue
import com.hyve.ui.core.domain.properties.PropertyValueVisitor
import com.hyve.ui.core.domain.styles.StyleDefinition
import com.hyve.ui.core.domain.styles.StyleReference
import com.hyve.ui.core.id.PropertyName
import com.hyve.ui.core.result.Result
import com.hyve.ui.exporter.formatter.Formatter
import com.hyve.ui.exporter.formatter.FormatterConfig

/**
 * Export UIDocument to .ui file text format.
 *
 * Supports:
 * - Import statements
 * - Style definitions
 * - Elements with properties
 * - Nested element hierarchies
 * - Proper indentation and formatting
 * - Round-trip safety (preserve all data)
 *
 * Example usage:
 * ```kotlin
 * val exporter = UIExporter()
 * val result = exporter.export(document)
 * when (result) {
 *     is Result.Success -> println(result.value)
 *     is Result.Failure -> println("Error: ${result.error}")
 * }
 * ```
 */
class UIExporter(
    private val config: FormatterConfig = FormatterConfig.DEFAULT
) {
    /**
     * Export UIDocument to .ui file text
     */
    fun export(document: UIDocument): Result<String, ExportError> {
        return try {
            val formatter = Formatter(config)

            // Emit file-header comments before imports/styles/elements
            document.comments
                .filter { it.position == CommentPosition.FileHeader }
                .forEach { comment ->
                    formatter.appendLine(comment.text)
                }
            if (document.comments.any { it.position == CommentPosition.FileHeader }) {
                formatter.appendBlankLine()
            }

            // Export imports
            if (document.imports.isNotEmpty()) {
                exportImports(document, formatter)
                if (config.addBlankLineAfterImports) {
                    formatter.appendBlankLine()
                }
            }

            // Export style definitions
            if (document.styles.isNotEmpty()) {
                exportStyles(document, formatter)
                if (config.addBlankLineAfterStyles) {
                    formatter.appendBlankLine()
                }
            }

            // Export root element — unwrap synthetic Root wrapper to avoid emitting "Root { }"
            if (document.root.type.value == "Root") {
                exportChildren(document.root.children, formatter)
            } else {
                exportElement(document.root, formatter)
            }

            Result.Success(formatter.getOutput().trimEnd() + "\n")
        } catch (e: Exception) {
            Result.Failure(ExportError.UnexpectedError(e.message ?: "Unknown error", e))
        }
    }

    /**
     * Export import statements
     */
    private fun exportImports(document: UIDocument, formatter: Formatter) {
        document.imports.forEach { (alias, path) ->
            val importLine = formatter.formatImport(alias.value, path)
            formatter.appendLine(importLine)
        }
    }

    /**
     * Export style definitions
     */
    private fun exportStyles(document: UIDocument, formatter: Formatter) {
        document.styles.values.forEach { style ->
            exportStyleDefinition(style, formatter)
        }
    }

    /**
     * Export a single style definition
     *
     * Handles different style formats:
     * - Tuple style: @MyStyle = (FontSize: 14);
     * - Type constructor: @PopupMenuLayerStyle = PopupMenuLayerStyle(...);
     * - Element-based: @FooterButton = TextButton { ... };
     */
    private fun exportStyleDefinition(style: StyleDefinition, formatter: Formatter) {
        val styleName = "@${style.name.value}"

        when {
            // Element-based style: @FooterButton = TextButton { ... };
            style.elementType != null -> {
                formatter.appendLine("$styleName = ${style.elementType} {")
                formatter.indented {
                    exportProperties(PropertyMap(style.properties), formatter)
                    if (style.children.isNotEmpty()) {
                        if (config.addBlankLineBetweenElements && style.properties.isNotEmpty()) {
                            formatter.appendBlankLine()
                        }
                        exportChildren(style.children, formatter)
                    }
                }
                formatter.appendLine("};")
            }
            // Type constructor: @PopupMenuLayerStyle = PopupMenuLayerStyle(...);
            style.typeName != null -> {
                val propertiesStr = formatStylePropertiesInline(style.properties)
                formatter.appendLine("$styleName = ${style.typeName}($propertiesStr);")
            }
            // Simple value style: @IconSize = 24; or @Color = @OtherColor;
            style.properties.size == 1 && style.properties.keys.first().value in listOf("_value", "_styleAlias") -> {
                val value = style.properties.values.first()
                formatter.appendLine("$styleName = ${formatPropertyValue(value)};")
            }
            // Tuple style: @MyStyle = (...);
            else -> {
                val propertiesStr = formatStyleProperties(style.properties)
                val styleLine = formatter.formatStyleDefinition(styleName, propertiesStr)
                formatter.appendLine(styleLine)
            }
        }
    }

    /**
     * Format style properties inline (comma-separated, without parens)
     */
    private fun formatStylePropertiesInline(properties: Map<PropertyName, PropertyValue>): String {
        if (properties.isEmpty()) {
            return ""
        }
        return properties.entries.joinToString(", ") { (name, value) ->
            if (name.value.startsWith("_spread_")) {
                formatPropertyValue(value)
            } else {
                "${name.value}: ${formatPropertyValue(value)}"
            }
        }
    }

    /**
     * Format style properties as tuple
     */
    private fun formatStyleProperties(properties: Map<PropertyName, PropertyValue>): String {
        if (properties.isEmpty()) {
            return "()"
        }

        val entries = properties.entries.joinToString(", ") { (name, value) ->
            if (name.value.startsWith("_spread_")) {
                formatPropertyValue(value)
            } else {
                "${name.value}: ${formatPropertyValue(value)}"
            }
        }
        return "($entries)"
    }

    /**
     * Export an element and its children
     */
    private fun exportElement(element: UIElement, formatter: Formatter) {
        val elementType = element.type.value

        // Handle special internal element types
        when (elementType) {
            "_StylePrefixedElement" -> {
                exportStylePrefixedElement(element, formatter)
                return
            }
            "_VariableRefElement" -> {
                exportVariableRefElement(element, formatter)
                return
            }
            "_IdOnlyBlock" -> {
                exportIdOnlyBlock(element, formatter)
                return
            }
        }

        // Apply reverse abstraction mapping
        val (exportType, exportProperties) = applyReverseAbstractionMapping(element)

        // Element opening: "Group #MyId {" or "Group {"
        val opening = formatter.formatElementOpening(
            exportType,
            element.id?.let { "#${it.value}" }
        )
        formatter.appendLine(opening)

        // Properties
        formatter.indented {
            exportProperties(exportProperties, formatter)

            // Add blank line between properties and children if configured
            if (config.addBlankLineBetweenElements && exportProperties.isNotEmpty() && element.children.isNotEmpty()) {
                formatter.appendBlankLine()
            }

            // Children
            exportChildren(element.children, formatter)
        }

        // Element closing: "}"
        formatter.appendLine(formatter.formatElementClosing())
    }

    /**
     * Export style-prefixed element: @FooterButton #Id { ... }
     */
    private fun exportStylePrefixedElement(element: UIElement, formatter: Formatter) {
        val stylePrefix = (element.properties[PropertyName("_stylePrefix")] as? PropertyValue.Text)?.value ?: "@Unknown"
        val idPart = element.id?.let { " #${it.value}" } ?: ""
        formatter.appendLine("$stylePrefix$idPart {")

        val props = element.properties.filter { it.key.value != "_stylePrefix" }
        formatter.indented {
            exportProperties(props, formatter)
            if (config.addBlankLineBetweenElements && props.isNotEmpty() && element.children.isNotEmpty()) {
                formatter.appendBlankLine()
            }
            exportChildren(element.children, formatter)
        }
        formatter.appendLine("}")
    }

    /**
     * Export variable reference element: $AssetEditor.@Spinner #Id { ... }
     */
    private fun exportVariableRefElement(element: UIElement, formatter: Formatter) {
        val variableRef = (element.properties[PropertyName("_variableRef")] as? PropertyValue.Text)?.value ?: "\$Unknown"
        val idPart = element.id?.let { " #${it.value}" } ?: ""
        formatter.appendLine("$variableRef$idPart {")

        val props = element.properties.filter { it.key.value != "_variableRef" }
        formatter.indented {
            exportProperties(props, formatter)
            if (config.addBlankLineBetweenElements && props.isNotEmpty() && element.children.isNotEmpty()) {
                formatter.appendBlankLine()
            }
            exportChildren(element.children, formatter)
        }
        formatter.appendLine("}")
    }

    /**
     * Export ID-only block: #Buttons { ... }
     */
    private fun exportIdOnlyBlock(element: UIElement, formatter: Formatter) {
        val idPart = element.id?.let { "#${it.value}" } ?: "#Unknown"
        formatter.appendLine("$idPart {")

        formatter.indented {
            exportProperties(element.properties, formatter)
            if (config.addBlankLineBetweenElements && element.properties.isNotEmpty() && element.children.isNotEmpty()) {
                formatter.appendBlankLine()
            }
            exportChildren(element.children, formatter)
        }
        formatter.appendLine("}")
    }

    /**
     * Apply reverse abstraction mapping to convert abstract elements to format-specific syntax.
     *
     * Mappings (reverse of parser):
     * - Button → TextButton
     * - Image → AssetImage
     * - ScrollView → Group { LayoutMode: TopScrolling/LeftScrolling }
     * - TabPanel → TabNavigation
     *
     * See ABSTRACTION_LAYER.md for full documentation.
     *
     * @return Pair of (exportType, exportProperties)
     */
    private fun applyReverseAbstractionMapping(
        element: UIElement
    ): Pair<String, PropertyMap> {
        val elementType = element.type.value
        var properties = element.properties

        // Name normalization mappings (reverse)
        val exportType = when (elementType) {
            "Button" -> "TextButton"
            "Image" -> "AssetImage"
            "TabPanel" -> "TabNavigation"
            "ScrollView" -> {
                // Convert ScrollView back to Group with LayoutMode
                val orientation = properties[PropertyName("Orientation")] as? PropertyValue.Text

                // Remove Orientation property and add LayoutMode
                properties = properties.remove(PropertyName("Orientation"))

                // Add LayoutMode property based on orientation
                val layoutMode = when (orientation?.value) {
                    "Horizontal" -> "LeftScrolling"
                    else -> "TopScrolling" // Default to vertical
                }
                properties = properties.set(PropertyName("LayoutMode"), PropertyValue.Text(layoutMode, quoted = false))

                "Group"
            }
            else -> elementType
        }

        return exportType to properties
    }

    /**
     * Export element properties
     */
    private fun exportProperties(properties: PropertyMap, formatter: Formatter) {
        properties.entries().forEach { (name, value) ->
            // Emit synthetic comment properties as raw comment text
            if (name.value.startsWith("_comment_")) {
                if (value is PropertyValue.Text) {
                    formatter.appendLine(value.value)
                }
                return@forEach
            }

            // Emit spread entries: ...@StyleRef or ...(Tuple)
            if (name.value.startsWith("_spread_")) {
                formatter.appendLine("${formatPropertyValue(value)};")
                return@forEach
            }

            // Emit element-scoped style definitions: @StyleName = value;
            if (name.value.startsWith("@")) {
                val styleValue = unwrapStyleValue(value)
                formatter.appendLine("${name.value} = $styleValue;")
                return@forEach
            }

            val propertyLine = formatter.formatProperty(
                name.value,
                formatPropertyValue(value)
            )
            formatter.appendLine(propertyLine)
        }
    }

    /**
     * Unwrap element-scoped style value for export.
     * Element-scoped styles are stored as Tuple properties with @-prefixed keys.
     * Handles:
     * - Tuple with single _value key: unwrap to the inner value (e.g., @Label = "";)
     * - Regular Tuple: format as tuple (e.g., @StyleState = (Key: Value);)
     */
    private fun unwrapStyleValue(value: PropertyValue): String {
        if (value is PropertyValue.Tuple) {
            val entries = value.values
            // Single _value entry: unwrap to export as @Name = value;
            if (entries.size == 1 && entries.containsKey("_value")) {
                return formatPropertyValue(entries["_value"]!!)
            }
        }
        return formatPropertyValue(value)
    }

    /**
     * Export child elements.
     * Handles synthetic _Comment children (preserving comment position relative to siblings).
     */
    private fun exportChildren(children: List<UIElement>, formatter: Formatter) {
        children.forEachIndexed { index, child ->
            if (child.type.value == "_Comment") {
                // Synthetic comment child — emit raw comment text without blank-line padding
                val text = (child.properties[PropertyName("text")] as? PropertyValue.Text)?.value
                if (text != null) {
                    formatter.appendLine(text)
                }
            } else {
                if (index > 0 && config.addBlankLineBetweenElements) {
                    // Don't insert blank line if the previous sibling was a comment
                    val prev = children[index - 1]
                    if (prev.type.value != "_Comment") {
                        formatter.appendBlankLine()
                    }
                }
                exportElement(child, formatter)
            }
        }
    }

    /**
     * Format a property value to string.
     * Uses [PropertyValueVisitor] for exhaustive dispatch — adding a new
     * PropertyValue subtype will cause a compile error here.
     */
    private fun formatPropertyValue(value: PropertyValue): String {
        return value.accept(formatVisitor)
    }

    private val formatVisitor = object : PropertyValueVisitor<String> {
        override fun visitText(value: PropertyValue.Text) =
            if (value.quoted) formatString(value.value) else value.value
        override fun visitNumber(value: PropertyValue.Number) = formatNumber(value.value)
        override fun visitPercent(value: PropertyValue.Percent) = formatPercent(value)
        override fun visitBoolean(value: PropertyValue.Boolean) = value.value.toString()
        override fun visitColor(value: PropertyValue.Color) = formatColor(value)
        override fun visitAnchor(value: PropertyValue.Anchor) = formatAnchor(value)
        override fun visitStyle(value: PropertyValue.Style) = formatStyleReference(value.reference)
        override fun visitTuple(value: PropertyValue.Tuple) = formatTuple(value.values)
        override fun visitList(value: PropertyValue.List) = formatList(value.values)
        override fun visitImagePath(value: PropertyValue.ImagePath) = formatString(value.path)
        override fun visitFontPath(value: PropertyValue.FontPath) = formatString(value.path)
        override fun visitLocalizedText(value: PropertyValue.LocalizedText) = "%${value.key}"
        override fun visitVariableRef(value: PropertyValue.VariableRef) = formatVariableRef(value)
        override fun visitSpread(value: PropertyValue.Spread) = "...${formatPropertyValue(value.value)}"
        override fun visitExpression(value: PropertyValue.Expression) = formatExpression(value)
        override fun visitUnknown(value: PropertyValue.Unknown) = value.raw
        override fun visitNull(value: PropertyValue.Null) = "null"
    }

    /**
     * Format arithmetic expression with proper parenthesization.
     * Adds grouping parentheses when needed to preserve operator precedence.
     * Example: @Value * 1000, 74 * 3, 2 - (@SSH - @DIS) / 2
     */
    private fun formatExpression(expr: PropertyValue.Expression): String {
        val left = formatExpressionOperand(expr.left, expr.operator, isRight = false)
        val right = formatExpressionOperand(expr.right, expr.operator, isRight = true)
        return "$left ${expr.operator} $right"
    }

    /**
     * Format an expression operand, adding parentheses if needed for correct precedence.
     */
    private fun formatExpressionOperand(operand: PropertyValue, parentOp: String, isRight: Boolean): String {
        if (operand is PropertyValue.Expression) {
            val needsParens = needsParentheses(operand.operator, parentOp, isRight)
            val formatted = formatExpression(operand)
            return if (needsParens) "($formatted)" else formatted
        }
        return formatPropertyValue(operand)
    }

    /**
     * Determine if an inner expression needs parentheses within an outer expression.
     * Rules:
     * - Lower precedence inner expression always needs parens (e.g., (a + b) * c)
     * - Same precedence right operand of - or / needs parens for left-associativity
     *   (e.g., a - (b - c) is different from a - b - c)
     */
    private fun needsParentheses(innerOp: String, outerOp: String, isRight: Boolean): Boolean {
        val innerPrec = operatorPrecedence(innerOp)
        val outerPrec = operatorPrecedence(outerOp)

        if (innerPrec < outerPrec) return true
        if (innerPrec == outerPrec && isRight && outerOp in listOf("-", "/")) return true

        return false
    }

    private fun operatorPrecedence(op: String): Int = when (op) {
        "+", "-" -> 1
        "*", "/" -> 2
        else -> 0
    }

    /**
     * Format variable reference
     * Example: $AssetEditor.@DropdownBoxStyle
     */
    private fun formatVariableRef(value: PropertyValue.VariableRef): String {
        val base = "$${value.alias}"
        return if (value.path.isEmpty()) {
            base
        } else {
            base + value.path.joinToString("") { ".$it" }
        }
    }

    /**
     * Format string value with quotes and escaping
     */
    private fun formatString(value: String): String {
        return "\"${escapeString(value)}\""
    }

    /**
     * Escape special characters in strings
     */
    private fun escapeString(value: String): String {
        return value
            .replace("\\", "\\\\")  // Backslash
            .replace("\"", "\\\"")  // Quote
            .replace("\n", "\\n")   // Newline
            .replace("\r", "\\r")   // Carriage return
            .replace("\t", "\\t")   // Tab
    }

    /**
     * Format number (remove unnecessary .0 for integers)
     */
    private fun formatNumber(value: Double): String {
        return if (value % 1.0 == 0.0) {
            value.toInt().toString()
        } else {
            value.toString()
        }
    }

    /**
     * Format percentage value
     * Example: 100%, 50%, 75.5%
     */
    private fun formatPercent(percent: PropertyValue.Percent): String {
        // Round to 2 decimal places to avoid floating point precision issues
        val pct = kotlin.math.round(percent.ratio * 10000.0) / 100.0
        return if (pct % 1.0 == 0.0) {
            "${pct.toInt()}%"
        } else {
            "$pct%"
        }
    }

    /**
     * Format color value
     * Examples: #ffffff, #ff0000(0.5)
     */
    private fun formatColor(color: PropertyValue.Color): String {
        return if (color.alpha != null) {
            "${color.hex}(${color.alpha})"
        } else {
            color.hex
        }
    }

    /**
     * Format anchor value, preserving original field order for round-trip fidelity.
     * Example: (Left: 10, Top: 5, Width: 100%, Height: 50%)
     */
    private fun formatAnchor(anchor: PropertyValue.Anchor): String {
        val anchorValue = anchor.anchor

        // Use original field order when available, otherwise default order
        val order = if (anchorValue.fieldOrder.isNotEmpty()) anchorValue.fieldOrder
                    else listOf("Left", "Top", "Right", "Bottom", "Width", "Height")

        val entries = order.mapNotNull { field ->
            val dim = when (field) {
                "Left" -> anchorValue.left
                "Top" -> anchorValue.top
                "Right" -> anchorValue.right
                "Bottom" -> anchorValue.bottom
                "Width" -> anchorValue.width
                "Height" -> anchorValue.height
                else -> null
            }
            dim?.let { "$field: $it" }
        }

        return "(${entries.joinToString(", ")})"
    }

    /**
     * Format style reference
     * Examples: @MyStyle, $Common.@HeaderStyle, ...@BaseStyle
     */
    private fun formatStyleReference(reference: StyleReference): String {
        return when (reference) {
            is StyleReference.Local -> "@${reference.name.value}"
            is StyleReference.Imported -> "${reference.alias.value}.@${reference.name.value}"
            is StyleReference.Spread -> "...${formatStyleReference(reference.reference)}"
            is StyleReference.Inline -> formatStyleProperties(reference.properties)
        }
    }

    /**
     * Format tuple value
     * Example: (X: 10, Y: 20)
     * Also handles spread entries: (...@StyleState, Background: #000)
     */
    private fun formatTuple(values: Map<String, PropertyValue>): String {
        if (values.isEmpty()) {
            return "()"
        }

        val entries = values.entries.joinToString(", ") { (key, value) ->
            // Check if this is a spread entry (stored with _spread_ prefix)
            if (key.startsWith("_spread_")) {
                formatPropertyValue(value)
            } else {
                "$key: ${formatPropertyValue(value)}"
            }
        }
        return "($entries)"
    }

    /**
     * Format list value
     * Example: ["One", "Two", "Three"]
     */
    private fun formatList(values: List<PropertyValue>): String {
        val elements = values.joinToString(", ") { formatPropertyValue(it) }
        return "[$elements]"
    }
}

/**
 * Errors that can occur during export
 */
sealed class ExportError {
    abstract val message: String

    data class InvalidDocument(override val message: String) : ExportError()
    data class InvalidProperty(val propertyName: String, override val message: String) : ExportError()
    data class UnexpectedError(override val message: String, val cause: Throwable) : ExportError()
}
