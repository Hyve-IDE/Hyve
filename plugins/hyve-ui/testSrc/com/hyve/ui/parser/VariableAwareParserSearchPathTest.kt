// Copyright 2026 Hyve. All rights reserved.
package com.hyve.ui.parser

import com.hyve.ui.core.domain.properties.PropertyValue
import com.hyve.ui.core.id.PropertyName
import com.hyve.ui.core.result.Result
import org.assertj.core.api.Assertions.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Tests for VariableAwareParser.forSourceWithPath() with importSearchPaths parameter (P0).
 * Verifies that search paths are correctly threaded to ImportResolver.
 */
class VariableAwareParserSearchPathTest {

    @Rule
    @JvmField
    val tempFolder = TemporaryFolder()

    @Test
    fun `forSourceWithPath with importSearchPaths resolves cross-directory import`() {
        // Arrange: Common.ui in vanilla dir, main source in project dir
        val vanillaDir = tempFolder.newFolder("vanilla")
        val projectDir = tempFolder.newFolder("project")

        File(vanillaDir, "Common.ui").writeText("""
            @CommonSize = 42;
            @CommonStyle = (FontSize: 18, RenderBold: true);
        """.trimIndent())

        val source = """
            ${'$'}Common = "Common.ui";

            Group {
                Width: ${'$'}Common.@CommonSize;
                Style: ${'$'}Common.@CommonStyle;
            }
        """.trimIndent()

        val mainFilePath = projectDir.toPath().resolve("Main.ui")

        // Act
        val result = VariableAwareParser.forSourceWithPath(
            source, mainFilePath,
            importSearchPaths = listOf(vanillaDir.toPath())
        ).parse()

        // Assert
        assertThat(result.isSuccess()).isTrue()
        val parsed = (result as Result.Success).value
        val widthProp = parsed.document.root.properties[PropertyName("Width")]
        assertThat(widthProp).isEqualTo(PropertyValue.Number(42.0))

        val styleProp = parsed.document.root.properties[PropertyName("Style")]
        assertThat(styleProp).isInstanceOf(PropertyValue.Tuple::class.java)
        val tuple = styleProp as PropertyValue.Tuple
        assertThat(tuple.values["FontSize"]).isEqualTo(PropertyValue.Number(18.0))
        assertThat(tuple.values["RenderBold"]).isEqualTo(PropertyValue.Boolean(true))
    }

    @Test
    fun `forSourceWithPath resolves imported PatchStyle tuple`() {
        // Arrange: Common.ui defines a PatchStyle tuple (used by TextField Background)
        val vanillaDir = tempFolder.newFolder("vanilla")
        val projectDir = tempFolder.newFolder("project")

        File(vanillaDir, "Common.ui").writeText("""
            @InputBoxBackground = (TexturePath: "textures/input_bg.png", Border: 16);
        """.trimIndent())

        val source = """
            ${'$'}Common = "Common.ui";

            TextField {
                Background: ${'$'}Common.@InputBoxBackground;
            }
        """.trimIndent()

        val mainFilePath = projectDir.toPath().resolve("Main.ui")

        // Act
        val result = VariableAwareParser.forSourceWithPath(
            source, mainFilePath,
            importSearchPaths = listOf(vanillaDir.toPath())
        ).parse()

        // Assert
        assertThat(result.isSuccess()).isTrue()
        val parsed = (result as Result.Success).value
        val bgProp = parsed.document.root.properties[PropertyName("Background")]
        assertThat(bgProp).isInstanceOf(PropertyValue.Tuple::class.java)
        val tuple = bgProp as PropertyValue.Tuple
        assertThat((tuple.values["TexturePath"] as? PropertyValue.Text)?.value).isEqualTo("textures/input_bg.png")
        assertThat((tuple.values["Border"] as? PropertyValue.Number)?.value).isEqualTo(16.0)
    }

