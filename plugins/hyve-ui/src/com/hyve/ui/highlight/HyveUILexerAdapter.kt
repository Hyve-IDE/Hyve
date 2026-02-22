// Copyright 2026 Hyve. All rights reserved.
package com.hyve.ui.highlight

import com.hyve.ui.parser.lexer.Lexer
import com.hyve.ui.parser.lexer.Token
import com.hyve.ui.parser.lexer.TokenType
import com.intellij.lexer.LexerBase
import com.intellij.psi.tree.IElementType

/**
 * Adapts the hand-written .ui [Lexer] to IntelliJ's [com.intellij.lexer.Lexer] interface
 * for syntax highlighting.
 *
 * Batch-tokenizes the buffer on [start] and then iterates through tokens via [advance].
 * Performs a lookahead pass to distinguish element type names (Group, Label, Button)
 * from regular identifiers (property names, values).
 */
class HyveUILexerAdapter : LexerBase() {

    private var buffer: CharSequence = ""
    private var startOffset = 0
    private var endOffset = 0
    private var tokens: List<Token> = emptyList()
    private var elementTypeIndices: Set<Int> = emptySet()
    private var index = 0

    override fun start(buffer: CharSequence, startOffset: Int, endOffset: Int, initialState: Int) {
        this.buffer = buffer
        this.startOffset = startOffset
        this.endOffset = endOffset

        val text = buffer.subSequence(startOffset, endOffset).toString()
        val lexer = Lexer(text)
        val rawTokens = lexer.tokenize()

        // Filter out EOF and build offset-adjusted token list.
        // The hand-written lexer skips whitespace, so we need to synthesize whitespace
        // tokens to fill gaps between real tokens.
        tokens = buildTokenListWithWhitespace(rawTokens, text)
        elementTypeIndices = findElementTypeNames(tokens)
        index = 0
    }

    override fun getState(): Int = 0

    override fun getTokenType(): IElementType? {
        if (index >= tokens.size) return null
        if (index in elementTypeIndices) return HyveUIElementTypes.ELEMENT_TYPE_NAME
        return HyveUIElementTypes.fromTokenType(tokens[index].type)
    }

    override fun getTokenStart(): Int {
        if (index >= tokens.size) return endOffset
        return tokens[index].position.offset + startOffset
    }

    override fun getTokenEnd(): Int {
        if (index >= tokens.size) return endOffset
        val token = tokens[index]
        return token.position.offset + token.lexeme.length + startOffset
    }

    override fun advance() {
        index++
    }

    override fun getBufferSequence(): CharSequence = buffer

    override fun getBufferEnd(): Int = endOffset

    /**
     * Identifies IDENTIFIER tokens that are element type declarations by looking ahead
     * for the pattern: IDENTIFIER [HASH IDENTIFIER] LEFT_BRACE
     *
     * Examples that match:
     * - `Group {`
     * - `Label #Title {`
     * - `Button #PlayButton {`
     */
    private fun findElementTypeNames(tokens: List<Token>): Set<Int> {
        val result = mutableSetOf<Int>()

        for (i in tokens.indices) {
            if (tokens[i].type != TokenType.IDENTIFIER) continue

            var j = i + 1
            j = skipWhitespace(j)

            if (j >= tokens.size) continue

            when (tokens[j].type) {
                // IDENTIFIER { → element type
                TokenType.LEFT_BRACE -> result.add(i)

                // IDENTIFIER # IDENTIFIER { → element type with ID
                TokenType.HASH -> {
                    j = skipWhitespace(j + 1)
                    if (j < tokens.size && tokens[j].type == TokenType.IDENTIFIER) {
                        j = skipWhitespace(j + 1)
                        if (j < tokens.size && tokens[j].type == TokenType.LEFT_BRACE) {
                            result.add(i)
                        }
                    }
                }

                else -> { /* not an element type declaration */ }
            }
        }

        return result
    }

    /** Advances index past WHITESPACE tokens. */
    private fun skipWhitespace(from: Int): Int {
        var j = from
        while (j < tokens.size && tokens[j].type == TokenType.WHITESPACE) j++
        return j
    }

    /**
     * The hand-written lexer skips whitespace characters. We need to fill gaps
     * with synthetic WHITESPACE tokens so IntelliJ gets a contiguous token stream.
     */
    private fun buildTokenListWithWhitespace(rawTokens: List<Token>, text: String): List<Token> {
        val result = mutableListOf<Token>()
        var pos = 0

        for (token in rawTokens) {
            if (token.type == TokenType.EOF) break

            val tokenStart = token.position.offset

            // Fill gap before this token with whitespace
            if (tokenStart > pos) {
                result.add(Token(
                    type = TokenType.WHITESPACE,
                    lexeme = text.substring(pos, tokenStart),
                    position = com.hyve.ui.parser.lexer.Position(0, 0, pos)
                ))
            }

            result.add(token)
            pos = tokenStart + token.lexeme.length
        }

        // Fill trailing whitespace
        if (pos < text.length) {
            result.add(Token(
                type = TokenType.WHITESPACE,
                lexeme = text.substring(pos),
                position = com.hyve.ui.parser.lexer.Position(0, 0, pos)
            ))
        }

        return result
    }
}
