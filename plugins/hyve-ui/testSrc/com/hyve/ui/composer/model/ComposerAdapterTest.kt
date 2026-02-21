// Copyright 2026 Hyve. All rights reserved.
package com.hyve.ui.composer.model

import com.hyve.ui.core.domain.anchor.AnchorDimension
import com.hyve.ui.core.domain.anchor.AnchorValue
import com.hyve.ui.core.domain.elements.UIElement
import com.hyve.ui.core.domain.properties.PropertyMap
import com.hyve.ui.core.domain.properties.PropertyValue
import com.hyve.ui.core.id.ElementId
import com.hyve.ui.core.id.ElementType
import com.hyve.ui.core.id.PropertyName
import com.hyve.ui.schema.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

class ComposerAdapterTest {

    // -- toSlotValue tests --

    @Test
    fun `should convert Text property to literal fill mode`() {
        val (mode, value) = PropertyValue.Text("Hello").toSlotValue()
        assertThat(mode).isEqualTo(FillMode.LITERAL)
        assertThat(value).isEqualTo("Hello")
    }

    @Test
    fun `should convert Number property to literal fill mode`() {
        val (mode, value) = PropertyValue.Number(14.0).toSlotValue()
        assertThat(mode).isEqualTo(FillMode.LITERAL)
        assertThat(value).isEqualTo("14")
    }

    @Test
    fun `should convert decimal Number property correctly`() {
        val (mode, value) = PropertyValue.Number(0.5).toSlotValue()
        assertThat(mode).isEqualTo(FillMode.LITERAL)
        assertThat(value).isEqualTo("0.5")
    }

    @Test
    fun `should convert Percent property to literal fill mode`() {
        val (mode, value) = PropertyValue.Percent(0.5).toSlotValue()
        assertThat(mode).isEqualTo(FillMode.LITERAL)
        assertThat(value).isEqualTo("50%")
    }

    @Test
    fun `should convert Boolean property to literal fill mode`() {
        val (mode, value) = PropertyValue.Boolean(true).toSlotValue()
        assertThat(mode).isEqualTo(FillMode.LITERAL)
        assertThat(value).isEqualTo("true")
    }

    @Test
    fun `should convert Color property to literal fill mode`() {
        val (mode, value) = PropertyValue.Color("#ff0000").toSlotValue()
        assertThat(mode).isEqualTo(FillMode.LITERAL)
        assertThat(value).isEqualTo("#ff0000")
    }

    @Test
    fun `should convert ImagePath property to literal fill mode`() {
        val (mode, value) = PropertyValue.ImagePath("icon.png").toSlotValue()
        assertThat(mode).isEqualTo(FillMode.LITERAL)
        assertThat(value).isEqualTo("icon.png")
    }

    @Test
    fun `should convert LocalizedText property to localization fill mode`() {
        val (mode, value) = PropertyValue.LocalizedText("ui.button.submit").toSlotValue()
        assertThat(mode).isEqualTo(FillMode.LOCALIZATION)
        assertThat(value).isEqualTo("ui.button.submit")
    }

    @Test
    fun `should convert VariableRef property to import fill mode`() {
        val (mode, value) = PropertyValue.VariableRef("Common", listOf("@HeaderStyle")).toSlotValue()
        assertThat(mode).isEqualTo(FillMode.IMPORT)
        assertThat(value).isEqualTo("\$Common.@HeaderStyle")
    }

    @Test
    fun `should convert Expression property to expression fill mode`() {
        val expr = PropertyValue.Expression(
            left = PropertyValue.Number(10.0),
            operator = "*",
            right = PropertyValue.Number(2.0)
        )
        val (mode, value) = expr.toSlotValue()
        assertThat(mode).isEqualTo(FillMode.EXPRESSION)
        assertThat(value).isEqualTo("10 * 2")
    }

    @Test
    fun `should convert Null property to empty fill mode`() {
        val (mode, value) = PropertyValue.Null.toSlotValue()
        assertThat(mode).isEqualTo(FillMode.EMPTY)
        assertThat(value).isEmpty()
    }

    // -- toComposerPropertyType tests --

