// Copyright 2026 Hyve. All rights reserved.
package com.hyve.ui.composer.model

/**
 * Which popover dialog is currently open.
 *
 * Only one popover can be open at a time. Used by [com.hyve.ui.composer.wordbank.WordBankState]
 * to track open/close state.
 */
enum class PopoverKind {
    ADD_VARIABLE,
    ADD_STYLE,
    ADD_IMPORT,
}

/**
 * Supported style types for the Add Style popover (spec 06 FR-3).
 *
 * Each style type determines which property slots are available per visual state.
 * The mapping from type to slots lives in [StyleTypeRegistry].
 */
enum class StyleType(val displayName: String) {
    TEXT_BUTTON_STYLE("TextButtonStyle"),
    LABEL_STYLE("LabelStyle"),
    TEXT_FIELD_STYLE("TextFieldStyle"),
    CHECK_BOX_STYLE("CheckBoxStyle"),
    SCROLLBAR_STYLE("ScrollbarStyle"),
    SLIDER_STYLE("SliderStyle"),
    DROPDOWN_BOX_STYLE("DropdownBoxStyle"),
    TAB_PANEL_STYLE("TabPanelStyle"),
    PROGRESS_BAR_STYLE("ProgressBarStyle"),
    TOOLTIP_STYLE("TooltipStyle"),
}

/**
 * The 7 variable types available in the Add Variable type dropdown (spec 06 FR-2).
 */
val VARIABLE_TYPE_OPTIONS: List<ComposerPropertyType> = listOf(
    ComposerPropertyType.TEXT,
    ComposerPropertyType.NUMBER,
    ComposerPropertyType.COLOR,
    ComposerPropertyType.BOOLEAN,
    ComposerPropertyType.IMAGE,
    ComposerPropertyType.FONT,
    ComposerPropertyType.PERCENT,
)

/**
 * Returns the placeholder text for the default value field based on the selected type.
 */
fun defaultPlaceholder(type: ComposerPropertyType): String = when (type) {
    ComposerPropertyType.TEXT -> "\"Hello World\""
    ComposerPropertyType.NUMBER -> "0"
    ComposerPropertyType.COLOR -> "#000000"
    ComposerPropertyType.BOOLEAN -> "true"
    ComposerPropertyType.IMAGE -> "Texture.png"
    ComposerPropertyType.FONT -> "Font.ttf"
    ComposerPropertyType.PERCENT -> "100"
    else -> ""
}

/**
 * A `.ui` file discovered from the project that has importable exports (spec 06 FR-4).
 *
 * @param name The file name without extension (e.g. "Common")
 * @param fileName The full file name (e.g. "Common.ui")
 * @param exports The named style definitions in this file
 */
data class ImportableFile(
    val name: String,
    val fileName: String,
    val exports: List<ImportableExport>,
)

/**
 * A single named export from a `.ui` file.
 *
 * @param name The export name without `@` prefix (e.g. "DefaultButtonStyle")
 * @param type The inferred property type for this export
 */
data class ImportableExport(
    val name: String,
    val type: ComposerPropertyType,
)
