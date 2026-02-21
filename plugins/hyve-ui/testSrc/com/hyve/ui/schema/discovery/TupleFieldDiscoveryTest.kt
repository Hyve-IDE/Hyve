// Copyright 2026 Hyve. All rights reserved.
package com.hyve.ui.schema.discovery

import com.hyve.ui.core.domain.UIDocument
import com.hyve.ui.core.result.Result
import com.hyve.ui.parser.UIParser
import org.assertj.core.api.Assertions.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Tests for [TupleFieldDiscovery] — core tuple sub-field discovery logic.
 */
class TupleFieldDiscoveryTest {

    @Rule
    @JvmField
    val tempFolder = TemporaryFolder()

    // ---------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------

    private fun writeUiFile(name: String, content: String): File {
        val file = File(tempFolder.root, name)
        file.writeText(content)
        return file
    }

    private fun parseDocument(source: String): UIDocument {
        val result = UIParser(source).parse()
        assertThat(result.isSuccess()).isTrue()
        return (result as Result.Success).value
    }

    private fun discover(vararg sources: Pair<String, String>): TupleFieldResult {
        for ((name, content) in sources) {
            writeUiFile(name, content)
        }
        return TupleFieldDiscovery().discoverFromDirectory(tempFolder.root)
    }

    // ---------------------------------------------------------------
    // Tests
    // ---------------------------------------------------------------

    @Test
    fun `discoverFromDirectory finds tuple fields in ui files`() {
        val result = discover(
            "test.ui" to """
                Label {
                    Style: (FontSize: 24, TextColor: #ffffff);
                }
            """.trimIndent()
        )

        assertThat(result.sourceFiles).isEqualTo(1)

        val styleFields = result.fieldsByProperty["Style"]
        assertThat(styleFields).isNotNull()

        val fieldNames = styleFields!!.map { it.name }
        assertThat(fieldNames).contains("FontSize", "TextColor")

        val fontSizeField = styleFields.first { it.name == "FontSize" }
        assertThat(fontSizeField.inferredType).isEqualTo("NUMBER")

        val textColorField = styleFields.first { it.name == "TextColor" }
        assertThat(textColorField.inferredType).isEqualTo("COLOR")
    }

    @Test
    fun `aggregates occurrences across multiple files`() {
        val result = discover(
            "file1.ui" to """
                Label { Style: (FontSize: 14); }
            """.trimIndent(),
            "file2.ui" to """
                Label { Style: (FontSize: 18); }
            """.trimIndent()
        )

        assertThat(result.sourceFiles).isEqualTo(2)

        val styleFields = result.fieldsByProperty["Style"]
        assertThat(styleFields).isNotNull()

        val fontSizeField = styleFields!!.first { it.name == "FontSize" }
        assertThat(fontSizeField.occurrences).isEqualTo(2)
    }

    @Test
    fun `nested tuples use composite keys`() {
        val result = discover(
            "test.ui" to """
                Label {
                    Style: (Default: (FontSize: 14));
                }
            """.trimIndent()
        )

        // Top-level: Style has a "Default" field of type TUPLE
        val styleFields = result.fieldsByProperty["Style"]
        assertThat(styleFields).isNotNull()

        val defaultField = styleFields!!.first { it.name == "Default" }
        assertThat(defaultField.inferredType).isEqualTo("TUPLE")

        // Nested: Style.Default has a "FontSize" field of type NUMBER
        val nestedFields = result.fieldsByProperty["Style.Default"]
        assertThat(nestedFields).isNotNull()

        val fontSizeField = nestedFields!!.first { it.name == "FontSize" }
        assertThat(fontSizeField.inferredType).isEqualTo("NUMBER")
    }

    @Test
    fun `recursion stops at depth 2`() {
        // Depth 0: P → field B (TUPLE)
        // Depth 1: P.B → field C (TUPLE)
        // Depth 2: should NOT recurse into C's children → P.B.C should not exist
        val result = discover(
            "test.ui" to """
                Group {
                    P: (B: (C: (D: 1)));
                }
            """.trimIndent()
        )

        assertThat(result.fieldsByProperty).containsKey("P")
        assertThat(result.fieldsByProperty).containsKey("P.B")
        assertThat(result.fieldsByProperty).doesNotContainKey("P.B.C")
    }

    @Test
    fun `variable refs are skipped during type inference`() {
        // $Common.@Styles.FontSize → parsed as VariableRef → skipped by updateInferredType
        val result = discover(
            "test.ui" to """
                ${'$'}Common = "../Common.ui";
                Label {
                    Style: (FontSize: ${'$'}Common.@Styles.FontSize);
                }
            """.trimIndent()
        )

        val styleFields = result.fieldsByProperty["Style"]
        assertThat(styleFields).isNotNull()

        val fontSizeField = styleFields!!.first { it.name == "FontSize" }
        // VariableRef is skipped → type stays UNKNOWN
        assertThat(fontSizeField.inferredType).isEqualTo("UNKNOWN")
    }

