// Copyright 2026 Hyve. All rights reserved.
package com.hyve.ui.canvas

import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

class ScreenshotModeTest {

    // --- ScreenshotMode enum values ---

    @Test
    fun `should have exactly two modes`() {
        assertThat(ScreenshotMode.entries).hasSize(2)
    }

    @Test
    fun `HUD mode should have correct label`() {
        assertThat(ScreenshotMode.HUD.label).isEqualTo("With HUD")
    }

    @Test
    fun `NO_HUD mode should have correct label`() {
        assertThat(ScreenshotMode.NO_HUD.label).isEqualTo("No HUD")
    }

    @Test
    fun `HUD mode should reference hud screenshot resource`() {
        assertThat(ScreenshotMode.HUD.resourcePath).isEqualTo("/assets/screenshots/hytale_hud.png")
    }

    @Test
    fun `NO_HUD mode should reference no-hud screenshot resource`() {
        assertThat(ScreenshotMode.NO_HUD.resourcePath).isEqualTo("/assets/screenshots/hytale_no_hud.png")
    }

    // --- next() cycling ---

    @Test
    fun `next from HUD should be NO_HUD`() {
        assertThat(ScreenshotMode.HUD.next()).isEqualTo(ScreenshotMode.NO_HUD)
    }

    @Test
    fun `next from NO_HUD should wrap to HUD`() {
        assertThat(ScreenshotMode.NO_HUD.next()).isEqualTo(ScreenshotMode.HUD)
    }

    @Test
    fun `cycling through all modes returns to start`() {
        var mode = ScreenshotMode.HUD
        for (i in ScreenshotMode.entries.indices) {
            mode = mode.next()
        }
        assertThat(mode).isEqualTo(ScreenshotMode.HUD)
    }

    // --- Resource path uniqueness ---

    @Test
    fun `each mode should have a unique resource path`() {
        val paths = ScreenshotMode.entries.map { it.resourcePath }
        assertThat(paths).doesNotHaveDuplicates()
    }
}
