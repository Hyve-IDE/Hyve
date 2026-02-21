// Copyright 2026 Hyve. All rights reserved.
package com.hyve.ui.rendering.painter

import androidx.compose.ui.graphics.Color
import com.hyve.ui.core.domain.elements.UIElement
import com.hyve.ui.core.domain.properties.PropertyMap
import com.hyve.ui.core.domain.properties.PropertyValue
import com.hyve.ui.core.id.ElementType
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

/**
 * Tests the tuple extraction contracts that P1-P4 draw methods depend on.
 * Uses resolveStyleToTuple and PropertyValue.Tuple.get() — the same functions
 * the private draw methods call internally.
 */
class WidgetStyleTupleExtractionTest {

    private fun element(vararg props: Pair<String, PropertyValue>) = UIElement(
        type = ElementType("Test"),
        id = null,
        properties = PropertyMap.of(*props)
    )

    // ═══════════════════════════════════════════════════════
    // TextField (P1) - Background PatchStyle tuple extraction
    // ═══════════════════════════════════════════════════════

    @Test
    fun `TextField Background tuple extracts TexturePath and Border`() {
        val bgTuple = PropertyValue.Tuple(mapOf(
            "TexturePath" to PropertyValue.Text("textures/input_bg.png"),
            "Border" to PropertyValue.Number(16.0)
        ))
        val el = element("Background" to bgTuple)

        val resolved = resolveStyleToTuple(el.getProperty("Background"))
        assertThat(resolved).isNotNull
        assertThat((resolved!!.get("TexturePath") as? PropertyValue.Text)?.value)
            .isEqualTo("textures/input_bg.png")
        assertThat((resolved.get("Border") as? PropertyValue.Number)?.value?.toFloat())
            .isEqualTo(16f)
    }

    @Test
    fun `TextField Background tuple with HorizontalBorder and VerticalBorder`() {
        val bgTuple = PropertyValue.Tuple(mapOf(
            "TexturePath" to PropertyValue.Text("textures/input_bg.png"),
            "Border" to PropertyValue.Number(16.0),
            "HorizontalBorder" to PropertyValue.Number(12.0),
            "VerticalBorder" to PropertyValue.Number(8.0)
        ))

        val resolved = resolveStyleToTuple(bgTuple)
        assertThat(resolved).isNotNull

        // Separate H/V borders should be extractable
        val hBorder = (resolved!!.get("HorizontalBorder") as? PropertyValue.Number)?.value?.toFloat()
        val vBorder = (resolved.get("VerticalBorder") as? PropertyValue.Number)?.value?.toFloat()
        assertThat(hBorder).isEqualTo(12f)
        assertThat(vBorder).isEqualTo(8f)

        // Uniform border still present as fallback
        assertThat((resolved.get("Border") as? PropertyValue.Number)?.value?.toFloat()).isEqualTo(16f)
    }

    @Test
    fun `TextField Background tuple with Color but no TexturePath`() {
        val bgTuple = PropertyValue.Tuple(mapOf(
            "Color" to PropertyValue.Text("#2D2D44")
        ))
        val el = element("Background" to bgTuple)

        val resolved = resolveStyleToTuple(el.getProperty("Background"))
        assertThat(resolved).isNotNull

        // TexturePath should be null — caller falls back to color fill
        assertThat(resolved!!.get("TexturePath")).isNull()
        val colorVal = resolved.get("Color")
        assertThat(colorVal).isNotNull
        val color = colorFromValue(colorVal, Color.Transparent)
        assertThat(color).isNotEqualTo(Color.Transparent)
    }

