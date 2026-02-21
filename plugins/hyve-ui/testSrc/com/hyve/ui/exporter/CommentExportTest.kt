// Copyright 2026 Hyve. All rights reserved.
package com.hyve.ui.exporter

import com.hyve.ui.core.result.Result
import com.hyve.ui.parser.UIParser
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

/**
 * Tests for comment preservation in UIExporter.
 * Validates that file-header and standalone in-element comments round-trip correctly.
 */
class CommentExportTest {

    @Test
    fun `file-header comment appears before imports in exported text`() {
        val source = """
            // This is a header comment
            ${'$'}Common = "../../Common.ui";

            Group { }
        """.trimIndent()

        val (exported, _) = roundTrip(source)

        assertThat(exported).startsWith("// This is a header comment")
    }

    @Test
    fun `standalone comment inside element body preserved`() {
        val source = """
            Group {
                Text: "Hello";
                // This is a standalone comment
                Color: #ffffff;
            }
        """.trimIndent()

        val (exported, _) = roundTrip(source)

        assertThat(exported).contains("// This is a standalone comment")
        // Comment should appear after Text property but before Color
        val textIndex = exported.indexOf("Text:")
        val commentIndex = exported.indexOf("// This is a standalone comment")
        val colorIndex = exported.indexOf("Color:")
        assertThat(commentIndex).isGreaterThan(textIndex)
        assertThat(colorIndex).isGreaterThan(commentIndex)
    }

    @Test
    fun `multiple consecutive comment lines preserved as block`() {
        val source = """
            Group {
                // Comment line 1
                // Comment line 2
                // Comment line 3
                Text: "Hello";
            }
        """.trimIndent()

        val (exported, _) = roundTrip(source)

        assertThat(exported).contains("// Comment line 1")
        assertThat(exported).contains("// Comment line 2")
        assertThat(exported).contains("// Comment line 3")
    }

    @Test
    fun `block comment delimiters preserved`() {
        val source = """
            /* Block comment */
            Group {
                Text: "Hello";
            }
        """.trimIndent()

        val (exported, _) = roundTrip(source)

        assertThat(exported).contains("/* Block comment */")
    }

    @Test
    fun `file without comments exports unchanged`() {
        val source = """
            Group {
                Text: "Hello";
                Color: #ffffff;
            }
        """.trimIndent()

        val parser = UIParser(source)
        val original = (parser.parse() as Result.Success).value
        val exporter = UIExporter()
        val exported = (exporter.export(original) as Result.Success).value

        assertThat(exported).contains("Text:")
        assertThat(exported).contains("Color:")
        assertThat(exported).doesNotContain("//")
        assertThat(exported).doesNotContain("/*")
    }

    // Helper methods

    private fun roundTrip(source: String): Pair<String, com.hyve.ui.core.domain.UIDocument> {
        val parser = UIParser(source)
        val original = (parser.parse() as Result.Success).value
        val exporter = UIExporter()
        val exported = (exporter.export(original) as Result.Success).value
        val reparsed = (UIParser(exported).parse() as Result.Success).value
        return Pair(exported, reparsed)
    }
}
