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
 * Tests for property reading on 3D preview element types.
 *
 * These verify that the property helper functions (colorProperty, numberProperty,
 * textProperty) correctly extract values from 3D element types used by the new
 * renderers in CanvasPainter (spec 14).
 */
class ThreeDPreviewHelpersTest {

    private fun element(
        type: String,
        vararg props: Pair<String, PropertyValue>
    ) = UIElement(
        type = ElementType(type),
        id = null,
        properties = PropertyMap.of(*props)
    )

    // --- ItemPreviewComponent property reading ---

    @Test
    fun `should read PreviewItemId from ItemPreviewComponent`() {
        // Arrange
        val el = element("ItemPreviewComponent", "PreviewItemId" to PropertyValue.Text("Sword_Iron"))

        // Act
        val result = el.textProperty("PreviewItemId", "")

        // Assert
        assertThat(result).isEqualTo("Sword_Iron")
    }

    @Test
    fun `should return empty default when PreviewItemId is missing`() {
        // Arrange
        val el = element("ItemPreviewComponent")

        // Act
        val result = el.textProperty("PreviewItemId", "")

        // Assert
        assertThat(result).isEqualTo("")
    }

    @Test
    fun `should return empty default when PreviewItemId is wrong type`() {
        // Arrange
        val el = element("ItemPreviewComponent", "PreviewItemId" to PropertyValue.Number(42.0))

        // Act
        val result = el.textProperty("PreviewItemId", "")

        // Assert
        assertThat(result).isEqualTo("")
    }

    // --- BlockSelector property reading ---

    @Test
    fun `should read Block from BlockSelector`() {
        // Arrange
        val el = element("BlockSelector", "Block" to PropertyValue.Text("stone"))

        // Act
        val result = el.textProperty("Block", "")

        // Assert
        assertThat(result).isEqualTo("stone")
    }

    @Test
    fun `should return empty default when Block is missing`() {
        // Arrange
        val el = element("BlockSelector")

        // Act
        val result = el.textProperty("Block", "")

        // Assert
        assertThat(result).isEqualTo("")
    }

    // --- ItemGrid property reading ---

    @Test
    fun `should read SlotsPerRow from ItemGrid`() {
        // Arrange
        val el = element("ItemGrid", "SlotsPerRow" to PropertyValue.Number(6.0))

        // Act
        val result = el.numberProperty("SlotsPerRow", 9f)

        // Assert
        assertThat(result).isEqualTo(6f)
    }

    @Test
    fun `should return default SlotsPerRow when missing`() {
        // Arrange
        val el = element("ItemGrid")

        // Act
        val result = el.numberProperty("SlotsPerRow", 9f)

        // Assert
        assertThat(result).isEqualTo(9f)
    }

    @Test
    fun `should read SlotSize from ItemGrid`() {
        // Arrange
        val el = element("ItemGrid", "SlotSize" to PropertyValue.Number(32.0))

        // Act
        val result = el.numberProperty("SlotSize", 74f)

        // Assert
        assertThat(result).isEqualTo(32f)
    }

    @Test
    fun `should return default SlotSize when missing`() {
        // Arrange
        val el = element("ItemGrid")

        // Act
        val result = el.numberProperty("SlotSize", 74f)

        // Assert
        assertThat(result).isEqualTo(74f)
    }

    @Test
    fun `should read SlotSpacing from ItemGrid`() {
        // Arrange
        val el = element("ItemGrid", "SlotSpacing" to PropertyValue.Number(8.0))

        // Act
        val result = el.numberProperty("SlotSpacing", 4f)

        // Assert
        assertThat(result).isEqualTo(8f)
    }

    @Test
    fun `should return default SlotSpacing when missing`() {
        // Arrange
        val el = element("ItemGrid")

        // Act
        val result = el.numberProperty("SlotSpacing", 4f)

        // Assert
        assertThat(result).isEqualTo(4f)
    }

    @Test
    fun `should read SlotBorderColor from ItemGrid`() {
        // Arrange
        val el = element("ItemGrid", "SlotBorderColor" to PropertyValue.Color("#FF0000"))

        // Act
        val result = el.colorProperty("SlotBorderColor", Color(0xFF4A4A5A))

        // Assert
        assertThat(result).isEqualTo(Color(1f, 0f, 0f, 1f))
    }

    @Test
    fun `should return default SlotBorderColor when missing`() {
        // Arrange
        val default = Color(0xFF4A4A5A)
        val el = element("ItemGrid")

        // Act
        val result = el.colorProperty("SlotBorderColor", default)

        // Assert
        assertThat(result).isEqualTo(default)
    }

    @Test
    fun `should read SlotBackground as color from ItemGrid`() {
        // Arrange
        val el = element("ItemGrid", "SlotBackground" to PropertyValue.Color("#22223A"))

        // Act
        val result = el.colorProperty("SlotBackground", Color.Black)

        // Assert
        assertThat(result.red).isCloseTo(0x22 / 255f, org.assertj.core.data.Offset.offset(0.01f))
        assertThat(result.green).isCloseTo(0x22 / 255f, org.assertj.core.data.Offset.offset(0.01f))
        assertThat(result.blue).isCloseTo(0x3A / 255f, org.assertj.core.data.Offset.offset(0.01f))
    }

    @Test
    fun `should return default SlotBackground when missing`() {
        // Arrange
        val default = Color(0xFF22223A)
        val el = element("ItemGrid")

        // Act
        val result = el.colorProperty("SlotBackground", default)

        // Assert
        assertThat(result).isEqualTo(default)
    }

    // --- SlotsPerRow coercion ---

    @Test
    fun `SlotsPerRow should coerce to at least 1 when used as Int`() {
        // Arrange
        val el = element("ItemGrid", "SlotsPerRow" to PropertyValue.Number(0.0))

        // Act
        val result = el.numberProperty("SlotsPerRow", 9f).toInt().coerceAtLeast(1)

        // Assert
        assertThat(result).isEqualTo(1)
    }

    @Test
    fun `SlotsPerRow should coerce negative to 1`() {
        // Arrange
        val el = element("ItemGrid", "SlotsPerRow" to PropertyValue.Number(-3.0))

        // Act
        val result = el.numberProperty("SlotsPerRow", 9f).toInt().coerceAtLeast(1)

        // Assert
        assertThat(result).isEqualTo(1)
    }
}
