// Copyright 2026 Hyve. All rights reserved.
package com.hyve.ui.composer

import com.hyve.ui.composer.model.*
import com.hyve.ui.core.id.ElementType
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

class ComposerModalStateTest {

    private fun createTestElement() = ElementDefinition(
        type = ElementType("Button"),
        id = "TestBtn",
        slots = listOf(
            PropertySlot("Text", ComposerPropertyType.TEXT, SlotCategory.TEXT, FillMode.LITERAL, "Hello"),
            PropertySlot("Color", ComposerPropertyType.COLOR, SlotCategory.APPEARANCE, FillMode.EMPTY, ""),
            PropertySlot("FontSize", ComposerPropertyType.NUMBER, SlotCategory.TEXT, FillMode.LITERAL, "14")
        )
    )

    @Test
    fun `should initialize with provided element`() {
        val element = createTestElement()
        val state = ComposerModalState(element)

        assertThat(state.element.value).isEqualTo(element)
        assertThat(state.openStyleTabs.value).isEmpty()
        assertThat(state.activeTab.value).isNull()
        assertThat(state.showCode.value).isTrue() // Code panel shown by default
        assertThat(state.editingId.value).isFalse()
        assertThat(state.collapsedCategories.value).isEmpty()
    }

    // -- Tab lifecycle tests (FR-6, FR-7) --

    @Test
    fun `should open style tab and activate it`() {
        val state = ComposerModalState(createTestElement())

        state.openStyleTab("@ButtonStyle")

        assertThat(state.openStyleTabs.value).containsExactly("@ButtonStyle")
        assertThat(state.activeTab.value).isEqualTo("@ButtonStyle")
    }

    @Test
    fun `should not create duplicate tabs`() {
        val state = ComposerModalState(createTestElement())

        state.openStyleTab("@ButtonStyle")
        state.openStyleTab("@ButtonStyle")

        assertThat(state.openStyleTabs.value).containsExactly("@ButtonStyle")
    }

    @Test
    fun `should open multiple style tabs in order`() {
        val state = ComposerModalState(createTestElement())

        state.openStyleTab("@ButtonStyle")
        state.openStyleTab("@LabelStyle")

        assertThat(state.openStyleTabs.value).containsExactly("@ButtonStyle", "@LabelStyle")
        assertThat(state.activeTab.value).isEqualTo("@LabelStyle") // Last opened is active
    }

    @Test
    fun `should activate existing tab without duplicating`() {
        val state = ComposerModalState(createTestElement())

        state.openStyleTab("@ButtonStyle")
        state.openStyleTab("@LabelStyle")
        state.openStyleTab("@ButtonStyle") // Re-activate, don't duplicate

        assertThat(state.openStyleTabs.value).containsExactly("@ButtonStyle", "@LabelStyle")
        assertThat(state.activeTab.value).isEqualTo("@ButtonStyle")
    }

    @Test
    fun `should close style tab and revert to element tab when active`() {
        val state = ComposerModalState(createTestElement())

        state.openStyleTab("@ButtonStyle")
        assertThat(state.activeTab.value).isEqualTo("@ButtonStyle")

        state.closeStyleTab("@ButtonStyle")

        assertThat(state.openStyleTabs.value).isEmpty()
        assertThat(state.activeTab.value).isNull() // Reverted to element tab
    }

    @Test
    fun `should close inactive tab without changing active tab`() {
        val state = ComposerModalState(createTestElement())

        state.openStyleTab("@ButtonStyle")
        state.openStyleTab("@LabelStyle")
        // Active is @LabelStyle

        state.closeStyleTab("@ButtonStyle") // Close inactive tab

        assertThat(state.openStyleTabs.value).containsExactly("@LabelStyle")
        assertThat(state.activeTab.value).isEqualTo("@LabelStyle") // Unchanged
    }

    @Test
    fun `should switch to element tab via setActiveTab null`() {
        val state = ComposerModalState(createTestElement())

        state.openStyleTab("@ButtonStyle")
        assertThat(state.activeTab.value).isEqualTo("@ButtonStyle")

        state.setActiveTab(null)

        assertThat(state.activeTab.value).isNull()
    }

    // -- Code toggle tests --

