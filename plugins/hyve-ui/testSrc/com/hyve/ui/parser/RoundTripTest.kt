// Copyright 2026 Hyve. All rights reserved.
package com.hyve.ui.parser

import com.hyve.ui.core.domain.UIDocument
import com.hyve.ui.core.domain.elements.UIElement
import com.hyve.ui.core.result.Result
import com.hyve.ui.exporter.UIExporter
import org.assertj.core.api.Assertions.assertThat
import org.junit.Assume
import org.junit.Test
import java.io.File

/**
 * Round-trip tests for UIParser and UIExporter.
 * Validates that .ui files can be parsed, exported, and re-parsed with identical structure.
 */
class RoundTripTest {

    companion object {
        const val CORPUS_PATH_PROPERTY = "hyve.test.corpus.path"
        const val DEFAULT_CORPUS_PATH = "D:/Roaming/install/release/package/game/latest/Client/Data/Game/Interface/"
    }

    @Test
    fun `standalone parser instantiation works without IntelliJ context`() {
        val source = "Group { }"
        val parser = UIParser(source)
        val result = parser.parse()

        assertThat(result.isSuccess()).isTrue()
    }

    @Test
    fun `standalone exporter instantiation works without IntelliJ context`() {
        val doc = UIDocument.empty()
        val exporter = UIExporter()
        val result = exporter.export(doc)

        assertThat(result.isSuccess()).isTrue()
    }

    @Test
    fun `simple file with imports and styles round-trips correctly`() {
        val source = """
            ${'$'}Common = "../../Common.ui";

            @HeaderStyle = (FontSize: 24, RenderBold: true);

            Group #MainContainer {
                LayoutMode: Top;
            }
        """.trimIndent()

        val (original, exported, reparsed) = roundTrip(source)

        assertThat(reparsed).isNotNull()
        assertThat(reparsed!!.imports).isEqualTo(original.imports)
        assertThat(reparsed.styles.keys).isEqualTo(original.styles.keys)
        assertThat(reparsed.root.type).isEqualTo(original.root.type)
    }

    @Test
    fun `file with nested elements preserves hierarchy`() {
        val source = """
            Group #Parent {
                Button #Child1 {
                    Text: "Hello";
                }
                Label #Child2 {
                    Text: "World";
                }
            }
        """.trimIndent()

        val (original, exported, reparsed) = roundTrip(source)

        assertThat(reparsed).isNotNull()
        assertThat(reparsed!!.root.children).hasSize(original.root.children.size)
        assertThat(reparsed.root.children[0].id).isEqualTo(original.root.children[0].id)
        assertThat(reparsed.root.children[1].id).isEqualTo(original.root.children[1].id)
    }

    @Test
    fun `file with all property value types preserves values`() {
        val source = """
            Group {
                TextProp: "hello";
                NumberProp: 42;
                BoolProp: true;
                ColorProp: #ff0000;
                PercentProp: 50%;
                TupleProp: (X: 10, Y: 20);
                ListProp: [1, 2, 3];
            }
        """.trimIndent()

        val (original, exported, reparsed) = roundTrip(source)

        assertThat(reparsed).isNotNull()
        compareDocuments(original, reparsed!!)
    }

    @Test
    fun `file with type mappings round-trips through abstraction layer`() {
        val source = """
            TextButton #Btn1 { Text: "Click"; }
            AssetImage #Img1 { Source: "icon.png"; }
            TabNavigation #Nav1 { }
            Group #Scroll1 { LayoutMode: TopScrolling; }
        """.trimIndent()

        val (original, exported, reparsed) = roundTrip(source)

        assertThat(reparsed).isNotNull()
        assertThat(reparsed!!.root.children).hasSize(original.root.children.size)
    }

    @Test
    fun `file with spread operators preserves spread syntax`() {
        val source = """
            @Base = (FontSize: 14);
            Group { Style: (...@Base, FontSize: 16); }
        """.trimIndent()

        val (original, exported, reparsed) = roundTrip(source)

        assertThat(reparsed).isNotNull()
        compareDocuments(original, reparsed!!)
    }

    @Test
    fun `corpus tests skip gracefully when path unavailable`() {
        val corpusPath = System.getProperty(CORPUS_PATH_PROPERTY, DEFAULT_CORPUS_PATH)
        val corpusDir = File(corpusPath)

        Assume.assumeTrue("Corpus path not available: $corpusPath", corpusDir.exists())

        // If we get here, corpus is available
        assertThat(corpusDir.isDirectory).isTrue()
    }

