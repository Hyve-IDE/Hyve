package com.hyve.ui.parser.lexer

/**
 * Lexer for .ui files - converts source text into tokens.
 * Hand-written for precise control and good error messages.
 */
class Lexer(private val source: String) {
    private var current = 0
    private var line = 1
    private var column = 1
    private val tokens = mutableListOf<Token>()

    /**
     * Tokenize entire source file
     */
    fun tokenize(): List<Token> {
        skipBOM()
        while (!isAtEnd()) {
            scanToken()
        }
        addToken(TokenType.EOF, "")
        return tokens
    }

    /**
     * Skip UTF-8 BOM (Byte Order Mark) if present at start of file.
     * The BOM is the byte sequence EF BB BF, which appears as the character U+FEFF.
     */
    private fun skipBOM() {
        if (source.isNotEmpty() && source[0] == '\uFEFF') {
            current = 1
            column = 2
        }
    }

    /**
     * Scan single token
     */
    private fun scanToken() {
        val start = current
        val startLine = line
        val startColumn = column

        val c = advance()

        when (c) {
            // Single character tokens
            '$' -> addToken(TokenType.DOLLAR, "$", start, startLine, startColumn)
            '@' -> addToken(TokenType.AT, "@", start, startLine, startColumn)
            '.' -> {
                // Check for spread operator (...)
                if (match('.') && match('.')) {
                    addToken(TokenType.SPREAD, "...", start, startLine, startColumn)
                } else {
                    addToken(TokenType.DOT, ".", start, startLine, startColumn)
                }
            }
            ',' -> addToken(TokenType.COMMA, ",", start, startLine, startColumn)
            ':' -> addToken(TokenType.COLON, ":", start, startLine, startColumn)
            ';' -> addToken(TokenType.SEMICOLON, ";", start, startLine, startColumn)
            '=' -> addToken(TokenType.EQUALS, "=", start, startLine, startColumn)
            '(' -> addToken(TokenType.LEFT_PAREN, "(", start, startLine, startColumn)
            ')' -> addToken(TokenType.RIGHT_PAREN, ")", start, startLine, startColumn)
            '{' -> addToken(TokenType.LEFT_BRACE, "{", start, startLine, startColumn)
            '}' -> addToken(TokenType.RIGHT_BRACE, "}", start, startLine, startColumn)
            '[' -> addToken(TokenType.LEFT_BRACKET, "[", start, startLine, startColumn)
            ']' -> addToken(TokenType.RIGHT_BRACKET, "]", start, startLine, startColumn)

            // Color or hash ID
            '#' -> {
                // Look ahead to determine if this is a color (#RRGGBB) or hash for ID
                // Colors are exactly 6 hex digits, so we need to check ahead
                if (isValidColorAhead()) {
                    scanColor(start, startLine, startColumn)
                } else {
                    addToken(TokenType.HASH, "#", start, startLine, startColumn)
                }
            }

            // String literal
            '"' -> scanString(start, startLine, startColumn)

            // Comments or division
            '/' -> {
                when {
                    match('/') -> scanComment(start, startLine, startColumn)
                    match('*') -> scanBlockComment(start, startLine, startColumn)
                    else -> addToken(TokenType.SLASH, "/", start, startLine, startColumn)
                }
            }

            // Arithmetic operators
            '*' -> addToken(TokenType.STAR, "*", start, startLine, startColumn)
            '+' -> addToken(TokenType.PLUS, "+", start, startLine, startColumn)

            // Localization key: %client.assetEditor.mode.editor
            '%' -> {
                scanLocalizedKey(start, startLine, startColumn)
            }

            // Whitespace (preserve for round-trip, but can be filtered later)
            ' ', '\t', '\r' -> {
                // Skip whitespace but track column
            }
            '\n' -> {
                line++
                column = 1
            }

            // Numbers (including negative)
            in '0'..'9' -> scanNumber(start, startLine, startColumn)

            // Minus sign (for negative numbers)
            '-' -> addToken(TokenType.MINUS, "-", start, startLine, startColumn)

            // Identifiers and keywords
            else -> {
                if (isAlpha(c)) {
                    scanIdentifier(start, startLine, startColumn)
                } else {
                    addToken(TokenType.ERROR, c.toString(), start, startLine, startColumn)
                }
            }
        }
    }