    @Test
    fun `should toggle code preview on and off`() {
        val state = ComposerModalState(createTestElement())

        assertThat(state.showCode.value).isTrue() // Default is on

        state.toggleCode()
        assertThat(state.showCode.value).isFalse()

        state.toggleCode()
        assertThat(state.showCode.value).isTrue()
    }

    // -- ID editing tests (FR-4) --

    @Test
    fun `should start editing id`() {
        val state = ComposerModalState(createTestElement())

        state.startEditingId()

        assertThat(state.editingId.value).isTrue()
    }

    @Test
    fun `should commit id and stop editing`() {
        val state = ComposerModalState(createTestElement())

        state.startEditingId()
        state.commitId("NewId")

        assertThat(state.editingId.value).isFalse()
        assertThat(state.element.value.id).isEqualTo("NewId")
    }

    @Test
    fun `should trim whitespace on id commit`() {
        val state = ComposerModalState(createTestElement())

        state.startEditingId()
        state.commitId("  SpacedId  ")

        assertThat(state.element.value.id).isEqualTo("SpacedId")
    }

    @Test
    fun `should cancel id editing without changing element`() {
        val state = ComposerModalState(createTestElement())
        val originalId = state.element.value.id

        state.startEditingId()
        state.cancelEditingId()

        assertThat(state.editingId.value).isFalse()
        assertThat(state.element.value.id).isEqualTo(originalId)
    }

    @Test
    fun `should allow empty id on commit`() {
        val state = ComposerModalState(createTestElement())

        state.startEditingId()
        state.commitId("")

        assertThat(state.element.value.id).isEmpty()
    }

    // -- Category collapse tests --

    @Test
    fun `should toggle category collapse`() {
        val state = ComposerModalState(createTestElement())

        state.toggleCategory("layout")
        assertThat(state.collapsedCategories.value).containsExactly("layout")

        state.toggleCategory("layout")
        assertThat(state.collapsedCategories.value).isEmpty()
    }

    @Test
    fun `should collapse multiple categories independently`() {
        val state = ComposerModalState(createTestElement())

        state.toggleCategory("layout")
        state.toggleCategory("text")

        assertThat(state.collapsedCategories.value).containsExactlyInAnyOrder("layout", "text")

        state.toggleCategory("layout")
        assertThat(state.collapsedCategories.value).containsExactly("text")
    }

    // -- Slot mutation tests --

    @Test
    fun `should update slot fill mode and value`() {
        val state = ComposerModalState(createTestElement())

        state.updateSlot("Color", fillMode = FillMode.LITERAL, value = "#ff0000")

        val updated = state.element.value.slots.first { it.name == "Color" }
        assertThat(updated.fillMode).isEqualTo(FillMode.LITERAL)
        assertThat(updated.value).isEqualTo("#ff0000")
    }

    @Test
    fun `should clear slot back to empty`() {
        val state = ComposerModalState(createTestElement())

        // Text is initially filled
        val before = state.element.value.slots.first { it.name == "Text" }
        assertThat(before.fillMode).isEqualTo(FillMode.LITERAL)

        state.clearSlot("Text")

        val after = state.element.value.slots.first { it.name == "Text" }
        assertThat(after.fillMode).isEqualTo(FillMode.EMPTY)
        assertThat(after.value).isEmpty()
    }

    @Test
    fun `should only update targeted slot leaving others unchanged`() {
        val state = ComposerModalState(createTestElement())

        state.updateSlot("Color", fillMode = FillMode.VARIABLE, value = "@primaryColor")

        // Text should be unchanged
        val textSlot = state.element.value.slots.first { it.name == "Text" }
        assertThat(textSlot.value).isEqualTo("Hello")
        assertThat(textSlot.fillMode).isEqualTo(FillMode.LITERAL)
    }

    @Test
    fun `should update only fill mode when value is null`() {
        val state = ComposerModalState(createTestElement())

        state.updateSlot("Text", fillMode = FillMode.VARIABLE)

        val slot = state.element.value.slots.first { it.name == "Text" }
        assertThat(slot.fillMode).isEqualTo(FillMode.VARIABLE)
        assertThat(slot.value).isEqualTo("Hello") // Value unchanged
    }

