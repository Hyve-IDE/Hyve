// Copyright 2026 Hyve. All rights reserved.
package com.hyve.ui.composer.codegen

import com.hyve.ui.composer.model.*
import com.hyve.ui.core.id.ElementType
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

class CodeGeneratorTest {

    // -- FR-3: Element Block --

    @Test
    fun `should generate element block with type and id`() {
        // Arrange
        val element = ElementDefinition(
            type = ElementType("Button"),
            id = "SubmitBtn",
            slots = emptyList()
        )

        // Act
        val result = CodeGenerator.generateUiCode(element)

        // Assert
        assertThat(result).isEqualTo("Button #SubmitBtn {\n};")
    }

    @Test
    fun `should generate element block without id when id is empty`() {
        // Arrange
        val element = ElementDefinition(
            type = ElementType("Label"),
            id = "",
            slots = emptyList()
        )

        // Act
        val result = CodeGenerator.generateUiCode(element)

        // Assert
        assertThat(result).isEqualTo("Label {\n};")
    }

    @Test
    fun `should omit empty slots from output`() {
        // Arrange
        val element = ElementDefinition(
            type = ElementType("Button"),
            id = "btn",
            slots = listOf(
                PropertySlot("Text", ComposerPropertyType.TEXT, SlotCategory.TEXT, FillMode.LITERAL, "Hello"),
                PropertySlot("TextColor", ComposerPropertyType.COLOR, SlotCategory.APPEARANCE, FillMode.EMPTY, ""),
                PropertySlot("FontSize", ComposerPropertyType.NUMBER, SlotCategory.TEXT, FillMode.LITERAL, "14"),
            )
        )

        // Act
        val result = CodeGenerator.generateUiCode(element)

        // Assert
        assertThat(result).isEqualTo(
            """
            Button #btn {
              Text: "Hello";
              FontSize: 14;
            };
            """.trimIndent()
        )
    }

    @Test
    fun `should generate element with all empty slots as minimal block`() {
        // Arrange
        val element = ElementDefinition(
            type = ElementType("Group"),
            id = "container",
            slots = listOf(
                PropertySlot("Text", ComposerPropertyType.TEXT, SlotCategory.TEXT, FillMode.EMPTY, ""),
                PropertySlot("Visible", ComposerPropertyType.BOOLEAN, SlotCategory.STATE, FillMode.EMPTY, ""),
            )
        )

        // Act
        val result = CodeGenerator.generateUiCode(element)

        // Assert
        assertThat(result).isEqualTo("Group #container {\n};")
    }

    // -- FR-4: Value Formatting (Literals) --

    @Test
    fun `should quote literal text values`() {
        // Arrange
        val slot = PropertySlot("Text", ComposerPropertyType.TEXT, SlotCategory.TEXT, FillMode.LITERAL, "Submit")

        // Act
        val result = CodeGenerator.formatSlotValue(slot)

        // Assert
        assertThat(result).isEqualTo("\"Submit\"")
    }

    @Test
    fun `should output literal number as raw value`() {
        // Arrange
        val slot = PropertySlot("FontSize", ComposerPropertyType.NUMBER, SlotCategory.TEXT, FillMode.LITERAL, "14")

        // Act
        val result = CodeGenerator.formatSlotValue(slot)

        // Assert
        assertThat(result).isEqualTo("14")
    }

    @Test
    fun `should output literal color as raw hex`() {
        // Arrange
        val slot = PropertySlot("TextColor", ComposerPropertyType.COLOR, SlotCategory.APPEARANCE, FillMode.LITERAL, "#ff6b00")

        // Act
        val result = CodeGenerator.formatSlotValue(slot)

        // Assert
        assertThat(result).isEqualTo("#ff6b00")
    }

    @Test
    fun `should output literal boolean as raw value`() {
        // Arrange
        val slot = PropertySlot("Visible", ComposerPropertyType.BOOLEAN, SlotCategory.STATE, FillMode.LITERAL, "true")

        // Act
        val result = CodeGenerator.formatSlotValue(slot)

        // Assert
        assertThat(result).isEqualTo("true")
    }

