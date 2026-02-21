// Copyright 2026 Hyve. All rights reserved.
package com.hyve.ui.composer.model

import com.hyve.ui.core.id.ElementType
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

class ValidationTest {

    // -- Test helpers --

    private fun element(
        type: String = "Button",
        id: String = "testId",
        slots: List<PropertySlot> = emptyList(),
    ) = ElementDefinition(
        type = ElementType(type),
        id = id,
        slots = slots,
    )

    private fun slot(
        name: String = "TestProp",
        type: ComposerPropertyType = ComposerPropertyType.TEXT,
        fillMode: FillMode = FillMode.EMPTY,
        value: String = "",
        required: Boolean = false,
    ) = PropertySlot(
        name = name,
        type = type,
        category = SlotCategory.APPEARANCE,
        fillMode = fillMode,
        value = value,
        required = required,
    )

    private fun wordBankItem(
        name: String,
        kind: WordBankKind,
        type: ComposerPropertyType = ComposerPropertyType.TEXT,
    ) = WordBankItem(id = name, name = name, type = type, kind = kind)

    // ========================================================================
    // FR-2: Missing Required Properties
    // ========================================================================

    @Test
    fun `should report error for required empty slot`() {
        // Arrange
        val el = element(slots = listOf(
            slot(name = "Text", required = true, fillMode = FillMode.EMPTY)
        ))

        // Act
        val result = validate(el, emptyList())

        // Assert
        assertThat(result).hasSize(1)
        assertThat(result[0].severity).isEqualTo(ProblemSeverity.ERROR)
        assertThat(result[0].message).isEqualTo("Required property \"Text\" is not set")
        assertThat(result[0].property).isEqualTo("Text")
    }

    @Test
    fun `should not report error for required filled slot`() {
        val el = element(slots = listOf(
            slot(name = "Text", required = true, fillMode = FillMode.LITERAL, value = "Hello")
        ))
        val result = validate(el, emptyList())
        assertThat(result.filter { it.message.contains("Required") }).isEmpty()
    }

    @Test
    fun `should not report error for optional empty slot`() {
        val el = element(slots = listOf(
            slot(name = "Text", required = false, fillMode = FillMode.EMPTY)
        ))
        val result = validate(el, emptyList())
        assertThat(result.filter { it.message.contains("Required") }).isEmpty()
    }

    @Test
    fun `should report errors for multiple required empty slots`() {
        val el = element(slots = listOf(
            slot(name = "Text", required = true, fillMode = FillMode.EMPTY),
            slot(name = "Style", required = true, fillMode = FillMode.EMPTY, type = ComposerPropertyType.STYLE),
        ))
        val result = validate(el, emptyList())
        val requiredErrors = result.filter { it.message.contains("Required") }
        assertThat(requiredErrors).hasSize(2)
    }

    @Test
    fun `should not report required error for variable-filled required slot`() {
        val el = element(slots = listOf(
            slot(name = "Text", required = true, fillMode = FillMode.VARIABLE, value = "@someVar")
        ))
        val items = listOf(wordBankItem("@someVar", WordBankKind.VARIABLE))
        val result = validate(el, items)
        assertThat(result.filter { it.message.contains("Required") }).isEmpty()
    }

    // ========================================================================
    // FR-3: Missing Element ID
    // ========================================================================

    @Test
    fun `should warn for Button without id`() {
        val el = element(type = "Button", id = "")
        val result = validate(el, emptyList())
        assertThat(result).anyMatch {
            it.severity == ProblemSeverity.WARNING &&
                it.message == "Button has no #id \u2014 Java event binding requires an ID"
        }
    }

    @Test
    fun `should warn for TextField without id`() {
        val el = element(type = "TextField", id = "")
        val result = validate(el, emptyList())
        assertThat(result).anyMatch { it.message.contains("TextField has no #id") }
    }

    @Test
    fun `should warn for CheckBox without id`() {
        val el = element(type = "CheckBox", id = "")
        val result = validate(el, emptyList())
        assertThat(result).anyMatch { it.message.contains("CheckBox has no #id") }
    }

    @Test
    fun `should warn for Slider without id`() {
        val el = element(type = "Slider", id = "")
        val result = validate(el, emptyList())
        assertThat(result).anyMatch { it.message.contains("Slider has no #id") }
    }

    @Test
    fun `should warn for DropdownBox without id`() {
        val el = element(type = "DropdownBox", id = "")
        val result = validate(el, emptyList())
        assertThat(result).anyMatch { it.message.contains("DropdownBox has no #id") }
    }