    @Test
    fun `should update only value when fillMode is null`() {
        val state = ComposerModalState(createTestElement())

        state.updateSlot("Text", value = "World")

        val slot = state.element.value.slots.first { it.name == "Text" }
        assertThat(slot.fillMode).isEqualTo(FillMode.LITERAL) // Mode unchanged
        assertThat(slot.value).isEqualTo("World")
    }

    // -- Anchor value update tests --

    private fun createTestElementWithAnchor() = ElementDefinition(
        type = ElementType("Panel"),
        id = "TestPanel",
        slots = listOf(
            PropertySlot(
                "Anchor", ComposerPropertyType.ANCHOR, SlotCategory.LAYOUT,
                FillMode.LITERAL, "left:10, top:20",
                anchorValues = mapOf("left" to "10", "top" to "20"),
            ),
            PropertySlot("Text", ComposerPropertyType.TEXT, SlotCategory.TEXT, FillMode.LITERAL, "Hello"),
        )
    )

    @Test
    fun `should update single anchor field value`() {
        val state = ComposerModalState(ElementDefinition(
            type = ElementType("Panel"), id = "P",
            slots = listOf(
                PropertySlot("Anchor", ComposerPropertyType.ANCHOR, SlotCategory.LAYOUT),
            ),
        ))

        state.updateSlotAnchorValues("Anchor", "left", "10")

        val slot = state.element.value.slots.first { it.name == "Anchor" }
        assertThat(slot.anchorValues).containsEntry("left", "10")
        assertThat(slot.fillMode).isEqualTo(FillMode.LITERAL)
    }

    @Test
    fun `should update anchor field without affecting other fields`() {
        val state = ComposerModalState(createTestElementWithAnchor())

        state.updateSlotAnchorValues("Anchor", "width", "100")

        val slot = state.element.value.slots.first { it.name == "Anchor" }
        assertThat(slot.anchorValues).containsEntry("left", "10")
        assertThat(slot.anchorValues).containsEntry("top", "20")
        assertThat(slot.anchorValues).containsEntry("width", "100")
    }

    @Test
    fun `should remove anchor field when value is blank`() {
        val state = ComposerModalState(createTestElementWithAnchor())

        state.updateSlotAnchorValues("Anchor", "left", "")

        val slot = state.element.value.slots.first { it.name == "Anchor" }
        assertThat(slot.anchorValues).doesNotContainKey("left")
        assertThat(slot.anchorValues).containsEntry("top", "20")
    }

    @Test
    fun `should synthesize value string from anchor fields`() {
        val state = ComposerModalState(ElementDefinition(
            type = ElementType("Panel"), id = "P",
            slots = listOf(
                PropertySlot("Anchor", ComposerPropertyType.ANCHOR, SlotCategory.LAYOUT),
            ),
        ))

        state.updateSlotAnchorValues("Anchor", "left", "10")
        state.updateSlotAnchorValues("Anchor", "top", "20")

        val slot = state.element.value.slots.first { it.name == "Anchor" }
        assertThat(slot.value).isEqualTo("left:10, top:20")
    }

    @Test
    fun `should set fill mode to LITERAL when updating anchor field`() {
        val state = ComposerModalState(ElementDefinition(
            type = ElementType("Panel"), id = "P",
            slots = listOf(
                PropertySlot("Anchor", ComposerPropertyType.ANCHOR, SlotCategory.LAYOUT, FillMode.EMPTY, ""),
            ),
        ))

        state.updateSlotAnchorValues("Anchor", "width", "50")

        val slot = state.element.value.slots.first { it.name == "Anchor" }
        assertThat(slot.fillMode).isEqualTo(FillMode.LITERAL)
    }

    @Test
    fun `should clear anchor values when clearing anchor slot`() {
        val state = ComposerModalState(createTestElementWithAnchor())

        state.clearSlot("Anchor")

        val slot = state.element.value.slots.first { it.name == "Anchor" }
        assertThat(slot.fillMode).isEqualTo(FillMode.EMPTY)
        assertThat(slot.value).isEmpty()
        assertThat(slot.anchorValues).isEmpty()
    }

    @Test
    fun `should clear regular slot without affecting other slots`() {
        val state = ComposerModalState(createTestElementWithAnchor())

        state.clearSlot("Text")

        val textSlot = state.element.value.slots.first { it.name == "Text" }
        assertThat(textSlot.fillMode).isEqualTo(FillMode.EMPTY)

        val anchorSlot = state.element.value.slots.first { it.name == "Anchor" }
        assertThat(anchorSlot.anchorValues).containsEntry("left", "10")
        assertThat(anchorSlot.fillMode).isEqualTo(FillMode.LITERAL)
    }