    @Test
    fun `should output literal percent as raw number`() {
        // Arrange
        val slot = PropertySlot("Opacity", ComposerPropertyType.PERCENT, SlotCategory.APPEARANCE, FillMode.LITERAL, "100")

        // Act
        val result = CodeGenerator.formatSlotValue(slot)

        // Assert
        assertThat(result).isEqualTo("100")
    }

    @Test
    fun `should output literal image as raw filename`() {
        // Arrange
        val slot = PropertySlot("BackgroundImage", ComposerPropertyType.IMAGE, SlotCategory.APPEARANCE, FillMode.LITERAL, "Panel.png")

        // Act
        val result = CodeGenerator.formatSlotValue(slot)

        // Assert
        assertThat(result).isEqualTo("Panel.png")
    }

    @Test
    fun `should output literal font as raw filename`() {
        // Arrange
        val slot = PropertySlot("Font", ComposerPropertyType.FONT, SlotCategory.TEXT, FillMode.LITERAL, "Default.ttf")

        // Act
        val result = CodeGenerator.formatSlotValue(slot)

        // Assert
        assertThat(result).isEqualTo("Default.ttf")
    }

    @Test
    fun `should output literal tuple as raw value`() {
        // Arrange
        val slot = PropertySlot("Offset", ComposerPropertyType.TUPLE, SlotCategory.LAYOUT, FillMode.LITERAL, "(0, 0, 0)")

        // Act
        val result = CodeGenerator.formatSlotValue(slot)

        // Assert
        assertThat(result).isEqualTo("(0, 0, 0)")
    }

    // -- FR-4: Value Formatting (References) --

    @Test
    fun `should output variable reference as raw value`() {
        // Arrange
        val slot = PropertySlot("TextColor", ComposerPropertyType.COLOR, SlotCategory.APPEARANCE, FillMode.VARIABLE, "@TextWhite")

        // Act
        val result = CodeGenerator.formatSlotValue(slot)

        // Assert
        assertThat(result).isEqualTo("@TextWhite")
    }

    @Test
    fun `should output import reference as raw value`() {
        // Arrange
        val slot = PropertySlot("Style", ComposerPropertyType.STYLE, SlotCategory.APPEARANCE, FillMode.IMPORT, "\$Common.@DefaultButtonStyle")

        // Act
        val result = CodeGenerator.formatSlotValue(slot)

        // Assert
        assertThat(result).isEqualTo("\$Common.@DefaultButtonStyle")
    }

    @Test
    fun `should output localization reference as raw value`() {
        // Arrange
        val slot = PropertySlot("Text", ComposerPropertyType.TEXT, SlotCategory.TEXT, FillMode.LOCALIZATION, "%button.submit")

        // Act
        val result = CodeGenerator.formatSlotValue(slot)

        // Assert
        assertThat(result).isEqualTo("%button.submit")
    }

    @Test
    fun `should output expression as raw value`() {
        // Arrange
        val slot = PropertySlot("Width", ComposerPropertyType.NUMBER, SlotCategory.LAYOUT, FillMode.EXPRESSION, "f(@width * 2)")

        // Act
        val result = CodeGenerator.formatSlotValue(slot)

        // Assert
        assertThat(result).isEqualTo("f(@width * 2)")
    }

    // -- FR-4: Anchor Formatting --

    @Test
    fun `should format anchor with all fields`() {
        // Arrange
        val anchorValues = mapOf(
            "left" to "10",
            "top" to "20",
            "right" to "30",
            "bottom" to "40",
            "width" to "200",
            "height" to "40",
        )

        // Act
        val result = CodeGenerator.formatAnchorValue(anchorValues)

        // Assert
        assertThat(result).isEqualTo("(Left: 10, Top: 20, Right: 30, Bottom: 40, Width: 200, Height: 40)")
    }

    @Test
    fun `should format anchor with only some fields`() {
        // Arrange
        val anchorValues = mapOf(
            "left" to "10",
            "width" to "200",
        )

        // Act
        val result = CodeGenerator.formatAnchorValue(anchorValues)

        // Assert
        assertThat(result).isEqualTo("(Left: 10, Width: 200)")
    }