    @Test
    fun `should map all PropertyType values to ComposerPropertyType`() {
        assertThat(PropertyType.TEXT.toComposerPropertyType()).isEqualTo(ComposerPropertyType.TEXT)
        assertThat(PropertyType.NUMBER.toComposerPropertyType()).isEqualTo(ComposerPropertyType.NUMBER)
        assertThat(PropertyType.PERCENT.toComposerPropertyType()).isEqualTo(ComposerPropertyType.PERCENT)
        assertThat(PropertyType.BOOLEAN.toComposerPropertyType()).isEqualTo(ComposerPropertyType.BOOLEAN)
        assertThat(PropertyType.COLOR.toComposerPropertyType()).isEqualTo(ComposerPropertyType.COLOR)
        assertThat(PropertyType.ANCHOR.toComposerPropertyType()).isEqualTo(ComposerPropertyType.ANCHOR)
        assertThat(PropertyType.STYLE.toComposerPropertyType()).isEqualTo(ComposerPropertyType.STYLE)
        assertThat(PropertyType.TUPLE.toComposerPropertyType()).isEqualTo(ComposerPropertyType.TUPLE)
        assertThat(PropertyType.IMAGE_PATH.toComposerPropertyType()).isEqualTo(ComposerPropertyType.IMAGE)
        assertThat(PropertyType.FONT_PATH.toComposerPropertyType()).isEqualTo(ComposerPropertyType.FONT)
    }

    // -- inferSlotCategory tests --

    @Test
    fun `should categorize layout properties`() {
        assertThat(inferSlotCategory("Anchor")).isEqualTo(SlotCategory.LAYOUT)
        assertThat(inferSlotCategory("Width")).isEqualTo(SlotCategory.LAYOUT)
        assertThat(inferSlotCategory("Padding")).isEqualTo(SlotCategory.LAYOUT)
    }

    @Test
    fun `should categorize appearance properties`() {
        assertThat(inferSlotCategory("Background")).isEqualTo(SlotCategory.APPEARANCE)
        assertThat(inferSlotCategory("BorderColor")).isEqualTo(SlotCategory.APPEARANCE)
        assertThat(inferSlotCategory("Opacity")).isEqualTo(SlotCategory.APPEARANCE)
    }

    @Test
    fun `should categorize text properties`() {
        assertThat(inferSlotCategory("Text")).isEqualTo(SlotCategory.TEXT)
        assertThat(inferSlotCategory("FontSize")).isEqualTo(SlotCategory.TEXT)
        assertThat(inferSlotCategory("RenderBold")).isEqualTo(SlotCategory.TEXT)
    }

    @Test
    fun `should categorize interaction properties`() {
        assertThat(inferSlotCategory("OnClick")).isEqualTo(SlotCategory.INTERACTION)
        assertThat(inferSlotCategory("Enabled")).isEqualTo(SlotCategory.INTERACTION)
    }

    @Test
    fun `should categorize state properties`() {
        assertThat(inferSlotCategory("Visible")).isEqualTo(SlotCategory.STATE)
        assertThat(inferSlotCategory("Style")).isEqualTo(SlotCategory.STATE)
    }

    @Test
    fun `should default unknown properties to data category`() {
        assertThat(inferSlotCategory("CustomProperty")).isEqualTo(SlotCategory.DATA)
        assertThat(inferSlotCategory("UnknownField")).isEqualTo(SlotCategory.DATA)
    }

    // -- toElementDefinition tests --

    @Test
    fun `should convert UIElement to ElementDefinition with schema`() {
        val element = UIElement(
            type = ElementType("Button"),
            id = ElementId("SubmitBtn"),
            properties = PropertyMap.of(
                "Text" to PropertyValue.Text("Submit"),
                "FontSize" to PropertyValue.Number(14.0),
                "Visible" to PropertyValue.Boolean(true)
            )
        )

        val schema = ElementSchema(
            type = ElementType("Button"),
            category = ElementCategory.INTERACTIVE,
            description = "Button element",
            canHaveChildren = false,
            properties = listOf(
                PropertySchema(PropertyName("Text"), PropertyType.TEXT),
                PropertySchema(PropertyName("FontSize"), PropertyType.NUMBER),
                PropertySchema(PropertyName("Visible"), PropertyType.BOOLEAN),
                PropertySchema(PropertyName("Color"), PropertyType.COLOR)
            )
        )

        val definition = element.toElementDefinition(schema)

        assertThat(definition.type.value).isEqualTo("Button")
        assertThat(definition.id).isEqualTo("SubmitBtn")
        assertThat(definition.slots).hasSize(4)

        // Text slot should be filled
        val textSlot = definition.slots.first { it.name == "Text" }
        assertThat(textSlot.fillMode).isEqualTo(FillMode.LITERAL)
        assertThat(textSlot.value).isEqualTo("Submit")
        assertThat(textSlot.type).isEqualTo(ComposerPropertyType.TEXT)

        // Color slot should be empty
        val colorSlot = definition.slots.first { it.name == "Color" }
        assertThat(colorSlot.fillMode).isEqualTo(FillMode.EMPTY)
        assertThat(colorSlot.value).isEmpty()
    }