    // -- Tuple value update tests --

    private fun createTestElementWithTuple() = ElementDefinition(
        type = ElementType("Label"),
        id = "TestLabel",
        slots = listOf(
            PropertySlot(
                "Style", ComposerPropertyType.TUPLE, SlotCategory.STATE,
                FillMode.LITERAL, "FontSize: 24, TextColor: #ffffff",
                tupleValues = mapOf("FontSize" to "24", "TextColor" to "#ffffff"),
            ),
            PropertySlot("Text", ComposerPropertyType.TEXT, SlotCategory.TEXT, FillMode.LITERAL, "Hello"),
        )
    )

    @Test
    fun `should update single tuple field value`() {
        val state = ComposerModalState(createTestElementWithTuple())

        state.updateSlotTupleValues("Style", "FontSize", "32")

        val slot = state.element.value.slots.first { it.name == "Style" }
        assertThat(slot.tupleValues).containsEntry("FontSize", "32")
        assertThat(slot.tupleValues).containsEntry("TextColor", "#ffffff")
        assertThat(slot.fillMode).isEqualTo(FillMode.LITERAL)
    }

    @Test
    fun `should update tuple field without affecting other fields`() {
        val state = ComposerModalState(createTestElementWithTuple())

        state.updateSlotTupleValues("Style", "TextColor", "#ff0000")

        val slot = state.element.value.slots.first { it.name == "Style" }
        assertThat(slot.tupleValues["FontSize"]).isEqualTo("24")
        assertThat(slot.tupleValues["TextColor"]).isEqualTo("#ff0000")
    }

    @Test
    fun `should synthesize value string from tuple fields`() {
        val state = ComposerModalState(ElementDefinition(
            type = ElementType("Label"), id = "L",
            slots = listOf(
                PropertySlot("Style", ComposerPropertyType.TUPLE, SlotCategory.STATE),
            ),
        ))

        state.updateSlotTupleValues("Style", "FontSize", "24")
        state.updateSlotTupleValues("Style", "TextColor", "#ffffff")

        val slot = state.element.value.slots.first { it.name == "Style" }
        assertThat(slot.value).contains("FontSize: 24")
        assertThat(slot.value).contains("TextColor: #ffffff")
    }

    @Test
    fun `should set fill mode to LITERAL when updating tuple field`() {
        val state = ComposerModalState(ElementDefinition(
            type = ElementType("Label"), id = "L",
            slots = listOf(
                PropertySlot("Style", ComposerPropertyType.TUPLE, SlotCategory.STATE, FillMode.EMPTY, ""),
            ),
        ))

        state.updateSlotTupleValues("Style", "FontSize", "14")

        val slot = state.element.value.slots.first { it.name == "Style" }
        assertThat(slot.fillMode).isEqualTo(FillMode.LITERAL)
    }

    @Test
    fun `should add new tuple field`() {
        val state = ComposerModalState(createTestElementWithTuple())

        state.addSlotTupleField("Style", "RenderBold")

        val slot = state.element.value.slots.first { it.name == "Style" }
        assertThat(slot.tupleValues).containsKey("RenderBold")
        assertThat(slot.tupleValues["RenderBold"]).isEmpty()
        // Existing fields preserved
        assertThat(slot.tupleValues["FontSize"]).isEqualTo("24")
        assertThat(slot.tupleValues["TextColor"]).isEqualTo("#ffffff")
    }

    @Test
    fun `should not create duplicate tuple field`() {
        val state = ComposerModalState(createTestElementWithTuple())

        state.addSlotTupleField("Style", "FontSize")

        val slot = state.element.value.slots.first { it.name == "Style" }
        assertThat(slot.tupleValues["FontSize"]).isEqualTo("24") // Preserved original value
    }

    @Test
    fun `should remove tuple field`() {
        val state = ComposerModalState(createTestElementWithTuple())

        state.removeSlotTupleField("Style", "FontSize")

        val slot = state.element.value.slots.first { it.name == "Style" }
        assertThat(slot.tupleValues).doesNotContainKey("FontSize")
        assertThat(slot.tupleValues).containsEntry("TextColor", "#ffffff")
        assertThat(slot.fillMode).isEqualTo(FillMode.LITERAL)
    }