    @Test
    fun `TextField Decoration Default Background extraction`() {
        // Nested tuple: Decoration -> Default -> Background -> TexturePath/Border
        val decorBg = PropertyValue.Tuple(mapOf(
            "TexturePath" to PropertyValue.Text("textures/decor_bg.png"),
            "Border" to PropertyValue.Number(10.0)
        ))
        val decorDefault = PropertyValue.Tuple(mapOf(
            "Background" to decorBg
        ))
        val decoration = PropertyValue.Tuple(mapOf(
            "Default" to decorDefault
        ))
        val el = element("Decoration" to decoration)

        val decorTuple = resolveStyleToTuple(el.getProperty("Decoration"))
        assertThat(decorTuple).isNotNull

        val defaultTuple = decorTuple!!.get("Default") as? PropertyValue.Tuple
        assertThat(defaultTuple).isNotNull

        val bgTuple = defaultTuple!!.get("Background") as? PropertyValue.Tuple
        assertThat(bgTuple).isNotNull
        assertThat((bgTuple!!.get("TexturePath") as? PropertyValue.Text)?.value)
            .isEqualTo("textures/decor_bg.png")
        assertThat((bgTuple.get("Border") as? PropertyValue.Number)?.value?.toFloat())
            .isEqualTo(10f)
    }

    @Test
    fun `TextField Background tuple with ImagePath type`() {
        // TexturePath can be PropertyValue.ImagePath instead of Text
        val bgTuple = PropertyValue.Tuple(mapOf(
            "TexturePath" to PropertyValue.ImagePath("textures/input_bg.png"),
            "Border" to PropertyValue.Number(16.0)
        ))
        val el = element("Background" to bgTuple)

        val resolved = resolveStyleToTuple(el.getProperty("Background"))
        assertThat(resolved).isNotNull

        // drawTextField extracts both Text and ImagePath for TexturePath
        val texPath = (resolved!!.get("TexturePath") as? PropertyValue.Text)?.value
            ?: (resolved.get("TexturePath") as? PropertyValue.ImagePath)?.path
        assertThat(texPath).isEqualTo("textures/input_bg.png")
    }

    // ═══════════════════════════════════════════════════════
    // DropdownBox (P2) - DropdownBoxStyle tuple extraction
    // ═══════════════════════════════════════════════════════

    @Test
    fun `DropdownBox Style DefaultBackground tuple extraction`() {
        val defaultBg = PropertyValue.Tuple(mapOf(
            "TexturePath" to PropertyValue.Text("textures/dropdown_bg.png"),
            "Border" to PropertyValue.Number(16.0)
        ))
        val style = PropertyValue.Tuple(mapOf(
            "DefaultBackground" to defaultBg
        ))
        val el = element("Style" to style)

        val styleTuple = resolveStyleToTuple(el.getProperty("Style"))
        assertThat(styleTuple).isNotNull

        val bgTuple = styleTuple!!.get("DefaultBackground") as? PropertyValue.Tuple
        assertThat(bgTuple).isNotNull
        assertThat((bgTuple!!.get("TexturePath") as? PropertyValue.Text)?.value)
            .isEqualTo("textures/dropdown_bg.png")
        assertThat((bgTuple.get("Border") as? PropertyValue.Number)?.value?.toFloat())
            .isEqualTo(16f)
    }

    @Test
    fun `DropdownBox Style arrow texture and dimensions`() {
        val style = PropertyValue.Tuple(mapOf(
            "DefaultArrowTexturePath" to PropertyValue.Text("textures/arrow_down.png"),
            "ArrowWidth" to PropertyValue.Number(12.0),
            "ArrowHeight" to PropertyValue.Number(8.0)
        ))
        val el = element("Style" to style)

        val styleTuple = resolveStyleToTuple(el.getProperty("Style"))
        assertThat(styleTuple).isNotNull

        assertThat((styleTuple!!.get("DefaultArrowTexturePath") as? PropertyValue.Text)?.value)
            .isEqualTo("textures/arrow_down.png")
        assertThat((styleTuple.get("ArrowWidth") as? PropertyValue.Number)?.value?.toFloat())
            .isEqualTo(12f)
        assertThat((styleTuple.get("ArrowHeight") as? PropertyValue.Number)?.value?.toFloat())
            .isEqualTo(8f)
    }

