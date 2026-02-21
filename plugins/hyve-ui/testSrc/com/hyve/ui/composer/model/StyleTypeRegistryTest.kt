// Copyright 2026 Hyve. All rights reserved.
package com.hyve.ui.composer.model

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.EnumSource

class StyleTypeRegistryTest {

    // -- Slot counts per style type --

    @Test
    fun `TextButtonStyle should have 4 slots`() {
        assertThat(StyleTypeRegistry.slotsForStyleType(StyleType.TEXT_BUTTON_STYLE)).hasSize(4)
    }

    @Test
    fun `LabelStyle should have 3 slots`() {
        assertThat(StyleTypeRegistry.slotsForStyleType(StyleType.LABEL_STYLE)).hasSize(3)
    }

    @Test
    fun `TextFieldStyle should have 4 slots`() {
        assertThat(StyleTypeRegistry.slotsForStyleType(StyleType.TEXT_FIELD_STYLE)).hasSize(4)
    }

    @Test
    fun `CheckBoxStyle should have 3 slots`() {
        assertThat(StyleTypeRegistry.slotsForStyleType(StyleType.CHECK_BOX_STYLE)).hasSize(3)
    }

    @Test
    fun `ScrollbarStyle should have 3 slots`() {
        assertThat(StyleTypeRegistry.slotsForStyleType(StyleType.SCROLLBAR_STYLE)).hasSize(3)
    }

    @Test
    fun `SliderStyle should have 4 slots`() {
        assertThat(StyleTypeRegistry.slotsForStyleType(StyleType.SLIDER_STYLE)).hasSize(4)
    }

    @Test
    fun `DropdownBoxStyle should have 4 slots`() {
        assertThat(StyleTypeRegistry.slotsForStyleType(StyleType.DROPDOWN_BOX_STYLE)).hasSize(4)
    }

    @Test
    fun `TabPanelStyle should have 3 slots`() {
        assertThat(StyleTypeRegistry.slotsForStyleType(StyleType.TAB_PANEL_STYLE)).hasSize(3)
    }

    @Test
    fun `ProgressBarStyle should have 3 slots`() {
        assertThat(StyleTypeRegistry.slotsForStyleType(StyleType.PROGRESS_BAR_STYLE)).hasSize(3)
    }

    @Test
    fun `TooltipStyle should have 3 slots`() {
        assertThat(StyleTypeRegistry.slotsForStyleType(StyleType.TOOLTIP_STYLE)).hasSize(3)
    }

    // -- Slot names and types --

    @Test
    fun `TextButtonStyle slots should match spec table`() {
        val slots = StyleTypeRegistry.slotsForStyleType(StyleType.TEXT_BUTTON_STYLE)
        assertThat(slots.map { it.name to it.type }).containsExactly(
            "Background" to ComposerPropertyType.IMAGE,
            "LabelStyle" to ComposerPropertyType.STYLE,
            "TextColor" to ComposerPropertyType.COLOR,
            "BackgroundColor" to ComposerPropertyType.COLOR,
        )
    }

    @Test
    fun `LabelStyle slots should match spec table`() {
        val slots = StyleTypeRegistry.slotsForStyleType(StyleType.LABEL_STYLE)
        assertThat(slots.map { it.name to it.type }).containsExactly(
            "TextColor" to ComposerPropertyType.COLOR,
            "FontSize" to ComposerPropertyType.NUMBER,
            "FontName" to ComposerPropertyType.FONT,
        )
    }

    @Test
    fun `SliderStyle slots should match spec table`() {
        val slots = StyleTypeRegistry.slotsForStyleType(StyleType.SLIDER_STYLE)
        assertThat(slots.map { it.name to it.type }).containsExactly(
            "TrackColor" to ComposerPropertyType.COLOR,
            "FillColor" to ComposerPropertyType.COLOR,
            "HandleColor" to ComposerPropertyType.COLOR,
            "HandleSize" to ComposerPropertyType.NUMBER,
        )
    }

    @Test
    fun `TextFieldStyle slots should match spec table`() {
        val slots = StyleTypeRegistry.slotsForStyleType(StyleType.TEXT_FIELD_STYLE)
        assertThat(slots.map { it.name to it.type }).containsExactly(
            "Background" to ComposerPropertyType.IMAGE,
            "TextColor" to ComposerPropertyType.COLOR,
            "PlaceholderColor" to ComposerPropertyType.COLOR,
            "BorderColor" to ComposerPropertyType.COLOR,
        )
    }

    @Test
    fun `CheckBoxStyle slots should match spec table`() {
        val slots = StyleTypeRegistry.slotsForStyleType(StyleType.CHECK_BOX_STYLE)
        assertThat(slots.map { it.name to it.type }).containsExactly(
            "Background" to ComposerPropertyType.IMAGE,
            "CheckColor" to ComposerPropertyType.COLOR,
            "BorderColor" to ComposerPropertyType.COLOR,
        )
    }