    @Test
    fun `should set fill mode to EMPTY when removing last tuple field`() {
        val state = ComposerModalState(ElementDefinition(
            type = ElementType("Label"), id = "L",
            slots = listOf(
                PropertySlot(
                    "Style", ComposerPropertyType.TUPLE, SlotCategory.STATE,
                    FillMode.LITERAL, "FontSize: 24",
                    tupleValues = mapOf("FontSize" to "24"),
                ),
            ),
        ))

        state.removeSlotTupleField("Style", "FontSize")

        val slot = state.element.value.slots.first { it.name == "Style" }
        assertThat(slot.tupleValues).isEmpty()
        assertThat(slot.fillMode).isEqualTo(FillMode.EMPTY)
    }

    @Test
    fun `should clear tuple values when clearing tuple slot`() {
        val state = ComposerModalState(createTestElementWithTuple())

        state.clearSlot("Style")

        val slot = state.element.value.slots.first { it.name == "Style" }
        assertThat(slot.fillMode).isEqualTo(FillMode.EMPTY)
        assertThat(slot.value).isEmpty()
        assertThat(slot.tupleValues).isEmpty()
    }

    @Test
    fun `should not affect other slots when updating tuple`() {
        val state = ComposerModalState(createTestElementWithTuple())

        state.updateSlotTupleValues("Style", "FontSize", "32")

        val textSlot = state.element.value.slots.first { it.name == "Text" }
        assertThat(textSlot.value).isEqualTo("Hello")
        assertThat(textSlot.fillMode).isEqualTo(FillMode.LITERAL)
    }

    // -- synthesizeTupleValue tests --

    @Test
    fun `should synthesize empty string for empty tuple values`() {
        val result = ComposerModalState.synthesizeTupleValue(emptyMap())
        assertThat(result).isEmpty()
    }

    @Test
    fun `should synthesize value from single tuple field`() {
        val result = ComposerModalState.synthesizeTupleValue(mapOf("FontSize" to "24"))
        assertThat(result).isEqualTo("FontSize: 24")
    }

    @Test
    fun `should synthesize value from multiple tuple fields`() {
        val result = ComposerModalState.synthesizeTupleValue(
            mapOf("FontSize" to "24", "TextColor" to "#ffffff")
        )
        assertThat(result).contains("FontSize: 24")
        assertThat(result).contains("TextColor: #ffffff")
        assertThat(result).contains(", ")
    }

    // -- Style-level tuple tests --

    @Test
    fun `should update style slot tuple field`() {
        val state = ComposerModalState(ElementDefinition(
            type = ElementType("Label"), id = "L",
            slots = emptyList(),
        ))

        val styleTab = StyleTab(
            name = "@LabelStyle",
            styleType = "TextStyle",
            states = listOf(
                StyleState(
                    name = "Default",
                    slots = listOf(
                        PropertySlot(
                            "Background", ComposerPropertyType.TUPLE, SlotCategory.APPEARANCE,
                            FillMode.LITERAL, "TexturePath: bg.png, Border: 8",
                            tupleValues = mapOf("TexturePath" to "bg.png", "Border" to "8"),
                        ),
                    ),
                ),
            ),
        )
        state.setStyleData("@LabelStyle", styleTab)

        state.updateStyleSlotTupleValues("@LabelStyle", "Default", "Background", "Border", "12")

        val updatedTab = state.styleData.value["@LabelStyle"]!!
        val bgSlot = updatedTab.states.first().slots.first { it.name == "Background" }
        assertThat(bgSlot.tupleValues["Border"]).isEqualTo("12")
        assertThat(bgSlot.tupleValues["TexturePath"]).isEqualTo("bg.png")
    }