    @Test
    fun `DropdownBox Style LabelStyle nested extraction`() {
        val labelStyle = PropertyValue.Tuple(mapOf(
            "TextColor" to PropertyValue.Text("#FFFFFF"),
            "FontSize" to PropertyValue.Number(14.0),
            "RenderBold" to PropertyValue.Boolean(true),
            "RenderUppercase" to PropertyValue.Boolean(false)
        ))
        val style = PropertyValue.Tuple(mapOf(
            "LabelStyle" to labelStyle
        ))
        val el = element("Style" to style)

        val styleTuple = resolveStyleToTuple(el.getProperty("Style"))
        assertThat(styleTuple).isNotNull

        // resolveStyleToTuple also handles nested tuple (LabelStyle is a Tuple)
        val labelTuple = resolveStyleToTuple(styleTuple!!.get("LabelStyle"))
        assertThat(labelTuple).isNotNull
        assertThat((labelTuple!!.get("FontSize") as? PropertyValue.Number)?.value?.toFloat())
            .isEqualTo(14f)
        assertThat((labelTuple.get("RenderBold") as? PropertyValue.Boolean)?.value).isTrue()
        assertThat((labelTuple.get("RenderUppercase") as? PropertyValue.Boolean)?.value).isFalse()

        val textColor = colorFromValue(labelTuple.get("TextColor"), Color.Black)
        assertThat(textColor).isEqualTo(Color.White)
    }

    @Test
    fun `DropdownBox Style HorizontalPadding extraction`() {
        val style = PropertyValue.Tuple(mapOf(
            "HorizontalPadding" to PropertyValue.Number(8.0),
            "DefaultArrowTexturePath" to PropertyValue.Text("textures/arrow.png")
        ))
        val el = element("Style" to style)

        val styleTuple = resolveStyleToTuple(el.getProperty("Style"))
        assertThat(styleTuple).isNotNull
        assertThat((styleTuple!!.get("HorizontalPadding") as? PropertyValue.Number)?.value?.toFloat())
            .isEqualTo(8f)
    }

    @Test
    fun `DropdownBox Style with missing DefaultBackground falls through`() {
        // Style without DefaultBackground — caller should fall back to flat color
        val style = PropertyValue.Tuple(mapOf(
            "LabelStyle" to PropertyValue.Tuple(mapOf("FontSize" to PropertyValue.Number(12.0)))
        ))
        val el = element("Style" to style)

        val styleTuple = resolveStyleToTuple(el.getProperty("Style"))
        assertThat(styleTuple).isNotNull

        val defaultBg = styleTuple!!.get("DefaultBackground") as? PropertyValue.Tuple
        assertThat(defaultBg).isNull()
    }

    // ═══════════════════════════════════════════════════════
    // CheckBox (P3) - CheckBoxStyle with Checked/Unchecked states
    // ═══════════════════════════════════════════════════════

    @Test
    fun `CheckBox Style Checked DefaultBackground texture extraction`() {
        val checkedBg = PropertyValue.Tuple(mapOf(
            "TexturePath" to PropertyValue.Text("textures/checkbox_checked.png")
        ))
        val checkedState = PropertyValue.Tuple(mapOf(
            "DefaultBackground" to checkedBg
        ))
        val style = PropertyValue.Tuple(mapOf(
            "Checked" to checkedState
        ))
        val el = element("Style" to style)

        val styleTuple = resolveStyleToTuple(el.getProperty("Style"))
        assertThat(styleTuple).isNotNull

        val checked = styleTuple!!.get("Checked") as? PropertyValue.Tuple
        assertThat(checked).isNotNull

        val bg = checked!!.get("DefaultBackground") as? PropertyValue.Tuple
        assertThat(bg).isNotNull
        assertThat((bg!!.get("TexturePath") as? PropertyValue.Text)?.value)
            .isEqualTo("textures/checkbox_checked.png")
    }

