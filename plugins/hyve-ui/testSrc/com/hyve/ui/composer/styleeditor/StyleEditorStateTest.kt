// Copyright 2026 Hyve. All rights reserved.
package com.hyve.ui.composer.styleeditor

import com.hyve.ui.composer.ComposerModalState
import com.hyve.ui.composer.model.*
import com.hyve.ui.core.id.ElementType
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

/**
 * Tests for style editor state management in [ComposerModalState].
 *
 * Covers: style data CRUD, slot mutations within styles, custom state
 * addition, and state collapse toggling.
 */
class StyleEditorStateTest {

    private fun createTestElement() = ElementDefinition(
        type = ElementType("Button"),
        id = "TestBtn",
        slots = listOf(
            PropertySlot("Text", ComposerPropertyType.TEXT, SlotCategory.TEXT, FillMode.LITERAL, "Hello"),
        ),
    )

    private fun createTestStyleTab(
        name: String = "@ButtonStyle",
        styleType: String = "TextButtonStyle",
    ) = StyleTab(
        name = name,
        styleType = styleType,
        states = listOf(
            StyleState("Default", listOf(
                PropertySlot("Background", ComposerPropertyType.IMAGE, SlotCategory.APPEARANCE, FillMode.LITERAL, "bg.png"),
                PropertySlot("TextColor", ComposerPropertyType.COLOR, SlotCategory.APPEARANCE, FillMode.EMPTY, ""),
            )),
            StyleState("Hovered", listOf(
                PropertySlot("Background", ComposerPropertyType.IMAGE, SlotCategory.APPEARANCE, FillMode.LITERAL, "bg_hover.png"),
                PropertySlot("TextColor", ComposerPropertyType.COLOR, SlotCategory.APPEARANCE, FillMode.LITERAL, "#ffffff"),
            )),
        ),
    )

    // -- Style data CRUD --

    @Test
    fun `should initialize with empty style data`() {
        val state = ComposerModalState(createTestElement())

        assertThat(state.styleData.value).isEmpty()
    }

    @Test
    fun `should set style data for a style name`() {
        val state = ComposerModalState(createTestElement())
        val tab = createTestStyleTab()

        state.setStyleData("@ButtonStyle", tab)

        assertThat(state.styleData.value).containsKey("@ButtonStyle")
        assertThat(state.styleData.value["@ButtonStyle"]).isEqualTo(tab)
    }

    @Test
    fun `should overwrite existing style data`() {
        val state = ComposerModalState(createTestElement())
        val tab1 = createTestStyleTab()
        val tab2 = createTestStyleTab(styleType = "ModifiedType")

        state.setStyleData("@ButtonStyle", tab1)
        state.setStyleData("@ButtonStyle", tab2)

        assertThat(state.styleData.value["@ButtonStyle"]?.styleType).isEqualTo("ModifiedType")
    }

    @Test
    fun `should store multiple styles independently`() {
        val state = ComposerModalState(createTestElement())
        val buttonTab = createTestStyleTab("@ButtonStyle", "TextButtonStyle")
        val labelTab = createTestStyleTab("@LabelStyle", "LabelStyle")

        state.setStyleData("@ButtonStyle", buttonTab)
        state.setStyleData("@LabelStyle", labelTab)

        assertThat(state.styleData.value).hasSize(2)
        assertThat(state.styleData.value["@ButtonStyle"]?.styleType).isEqualTo("TextButtonStyle")
        assertThat(state.styleData.value["@LabelStyle"]?.styleType).isEqualTo("LabelStyle")
    }

    @Test
    fun `should remove style data`() {
        val state = ComposerModalState(createTestElement())
        state.setStyleData("@ButtonStyle", createTestStyleTab())

        state.removeStyleData("@ButtonStyle")

        assertThat(state.styleData.value).isEmpty()
    }

    @Test
    fun `should remove non-existent style gracefully`() {
        val state = ComposerModalState(createTestElement())
        state.setStyleData("@ButtonStyle", createTestStyleTab())

        state.removeStyleData("@NonExistent")

        assertThat(state.styleData.value).hasSize(1)
    }