    @Test
    fun `should add tuple field to style slot`() {
        val state = ComposerModalState(ElementDefinition(
            type = ElementType("Label"), id = "L",
            slots = emptyList(),
        ))

        val styleTab = StyleTab(
            name = "@LabelStyle",
            styleType = "TextStyle",
            states = listOf(
                StyleState(
                    name = "Default",
                    slots = listOf(
                        PropertySlot(
                            "Background", ComposerPropertyType.TUPLE, SlotCategory.APPEARANCE,
                            FillMode.LITERAL, "TexturePath: bg.png",
                            tupleValues = mapOf("TexturePath" to "bg.png"),
                        ),
                    ),
                ),
            ),
        )
        state.setStyleData("@LabelStyle", styleTab)

        state.addStyleSlotTupleField("@LabelStyle", "Default", "Background", "Border")

        val updatedTab = state.styleData.value["@LabelStyle"]!!
        val bgSlot = updatedTab.states.first().slots.first { it.name == "Background" }
        assertThat(bgSlot.tupleValues).containsKey("Border")
        assertThat(bgSlot.tupleValues["TexturePath"]).isEqualTo("bg.png")
    }

    @Test
    fun `should remove tuple field from style slot`() {
        val state = ComposerModalState(ElementDefinition(
            type = ElementType("Label"), id = "L",
            slots = emptyList(),
        ))

        val styleTab = StyleTab(
            name = "@LabelStyle",
            styleType = "TextStyle",
            states = listOf(
                StyleState(
                    name = "Default",
                    slots = listOf(
                        PropertySlot(
                            "Background", ComposerPropertyType.TUPLE, SlotCategory.APPEARANCE,
                            FillMode.LITERAL, "TexturePath: bg.png, Border: 8",
                            tupleValues = mapOf("TexturePath" to "bg.png", "Border" to "8"),
                        ),
                    ),
                ),
            ),
        )
        state.setStyleData("@LabelStyle", styleTab)

        state.removeStyleSlotTupleField("@LabelStyle", "Default", "Background", "Border")

        val updatedTab = state.styleData.value["@LabelStyle"]!!
        val bgSlot = updatedTab.states.first().slots.first { it.name == "Background" }
        assertThat(bgSlot.tupleValues).doesNotContainKey("Border")
        assertThat(bgSlot.tupleValues).containsEntry("TexturePath", "bg.png")
    }

    @Test
    fun `should set style tuple slot to EMPTY when removing last field`() {
        val state = ComposerModalState(ElementDefinition(
            type = ElementType("Label"), id = "L",
            slots = emptyList(),
        ))

        val styleTab = StyleTab(
            name = "@LabelStyle",
            styleType = "TextStyle",
            states = listOf(
                StyleState(
                    name = "Default",
                    slots = listOf(
                        PropertySlot(
                            "Background", ComposerPropertyType.TUPLE, SlotCategory.APPEARANCE,
                            FillMode.LITERAL, "Border: 8",
                            tupleValues = mapOf("Border" to "8"),
                        ),
                    ),
                ),
            ),
        )
        state.setStyleData("@LabelStyle", styleTab)

        state.removeStyleSlotTupleField("@LabelStyle", "Default", "Background", "Border")

        val updatedTab = state.styleData.value["@LabelStyle"]!!
        val bgSlot = updatedTab.states.first().slots.first { it.name == "Background" }
        assertThat(bgSlot.tupleValues).isEmpty()
        assertThat(bgSlot.fillMode).isEqualTo(FillMode.EMPTY)
    }

    @Test
    fun `should clear style slot tuple values`() {
        val state = ComposerModalState(ElementDefinition(
            type = ElementType("Label"), id = "L",
            slots = emptyList(),
        ))

        val styleTab = StyleTab(
            name = "@LabelStyle",
            styleType = "TextStyle",
            states = listOf(
                StyleState(
                    name = "Default",
                    slots = listOf(
                        PropertySlot(
                            "Background", ComposerPropertyType.TUPLE, SlotCategory.APPEARANCE,
                            FillMode.LITERAL, "TexturePath: bg.png",
                            tupleValues = mapOf("TexturePath" to "bg.png"),
                        ),
                    ),
                ),
            ),
        )
        state.setStyleData("@LabelStyle", styleTab)

        state.clearStyleSlot("@LabelStyle", "Default", "Background")

        val updatedTab = state.styleData.value["@LabelStyle"]!!
        val bgSlot = updatedTab.states.first().slots.first { it.name == "Background" }
        assertThat(bgSlot.fillMode).isEqualTo(FillMode.EMPTY)
        assertThat(bgSlot.value).isEmpty()
        assertThat(bgSlot.tupleValues).isEmpty()
    }
}