    @Test
    fun `should handle UIElement with no id`() {
        val element = UIElement(
            type = ElementType("Label"),
            id = null,
            properties = PropertyMap.empty()
        )

        val schema = ElementSchema(
            type = ElementType("Label"),
            category = ElementCategory.TEXT,
            description = "Label",
            canHaveChildren = false,
            properties = listOf(
                PropertySchema(PropertyName("Text"), PropertyType.TEXT)
            )
        )

        val definition = element.toElementDefinition(schema)
        assertThat(definition.id).isEmpty()
    }

    // -- applyTo tests --

    @Test
    fun `should apply ElementDefinition back to UIElement`() {
        val original = UIElement(
            type = ElementType("Button"),
            id = ElementId("Btn"),
            properties = PropertyMap.of(
                "Text" to PropertyValue.Text("Old")
            )
        )

        val definition = ElementDefinition(
            type = ElementType("Button"),
            id = "UpdatedBtn",
            slots = listOf(
                PropertySlot("Text", ComposerPropertyType.TEXT, SlotCategory.TEXT, FillMode.LITERAL, "New"),
                PropertySlot("Color", ComposerPropertyType.COLOR, SlotCategory.APPEARANCE, FillMode.EMPTY, "")
            )
        )

        val result = definition.applyTo(original)

        assertThat(result.id?.value).isEqualTo("UpdatedBtn")
        assertThat(result.getProperty("Text")).isEqualTo(PropertyValue.Text("New"))
        // Empty slot should not be in properties
        assertThat(result.getProperty("Color")).isNull()
    }

    @Test
    fun `should preserve properties not in slots during applyTo`() {
        val original = UIElement(
            type = ElementType("Button"),
            id = ElementId("Btn"),
            properties = PropertyMap.of(
                "Text" to PropertyValue.Text("Hello"),
                "CustomProp" to PropertyValue.Text("preserved")
            )
        )

        val definition = ElementDefinition(
            type = ElementType("Button"),
            id = "Btn",
            slots = listOf(
                PropertySlot("Text", ComposerPropertyType.TEXT, SlotCategory.TEXT, FillMode.LITERAL, "Updated")
            )
        )

        val result = definition.applyTo(original)
        assertThat(result.getProperty("Text")).isEqualTo(PropertyValue.Text("Updated"))
        assertThat(result.getProperty("CustomProp")).isEqualTo(PropertyValue.Text("preserved"))
    }

    @Test
    fun `should set id to null when blank`() {
        val original = UIElement(
            type = ElementType("Label"),
            id = ElementId("OldId"),
            properties = PropertyMap.empty()
        )

        val definition = ElementDefinition(
            type = ElementType("Label"),
            id = "",
            slots = emptyList()
        )

        val result = definition.applyTo(original)
        assertThat(result.id).isNull()
    }

    // -- slotToPropertyValue tests --

    @Test
    fun `should convert literal text slot to Text property`() {
        val slot = PropertySlot("Text", ComposerPropertyType.TEXT, SlotCategory.TEXT, FillMode.LITERAL, "Hello")
        val result = slotToPropertyValue(slot)
        assertThat(result).isEqualTo(PropertyValue.Text("Hello"))
    }

    @Test
    fun `should convert literal number slot to Number property`() {
        val slot = PropertySlot("FontSize", ComposerPropertyType.NUMBER, SlotCategory.TEXT, FillMode.LITERAL, "14")
        val result = slotToPropertyValue(slot)
        assertThat(result).isEqualTo(PropertyValue.Number(14.0))
    }

    @Test
    fun `should convert literal color slot to Color property`() {
        val slot = PropertySlot("Color", ComposerPropertyType.COLOR, SlotCategory.APPEARANCE, FillMode.LITERAL, "#ff0000")
        val result = slotToPropertyValue(slot)
        assertThat(result).isEqualTo(PropertyValue.Color("#ff0000"))
    }

