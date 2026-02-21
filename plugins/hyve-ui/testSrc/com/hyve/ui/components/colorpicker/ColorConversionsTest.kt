// Copyright 2026 Hyve. All rights reserved.
package com.hyve.ui.components.colorpicker

import androidx.compose.ui.graphics.Color
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

class ColorConversionsTest {

    @Test
    fun `hexToHsv round-trips with hsvToHex for red`() {
        val (h, s, v) = hexToHsv("#ff0000")
        assertThat(h).isEqualTo(0f)
        assertThat(s).isEqualTo(1f)
        assertThat(v).isEqualTo(1f)
        assertThat(hsvToHex(0f, 1f, 1f)).isEqualTo("#ff0000")
    }

    @Test
    fun `hexToHsv round-trips with hsvToHex for green`() {
        val (h, s, v) = hexToHsv("#00ff00")
        assertThat(h).isEqualTo(120f)
        assertThat(s).isEqualTo(1f)
        assertThat(v).isEqualTo(1f)
        assertThat(hsvToHex(120f, 1f, 1f)).isEqualTo("#00ff00")
    }

    @Test
    fun `hexToHsv round-trips with hsvToHex for blue`() {
        val (h, s, v) = hexToHsv("#0000ff")
        assertThat(h).isEqualTo(240f)
        assertThat(s).isEqualTo(1f)
        assertThat(v).isEqualTo(1f)
        assertThat(hsvToHex(240f, 1f, 1f)).isEqualTo("#0000ff")
    }

    @Test
    fun `hexToHsv round-trips with hsvToHex for white`() {
        val (h, s, v) = hexToHsv("#ffffff")
        assertThat(h).isEqualTo(0f)
        assertThat(s).isEqualTo(0f)
        assertThat(v).isEqualTo(1f)
        assertThat(hsvToHex(0f, 0f, 1f)).isEqualTo("#ffffff")
    }

    @Test
    fun `hexToHsv round-trips with hsvToHex for black`() {
        val (h, s, v) = hexToHsv("#000000")
        assertThat(h).isEqualTo(0f)
        assertThat(s).isEqualTo(0f)
        assertThat(v).isEqualTo(0f)
        assertThat(hsvToHex(0f, 0f, 0f)).isEqualTo("#000000")
    }

    @Test
    fun `isValidHexColor accepts valid hex strings`() {
        assertThat(isValidHexColor("#ff0000")).isTrue()
        assertThat(isValidHexColor("#FF0000")).isTrue()
        assertThat(isValidHexColor("#ff0000ff")).isTrue()
        assertThat(isValidHexColor("#00FF0080")).isTrue()
    }

    @Test
    fun `isValidHexColor rejects malformed input`() {
        assertThat(isValidHexColor("ff0000")).isFalse()    // no #
        assertThat(isValidHexColor("#fff")).isFalse()       // too short
        assertThat(isValidHexColor("#xyz000")).isFalse()    // invalid chars
        assertThat(isValidHexColor("")).isFalse()           // empty
        assertThat(isValidHexColor("#ff00")).isFalse()      // wrong length
    }

    @Test
    fun `parseHexColor handles 6-digit hex`() {
        val color = parseHexColor("#ff0000")
        assertThat(color.red).isEqualTo(1f)
        assertThat(color.green).isEqualTo(0f)
        assertThat(color.blue).isEqualTo(0f)
        assertThat(color.alpha).isEqualTo(1f)
    }

    @Test
    fun `parseHexColor handles 8-digit hex with alpha`() {
        val color = parseHexColor("#ff000080")
        assertThat(color.red).isEqualTo(1f)
        assertThat(color.green).isEqualTo(0f)
        assertThat(color.blue).isEqualTo(0f)
        // Alpha 0x80 = 128, 128/255 ≈ 0.502
        assertThat(color.alpha).isCloseTo(128f / 255f, org.assertj.core.data.Offset.offset(0.01f))
    }

    @Test
    fun `parseHexColor handles hex without prefix`() {
        val color = parseHexColor("ff0000")
        assertThat(color.red).isEqualTo(1f)
        assertThat(color.green).isEqualTo(0f)
        assertThat(color.blue).isEqualTo(0f)
    }

    @Test
    fun `parseHexColor returns gray for invalid input`() {
        val color = parseHexColor("invalid")
        assertThat(color).isEqualTo(Color.Gray)
    }

    @Test
    fun `hexToHsv handles 8-digit hex`() {
        val (h, s, v) = hexToHsv("#ff000080")
        assertThat(h).isEqualTo(0f)
        assertThat(s).isEqualTo(1f)
        assertThat(v).isEqualTo(1f)
    }
}
