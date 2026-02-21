// Copyright 2026 Hyve. All rights reserved.
package com.hyve.ui.rendering.painter

import androidx.compose.ui.graphics.Color
import com.hyve.ui.core.domain.properties.PropertyValue
import com.hyve.ui.core.domain.styles.StyleReference
import com.hyve.ui.core.id.PropertyName
import com.hyve.ui.core.id.StyleName
import com.hyve.ui.core.id.ImportAlias
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

class CanvasPainterTupleResolutionTest {

    // --- resolveStyleToTuple ---

    @Test
    fun `resolveStyleToTuple returns Tuple as-is`() {
        val tuple = PropertyValue.Tuple(mapOf("FontSize" to PropertyValue.Number(14.0)))
        assertThat(resolveStyleToTuple(tuple)).isSameAs(tuple)
    }

    @Test
    fun `resolveStyleToTuple converts Inline StyleReference to Tuple`() {
        val inlineProps = mapOf(
            PropertyName("FontSize") to PropertyValue.Number(14.0) as PropertyValue,
            PropertyName("RenderBold") to PropertyValue.Boolean(true) as PropertyValue
        )
        val style = PropertyValue.Style(StyleReference.Inline(inlineProps))
        val result = resolveStyleToTuple(style)

        assertThat(result).isNotNull
        assertThat(result!!.values).containsKey("FontSize")
        assertThat(result.values).containsKey("RenderBold")
        assertThat((result.values["FontSize"] as PropertyValue.Number).value).isEqualTo(14.0)
    }

    @Test
    fun `resolveStyleToTuple returns null for Local StyleReference`() {
        val style = PropertyValue.Style(StyleReference.Local(StyleName("MyStyle")))
        assertThat(resolveStyleToTuple(style)).isNull()
    }

    @Test
    fun `resolveStyleToTuple returns null for Imported StyleReference`() {
        val style = PropertyValue.Style(
            StyleReference.Imported(ImportAlias("\$Common"), StyleName("HeaderStyle"))
        )
        assertThat(resolveStyleToTuple(style)).isNull()
    }

    @Test
    fun `resolveStyleToTuple returns null for null input`() {
        assertThat(resolveStyleToTuple(null)).isNull()
    }

    @Test
    fun `resolveStyleToTuple returns null for non-Style non-Tuple types`() {
        assertThat(resolveStyleToTuple(PropertyValue.Text("hello"))).isNull()
        assertThat(resolveStyleToTuple(PropertyValue.Number(42.0))).isNull()
        assertThat(resolveStyleToTuple(PropertyValue.Boolean(true))).isNull()
    }

    // --- Tuple.get ---

    @Test
    fun `Tuple get returns matching value by exact key`() {
        val tuple = PropertyValue.Tuple(mapOf(
            "FontSize" to PropertyValue.Number(14.0),
            "TextColor" to PropertyValue.Text("#FF0000")
        ))
        val result = tuple.get("FontSize")
        assertThat(result).isEqualTo(PropertyValue.Number(14.0))
    }

    @Test
    fun `Tuple get returns matching value case-insensitively`() {
        val tuple = PropertyValue.Tuple(mapOf(
            "FontSize" to PropertyValue.Number(14.0)
        ))
        assertThat(tuple.get("fontsize")).isEqualTo(PropertyValue.Number(14.0))
        assertThat(tuple.get("FONTSIZE")).isEqualTo(PropertyValue.Number(14.0))
        assertThat(tuple.get("fontSize")).isEqualTo(PropertyValue.Number(14.0))
    }

    @Test
    fun `Tuple get returns null for missing key`() {
        val tuple = PropertyValue.Tuple(mapOf(
            "FontSize" to PropertyValue.Number(14.0)
        ))
        assertThat(tuple.get("Background")).isNull()
    }

    @Test
    fun `Tuple get returns first match when multiple case variants exist`() {
        // LinkedHashMap preserves insertion order, so first match = first inserted
        val tuple = PropertyValue.Tuple(linkedMapOf(
            "fontSize" to PropertyValue.Number(10.0),
            "FontSize" to PropertyValue.Number(14.0)
        ))
        // find {} returns the first match
        val result = tuple.get("fontsize")
        assertThat(result).isEqualTo(PropertyValue.Number(10.0))
    }