    @Test
    fun `should skip blank anchor field values`() {
        // Arrange
        val anchorValues = mapOf(
            "left" to "10",
            "top" to "",
            "width" to "200",
            "height" to "  ",
        )

        // Act
        val result = CodeGenerator.formatAnchorValue(anchorValues)

        // Assert
        assertThat(result).isEqualTo("(Left: 10, Width: 200)")
    }

    @Test
    fun `should format empty anchor values as empty tuple`() {
        // Arrange
        val anchorValues = emptyMap<String, String>()

        // Act
        val result = CodeGenerator.formatAnchorValue(anchorValues)

        // Assert
        assertThat(result).isEqualTo("()")
    }

    @Test
    fun `should format anchor slot in full element output`() {
        // Arrange
        val element = ElementDefinition(
            type = ElementType("Button"),
            id = "btn",
            slots = listOf(
                PropertySlot(
                    "Anchor", ComposerPropertyType.ANCHOR, SlotCategory.LAYOUT,
                    FillMode.LITERAL, "",
                    anchorValues = mapOf("left" to "10", "top" to "20", "width" to "200", "height" to "40")
                ),
            )
        )

        // Act
        val result = CodeGenerator.generateUiCode(element)

        // Assert
        assertThat(result).isEqualTo(
            """
            Button #btn {
              Anchor: (Left: 10, Top: 20, Width: 200, Height: 40);
            };
            """.trimIndent()
        )
    }

    // -- FR-2: Import Declarations --

    @Test
    fun `should generate import declarations for import-mode slots`() {
        // Arrange
        val element = ElementDefinition(
            type = ElementType("Button"),
            id = "btn",
            slots = listOf(
                PropertySlot("Style", ComposerPropertyType.STYLE, SlotCategory.APPEARANCE, FillMode.IMPORT, "\$Common.@DefaultButtonStyle"),
                PropertySlot("Text", ComposerPropertyType.TEXT, SlotCategory.TEXT, FillMode.LITERAL, "Click"),
            )
        )

        // Act
        val result = CodeGenerator.generateUiCode(element)

        // Assert
        assertThat(result).startsWith("\$Common = \".../${'$'}Common.ui\";\n\n")
        assertThat(result).contains("Style: \$Common.@DefaultButtonStyle;")
    }

    @Test
    fun `should deduplicate import aliases`() {
        // Arrange
        val element = ElementDefinition(
            type = ElementType("Button"),
            id = "btn",
            slots = listOf(
                PropertySlot("Style", ComposerPropertyType.STYLE, SlotCategory.APPEARANCE, FillMode.IMPORT, "\$Common.@Style1"),
                PropertySlot("TextColor", ComposerPropertyType.COLOR, SlotCategory.APPEARANCE, FillMode.IMPORT, "\$Common.@Style2"),
            )
        )

        // Act
        val aliases = CodeGenerator.collectImportAliases(element)

        // Assert
        assertThat(aliases).containsExactly("\$Common")
    }

    @Test
    fun `should collect multiple distinct import aliases`() {
        // Arrange
        val element = ElementDefinition(
            type = ElementType("Button"),
            id = "btn",
            slots = listOf(
                PropertySlot("Style", ComposerPropertyType.STYLE, SlotCategory.APPEARANCE, FillMode.IMPORT, "\$Common.@Style1"),
                PropertySlot("TextColor", ComposerPropertyType.COLOR, SlotCategory.APPEARANCE, FillMode.IMPORT, "\$Theme.@AccentColor"),
            )
        )

        // Act
        val aliases = CodeGenerator.collectImportAliases(element)

        // Assert
        assertThat(aliases).containsExactly("\$Common", "\$Theme")
    }

    @Test
    fun `should omit import section when no slots use imports`() {
        // Arrange
        val element = ElementDefinition(
            type = ElementType("Button"),
            id = "btn",
            slots = listOf(
                PropertySlot("Text", ComposerPropertyType.TEXT, SlotCategory.TEXT, FillMode.LITERAL, "Hello"),
            )
        )

        // Act
        val result = CodeGenerator.generateUiCode(element)

        // Assert
        assertThat(result).startsWith("Button #btn {")
    }

    // -- FR-5: Full Example (integration) --