    @Test
    fun `forSourceWithPath resolves nested style from import`() {
        // Arrange: Common.ui defines a SliderStyle with nested Background sub-tuple
        val vanillaDir = tempFolder.newFolder("vanilla")
        val projectDir = tempFolder.newFolder("project")

        File(vanillaDir, "Common.ui").writeText("""
            @DefaultSliderStyle = (
                Background: (TexturePath: "textures/slider_track.png", Border: 8),
                Handle: "textures/slider_handle.png",
                HandleWidth: 20,
                HandleHeight: 20
            );
        """.trimIndent())

        val source = """
            ${'$'}Common = "Common.ui";

            Slider {
                SliderStyle: ${'$'}Common.@DefaultSliderStyle;
            }
        """.trimIndent()

        val mainFilePath = projectDir.toPath().resolve("Main.ui")

        // Act
        val result = VariableAwareParser.forSourceWithPath(
            source, mainFilePath,
            importSearchPaths = listOf(vanillaDir.toPath())
        ).parse()

        // Assert
        assertThat(result.isSuccess()).isTrue()
        val parsed = (result as Result.Success).value
        val sliderStyleProp = parsed.document.root.properties[PropertyName("SliderStyle")]
        assertThat(sliderStyleProp).isInstanceOf(PropertyValue.Tuple::class.java)
        val tuple = sliderStyleProp as PropertyValue.Tuple

        // Background sub-tuple
        val bgTuple = tuple.values["Background"]
        assertThat(bgTuple).isInstanceOf(PropertyValue.Tuple::class.java)
        val bg = bgTuple as PropertyValue.Tuple
        assertThat((bg.values["TexturePath"] as? PropertyValue.Text)?.value).isEqualTo("textures/slider_track.png")
        assertThat((bg.values["Border"] as? PropertyValue.Number)?.value).isEqualTo(8.0)

        // Handle as text
        assertThat((tuple.values["Handle"] as? PropertyValue.Text)?.value).isEqualTo("textures/slider_handle.png")
        assertThat((tuple.values["HandleWidth"] as? PropertyValue.Number)?.value).isEqualTo(20.0)
        assertThat((tuple.values["HandleHeight"] as? PropertyValue.Number)?.value).isEqualTo(20.0)
    }

    @Test
    fun `forSourceWithPath with empty search paths skips fallback gracefully`() {
        // Arrange: import path doesn't exist relative to main, empty search paths
        val projectDir = tempFolder.newFolder("project")

        val source = """
            ${'$'}Common = "Common.ui";

            Group {
                Width: 100;
            }
        """.trimIndent()

        val mainFilePath = projectDir.toPath().resolve("Main.ui")
        val warnings = mutableListOf<String>()

        // Act
        val result = VariableAwareParser.forSourceWithPath(
            source, mainFilePath,
            importSearchPaths = emptyList()
        ) { warnings.add(it) }.parse()

        // Assert: parse succeeds (import failure is non-fatal), warning generated
        assertThat(result.isSuccess()).isTrue()
        val parsed = (result as Result.Success).value
        // Import error warnings should exist
        val allWarnings = (warnings + parsed.warnings).distinct()
        assertThat(allWarnings.any { it.contains("Common") && it.contains("not found") }).isTrue()

        // Width still resolves
        val widthProp = parsed.document.root.properties[PropertyName("Width")]
        assertThat(widthProp).isEqualTo(PropertyValue.Number(100.0))
    }

    @Test
    fun `forSourceWithPath search paths do not affect local variable resolution`() {
        // Arrange: search paths are set, but local variables should still resolve as usual
        val vanillaDir = tempFolder.newFolder("vanilla")
        val projectDir = tempFolder.newFolder("project")

        // Put a Common.ui in vanilla dir in case it's imported
        File(vanillaDir, "Common.ui").writeText("@Imported = 999;")

        val source = """
            @Size = 64;
            @Title = "Hello";
            @Style = (FontSize: 14, RenderBold: true);

            Group {
                Width: @Size;
                Text: @Title;
                Style: @Style;
            }
        """.trimIndent()

        val mainFilePath = projectDir.toPath().resolve("Main.ui")

        // Act
        val result = VariableAwareParser.forSourceWithPath(
            source, mainFilePath,
            importSearchPaths = listOf(vanillaDir.toPath())
        ).parse()

        // Assert: local variables resolve correctly, search paths don't interfere
        assertThat(result.isSuccess()).isTrue()
        val parsed = (result as Result.Success).value
        val root = parsed.document.root

        assertThat(root.properties[PropertyName("Width")]).isEqualTo(PropertyValue.Number(64.0))
        assertThat(root.properties[PropertyName("Text")]).isEqualTo(PropertyValue.Text("Hello"))

        val styleProp = root.properties[PropertyName("Style")]
        assertThat(styleProp).isInstanceOf(PropertyValue.Tuple::class.java)
        val tuple = styleProp as PropertyValue.Tuple
        assertThat(tuple.values["FontSize"]).isEqualTo(PropertyValue.Number(14.0))
    }
}