    // -- Style slot updates --

    @Test
    fun `should update style slot fill mode and value`() {
        val state = ComposerModalState(createTestElement())
        state.setStyleData("@ButtonStyle", createTestStyleTab())

        state.updateStyleSlot("@ButtonStyle", "Default", "TextColor",
            fillMode = FillMode.LITERAL, value = "#ff0000")

        val slot = state.styleData.value["@ButtonStyle"]!!
            .states.first { it.name == "Default" }
            .slots.first { it.name == "TextColor" }
        assertThat(slot.fillMode).isEqualTo(FillMode.LITERAL)
        assertThat(slot.value).isEqualTo("#ff0000")
    }

    @Test
    fun `should update only style slot value when fillMode is null`() {
        val state = ComposerModalState(createTestElement())
        state.setStyleData("@ButtonStyle", createTestStyleTab())

        state.updateStyleSlot("@ButtonStyle", "Default", "Background", value = "new_bg.png")

        val slot = state.styleData.value["@ButtonStyle"]!!
            .states.first { it.name == "Default" }
            .slots.first { it.name == "Background" }
        assertThat(slot.fillMode).isEqualTo(FillMode.LITERAL) // unchanged
        assertThat(slot.value).isEqualTo("new_bg.png")
    }

    @Test
    fun `should update only style slot fillMode when value is null`() {
        val state = ComposerModalState(createTestElement())
        state.setStyleData("@ButtonStyle", createTestStyleTab())

        state.updateStyleSlot("@ButtonStyle", "Default", "Background", fillMode = FillMode.VARIABLE)

        val slot = state.styleData.value["@ButtonStyle"]!!
            .states.first { it.name == "Default" }
            .slots.first { it.name == "Background" }
        assertThat(slot.fillMode).isEqualTo(FillMode.VARIABLE)
        assertThat(slot.value).isEqualTo("bg.png") // unchanged
    }

    @Test
    fun `should only update targeted slot leaving others unchanged`() {
        val state = ComposerModalState(createTestElement())
        state.setStyleData("@ButtonStyle", createTestStyleTab())

        state.updateStyleSlot("@ButtonStyle", "Default", "TextColor",
            fillMode = FillMode.VARIABLE, value = "@accent")

        val bgSlot = state.styleData.value["@ButtonStyle"]!!
            .states.first { it.name == "Default" }
            .slots.first { it.name == "Background" }
        assertThat(bgSlot.value).isEqualTo("bg.png")
        assertThat(bgSlot.fillMode).isEqualTo(FillMode.LITERAL)
    }

    @Test
    fun `should only update targeted state leaving others unchanged`() {
        val state = ComposerModalState(createTestElement())
        state.setStyleData("@ButtonStyle", createTestStyleTab())

        state.updateStyleSlot("@ButtonStyle", "Default", "TextColor",
            fillMode = FillMode.LITERAL, value = "#ff0000")

        val hoveredSlot = state.styleData.value["@ButtonStyle"]!!
            .states.first { it.name == "Hovered" }
            .slots.first { it.name == "TextColor" }
        assertThat(hoveredSlot.value).isEqualTo("#ffffff") // unchanged
    }

    @Test
    fun `should no-op when updating slot in non-existent style`() {
        val state = ComposerModalState(createTestElement())

        state.updateStyleSlot("@NonExistent", "Default", "TextColor",
            fillMode = FillMode.LITERAL, value = "#ff0000")

        assertThat(state.styleData.value).isEmpty()
    }

    // -- Style slot clear --

    @Test
    fun `should clear style slot back to empty`() {
        val state = ComposerModalState(createTestElement())
        state.setStyleData("@ButtonStyle", createTestStyleTab())

        state.clearStyleSlot("@ButtonStyle", "Default", "Background")

        val slot = state.styleData.value["@ButtonStyle"]!!
            .states.first { it.name == "Default" }
            .slots.first { it.name == "Background" }
        assertThat(slot.fillMode).isEqualTo(FillMode.EMPTY)
        assertThat(slot.value).isEmpty()
    }

