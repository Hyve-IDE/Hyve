package com.hyve.ui.parser

import com.hyve.ui.core.domain.*
import com.hyve.ui.core.domain.anchor.AnchorDimension
import com.hyve.ui.core.domain.anchor.AnchorValue
import com.hyve.ui.core.domain.elements.UIElement
import com.hyve.ui.core.domain.properties.PropertyMap
import com.hyve.ui.core.domain.properties.PropertyValue
import com.hyve.ui.core.domain.styles.StyleDefinition
import com.hyve.ui.core.domain.styles.StyleReference
import com.hyve.ui.core.id.*
import com.hyve.ui.core.result.Result
import com.hyve.ui.parser.ast.*
import com.hyve.ui.parser.lexer.Lexer
import com.hyve.ui.parser.lexer.Token
import com.hyve.ui.parser.lexer.TokenType

/**
 * Recursive descent parser for .ui files.
 * Converts tokens into AST, then AST into domain model (UIDocument).
 *
 * @param source The .ui file source text to parse
 * @param warningCallback Optional callback for parse warnings (e.g., unknown properties).
 *                        Warnings don't prevent successful parsing but may indicate issues.
 */
class UIParser(
    private val source: String,
    private val warningCallback: ((ParseWarning) -> Unit)? = null
) {
    private val tokens: List<Token>
    private var current = 0
    private val errors = mutableListOf<ParseError>()

    init {
        // Tokenize source
        val lexer = Lexer(source)
        tokens = lexer.tokenize().filter { it.type != TokenType.WHITESPACE } // Skip whitespace
    }

    /**
     * Parse source into UIDocument
     */
    fun parse(): Result<UIDocument, List<ParseError>> {
        try {
            val document = parseDocument()
            return if (errors.isEmpty()) {
                Result.Success(document)
            } else {
                Result.Failure(errors.toList())
            }
        } catch (e: Exception) {
            errors.add(ParseError.UnexpectedException(e.message ?: "Unknown error", peek().position))
            return Result.Failure(errors.toList())
        }
    }

    /**
     * Parse entire document
     */
    private fun parseDocument(): UIDocument {
        val imports = mutableMapOf<ImportAlias, String>()
        val styles = mutableMapOf<StyleName, StyleDefinition>()
        val comments = mutableListOf<Comment>()
        val rootChildren = mutableListOf<UIElement>()

        while (!isAtEnd()) {
            when {
                // Comments
                check(TokenType.COMMENT) -> {
                    val commentToken = advance()
                    comments.add(Comment(
                        text = commentToken.lexeme,
                        position = CommentPosition.FileHeader
                    ))
                }

                // Variable reference element at top level: $AssetEditor.@TextButton #Button { }
                check(TokenType.DOLLAR) && peekIsVariableReferenceElement() -> {
                    parseVariableReferenceElement()?.let { element ->
                        rootChildren.add(element)
                    }
                }

                // Imports: $Common = "path";
                check(TokenType.DOLLAR) -> {
                    parseImport()?.let { (alias, path) ->
                        imports[alias] = path
                    }
                }

                // Style-prefixed elements at top level: @FooterButton #Id { }
                check(TokenType.AT) && peekIsStylePrefixedElement() -> {
                    parseStylePrefixedElement()?.let { element ->
                        rootChildren.add(element)
                    }
                }

                // Styles: @MyStyle = (...);
                check(TokenType.AT) -> {
                    parseStyleDefinition()?.let { style ->
                        styles[style.name] = style
                    }
                }

                // Elements: Group { ... }
                check(TokenType.IDENTIFIER) -> {
                    parseElement()?.let { element ->
                        rootChildren.add(element)
                    }
                }

                else -> {
                    error("Unexpected token: ${peek().lexeme}")
                    advance()
                }
            }
        }

        // Create root element to hold all top-level children
        val root = if (rootChildren.isEmpty()) {
            UIElement.root()
        } else if (rootChildren.size == 1) {
            rootChildren.first()
        } else {
            // Multiple top-level elements, wrap in root
            UIElement.root().copy(children = rootChildren)
        }

        return UIDocument(
            imports = imports,
            styles = styles,
            root = root,
            comments = comments
        )
    }

    /**
     * Parse import: $Common = "path/to/Common.ui";
     */
    private fun parseImport(): Pair<ImportAlias, String>? {
        val dollarToken = consume(TokenType.DOLLAR, "Expected '$'")
        val nameToken = consume(TokenType.IDENTIFIER, "Expected import alias name")

        val alias = try {
            ImportAlias("$${nameToken.lexeme}")
        } catch (e: Exception) {
            error("Invalid import alias: ${e.message}")
            return null
        }

        consume(TokenType.EQUALS, "Expected '=' after import alias")

        val pathToken = consume(TokenType.STRING, "Expected import path string")
        val path = pathToken.value as? String ?: pathToken.lexeme.trim('"')

        consume(TokenType.SEMICOLON, "Expected ';' after import")

        return alias to path
    }

    /**
     * Parse style definition.
     *
     * Supports multiple forms:
     * - Tuple style: @MyStyle = (FontSize: 14, RenderBold: true);
     * - Type constructor: @PopupMenuLayerStyle = PopupMenuLayerStyle(...);
     * - Element-based: @FooterButton = TextButton { Style: (...); };
     */
    private fun parseStyleDefinition(): StyleDefinition? {
        val atToken = consume(TokenType.AT, "Expected '@'")
        val nameToken = consume(TokenType.IDENTIFIER, "Expected style name")

        val styleName = try {
            StyleName(nameToken.lexeme)
        } catch (e: Exception) {
            error("Invalid style name: ${e.message}")
            return null
        }

        consume(TokenType.EQUALS, "Expected '=' after style name")

        // Determine the style definition format
        return when {
            // Type constructor or element-based: TypeName(...) or TypeName { ... }
            check(TokenType.IDENTIFIER) -> {
                val typeName = advance().lexeme

                when {
                    // Type constructor: TypeName(...)
                    check(TokenType.LEFT_PAREN) -> {
                        val properties = parseTypeConstructorArgs()
                        consume(TokenType.SEMICOLON, "Expected ';' after style definition")
                        StyleDefinition(
                            name = styleName,
                            properties = properties,
                            typeName = typeName
                        )
                    }
                    // Element-based style: TypeName { ... }
                    check(TokenType.LEFT_BRACE) -> {
                        consume(TokenType.LEFT_BRACE, "Expected '{' after type name")
                        val (properties, children) = parseElementBody()
                        consume(TokenType.RIGHT_BRACE, "Expected '}' after style element body")
                        consume(TokenType.SEMICOLON, "Expected ';' after style definition")
                        StyleDefinition(
                            name = styleName,
                            properties = properties,
                            elementType = typeName,
                            children = children
                        )
                    }
                    else -> {
                        error("Expected '(' or '{' after type name in style definition")
                        null
                    }
                }
            }
            // Tuple style: (Key: Value, ...) or (...@Spread, ...)
            // Must distinguish from expression grouping: (expression)
            check(TokenType.LEFT_PAREN) && isTupleAhead() -> {
                val properties = parseInlineStyle()
                consume(TokenType.SEMICOLON, "Expected ';' after style definition")
                StyleDefinition(
                    name = styleName,
                    properties = properties
                )
            }
            // Value-based style (including expressions): @Value = @Ref + 9; or @Color = #fff;
            // This handles: style references, variable references, colors, numbers, strings,
            // booleans, percentages, localized keys, negative numbers, expressions (including
            // grouped expressions like (a + b) * c), and lists
            check(TokenType.DOLLAR) || check(TokenType.AT) || check(TokenType.COLOR) ||
            check(TokenType.NUMBER) || check(TokenType.STRING) || check(TokenType.BOOLEAN) ||
            check(TokenType.PERCENT) || check(TokenType.LOCALIZED_KEY) || check(TokenType.MINUS) ||
            check(TokenType.LEFT_PAREN) || check(TokenType.LEFT_BRACKET) -> {
                val value = parsePropertyValue()
                consume(TokenType.SEMICOLON, "Expected ';' after style value")

                // Determine property name based on value type
                val propName = when (value) {
                    is PropertyValue.Style -> PropertyName("_styleAlias")
                    is PropertyValue.VariableRef -> PropertyName("_styleAlias")
                    else -> PropertyName("_value")
                }

                StyleDefinition(
                    name = styleName,
                    properties = mapOf(propName to value)
                )
            }
            else -> {
                error("Expected '(' or identifier after '=' in style definition")
                null
            }
        }
    }

    /**
     * Parse type constructor arguments: TypeName(Prop1: val1, Prop2: val2)
     * Also handles spread operators: TypeName(...@BaseStyle, NewProp: val)
     * Similar to parseInlineStyle but handles the full property value syntax
     */
    private fun parseTypeConstructorArgs(): Map<PropertyName, PropertyValue> {
        consume(TokenType.LEFT_PAREN, "Expected '(' for type constructor")

        val properties = mutableMapOf<PropertyName, PropertyValue>()
        var spreadCount = 0

        while (!check(TokenType.RIGHT_PAREN) && !isAtEnd()) {
            // Handle spread operator: (...@StyleName, ...)
            if (check(TokenType.SPREAD)) {
                val spreadValue = parseSpread()
                properties[PropertyName("_spread_$spreadCount")] = spreadValue
                spreadCount++
            } else {
                val nameToken = consume(TokenType.IDENTIFIER, "Expected property name")
                val propertyName = PropertyName(nameToken.lexeme)

                consume(TokenType.COLON, "Expected ':' after property name")

                val value = parsePropertyValue(propertyName.value)
                properties[propertyName] = value
            }

            if (!check(TokenType.RIGHT_PAREN)) {
                if (check(TokenType.COMMA)) {
                    advance()
                }
            }
        }

        consume(TokenType.RIGHT_PAREN, "Expected ')' after type constructor")

        return properties
    }

    /**
     * Parse inline style: (FontSize: 14, RenderBold: true)
     */
    private fun parseInlineStyle(): Map<PropertyName, PropertyValue> {
        consume(TokenType.LEFT_PAREN, "Expected '(' for style properties")

        val properties = mutableMapOf<PropertyName, PropertyValue>()
        var spreadCount = 0

        while (!check(TokenType.RIGHT_PAREN) && !isAtEnd()) {
            // Handle spread operator: (...@StyleName, ...)
            if (check(TokenType.SPREAD)) {
                val spreadValue = parseSpread()
                properties[PropertyName("_spread_$spreadCount")] = spreadValue
                spreadCount++
            } else {
                val nameToken = consume(TokenType.IDENTIFIER, "Expected property name")
                val propertyName = PropertyName(nameToken.lexeme)

                consume(TokenType.COLON, "Expected ':' after property name")

                val value = parsePropertyValue(propertyName.value)
                properties[propertyName] = value
            }

            if (!check(TokenType.RIGHT_PAREN)) {
                consume(TokenType.COMMA, "Expected ',' between properties")
            }
        }

        consume(TokenType.RIGHT_PAREN, "Expected ')' after style properties")

        return properties
    }

    /**
     * Parse element: Group #MyId { ... }
     *
     * Also handles:
     * - Style-prefixed elements: @FooterButton #Id { ... }
     * - Variable reference elements: $AssetEditor.@Spinner { ... }
     */
    private fun parseElement(): UIElement? {
        val typeToken = consume(TokenType.IDENTIFIER, "Expected element type")
        val rawElementType = typeToken.lexeme

        // Optional ID: #MyId
        val elementId = if (check(TokenType.HASH)) {
            advance() // consume '#'
            val idToken = consume(TokenType.IDENTIFIER, "Expected element ID after '#'")
            try {
                ElementId(idToken.lexeme)
            } catch (e: Exception) {
                error("Invalid element ID: ${e.message}")
                null
            }
        } else {
            null
        }

        consume(TokenType.LEFT_BRACE, "Expected '{' after element declaration")

        val (properties, children) = parseElementBody()

        consume(TokenType.RIGHT_BRACE, "Expected '}' after element body")

        // Apply abstraction layer mapping
        return applyAbstractionMapping(rawElementType, elementId, properties, children)
    }

    /**
     * Parse element body contents (properties and children)
     * Used both for normal elements and element-based style definitions
     *
     * Handles:
     * - Properties: Name: value;
     * - Children: ChildType { ... }
     * - Element-scoped styles: @StyleState = (...);
     * - Style-prefixed children: @FooterButton #Id { ... }
     * - Variable reference children: $AssetEditor.@Spinner { ... }
     */
    private fun parseElementBody(): Pair<MutableMap<PropertyName, PropertyValue>, MutableList<UIElement>> {
        val properties = mutableMapOf<PropertyName, PropertyValue>()
        val children = mutableListOf<UIElement>()

        // Element-scoped style definitions (stored locally for tuple value expansion)
        val localStyles = mutableMapOf<StyleName, StyleDefinition>()

        // Counter for synthetic comment property keys
        var commentIndex = 0

        // Track whether we've seen a child element.
        // Comments before the first child are stored as properties (exported with properties).
        // Comments after the first child are stored as synthetic _Comment child elements
        // to preserve their position relative to siblings.
        var seenChild = false

        while (!check(TokenType.RIGHT_BRACE) && !isAtEnd()) {
            when {
                // Preserve comments inside elements
                check(TokenType.COMMENT) -> {
                    val commentToken = advance()
                    if (seenChild) {
                        // Comment between children → synthetic child element
                        children.add(UIElement(
                            type = ElementType("_Comment"),
                            id = null,
                            properties = PropertyMap.of(
                                PropertyName("text") to PropertyValue.Text(commentToken.lexeme)
                            )
                        ))
                    } else {
                        // Comment before first child → property (exported with properties)
                        properties[PropertyName("_comment_${commentIndex++}")] = PropertyValue.Text(commentToken.lexeme)
                    }
                }

                // Element-scoped style definition: @StyleState = (...);
                check(TokenType.AT) && peekIsLocalStyleDefinition() -> {
                    parseElementScopedStyle()?.let { style ->
                        localStyles[style.name] = style
                        // Store as a local style property for export
                        properties[PropertyName("@${style.name.value}")] = styleToPropertyValue(style)

                        // Element-based scoped styles may have children — store them
                        // as a synthetic wrapper so the exporter can reconstruct them.
                        if (style.children.isNotEmpty()) {
                            children.add(UIElement(
                                type = ElementType("_ScopedStyleChildren"),
                                id = null,
                                properties = PropertyMap.of(
                                    PropertyName("_forStyle") to PropertyValue.Text("@${style.name.value}")
                                ),
                                children = style.children
                            ))
                        }
                    }
                }

                // Style-prefixed child element: @FooterButton #Id { ... }
                check(TokenType.AT) && peekIsStylePrefixedElement() -> {
                    parseStylePrefixedElement()?.let { children.add(it) }
                    seenChild = true
                }

                // Variable reference element: $AssetEditor.@Spinner { ... }
                check(TokenType.DOLLAR) && peekIsVariableReferenceElement() -> {
                    parseVariableReferenceElement()?.let { children.add(it) }
                    seenChild = true
                }

                // Child element: ChildType #Id { ... } or ChildType { ... }
                check(TokenType.IDENTIFIER) && peekNext()?.type in listOf(TokenType.HASH, TokenType.LEFT_BRACE) -> {
                    parseElement()?.let { children.add(it) }
                    seenChild = true
                }

                // ID-only block: #Buttons { ... } - implicit container with just ID
                check(TokenType.HASH) && peekIsIdOnlyBlock() -> {
                    parseIdOnlyBlock()?.let { children.add(it) }
                    seenChild = true
                }

                // Property: Name: value;
                check(TokenType.IDENTIFIER) -> {
                    val nameToken = consume(TokenType.IDENTIFIER, "Expected property name")
                    val propertyName = PropertyName(nameToken.lexeme)

                    consume(TokenType.COLON, "Expected ':' after property name")

                    val value = parsePropertyValue(propertyName.value)
                    // Upgrade quoted strings to ImagePath/FontPath for known property names
                    properties[propertyName] = upgradePropertyValue(propertyName.value, value)

                    consume(TokenType.SEMICOLON, "Expected ';' after property")
                }

                else -> {
                    error("Unexpected token in element body: ${peek().lexeme}")
                    advance()
                }
            }
        }

        return properties to children
    }

    /**
     * Check if the current #token is an ID-only block (#Id { ... })
     */
    private fun peekIsIdOnlyBlock(): Boolean {
        // Looking for: #Id {
        if (current + 2 >= tokens.size) return false
        return tokens[current + 1].type == TokenType.IDENTIFIER &&
               tokens[current + 2].type == TokenType.LEFT_BRACE
    }

    /**
     * Parse ID-only block: #Buttons { ... }
     * These are implicit containers with just an ID
     */
    private fun parseIdOnlyBlock(): UIElement? {
        consume(TokenType.HASH, "Expected '#'")
        val idToken = consume(TokenType.IDENTIFIER, "Expected ID")

        val elementId = try {
            ElementId(idToken.lexeme)
        } catch (e: Exception) {
            error("Invalid element ID: ${e.message}")
            null
        }

        consume(TokenType.LEFT_BRACE, "Expected '{' after ID-only block")

        val (properties, children) = parseElementBody()

        consume(TokenType.RIGHT_BRACE, "Expected '}' after ID-only block body")

        return UIElement(
            type = ElementType("_IdOnlyBlock"),
            id = elementId,
            properties = PropertyMap.of(*properties.map { it.key to it.value }.toTypedArray()),
            children = children
        )
    }

    /**
     * Check if the current @token is followed by identifier + '=' (a local style definition)
     */
    private fun peekIsLocalStyleDefinition(): Boolean {
        // Looking for: @StyleName =
        if (current + 2 >= tokens.size) return false
        return tokens[current + 1].type == TokenType.IDENTIFIER &&
               tokens[current + 2].type == TokenType.EQUALS
    }

    /**
     * Check if the current @token is a style prefix for an element (followed by #Id or {)
     */
    private fun peekIsStylePrefixedElement(): Boolean {
        // Looking for: @StyleName #Id { or @StyleName {
        if (current + 1 >= tokens.size) return false
        val afterAt = tokens[current + 1]
        if (afterAt.type != TokenType.IDENTIFIER) return false

        if (current + 2 >= tokens.size) return false
        val afterName = tokens[current + 2]
        return afterName.type == TokenType.HASH || afterName.type == TokenType.LEFT_BRACE
    }

    /**
     * Check if the current $token is a variable reference element
     * Looking for: $Alias.@Style { or similar patterns
     */
    private fun peekIsVariableReferenceElement(): Boolean {
        // Looking for: $Alias.@StyleName { or $Alias.@StyleName #Id {
        var i = current + 1
        if (i >= tokens.size) return false

        // Expect identifier after $
        if (tokens[i].type != TokenType.IDENTIFIER) return false
        i++

        // Expect .
        if (i >= tokens.size || tokens[i].type != TokenType.DOT) return false
        i++

        // Expect @
        if (i >= tokens.size || tokens[i].type != TokenType.AT) return false
        i++

        // Expect identifier
        if (i >= tokens.size || tokens[i].type != TokenType.IDENTIFIER) return false
        i++

        // Expect { or #
        if (i >= tokens.size) return false
        return tokens[i].type == TokenType.LEFT_BRACE || tokens[i].type == TokenType.HASH
    }

    /**
     * Parse element-scoped style definition: @StyleState = (...);
     */
    private fun parseElementScopedStyle(): StyleDefinition? {
        consume(TokenType.AT, "Expected '@'")
        val nameToken = consume(TokenType.IDENTIFIER, "Expected style name")

        val styleName = try {
            StyleName(nameToken.lexeme)
        } catch (e: Exception) {
            error("Invalid style name: ${e.message}")
            return null
        }

        consume(TokenType.EQUALS, "Expected '=' after style name")

        // Parse style body - can be tuple, type constructor, element-based, or simple value
        var elementChildren: List<UIElement> = emptyList()
        var typeConstructorName: String? = null
        var elementTypeName: String? = null

        val properties = when {
            check(TokenType.LEFT_PAREN) && isTupleAhead() -> parseInlineStyle()
            // Type constructor: TypeName(...)
            check(TokenType.IDENTIFIER) && peekNext()?.type == TokenType.LEFT_PAREN -> {
                typeConstructorName = advance().lexeme
                parseTypeConstructorArgs()
            }
            // Element-based style: TypeName { ... }
            check(TokenType.IDENTIFIER) && peekNext()?.type == TokenType.LEFT_BRACE -> {
                elementTypeName = advance().lexeme
                consume(TokenType.LEFT_BRACE, "Expected '{' after type name")
                val (bodyProps, bodyChildren) = parseElementBody()
                consume(TokenType.RIGHT_BRACE, "Expected '}' after style element body")
                elementChildren = bodyChildren
                bodyProps
            }
            // Simple values like @Label = ""; or @Min = 0; or expressions
            check(TokenType.STRING) || check(TokenType.NUMBER) || check(TokenType.COLOR) ||
            check(TokenType.BOOLEAN) || check(TokenType.PERCENT) || check(TokenType.LOCALIZED_KEY) ||
            check(TokenType.MINUS) || check(TokenType.IDENTIFIER) || check(TokenType.AT) ||
            check(TokenType.DOLLAR) || check(TokenType.LEFT_PAREN) -> {
                val value = parsePropertyValue()
                mapOf(PropertyName("_value") to value)
            }
            else -> {
                error("Expected '(' or value after '='")
                emptyMap()
            }
        }

        consume(TokenType.SEMICOLON, "Expected ';' after element-scoped style")

        return StyleDefinition(
            name = styleName,
            properties = properties,
            typeName = typeConstructorName,
            elementType = elementTypeName,
            children = elementChildren
        )
    }

    /**
     * Parse style-prefixed element: @FooterButton #Id { ... }
     */
    private fun parseStylePrefixedElement(): UIElement? {
        consume(TokenType.AT, "Expected '@'")
        val styleNameToken = consume(TokenType.IDENTIFIER, "Expected style name")
        val styleName = "@${styleNameToken.lexeme}"

        // Optional ID: #MyId
        val elementId = if (check(TokenType.HASH)) {
            advance() // consume '#'
            val idToken = consume(TokenType.IDENTIFIER, "Expected element ID after '#'")
            try {
                ElementId(idToken.lexeme)
            } catch (e: Exception) {
                error("Invalid element ID: ${e.message}")
                null
            }
        } else {
            null
        }

        consume(TokenType.LEFT_BRACE, "Expected '{' after style-prefixed element")

        val (properties, children) = parseElementBody()

        consume(TokenType.RIGHT_BRACE, "Expected '}' after element body")

        // Store the style reference
        val mutableProps = properties.toMutableMap()
        mutableProps[PropertyName("_stylePrefix")] = PropertyValue.Text(styleName)

        return UIElement(
            type = ElementType("_StylePrefixedElement"),
            id = elementId,
            properties = PropertyMap.of(*mutableProps.map { it.key to it.value }.toTypedArray()),
            children = children
        )
    }

    /**
     * Parse variable reference element: $AssetEditor.@Spinner {}
     */
    private fun parseVariableReferenceElement(): UIElement? {
        consume(TokenType.DOLLAR, "Expected '$'")
        val aliasToken = consume(TokenType.IDENTIFIER, "Expected alias name")
        consume(TokenType.DOT, "Expected '.'")
        consume(TokenType.AT, "Expected '@'")
        val styleNameToken = consume(TokenType.IDENTIFIER, "Expected style name")

        val variableRef = "$${aliasToken.lexeme}.@${styleNameToken.lexeme}"

        // Optional ID: #MyId
        val elementId = if (check(TokenType.HASH)) {
            advance() // consume '#'
            val idToken = consume(TokenType.IDENTIFIER, "Expected element ID after '#'")
            try {
                ElementId(idToken.lexeme)
            } catch (e: Exception) {
                error("Invalid element ID: ${e.message}")
                null
            }
        } else {
            null
        }

        consume(TokenType.LEFT_BRACE, "Expected '{' after variable reference element")

        val (properties, children) = parseElementBody()

        consume(TokenType.RIGHT_BRACE, "Expected '}' after element body")

        // Store the variable reference
        val mutableProps = properties.toMutableMap()
        mutableProps[PropertyName("_variableRef")] = PropertyValue.Text(variableRef)

        return UIElement(
            type = ElementType("_VariableRefElement"),
            id = elementId,
            properties = PropertyMap.of(*mutableProps.map { it.key to it.value }.toTypedArray()),
            children = children
        )
    }

    /**
     * Convert a StyleDefinition to a PropertyValue for storage in element properties.
     * Preserves typeName and elementType as metadata keys for round-trip fidelity.
     */
    private fun styleToPropertyValue(style: StyleDefinition): PropertyValue {
        val map = LinkedHashMap<String, PropertyValue>()

        // Preserve type constructor name for round-trip export
        if (style.typeName != null) {
            map["_typeName"] = PropertyValue.Text(style.typeName, quoted = false)
        }

        // Preserve element type for round-trip export
        if (style.elementType != null) {
            map["_elementType"] = PropertyValue.Text(style.elementType, quoted = false)
        }

        // Add all style properties
        style.properties.forEach { (k, v) -> map[k.value] = v }

        return PropertyValue.Tuple(map)
    }

    /**
     * Apply abstraction layer mapping to convert format-specific syntax to abstract elements.
     *
     * Mappings:
     * - TextButton → Button
     * - AssetImage → Image
     * - Group { LayoutMode: TopScrolling } → ScrollView
     * - Group { LayoutMode: LeftScrolling } → ScrollView (horizontal)
     * - TabNavigation → TabPanel
     *
     * See ABSTRACTION_LAYER.md for full documentation.
     */
    private fun applyAbstractionMapping(
        rawType: String,
        elementId: ElementId?,
        properties: MutableMap<PropertyName, PropertyValue>,
        children: List<UIElement>
    ): UIElement {
        // Name normalization mappings
        val mappedType = when (rawType) {
            "TextButton" -> "Button"
            "AssetImage" -> "Image"
            "TabNavigation" -> "TabPanel"
            else -> rawType
        }

        // ScrollView detection: Group with LayoutMode: TopScrolling or LeftScrolling
        val finalType = if (mappedType == "Group") {
            val layoutMode = properties[PropertyName("LayoutMode")] as? PropertyValue.Text
            when (layoutMode?.value) {
                "TopScrolling" -> {
                    // Convert to ScrollView, add Orientation property
                    properties.remove(PropertyName("LayoutMode"))
                    properties[PropertyName("Orientation")] = PropertyValue.Text("Vertical", quoted = false)
                    "ScrollView"
                }
                "LeftScrolling" -> {
                    // Convert to ScrollView, add Orientation property
                    properties.remove(PropertyName("LayoutMode"))
                    properties[PropertyName("Orientation")] = PropertyValue.Text("Horizontal", quoted = false)
                    "ScrollView"
                }
                else -> mappedType
            }
        } else {
            mappedType
        }

        return UIElement(
            type = ElementType(finalType),
            id = elementId,
            properties = PropertyMap.of(*properties.map { it.key to it.value }.toTypedArray()),
            children = children
        )
    }

    /**
     * Parse property value (string, number, color, tuple, style ref, etc.)
     * Handles arithmetic expressions with proper operator precedence.
     *
     * @param propertyName Optional property name for warning context (null for nested values like tuples)
     */
    private fun parsePropertyValue(propertyName: String? = null): PropertyValue {
        return parseAdditionSubtraction(propertyName)
    }

    /**
     * Parse addition and subtraction (lowest precedence)
     * Grammar: multiplication (('+' | '-') multiplication)*
     */
    private fun parseAdditionSubtraction(propertyName: String? = null): PropertyValue {
        var left = parseMultiplicationDivision(propertyName)

        while (check(TokenType.PLUS) || (check(TokenType.MINUS) && !isStartOfNegativeNumber())) {
            val operatorToken = advance()
            val operator = operatorToken.lexeme
            val right = parseMultiplicationDivision(propertyName)
            left = PropertyValue.Expression(left, operator, right)
        }

        return left
    }

    /**
     * Check if the current minus is the start of a negative number (not subtraction)
     * A minus is for a negative number if:
     * - It's at the start of parsing (previous token is colon, comma, paren, etc.)
     * - The next token is a number or percent
     */
    private fun isStartOfNegativeNumber(): Boolean {
        if (!check(TokenType.MINUS)) return false

        val nextType = peekNext()?.type ?: return false
        if (nextType != TokenType.NUMBER && nextType != TokenType.PERCENT) return false

        // Check if we're in a context where negative number makes sense
        // (after :, (, ,, [, or at start of value parsing)
        val prevToken = if (current > 0) tokens[current - 1] else null
        val prevType = prevToken?.type

        return prevType in listOf(
            TokenType.COLON, TokenType.COMMA, TokenType.LEFT_PAREN,
            TokenType.LEFT_BRACKET, TokenType.EQUALS, null
        )
    }

    /**
     * Parse multiplication and division (higher precedence than +/-)
     * Grammar: primary (('*' | '/') primary)*
     */
    private fun parseMultiplicationDivision(propertyName: String? = null): PropertyValue {
        var left = parsePrimaryValue(propertyName)

        while (check(TokenType.STAR) || check(TokenType.SLASH)) {
            val operatorToken = advance()
            val operator = operatorToken.lexeme
            val right = parsePrimaryValue(propertyName)
            left = PropertyValue.Expression(left, operator, right)
        }

        return left
    }

    /**
     * Check if the current '(' starts a tuple (Key: Value) rather than a grouping expression
     * A tuple starts with: (Identifier: or (...spread
     */
    private fun isTupleAhead(): Boolean {
        if (!check(TokenType.LEFT_PAREN)) return false

        // Look at what comes after '('
        val afterParen = if (current + 1 < tokens.size) tokens[current + 1] else return false

        // Check for spread operator: (...
        if (afterParen.type == TokenType.SPREAD) return true

        // Check for identifier followed by colon: (Key:
        if (afterParen.type == TokenType.IDENTIFIER) {
            val afterIdentifier = if (current + 2 < tokens.size) tokens[current + 2] else return false
            return afterIdentifier.type == TokenType.COLON
        }

        // Empty tuple: ()
        if (afterParen.type == TokenType.RIGHT_PAREN) {
            return true
        }

        return false
    }

    /**
     * Parse grouping parentheses: (expression)
     * Used for expression grouping like (@Value / 2) or (1 + 2)
     */
    private fun parseGroupingParens(): PropertyValue {
        consume(TokenType.LEFT_PAREN, "Expected '('")
        val innerValue = parsePropertyValue() // Parse the full expression inside
        consume(TokenType.RIGHT_PAREN, "Expected ')' after grouped expression")
        return innerValue
    }

    /**
     * Parse primary value (the base values: numbers, strings, style refs, etc.)
     */
    private fun parsePrimaryValue(propertyName: String? = null): PropertyValue {
        return when {
            // String
            check(TokenType.STRING) -> {
                val token = advance()
                PropertyValue.Text(token.value as? String ?: token.lexeme.trim('"'))
            }

            // Negative number: -42, -3.14
            check(TokenType.MINUS) && peekNext()?.type == TokenType.NUMBER -> {
                advance() // consume '-'
                val token = advance()
                val value = token.value as? Double ?: token.lexeme.toDouble()
                PropertyValue.Number(-value)
            }

            // Negative percentage: -10%
            check(TokenType.MINUS) && peekNext()?.type == TokenType.PERCENT -> {
                advance() // consume '-'
                val token = advance()
                val ratio = token.value as? Double ?: token.lexeme.dropLast(1).toDouble() / 100.0
                PropertyValue.Percent(-ratio)
            }

            // Number
            check(TokenType.NUMBER) -> {
                val token = advance()
                PropertyValue.Number(token.value as? Double ?: token.lexeme.toDouble())
            }

            // Percentage (e.g., 100%, 50%)
            check(TokenType.PERCENT) -> {
                val token = advance()
                // The lexer already converts the percentage to a ratio (0-100 -> 0.0-1.0)
                PropertyValue.Percent(token.value as? Double ?: token.lexeme.dropLast(1).toDouble() / 100.0)
            }

            // Boolean
            check(TokenType.BOOLEAN) -> {
                val token = advance()
                val boolValue = when (val tokenValue = token.value) {
                    is Boolean -> tokenValue
                    else -> token.lexeme == "true"
                }
                PropertyValue.Boolean(boolValue)
            }

            // Color
            check(TokenType.COLOR) -> {
                val token = advance()
                val (hex, alpha) = token.value as? Pair<*, *> ?: (token.lexeme to null)
                PropertyValue.Color(hex as String, alpha as? Float)
            }

            // Localized text: %client.assetEditor.mode.editor
            check(TokenType.LOCALIZED_KEY) -> {
                val token = advance()
                PropertyValue.LocalizedText(token.value as? String ?: token.lexeme.substring(1))
            }

            // Tuple: (Key: Value, ...) or grouping parentheses: (expression)
            check(TokenType.LEFT_PAREN) -> {
                // Distinguish between tuple and grouping expression:
                // - Tuple: (Identifier: value, ...) or (...spread)
                // - Grouping: (expression) where expression is a value token
                if (isTupleAhead()) {
                    parseTuple(propertyName)
                } else {
                    parseGroupingParens()
                }
            }

            // List: [1, 2, 3]
            check(TokenType.LEFT_BRACKET) -> {
                parseList()
            }

            // Spread operator: ...@Style or ...(Tuple)
            check(TokenType.SPREAD) -> {
                parseSpread()
            }

            // Style reference: @MyStyle
            check(TokenType.AT) -> {
                parseStyleReference()
            }

            // Variable reference: $Common.@Style or $Common.@Prop
            check(TokenType.DOLLAR) -> {
                parseVariableReference()
            }

            // Identifier (enum value, null literal, or type constructor call)
            check(TokenType.IDENTIFIER) -> {
                val token = advance()

                when {
                    // Null literal
                    token.lexeme == "null" -> PropertyValue.Null

                    // Type constructor call: Identifier(...)
                    check(TokenType.LEFT_PAREN) -> {
                        val args = parseTypeConstructorArgs()
                        PropertyValue.Tuple(args.map { (k, v) -> k.value to v }.toMap())
                    }

                    // Plain identifier (enum value) — unquoted
                    else -> PropertyValue.Text(token.lexeme, quoted = false)
                }
            }

            else -> {
                val token = peek()
                val rawValue = token.lexeme

                // Emit warning for unknown property value (don't fail parsing)
                if (propertyName != null) {
                    warn(ParseWarning.UnknownPropertyValue(propertyName, rawValue, token.position))
                } else {
                    warn(ParseWarning.UnrecognizedSyntax("Unknown value: $rawValue", token.position))
                }

                // Advance past the unknown token to continue parsing
                advance()

                PropertyValue.Unknown(rawValue)
            }
        }
    }

    /**
     * Parse spread operator: ...@StyleRef or ...value
     */
    private fun parseSpread(): PropertyValue {
        consume(TokenType.SPREAD, "Expected '...'")

        // After spread, could be a style reference or another value
        return when {
            check(TokenType.AT) -> {
                val styleValue = parseStyleReference()
                // Convert to spread
                when (styleValue) {
                    is PropertyValue.Style -> PropertyValue.Style(StyleReference.Spread(styleValue.reference))
                    else -> PropertyValue.Spread(styleValue)
                }
            }
            check(TokenType.DOLLAR) -> {
                val varRef = parseVariableReference()
                PropertyValue.Spread(varRef)
            }
            else -> {
                // Use parsePrimaryValue to avoid expression parsing inside spread
                val value = parsePrimaryValue()
                PropertyValue.Spread(value)
            }
        }
    }

    /**
     * Parse variable reference: $Alias.@StyleName or $Alias.@PropertyName
     * Can be used as:
     * - Style reference: Style: $AssetEditor.@DropdownBoxStyle
     * - Property value: Anchor: (Width: $AssetEditor.@ContextPaneWidth)
     */
    private fun parseVariableReference(): PropertyValue {
        consume(TokenType.DOLLAR, "Expected '$'")
        val aliasToken = consume(TokenType.IDENTIFIER, "Expected alias name")
        val alias = aliasToken.lexeme

        val path = mutableListOf<String>()

        // Parse the chain: .@StyleName or just .@PropName
        while (check(TokenType.DOT)) {
            advance() // consume '.'

            if (check(TokenType.AT)) {
                advance() // consume '@'
                val nameToken = consume(TokenType.IDENTIFIER, "Expected style/property name after '@'")
                path.add("@${nameToken.lexeme}")
            } else if (check(TokenType.IDENTIFIER)) {
                val nameToken = advance()
                path.add(nameToken.lexeme)
            } else {
                break
            }
        }

        // If path contains a style reference (@name), return as style reference
        // Otherwise, return as variable reference
        if (path.isNotEmpty() && path.last().startsWith("@")) {
            val styleName = path.last().substring(1) // Remove '@'
            try {
                val importAlias = ImportAlias("$$alias")
                val style = StyleName(styleName)
                return PropertyValue.Style(StyleReference.Imported(importAlias, style))
            } catch (e: Exception) {
                // Fall through to VariableRef
            }
        }

        return PropertyValue.VariableRef(alias, path)
    }

    /**
     * Parse tuple: (Left: 10, Top: 5, Width: 100)
     * Also handles spread inside tuples: (...@StyleState, Background: #000)
     */
    /** Properties that use anchor-like keys (Top/Left/Right/Bottom) but are NOT anchors. */
    private val NON_ANCHOR_PROPERTIES = setOf(
        "Padding", "Margin", "Border", "Inset", "Offset"
    )

    /** Property names whose string values should be upgraded to ImagePath. */
    private val IMAGE_PATH_PROPERTIES = setOf(
        "Source", "BackgroundImage", "Icon", "Image",
        "TexturePath", "MaskTexturePath", "BarTexturePath",
        "TabBackground", "SlotBackground"
    )

    /** Property names whose string values should be upgraded to FontPath. */
    private val FONT_PATH_PROPERTIES = setOf("Font", "FontName")

    /**
     * Upgrade a parsed property value to a semantic type based on the property name.
     * Only upgrades quoted Text values — unquoted identifiers, expressions, etc. are left as-is.
     * Applied only to top-level element properties to avoid over-upgrading inside styles/tuples.
     */
    private fun upgradePropertyValue(propertyName: String, value: PropertyValue): PropertyValue {
        if (value !is PropertyValue.Text || !value.quoted) return value
        return when (propertyName) {
            in IMAGE_PATH_PROPERTIES -> PropertyValue.ImagePath(value.value)
            in FONT_PATH_PROPERTIES -> PropertyValue.FontPath(value.value)
            else -> value
        }
    }

    private fun parseTuple(propertyName: String? = null): PropertyValue {
        consume(TokenType.LEFT_PAREN, "Expected '('")

        val entries = mutableMapOf<String, PropertyValue>()
        var spreadCount = 0

        while (!check(TokenType.RIGHT_PAREN) && !isAtEnd()) {
            // Check for spread operator at tuple entry level
            if (check(TokenType.SPREAD)) {
                val spreadValue = parseSpread()
                // Store spread with a unique key to preserve order and semantics
                entries["_spread_$spreadCount"] = spreadValue
                spreadCount++
            } else {
                val keyToken = consume(TokenType.IDENTIFIER, "Expected tuple key")
                val key = keyToken.lexeme

                consume(TokenType.COLON, "Expected ':' after tuple key")

                val value = parsePropertyValue()
                entries[key] = value
            }

            if (!check(TokenType.RIGHT_PAREN)) {
                if (!check(TokenType.COMMA)) {
                    break
                }
                advance() // consume comma
            }
        }

        consume(TokenType.RIGHT_PAREN, "Expected ')' after tuple")

        // Check if it's an Anchor (only if no spreads, and not a known non-anchor property like Padding)
        if (spreadCount == 0 && propertyName !in NON_ANCHOR_PROPERTIES && isAnchorTuple(entries)) {
            return PropertyValue.Anchor(parseAnchorFromTuple(entries))
        }

        return PropertyValue.Tuple(entries)
    }

    /**
     * Parse list: ["One", "Two", "Three"]
     */
    private fun parseList(): PropertyValue {
        consume(TokenType.LEFT_BRACKET, "Expected '['")

        val elements = mutableListOf<PropertyValue>()

        while (!check(TokenType.RIGHT_BRACKET) && !isAtEnd()) {
            elements.add(parsePropertyValue())

            if (!check(TokenType.RIGHT_BRACKET)) {
                if (!check(TokenType.COMMA)) {
                    break
                }
                advance()
            }
        }

        consume(TokenType.RIGHT_BRACKET, "Expected ']' after list")

        return PropertyValue.List(elements)
    }

    /**
     * Parse local style reference: @MyStyle
     * Note: Spread (...) and imported ($Alias.@) references are handled separately
     */
    private fun parseStyleReference(): PropertyValue {
        consume(TokenType.AT, "Expected '@' for style reference")
        val nameToken = consume(TokenType.IDENTIFIER, "Expected style name")

        val styleName = try {
            StyleName(nameToken.lexeme)
        } catch (e: Exception) {
            error("Invalid style name: ${e.message}")
            StyleName("Invalid")
        }

        return PropertyValue.Style(StyleReference.Local(styleName))
    }

    /**
     * Check if tuple is an Anchor tuple with all concrete values.
     * If any value is an expression or variable reference, return false
     * so it stays as a Tuple for later evaluation.
     */
    private fun isAnchorTuple(entries: Map<String, PropertyValue>): Boolean {
        val anchorKeys = setOf("Left", "Top", "Right", "Bottom", "Width", "Height")

        // Must have at least one anchor key
        if (!entries.keys.any { it in anchorKeys }) {
            return false
        }

        // All anchor values must be concrete (Number or Percent)
        // If any contain expressions/variables, keep as Tuple for later evaluation
        for ((key, value) in entries) {
            if (key in anchorKeys) {
                when (value) {
                    is PropertyValue.Number,
                    is PropertyValue.Percent -> {} // OK
                    else -> return false // Expression, variable ref, etc.
                }
            }
        }

        return true
    }

    /**
     * Parse Anchor from tuple
     */
    private fun parseAnchorFromTuple(entries: Map<String, PropertyValue>): AnchorValue {
        fun getDimension(key: String): AnchorDimension? {
            return when (val value = entries[key]) {
                is PropertyValue.Percent -> {
                    // Percentages are always relative (0-100% -> 0.0-1.0)
                    AnchorDimension.Relative(value.ratio.toFloat())
                }
                is PropertyValue.Number -> {
                    // Bare numbers (without %) are always absolute pixels.
                    // Only PropertyValue.Percent (e.g. "50%") produces Relative dimensions.
                    AnchorDimension.Absolute(value.value.toFloat())
                }
                else -> null
            }
        }

        // entries is a LinkedHashMap — keys().toList() preserves original parse order
        val fieldOrder = entries.keys.toList()

        return AnchorValue(
            left = getDimension("Left"),
            top = getDimension("Top"),
            right = getDimension("Right"),
            bottom = getDimension("Bottom"),
            width = getDimension("Width"),
            height = getDimension("Height"),
            fieldOrder = fieldOrder
        )
    }

    // --- Helper functions ---

    private fun check(type: TokenType): Boolean {
        if (isAtEnd()) return false
        return peek().type == type
    }

    private fun advance(): Token {
        if (!isAtEnd()) current++
        return previous()
    }

    private fun isAtEnd(): Boolean = peek().type == TokenType.EOF

    private fun peek(): Token = tokens[current]

    private fun peekNext(): Token? {
        if (current + 1 >= tokens.size) return null
        return tokens[current + 1]
    }

    private fun previous(): Token = tokens[current - 1]

    private fun consume(type: TokenType, message: String): Token {
        if (check(type)) return advance()

        error(message)
        return peek()
    }

    private fun error(message: String) {
        errors.add(ParseError.SyntaxError(message, peek().position))
    }

    private fun warn(warning: ParseWarning) {
        warningCallback?.invoke(warning)
    }
}

