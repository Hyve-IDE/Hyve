// Copyright 2026 Hyve. All rights reserved.
package com.hyve.ui.highlight

import com.hyve.ui.HyveUILanguage
import com.hyve.ui.parser.lexer.TokenType
import com.intellij.psi.tree.IElementType

/**
 * IElementType instances for each .ui token type.
 * Maps the hand-written lexer's [TokenType] enum to IntelliJ's PSI element types.
 */
object HyveUIElementTypes {

    // Literals
    @JvmField val IDENTIFIER = IElementType("HYTALE_UI_IDENTIFIER", HyveUILanguage.INSTANCE)
    @JvmField val STRING = IElementType("HYTALE_UI_STRING", HyveUILanguage.INSTANCE)
    @JvmField val NUMBER = IElementType("HYTALE_UI_NUMBER", HyveUILanguage.INSTANCE)
    @JvmField val PERCENT = IElementType("HYTALE_UI_PERCENT", HyveUILanguage.INSTANCE)
    @JvmField val COLOR = IElementType("HYTALE_UI_COLOR", HyveUILanguage.INSTANCE)
    @JvmField val BOOLEAN = IElementType("HYTALE_UI_BOOLEAN", HyveUILanguage.INSTANCE)
    @JvmField val LOCALIZED_KEY = IElementType("HYTALE_UI_LOCALIZED_KEY", HyveUILanguage.INSTANCE)

    // Symbols
    @JvmField val DOLLAR = IElementType("HYTALE_UI_DOLLAR", HyveUILanguage.INSTANCE)
    @JvmField val AT = IElementType("HYTALE_UI_AT", HyveUILanguage.INSTANCE)
    @JvmField val HASH = IElementType("HYTALE_UI_HASH", HyveUILanguage.INSTANCE)
    @JvmField val DOT = IElementType("HYTALE_UI_DOT", HyveUILanguage.INSTANCE)
    @JvmField val COMMA = IElementType("HYTALE_UI_COMMA", HyveUILanguage.INSTANCE)
    @JvmField val COLON = IElementType("HYTALE_UI_COLON", HyveUILanguage.INSTANCE)
    @JvmField val SEMICOLON = IElementType("HYTALE_UI_SEMICOLON", HyveUILanguage.INSTANCE)
    @JvmField val EQUALS = IElementType("HYTALE_UI_EQUALS", HyveUILanguage.INSTANCE)
    @JvmField val SPREAD = IElementType("HYTALE_UI_SPREAD", HyveUILanguage.INSTANCE)
    @JvmField val MINUS = IElementType("HYTALE_UI_MINUS", HyveUILanguage.INSTANCE)
    @JvmField val PLUS = IElementType("HYTALE_UI_PLUS", HyveUILanguage.INSTANCE)
    @JvmField val STAR = IElementType("HYTALE_UI_STAR", HyveUILanguage.INSTANCE)
    @JvmField val SLASH = IElementType("HYTALE_UI_SLASH", HyveUILanguage.INSTANCE)

    // Brackets
    @JvmField val LEFT_PAREN = IElementType("HYTALE_UI_LEFT_PAREN", HyveUILanguage.INSTANCE)
    @JvmField val RIGHT_PAREN = IElementType("HYTALE_UI_RIGHT_PAREN", HyveUILanguage.INSTANCE)
    @JvmField val LEFT_BRACE = IElementType("HYTALE_UI_LEFT_BRACE", HyveUILanguage.INSTANCE)
    @JvmField val RIGHT_BRACE = IElementType("HYTALE_UI_RIGHT_BRACE", HyveUILanguage.INSTANCE)
    @JvmField val LEFT_BRACKET = IElementType("HYTALE_UI_LEFT_BRACKET", HyveUILanguage.INSTANCE)
    @JvmField val RIGHT_BRACKET = IElementType("HYTALE_UI_RIGHT_BRACKET", HyveUILanguage.INSTANCE)

    // Special
    @JvmField val COMMENT = IElementType("HYTALE_UI_COMMENT", HyveUILanguage.INSTANCE)
    @JvmField val WHITESPACE = IElementType("HYTALE_UI_WHITESPACE", HyveUILanguage.INSTANCE)

    // Contextual (resolved by lexer adapter lookahead, not the raw lexer)
    @JvmField val ELEMENT_TYPE_NAME = IElementType("HYTALE_UI_ELEMENT_TYPE_NAME", HyveUILanguage.INSTANCE)

    // Error
    @JvmField val ERROR = IElementType("HYTALE_UI_ERROR", HyveUILanguage.INSTANCE)

    private val mapping = mapOf(
        TokenType.IDENTIFIER to IDENTIFIER,
        TokenType.STRING to STRING,
        TokenType.NUMBER to NUMBER,
        TokenType.PERCENT to PERCENT,
        TokenType.COLOR to COLOR,
        TokenType.BOOLEAN to BOOLEAN,
        TokenType.LOCALIZED_KEY to LOCALIZED_KEY,
        TokenType.DOLLAR to DOLLAR,
        TokenType.AT to AT,
        TokenType.HASH to HASH,
        TokenType.DOT to DOT,
        TokenType.COMMA to COMMA,
        TokenType.COLON to COLON,
        TokenType.SEMICOLON to SEMICOLON,
        TokenType.EQUALS to EQUALS,
        TokenType.SPREAD to SPREAD,
        TokenType.MINUS to MINUS,
        TokenType.PLUS to PLUS,
        TokenType.STAR to STAR,
        TokenType.SLASH to SLASH,
        TokenType.LEFT_PAREN to LEFT_PAREN,
        TokenType.RIGHT_PAREN to RIGHT_PAREN,
        TokenType.LEFT_BRACE to LEFT_BRACE,
        TokenType.RIGHT_BRACE to RIGHT_BRACE,
        TokenType.LEFT_BRACKET to LEFT_BRACKET,
        TokenType.RIGHT_BRACKET to RIGHT_BRACKET,
        TokenType.COMMENT to COMMENT,
        TokenType.WHITESPACE to WHITESPACE,
        TokenType.ERROR to ERROR,
    )

    fun fromTokenType(tokenType: TokenType): IElementType = mapping[tokenType] ?: ERROR
}