    @Test
    fun `should not warn for Label without id`() {
        val el = element(type = "Label", id = "")
        val result = validate(el, emptyList())
        assertThat(result.filter { it.message.contains("has no #id") }).isEmpty()
    }

    @Test
    fun `should not warn for Group without id`() {
        val el = element(type = "Group", id = "")
        val result = validate(el, emptyList())
        assertThat(result.filter { it.message.contains("has no #id") }).isEmpty()
    }

    @Test
    fun `should not warn for Image without id`() {
        val el = element(type = "Image", id = "")
        val result = validate(el, emptyList())
        assertThat(result.filter { it.message.contains("has no #id") }).isEmpty()
    }

    @Test
    fun `should not warn for ProgressBar without id`() {
        val el = element(type = "ProgressBar", id = "")
        val result = validate(el, emptyList())
        assertThat(result.filter { it.message.contains("has no #id") }).isEmpty()
    }

    @Test
    fun `should not warn for interactive type with id`() {
        val el = element(type = "Button", id = "myBtn")
        val result = validate(el, emptyList())
        assertThat(result.filter { it.message.contains("has no #id") }).isEmpty()
    }

    @Test
    fun `should have null property for missing id warning`() {
        val el = element(type = "Button", id = "")
        val result = validate(el, emptyList())
        val idWarning = result.first { it.message.contains("has no #id") }
        assertThat(idWarning.property).isNull()
    }

    // ========================================================================
    // FR-4: Undefined Variable References
    // ========================================================================

    @Test
    fun `should report error for undefined variable reference`() {
        val el = element(slots = listOf(
            slot(name = "TextColor", fillMode = FillMode.VARIABLE, value = "@MissingVar")
        ))
        val result = validate(el, emptyList())
        assertThat(result).anyMatch {
            it.severity == ProblemSeverity.ERROR &&
                it.message.contains("@MissingVar") &&
                it.message.contains("not defined") &&
                it.property == "TextColor"
        }
    }

    @Test
    fun `should not report error when variable exists in word bank`() {
        val el = element(slots = listOf(
            slot(name = "TextColor", fillMode = FillMode.VARIABLE, value = "@AccentColor")
        ))
        val items = listOf(wordBankItem("@AccentColor", WordBankKind.VARIABLE, ComposerPropertyType.COLOR))
        val result = validate(el, items)
        assertThat(result.filter { it.message.contains("not defined") }).isEmpty()
    }

    @Test
    fun `should accept style reference as valid variable`() {
        val el = element(slots = listOf(
            slot(name = "Style", fillMode = FillMode.VARIABLE, value = "@ButtonStyle", type = ComposerPropertyType.STYLE)
        ))
        val items = listOf(wordBankItem("@ButtonStyle", WordBankKind.STYLE, ComposerPropertyType.STYLE))
        val result = validate(el, items)
        assertThat(result.filter { it.message.contains("not defined") }).isEmpty()
    }

    @Test
    fun `should report error when variable removed from word bank`() {
        val el = element(slots = listOf(
            slot(name = "TextColor", fillMode = FillMode.VARIABLE, value = "@Removed")
        ))
        val result = validate(el, emptyList())
        assertThat(result).anyMatch {
            it.severity == ProblemSeverity.ERROR && it.message.contains("@Removed")
        }
    }

    @Test
    fun `should not validate blank variable value`() {
        val el = element(slots = listOf(
            slot(name = "TextColor", fillMode = FillMode.VARIABLE, value = "")
        ))
        val result = validate(el, emptyList())
        assertThat(result.filter { it.message.contains("not defined") }).isEmpty()
    }

    // ========================================================================
    // FR-5: Undefined Import References
    // ========================================================================

    @Test
    fun `should report error for undefined import alias`() {
        val el = element(slots = listOf(
            slot(name = "Style", fillMode = FillMode.IMPORT, value = "\$Missing.@SomeStyle")
        ))
        val result = validate(el, emptyList())
        assertThat(result).anyMatch {
            it.severity == ProblemSeverity.ERROR &&
                it.message.contains("\$Missing") &&
                it.property == "Style"
        }
    }

    @Test
    fun `should not report error when import alias exists`() {
        val el = element(slots = listOf(
            slot(name = "Style", fillMode = FillMode.IMPORT, value = "\$Common.@HeaderStyle")
        ))
        val items = listOf(wordBankItem("\$Common.@HeaderStyle", WordBankKind.IMPORT, ComposerPropertyType.STYLE))
        val result = validate(el, items)
        assertThat(result.filter { it.message.contains("Import") }).isEmpty()
    }

