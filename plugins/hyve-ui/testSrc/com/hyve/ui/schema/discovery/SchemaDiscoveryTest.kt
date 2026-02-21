// Copyright 2026 Hyve. All rights reserved.
package com.hyve.ui.schema.discovery

import org.assertj.core.api.Assertions.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Tests for expanded [SchemaDiscovery] pipeline:
 * multi-directory scan, in-memory sources, and style property mining.
 */
class SchemaDiscoveryTest {

    @Rule
    @JvmField
    val tempFolder = TemporaryFolder()

    // ---------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------

    private fun writeUiFile(dir: File, name: String, content: String): File {
        val file = File(dir, name)
        file.parentFile.mkdirs()
        file.writeText(content)
        return file
    }

    // ---------------------------------------------------------------
    // Multi-directory discovery
    // ---------------------------------------------------------------

    @Test
    fun `discoverFromSources finds elements from multiple directories`() {
        val dir1 = tempFolder.newFolder("game")
        val dir2 = tempFolder.newFolder("editor")

        writeUiFile(dir1, "panel.ui", """
            Group #Panel {
                LayoutMode: Top;
            }
        """.trimIndent())

        writeUiFile(dir2, "inspector.ui", """
            Slider #Volume {
                Value: 50;
                MinValue: 0;
                MaxValue: 100;
            }
        """.trimIndent())

        val result = SchemaDiscovery().discoverFromSources(
            directories = listOf(dir1, dir2)
        )

        assertThat(result.sourceFiles).isEqualTo(2)
        assertThat(result.uniqueElementTypes).isGreaterThanOrEqualTo(2)

        val types = result.elements.map { it.type }
        assertThat(types).contains("Group", "Slider")
    }

    @Test
    fun `discoverFromSources handles empty directory list gracefully`() {
        val result = SchemaDiscovery().discoverFromSources(
            directories = emptyList()
        )

        assertThat(result.sourceFiles).isEqualTo(0)
        assertThat(result.elements).isEmpty()
    }

    @Test
    fun `discoverFromSources skips non-existent directories`() {
        val dir1 = tempFolder.newFolder("real")
        val dir2 = File(tempFolder.root, "nonexistent")

        writeUiFile(dir1, "test.ui", """
            Label { Text: "hello"; }
        """.trimIndent())

        val result = SchemaDiscovery().discoverFromSources(
            directories = listOf(dir1, dir2)
        )

        assertThat(result.sourceFiles).isEqualTo(1)
        val types = result.elements.map { it.type }
        assertThat(types).contains("Label")
    }

    // ---------------------------------------------------------------
    // In-memory source discovery (zip simulation)
    // ---------------------------------------------------------------

    @Test
    fun `discoverFromSources processes in-memory sources`() {
        val inMemory = listOf(
            "zip/CustomUI.ui" to """
                NumberField #Amount {
                    Format: "0.00";
                    MaxDecimalPlaces: 2;
                }
            """.trimIndent()
        )

        val result = SchemaDiscovery().discoverFromSources(
            inMemorySources = inMemory
        )

        assertThat(result.sourceFiles).isEqualTo(1)
        val types = result.elements.map { it.type }
        assertThat(types).contains("NumberField")

        val nf = result.elements.first { it.type == "NumberField" }
        val propNames = nf.properties.map { it.name }
        assertThat(propNames).contains("Format", "MaxDecimalPlaces")
    }

    @Test
    fun `discoverFromSources combines directories and in-memory sources`() {
        val dir = tempFolder.newFolder("fs")
        writeUiFile(dir, "main.ui", """
            Label { Text: "hi"; }
        """.trimIndent())

        val inMemory = listOf(
            "zip/extra.ui" to """
                TextField { AutoFocus: true; }
            """.trimIndent()
        )

        val result = SchemaDiscovery().discoverFromSources(
            directories = listOf(dir),
            inMemorySources = inMemory
        )

        assertThat(result.sourceFiles).isEqualTo(2)
        val types = result.elements.map { it.type }
        assertThat(types).contains("Label", "TextField")
    }

    @Test
    fun `in-memory source parse errors are recorded with source name`() {
        val inMemory = listOf(
            "zip/broken.ui" to "{{{ invalid content"
        )

        val result = SchemaDiscovery().discoverFromSources(
            inMemorySources = inMemory
        )

        assertThat(result.parseErrors).isGreaterThan(0)
        assertThat(result.errors).anyMatch { it.file == "zip/broken.ui" }
    }

    // ---------------------------------------------------------------
    // Style property mining
    // ---------------------------------------------------------------

