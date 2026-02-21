// Copyright 2026 Hyve. All rights reserved.
package com.hyve.ui.composer.propertyform

import androidx.compose.ui.graphics.Color
import com.hyve.common.compose.HyveExtendedColors
import com.hyve.ui.composer.model.ComposerPropertyType
import com.hyve.ui.composer.model.FillMode

/**
 * Type dot color for each property type, resolved from theme colors.
 * Falls back to dark-theme defaults when no [colors] are provided (e.g. in tests).
 */
fun typeColor(type: ComposerPropertyType, colors: HyveExtendedColors = HyveExtendedColors()): Color = when (type) {
    ComposerPropertyType.TEXT -> colors.warning
    ComposerPropertyType.NUMBER -> colors.success
    ComposerPropertyType.COLOR -> colors.kindColor
    ComposerPropertyType.BOOLEAN -> colors.error
    ComposerPropertyType.ANCHOR -> colors.kindAnchor
    ComposerPropertyType.STYLE -> colors.info
    ComposerPropertyType.IMAGE -> colors.kindImport
    ComposerPropertyType.FONT -> colors.kindImport
    ComposerPropertyType.TUPLE -> colors.kindTuple
    ComposerPropertyType.PERCENT -> colors.success
}

/**
 * Fill mode badge icon character.
 */
fun fillModeIcon(mode: FillMode): String = when (mode) {
    FillMode.LITERAL -> "\u270E"     // ✎
    FillMode.VARIABLE -> "@"
    FillMode.LOCALIZATION -> "%"
    FillMode.EXPRESSION -> "\u0192"  // ƒ
    FillMode.IMPORT -> "$"
    FillMode.EMPTY -> ""
}

/**
 * Fill mode badge background tint color, resolved from theme colors.
 * Falls back to dark-theme defaults when no [colors] are provided (e.g. in tests).
 */
fun fillModeBadgeColor(mode: FillMode, colors: HyveExtendedColors = HyveExtendedColors()): Color = when (mode) {
    FillMode.LITERAL -> colors.textDisabled
    FillMode.VARIABLE -> colors.kindVariable
    FillMode.LOCALIZATION -> colors.kindLocalization
    FillMode.EXPRESSION -> colors.success
    FillMode.IMPORT -> colors.kindImport
    FillMode.EMPTY -> Color.Transparent
}

/**
 * Type-specific placeholder text for empty slots (FR-3).
 */
fun emptyPlaceholder(type: ComposerPropertyType): String = when (type) {
    ComposerPropertyType.TEXT -> "________"
    ComposerPropertyType.NUMBER -> "___"
    ComposerPropertyType.COLOR -> "#______"
    ComposerPropertyType.BOOLEAN -> "true/false"
    ComposerPropertyType.ANCHOR -> "________"
    ComposerPropertyType.STYLE -> "@_________"
    ComposerPropertyType.IMAGE -> "________.png"
    ComposerPropertyType.FONT -> "________.ttf"
    ComposerPropertyType.TUPLE -> "(___)"
    ComposerPropertyType.PERCENT -> "___%"
}

/**
 * Default value when activating a literal from empty (FR-4).
 */
fun defaultLiteralValue(type: ComposerPropertyType): String = when (type) {
    ComposerPropertyType.BOOLEAN -> "true"
    ComposerPropertyType.COLOR -> "#ffffff"
    ComposerPropertyType.NUMBER -> "0"
    ComposerPropertyType.PERCENT -> "100"
    else -> ""
}
