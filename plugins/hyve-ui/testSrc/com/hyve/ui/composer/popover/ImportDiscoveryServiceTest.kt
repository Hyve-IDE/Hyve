// Copyright 2026 Hyve. All rights reserved.
package com.hyve.ui.composer.popover

import com.hyve.ui.composer.model.ComposerPropertyType
import com.hyve.ui.core.domain.properties.PropertyValue
import com.hyve.ui.core.domain.styles.StyleDefinition
import com.hyve.ui.core.id.PropertyName
import com.hyve.ui.core.id.StyleName
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class ImportDiscoveryServiceTest {

    private val service = ImportDiscoveryService()

    @TempDir
    lateinit var tempDir: File

    @Test
    fun `should return empty list for empty directory`() {
        val result = service.discoverImports(tempDir)
        assertThat(result).isEmpty()
    }

    @Test
    fun `should return empty list for non-existent directory`() {
        val result = service.discoverImports(File("non_existent_path_xyz"))
        assertThat(result).isEmpty()
    }

    @Test
    fun `should discover file with style definitions`() {
        // Arrange
        val uiFile = File(tempDir, "Common.ui")
        uiFile.writeText("""
            @HeaderStyle = (FontSize: 24, RenderBold: true);
            @AccentColor = #ff6b00;

            Group { }
        """.trimIndent())

        // Act
        val result = service.discoverImports(tempDir)

        // Assert
        assertThat(result).hasSize(1)
        assertThat(result[0].name).isEqualTo("Common")
        assertThat(result[0].fileName).isEqualTo("Common.ui")
        assertThat(result[0].exports).hasSize(2)
    }

    @Test
    fun `should extract export names correctly`() {
        // Arrange
        val uiFile = File(tempDir, "Styles.ui")
        uiFile.writeText("""
            @DefaultButtonStyle = (Background: "ButtonBg.png", TextColor: #ffffff);

            Group { }
        """.trimIndent())

        // Act
        val result = service.discoverImports(tempDir)

        // Assert
        assertThat(result).hasSize(1)
        val exports = result[0].exports
        assertThat(exports).hasSize(1)
        assertThat(exports[0].name).isEqualTo("DefaultButtonStyle")
    }

    @Test
    fun `should infer COLOR type for color value exports`() {
        // Arrange
        val uiFile = File(tempDir, "Colors.ui")
        uiFile.writeText("""
            @PrimaryColor = #ff6b00;

            Group { }
        """.trimIndent())

        // Act
        val result = service.discoverImports(tempDir)

        // Assert
        assertThat(result).hasSize(1)
        assertThat(result[0].exports[0].type).isEqualTo(ComposerPropertyType.COLOR)
    }

    @Test
    fun `should infer NUMBER type for numeric value exports`() {
        // Arrange
        val uiFile = File(tempDir, "Sizes.ui")
        uiFile.writeText("""
            @DefaultSize = 64;

            Group { }
        """.trimIndent())

        // Act
        val result = service.discoverImports(tempDir)

        // Assert
        assertThat(result).hasSize(1)
        assertThat(result[0].exports[0].type).isEqualTo(ComposerPropertyType.NUMBER)
    }

    @Test
    fun `should infer STYLE type for tuple style exports`() {
        // Arrange
        val uiFile = File(tempDir, "Styles.ui")
        uiFile.writeText("""
            @MyStyle = (FontSize: 14, TextColor: #000000);

            Group { }
        """.trimIndent())

        // Act
        val result = service.discoverImports(tempDir)

        // Assert
        assertThat(result).hasSize(1)
        assertThat(result[0].exports[0].type).isEqualTo(ComposerPropertyType.STYLE)
    }

    @Test
    fun `should exclude files with no exports`() {
        // Arrange — a file with elements but no style definitions
        val uiFile = File(tempDir, "NoExports.ui")
        uiFile.writeText("""
            Group {
                Label { Text: "Hello"; }
            }
        """.trimIndent())

        // Act
        val result = service.discoverImports(tempDir)

        // Assert
        assertThat(result).isEmpty()
    }

    @Test
    fun `should skip files that fail to parse`() {
        // Arrange
        val validFile = File(tempDir, "Valid.ui")
        validFile.writeText("""
            @MyColor = #ff0000;
            Group { }
        """.trimIndent())

        val invalidFile = File(tempDir, "Invalid.ui")
        invalidFile.writeText("this is not valid .ui content {{{{")

        // Act
        val result = service.discoverImports(tempDir)

        // Assert — invalid file is skipped, valid file is included
        assertThat(result.map { it.name }).contains("Valid")
    }

    @Test
    fun `should exclude current file by name`() {
        // Arrange
        val file1 = File(tempDir, "Common.ui")
        file1.writeText("@Style1 = (FontSize: 14);\nGroup { }")

        val file2 = File(tempDir, "Current.ui")
        file2.writeText("@Style2 = (FontSize: 16);\nGroup { }")

        // Act
        val result = service.discoverImports(tempDir, excludeFileName = "Current.ui")

        // Assert
        assertThat(result).hasSize(1)
        assertThat(result[0].name).isEqualTo("Common")
    }

    @Test
    fun `should sort results alphabetically by name`() {
        // Arrange
        val fileC = File(tempDir, "Charlie.ui")
        fileC.writeText("@Style = (FontSize: 14);\nGroup { }")

        val fileA = File(tempDir, "Alpha.ui")
        fileA.writeText("@Style = (FontSize: 14);\nGroup { }")

        val fileB = File(tempDir, "Bravo.ui")
        fileB.writeText("@Style = (FontSize: 14);\nGroup { }")

        // Act
        val result = service.discoverImports(tempDir)

        // Assert
        assertThat(result.map { it.name }).containsExactly("Alpha", "Bravo", "Charlie")
    }

    @Test
    fun `should sort exports alphabetically by name within a file`() {
        // Arrange
        val uiFile = File(tempDir, "Styles.ui")
        uiFile.writeText("""
            @Zebra = #ff0000;
            @Alpha = #00ff00;
            @Middle = #0000ff;

            Group { }
        """.trimIndent())

        // Act
        val result = service.discoverImports(tempDir)

        // Assert
        assertThat(result).hasSize(1)
        assertThat(result[0].exports.map { it.name }).containsExactly("Alpha", "Middle", "Zebra")
    }

    @Test
    fun `should discover files in subdirectories`() {
        // Arrange
        val subDir = File(tempDir, "sub")
        subDir.mkdirs()
        val uiFile = File(subDir, "Nested.ui")
        uiFile.writeText("@Color = #ff0000;\nGroup { }")

        // Act
        val result = service.discoverImports(tempDir)

        // Assert
        assertThat(result).hasSize(1)
        assertThat(result[0].name).isEqualTo("Nested")
    }

    @Test
    fun `should ignore non-ui files`() {
        // Arrange
        val txtFile = File(tempDir, "readme.txt")
        txtFile.writeText("not a ui file")

        val jsonFile = File(tempDir, "schema.json")
        jsonFile.writeText("{}")

        // Act
        val result = service.discoverImports(tempDir)

        // Assert
        assertThat(result).isEmpty()
    }

    // -- Type inference integration tests (via .ui file content) --

    @Test
    fun `should infer TEXT type for quoted string value export`() {
        // Arrange
        val uiFile = File(tempDir, "Strings.ui")
        uiFile.writeText("@MyText = \"hello\";\n\nGroup { }")

        // Act
        val result = service.discoverImports(tempDir)

        // Assert
        assertThat(result).hasSize(1)
        assertThat(result[0].exports[0].type).isEqualTo(ComposerPropertyType.TEXT)
    }

    @Test
    fun `should infer BOOLEAN type for boolean value export`() {
        // Arrange
        val uiFile = File(tempDir, "Flags.ui")
        uiFile.writeText("@Flag = true;\n\nGroup { }")

        // Act
        val result = service.discoverImports(tempDir)

        // Assert
        assertThat(result).hasSize(1)
        assertThat(result[0].exports[0].type).isEqualTo(ComposerPropertyType.BOOLEAN)
    }

    @Test
    fun `should infer PERCENT type for percent value export`() {
        // Arrange
        val uiFile = File(tempDir, "Percents.ui")
        uiFile.writeText("@Scale = 50%;\n\nGroup { }")

        // Act
        val result = service.discoverImports(tempDir)

        // Assert
        assertThat(result).hasSize(1)
        assertThat(result[0].exports[0].type).isEqualTo(ComposerPropertyType.PERCENT)
    }

    @Test
    fun `should discover files with case-insensitive UI extension`() {
        // Arrange
        val uiFile = File(tempDir, "Uppercase.UI")
        uiFile.writeText("@Color = #ff0000;\n\nGroup { }")

        // Act
        val result = service.discoverImports(tempDir)

        // Assert
        assertThat(result).hasSize(1)
        assertThat(result[0].name).isEqualTo("Uppercase")
    }

    // -- Direct inferExportType unit tests (for parser-unreachable paths) --

    @Test
    fun `should infer STYLE for type-constructor style definition`() {
        // Arrange
        val styleDef = StyleDefinition(
            name = StyleName("MyButtonStyle"),
            properties = mapOf(
                PropertyName("Background") to PropertyValue.Color("#ffffff"),
            ),
            typeName = "TextButtonStyle",
        )

        // Act
        val result = service.inferExportType(styleDef)

        // Assert
        assertThat(result).isEqualTo(ComposerPropertyType.STYLE)
    }

    @Test
    fun `should infer STYLE for element-type style definition`() {
        // Arrange
        val styleDef = StyleDefinition(
            name = StyleName("FooterButton"),
            properties = emptyMap(),
            elementType = "TextButton",
        )

        // Act
        val result = service.inferExportType(styleDef)

        // Assert
        assertThat(result).isEqualTo(ComposerPropertyType.STYLE)
    }

    @Test
    fun `should infer IMAGE for single ImagePath value`() {
        // Arrange
        val styleDef = StyleDefinition(
            name = StyleName("BgImage"),
            properties = mapOf(
                PropertyName("_value") to PropertyValue.ImagePath("textures/bg.png"),
            ),
        )

        // Act
        val result = service.inferExportType(styleDef)

        // Assert
        assertThat(result).isEqualTo(ComposerPropertyType.IMAGE)
    }

    @Test
    fun `should infer FONT for single FontPath value`() {
        // Arrange
        val styleDef = StyleDefinition(
            name = StyleName("HeaderFont"),
            properties = mapOf(
                PropertyName("_value") to PropertyValue.FontPath("fonts/roboto.ttf"),
            ),
        )

        // Act
        val result = service.inferExportType(styleDef)

        // Assert
        assertThat(result).isEqualTo(ComposerPropertyType.FONT)
    }

    @Test
    fun `should infer STYLE as fallback for unsupported single value type`() {
        // Arrange — VariableRef is not one of the mapped types
        val styleDef = StyleDefinition(
            name = StyleName("Ref"),
            properties = mapOf(
                PropertyName("_value") to PropertyValue.Null,
            ),
        )

        // Act
        val result = service.inferExportType(styleDef)

        // Assert
        assertThat(result).isEqualTo(ComposerPropertyType.STYLE)
    }
}
