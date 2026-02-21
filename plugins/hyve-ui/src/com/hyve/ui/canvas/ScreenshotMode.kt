// Copyright 2026 Hyve. All rights reserved.
package com.hyve.ui.canvas

/**
 * Screenshot reference overlay modes.
 *
 * Controls which bundled Hytale screenshot is shown as a background
 * reference image on the canvas — either with the vanilla HUD visible
 * or without it (clean scene).
 */
enum class ScreenshotMode(val label: String, val resourcePath: String) {
    /** Screenshot with vanilla Hytale HUD elements visible. */
    HUD("With HUD", "/assets/screenshots/hytale_hud.png"),

    /** Clean screenshot with HUD hidden. */
    NO_HUD("No HUD", "/assets/screenshots/hytale_no_hud.png");

    /** Cycle to the next mode. */
    fun next(): ScreenshotMode {
        val values = entries
        return values[(ordinal + 1) % values.size]
    }
}
