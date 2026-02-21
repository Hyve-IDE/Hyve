// Copyright 2026 Hyve. All rights reserved.
package com.hyve.ui.composer.propertyform

import androidx.compose.ui.graphics.Color
import com.hyve.ui.composer.model.ComposerPropertyType
import com.hyve.ui.composer.model.FillMode
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

class PropertyFormColorsTest {

    @Test
    fun `should return a non-transparent color for every property type`() {
        for (type in ComposerPropertyType.entries) {
            val color = typeColor(type)
            assertThat(color).isNotEqualTo(Color.Transparent)
        }
    }

    @Test
    fun `should return matching colors for IMAGE and FONT`() {
        assertThat(typeColor(ComposerPropertyType.IMAGE))
            .isEqualTo(typeColor(ComposerPropertyType.FONT))
    }

    @Test
    fun `should return matching colors for NUMBER and PERCENT`() {
        assertThat(typeColor(ComposerPropertyType.NUMBER))
            .isEqualTo(typeColor(ComposerPropertyType.PERCENT))
    }

    @Test
    fun `should return correct placeholder for each property type`() {
        assertThat(emptyPlaceholder(ComposerPropertyType.TEXT)).isEqualTo("________")
        assertThat(emptyPlaceholder(ComposerPropertyType.NUMBER)).isEqualTo("___")
        assertThat(emptyPlaceholder(ComposerPropertyType.COLOR)).isEqualTo("#______")
        assertThat(emptyPlaceholder(ComposerPropertyType.BOOLEAN)).isEqualTo("true/false")
        assertThat(emptyPlaceholder(ComposerPropertyType.ANCHOR)).isEqualTo("________")
        assertThat(emptyPlaceholder(ComposerPropertyType.STYLE)).isEqualTo("@_________")
        assertThat(emptyPlaceholder(ComposerPropertyType.IMAGE)).isEqualTo("________.png")
        assertThat(emptyPlaceholder(ComposerPropertyType.FONT)).isEqualTo("________.ttf")
        assertThat(emptyPlaceholder(ComposerPropertyType.TUPLE)).isEqualTo("(___)")
        assertThat(emptyPlaceholder(ComposerPropertyType.PERCENT)).isEqualTo("___%")
    }

    @Test
    fun `should return correct default literal value for boolean`() {
        assertThat(defaultLiteralValue(ComposerPropertyType.BOOLEAN)).isEqualTo("true")
    }

    @Test
    fun `should return correct default literal value for color`() {
        assertThat(defaultLiteralValue(ComposerPropertyType.COLOR)).isEqualTo("#ffffff")
    }

    @Test
    fun `should return correct default literal value for number`() {
        assertThat(defaultLiteralValue(ComposerPropertyType.NUMBER)).isEqualTo("0")
    }

    @Test
    fun `should return correct default literal value for percent`() {
        assertThat(defaultLiteralValue(ComposerPropertyType.PERCENT)).isEqualTo("100")
    }

    @Test
    fun `should return empty string default for text type`() {
        assertThat(defaultLiteralValue(ComposerPropertyType.TEXT)).isEmpty()
    }

    @Test
    fun `should return correct icon for each fill mode`() {
        assertThat(fillModeIcon(FillMode.LITERAL)).isEqualTo("\u270E")
        assertThat(fillModeIcon(FillMode.VARIABLE)).isEqualTo("@")
        assertThat(fillModeIcon(FillMode.LOCALIZATION)).isEqualTo("%")
        assertThat(fillModeIcon(FillMode.EXPRESSION)).isEqualTo("\u0192")
        assertThat(fillModeIcon(FillMode.IMPORT)).isEqualTo("$")
        assertThat(fillModeIcon(FillMode.EMPTY)).isEmpty()
    }

    @Test
    fun `should return transparent badge color for empty fill mode`() {
        assertThat(fillModeBadgeColor(FillMode.EMPTY)).isEqualTo(Color.Transparent)
    }

    @Test
    fun `should return non-transparent badge color for all non-empty fill modes`() {
        val nonEmptyModes = FillMode.entries.filter { it != FillMode.EMPTY }
        for (mode in nonEmptyModes) {
            assertThat(fillModeBadgeColor(mode))
                .withFailMessage("Expected non-transparent color for $mode")
                .isNotEqualTo(Color.Transparent)
        }
    }
}