    @Test
    fun `mixed types produce ANY`() {
        val result = discover(
            "file1.ui" to """
                Group { P: (X: 10); }
            """.trimIndent(),
            "file2.ui" to """
                Group { P: (X: "text"); }
            """.trimIndent()
        )

        val fields = result.fieldsByProperty["P"]
        assertThat(fields).isNotNull()

        val xField = fields!!.first { it.name == "X" }
        assertThat(xField.inferredType).isEqualTo("ANY")
    }

    @Test
    fun `observed values capped at 10`() {
        // Create 20 files each with a unique FontSize value
        val sources = (1..20).map { i ->
            "file$i.ui" to "Label { Style: (FontSize: $i); }"
        }.toTypedArray()

        val result = discover(*sources)

        val styleFields = result.fieldsByProperty["Style"]
        assertThat(styleFields).isNotNull()

        val fontSizeField = styleFields!!.first { it.name == "FontSize" }
        assertThat(fontSizeField.observedValues).hasSizeLessThanOrEqualTo(10)
        assertThat(fontSizeField.occurrences).isEqualTo(20)
    }

    @Test
    fun `empty directory returns empty result`() {
        val result = TupleFieldDiscovery().discoverFromDirectory(tempFolder.root)

        assertThat(result.fieldsByProperty).isEmpty()
        assertThat(result.sourceFiles).isEqualTo(0)
    }

    @Test
    fun `unparseable files are silently skipped`() {
        writeUiFile("bad.ui", "{{{bad content not valid")

        val result = TupleFieldDiscovery().discoverFromDirectory(tempFolder.root)

        assertThat(result.fieldsByProperty).isEmpty()
        assertThat(result.sourceFiles).isEqualTo(1)
    }

    @Test
    fun `discoverFromDocuments works with pre-parsed docs`() {
        val source = """
            Label {
                Style: (FontSize: 24, TextColor: #ffffff);
            }
        """.trimIndent()

        val doc = parseDocument(source)
        val result = TupleFieldDiscovery().discoverFromDocuments(listOf(doc))

        assertThat(result.sourceFiles).isEqualTo(1)

        val styleFields = result.fieldsByProperty["Style"]
        assertThat(styleFields).isNotNull()

        val fieldNames = styleFields!!.map { it.name }
        assertThat(fieldNames).contains("FontSize", "TextColor")
    }

    @Test
    fun `style definitions with tuple-valued properties contribute tuple fields`() {
        // Style definition whose properties include a nested tuple
        val result = discover(
            "test.ui" to """
                @MyStyle = (Background: (TexturePath: "foo.png", Border: 2));
                Group { }
            """.trimIndent()
        )

        val bgFields = result.fieldsByProperty["Background"]
        assertThat(bgFields).isNotNull()

        val fieldNames = bgFields!!.map { it.name }
        assertThat(fieldNames).contains("TexturePath", "Border")

        assertThat(bgFields.first { it.name == "TexturePath" }.inferredType).isEqualTo("TEXT")
        assertThat(bgFields.first { it.name == "Border" }.inferredType).isEqualTo("NUMBER")
    }

    @Test
    fun `non-tuple properties are ignored`() {
        val result = discover(
            "test.ui" to """
                Label {
                    Text: "hello";
                    Visible: true;
                    FontSize: 14;
                }
            """.trimIndent()
        )

        // No tuple properties → no fields discovered
        assertThat(result.fieldsByProperty).isEmpty()
    }

    // ---------------------------------------------------------------
    // Multi-directory + in-memory source tests
    // ---------------------------------------------------------------

    @Test
    fun `discoverFromSources combines directories and in-memory sources`() {
        val dir1 = tempFolder.newFolder("dir1")
        File(dir1, "a.ui").writeText("Label { Style: (FontSize: 14); }")

        val dir2 = tempFolder.newFolder("dir2")
        File(dir2, "b.ui").writeText("Label { Style: (TextColor: #ff0000); }")

        val inMemory = listOf(
            "zip/c.ui" to "Label { Style: (RenderBold: true); }"
        )

        val result = TupleFieldDiscovery().discoverFromSources(
            directories = listOf(dir1, dir2),
            inMemorySources = inMemory
        )

        assertThat(result.sourceFiles).isEqualTo(3)

        val styleFields = result.fieldsByProperty["Style"]
        assertThat(styleFields).isNotNull()

        val fieldNames = styleFields!!.map { it.name }
        assertThat(fieldNames).contains("FontSize", "TextColor", "RenderBold")
    }

    @Test
    fun `discoverFromSources with only in-memory sources`() {
        val inMemory = listOf(
            "a.ui" to "Group { Background: (TexturePath: \"bg.png\"); }"
        )

        val result = TupleFieldDiscovery().discoverFromSources(
            inMemorySources = inMemory
        )

        assertThat(result.sourceFiles).isEqualTo(1)
        val bgFields = result.fieldsByProperty["Background"]
        assertThat(bgFields).isNotNull()
        assertThat(bgFields!!.first { it.name == "TexturePath" }.inferredType).isEqualTo("TEXT")
    }
}