    @Test
    fun `should convert literal boolean slot to Boolean property`() {
        val slot = PropertySlot("Visible", ComposerPropertyType.BOOLEAN, SlotCategory.STATE, FillMode.LITERAL, "true")
        val result = slotToPropertyValue(slot)
        assertThat(result).isEqualTo(PropertyValue.Boolean(true))
    }

    @Test
    fun `should convert localization slot to LocalizedText property`() {
        val slot = PropertySlot("Text", ComposerPropertyType.TEXT, SlotCategory.TEXT, FillMode.LOCALIZATION, "ui.button.ok")
        val result = slotToPropertyValue(slot)
        assertThat(result).isEqualTo(PropertyValue.LocalizedText("ui.button.ok"))
    }

    @Test
    fun `should return null for empty slot`() {
        val slot = PropertySlot("Text", ComposerPropertyType.TEXT, SlotCategory.TEXT, FillMode.EMPTY, "")
        val result = slotToPropertyValue(slot)
        assertThat(result).isNull()
    }

    @Test
    fun `should return Unknown for invalid number value`() {
        val slot = PropertySlot("FontSize", ComposerPropertyType.NUMBER, SlotCategory.TEXT, FillMode.LITERAL, "not-a-number")
        val result = slotToPropertyValue(slot)
        assertThat(result).isInstanceOf(PropertyValue.Unknown::class.java)
    }

    @Test
    fun `should convert literal percent slot to Percent property`() {
        val slot = PropertySlot("Width", ComposerPropertyType.PERCENT, SlotCategory.LAYOUT, FillMode.LITERAL, "50%")
        val result = slotToPropertyValue(slot)
        assertThat(result).isEqualTo(PropertyValue.Percent(0.5))
    }

    @Test
    fun `should convert image slot to ImagePath property`() {
        val slot = PropertySlot("Icon", ComposerPropertyType.IMAGE, SlotCategory.APPEARANCE, FillMode.LITERAL, "icon.png")
        val result = slotToPropertyValue(slot)
        assertThat(result).isEqualTo(PropertyValue.ImagePath("icon.png"))
    }

    @Test
    fun `should convert font slot to FontPath property`() {
        val slot = PropertySlot("Font", ComposerPropertyType.FONT, SlotCategory.TEXT, FillMode.LITERAL, "arial.ttf")
        val result = slotToPropertyValue(slot)
        assertThat(result).isEqualTo(PropertyValue.FontPath("arial.ttf"))
    }

    // -- Anchor round-trip tests --

    @Test
    fun `should reconstruct Anchor from slot with absolute anchorValues`() {
        val slot = PropertySlot(
            name = "Anchor",
            type = ComposerPropertyType.ANCHOR,
            category = SlotCategory.LAYOUT,
            fillMode = FillMode.LITERAL,
            value = "(Left: 10, Top: 20, Width: 304, Height: 152)",
            anchorValues = mapOf("left" to "10", "top" to "20", "width" to "304", "height" to "152")
        )
        val result = slotToPropertyValue(slot)
        assertThat(result).isInstanceOf(PropertyValue.Anchor::class.java)
        val anchor = (result as PropertyValue.Anchor).anchor
        assertThat(anchor.left).isEqualTo(AnchorDimension.Absolute(10f))
        assertThat(anchor.top).isEqualTo(AnchorDimension.Absolute(20f))
        assertThat(anchor.width).isEqualTo(AnchorDimension.Absolute(304f))
        assertThat(anchor.height).isEqualTo(AnchorDimension.Absolute(152f))
        assertThat(anchor.right).isNull()
        assertThat(anchor.bottom).isNull()
    }

    @Test
    fun `should reconstruct Anchor with relative percent values`() {
        val slot = PropertySlot(
            name = "Anchor",
            type = ComposerPropertyType.ANCHOR,
            category = SlotCategory.LAYOUT,
            fillMode = FillMode.LITERAL,
            value = "(Left: 0, Top: 0, Width: 100%, Height: 50%)",
            anchorValues = mapOf("left" to "0", "top" to "0", "width" to "100%", "height" to "50%")
        )
        val result = slotToPropertyValue(slot)
        assertThat(result).isInstanceOf(PropertyValue.Anchor::class.java)
        val anchor = (result as PropertyValue.Anchor).anchor
        assertThat(anchor.left).isEqualTo(AnchorDimension.Absolute(0f))
        assertThat(anchor.top).isEqualTo(AnchorDimension.Absolute(0f))
        assertThat(anchor.width).isEqualTo(AnchorDimension.Relative(1.0f))
        assertThat(anchor.height).isEqualTo(AnchorDimension.Relative(0.5f))
    }

