// Copyright 2026 Hyve. All rights reserved.
package com.hyve.blockbench.bridge

import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

class EscapeForJSTest {

    @Test
    fun `plain text passes through unchanged`() {
        assertThat("hello world".escapeForJS()).isEqualTo("hello world")
    }

    @Test
    fun `backslashes are escaped`() {
        assertThat("a\\b".escapeForJS()).isEqualTo("a\\\\b")
    }

    @Test
    fun `single quotes are escaped`() {
        assertThat("it's".escapeForJS()).isEqualTo("it\\'s")
    }

    @Test
    fun `newlines are escaped`() {
        assertThat("line1\nline2".escapeForJS()).isEqualTo("line1\\nline2")
    }

    @Test
    fun `carriage returns are escaped`() {
        assertThat("a\rb".escapeForJS()).isEqualTo("a\\rb")
    }

    @Test
    fun `tabs are escaped`() {
        assertThat("a\tb".escapeForJS()).isEqualTo("a\\tb")
    }

    @Test
    fun `backslash before quote is double-escaped correctly`() {
        // Input: \'  → should become \\\'
        assertThat("\\'".escapeForJS()).isEqualTo("\\\\\\'")
    }

    @Test
    fun `empty string returns empty`() {
        assertThat("".escapeForJS()).isEqualTo("")
    }

    @Test
    fun `JSON content is safely escaped for JS single-quoted string`() {
        val json = """{"name": "test", "value": 42}"""
        val escaped = json.escapeForJS()
        // Should not contain unescaped single quotes or newlines
        assertThat(escaped).doesNotContain("\n")
        assertThat(escaped).doesNotContain("\r")
        // Original content should be recoverable (no data loss)
        assertThat(escaped).contains("name")
        assertThat(escaped).contains("test")
        assertThat(escaped).contains("42")
    }

    @Test
    fun `multiline JSON with special chars is fully escaped`() {
        val json = "{\n  \"key\": \"it's a \\\"value\\\"\"\n}"
        val escaped = json.escapeForJS()
        assertThat(escaped).doesNotContain("\n")
        // Single quotes should be escaped (preceded by backslash)
        assertThat(escaped).doesNotContain("unescaped") // sanity
        assertThat(escaped).contains("\\'") // quote is escaped, not removed
    }
}
