// Copyright 2026 Hyve. All rights reserved.
package com.hyve.ui.rendering.painter

import androidx.compose.ui.graphics.Color
import com.hyve.ui.core.domain.elements.UIElement
import com.hyve.ui.core.domain.properties.PropertyMap
import com.hyve.ui.core.domain.properties.PropertyValue
import com.hyve.ui.core.id.ElementType
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

class CanvasPainterHelpersTest {

    private fun element(vararg props: Pair<String, PropertyValue>) = UIElement(
        type = ElementType("Test"),
        id = null,
        properties = PropertyMap.of(*props)
    )

    // --- colorProperty ---

    @Test
    fun `colorProperty should read PropertyValue Color`() {
        // Arrange
        val el = element("Bg" to PropertyValue.Color("#FF0000"))

        // Act
        val result = el.colorProperty("Bg", Color.White)

        // Assert
        assertThat(result).isEqualTo(Color(1f, 0f, 0f, 1f))
    }

    @Test
    fun `colorProperty should read PropertyValue Color with alpha`() {
        // Arrange
        val el = element("Bg" to PropertyValue.Color("#00FF00", alpha = 0.5f))

        // Act
        val result = el.colorProperty("Bg", Color.White)

        // Assert
        assertThat(result).isEqualTo(Color(0f, 1f, 0f, 0.5f))
    }

    @Test
    fun `colorProperty should parse hex string from Text`() {
        // Arrange
        val el = element("Bg" to PropertyValue.Text("#0000FF"))

        // Act
        val result = el.colorProperty("Bg", Color.White)

        // Assert
        assertThat(result).isEqualTo(Color(0f, 0f, 1f))
    }

    @Test
    fun `colorProperty should return default for invalid hex in Text`() {
        // Arrange
        val el = element("Bg" to PropertyValue.Text("not-a-color"))

        // Act
        val result = el.colorProperty("Bg", Color.White)

        // Assert
        assertThat(result).isEqualTo(Color.White)
    }

    @Test
    fun `colorProperty should parse short 3-digit hex from Text`() {
        // Arrange — #FFF is shorthand for #FFFFFF (white)
        val el = element("Bg" to PropertyValue.Text("#FFF"))

        // Act
        val result = el.colorProperty("Bg", Color.Black)

        // Assert
        assertThat(result).isEqualTo(Color(1f, 1f, 1f))
    }

    @Test
    fun `colorProperty should parse RRGGBB alpha format from Text`() {
        // Arrange — Hytale #RRGGBB(float) format
        val el = element("Bg" to PropertyValue.Text("#ffffff(0.5)"))

        // Act
        val result = el.colorProperty("Bg", Color.Black)

        // Assert
        assertThat(result).isEqualTo(Color(1f, 1f, 1f, 0.5f))
    }

    @Test
    fun `colorProperty should parse RRGGBBAA hex from Text`() {
        // Arrange — 8-digit hex with alpha byte
        val el = element("Bg" to PropertyValue.Text("#FF000080"))

        // Act
        val result = el.colorProperty("Bg", Color.White)

        // Assert
        assertThat(result.red).isEqualTo(1f)
        assertThat(result.green).isEqualTo(0f)
        assertThat(result.blue).isEqualTo(0f)
        assertThat(result.alpha).isCloseTo(0.502f, org.assertj.core.data.Offset.offset(0.01f))
    }

    @Test
    fun `colorProperty should parse color from Unknown value`() {
        // Arrange — PropertyValue.Unknown with hex color text
        val el = element("Bg" to PropertyValue.Unknown("#00ff00(0.8)"))

        // Act
        val result = el.colorProperty("Bg", Color.White)

        // Assert
        assertThat(result).isEqualTo(Color(0f, 1f, 0f, 0.8f))
    }

    @Test
    fun `colorProperty should return default for wrong property type`() {
        // Arrange
        val el = element("Bg" to PropertyValue.Number(42.0))

        // Act
        val result = el.colorProperty("Bg", Color.White)

        // Assert
        assertThat(result).isEqualTo(Color.White)
    }