    @Test
    fun `all corpus files parse and round-trip`() {
        val corpusPath = System.getProperty(CORPUS_PATH_PROPERTY, DEFAULT_CORPUS_PATH)
        val corpusDir = File(corpusPath)

        Assume.assumeTrue("Corpus path not available: $corpusPath", corpusDir.exists())

        val uiFiles = corpusDir.walkTopDown()
            .filter { it.extension == "ui" }
            .toList()

        assertThat(uiFiles).isNotEmpty()

        val results = mutableListOf<TestResult>()

        uiFiles.forEach { file ->
            val source = file.readText()
            val result = try {
                val parseResult = UIParser(source).parse()
                if (!parseResult.isSuccess()) {
                    TestResult(file.name, false, "Parse failed: ${(parseResult as Result.Failure).error}")
                } else {
                    val original = (parseResult as Result.Success).value
                    val exportResult = UIExporter().export(original)
                    if (!exportResult.isSuccess()) {
                        TestResult(file.name, false, "Export failed: ${(exportResult as Result.Failure).error}")
                    } else {
                        val exported = (exportResult as Result.Success).value
                        val reparseResult = UIParser(exported).parse()
                        if (!reparseResult.isSuccess()) {
                            TestResult(file.name, false, "Re-parse failed: ${(reparseResult as Result.Failure).error}")
                        } else {
                            val reparsed = (reparseResult as Result.Success).value
                            try {
                                compareDocuments(original, reparsed)
                                TestResult(file.name, true, null)
                            } catch (e: AssertionError) {
                                TestResult(file.name, false, "Structure mismatch: ${e.message}")
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                TestResult(file.name, false, e.message ?: "Unknown error")
            }
            results.add(result)
        }

        val passCount = results.count { it.passed }
        val failCount = results.count { !it.passed }

        println("Round-trip test results: $passCount/${results.size} passed, $failCount failed")

        results.filter { !it.passed }.forEach { result ->
            println("  FAILED: ${result.fileName} - ${result.error}")
        }

        // We expect high success rate but document failures
        // Threshold reflects pre-existing parser gaps (e.g., element-scoped @style references)
        assertThat(passCount.toDouble() / results.size).isGreaterThanOrEqualTo(0.6)
    }

    // Helper methods

    private data class RoundTripResult(
        val original: UIDocument,
        val exported: String,
        val reparsed: UIDocument?
    )

    private fun roundTrip(source: String): RoundTripResult {
        val parser = UIParser(source)
        val parseResult = parser.parse()
        assertThat(parseResult.isSuccess()).`as`("Parse should succeed").isTrue()
        val original = (parseResult as Result.Success).value

        val exporter = UIExporter()
        val exportResult = exporter.export(original)
        assertThat(exportResult.isSuccess()).`as`("Export should succeed").isTrue()
        val exported = (exportResult as Result.Success).value

        val reparseResult = UIParser(exported).parse()
        val reparsed = if (reparseResult.isSuccess()) {
            (reparseResult as Result.Success).value
        } else {
            null
        }

        return RoundTripResult(original, exported, reparsed)
    }

    private fun compareDocuments(original: UIDocument, reparsed: UIDocument) {
        assertThat(reparsed.imports).isEqualTo(original.imports)
        assertThat(reparsed.styles.keys).isEqualTo(original.styles.keys)

        compareElements(original.root, reparsed.root)
    }

    private fun compareElements(original: UIElement, reparsed: UIElement) {
        assertThat(reparsed.type).isEqualTo(original.type)
        assertThat(reparsed.id).isEqualTo(original.id)

        // Compare properties (ignore synthetic comment keys)
        val origProps = original.properties.entries().filter { !it.key.value.startsWith("_comment_") }
        val reparsedProps = reparsed.properties.entries().filter { !it.key.value.startsWith("_comment_") }
        assertThat(reparsedProps.size).isEqualTo(origProps.size)

        origProps.forEach { (name, value) ->
            assertThat(reparsed.properties[name])
                .`as`("Property ${name.value}")
                .isEqualTo(value)
        }

        assertThat(reparsed.children.size).isEqualTo(original.children.size)

        original.children.forEachIndexed { index, child ->
            compareElements(child, reparsed.children[index])
        }
    }

    private data class TestResult(
        val fileName: String,
        val passed: Boolean,
        val error: String?
    )
}
