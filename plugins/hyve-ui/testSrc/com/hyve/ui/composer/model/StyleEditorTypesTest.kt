// Copyright 2026 Hyve. All rights reserved.
package com.hyve.ui.composer.model

import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

class StyleEditorTypesTest {

    private fun createTestSlots() = listOf(
        PropertySlot("Background", ComposerPropertyType.IMAGE, SlotCategory.APPEARANCE, FillMode.LITERAL, "bg.png"),
        PropertySlot("TextColor", ComposerPropertyType.COLOR, SlotCategory.APPEARANCE, FillMode.EMPTY, ""),
    )

    @Test
    fun `should create StyleState with name and slots`() {
        val slots = createTestSlots()
        val state = StyleState(name = "Default", slots = slots)

        assertThat(state.name).isEqualTo("Default")
        assertThat(state.slots).hasSize(2)
        assertThat(state.slots[0].name).isEqualTo("Background")
    }

    @Test
    fun `should compute filledCount from non-empty slots`() {
        val state = StyleState(
            name = "Default",
            slots = createTestSlots(),
        )

        assertThat(state.filledCount).isEqualTo(1) // Background is LITERAL, TextColor is EMPTY
    }

    @Test
    fun `should report zero filledCount when all slots empty`() {
        val state = StyleState(
            name = "Default",
            slots = listOf(
                PropertySlot("TextColor", ComposerPropertyType.COLOR, SlotCategory.APPEARANCE),
                PropertySlot("FontSize", ComposerPropertyType.NUMBER, SlotCategory.APPEARANCE),
            ),
        )

        assertThat(state.filledCount).isEqualTo(0)
    }

    @Test
    fun `should create StyleTab with name, type, and states`() {
        val tab = StyleTab(
            name = "@ButtonStyle",
            styleType = "TextButtonStyle",
            states = listOf(
                StyleState("Default", createTestSlots()),
                StyleState("Hovered", createTestSlots()),
            ),
        )

        assertThat(tab.name).isEqualTo("@ButtonStyle")
        assertThat(tab.styleType).isEqualTo("TextButtonStyle")
        assertThat(tab.states).hasSize(2)
    }

    @Test
    fun `should compute stateCount from states list`() {
        val tab = StyleTab(
            name = "@ButtonStyle",
            styleType = "TextButtonStyle",
            states = listOf(
                StyleState("Default", emptyList()),
                StyleState("Hovered", emptyList()),
                StyleState("Pressed", emptyList()),
            ),
        )

        assertThat(tab.stateCount).isEqualTo(3)
    }

    @Test
    fun `should preserve state order in states list`() {
        val tab = StyleTab(
            name = "@ButtonStyle",
            styleType = "TextButtonStyle",
            states = listOf(
                StyleState("Default", emptyList()),
                StyleState("Hovered", emptyList()),
                StyleState("Custom", emptyList()),
            ),
        )

        assertThat(tab.states.map { it.name }).containsExactly("Default", "Hovered", "Custom")
    }

    @Test
    fun `should copy StyleTab with modified states`() {
        val original = StyleTab(
            name = "@LabelStyle",
            styleType = "LabelStyle",
            states = listOf(StyleState("Default", createTestSlots())),
        )

        val modified = original.copy(
            states = original.states + StyleState("Hovered", emptyList()),
        )

        assertThat(original.states).hasSize(1)
        assertThat(modified.states).hasSize(2)
        assertThat(modified.name).isEqualTo("@LabelStyle")
    }

    // -- filledCount with non-LITERAL fill modes --

    @Test
    fun `should count all VARIABLE slots as filled`() {
        val state = StyleState(
            name = "Default",
            slots = listOf(
                PropertySlot("TextColor", ComposerPropertyType.COLOR, SlotCategory.APPEARANCE, FillMode.VARIABLE, "@accent"),
                PropertySlot("FontSize", ComposerPropertyType.NUMBER, SlotCategory.APPEARANCE, FillMode.VARIABLE, "@size"),
            ),
        )

        assertThat(state.filledCount).isEqualTo(2)
    }

    @Test
    fun `should count mixed non-EMPTY fill modes as filled`() {
        val state = StyleState(
            name = "Default",
            slots = listOf(
                PropertySlot("Background", ComposerPropertyType.IMAGE, SlotCategory.APPEARANCE, FillMode.LITERAL, "bg.png"),
                PropertySlot("TextColor", ComposerPropertyType.COLOR, SlotCategory.APPEARANCE, FillMode.VARIABLE, "@accent"),
                PropertySlot("Style", ComposerPropertyType.STYLE, SlotCategory.APPEARANCE, FillMode.IMPORT, "\$Common.@Style"),
                PropertySlot("Text", ComposerPropertyType.TEXT, SlotCategory.APPEARANCE, FillMode.LOCALIZATION, "%label"),
                PropertySlot("FontSize", ComposerPropertyType.NUMBER, SlotCategory.APPEARANCE, FillMode.EMPTY, ""),
            ),
        )

        assertThat(state.filledCount).isEqualTo(4)
    }

    // -- Edge cases --

    @Test
    fun `should report zero stateCount for StyleTab with empty states list`() {
        val tab = StyleTab(
            name = "@EmptyStyle",
            styleType = "TestType",
            states = emptyList(),
        )

        assertThat(tab.stateCount).isEqualTo(0)
    }

    @Test
    fun `should report zero filledCount for StyleState with empty slots list`() {
        val state = StyleState(
            name = "Default",
            slots = emptyList(),
        )

        assertThat(state.filledCount).isEqualTo(0)
    }
}