    @Test
    fun `colorProperty should return default for missing property`() {
        // Arrange
        val el = element()

        // Act
        val result = el.colorProperty("Bg", Color.White)

        // Assert
        assertThat(result).isEqualTo(Color.White)
    }

    // --- numberProperty ---

    @Test
    fun `numberProperty should read PropertyValue Number`() {
        // Arrange
        val el = element("Size" to PropertyValue.Number(42.0))

        // Act
        val result = el.numberProperty("Size", 0f)

        // Assert
        assertThat(result).isEqualTo(42f)
    }

    @Test
    fun `numberProperty should read decimal Number`() {
        // Arrange
        val el = element("Ratio" to PropertyValue.Number(0.75))

        // Act
        val result = el.numberProperty("Ratio", 0f)

        // Assert
        assertThat(result).isEqualTo(0.75f)
    }

    @Test
    fun `numberProperty should return default for wrong type`() {
        // Arrange
        val el = element("Size" to PropertyValue.Text("big"))

        // Act
        val result = el.numberProperty("Size", 10f)

        // Assert
        assertThat(result).isEqualTo(10f)
    }

    @Test
    fun `numberProperty should return default for missing property`() {
        // Arrange
        val el = element()

        // Act
        val result = el.numberProperty("Size", 10f)

        // Assert
        assertThat(result).isEqualTo(10f)
    }

    // --- booleanProperty ---

    @Test
    fun `booleanProperty should read true`() {
        // Arrange
        val el = element("Checked" to PropertyValue.Boolean(true))

        // Act
        val result = el.booleanProperty("Checked", false)

        // Assert
        assertThat(result).isTrue()
    }

    @Test
    fun `booleanProperty should read false`() {
        // Arrange
        val el = element("Checked" to PropertyValue.Boolean(false))

        // Act
        val result = el.booleanProperty("Checked", true)

        // Assert
        assertThat(result).isFalse()
    }

    @Test
    fun `booleanProperty should return default for wrong type`() {
        // Arrange
        val el = element("Checked" to PropertyValue.Text("yes"))

        // Act
        val result = el.booleanProperty("Checked", true)

        // Assert
        assertThat(result).isTrue()
    }

    @Test
    fun `booleanProperty should return default for missing property`() {
        // Arrange
        val el = element()

        // Act
        val result = el.booleanProperty("Checked", false)

        // Assert
        assertThat(result).isFalse()
    }

    // --- textProperty ---

    @Test
    fun `textProperty should read PropertyValue Text`() {
        // Arrange
        val el = element("Label" to PropertyValue.Text("Hello World"))

        // Act
        val result = el.textProperty("Label", "")

        // Assert
        assertThat(result).isEqualTo("Hello World")
    }

    @Test
    fun `textProperty should read empty Text`() {
        // Arrange
        val el = element("Label" to PropertyValue.Text(""))

        // Act
        val result = el.textProperty("Label", "fallback")

        // Assert
        assertThat(result).isEqualTo("")
    }

    @Test
    fun `textProperty should return default for wrong type`() {
        // Arrange
        val el = element("Label" to PropertyValue.Number(42.0))

        // Act
        val result = el.textProperty("Label", "fallback")

        // Assert
        assertThat(result).isEqualTo("fallback")
    }

    @Test
    fun `textProperty should return default for missing property`() {
        // Arrange
        val el = element()

        // Act
        val result = el.textProperty("Label", "default")

        // Assert
        assertThat(result).isEqualTo("default")
    }

    // --- OutlineSize / OutlineColor (GAP-C06) ---

    @Test
    fun `numberProperty should read OutlineSize`() {
        val el = element("OutlineSize" to PropertyValue.Number(2.0))
        assertThat(el.numberProperty("OutlineSize", 0f)).isEqualTo(2f)
    }

    @Test
    fun `colorProperty should read OutlineColor`() {
        val el = element("OutlineColor" to PropertyValue.Color("#000000"))
        assertThat(el.colorProperty("OutlineColor", Color.White)).isEqualTo(Color.Black)
    }