    @Test
    fun `should match import alias by prefix`() {
        val el = element(slots = listOf(
            slot(name = "Style", fillMode = FillMode.IMPORT, value = "\$Common.@OtherStyle")
        ))
        val items = listOf(wordBankItem("\$Common.@HeaderStyle", WordBankKind.IMPORT))
        val result = validate(el, items)
        assertThat(result.filter { it.message.contains("Import") }).isEmpty()
    }

    @Test
    fun `should not validate blank import value`() {
        val el = element(slots = listOf(
            slot(name = "Style", fillMode = FillMode.IMPORT, value = "")
        ))
        val result = validate(el, emptyList())
        assertThat(result.filter { it.message.contains("Import") }).isEmpty()
    }

    // ========================================================================
    // FR-6: Undefined Localization Keys
    // ========================================================================

    @Test
    fun `should warn for undefined localization key`() {
        val el = element(slots = listOf(
            slot(name = "Text", fillMode = FillMode.LOCALIZATION, value = "%missing.key")
        ))
        val result = validate(el, emptyList())
        assertThat(result).anyMatch {
            it.severity == ProblemSeverity.WARNING &&
                it.message.contains("%missing.key") &&
                it.property == "Text"
        }
    }

    @Test
    fun `should not warn when localization key exists`() {
        val el = element(slots = listOf(
            slot(name = "Text", fillMode = FillMode.LOCALIZATION, value = "%button.submit")
        ))
        val items = listOf(wordBankItem("%button.submit", WordBankKind.LOCALIZATION))
        val result = validate(el, items)
        assertThat(result.filter { it.message.contains("Localization") }).isEmpty()
    }

    @Test
    fun `should not validate blank localization value`() {
        val el = element(slots = listOf(
            slot(name = "Text", fillMode = FillMode.LOCALIZATION, value = "")
        ))
        val result = validate(el, emptyList())
        assertThat(result.filter { it.message.contains("Localization") }).isEmpty()
    }

    // ========================================================================
    // FR-7: Color Format Validation
    // ========================================================================

    @Test
    fun `should warn for invalid color format`() {
        val el = element(slots = listOf(
            slot(name = "TextColor", type = ComposerPropertyType.COLOR, fillMode = FillMode.LITERAL, value = "red")
        ))
        val result = validate(el, emptyList())
        assertThat(result).anyMatch {
            it.severity == ProblemSeverity.WARNING &&
                it.message.contains("red") &&
                it.message.contains("valid color")
        }
    }

    @Test
    fun `should accept valid 3-digit hex color`() {
        val el = element(slots = listOf(
            slot(name = "TextColor", type = ComposerPropertyType.COLOR, fillMode = FillMode.LITERAL, value = "#fff")
        ))
        val result = validate(el, emptyList())
        assertThat(result.filter { it.message.contains("valid color") }).isEmpty()
    }

    @Test
    fun `should accept valid 6-digit hex color`() {
        val el = element(slots = listOf(
            slot(name = "TextColor", type = ComposerPropertyType.COLOR, fillMode = FillMode.LITERAL, value = "#ff6b00")
        ))
        val result = validate(el, emptyList())
        assertThat(result.filter { it.message.contains("valid color") }).isEmpty()
    }

    @Test
    fun `should accept valid 8-digit hex color with alpha`() {
        val el = element(slots = listOf(
            slot(name = "TextColor", type = ComposerPropertyType.COLOR, fillMode = FillMode.LITERAL, value = "#ff6b00ff")
        ))
        val result = validate(el, emptyList())
        assertThat(result.filter { it.message.contains("valid color") }).isEmpty()
    }

    @Test
    fun `should warn for 5-digit hex color`() {
        val el = element(slots = listOf(
            slot(name = "TextColor", type = ComposerPropertyType.COLOR, fillMode = FillMode.LITERAL, value = "#ff6b0")
        ))
        val result = validate(el, emptyList())
        assertThat(result).anyMatch { it.message.contains("valid color") }
    }

    @Test
    fun `should warn for 4-digit hex color`() {
        val el = element(slots = listOf(
            slot(name = "TextColor", type = ComposerPropertyType.COLOR, fillMode = FillMode.LITERAL, value = "#ff6b")
        ))
        val result = validate(el, emptyList())
        assertThat(result).anyMatch { it.message.contains("valid color") }
    }