    /**
     * Scan string literal
     */
    private fun scanString(start: Int, startLine: Int, startColumn: Int) {
        val value = StringBuilder()

        while (!isAtEnd() && peek() != '"') {
            if (peek() == '\n') {
                line++
                column = 1
            }

            // Handle escape sequences
            if (peek() == '\\' && peekNext() != '\u0000') {
                advance() // consume backslash
                val escaped = advance()
                value.append(when (escaped) {
                    'n' -> '\n'
                    't' -> '\t'
                    'r' -> '\r'
                    '"' -> '"'
                    '\\' -> '\\'
                    else -> escaped // Unknown escape, preserve as-is
                })
            } else {
                value.append(advance())
            }
        }

        if (isAtEnd()) {
            addToken(TokenType.ERROR, source.substring(start, current), start, startLine, startColumn)
            return
        }

        // Closing quote
        advance()

        val lexeme = source.substring(start, current)
        addToken(TokenType.STRING, lexeme, start, startLine, startColumn, value.toString())
    }

    /**
     * Scan number (integer or decimal) or percentage (e.g., 100%)
     */
    private fun scanNumber(start: Int, startLine: Int, startColumn: Int) {
        while (isDigit(peek())) {
            advance()
        }

        // Check for decimal
        if (peek() == '.' && isDigit(peekNext())) {
            advance() // consume '.'
            while (isDigit(peek())) {
                advance()
            }
        }

        // Check for percentage suffix
        if (peek() == '%') {
            val numLexeme = source.substring(start, current)
            val numValue = numLexeme.toDoubleOrNull()

            advance() // consume '%'
            val lexeme = source.substring(start, current)

            if (numValue != null) {
                // Store the percentage as a ratio (0-100 -> 0.0-1.0)
                addToken(TokenType.PERCENT, lexeme, start, startLine, startColumn, numValue / 100.0)
            } else {
                addToken(TokenType.ERROR, lexeme, start, startLine, startColumn)
            }
            return
        }

        val lexeme = source.substring(start, current)
        val value = lexeme.toDoubleOrNull()

        if (value != null) {
            addToken(TokenType.NUMBER, lexeme, start, startLine, startColumn, value)
        } else {
            addToken(TokenType.ERROR, lexeme, start, startLine, startColumn)
        }
    }

    /**
     * Scan color (#RRGGBB, #RRGGBBAA, or #RRGGBB(alpha))
     * Supports both 6-digit (no alpha) and 8-digit (with alpha) hex colors
     */
    private fun scanColor(start: Int, startLine: Int, startColumn: Int) {
        // Already consumed '#'
        val hexStart = current

        // Read up to 8 hex digits
        var hexDigitCount = 0
        while (hexDigitCount < 8 && isHexDigit(peek())) {
            advance()
            hexDigitCount++
        }

        // Must be exactly 6 or 8 digits
        if (hexDigitCount != 6 && hexDigitCount != 8) {
            addToken(TokenType.ERROR, source.substring(start, current), start, startLine, startColumn)
            return
        }

        val hexValue = source.substring(hexStart, current)

        if (hexDigitCount == 8) {
            // 8-digit format: #RRGGBBAA
            val rgbHex = "#${hexValue.substring(0, 6)}"
            val alphaHex = hexValue.substring(6, 8)
            val alpha = alphaHex.toInt(16) / 255.0f

            val lexeme = source.substring(start, current)
            addToken(TokenType.COLOR, lexeme, start, startLine, startColumn, Pair(rgbHex, alpha))
        } else {
            // 6-digit format: #RRGGBB with optional (alpha)
            val fullColor = "#$hexValue"

            // Check for alpha value (optional)
            if (peek() == '(') {
                advance() // consume '('
                val alphaStart = current

                // Read alpha value (number)
                while (isDigit(peek()) || peek() == '.') {
                    advance()
                }

                if (peek() != ')') {
                    addToken(TokenType.ERROR, source.substring(start, current), start, startLine, startColumn)
                    return
                }

                advance() // consume ')'

                val lexeme = source.substring(start, current)
                val alpha = source.substring(alphaStart, current - 1).toFloatOrNull()

                if (alpha != null && alpha in 0.0f..1.0f) {
                    addToken(TokenType.COLOR, lexeme, start, startLine, startColumn, Pair(fullColor, alpha))
                } else {
                    addToken(TokenType.ERROR, lexeme, start, startLine, startColumn)
                }
            } else {
                // No alpha
                addToken(TokenType.COLOR, fullColor, start, startLine, startColumn, Pair(fullColor, null))
            }
        }
    }

