// Copyright 2026 Hyve. All rights reserved.
package com.hyve.ui.canvas

import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

class ScreenshotStateTest {

    private fun createState() = CanvasState()

    // --- showScreenshot ---

    @Test
    fun `screenshot should be hidden by default`() {
        val state = createState()
        assertThat(state.showScreenshot.value).isFalse()
    }

    @Test
    fun `toggleScreenshot should show when hidden`() {
        val state = createState()
        state.toggleScreenshot()
        assertThat(state.showScreenshot.value).isTrue()
    }

    @Test
    fun `toggleScreenshot twice should hide again`() {
        val state = createState()
        state.toggleScreenshot()
        state.toggleScreenshot()
        assertThat(state.showScreenshot.value).isFalse()
    }

    @Test
    fun `setShowScreenshot should set explicitly`() {
        val state = createState()
        state.setShowScreenshot(true)
        assertThat(state.showScreenshot.value).isTrue()
        state.setShowScreenshot(false)
        assertThat(state.showScreenshot.value).isFalse()
    }

    // --- screenshotOpacity ---

    @Test
    fun `default opacity should be 0_3`() {
        val state = createState()
        assertThat(state.screenshotOpacity.value).isEqualTo(0.3f)
    }

    @Test
    fun `setScreenshotOpacity should update value`() {
        val state = createState()
        state.setScreenshotOpacity(0.75f)
        assertThat(state.screenshotOpacity.value).isEqualTo(0.75f)
    }

    @Test
    fun `setScreenshotOpacity should clamp below zero`() {
        val state = createState()
        state.setScreenshotOpacity(-0.5f)
        assertThat(state.screenshotOpacity.value).isEqualTo(0f)
    }

    @Test
    fun `setScreenshotOpacity should clamp above one`() {
        val state = createState()
        state.setScreenshotOpacity(1.5f)
        assertThat(state.screenshotOpacity.value).isEqualTo(1f)
    }

    @Test
    fun `setScreenshotOpacity to zero should be valid`() {
        val state = createState()
        state.setScreenshotOpacity(0f)
        assertThat(state.screenshotOpacity.value).isEqualTo(0f)
    }

    @Test
    fun `setScreenshotOpacity to one should be valid`() {
        val state = createState()
        state.setScreenshotOpacity(1f)
        assertThat(state.screenshotOpacity.value).isEqualTo(1f)
    }

    // --- screenshotMode ---

    @Test
    fun `default mode should be NO_HUD`() {
        val state = createState()
        assertThat(state.screenshotMode.value).isEqualTo(ScreenshotMode.NO_HUD)
    }

    @Test
    fun `setScreenshotMode should update mode`() {
        val state = createState()
        state.setScreenshotMode(ScreenshotMode.HUD)
        assertThat(state.screenshotMode.value).isEqualTo(ScreenshotMode.HUD)
    }

    @Test
    fun `cycleScreenshotMode from NO_HUD should go to HUD`() {
        val state = createState()
        state.cycleScreenshotMode()
        assertThat(state.screenshotMode.value).isEqualTo(ScreenshotMode.HUD)
    }

    @Test
    fun `cycleScreenshotMode from HUD should wrap to NO_HUD`() {
        val state = createState()
        state.setScreenshotMode(ScreenshotMode.HUD)
        state.cycleScreenshotMode()
        assertThat(state.screenshotMode.value).isEqualTo(ScreenshotMode.NO_HUD)
    }

    @Test
    fun `cycling through all modes returns to default`() {
        val state = createState()
        val initialMode = state.screenshotMode.value
        for (i in ScreenshotMode.entries.indices) {
            state.cycleScreenshotMode()
        }
        assertThat(state.screenshotMode.value).isEqualTo(initialMode)
    }

    // --- Integration: toggle + mode interaction ---

    @Test
    fun `toggling visibility should not change mode`() {
        val state = createState()
        state.setScreenshotMode(ScreenshotMode.HUD)
        state.toggleScreenshot()
        assertThat(state.screenshotMode.value).isEqualTo(ScreenshotMode.HUD)
    }

    @Test
    fun `changing mode should not affect visibility`() {
        val state = createState()
        state.setShowScreenshot(true)
        state.cycleScreenshotMode()
        assertThat(state.showScreenshot.value).isTrue()
    }

    @Test
    fun `changing mode should not affect opacity`() {
        val state = createState()
        state.setScreenshotOpacity(0.6f)
        state.cycleScreenshotMode()
        assertThat(state.screenshotOpacity.value).isEqualTo(0.6f)
    }
}