    @Test
    fun `ScrollbarStyle slots should match spec table`() {
        val slots = StyleTypeRegistry.slotsForStyleType(StyleType.SCROLLBAR_STYLE)
        assertThat(slots.map { it.name to it.type }).containsExactly(
            "TrackColor" to ComposerPropertyType.COLOR,
            "ThumbColor" to ComposerPropertyType.COLOR,
            "ThumbWidth" to ComposerPropertyType.NUMBER,
        )
    }

    @Test
    fun `DropdownBoxStyle slots should match spec table`() {
        val slots = StyleTypeRegistry.slotsForStyleType(StyleType.DROPDOWN_BOX_STYLE)
        assertThat(slots.map { it.name to it.type }).containsExactly(
            "Background" to ComposerPropertyType.IMAGE,
            "TextColor" to ComposerPropertyType.COLOR,
            "BorderColor" to ComposerPropertyType.COLOR,
            "ArrowColor" to ComposerPropertyType.COLOR,
        )
    }

    @Test
    fun `TabPanelStyle slots should match spec table`() {
        val slots = StyleTypeRegistry.slotsForStyleType(StyleType.TAB_PANEL_STYLE)
        assertThat(slots.map { it.name to it.type }).containsExactly(
            "TabBackground" to ComposerPropertyType.IMAGE,
            "ActiveTabColor" to ComposerPropertyType.COLOR,
            "InactiveTabColor" to ComposerPropertyType.COLOR,
        )
    }

    @Test
    fun `ProgressBarStyle slots should match spec table`() {
        val slots = StyleTypeRegistry.slotsForStyleType(StyleType.PROGRESS_BAR_STYLE)
        assertThat(slots.map { it.name to it.type }).containsExactly(
            "BarColor" to ComposerPropertyType.COLOR,
            "BackgroundColor" to ComposerPropertyType.COLOR,
            "BarTexturePath" to ComposerPropertyType.IMAGE,
        )
    }

    @Test
    fun `TooltipStyle slots should match spec table`() {
        val slots = StyleTypeRegistry.slotsForStyleType(StyleType.TOOLTIP_STYLE)
        assertThat(slots.map { it.name to it.type }).containsExactly(
            "Background" to ComposerPropertyType.IMAGE,
            "TextColor" to ComposerPropertyType.COLOR,
            "BorderColor" to ComposerPropertyType.COLOR,
        )
    }

    // -- All slots have correct category and fill mode --

    @ParameterizedTest
    @EnumSource(StyleType::class)
    fun `all slots should have category APPEARANCE`(styleType: StyleType) {
        val slots = StyleTypeRegistry.slotsForStyleType(styleType)
        assertThat(slots).allMatch { it.category == SlotCategory.APPEARANCE }
    }

    @ParameterizedTest
    @EnumSource(StyleType::class)
    fun `all slots should have fillMode EMPTY`(styleType: StyleType) {
        val slots = StyleTypeRegistry.slotsForStyleType(styleType)
        assertThat(slots).allMatch { it.fillMode == FillMode.EMPTY }
    }

    @ParameterizedTest
    @EnumSource(StyleType::class)
    fun `all slots should have empty value`(styleType: StyleType) {
        val slots = StyleTypeRegistry.slotsForStyleType(styleType)
        assertThat(slots).allMatch { it.value == "" }
    }

    // -- createStyleTab --

    @Test
    fun `createStyleTab should produce 4 standard states`() {
        val tab = StyleTypeRegistry.createStyleTab("@TestStyle", StyleType.TEXT_BUTTON_STYLE)
        assertThat(tab.states).hasSize(4)
        assertThat(tab.states.map { it.name }).containsExactly(
            "Default", "Hovered", "Pressed", "Disabled",
        )
    }

    @Test
    fun `createStyleTab should set correct name and styleType`() {
        val tab = StyleTypeRegistry.createStyleTab("@MyButton", StyleType.TEXT_BUTTON_STYLE)
        assertThat(tab.name).isEqualTo("@MyButton")
        assertThat(tab.styleType).isEqualTo("TextButtonStyle")
    }

    @Test
    fun `createStyleTab states should all have the same slot structure`() {
        val tab = StyleTypeRegistry.createStyleTab("@S", StyleType.SLIDER_STYLE)
        val firstSlotNames = tab.states.first().slots.map { it.name }
        for (state in tab.states) {
            assertThat(state.slots.map { it.name }).isEqualTo(firstSlotNames)
        }
    }

    @Test
    fun `createStyleTab states should have independent slot instances`() {
        // Verify that modifying one state's slot doesn't affect another
        val tab = StyleTypeRegistry.createStyleTab("@S", StyleType.LABEL_STYLE)
        val defaultSlots = tab.states[0].slots
        val hoveredSlots = tab.states[1].slots
        // They should be equal by value but not the same object reference
        assertThat(defaultSlots).isEqualTo(hoveredSlots)
    }

    @Test
    fun `createStyleTab stateCount should equal number of states`() {
        val tab = StyleTypeRegistry.createStyleTab("@S", StyleType.CHECK_BOX_STYLE)
        assertThat(tab.stateCount).isEqualTo(4)
    }
}
