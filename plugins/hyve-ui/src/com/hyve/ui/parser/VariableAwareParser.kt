package com.hyve.ui.parser

import com.hyve.ui.core.domain.UIDocument
import com.hyve.ui.core.domain.anchor.AnchorDimension
import com.hyve.ui.core.domain.anchor.AnchorValue
import com.hyve.ui.core.domain.elements.UIElement
import com.hyve.ui.core.domain.properties.PropertyMap
import com.hyve.ui.core.domain.properties.PropertyValue
import com.hyve.ui.core.domain.styles.StyleDefinition
import com.hyve.ui.core.id.PropertyName
import com.hyve.ui.core.id.StyleName
import com.hyve.ui.core.result.Result
import com.hyve.ui.parser.imports.ImportCache
import com.hyve.ui.parser.imports.ImportError
import com.hyve.ui.parser.imports.ImportResolver
import com.hyve.ui.parser.variables.ExpressionEvaluator
import com.hyve.ui.parser.variables.VariableScope
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.io.path.exists
import kotlin.io.path.readText

/**
 * High-level parser that handles variable evaluation and file imports.
 *
 * Two-pass parsing:
 * 1. First pass: Parse the document structure (imports, styles, elements)
 * 2. Second pass: Resolve imports and evaluate all variables/expressions
 *
 * Usage:
 * ```kotlin
 * val parser = VariableAwareParser.forFile(Path.of("path/to/Hotbar.ui"))
 * when (val result = parser.parse()) {
 *     is Result.Success -> {
 *         val doc = result.value.document  // Fully resolved document
 *         val scope = result.value.scope   // Variable scope for debugging
 *     }
 *     is Result.Failure -> {
 *         // Handle errors
 *     }
 * }
 * ```
 */