    @Test
    fun `should generate full example matching spec output`() {
        // Arrange
        val element = ElementDefinition(
            type = ElementType("Button"),
            id = "SubmitBtn",
            slots = listOf(
                PropertySlot(
                    "Anchor", ComposerPropertyType.ANCHOR, SlotCategory.LAYOUT,
                    FillMode.LITERAL, "",
                    anchorValues = mapOf("left" to "10", "top" to "20", "width" to "200", "height" to "40")
                ),
                PropertySlot("Style", ComposerPropertyType.STYLE, SlotCategory.APPEARANCE, FillMode.IMPORT, "\$Common.@DefaultButtonStyle"),
                PropertySlot("Text", ComposerPropertyType.TEXT, SlotCategory.TEXT, FillMode.LOCALIZATION, "%button.submit"),
                PropertySlot("TextColor", ComposerPropertyType.COLOR, SlotCategory.APPEARANCE, FillMode.VARIABLE, "@TextWhite"),
                PropertySlot("FontSize", ComposerPropertyType.NUMBER, SlotCategory.TEXT, FillMode.LITERAL, "14"),
                PropertySlot("Visible", ComposerPropertyType.BOOLEAN, SlotCategory.STATE, FillMode.LITERAL, "true"),
            )
        )

        // Act
        val result = CodeGenerator.generateUiCode(element)

        // Assert
        val expected = """
            ${'$'}Common = ".../${'$'}Common.ui";

            Button #SubmitBtn {
              Anchor: (Left: 10, Top: 20, Width: 200, Height: 40);
              Style: ${'$'}Common.@DefaultButtonStyle;
              Text: %button.submit;
              TextColor: @TextWhite;
              FontSize: 14;
              Visible: true;
            };
        """.trimIndent()
        assertThat(result).isEqualTo(expected)
    }

    @Test
    fun `should handle element with only variable references and no imports`() {
        // Arrange
        val element = ElementDefinition(
            type = ElementType("Label"),
            id = "title",
            slots = listOf(
                PropertySlot("Text", ComposerPropertyType.TEXT, SlotCategory.TEXT, FillMode.VARIABLE, "@HeaderText"),
                PropertySlot("TextColor", ComposerPropertyType.COLOR, SlotCategory.APPEARANCE, FillMode.VARIABLE, "@TextWhite"),
            )
        )

        // Act
        val result = CodeGenerator.generateUiCode(element)

        // Assert
        assertThat(result).isEqualTo(
            """
            Label #title {
              Text: @HeaderText;
              TextColor: @TextWhite;
            };
            """.trimIndent()
        )
    }

    @Test
    fun `should handle mixed fill modes in single element`() {
        // Arrange
        val element = ElementDefinition(
            type = ElementType("Button"),
            id = "",
            slots = listOf(
                PropertySlot("Text", ComposerPropertyType.TEXT, SlotCategory.TEXT, FillMode.LITERAL, "Click"),
                PropertySlot("TextColor", ComposerPropertyType.COLOR, SlotCategory.APPEARANCE, FillMode.VARIABLE, "@Primary"),
                PropertySlot("Style", ComposerPropertyType.STYLE, SlotCategory.APPEARANCE, FillMode.IMPORT, "\$Lib.@BtnStyle"),
                PropertySlot("Tooltip", ComposerPropertyType.TEXT, SlotCategory.TEXT, FillMode.LOCALIZATION, "%tooltip.action"),
                PropertySlot("Width", ComposerPropertyType.NUMBER, SlotCategory.LAYOUT, FillMode.EXPRESSION, "f(@base * 2)"),
                PropertySlot("Visible", ComposerPropertyType.BOOLEAN, SlotCategory.STATE, FillMode.EMPTY, ""),
            )
        )

        // Act
        val result = CodeGenerator.generateUiCode(element)

        // Assert
        val expected = """
            ${'$'}Lib = ".../${'$'}Lib.ui";

            Button {
              Text: "Click";
              TextColor: @Primary;
              Style: ${'$'}Lib.@BtnStyle;
              Tooltip: %tooltip.action;
              Width: f(@base * 2);
            };
        """.trimIndent()
        assertThat(result).isEqualTo(expected)
    }