    @Test
    fun `CheckBox Style Unchecked DefaultBackground color extraction`() {
        val uncheckedBg = PropertyValue.Tuple(mapOf(
            "Color" to PropertyValue.Text("#00000000")
        ))
        val uncheckedState = PropertyValue.Tuple(mapOf(
            "DefaultBackground" to uncheckedBg
        ))
        val style = PropertyValue.Tuple(mapOf(
            "Unchecked" to uncheckedState
        ))
        val el = element("Style" to style)

        val styleTuple = resolveStyleToTuple(el.getProperty("Style"))
        assertThat(styleTuple).isNotNull

        val unchecked = styleTuple!!.get("Unchecked") as? PropertyValue.Tuple
        assertThat(unchecked).isNotNull

        val bg = unchecked!!.get("DefaultBackground") as? PropertyValue.Tuple
        assertThat(bg).isNotNull

        val color = colorFromValue(bg!!.get("Color"), Color.White)
        assertThat(color.alpha).isEqualTo(0f) // transparent
    }

    @Test
    fun `CheckBox Style presence suppresses border`() {
        // When CheckBoxStyle is present, drawCheckBox skips border drawing
        val style = PropertyValue.Tuple(mapOf(
            "Checked" to PropertyValue.Tuple(mapOf(
                "DefaultBackground" to PropertyValue.Tuple(mapOf(
                    "TexturePath" to PropertyValue.Text("check.png")
                ))
            ))
        ))
        val el = element("Style" to style)

        // The check in drawCheckBox: val hasStyle = resolveStyleToTuple(element.getProperty("Style")) != null
        val resolved = resolveStyleToTuple(el.getProperty("Style"))
        assertThat(resolved).isNotNull // hasStyle = true -> border suppressed
    }

    @Test
    fun `CheckBox with no Style returns null for border logic`() {
        val el = element("Checked" to PropertyValue.Boolean(true))

        // No Style property => resolveStyleToTuple returns null => border drawn
        val resolved = resolveStyleToTuple(el.getProperty("Style"))
        assertThat(resolved).isNull() // hasStyle = false -> border drawn
    }

    @Test
    fun `CheckBox Style checked vs unchecked state selection`() {
        val checkedBg = PropertyValue.Tuple(mapOf(
            "TexturePath" to PropertyValue.Text("textures/checked.png")
        ))
        val uncheckedBg = PropertyValue.Tuple(mapOf(
            "Color" to PropertyValue.Text("#00000000")
        ))
        val style = PropertyValue.Tuple(mapOf(
            "Checked" to PropertyValue.Tuple(mapOf("DefaultBackground" to checkedBg)),
            "Unchecked" to PropertyValue.Tuple(mapOf("DefaultBackground" to uncheckedBg))
        ))

        val styleTuple = resolveStyleToTuple(style)
        assertThat(styleTuple).isNotNull

        val checkedState = styleTuple!!.get("Checked") as? PropertyValue.Tuple
        val uncheckedState = styleTuple.get("Unchecked") as? PropertyValue.Tuple
        assertThat(checkedState).isNotNull
        assertThat(uncheckedState).isNotNull

        // When checked=true, use checkedState; when checked=false, use uncheckedState
        val activeWhenChecked = checkedState
        val activeWhenUnchecked = uncheckedState

        val checkedTexture = (activeWhenChecked!!.get("DefaultBackground") as? PropertyValue.Tuple)
            ?.get("TexturePath")
        assertThat((checkedTexture as? PropertyValue.Text)?.value).isEqualTo("textures/checked.png")

        val uncheckedColor = (activeWhenUnchecked!!.get("DefaultBackground") as? PropertyValue.Tuple)
            ?.get("Color")
        assertThat(colorFromValue(uncheckedColor, Color.White).alpha).isEqualTo(0f)
    }

    // ═══════════════════════════════════════════════════════
    // Slider (P4) - SliderStyle tuple extraction
    // ═══════════════════════════════════════════════════════

