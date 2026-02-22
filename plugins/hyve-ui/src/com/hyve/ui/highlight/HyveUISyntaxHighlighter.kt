// Copyright 2026 Hyve. All rights reserved.
package com.hyve.ui.highlight

import com.intellij.lexer.Lexer
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors
import com.intellij.openapi.editor.HighlighterColors
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.editor.colors.TextAttributesKey.createTextAttributesKey
import com.intellij.openapi.fileTypes.SyntaxHighlighterBase
import com.intellij.psi.tree.IElementType

/**
 * Syntax highlighter for Hytale .ui files.
 *
 * Maps [HyveUIElementTypes] to [TextAttributesKey] instances that fall back
 * to IntelliJ's [DefaultLanguageHighlighterColors], ensuring good defaults
 * in any color scheme while allowing customization via the color settings page.
 */
class HyveUISyntaxHighlighter : SyntaxHighlighterBase() {

    override fun getHighlightingLexer(): Lexer = HyveUILexerAdapter()

    override fun getTokenHighlights(tokenType: IElementType): Array<TextAttributesKey> {
        return pack(ATTRIBUTES[tokenType])
    }

    companion object {
        // --- TextAttributesKey definitions ---
        // Each key falls back to a sensible IntelliJ default.

        // Element type names in declarations (Group {, Label #Title {)
        val ELEMENT_TYPE_NAME = createTextAttributesKey(
            "HYTALE_UI_ELEMENT_TYPE_NAME", DefaultLanguageHighlighterColors.KEYWORD
        )

        // Other identifiers (property names, values)
        val IDENTIFIER = createTextAttributesKey(
            "HYTALE_UI_IDENTIFIER", DefaultLanguageHighlighterColors.CLASS_NAME
        )

        // String literals ("Hello World")
        val STRING = createTextAttributesKey(
            "HYTALE_UI_STRING", DefaultLanguageHighlighterColors.STRING
        )

        // Numeric literals (42, 3.14)
        val NUMBER = createTextAttributesKey(
            "HYTALE_UI_NUMBER", DefaultLanguageHighlighterColors.NUMBER
        )

        // Percentage values (100%, 50%)
        val PERCENT = createTextAttributesKey(
            "HYTALE_UI_PERCENT", DefaultLanguageHighlighterColors.NUMBER
        )

        // Color literals (#ffffff, #ff0000)
        val COLOR = createTextAttributesKey(
            "HYTALE_UI_COLOR", DefaultLanguageHighlighterColors.NUMBER
        )

        // Boolean keywords (true, false)
        val BOOLEAN = createTextAttributesKey(
            "HYTALE_UI_BOOLEAN", DefaultLanguageHighlighterColors.KEYWORD
        )

        // Localization keys (%client.assetEditor.mode.editor)
        val LOCALIZED_KEY = createTextAttributesKey(
            "HYTALE_UI_LOCALIZED_KEY", DefaultLanguageHighlighterColors.METADATA
        )

        // $ prefix (import alias / variable reference)
        val VARIABLE_PREFIX = createTextAttributesKey(
            "HYTALE_UI_VARIABLE_PREFIX", DefaultLanguageHighlighterColors.GLOBAL_VARIABLE
        )

        // @ prefix (style name)
        val STYLE_PREFIX = createTextAttributesKey(
            "HYTALE_UI_STYLE_PREFIX", DefaultLanguageHighlighterColors.METADATA
        )

        // # prefix (element ID)
        val ID_PREFIX = createTextAttributesKey(
            "HYTALE_UI_ID_PREFIX", DefaultLanguageHighlighterColors.OPERATION_SIGN
        )

        // Comments (// and /* */)
        val COMMENT = createTextAttributesKey(
            "HYTALE_UI_COMMENT", DefaultLanguageHighlighterColors.LINE_COMMENT
        )

        // Operators and punctuation
        val OPERATOR = createTextAttributesKey(
            "HYTALE_UI_OPERATOR", DefaultLanguageHighlighterColors.OPERATION_SIGN
        )
        val COLON = createTextAttributesKey(
            "HYTALE_UI_COLON", DefaultLanguageHighlighterColors.OPERATION_SIGN
        )
        val SEMICOLON = createTextAttributesKey(
            "HYTALE_UI_SEMICOLON", DefaultLanguageHighlighterColors.SEMICOLON
        )
        val COMMA = createTextAttributesKey(
            "HYTALE_UI_COMMA", DefaultLanguageHighlighterColors.COMMA
        )
        val DOT = createTextAttributesKey(
            "HYTALE_UI_DOT", DefaultLanguageHighlighterColors.DOT
        )

        // Braces, brackets, parens
        val BRACES = createTextAttributesKey(
            "HYTALE_UI_BRACES", DefaultLanguageHighlighterColors.BRACES
        )
        val BRACKETS = createTextAttributesKey(
            "HYTALE_UI_BRACKETS", DefaultLanguageHighlighterColors.BRACKETS
        )
        val PARENTHESES = createTextAttributesKey(
            "HYTALE_UI_PARENTHESES", DefaultLanguageHighlighterColors.PARENTHESES
        )

        // Bad character / error token
        val BAD_CHARACTER = createTextAttributesKey(
            "HYTALE_UI_BAD_CHARACTER", HighlighterColors.BAD_CHARACTER
        )

        private val ATTRIBUTES = mapOf<IElementType, TextAttributesKey>(
            HyveUIElementTypes.ELEMENT_TYPE_NAME to ELEMENT_TYPE_NAME,
            HyveUIElementTypes.IDENTIFIER to IDENTIFIER,
            HyveUIElementTypes.STRING to STRING,
            HyveUIElementTypes.NUMBER to NUMBER,
            HyveUIElementTypes.PERCENT to PERCENT,
            HyveUIElementTypes.COLOR to COLOR,
            HyveUIElementTypes.BOOLEAN to BOOLEAN,
            HyveUIElementTypes.LOCALIZED_KEY to LOCALIZED_KEY,
            HyveUIElementTypes.DOLLAR to VARIABLE_PREFIX,
            HyveUIElementTypes.AT to STYLE_PREFIX,
            HyveUIElementTypes.HASH to ID_PREFIX,
            HyveUIElementTypes.DOT to DOT,
            HyveUIElementTypes.COMMA to COMMA,
            HyveUIElementTypes.COLON to COLON,
            HyveUIElementTypes.SEMICOLON to SEMICOLON,
            HyveUIElementTypes.EQUALS to OPERATOR,
            HyveUIElementTypes.SPREAD to OPERATOR,
            HyveUIElementTypes.MINUS to OPERATOR,
            HyveUIElementTypes.PLUS to OPERATOR,
            HyveUIElementTypes.STAR to OPERATOR,
            HyveUIElementTypes.SLASH to OPERATOR,
            HyveUIElementTypes.LEFT_PAREN to PARENTHESES,
            HyveUIElementTypes.RIGHT_PAREN to PARENTHESES,
            HyveUIElementTypes.LEFT_BRACE to BRACES,
            HyveUIElementTypes.RIGHT_BRACE to BRACES,
            HyveUIElementTypes.LEFT_BRACKET to BRACKETS,
            HyveUIElementTypes.RIGHT_BRACKET to BRACKETS,
            HyveUIElementTypes.COMMENT to COMMENT,
            HyveUIElementTypes.ERROR to BAD_CHARACTER,
        )
    }
}