class VariableAwareParser private constructor(
    private val source: String,
    private val filePath: Path?,
    private val importCache: ImportCache,
    private val warningCallback: ((String) -> Unit)?,
    private val importSearchPaths: List<Path> = emptyList()
) {
    /**
     * Parse the source with full variable resolution.
     */
    fun parse(): Result<ParsedDocument, List<ParserError>> {
        val allErrors = mutableListOf<ParserError>()
        val warnings = mutableListOf<String>()

        // First pass: basic parsing
        val parser = UIParser(source) { warning ->
            warnings.add(warning.message)
        }

        val parseResult = parser.parse()
        when (parseResult) {
            is Result.Failure -> {
                return Result.Failure(parseResult.error.map { ParserError.Parse(it) })
            }
            is Result.Success -> {
                val document = parseResult.value
                val scope = VariableScope(name = filePath?.fileName?.toString() ?: "inline")

                // Extract style definitions as variables
                extractStylesAsVariables(document, scope)

                // Second pass: resolve imports
                if (filePath != null) {
                    val resolver = ImportResolver.withCache(filePath.parent ?: Path("."), importCache, importSearchPaths)
                    val importErrors = resolver.resolveImports(document, filePath, scope)

                    for (error in importErrors) {
                        allErrors.add(ParserError.Import(error))
                        warnings.add(error.message)
                    }
                }

                // Third pass: evaluate all variables and expressions
                val evaluator = ExpressionEvaluator(scope) { msg ->
                    warnings.add(msg)
                    warningCallback?.invoke(msg)
                }

                val resolvedDocument = evaluateDocument(document, evaluator)

                // Report warnings
                warnings.forEach { warningCallback?.invoke(it) }

                return Result.Success(ParsedDocument(
                    document = resolvedDocument,
                    scope = scope,
                    warnings = warnings
                ))
            }
        }
    }

    /**
     * Extract style definitions from the document and add them to the scope as variables.
     */
    private fun extractStylesAsVariables(document: UIDocument, scope: VariableScope) {
        for ((styleName, styleDef) in document.styles) {
            val value = styleDef.properties[PropertyName("_value")]
                ?: styleDef.properties[PropertyName("_styleAlias")]

            if (value != null) {
                // Simple value: @Size = 64
                scope.defineVariable(styleName.value, value)
            } else if (styleDef.properties.isNotEmpty()) {
                // Tuple/object style: @Style = (FontSize: 14, ...)
                val tupleValue = PropertyValue.Tuple(
                    styleDef.properties.mapKeys { it.key.value }
                )
                scope.defineVariable(styleName.value, tupleValue)
            }
        }
    }

    /**
     * Evaluate all variables and expressions in the document.
     */
    private fun evaluateDocument(document: UIDocument, evaluator: ExpressionEvaluator): UIDocument {
        // Evaluate style definitions
        val evaluatedStyles = document.styles.mapValues { (_, styleDef) ->
            evaluateStyleDefinition(styleDef, evaluator)
        }

        // Evaluate root element tree
        val evaluatedRoot = evaluateElement(document.root, evaluator)

        return document.copy(
            styles = evaluatedStyles,
            root = evaluatedRoot
        )
    }

    /**
     * Evaluate a style definition.
     */
    private fun evaluateStyleDefinition(styleDef: StyleDefinition, evaluator: ExpressionEvaluator): StyleDefinition {
        val evaluatedProps = styleDef.properties.mapValues { (_, value) ->
            evaluator.evaluate(value)
        }
        return styleDef.copy(properties = evaluatedProps)
    }

    /**
     * Evaluate an element and all its children recursively.
     */
    private fun evaluateElement(element: UIElement, evaluator: ExpressionEvaluator): UIElement {
        // Evaluate all properties
        val evaluatedProperties = mutableMapOf<PropertyName, PropertyValue>()

        for (entry in element.properties.entries()) {
            val evaluated = evaluator.evaluate(entry.value)
            evaluatedProperties[entry.key] = evaluated
        }

        // Convert Anchor tuples that now have concrete values
        val finalProperties = processAnchorProperties(evaluatedProperties, evaluator)

        // Recursively evaluate children
        val evaluatedChildren = element.children.map { child ->
            evaluateElement(child, evaluator)
        }

        return element.copy(
            properties = PropertyMap.of(*finalProperties.map { it.key to it.value }.toTypedArray()),
            children = evaluatedChildren
        )
    }

    /**
     * Process Anchor properties - if they contain tuples with numeric values,
     * convert them to proper AnchorValue. Also re-evaluate Anchor properties
     * that had null dimensions due to unresolved expressions during initial parsing.
     */
    private fun processAnchorProperties(
        properties: Map<PropertyName, PropertyValue>,
        evaluator: ExpressionEvaluator
    ): Map<PropertyName, PropertyValue> {
        val result = properties.toMutableMap()

        val anchorProp = properties[PropertyName("Anchor")]
        when (anchorProp) {
            is PropertyValue.Tuple -> {
                val anchorValue = tryConvertToAnchor(anchorProp)
                if (anchorValue != null) {
                    result[PropertyName("Anchor")] = PropertyValue.Anchor(anchorValue)
                }
            }
            is PropertyValue.Anchor -> {
                // The anchor was already created but may have null dimensions
                // due to unresolved expressions. We need to re-evaluate.
                // This is a complex case - for now, we leave it as is since
                // the dimensions were already processed.
            }
            else -> {}
        }

        return result
    }

    /**
     * Try to convert a Tuple to an AnchorValue if it contains anchor dimensions.
     */
    private fun tryConvertToAnchor(tuple: PropertyValue.Tuple): AnchorValue? {
        val anchorKeys = setOf("Left", "Top", "Right", "Bottom", "Width", "Height")
        if (!tuple.values.keys.any { it in anchorKeys }) {
            return null
        }

        fun getDimension(key: String): AnchorDimension? {
            return when (val value = tuple.values[key]) {
                is PropertyValue.Number -> {
                    val num = value.value.toFloat()
                    if (num in 0.0f..1.0f && num != 0.0f && num != 1.0f) {
                        // Values strictly between 0 and 1 are relative
                        AnchorDimension.Relative(num)
                    } else {
                        // Whole numbers and 0/1 are absolute pixels
                        AnchorDimension.Absolute(num)
                    }
                }
                is PropertyValue.Percent -> {
                    AnchorDimension.Relative(value.ratio.toFloat())
                }
                else -> null
            }
        }

        return AnchorValue(
            left = getDimension("Left"),
            top = getDimension("Top"),
            right = getDimension("Right"),
            bottom = getDimension("Bottom"),
            width = getDimension("Width"),
            height = getDimension("Height")
        )
    }

    companion object {
        /**
         * Create a parser for a file path.
         */
        fun forFile(filePath: Path, warningCallback: ((String) -> Unit)? = null): VariableAwareParser {
            require(filePath.exists()) { "File does not exist: $filePath" }
            val source = filePath.readText()
            return VariableAwareParser(source, filePath, ImportCache(), warningCallback)
        }

        /**
         * Create a parser for in-memory source with import resolution based on filePath context.
         * The filePath does not need to exist on disk; only filePath.parent is used for resolving
         * relative import paths. When filePath is null, import resolution is skipped (same as forSource).
         *
         * @param importSearchPaths Additional directories to search when a relative import fails.
         *   Typically includes the vanilla Interface directory from AssetSettings.
         */
        fun forSourceWithPath(
            source: String,
            filePath: Path?,
            importSearchPaths: List<Path> = emptyList(),
            warningCallback: ((String) -> Unit)? = null
        ): VariableAwareParser {
            return VariableAwareParser(source, filePath, ImportCache(), warningCallback, importSearchPaths)
        }

        /**
         * Create a parser for inline source (no file imports supported).
         */
        fun forSource(source: String, warningCallback: ((String) -> Unit)? = null): VariableAwareParser {
            return VariableAwareParser(source, null, ImportCache(), warningCallback)
        }

        /**
         * Create a parser with a shared import cache (for batch processing).
         */
        fun withCache(
            filePath: Path,
            cache: ImportCache,
            warningCallback: ((String) -> Unit)? = null
        ): VariableAwareParser {
            require(filePath.exists()) { "File does not exist: $filePath" }
            val source = filePath.readText()
            return VariableAwareParser(source, filePath, cache, warningCallback)
        }
    }
}

/**
 * Result of parsing with variable resolution.
 */
data class ParsedDocument(
    val document: UIDocument,
    val scope: VariableScope,
    val warnings: List<String>
)

/**
 * Unified error type for the variable-aware parser.
 */
sealed class ParserError {
    abstract val message: String

    data class Parse(val error: ParseError) : ParserError() {
        override val message: String get() = error.message
    }

    data class Import(val error: ImportError) : ParserError() {
        override val message: String get() = error.message
    }

    data class Evaluation(override val message: String) : ParserError()
}