    @Test
    fun `Tuple get works with empty tuple`() {
        val tuple = PropertyValue.Tuple(emptyMap())
        assertThat(tuple.get("anything")).isNull()
    }

    // --- Nested tuple navigation (P1-P4 draw method patterns) ---

    @Test
    fun `resolveStyleToTuple descends into nested Inline style`() {
        // A Style property containing another Style as a sub-property (Inline)
        val innerProps = mapOf(
            PropertyName("TextColor") to PropertyValue.Text("#FFFFFF") as PropertyValue,
            PropertyName("FontSize") to PropertyValue.Number(14.0) as PropertyValue
        )
        val outerProps = mapOf(
            PropertyName("LabelStyle") to PropertyValue.Style(StyleReference.Inline(innerProps)) as PropertyValue,
            PropertyName("PanelWidth") to PropertyValue.Number(200.0) as PropertyValue
        )
        val outerStyle = PropertyValue.Style(StyleReference.Inline(outerProps))

        val outerTuple = resolveStyleToTuple(outerStyle)
        assertThat(outerTuple).isNotNull

        // LabelStyle is a Style(Inline) — resolveStyleToTuple should convert it to Tuple
        val labelStyleValue = outerTuple!!.get("LabelStyle")
        val labelTuple = resolveStyleToTuple(labelStyleValue)
        assertThat(labelTuple).isNotNull
        assertThat((labelTuple!!.get("FontSize") as? PropertyValue.Number)?.value).isEqualTo(14.0)
    }

    @Test
    fun `Tuple get on nested Tuple returns inner Tuple`() {
        val innerTuple = PropertyValue.Tuple(mapOf(
            "TexturePath" to PropertyValue.Text("textures/bg.png"),
            "Border" to PropertyValue.Number(8.0)
        ))
        val outerTuple = PropertyValue.Tuple(mapOf(
            "Background" to innerTuple,
            "Handle" to PropertyValue.Text("textures/handle.png")
        ))

        val bg = outerTuple.get("Background")
        assertThat(bg).isInstanceOf(PropertyValue.Tuple::class.java)
        val bgTuple = bg as PropertyValue.Tuple
        assertThat((bgTuple.get("TexturePath") as? PropertyValue.Text)?.value)
            .isEqualTo("textures/bg.png")
        assertThat((bgTuple.get("Border") as? PropertyValue.Number)?.value?.toFloat())
            .isEqualTo(8f)
    }

    @Test
    fun `colorFromValue parses Color from Tuple context`() {
        // Helper used in CheckBox for DefaultBackground.Color
        val bgTuple = PropertyValue.Tuple(mapOf(
            "Color" to PropertyValue.Text("#00000000")
        ))

        val colorValue = bgTuple.get("Color")
        assertThat(colorValue).isNotNull

        val color = colorFromValue(colorValue, Color.White)
        // #00000000 = fully transparent black
        assertThat(color.alpha).isEqualTo(0f)
    }

    @Test
    fun `Tuple get returns null for deeply nested missing key`() {
        // Multi-level access chain: outer -> middle -> inner (missing)
        val middleTuple = PropertyValue.Tuple(mapOf(
            "FontSize" to PropertyValue.Number(14.0)
        ))
        val outerTuple = PropertyValue.Tuple(mapOf(
            "LabelStyle" to middleTuple
        ))

        val labelStyle = outerTuple.get("LabelStyle") as? PropertyValue.Tuple
        assertThat(labelStyle).isNotNull

        // Accessing a key that doesn't exist in the nested tuple
        val missing = labelStyle!!.get("Background")
        assertThat(missing).isNull()

        // And accessing a key that doesn't exist in the outer tuple
        val missingOuter = outerTuple.get("NonExistent") as? PropertyValue.Tuple
        assertThat(missingOuter).isNull()
    }
}