    @Test
    fun `should fall back to Unknown for anchor with empty anchorValues`() {
        val slot = PropertySlot(
            name = "Anchor",
            type = ComposerPropertyType.ANCHOR,
            category = SlotCategory.LAYOUT,
            fillMode = FillMode.LITERAL,
            value = "(Left: 10, Top: 20)",
            anchorValues = emptyMap()
        )
        val result = slotToPropertyValue(slot)
        assertThat(result).isInstanceOf(PropertyValue.Unknown::class.java)
    }

    @Test
    fun `should round-trip Anchor through toSlotValue and back`() {
        val original = PropertyValue.Anchor(
            AnchorValue.absolute(left = 50f, top = 100f, width = 200f, height = 80f)
        )
        val (fillMode, stringValue) = original.toSlotValue()
        val anchorValues = mapOf(
            "left" to "50", "top" to "100", "width" to "200", "height" to "80"
        )
        val slot = PropertySlot(
            name = "Anchor",
            type = ComposerPropertyType.ANCHOR,
            category = SlotCategory.LAYOUT,
            fillMode = fillMode,
            value = stringValue,
            anchorValues = anchorValues
        )
        val result = slotToPropertyValue(slot)
        assertThat(result).isEqualTo(original)
    }

    @Test
    fun `should round-trip full element with Anchor through Composer`() {
        val anchor = AnchorValue.absolute(left = 10f, top = 20f, width = 304f, height = 152f)
        val original = UIElement(
            type = ElementType("ItemGrid"),
            id = ElementId("ItemGrid_1"),
            properties = PropertyMap.of(
                "Anchor" to PropertyValue.Anchor(anchor),
                "SlotsPerRow" to PropertyValue.Number(4.0)
            )
        )

        val schema = ElementSchema(
            type = ElementType("ItemGrid"),
            category = ElementCategory.CONTAINER,
            description = "Item grid",
            canHaveChildren = true,
            properties = listOf(
                PropertySchema(PropertyName("Anchor"), PropertyType.ANCHOR),
                PropertySchema(PropertyName("SlotsPerRow"), PropertyType.NUMBER)
            )
        )

        // Convert to definition and back
        val definition = original.toElementDefinition(schema)
        val result = definition.applyTo(original)

        // Anchor should be preserved as PropertyValue.Anchor
        val resultAnchor = result.getProperty("Anchor")
        assertThat(resultAnchor).isInstanceOf(PropertyValue.Anchor::class.java)
        assertThat(resultAnchor).isEqualTo(PropertyValue.Anchor(anchor))

        // Number properties should also round-trip
        assertThat(result.getProperty("SlotsPerRow")).isEqualTo(PropertyValue.Number(4.0))

        // Element should be unchanged (no-change detection)
        assertThat(result).isEqualTo(original)
    }

    @Test
    fun `should preserve Anchor when schema type is TUPLE`() {
        // Real-world scenario: schema discovery inferred Anchor as TUPLE because
        // game files use variable references in anchor values. But the actual
        // element on canvas has a proper PropertyValue.Anchor.
        val anchor = AnchorValue.absolute(left = 804f, top = 541f, width = 304f, height = 152f)
        val original = UIElement(
            type = ElementType("ItemGrid"),
            id = ElementId("ItemGrid_1"),
            properties = PropertyMap.of(
                "Anchor" to PropertyValue.Anchor(anchor),
                "SlotsPerRow" to PropertyValue.Number(4.0)
            )
        )

        val schema = ElementSchema(
            type = ElementType("ItemGrid"),
            category = ElementCategory.CONTAINER,
            description = "Item grid",
            canHaveChildren = true,
            properties = listOf(
                // Schema says TUPLE (not ANCHOR) — this is the real-world mismatch
                PropertySchema(PropertyName("Anchor"), PropertyType.TUPLE),
                // Schema says ANY — maps to TEXT in Composer
                PropertySchema(PropertyName("SlotsPerRow"), PropertyType.ANY)
            )
        )

        val definition = original.toElementDefinition(schema)
        val result = definition.applyTo(original)

        // Anchor must be preserved as PropertyValue.Anchor (not degraded to Unknown)
        val resultAnchor = result.getProperty("Anchor")
        assertThat(resultAnchor).isInstanceOf(PropertyValue.Anchor::class.java)
        assertThat(resultAnchor).isEqualTo(PropertyValue.Anchor(anchor))

        // SlotsPerRow must be preserved as Number (not degraded to Text)
        assertThat(result.getProperty("SlotsPerRow")).isEqualTo(PropertyValue.Number(4.0))

        // Element should be unchanged
        assertThat(result).isEqualTo(original)
    }