    @Test
    fun `should clear style slot anchor values`() {
        val state = ComposerModalState(createTestElement())
        val tabWithAnchor = StyleTab(
            name = "@TestStyle",
            styleType = "TestType",
            states = listOf(StyleState("Default", listOf(
                PropertySlot("Anchor", ComposerPropertyType.ANCHOR, SlotCategory.APPEARANCE,
                    FillMode.LITERAL, "left:10", anchorValues = mapOf("left" to "10")),
            ))),
        )
        state.setStyleData("@TestStyle", tabWithAnchor)

        state.clearStyleSlot("@TestStyle", "Default", "Anchor")

        val slot = state.styleData.value["@TestStyle"]!!
            .states.first().slots.first()
        assertThat(slot.fillMode).isEqualTo(FillMode.EMPTY)
        assertThat(slot.value).isEmpty()
        assertThat(slot.anchorValues).isEmpty()
    }

    // -- Style slot anchor values --

    @Test
    fun `should update style slot anchor values`() {
        val state = ComposerModalState(createTestElement())
        val tabWithAnchor = StyleTab(
            name = "@TestStyle",
            styleType = "TestType",
            states = listOf(StyleState("Default", listOf(
                PropertySlot("Anchor", ComposerPropertyType.ANCHOR, SlotCategory.APPEARANCE),
            ))),
        )
        state.setStyleData("@TestStyle", tabWithAnchor)

        state.updateStyleSlotAnchorValues("@TestStyle", "Default", "Anchor", "left", "10")

        val slot = state.styleData.value["@TestStyle"]!!
            .states.first().slots.first()
        assertThat(slot.anchorValues).containsEntry("left", "10")
        assertThat(slot.fillMode).isEqualTo(FillMode.LITERAL)
    }

    @Test
    fun `should remove anchor field when value is blank`() {
        val state = ComposerModalState(createTestElement())
        val tabWithAnchor = StyleTab(
            name = "@TestStyle",
            styleType = "TestType",
            states = listOf(StyleState("Default", listOf(
                PropertySlot("Anchor", ComposerPropertyType.ANCHOR, SlotCategory.APPEARANCE,
                    FillMode.LITERAL, "left:10, top:20",
                    anchorValues = mapOf("left" to "10", "top" to "20")),
            ))),
        )
        state.setStyleData("@TestStyle", tabWithAnchor)

        state.updateStyleSlotAnchorValues("@TestStyle", "Default", "Anchor", "left", "")

        val slot = state.styleData.value["@TestStyle"]!!
            .states.first().slots.first()
        assertThat(slot.anchorValues).doesNotContainKey("left")
        assertThat(slot.anchorValues).containsEntry("top", "20")
    }

    @Test
    fun `should synthesize anchor value in style slot`() {
        val state = ComposerModalState(createTestElement())
        val tabWithAnchor = StyleTab(
            name = "@TestStyle",
            styleType = "TestType",
            states = listOf(StyleState("Default", listOf(
                PropertySlot("Anchor", ComposerPropertyType.ANCHOR, SlotCategory.APPEARANCE),
            ))),
        )
        state.setStyleData("@TestStyle", tabWithAnchor)

        state.updateStyleSlotAnchorValues("@TestStyle", "Default", "Anchor", "left", "10")
        state.updateStyleSlotAnchorValues("@TestStyle", "Default", "Anchor", "top", "20")

        val slot = state.styleData.value["@TestStyle"]!!
            .states.first().slots.first()
        assertThat(slot.value).isEqualTo("left:10, top:20")
    }

    // -- Custom state addition --