    @Test
    fun `typed style definitions contribute properties to matching element type`() {
        val dir = tempFolder.newFolder("styles")
        writeUiFile(dir, "slider.ui", """
            @VolStyle = SliderStyle(Handle: "handle.png", HandleWidth: 16, Fill: "fill.png");
            Slider #Vol {
                Value: 50;
            }
        """.trimIndent())

        val result = SchemaDiscovery().discoverFromSources(
            directories = listOf(dir)
        )

        val slider = result.elements.firstOrNull { it.type == "Slider" }
        assertThat(slider).isNotNull

        val propNames = slider!!.properties.map { it.name }
        assertThat(propNames).contains("Value", "Handle", "HandleWidth", "Fill")
    }

    @Test
    fun `style properties do not create phantom element types`() {
        val dir = tempFolder.newFolder("phantom")
        writeUiFile(dir, "phantom.ui", """
            @MenuStyle = PopupMenuLayerStyle(Foo: "bar");
            Label { Text: "hello"; }
        """.trimIndent())

        val result = SchemaDiscovery().discoverFromSources(
            directories = listOf(dir)
        )

        val types = result.elements.map { it.type }
        // PopupMenuLayer should NOT appear — no real element of that type
        assertThat(types).doesNotContain("PopupMenuLayer")
        assertThat(types).contains("Label")
    }

    @Test
    fun `style properties merge with existing element properties`() {
        val dir = tempFolder.newFolder("merge")
        writeUiFile(dir, "merge.ui", """
            @SStyle = SliderStyle(Fill: "bar.png");
            Slider #S {
                Value: 50;
                Fill: "override.png";
            }
        """.trimIndent())

        val result = SchemaDiscovery().discoverFromSources(
            directories = listOf(dir)
        )

        val slider = result.elements.first { it.type == "Slider" }
        val fillProp = slider.properties.first { it.name == "Fill" }
        // Occurrences should be merged (1 from element + 1 from style)
        assertThat(fillProp.occurrences).isEqualTo(2)
    }

    @Test
    fun `non-Style-suffixed type names are ignored`() {
        val dir = tempFolder.newFolder("noStyle")
        writeUiFile(dir, "test.ui", """
            @Cfg = SliderConfig(Foo: "bar");
            Slider { Value: 1; }
        """.trimIndent())

        val result = SchemaDiscovery().discoverFromSources(
            directories = listOf(dir)
        )

        val slider = result.elements.first { it.type == "Slider" }
        val propNames = slider.properties.map { it.name }
        // "Foo" should NOT be added — "SliderConfig" doesn't end in "Style"
        assertThat(propNames).doesNotContain("Foo")
    }

    // ---------------------------------------------------------------
    // Fingerprint
    // ---------------------------------------------------------------

    @Test
    fun `fingerprint round-trips through JSON`() {
        val result = DiscoveryResult(
            version = "1.0.0",
            discoveredAt = "2026-01-01",
            sourceFiles = 5,
            totalElements = 10,
            uniqueElementTypes = 3,
            totalProperties = 20,
            parseErrors = 0,
            elements = emptyList(),
            corpusFingerprint = "296:1048576:1708200000000"
        )

        val json = result.toJson()
        val restored = DiscoveryResult.fromJson(json)

        assertThat(restored.corpusFingerprint).isEqualTo("296:1048576:1708200000000")
    }

    @Test
    fun `empty fingerprint is omitted from JSON`() {
        val result = DiscoveryResult(
            version = "1.0.0",
            discoveredAt = "2026-01-01",
            sourceFiles = 0,
            totalElements = 0,
            uniqueElementTypes = 0,
            totalProperties = 0,
            parseErrors = 0,
            elements = emptyList(),
            corpusFingerprint = ""
        )

        val json = result.toJson()
        assertThat(json).doesNotContain("corpusFingerprint")

        // Deserializing should produce empty string
        val restored = DiscoveryResult.fromJson(json)
        assertThat(restored.corpusFingerprint).isEmpty()
    }

    @Test
    fun `old cache JSON without fingerprint field deserializes gracefully`() {
        val oldJson = """
            {
                "version": "1.0.0",
                "discoveredAt": "2026-01-01",
                "sourceFiles": 1,
                "totalElements": 1,
                "uniqueElementTypes": 1,
                "totalProperties": 0,
                "parseErrors": 0,
                "elements": [],
                "errors": []
            }
        """.trimIndent()

        val result = DiscoveryResult.fromJson(oldJson)
        assertThat(result.corpusFingerprint).isEmpty()
    }

    // ---------------------------------------------------------------
    // Backward compatibility
    // ---------------------------------------------------------------

    @Test
    fun `discoverFromDirectory still works`() {
        val dir = tempFolder.newFolder("compat")
        writeUiFile(dir, "test.ui", """
            Button { Text: "Click"; }
        """.trimIndent())

        val result = SchemaDiscovery().discoverFromDirectory(dir)

        assertThat(result.sourceFiles).isEqualTo(1)
        val types = result.elements.map { it.type }
        assertThat(types).contains("Button")
    }
}