    // -- Tuple extraction tests --

    @Test
    fun `should extract tuple values from Tuple property`() {
        val tuple = PropertyValue.Tuple(mapOf(
            "FontSize" to PropertyValue.Number(24.0),
            "TextColor" to PropertyValue.Color("#ffffff"),
            "RenderBold" to PropertyValue.Boolean(true),
        ))

        val element = UIElement(
            type = ElementType("Label"),
            id = ElementId("lbl"),
            properties = PropertyMap.of("Style" to tuple)
        )

        val schema = ElementSchema(
            type = ElementType("Label"),
            category = ElementCategory.TEXT,
            description = "Label",
            canHaveChildren = false,
            properties = listOf(
                PropertySchema(PropertyName("Style"), PropertyType.TUPLE)
            )
        )

        val definition = element.toElementDefinition(schema)
        val styleSlot = definition.slots.first { it.name == "Style" }

        assertThat(styleSlot.type).isEqualTo(ComposerPropertyType.TUPLE)
        assertThat(styleSlot.fillMode).isEqualTo(FillMode.LITERAL)
        assertThat(styleSlot.tupleValues).hasSize(3)
        assertThat(styleSlot.tupleValues["FontSize"]).isEqualTo("24")
        assertThat(styleSlot.tupleValues["TextColor"]).isEqualTo("#ffffff")
        assertThat(styleSlot.tupleValues["RenderBold"]).isEqualTo("true")
    }

    @Test
    fun `should extract empty tuple values for non-tuple property`() {
        val element = UIElement(
            type = ElementType("Label"),
            id = null,
            properties = PropertyMap.of("Text" to PropertyValue.Text("Hello"))
        )

        val schema = ElementSchema(
            type = ElementType("Label"),
            category = ElementCategory.TEXT,
            description = "Label",
            canHaveChildren = false,
            properties = listOf(
                PropertySchema(PropertyName("Text"), PropertyType.TEXT)
            )
        )

        val definition = element.toElementDefinition(schema)
        val textSlot = definition.slots.first { it.name == "Text" }
        assertThat(textSlot.tupleValues).isEmpty()
    }

    @Test
    fun `should extract Text values without quotes in tuple values`() {
        val tuple = PropertyValue.Tuple(mapOf(
            "TexturePath" to PropertyValue.Text("button_bg.png"),
            "Border" to PropertyValue.Number(8.0),
        ))

        val element = UIElement(
            type = ElementType("Button"),
            id = null,
            properties = PropertyMap.of("Background" to tuple)
        )

        val schema = ElementSchema(
            type = ElementType("Button"),
            category = ElementCategory.INTERACTIVE,
            description = "Button",
            canHaveChildren = false,
            properties = listOf(
                PropertySchema(PropertyName("Background"), PropertyType.TUPLE)
            )
        )

        val definition = element.toElementDefinition(schema)
        val bgSlot = definition.slots.first { it.name == "Background" }
        assertThat(bgSlot.tupleValues["TexturePath"]).isEqualTo("button_bg.png")
        assertThat(bgSlot.tupleValues["Border"]).isEqualTo("8")
    }

    // -- Tuple reconstruction tests --

    @Test
    fun `should reconstruct Tuple from slot with tupleValues`() {
        val slot = PropertySlot(
            name = "Style",
            type = ComposerPropertyType.TUPLE,
            category = SlotCategory.STATE,
            fillMode = FillMode.LITERAL,
            value = "FontSize: 24, TextColor: #ffffff",
            tupleValues = mapOf("FontSize" to "24", "TextColor" to "#ffffff")
        )
        val result = slotToPropertyValue(slot)
        assertThat(result).isInstanceOf(PropertyValue.Tuple::class.java)
        val tuple = result as PropertyValue.Tuple
        assertThat(tuple.values).hasSize(2)
        assertThat(tuple.values["FontSize"]).isEqualTo(PropertyValue.Number(24.0))
        assertThat(tuple.values["TextColor"]).isEqualTo(PropertyValue.Color("#ffffff"))
    }

