package com.hyve.ui.parser.ast

import com.hyve.ui.parser.lexer.Position

/**
 * Abstract Syntax Tree nodes for .ui files.
 * These are intermediate representations before conversion to domain models.
 *
 * AST preserves source structure and comments for round-trip safety.
 */
sealed class ASTNode {
    abstract val position: Position
}

/**
 * Root node of a .ui file
 */
data class DocumentNode(
    val imports: List<ImportNode>,
    val styles: List<StyleDefinitionNode>,
    val elements: List<ElementNode>,
    val comments: List<CommentNode>,
    override val position: Position
) : ASTNode()

/**
 * Import statement: $Common = "path/to/Common.ui";
 */
data class ImportNode(
    val alias: String,              // $Common (with $)
    val path: String,               // "path/to/Common.ui"
    override val position: Position
) : ASTNode()

/**
 * Style definition: @MyStyle = (FontSize: 14, RenderBold: true);
 */
data class StyleDefinitionNode(
    val name: String,               // @MyStyle (with @)
    val properties: List<PropertyNode>,
    override val position: Position
) : ASTNode()

/**
 * UI Element: Group #MyId { ... }
 */
data class ElementNode(
    val type: String,               // Group, Label, Button, etc.
    val id: String?,                // #MyId (with #) or null
    val properties: List<PropertyNode>,
    val children: List<ElementNode>,
    override val position: Position
) : ASTNode()

/**
 * Property assignment: Text: "Hello", Anchor: (Left: 10, Top: 5)
 */
data class PropertyNode(
    val name: String,
    val value: PropertyValueNode,
    override val position: Position
) : ASTNode()

/**
 * Property values (literals, tuples, style references, etc.)
 */
sealed class PropertyValueNode : ASTNode() {
    /**
     * String literal: "Hello World"
     */
    data class StringLiteral(
        val value: String,
        override val position: Position
    ) : PropertyValueNode()

    /**
     * Number literal: 42, 3.14
     */
    data class NumberLiteral(
        val value: Double,
        override val position: Position
    ) : PropertyValueNode()

    /**
     * Boolean literal: true, false
     */
    data class BooleanLiteral(
        val value: Boolean,
        override val position: Position
    ) : PropertyValueNode()

    /**
     * Color literal: #ffffff, #ff0000(0.5)
     */
    data class ColorLiteral(
        val hex: String,            // #RRGGBB
        val alpha: Float?,          // Optional alpha (0.0 - 1.0)
        override val position: Position
    ) : PropertyValueNode()

    /**
     * Identifier (enum value, etc.): Top, Center, Bottom
     */
    data class Identifier(
        val name: String,
        override val position: Position
    ) : PropertyValueNode()

    /**
     * Tuple: (Left: 10, Top: 5, Width: 100)
     */
    data class Tuple(
        val entries: List<PropertyNode>,
        override val position: Position
    ) : PropertyValueNode()

    /**
     * List: ["One", "Two", "Three"]
     */
    data class ListValue(
        val elements: kotlin.collections.List<PropertyValueNode>,
        override val position: Position
    ) : PropertyValueNode()

    /**
     * Style reference: @MyStyle, $Common.@HeaderStyle, ...@BaseStyle
     */
    data class StyleReference(
        val alias: String?,         // $Common or null for local
        val name: String,           // @MyStyle (with @)
        val spread: Boolean,        // true if ...@MyStyle
        override val position: Position
    ) : PropertyValueNode()
}

/**
 * Comment node (preserved for round-trip)
 */
data class CommentNode(
    val text: String,
    val attachedTo: AttachmentTarget?,
    override val position: Position
) : ASTNode()

/**
 * What the comment is attached to (for formatting)
 */
sealed class AttachmentTarget {
    data class Import(val alias: String) : AttachmentTarget()
    data class Style(val name: String) : AttachmentTarget()
    data class Element(val path: List<Int>) : AttachmentTarget() // Path from root
    data object FileHeader : AttachmentTarget()
    data object FileFooter : AttachmentTarget()
}
