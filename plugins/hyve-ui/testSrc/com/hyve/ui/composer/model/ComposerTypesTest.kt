// Copyright 2026 Hyve. All rights reserved.
package com.hyve.ui.composer.model

import com.hyve.ui.core.id.ElementType
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

class ComposerTypesTest {

    @Test
    fun `should create PropertySlot with default empty fill mode`() {
        val slot = PropertySlot(
            name = "TextColor",
            type = ComposerPropertyType.COLOR,
            category = SlotCategory.APPEARANCE
        )

        assertThat(slot.fillMode).isEqualTo(FillMode.EMPTY)
        assertThat(slot.value).isEmpty()
        assertThat(slot.required).isFalse()
        assertThat(slot.description).isEmpty()
        assertThat(slot.anchorValues).isEmpty()
    }

    @Test
    fun `should create PropertySlot with literal fill mode`() {
        val slot = PropertySlot(
            name = "FontSize",
            type = ComposerPropertyType.NUMBER,
            category = SlotCategory.TEXT,
            fillMode = FillMode.LITERAL,
            value = "14"
        )

        assertThat(slot.fillMode).isEqualTo(FillMode.LITERAL)
        assertThat(slot.value).isEqualTo("14")
    }

    @Test
    fun `should create PropertySlot with variable fill mode`() {
        val slot = PropertySlot(
            name = "TextColor",
            type = ComposerPropertyType.COLOR,
            category = SlotCategory.APPEARANCE,
            fillMode = FillMode.VARIABLE,
            value = "@primaryColor"
        )

        assertThat(slot.fillMode).isEqualTo(FillMode.VARIABLE)
        assertThat(slot.value).isEqualTo("@primaryColor")
    }

    @Test
    fun `should count filled slots in ElementDefinition`() {
        val definition = ElementDefinition(
            type = ElementType("Button"),
            id = "SubmitBtn",
            slots = listOf(
                PropertySlot("Text", ComposerPropertyType.TEXT, SlotCategory.TEXT, FillMode.LITERAL, "Submit"),
                PropertySlot("Color", ComposerPropertyType.COLOR, SlotCategory.APPEARANCE, FillMode.EMPTY, ""),
                PropertySlot("FontSize", ComposerPropertyType.NUMBER, SlotCategory.TEXT, FillMode.LITERAL, "14"),
                PropertySlot("Visible", ComposerPropertyType.BOOLEAN, SlotCategory.STATE)
            )
        )

        assertThat(definition.filledCount).isEqualTo(2)
    }

    @Test
    fun `should group slots by category`() {
        val definition = ElementDefinition(
            type = ElementType("Button"),
            id = "Btn",
            slots = listOf(
                PropertySlot("Anchor", ComposerPropertyType.ANCHOR, SlotCategory.LAYOUT),
                PropertySlot("Text", ComposerPropertyType.TEXT, SlotCategory.TEXT),
                PropertySlot("Color", ComposerPropertyType.COLOR, SlotCategory.APPEARANCE),
                PropertySlot("FontSize", ComposerPropertyType.NUMBER, SlotCategory.TEXT),
                PropertySlot("Background", ComposerPropertyType.COLOR, SlotCategory.APPEARANCE)
            )
        )

        val grouped = definition.slotsByCategory
        assertThat(grouped.keys.toList()).containsExactly(
            SlotCategory.LAYOUT,
            SlotCategory.APPEARANCE,
            SlotCategory.TEXT
        )
        assertThat(grouped[SlotCategory.LAYOUT]).hasSize(1)
        assertThat(grouped[SlotCategory.APPEARANCE]).hasSize(2)
        assertThat(grouped[SlotCategory.TEXT]).hasSize(2)
    }

    @Test
    fun `should have all FillMode values`() {
        val modes = FillMode.entries
        assertThat(modes).containsExactly(
            FillMode.LITERAL,
            FillMode.VARIABLE,
            FillMode.LOCALIZATION,
            FillMode.EXPRESSION,
            FillMode.IMPORT,
            FillMode.EMPTY
        )
    }

    @Test
    fun `should have all SlotCategory values with display names`() {
        assertThat(SlotCategory.LAYOUT.displayName).isEqualTo("Layout")
        assertThat(SlotCategory.APPEARANCE.displayName).isEqualTo("Appearance")
        assertThat(SlotCategory.TEXT.displayName).isEqualTo("Text")
        assertThat(SlotCategory.INTERACTION.displayName).isEqualTo("Interaction")
        assertThat(SlotCategory.STATE.displayName).isEqualTo("State")
        assertThat(SlotCategory.DATA.displayName).isEqualTo("Data")
    }

    @Test
    fun `should have all ComposerPropertyType values with display names`() {
        assertThat(ComposerPropertyType.entries).hasSize(10)
        assertThat(ComposerPropertyType.TEXT.displayName).isEqualTo("Text")
        assertThat(ComposerPropertyType.NUMBER.displayName).isEqualTo("Number")
        assertThat(ComposerPropertyType.COLOR.displayName).isEqualTo("Color")
        assertThat(ComposerPropertyType.BOOLEAN.displayName).isEqualTo("Boolean")
        assertThat(ComposerPropertyType.ANCHOR.displayName).isEqualTo("Anchor")
        assertThat(ComposerPropertyType.STYLE.displayName).isEqualTo("Style")
        assertThat(ComposerPropertyType.IMAGE.displayName).isEqualTo("Image")
        assertThat(ComposerPropertyType.FONT.displayName).isEqualTo("Font")
        assertThat(ComposerPropertyType.TUPLE.displayName).isEqualTo("Tuple")
        assertThat(ComposerPropertyType.PERCENT.displayName).isEqualTo("Percent")
    }

    @Test
    fun `should return zero filled count for empty element`() {
        val definition = ElementDefinition(
            type = ElementType("Label"),
            id = "",
            slots = listOf(
                PropertySlot("Text", ComposerPropertyType.TEXT, SlotCategory.TEXT),
                PropertySlot("Color", ComposerPropertyType.COLOR, SlotCategory.APPEARANCE)
            )
        )

        assertThat(definition.filledCount).isEqualTo(0)
    }

    @Test
    fun `should handle element with no slots`() {
        val definition = ElementDefinition(
            type = ElementType("Group"),
            id = "Container",
            slots = emptyList()
        )

        assertThat(definition.filledCount).isEqualTo(0)
        assertThat(definition.slotsByCategory).isEmpty()
    }
}