    @Test
    fun `should add custom state with empty slots cloned from first state`() {
        val state = ComposerModalState(createTestElement())
        state.setStyleData("@ButtonStyle", createTestStyleTab())

        state.addCustomState("@ButtonStyle", "Selected")

        val tab = state.styleData.value["@ButtonStyle"]!!
        assertThat(tab.states).hasSize(3)
        val newState = tab.states.last()
        assertThat(newState.name).isEqualTo("Selected")
        assertThat(newState.slots).hasSize(2) // same structure as Default
        assertThat(newState.slots.all { it.fillMode == FillMode.EMPTY }).isTrue()
        assertThat(newState.slots.all { it.value == "" }).isTrue()
    }

    @Test
    fun `should preserve slot structure from first state when adding custom state`() {
        val state = ComposerModalState(createTestElement())
        state.setStyleData("@ButtonStyle", createTestStyleTab())

        state.addCustomState("@ButtonStyle", "Active")

        val newState = state.styleData.value["@ButtonStyle"]!!.states.last()
        assertThat(newState.slots.map { it.name }).containsExactly("Background", "TextColor")
        assertThat(newState.slots.map { it.type }).containsExactly(
            ComposerPropertyType.IMAGE, ComposerPropertyType.COLOR
        )
    }

    @Test
    fun `should no-op addCustomState when style not found`() {
        val state = ComposerModalState(createTestElement())

        state.addCustomState("@NonExistent", "Selected")

        assertThat(state.styleData.value).isEmpty()
    }

    @Test
    fun `should no-op addCustomState when style has no states`() {
        val state = ComposerModalState(createTestElement())
        val emptyTab = StyleTab("@Empty", "TestType", states = emptyList())
        state.setStyleData("@Empty", emptyTab)

        state.addCustomState("@Empty", "Selected")

        assertThat(state.styleData.value["@Empty"]!!.states).isEmpty()
    }

    // -- State collapse toggling --

    @Test
    fun `should initialize with empty collapsed states`() {
        val state = ComposerModalState(createTestElement())

        assertThat(state.collapsedStates.value).isEmpty()
    }

    @Test
    fun `should toggle state collapse with composite key`() {
        val state = ComposerModalState(createTestElement())

        state.toggleStateCollapse("@ButtonStyle:Default")
        assertThat(state.collapsedStates.value).containsExactly("@ButtonStyle:Default")

        state.toggleStateCollapse("@ButtonStyle:Default")
        assertThat(state.collapsedStates.value).isEmpty()
    }

    @Test
    fun `should collapse multiple states independently`() {
        val state = ComposerModalState(createTestElement())

        state.toggleStateCollapse("@ButtonStyle:Default")
        state.toggleStateCollapse("@ButtonStyle:Hovered")

        assertThat(state.collapsedStates.value).containsExactlyInAnyOrder(
            "@ButtonStyle:Default", "@ButtonStyle:Hovered"
        )

        state.toggleStateCollapse("@ButtonStyle:Default")
        assertThat(state.collapsedStates.value).containsExactly("@ButtonStyle:Hovered")
    }

    @Test
    fun `should track collapse state independently per style`() {
        val state = ComposerModalState(createTestElement())

        state.toggleStateCollapse("@ButtonStyle:Default")
        state.toggleStateCollapse("@LabelStyle:Default")

        assertThat(state.collapsedStates.value).containsExactlyInAnyOrder(
            "@ButtonStyle:Default", "@LabelStyle:Default"
        )
    }

    // -- Remaining gap coverage --

    @Test
    fun `setStyleData should not affect openStyleTabs`() {
        val state = ComposerModalState(createTestElement())
        state.openStyleTab("@ButtonStyle")

        state.setStyleData("@ButtonStyle", createTestStyleTab())

        assertThat(state.openStyleTabs.value).containsExactly("@ButtonStyle")
    }

