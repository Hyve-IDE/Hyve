// Copyright 2026 Hyve. All rights reserved.
package com.hyve.ui.components.colorpicker

import androidx.compose.ui.graphics.Color
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Converts RGB hex string to HSV color space.
 * Hue: 0-360 degrees. Saturation and Value: 0-1.
 * Strips Hytale opacity suffix before conversion: `#aabbcc(0.5)` → `#aabbcc`.
 * Malformed input returns (0, 0, 1) white default.
 */
fun hexToHsv(hex: String): Triple<Float, Float, Float> {
    return try {
        val (hexPart, _) = splitColorOpacity(hex)
        val cleanHex = hexPart.removePrefix("#")
        if (cleanHex.length != 6 && cleanHex.length != 8) return Triple(0f, 0f, 1f)

        val r = cleanHex.substring(0, 2).toInt(16) / 255f
        val g = cleanHex.substring(2, 4).toInt(16) / 255f
        val b = cleanHex.substring(4, 6).toInt(16) / 255f

        val max = maxOf(r, g, b)
        val min = minOf(r, g, b)
        val delta = max - min

        val value = max
        val saturation = if (max == 0f) 0f else delta / max

        val hue = when {
            delta == 0f -> 0f
            max == r -> 60f * (((g - b) / delta) % 6)
            max == g -> 60f * (((b - r) / delta) + 2)
            else -> 60f * (((r - g) / delta) + 4)
        }.let { if (it < 0) it + 360f else it }

        Triple(hue, saturation, value)
    } catch (_: Exception) {
        Triple(0f, 0f, 1f)
    }
}

/**
 * Converts HSV color space to RGB hex string.
 * Hue: 0-360 degrees. Saturation and Value: 0-1.
 * Output: 6-digit hex with # prefix (no alpha).
 */
fun hsvToHex(hue: Float, saturation: Float, value: Float): String {
    val c = value * saturation
    val x = c * (1 - abs((hue / 60f) % 2 - 1))
    val m = value - c

    val (r1, g1, b1) = when {
        hue < 60 -> Triple(c, x, 0f)
        hue < 120 -> Triple(x, c, 0f)
        hue < 180 -> Triple(0f, c, x)
        hue < 240 -> Triple(0f, x, c)
        hue < 300 -> Triple(x, 0f, c)
        else -> Triple(c, 0f, x)
    }

    val r = ((r1 + m) * 255).roundToInt().coerceIn(0, 255)
    val g = ((g1 + m) * 255).roundToInt().coerceIn(0, 255)
    val b = ((b1 + m) * 255).roundToInt().coerceIn(0, 255)

    return "#${r.toString(16).padStart(2, '0')}${g.toString(16).padStart(2, '0')}${b.toString(16).padStart(2, '0')}"
}

/**
 * Validates hex color format: #RRGGBB or #RRGGBBAA.
 */
fun isValidHexColor(hex: String): Boolean {
    return hex.matches(Regex("^#[0-9a-fA-F]{6}([0-9a-fA-F]{2})?$"))
}

/**
 * Parse hex color string to Compose Color with alpha channel.
 * Accepts: #RGB, #RRGGBB, #RRGGBBAA, and Hytale opacity syntax `#aabbcc(0.5)`.
 * Returns null on malformed input.
 */
fun parseHexColorOrNull(hex: String): Color? {
    return try {
        val trimmed = hex.trim()
        // Normalize: add # prefix if missing
        val normalized = if (!trimmed.startsWith("#")) "#$trimmed" else trimmed

        val (hexPart, parsedOpacity) = splitColorOpacity(normalized)
        val cleanHex = hexPart.removePrefix("#")

        when (cleanHex.length) {
            3 -> {
                // #RGB shorthand → expand to #RRGGBB
                val r = cleanHex[0].toString().repeat(2).toInt(16) / 255f
                val g = cleanHex[1].toString().repeat(2).toInt(16) / 255f
                val b = cleanHex[2].toString().repeat(2).toInt(16) / 255f
                Color(r, g, b, parsedOpacity ?: 1f)
            }
            6 -> {
                val r = cleanHex.substring(0, 2).toInt(16) / 255f
                val g = cleanHex.substring(2, 4).toInt(16) / 255f
                val b = cleanHex.substring(4, 6).toInt(16) / 255f
                Color(r, g, b, parsedOpacity ?: 1f)
            }
            8 -> {
                val r = cleanHex.substring(0, 2).toInt(16) / 255f
                val g = cleanHex.substring(2, 4).toInt(16) / 255f
                val b = cleanHex.substring(4, 6).toInt(16) / 255f
                val a = if (parsedOpacity != null) {
                    parsedOpacity.coerceIn(0f, 1f)
                } else {
                    cleanHex.substring(6, 8).toInt(16) / 255f
                }
                Color(r, g, b, a)
            }
            else -> null
        }
    } catch (_: Exception) {
        null
    }
}

/**
 * Parse hex color string to Compose Color with alpha channel.
 * Accepts: #RGB, #RRGGBB, #RRGGBBAA, and Hytale opacity syntax `#aabbcc(0.5)`.
 * Returns [default] on malformed input.
 */
fun parseHexColor(hex: String, default: Color = Color.Gray): Color {
    return parseHexColorOrNull(hex) ?: default
}

/**
 * Split a Hytale color string into the hex part and optional opacity.
 * `#aabbcc(0.5)` → `("#aabbcc", 0.5f)`
 * `#aabbcc` → `("#aabbcc", null)`
 */
fun splitColorOpacity(color: String): Pair<String, Float?> {
    val match = Regex("""^(#[0-9a-fA-F]{6})\(([0-9]*\.?[0-9]+)\)$""").find(color.trim())
    return if (match != null) {
        Pair(match.groupValues[1], match.groupValues[2].toFloatOrNull())
    } else {
        Pair(color.trim(), null)
    }
}

/**
 * Format a hex color with optional Hytale opacity suffix.
 * If opacity is 1.0 (or very close), returns just `#aabbcc`.
 * Otherwise returns `#aabbcc(0.5)`.
 */
fun formatColorWithOpacity(hex: String, opacity: Float): String {
    val cleanHex = hex.trim()
    return if (opacity >= 0.995f) {
        cleanHex
    } else {
        // Format to 1-2 decimal places, trimming trailing zeros
        val formatted = "%.2f".format(opacity).trimEnd('0').trimEnd('.')
        "$cleanHex($formatted)"
    }
}