    @Test
    fun `should reconstruct Tuple with boolean values`() {
        val slot = PropertySlot(
            name = "Style",
            type = ComposerPropertyType.TUPLE,
            category = SlotCategory.STATE,
            fillMode = FillMode.LITERAL,
            value = "RenderBold: true, RenderItalic: false",
            tupleValues = mapOf("RenderBold" to "true", "RenderItalic" to "false")
        )
        val result = slotToPropertyValue(slot)
        assertThat(result).isInstanceOf(PropertyValue.Tuple::class.java)
        val tuple = result as PropertyValue.Tuple
        assertThat(tuple.values["RenderBold"]).isEqualTo(PropertyValue.Boolean(true))
        assertThat(tuple.values["RenderItalic"]).isEqualTo(PropertyValue.Boolean(false))
    }

    @Test
    fun `should reconstruct Tuple with text values`() {
        val slot = PropertySlot(
            name = "Background",
            type = ComposerPropertyType.TUPLE,
            category = SlotCategory.APPEARANCE,
            fillMode = FillMode.LITERAL,
            value = "TexturePath: button_bg.png, Border: 8",
            tupleValues = mapOf("TexturePath" to "button_bg.png", "Border" to "8")
        )
        val result = slotToPropertyValue(slot)
        assertThat(result).isInstanceOf(PropertyValue.Tuple::class.java)
        val tuple = result as PropertyValue.Tuple
        assertThat(tuple.values["TexturePath"]).isEqualTo(PropertyValue.Text("button_bg.png"))
        assertThat(tuple.values["Border"]).isEqualTo(PropertyValue.Number(8.0))
    }

    @Test
    fun `should fall back to parseTupleString when tupleValues is empty`() {
        val slot = PropertySlot(
            name = "Offset",
            type = ComposerPropertyType.TUPLE,
            category = SlotCategory.LAYOUT,
            fillMode = FillMode.LITERAL,
            value = "(X: 10, Y: 20)",
            tupleValues = emptyMap()
        )
        val result = slotToPropertyValue(slot)
        // Falls back to parseTupleString which produces a real Tuple
        assertThat(result).isNotNull()
    }

    // -- inferPropertyValue tests --

    @Test
    fun `should infer boolean true`() {
        val result = inferPropertyValue("true")
        assertThat(result).isEqualTo(PropertyValue.Boolean(true))
    }

    @Test
    fun `should infer boolean false`() {
        val result = inferPropertyValue("false")
        assertThat(result).isEqualTo(PropertyValue.Boolean(false))
    }

    @Test
    fun `should infer boolean case-insensitively`() {
        assertThat(inferPropertyValue("True")).isEqualTo(PropertyValue.Boolean(true))
        assertThat(inferPropertyValue("FALSE")).isEqualTo(PropertyValue.Boolean(false))
    }

    @Test
    fun `should infer integer number`() {
        val result = inferPropertyValue("24")
        assertThat(result).isEqualTo(PropertyValue.Number(24.0))
    }

    @Test
    fun `should infer decimal number`() {
        val result = inferPropertyValue("0.5")
        assertThat(result).isEqualTo(PropertyValue.Number(0.5))
    }

    @Test
    fun `should infer negative number`() {
        val result = inferPropertyValue("-10")
        assertThat(result).isEqualTo(PropertyValue.Number(-10.0))
    }

    @Test
    fun `should infer color from hash prefix`() {
        val result = inferPropertyValue("#ff6b00")
        assertThat(result).isEqualTo(PropertyValue.Color("#ff6b00"))
    }

    @Test
    fun `should infer quoted string as text without quotes`() {
        val result = inferPropertyValue("\"hello world\"")
        assertThat(result).isEqualTo(PropertyValue.Text("hello world"))
    }

    @Test
    fun `should infer plain string as text`() {
        val result = inferPropertyValue("Center")
        assertThat(result).isEqualTo(PropertyValue.Text("Center"))
    }

    @Test
    fun `should infer string with whitespace trimmed`() {
        val result = inferPropertyValue("  24  ")
        assertThat(result).isEqualTo(PropertyValue.Number(24.0))
    }

    // -- Tuple round-trip tests --