    @Test
    fun `numberProperty returns default for missing OutlineSize`() {
        val el = element()
        assertThat(el.numberProperty("OutlineSize", 0f)).isEqualTo(0f)
    }

    // --- Image Tint (GAP-C07) ---

    @Test
    fun `colorProperty should read Tint color`() {
        val el = element("Tint" to PropertyValue.Text("#ff6b00"))
        val result = el.colorProperty("Tint", Color.Transparent)
        assertThat(result.red).isCloseTo(1f, org.assertj.core.data.Offset.offset(0.01f))
        assertThat(result.green).isCloseTo(0.42f, org.assertj.core.data.Offset.offset(0.02f))
        assertThat(result.blue).isCloseTo(0f, org.assertj.core.data.Offset.offset(0.01f))
    }

    // --- maskTexturePath ---

    @Test
    fun `maskTexturePath should read PropertyValue Text with png path`() {
        val el = element("MaskTexturePath" to PropertyValue.Text("PartMask.png"))
        assertThat(el.maskTexturePath()).isEqualTo("PartMask.png")
    }

    @Test
    fun `maskTexturePath should read PropertyValue ImagePath`() {
        val el = element("MaskTexturePath" to PropertyValue.ImagePath("TextGradient.png"))
        assertThat(el.maskTexturePath()).isEqualTo("TextGradient.png")
    }

    @Test
    fun `maskTexturePath should return null for missing property`() {
        val el = element()
        assertThat(el.maskTexturePath()).isNull()
    }

    @Test
    fun `maskTexturePath should return null for non-path Text`() {
        val el = element("MaskTexturePath" to PropertyValue.Text("#FF0000"))
        assertThat(el.maskTexturePath()).isNull()
    }

    @Test
    fun `maskTexturePath should read PropertyValue Unknown with png extension`() {
        val el = element("MaskTexturePath" to PropertyValue.Unknown("ColorOptionMask.png"))
        assertThat(el.maskTexturePath()).isEqualTo("ColorOptionMask.png")
    }

    @Test
    fun `maskTexturePath should return null for Unknown with non-image raw string`() {
        val el = element("MaskTexturePath" to PropertyValue.Unknown("#FF0000"))
        assertThat(el.maskTexturePath()).isNull()
    }

    @Test
    fun `maskTexturePath should read Unknown with jpg extension`() {
        val el = element("MaskTexturePath" to PropertyValue.Unknown("mask.jpg"))
        assertThat(el.maskTexturePath()).isEqualTo("mask.jpg")
    }

    @Test
    fun `maskTexturePath should return null for blank Text`() {
        val el = element("MaskTexturePath" to PropertyValue.Text(""))
        assertThat(el.maskTexturePath()).isNull()
    }

    // --- numberPropertyOrAlias ---

    @Test
    fun `numberPropertyOrAlias reads primary name`() {
        val el = element("MinValue" to PropertyValue.Number(5.0))
        assertThat(el.numberPropertyOrAlias("MinValue", "Min", 0f)).isEqualTo(5f)
    }

    @Test
    fun `numberPropertyOrAlias falls back to alias when primary missing`() {
        val el = element("Min" to PropertyValue.Number(3.0))
        assertThat(el.numberPropertyOrAlias("MinValue", "Min", 0f)).isEqualTo(3f)
    }

    @Test
    fun `numberPropertyOrAlias prefers primary over alias when both present`() {
        val el = element(
            "MinValue" to PropertyValue.Number(10.0),
            "Min" to PropertyValue.Number(5.0)
        )
        assertThat(el.numberPropertyOrAlias("MinValue", "Min", 0f)).isEqualTo(10f)
    }

    @Test
    fun `numberPropertyOrAlias returns default when both missing`() {
        val el = element()
        assertThat(el.numberPropertyOrAlias("MinValue", "Min", 99f)).isEqualTo(99f)
    }

    @Test
    fun `numberPropertyOrAlias returns default when primary is wrong type`() {
        val el = element("MinValue" to PropertyValue.Text("five"))
        assertThat(el.numberPropertyOrAlias("MinValue", "Min", 0f)).isEqualTo(0f)
    }
}