    @Test
    fun `should no-op updateStyleSlot for non-existent stateName`() {
        val state = ComposerModalState(createTestElement())
        state.setStyleData("@ButtonStyle", createTestStyleTab())

        state.updateStyleSlot("@ButtonStyle", "NonExistent", "TextColor",
            fillMode = FillMode.LITERAL, value = "#ff0000")

        val defaultSlot = state.styleData.value["@ButtonStyle"]!!
            .states.first { it.name == "Default" }
            .slots.first { it.name == "TextColor" }
        assertThat(defaultSlot.fillMode).isEqualTo(FillMode.EMPTY)
    }

    @Test
    fun `should no-op updateStyleSlot for non-existent slotName`() {
        val state = ComposerModalState(createTestElement())
        state.setStyleData("@ButtonStyle", createTestStyleTab())

        state.updateStyleSlot("@ButtonStyle", "Default", "NonExistent",
            fillMode = FillMode.LITERAL, value = "test")

        val bgSlot = state.styleData.value["@ButtonStyle"]!!
            .states.first { it.name == "Default" }
            .slots.first { it.name == "Background" }
        assertThat(bgSlot.value).isEqualTo("bg.png")
    }

    @Test
    fun `should no-op updateStyleSlotAnchorValues for non-existent style`() {
        val state = ComposerModalState(createTestElement())

        state.updateStyleSlotAnchorValues("@NonExistent", "Default", "Anchor", "left", "10")

        assertThat(state.styleData.value).isEmpty()
    }

    @Test
    fun `should no-op clearStyleSlot for non-existent style`() {
        val state = ComposerModalState(createTestElement())
        state.setStyleData("@ButtonStyle", createTestStyleTab())

        state.clearStyleSlot("@NonExistent", "Default", "Background")

        val bgSlot = state.styleData.value["@ButtonStyle"]!!
            .states.first { it.name == "Default" }
            .slots.first { it.name == "Background" }
        assertThat(bgSlot.fillMode).isEqualTo(FillMode.LITERAL)
    }

    @Test
    fun `should only clear targeted slot leaving siblings unchanged`() {
        val state = ComposerModalState(createTestElement())
        state.setStyleData("@ButtonStyle", createTestStyleTab())

        state.clearStyleSlot("@ButtonStyle", "Hovered", "TextColor")

        val bgSlot = state.styleData.value["@ButtonStyle"]!!
            .states.first { it.name == "Hovered" }
            .slots.first { it.name == "Background" }
        assertThat(bgSlot.fillMode).isEqualTo(FillMode.LITERAL)
        assertThat(bgSlot.value).isEqualTo("bg_hover.png")
    }

    @Test
    fun `should add multiple custom states in order`() {
        val state = ComposerModalState(createTestElement())
        state.setStyleData("@ButtonStyle", createTestStyleTab())

        state.addCustomState("@ButtonStyle", "Selected")
        state.addCustomState("@ButtonStyle", "Focused")

        val tab = state.styleData.value["@ButtonStyle"]!!
        assertThat(tab.states).hasSize(4)
        assertThat(tab.states.map { it.name }).containsExactly(
            "Default", "Hovered", "Selected", "Focused"
        )
    }

    @Test
    fun `should overwrite style data with same name preserving map size`() {
        val state = ComposerModalState(createTestElement())
        state.setStyleData("@ButtonStyle", createTestStyleTab())
        state.setStyleData("@LabelStyle", createTestStyleTab("@LabelStyle", "LabelStyle"))

        state.setStyleData("@ButtonStyle", createTestStyleTab(styleType = "ReplacedType"))

        assertThat(state.styleData.value).hasSize(2)
        assertThat(state.styleData.value["@ButtonStyle"]?.styleType).isEqualTo("ReplacedType")
    }

    @Test
    fun `should remove only targeted style from multi-style map`() {
        val state = ComposerModalState(createTestElement())
        state.setStyleData("@ButtonStyle", createTestStyleTab())
        state.setStyleData("@LabelStyle", createTestStyleTab("@LabelStyle", "LabelStyle"))

        state.removeStyleData("@ButtonStyle")

        assertThat(state.styleData.value).hasSize(1)
        assertThat(state.styleData.value).containsKey("@LabelStyle")
    }
}
