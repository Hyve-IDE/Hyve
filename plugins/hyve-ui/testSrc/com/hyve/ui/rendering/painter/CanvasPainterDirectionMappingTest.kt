// Copyright 2026 Hyve. All rights reserved.
package com.hyve.ui.rendering.painter

import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

class CanvasPainterDirectionMappingTest {

    @Test
    fun `explicit FillDirection takes precedence`() {
        assertThat(resolveProgressBarFillDirection("RightToLeft", "Start", "Vertical"))
            .isEqualTo("RightToLeft")
    }

    @Test
    fun `vertical + Start yields TopToBottom`() {
        assertThat(resolveProgressBarFillDirection("", "Start", "Vertical"))
            .isEqualTo("TopToBottom")
    }

    @Test
    fun `vertical + End yields BottomToTop`() {
        assertThat(resolveProgressBarFillDirection("", "End", "Vertical"))
            .isEqualTo("BottomToTop")
    }

    @Test
    fun `vertical + no direction yields BottomToTop default`() {
        assertThat(resolveProgressBarFillDirection("", "", "Vertical"))
            .isEqualTo("BottomToTop")
    }

    @Test
    fun `horizontal + Start yields LeftToRight`() {
        assertThat(resolveProgressBarFillDirection("", "Start", ""))
            .isEqualTo("LeftToRight")
    }

    @Test
    fun `horizontal + End yields RightToLeft`() {
        assertThat(resolveProgressBarFillDirection("", "End", ""))
            .isEqualTo("RightToLeft")
    }

    @Test
    fun `no direction no alignment yields LeftToRight default`() {
        assertThat(resolveProgressBarFillDirection("", "", ""))
            .isEqualTo("LeftToRight")
    }

    @Test
    fun `vertical alignment is case-insensitive`() {
        assertThat(resolveProgressBarFillDirection("", "Start", "vertical"))
            .isEqualTo("TopToBottom")
        assertThat(resolveProgressBarFillDirection("", "Start", "VERTICAL"))
            .isEqualTo("TopToBottom")
    }
}
