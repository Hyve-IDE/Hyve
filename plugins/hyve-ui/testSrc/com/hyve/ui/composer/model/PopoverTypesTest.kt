// Copyright 2026 Hyve. All rights reserved.
package com.hyve.ui.composer.model

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class PopoverTypesTest {

    @Test
    fun `VARIABLE_TYPE_OPTIONS should contain exactly 7 types`() {
        assertThat(VARIABLE_TYPE_OPTIONS).hasSize(7)
    }

    @Test
    fun `VARIABLE_TYPE_OPTIONS should contain the expected types`() {
        assertThat(VARIABLE_TYPE_OPTIONS).containsExactly(
            ComposerPropertyType.TEXT,
            ComposerPropertyType.NUMBER,
            ComposerPropertyType.COLOR,
            ComposerPropertyType.BOOLEAN,
            ComposerPropertyType.IMAGE,
            ComposerPropertyType.FONT,
            ComposerPropertyType.PERCENT,
        )
    }

    @Test
    fun `StyleType should have exactly 10 entries`() {
        assertThat(StyleType.entries).hasSize(10)
    }

    @Test
    fun `StyleType displayNames should match expected values`() {
        assertThat(StyleType.TEXT_BUTTON_STYLE.displayName).isEqualTo("TextButtonStyle")
        assertThat(StyleType.LABEL_STYLE.displayName).isEqualTo("LabelStyle")
        assertThat(StyleType.TEXT_FIELD_STYLE.displayName).isEqualTo("TextFieldStyle")
        assertThat(StyleType.CHECK_BOX_STYLE.displayName).isEqualTo("CheckBoxStyle")
        assertThat(StyleType.SCROLLBAR_STYLE.displayName).isEqualTo("ScrollbarStyle")
        assertThat(StyleType.SLIDER_STYLE.displayName).isEqualTo("SliderStyle")
        assertThat(StyleType.DROPDOWN_BOX_STYLE.displayName).isEqualTo("DropdownBoxStyle")
        assertThat(StyleType.TAB_PANEL_STYLE.displayName).isEqualTo("TabPanelStyle")
        assertThat(StyleType.PROGRESS_BAR_STYLE.displayName).isEqualTo("ProgressBarStyle")
        assertThat(StyleType.TOOLTIP_STYLE.displayName).isEqualTo("TooltipStyle")
    }

    @Test
    fun `defaultPlaceholder should return correct text for TEXT`() {
        assertThat(defaultPlaceholder(ComposerPropertyType.TEXT)).isEqualTo("\"Hello World\"")
    }

    @Test
    fun `defaultPlaceholder should return correct text for NUMBER`() {
        assertThat(defaultPlaceholder(ComposerPropertyType.NUMBER)).isEqualTo("0")
    }

    @Test
    fun `defaultPlaceholder should return correct text for COLOR`() {
        assertThat(defaultPlaceholder(ComposerPropertyType.COLOR)).isEqualTo("#000000")
    }

    @Test
    fun `defaultPlaceholder should return correct text for BOOLEAN`() {
        assertThat(defaultPlaceholder(ComposerPropertyType.BOOLEAN)).isEqualTo("true")
    }

    @Test
    fun `defaultPlaceholder should return correct text for IMAGE`() {
        assertThat(defaultPlaceholder(ComposerPropertyType.IMAGE)).isEqualTo("Texture.png")
    }

    @Test
    fun `defaultPlaceholder should return correct text for FONT`() {
        assertThat(defaultPlaceholder(ComposerPropertyType.FONT)).isEqualTo("Font.ttf")
    }

    @Test
    fun `defaultPlaceholder should return correct text for PERCENT`() {
        assertThat(defaultPlaceholder(ComposerPropertyType.PERCENT)).isEqualTo("100")
    }

    @Test
    fun `defaultPlaceholder should return empty string for unsupported types`() {
        assertThat(defaultPlaceholder(ComposerPropertyType.ANCHOR)).isEqualTo("")
        assertThat(defaultPlaceholder(ComposerPropertyType.STYLE)).isEqualTo("")
        assertThat(defaultPlaceholder(ComposerPropertyType.TUPLE)).isEqualTo("")
    }

    @Test
    fun `PopoverKind should have exactly 3 entries`() {
        assertThat(PopoverKind.entries).hasSize(3)
    }

    // -- ImportableFile and ImportableExport data class tests --

    @Test
    fun `ImportableFile should expose all constructor fields`() {
        val export = ImportableExport("HeaderStyle", ComposerPropertyType.STYLE)
        val file = ImportableFile(
            name = "Common",
            fileName = "Common.ui",
            exports = listOf(export),
        )

        assertThat(file.name).isEqualTo("Common")
        assertThat(file.fileName).isEqualTo("Common.ui")
        assertThat(file.exports).hasSize(1)
        assertThat(file.exports[0]).isEqualTo(export)
    }

    @Test
    fun `ImportableFile should allow empty exports list`() {
        val file = ImportableFile(
            name = "Empty",
            fileName = "Empty.ui",
            exports = emptyList(),
        )

        assertThat(file.exports).isEmpty()
    }

    @Test
    fun `ImportableExport should expose name and type`() {
        val export = ImportableExport("AccentColor", ComposerPropertyType.COLOR)

        assertThat(export.name).isEqualTo("AccentColor")
        assertThat(export.type).isEqualTo(ComposerPropertyType.COLOR)
    }

    @Test
    fun `ImportableExport data class equality should compare by value`() {
        val a = ImportableExport("Style", ComposerPropertyType.STYLE)
        val b = ImportableExport("Style", ComposerPropertyType.STYLE)

        assertThat(a).isEqualTo(b)
        assertThat(a.hashCode()).isEqualTo(b.hashCode())
    }
}