    @Test
    fun `should not validate color for non-literal fill mode`() {
        val el = element(slots = listOf(
            slot(name = "TextColor", type = ComposerPropertyType.COLOR, fillMode = FillMode.VARIABLE, value = "@someColor")
        ))
        val items = listOf(wordBankItem("@someColor", WordBankKind.VARIABLE, ComposerPropertyType.COLOR))
        val result = validate(el, items)
        assertThat(result.filter { it.message.contains("valid color") }).isEmpty()
    }

    @Test
    fun `should not validate empty color literal`() {
        val el = element(slots = listOf(
            slot(name = "TextColor", type = ComposerPropertyType.COLOR, fillMode = FillMode.LITERAL, value = "")
        ))
        val result = validate(el, emptyList())
        assertThat(result.filter { it.message.contains("valid color") }).isEmpty()
    }

    @Test
    fun `should not validate color for non-color slot type`() {
        val el = element(slots = listOf(
            slot(name = "Text", type = ComposerPropertyType.TEXT, fillMode = FillMode.LITERAL, value = "red")
        ))
        val result = validate(el, emptyList())
        assertThat(result.filter { it.message.contains("valid color") }).isEmpty()
    }

    // ========================================================================
    // FR-8: Number Format Validation
    // ========================================================================

    @Test
    fun `should warn for non-numeric literal number`() {
        val el = element(slots = listOf(
            slot(name = "FontSize", type = ComposerPropertyType.NUMBER, fillMode = FillMode.LITERAL, value = "abc")
        ))
        val result = validate(el, emptyList())
        assertThat(result).anyMatch {
            it.severity == ProblemSeverity.WARNING &&
                it.message.contains("abc") &&
                it.message.contains("valid number")
        }
    }

    @Test
    fun `should warn for empty number literal`() {
        val el = element(slots = listOf(
            slot(name = "FontSize", type = ComposerPropertyType.NUMBER, fillMode = FillMode.LITERAL, value = "")
        ))
        val result = validate(el, emptyList())
        assertThat(result).anyMatch { it.message.contains("valid number") }
    }

    @Test
    fun `should warn for whitespace-only number literal`() {
        val el = element(slots = listOf(
            slot(name = "FontSize", type = ComposerPropertyType.NUMBER, fillMode = FillMode.LITERAL, value = "  ")
        ))
        val result = validate(el, emptyList())
        assertThat(result).anyMatch { it.message.contains("valid number") }
    }

    @Test
    fun `should accept valid integer literal`() {
        val el = element(slots = listOf(
            slot(name = "FontSize", type = ComposerPropertyType.NUMBER, fillMode = FillMode.LITERAL, value = "14")
        ))
        val result = validate(el, emptyList())
        assertThat(result.filter { it.message.contains("valid number") }).isEmpty()
    }

    @Test
    fun `should accept valid negative number`() {
        val el = element(slots = listOf(
            slot(name = "Offset", type = ComposerPropertyType.NUMBER, fillMode = FillMode.LITERAL, value = "-5")
        ))
        val result = validate(el, emptyList())
        assertThat(result.filter { it.message.contains("valid number") }).isEmpty()
    }

    @Test
    fun `should accept valid decimal number`() {
        val el = element(slots = listOf(
            slot(name = "Opacity", type = ComposerPropertyType.NUMBER, fillMode = FillMode.LITERAL, value = "0.5")
        ))
        val result = validate(el, emptyList())
        assertThat(result.filter { it.message.contains("valid number") }).isEmpty()
    }

    @Test
    fun `should validate percent type as number`() {
        val el = element(slots = listOf(
            slot(name = "Opacity", type = ComposerPropertyType.PERCENT, fillMode = FillMode.LITERAL, value = "abc")
        ))
        val result = validate(el, emptyList())
        assertThat(result).anyMatch { it.message.contains("valid number") }
    }

    @Test
    fun `should accept valid percent literal`() {
        val el = element(slots = listOf(
            slot(name = "Opacity", type = ComposerPropertyType.PERCENT, fillMode = FillMode.LITERAL, value = "75")
        ))
        val result = validate(el, emptyList())
        assertThat(result.filter { it.message.contains("valid number") }).isEmpty()
    }

    @Test
    fun `should not validate number for non-literal fill mode`() {
        val el = element(slots = listOf(
            slot(name = "FontSize", type = ComposerPropertyType.NUMBER, fillMode = FillMode.VARIABLE, value = "@size")
        ))
        val items = listOf(wordBankItem("@size", WordBankKind.VARIABLE, ComposerPropertyType.NUMBER))
        val result = validate(el, items)
        assertThat(result.filter { it.message.contains("valid number") }).isEmpty()
    }

    // ========================================================================
    // FR-9: Problem Ordering
    // ========================================================================