    @Test
    fun `should round-trip Tuple through toElementDefinition and applyTo`() {
        val tuple = PropertyValue.Tuple(mapOf(
            "FontSize" to PropertyValue.Number(24.0),
            "TextColor" to PropertyValue.Color("#ffffff"),
            "RenderBold" to PropertyValue.Boolean(true),
        ))

        val original = UIElement(
            type = ElementType("Label"),
            id = ElementId("Lbl_1"),
            properties = PropertyMap.of("Style" to tuple)
        )

        val schema = ElementSchema(
            type = ElementType("Label"),
            category = ElementCategory.TEXT,
            description = "Label",
            canHaveChildren = false,
            properties = listOf(
                PropertySchema(PropertyName("Style"), PropertyType.TUPLE)
            )
        )

        // Convert to definition and back without modification
        val definition = original.toElementDefinition(schema)
        val result = definition.applyTo(original)

        // Tuple should be preserved unchanged
        val resultStyle = result.getProperty("Style")
        assertThat(resultStyle).isInstanceOf(PropertyValue.Tuple::class.java)
        assertThat(resultStyle).isEqualTo(tuple)
        assertThat(result).isEqualTo(original)
    }

    @Test
    fun `should detect tuple as changed when tupleValues modified`() {
        val tuple = PropertyValue.Tuple(mapOf(
            "FontSize" to PropertyValue.Number(24.0),
            "TextColor" to PropertyValue.Color("#ffffff"),
        ))

        val original = UIElement(
            type = ElementType("Label"),
            id = ElementId("Lbl_1"),
            properties = PropertyMap.of("Style" to tuple)
        )

        val schema = ElementSchema(
            type = ElementType("Label"),
            category = ElementCategory.TEXT,
            description = "Label",
            canHaveChildren = false,
            properties = listOf(
                PropertySchema(PropertyName("Style"), PropertyType.TUPLE)
            )
        )

        // Convert to definition, then modify a tuple field
        val definition = original.toElementDefinition(schema)
        val modifiedDefinition = definition.copy(
            slots = definition.slots.map { slot ->
                if (slot.name == "Style") {
                    val newTupleValues = slot.tupleValues.toMutableMap().apply {
                        put("FontSize", "32")
                    }
                    slot.copy(
                        value = "FontSize: 32, TextColor: #ffffff",
                        tupleValues = newTupleValues
                    )
                } else slot
            }
        )

        val result = modifiedDefinition.applyTo(original)
        val resultTuple = result.getProperty("Style") as PropertyValue.Tuple
        assertThat(resultTuple.values["FontSize"]).isEqualTo(PropertyValue.Number(32.0))
    }

    @Test
    fun `should preserve Tuple when schema type is ANY but value is Tuple`() {
        val tuple = PropertyValue.Tuple(mapOf(
            "X" to PropertyValue.Number(10.0),
            "Y" to PropertyValue.Number(20.0),
        ))

        val original = UIElement(
            type = ElementType("Widget"),
            id = ElementId("w1"),
            properties = PropertyMap.of("Offset" to tuple)
        )

        val schema = ElementSchema(
            type = ElementType("Widget"),
            category = ElementCategory.CONTAINER,
            description = "Widget",
            canHaveChildren = false,
            properties = listOf(
                PropertySchema(PropertyName("Offset"), PropertyType.ANY)
            )
        )

        val definition = original.toElementDefinition(schema)
        // resolvePropertyType should override ANY→TEXT to TUPLE
        val offsetSlot = definition.slots.first { it.name == "Offset" }
        assertThat(offsetSlot.type).isEqualTo(ComposerPropertyType.TUPLE)

        val result = definition.applyTo(original)
        assertThat(result).isEqualTo(original)
    }

    @Test
    fun `should use converted value when slot was modified by user`() {
        val original = UIElement(
            type = ElementType("Button"),
            id = ElementId("Btn"),
            properties = PropertyMap.of(
                "Text" to PropertyValue.Text("Old")
            )
        )

        val schema = ElementSchema(
            type = ElementType("Button"),
            category = ElementCategory.INTERACTIVE,
            description = "Button",
            canHaveChildren = false,
            properties = listOf(
                PropertySchema(PropertyName("Text"), PropertyType.TEXT)
            )
        )

        // Convert to definition, then modify the text slot
        val definition = original.toElementDefinition(schema)
        val modifiedDefinition = definition.copy(
            slots = definition.slots.map { slot ->
                if (slot.name == "Text") slot.copy(value = "New") else slot
            }
        )

        val result = modifiedDefinition.applyTo(original)

        // Modified slot should use the new value
        assertThat(result.getProperty("Text")).isEqualTo(PropertyValue.Text("New"))
    }
}
