package com.hyve.ui.parser.lexer

/**
 * Represents a token in the .ui file syntax.
 * Tokens are the basic building blocks produced by the lexer.
 */
data class Token(
    val type: TokenType,
    val lexeme: String,
    val position: Position,
    val value: Any? = null  // Parsed value for literals
) {
    override fun toString(): String = "Token($type, '$lexeme', $position)"
}

/**
 * Position in source file for error reporting
 */
data class Position(
    val line: Int,
    val column: Int,
    val offset: Int
) {
    override fun toString(): String = "$line:$column"
}

/**
 * All token types in the .ui file syntax
 */
enum class TokenType {
    // Literals
    IDENTIFIER,         // Group, Label, Button, TextField, etc.
    STRING,             // "Hello World"
    NUMBER,             // 42, 3.14
    PERCENT,            // 100%, 50%
    COLOR,              // #ffffff, #ff0000
    BOOLEAN,            // true, false
    LOCALIZED_KEY,      // %client.assetEditor.mode.editor (localization key)

    // Symbols
    DOLLAR,             // $ (import alias prefix / variable reference)
    AT,                 // @ (style name prefix)
    HASH,               // # (element ID prefix or color)
    DOT,                // . (imported style accessor / reference chain)
    COMMA,              // , (separator)
    COLON,              // : (property assignment)
    SEMICOLON,          // ; (statement terminator)
    EQUALS,             // = (import/style definition)
    SPREAD,             // ... (spread operator)
    MINUS,              // - (negative sign for numbers or subtraction)
    PLUS,               // + (addition)
    STAR,               // * (multiplication)
    SLASH,              // / (division - note: also used in comments)

    // Brackets
    LEFT_PAREN,         // ( (tuple/color alpha start)
    RIGHT_PAREN,        // ) (tuple/color alpha end)
    LEFT_BRACE,         // { (element body start)
    RIGHT_BRACE,        // } (element body end)
    LEFT_BRACKET,       // [ (list start)
    RIGHT_BRACKET,      // ] (list end)

    // Special
    COMMENT,            // // comment
    WHITESPACE,         // spaces, tabs, newlines (usually skipped except for formatting)
    EOF,                // End of file

    // Error
    ERROR               // Invalid token
}

/**
 * Keywords in the .ui syntax (currently just booleans)
 */
object Keywords {
    val keywords = mapOf(
        "true" to TokenType.BOOLEAN,
        "false" to TokenType.BOOLEAN
    )

    fun isKeyword(text: String): Boolean = text in keywords
    fun getKeywordType(text: String): TokenType? = keywords[text]
}