    // -- Minor gap coverage --

    @Test
    fun `should collect import alias without dot notation`() {
        // Arrange — value is just "$Alias" with no ".@export" suffix
        val element = ElementDefinition(
            type = ElementType("Button"),
            id = "btn",
            slots = listOf(
                PropertySlot("Style", ComposerPropertyType.STYLE, SlotCategory.APPEARANCE, FillMode.IMPORT, "\$Alias"),
            )
        )

        // Act
        val aliases = CodeGenerator.collectImportAliases(element)

        // Assert
        assertThat(aliases).containsExactly("\$Alias")
    }

    @Test
    fun `should return empty string for FillMode EMPTY`() {
        // Arrange
        val slot = PropertySlot("Text", ComposerPropertyType.TEXT, SlotCategory.TEXT, FillMode.EMPTY, "")

        // Act
        val result = CodeGenerator.formatSlotValue(slot)

        // Assert
        assertThat(result).isEmpty()
    }

    // -- Tuple Formatting --

    @Test
    fun `should format tuple from tupleValues map`() {
        // Arrange
        val tupleValues = mapOf(
            "FontSize" to "24",
            "TextColor" to "#ffffff",
            "RenderBold" to "true",
        )

        // Act
        val result = CodeGenerator.formatTupleValue(tupleValues)

        // Assert
        assertThat(result).isEqualTo("(FontSize: 24, TextColor: #ffffff, RenderBold: true)")
    }

    @Test
    fun `should format empty tuple values as empty tuple`() {
        // Arrange
        val tupleValues = emptyMap<String, String>()

        // Act
        val result = CodeGenerator.formatTupleValue(tupleValues)

        // Assert
        assertThat(result).isEqualTo("()")
    }

    @Test
    fun `should skip empty-valued fields in tuple`() {
        // Arrange
        val tupleValues = mapOf(
            "FontSize" to "24",
            "TextColor" to "",
            "RenderBold" to "true",
        )

        // Act
        val result = CodeGenerator.formatTupleValue(tupleValues)

        // Assert
        assertThat(result).isEqualTo("(FontSize: 24, RenderBold: true)")
    }

    @Test
    fun `should return empty tuple when all fields have empty values`() {
        // Arrange
        val tupleValues = mapOf(
            "FontSize" to "",
            "TextColor" to "",
        )

        // Act
        val result = CodeGenerator.formatTupleValue(tupleValues)

        // Assert
        assertThat(result).isEqualTo("()")
    }

    @Test
    fun `should quote tuple field values that contain spaces`() {
        // Arrange
        val tupleValues = mapOf(
            "FontFamily" to "Noto Sans",
        )

        // Act
        val result = CodeGenerator.formatTupleValue(tupleValues)

        // Assert
        assertThat(result).isEqualTo("(FontFamily: \"Noto Sans\")")
    }

    @Test
    fun `should not quote numeric tuple field values`() {
        // Arrange
        val tupleValues = mapOf("FontSize" to "24", "Border" to "8")

        // Act
        val result = CodeGenerator.formatTupleValue(tupleValues)

        // Assert
        assertThat(result).isEqualTo("(FontSize: 24, Border: 8)")
    }

    @Test
    fun `should not quote boolean tuple field values`() {
        // Arrange
        val tupleValues = mapOf("RenderBold" to "true", "RenderItalic" to "false")

        // Act
        val result = CodeGenerator.formatTupleValue(tupleValues)

        // Assert
        assertThat(result).isEqualTo("(RenderBold: true, RenderItalic: false)")
    }

    @Test
    fun `should not quote color tuple field values`() {
        // Arrange
        val tupleValues = mapOf("TextColor" to "#ff6b00")

        // Act
        val result = CodeGenerator.formatTupleValue(tupleValues)

        // Assert
        assertThat(result).isEqualTo("(TextColor: #ff6b00)")
    }

    @Test
    fun `should not quote nested tuple field values`() {
        // Arrange
        val tupleValues = mapOf("Border" to "(Left: 4, Top: 4)")

        // Act
        val result = CodeGenerator.formatTupleValue(tupleValues)

        // Assert
        assertThat(result).isEqualTo("(Border: (Left: 4, Top: 4))")
    }