    /**
     * Scan identifier or keyword
     */
    private fun scanIdentifier(start: Int, startLine: Int, startColumn: Int) {
        while (isAlphaNumeric(peek())) {
            advance()
        }

        val lexeme = source.substring(start, current)
        val type = Keywords.getKeywordType(lexeme) ?: TokenType.IDENTIFIER

        val value = when (type) {
            TokenType.BOOLEAN -> lexeme == "true"
            else -> null
        }

        addToken(type, lexeme, start, startLine, startColumn, value)
    }

    /**
     * Scan comment (// to end of line)
     */
    private fun scanComment(start: Int, startLine: Int, startColumn: Int) {
        // Consume until end of line
        while (!isAtEnd() && peek() != '\n') {
            advance()
        }

        val lexeme = source.substring(start, current)
        val commentText = lexeme.substring(2).trim() // Remove "//" and trim

        addToken(TokenType.COMMENT, lexeme, start, startLine, startColumn, commentText)
    }

    /**
     * Scan block comment: slash-star ... star-slash
     * Block comments can span multiple lines
     */
    private fun scanBlockComment(start: Int, startLine: Int, startColumn: Int) {
        // Already consumed '/*', now read until '*/'
        while (!isAtEnd()) {
            if (peek() == '*' && peekNext() == '/') {
                advance() // consume '*'
                advance() // consume '/'
                break
            }
            if (peek() == '\n') {
                line++
                column = 1
            }
            advance()
        }

        val lexeme = source.substring(start, current)
        val commentText = lexeme.drop(2).dropLast(2) // Remove /* and */

        addToken(TokenType.COMMENT, lexeme, start, startLine, startColumn, commentText)
    }

    /**
     * Scan localization key: %client.assetEditor.mode.editor
     * The key can contain dots for namespacing
     */
    private fun scanLocalizedKey(start: Int, startLine: Int, startColumn: Int) {
        // Already consumed '%', now read the key (identifier with dots)
        while (isAlphaNumeric(peek()) || peek() == '.') {
            advance()
        }

        val lexeme = source.substring(start, current)
        val key = lexeme.substring(1) // Remove leading '%'

        if (key.isEmpty()) {
            addToken(TokenType.ERROR, lexeme, start, startLine, startColumn)
        } else {
            addToken(TokenType.LOCALIZED_KEY, lexeme, start, startLine, startColumn, key)
        }
    }

    // --- Helper functions ---

    private fun advance(): Char {
        val c = source[current]
        current++
        column++
        return c
    }

    private fun match(expected: Char): Boolean {
        if (isAtEnd()) return false
        if (source[current] != expected) return false

        current++
        column++
        return true
    }

    private fun peek(): Char {
        if (isAtEnd()) return '\u0000'
        return source[current]
    }

    private fun peekNext(): Char {
        if (current + 1 >= source.length) return '\u0000'
        return source[current + 1]
    }

    private fun isAtEnd(): Boolean = current >= source.length

    private fun isDigit(c: Char): Boolean = c in '0'..'9'

    private fun isHexDigit(c: Char): Boolean =
        c in '0'..'9' || c in 'a'..'f' || c in 'A'..'F'

    private fun isAlpha(c: Char): Boolean =
        c in 'a'..'z' || c in 'A'..'Z' || c == '_'

    private fun isAlphaNumeric(c: Char): Boolean = isAlpha(c) || isDigit(c)

    /**
     * Check if the next characters form a valid color (#RRGGBB or #RRGGBBAA)
     * Colors are exactly 6 or 8 hex digits, nothing more, nothing less
     */
    private fun isValidColorAhead(): Boolean {
        // Save current position
        val savedCurrent = current

        // Try to read up to 8 hex digits
        var count = 0
        while (count < 8 && current < source.length && isHexDigit(source[current])) {
            current++
            count++
        }

        // Check if we got exactly 6 or 8 digits and the next char is not a hex digit or letter
        // (to distinguish from identifiers like #Container)
        val isValid = (count == 6 || count == 8) && (current >= source.length || !isAlphaNumeric(source[current]))

        // Restore position
        current = savedCurrent

        return isValid
    }

    private fun addToken(
        type: TokenType,
        lexeme: String,
        start: Int = current - lexeme.length,
        line: Int = this.line,
        column: Int = this.column - lexeme.length,
        value: Any? = null
    ) {
        tokens.add(Token(
            type = type,
            lexeme = lexeme,
            position = Position(line, column, start),
            value = value
        ))
    }
}