    @Test
    fun `should sort errors before warnings`() {
        val el = element(
            type = "Button", id = "",
            slots = listOf(
                slot(name = "Text", required = true, fillMode = FillMode.EMPTY),
            )
        )
        val result = validate(el, emptyList())
        assertThat(result).hasSizeGreaterThanOrEqualTo(2)
        val firstError = result.indexOfFirst { it.severity == ProblemSeverity.ERROR }
        val firstWarning = result.indexOfFirst { it.severity == ProblemSeverity.WARNING }
        assertThat(firstError).isLessThan(firstWarning)
    }

    @Test
    fun `should sort by property name within same severity`() {
        val el = element(slots = listOf(
            slot(name = "Zebra", required = true, fillMode = FillMode.EMPTY),
            slot(name = "Alpha", required = true, fillMode = FillMode.EMPTY),
        ))
        val result = validate(el, emptyList())
        val errors = result.filter { it.severity == ProblemSeverity.ERROR }
        assertThat(errors).hasSize(2)
        assertThat(errors[0].property).isEqualTo("Alpha")
        assertThat(errors[1].property).isEqualTo("Zebra")
    }

    @Test
    fun `should sort null-property problems after property-specific within severity`() {
        val el = element(
            type = "Button", id = "",
            slots = listOf(
                slot(name = "TextColor", type = ComposerPropertyType.COLOR, fillMode = FillMode.LITERAL, value = "bad"),
            )
        )
        val result = validate(el, emptyList())
        val warnings = result.filter { it.severity == ProblemSeverity.WARNING }
        assertThat(warnings).hasSizeGreaterThanOrEqualTo(2)
        val withProp = warnings.indexOfFirst { it.property != null }
        val withoutProp = warnings.indexOfFirst { it.property == null }
        assertThat(withProp).isLessThan(withoutProp)
    }

    @Test
    fun `should sort by message within same severity and property`() {
        val el = element(slots = listOf(
            slot(name = "Alpha", required = true, fillMode = FillMode.EMPTY),
            slot(name = "Alpha", type = ComposerPropertyType.COLOR, fillMode = FillMode.VARIABLE, value = "@missing"),
        ))
        val result = validate(el, emptyList())
        val alphaErrors = result.filter { it.property == "Alpha" && it.severity == ProblemSeverity.ERROR }
        if (alphaErrors.size >= 2) {
            assertThat(alphaErrors[0].message).isLessThanOrEqualTo(alphaErrors[1].message)
        }
    }

    // ========================================================================
    // FR-10: Pure function / Edge cases
    // ========================================================================

    @Test
    fun `should return empty list for valid element with id`() {
        val el = element(
            type = "Button", id = "btn",
            slots = listOf(
                slot(name = "Text", fillMode = FillMode.LITERAL, value = "Click"),
            )
        )
        val result = validate(el, emptyList())
        assertThat(result).isEmpty()
    }

    @Test
    fun `should return empty list for element with no slots`() {
        val el = element(type = "Label", id = "lbl", slots = emptyList())
        val result = validate(el, emptyList())
        assertThat(result).isEmpty()
    }

    @Test
    fun `should produce multiple problems from different rules`() {
        val el = element(
            type = "Button", id = "",
            slots = listOf(
                slot(name = "Text", required = true, fillMode = FillMode.EMPTY),
                slot(name = "TextColor", fillMode = FillMode.VARIABLE, value = "@missing"),
                slot(name = "Background", type = ComposerPropertyType.COLOR, fillMode = FillMode.LITERAL, value = "bad"),
            )
        )
        val result = validate(el, emptyList())
        // Expect: required error, undefined variable error, missing id warning, bad color warning
        assertThat(result).hasSizeGreaterThanOrEqualTo(4)
    }

    // ========================================================================
    // extractImportAlias helper
    // ========================================================================

    @Test
    fun `should extract alias from standard import value`() {
        assertThat(extractImportAlias("\$Common.@HeaderStyle")).isEqualTo("\$Common")
    }

    @Test
    fun `should extract alias from import value without dot`() {
        assertThat(extractImportAlias("\$Common")).isEqualTo("\$Common")
    }

    @Test
    fun `should return null for non-dollar-prefix value`() {
        assertThat(extractImportAlias("@SomeVar")).isNull()
    }

    @Test
    fun `should return null for empty string`() {
        assertThat(extractImportAlias("")).isNull()
    }

    @Test
    fun `should extract alias with complex path`() {
        assertThat(extractImportAlias("\$Shared.@DefaultButtonStyle")).isEqualTo("\$Shared")
    }
}