    @Test
    fun `should preserve already-quoted field values`() {
        // Arrange
        val tupleValues = mapOf("TexturePath" to "\"button_bg.png\"")

        // Act
        val result = CodeGenerator.formatTupleValue(tupleValues)

        // Assert
        assertThat(result).isEqualTo("(TexturePath: \"button_bg.png\")")
    }

    @Test
    fun `should not quote simple unquoted string values without spaces`() {
        // Arrange
        val tupleValues = mapOf("VerticalAlignment" to "Center")

        // Act
        val result = CodeGenerator.formatTupleValue(tupleValues)

        // Assert
        assertThat(result).isEqualTo("(VerticalAlignment: Center)")
    }

    @Test
    fun `should use tupleValues when present in literal tuple slot`() {
        // Arrange
        val slot = PropertySlot(
            "Style", ComposerPropertyType.TUPLE, SlotCategory.STATE,
            FillMode.LITERAL, "FontSize: 24",
            tupleValues = mapOf("FontSize" to "24", "TextColor" to "#ffffff")
        )

        // Act
        val result = CodeGenerator.formatSlotValue(slot)

        // Assert — should use tupleValues (2 fields), not raw value (1 field)
        assertThat(result).isEqualTo("(FontSize: 24, TextColor: #ffffff)")
    }

    @Test
    fun `should fall back to raw value when tupleValues is empty for tuple slot`() {
        // Arrange — backward compatibility: raw tuple string
        val slot = PropertySlot(
            "Offset", ComposerPropertyType.TUPLE, SlotCategory.LAYOUT,
            FillMode.LITERAL, "(0, 0, 0)"
        )

        // Act
        val result = CodeGenerator.formatSlotValue(slot)

        // Assert
        assertThat(result).isEqualTo("(0, 0, 0)")
    }

    @Test
    fun `should format tuple slot in full element output`() {
        // Arrange
        val element = ElementDefinition(
            type = ElementType("Label"),
            id = "lbl",
            slots = listOf(
                PropertySlot(
                    "Style", ComposerPropertyType.TUPLE, SlotCategory.STATE,
                    FillMode.LITERAL, "",
                    tupleValues = mapOf("FontSize" to "24", "TextColor" to "#ffffff", "RenderBold" to "true")
                ),
            )
        )

        // Act
        val result = CodeGenerator.generateUiCode(element)

        // Assert
        assertThat(result).isEqualTo(
            """
            Label #lbl {
              Style: (FontSize: 24, TextColor: #ffffff, RenderBold: true);
            };
            """.trimIndent()
        )
    }

    @Test
    fun `should format element with both anchor and tuple slots`() {
        // Arrange
        val element = ElementDefinition(
            type = ElementType("Label"),
            id = "lbl",
            slots = listOf(
                PropertySlot(
                    "Anchor", ComposerPropertyType.ANCHOR, SlotCategory.LAYOUT,
                    FillMode.LITERAL, "",
                    anchorValues = mapOf("left" to "10", "top" to "20", "width" to "200", "height" to "40")
                ),
                PropertySlot(
                    "Style", ComposerPropertyType.TUPLE, SlotCategory.STATE,
                    FillMode.LITERAL, "",
                    tupleValues = mapOf("FontSize" to "24", "RenderBold" to "true")
                ),
                PropertySlot("Text", ComposerPropertyType.TEXT, SlotCategory.TEXT, FillMode.LITERAL, "Hello"),
            )
        )

        // Act
        val result = CodeGenerator.generateUiCode(element)

        // Assert
        assertThat(result).isEqualTo(
            """
            Label #lbl {
              Anchor: (Left: 10, Top: 20, Width: 200, Height: 40);
              Style: (FontSize: 24, RenderBold: true);
              Text: "Hello";
            };
            """.trimIndent()
        )
    }

    @Test
    fun `should quote tuple field values containing commas`() {
        // Arrange
        val tupleValues = mapOf("Description" to "first, second")

        // Act
        val result = CodeGenerator.formatTupleValue(tupleValues)

        // Assert
        assertThat(result).isEqualTo("(Description: \"first, second\")")
    }
}