/**
 * Parse errors - prevent successful parsing
 */
sealed class ParseError {
    abstract val message: String
    abstract val position: com.hyve.ui.parser.lexer.Position

    data class SyntaxError(
        override val message: String,
        override val position: com.hyve.ui.parser.lexer.Position
    ) : ParseError()

    data class UnexpectedException(
        override val message: String,
        override val position: com.hyve.ui.parser.lexer.Position
    ) : ParseError()

    override fun toString(): String = "$message at $position"
}

/**
 * Parse warnings - don't prevent parsing but indicate potential issues
 */
sealed class ParseWarning {
    abstract val message: String
    abstract val position: com.hyve.ui.parser.lexer.Position

    /**
     * Warning when an unknown/unrecognized property value is encountered.
     * The value is preserved as PropertyValue.Unknown for round-trip safety.
     */
    data class UnknownPropertyValue(
        val propertyName: String,
        val rawValue: String,
        override val position: com.hyve.ui.parser.lexer.Position
    ) : ParseWarning() {
        override val message: String get() = "Unknown property value for '$propertyName': $rawValue"
    }

    /**
     * Warning when a property value type might not be recognized.
     */
    data class UnrecognizedSyntax(
        override val message: String,
        override val position: com.hyve.ui.parser.lexer.Position
    ) : ParseWarning()

    override fun toString(): String = "$message at $position"
}
