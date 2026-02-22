package com.hyve.ui.parser

import com.hyve.ui.core.id.ElementType
import com.hyve.ui.core.domain.UIDocument
import com.hyve.ui.core.domain.anchor.AnchorDimension
import com.hyve.ui.core.domain.anchor.AnchorValue
import com.hyve.ui.core.domain.elements.UIElement
import com.hyve.ui.core.domain.properties.PropertyMap
import com.hyve.ui.core.domain.properties.PropertyValue
import com.hyve.ui.core.domain.styles.StyleDefinition
import com.hyve.ui.core.id.PropertyName
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

                // Update scope templates with evaluated versions (children may have had variables resolved)
                for ((styleName, styleDef) in resolvedDocument.styles) {
                    if (styleDef.elementType != null) {
                        scope.defineTemplate(styleName.value, styleDef)
                    }
                }

                // Fourth pass: expand template references
                val expandedDocument = expandTemplates(resolvedDocument, scope)

                // Report warnings
                warnings.forEach { warningCallback?.invoke(it) }

                return Result.Success(ParsedDocument(
                    document = expandedDocument,
                    scope = scope,
                    warnings = warnings
                ))
            }
        }
    }

    /**
     * Extract style definitions from the document and add them to the scope as variables.
     * Element-based styles are also registered as templates for expansion.
     */
    private fun extractStylesAsVariables(document: UIDocument, scope: VariableScope) {
        for ((styleName, styleDef) in document.styles) {
            // Register element-based styles as templates (e.g., @Container = Group { ... })
            if (styleDef.elementType != null) {
                scope.defineTemplate(styleName.value, styleDef)
            }

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
        val evaluatedChildren = styleDef.children.map { evaluateElement(it, evaluator) }
        return styleDef.copy(properties = evaluatedProps, children = evaluatedChildren)
    }

    /**
     * Evaluate an element and all its children recursively.
     *
     * Element-scoped style definitions (@-prefixed property keys like @Default, @Hovered)
     * are registered in a child scope so that other properties can reference them.
     * For example: Style: (Default: @Default, Hovered: @Hovered) can resolve @Default
     * from the element's own @Default = (...) definition.
     */
    private fun evaluateElement(element: UIElement, evaluator: ExpressionEvaluator): UIElement {
        // Collect element-scoped style definitions (@-prefixed property keys)
        val elementStyles = mutableMapOf<String, PropertyValue>()
        for (entry in element.properties.entries()) {
            val key = entry.key.value
            if (key.startsWith("@")) {
                elementStyles[key.removePrefix("@")] = entry.value
            }
        }

        // If element has scoped styles, create a child evaluator with them in scope
        val activeEvaluator = if (elementStyles.isNotEmpty()) {
            evaluator.withChildScope(
                element.id?.value ?: element.type.value,
                elementStyles
            )
        } else {
            evaluator
        }

        // Evaluate all properties
        val evaluatedProperties = mutableMapOf<PropertyName, PropertyValue>()

        for (entry in element.properties.entries()) {
            val evaluated = activeEvaluator.evaluate(entry.value)
            evaluatedProperties[entry.key] = evaluated
        }

        // Convert Anchor tuples that now have concrete values
        val finalProperties = processAnchorProperties(evaluatedProperties, activeEvaluator)

        // Recursively evaluate children (parent scope, not element-scoped)
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
                    // Bare numbers (without %) are always absolute pixels.
                    // Only PropertyValue.Percent (e.g. "50%") produces Relative dimensions.
                    AnchorDimension.Absolute(value.value.toFloat())
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
            height = getDimension("Height"),
            fieldOrder = tuple.values.keys.toList()
        )
    }

    // --- Template expansion ---

    /**
     * Expand template references (_VariableRefElement, _StylePrefixedElement)
     * into concrete element types using template definitions from scope.
     */
    private fun expandTemplates(document: UIDocument, scope: VariableScope): UIDocument {
        val expandedRoot = expandElement(document.root, scope)
        return document.copy(root = expandedRoot)
    }

    /**
     * Recursively expand template references in an element tree.
     */
    private fun expandElement(element: UIElement, scope: VariableScope): UIElement {
        return when (element.type.value) {
            "_VariableRefElement" -> expandVariableRefElement(element, scope)
            "_StylePrefixedElement" -> expandStylePrefixedElement(element, scope)
            else -> {
                // Recursively expand children
                val expandedChildren = element.children.map { expandElement(it, scope) }
                if (expandedChildren !== element.children) {
                    element.copy(children = expandedChildren)
                } else {
                    element
                }
            }
        }
    }

    /**
     * Expand a _VariableRefElement (e.g., $Container.@Container #Panel { ... })
     * by looking up the template from the imported scope.
     */
    private fun expandVariableRefElement(element: UIElement, scope: VariableScope): UIElement {
        val refText = (element.properties[PropertyName("_variableRef")] as? PropertyValue.Text)?.value
            ?: return expandChildren(element, scope) // No ref found, leave as-is

        val (alias, templateName) = parseTemplateRef(refText)
            ?: return expandChildren(element, scope)

        val template = scope.resolveImportedTemplate(alias, templateName)
            ?: return expandChildren(element, scope) // Template not found, graceful fallback

        return expandWithTemplate(element, template, scope)
    }

    /**
     * Expand a _StylePrefixedElement (e.g., @FooterButton #Id { ... })
     * by looking up the template from the local scope.
     */
    private fun expandStylePrefixedElement(element: UIElement, scope: VariableScope): UIElement {
        val stylePrefix = (element.properties[PropertyName("_stylePrefix")] as? PropertyValue.Text)?.value
            ?: return expandChildren(element, scope)

        // Strip leading @ to get the template name
        val templateName = stylePrefix.removePrefix("@")
        val template = scope.getTemplate(templateName)
            ?: return expandChildren(element, scope) // Template not found, graceful fallback

        return expandWithTemplate(element, template, scope)
    }

    /**
     * Core merge: expand an instance element using a template definition.
     *
     * 1. Set type to template's elementType (with abstraction mapping)
     * 2. Template properties as baseline, instance properties overlay (instance wins)
     * 3. Preserve _variableRef/_stylePrefix for export round-trip
     * 4. Merge children: template children as baseline; instance's _IdOnlyBlock children
     *    merge by matching #Id onto template children
     * 5. Recursively expand merged children
     */
    private fun expandWithTemplate(
        instance: UIElement,
        template: StyleDefinition,
        scope: VariableScope
    ): UIElement {
        // 1. Determine the concrete element type with abstraction mapping
        val concreteType = mapAbstractType(template.elementType ?: "Group", template.properties)

        // 2. Merge properties: template baseline + instance overlay
        val mergedProps = mutableMapOf<PropertyName, PropertyValue>()

        // Add template properties
        for ((key, value) in template.properties) {
            mergedProps[key] = value
        }

        // Overlay instance properties (instance wins, skip internal markers)
        for (entry in instance.properties.entries()) {
            mergedProps[entry.key] = entry.value
        }

        // 3. Build children by merging template children with instance overrides
        val instanceChildren = instance.children
        val templateChildren = template.children

        // Separate instance children into _IdOnlyBlock overrides and regular children
        val idOverrides = mutableMapOf<String, UIElement>()
        val regularChildren = mutableListOf<UIElement>()

        for (child in instanceChildren) {
            if (child.type.value == "_IdOnlyBlock" && child.id != null) {
                idOverrides[child.id.value] = child
            } else {
                regularChildren.add(child)
            }
        }

        // Merge: start with template children, apply ID overrides
        val mergedChildren = mutableListOf<UIElement>()
        for (templateChild in templateChildren) {
            val override = templateChild.id?.let { idOverrides.remove(it.value) }
            if (override != null) {
                mergedChildren.add(mergeChildOverride(templateChild, override, scope))
            } else {
                mergedChildren.add(templateChild)
            }
        }

        // Append any remaining ID overrides and regular children
        for ((_, override) in idOverrides) {
            mergedChildren.add(override)
        }
        mergedChildren.addAll(regularChildren)

        // 5. Recursively expand merged children
        val expandedChildren = mergedChildren.map { expandElement(it, scope) }

        return UIElement(
            type = ElementType(concreteType),
            id = instance.id,
            properties = PropertyMap.of(*mergedProps.map { it.key to it.value }.toTypedArray()),
            children = expandedChildren,
            metadata = instance.metadata
        )
    }

    /**
     * Merge properties from an _IdOnlyBlock override onto a template child.
     */
    private fun mergeChildOverride(
        templateChild: UIElement,
        idOnlyBlock: UIElement,
        scope: VariableScope
    ): UIElement {
        // Overlay override properties onto template child
        val mergedProps = mutableMapOf<PropertyName, PropertyValue>()
        for (entry in templateChild.properties.entries()) {
            mergedProps[entry.key] = entry.value
        }
        for (entry in idOnlyBlock.properties.entries()) {
            mergedProps[entry.key] = entry.value
        }

        // Merge nested children recursively using the same ID-matching strategy
        val overrideChildren = idOnlyBlock.children
        val baseChildren = templateChild.children

        val nestedIdOverrides = mutableMapOf<String, UIElement>()
        val nestedRegularChildren = mutableListOf<UIElement>()

        for (child in overrideChildren) {
            if (child.type.value == "_IdOnlyBlock" && child.id != null) {
                nestedIdOverrides[child.id.value] = child
            } else {
                nestedRegularChildren.add(child)
            }
        }

        val mergedChildren = mutableListOf<UIElement>()
        for (baseChild in baseChildren) {
            val nestedOverride = baseChild.id?.let { nestedIdOverrides.remove(it.value) }
            if (nestedOverride != null) {
                mergedChildren.add(mergeChildOverride(baseChild, nestedOverride, scope))
            } else {
                mergedChildren.add(baseChild)
            }
        }
        for ((_, override) in nestedIdOverrides) {
            mergedChildren.add(override)
        }
        mergedChildren.addAll(nestedRegularChildren)

        return templateChild.copy(
            properties = PropertyMap.of(*mergedProps.map { it.key to it.value }.toTypedArray()),
            children = mergedChildren
        )
    }

    /**
     * Apply abstraction mapping to a template's element type.
     * Mirrors UIParser.applyAbstractionMapping.
     */
    private fun mapAbstractType(rawType: String, properties: Map<PropertyName, PropertyValue>): String {
        val mapped = when (rawType) {
            "TextButton" -> "Button"
            "AssetImage" -> "Image"
            "TabNavigation" -> "TabPanel"
            else -> rawType
        }

        if (mapped == "Group") {
            val layoutMode = properties[PropertyName("LayoutMode")] as? PropertyValue.Text
            return when (layoutMode?.value) {
                "TopScrolling", "BottomScrolling", "LeftScrolling" -> "ScrollView"
                else -> mapped
            }
        }

        return mapped
    }

    /**
     * Parse a template reference string like "$Alias.@TemplateName" into (alias, templateName).
     */
    private fun parseTemplateRef(refText: String): Pair<String, String>? {
        // Expected format: $Alias.@TemplateName
        if (!refText.startsWith("$")) return null
        val dotIndex = refText.indexOf('.')
        if (dotIndex < 0) return null

        val alias = refText.substring(1, dotIndex) // strip leading $
        val afterDot = refText.substring(dotIndex + 1)

        if (!afterDot.startsWith("@")) return null
        val templateName = afterDot.substring(1) // strip leading @

        if (alias.isEmpty() || templateName.isEmpty()) return null
        return alias to templateName
    }

    /**
     * Helper: expand children of an element without changing its type.
     * Used for graceful fallback when template lookup fails.
     */
    private fun expandChildren(element: UIElement, scope: VariableScope): UIElement {
        val expandedChildren = element.children.map { expandElement(it, scope) }
        return if (expandedChildren !== element.children) {
            element.copy(children = expandedChildren)
        } else {
            element
        }
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