    @Test
    fun `Slider SliderStyle Background tuple extraction`() {
        val bgTuple = PropertyValue.Tuple(mapOf(
            "TexturePath" to PropertyValue.Text("textures/slider_track.png"),
            "Border" to PropertyValue.Number(8.0)
        ))
        val style = PropertyValue.Tuple(mapOf(
            "Background" to bgTuple,
            "Handle" to PropertyValue.Text("textures/slider_handle.png")
        ))
        val el = element("SliderStyle" to style)

        val styleTuple = resolveStyleToTuple(el.getProperty("SliderStyle"))
        assertThat(styleTuple).isNotNull

        val bg = styleTuple!!.get("Background") as? PropertyValue.Tuple
        assertThat(bg).isNotNull
        assertThat((bg!!.get("TexturePath") as? PropertyValue.Text)?.value)
            .isEqualTo("textures/slider_track.png")
        assertThat((bg.get("Border") as? PropertyValue.Number)?.value?.toFloat())
            .isEqualTo(8f)
    }

    @Test
    fun `Slider SliderStyle Handle texture path`() {
        val style = PropertyValue.Tuple(mapOf(
            "Handle" to PropertyValue.Text("textures/slider_handle.png")
        ))
        val el = element("SliderStyle" to style)

        val styleTuple = resolveStyleToTuple(el.getProperty("SliderStyle"))
        assertThat(styleTuple).isNotNull
        assertThat((styleTuple!!.get("Handle") as? PropertyValue.Text)?.value)
            .isEqualTo("textures/slider_handle.png")
    }

    @Test
    fun `Slider SliderStyle HandleWidth and HandleHeight`() {
        val style = PropertyValue.Tuple(mapOf(
            "HandleWidth" to PropertyValue.Number(20.0),
            "HandleHeight" to PropertyValue.Number(24.0)
        ))
        val el = element("SliderStyle" to style)

        val styleTuple = resolveStyleToTuple(el.getProperty("SliderStyle"))
        assertThat(styleTuple).isNotNull
        assertThat((styleTuple!!.get("HandleWidth") as? PropertyValue.Number)?.value?.toFloat())
            .isEqualTo(20f)
        assertThat((styleTuple.get("HandleHeight") as? PropertyValue.Number)?.value?.toFloat())
            .isEqualTo(24f)
    }

    @Test
    fun `Slider SliderStyle with missing Background returns null`() {
        // Style without Background — fallback to drawn track
        val style = PropertyValue.Tuple(mapOf(
            "Handle" to PropertyValue.Text("textures/handle.png"),
            "HandleWidth" to PropertyValue.Number(20.0)
        ))
        val el = element("SliderStyle" to style)

        val styleTuple = resolveStyleToTuple(el.getProperty("SliderStyle"))
        assertThat(styleTuple).isNotNull

        val bg = styleTuple!!.get("Background") as? PropertyValue.Tuple
        assertThat(bg).isNull()
    }

    @Test
    fun `Slider NumberFieldStyle nested extraction`() {
        val nfStyle = PropertyValue.Tuple(mapOf(
            "TextColor" to PropertyValue.Text("#FFFFFF"),
            "FontSize" to PropertyValue.Number(12.0),
            "RenderBold" to PropertyValue.Boolean(false),
            "Background" to PropertyValue.Text("#1A1A2E")
        ))
        val el = element("NumberFieldStyle" to nfStyle)

        val styleTuple = resolveStyleToTuple(el.getProperty("NumberFieldStyle"))
        assertThat(styleTuple).isNotNull

        val textColor = colorFromValue(styleTuple!!.get("TextColor"), Color.Black)
        assertThat(textColor).isEqualTo(Color.White)

        assertThat((styleTuple.get("FontSize") as? PropertyValue.Number)?.value?.toFloat())
            .isEqualTo(12f)
        assertThat((styleTuple.get("RenderBold") as? PropertyValue.Boolean)?.value).isFalse()

        val bgColor = colorFromValue(styleTuple.get("Background"), Color.Transparent)
        assertThat(bgColor).isNotEqualTo(Color.Transparent)
    }
}
