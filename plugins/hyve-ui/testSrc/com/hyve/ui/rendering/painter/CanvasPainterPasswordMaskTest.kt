// Copyright 2026 Hyve. All rights reserved.
package com.hyve.ui.rendering.painter

import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

class CanvasPainterPasswordMaskTest {

    @Test
    fun `masks text with password character`() {
        assertThat(applyPasswordMask("hello", "*", ""))
            .isEqualTo("*****")
    }

    @Test
    fun `does not mask when passwordChar is empty`() {
        assertThat(applyPasswordMask("hello", "", ""))
            .isEqualTo("hello")
    }

    @Test
    fun `does not mask placeholder text`() {
        assertThat(applyPasswordMask("Enter password...", "*", "Enter password..."))
            .isEqualTo("Enter password...")
    }

    @Test
    fun `uses only first character of multi-char passwordChar`() {
        assertThat(applyPasswordMask("abc", "XY", ""))
            .isEqualTo("XXX")
    }

    @Test
    fun `returns empty string for empty rawText`() {
        assertThat(applyPasswordMask("", "*", ""))
            .isEqualTo("")
    }

    @Test
    fun `masks with custom character bullet`() {
        val bullet = "\u2022"
        assertThat(applyPasswordMask("test", bullet, ""))
            .isEqualTo("\u2022\u2022\u2022\u2022")
    }
}
