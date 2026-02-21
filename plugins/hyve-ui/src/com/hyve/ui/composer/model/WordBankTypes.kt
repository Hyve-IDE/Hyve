// Copyright 2026 Hyve. All rights reserved.
package com.hyve.ui.composer.model

/**
 * A single item in the Word Bank sidebar.
 *
 * Represents a reusable value (variable, style, import, localization key, or asset)
 * that can be dragged into property slots. Each item has a unique [id] so duplicates
 * by name are allowed.
 *
 * @param id Unique identifier for this item instance
 * @param name Display name including prefix (e.g. "@AccentColor", "%button.submit", "$Common.@Style")
 * @param type The property type this item provides
 * @param kind Which section this item belongs to
 * @param value Optional preview value (e.g. "#ff6b00", "Submit")
 * @param source Optional originating file (e.g. "Common.ui") or "local" for locally defined items
 */
data class WordBankItem(
    val id: String,
    val name: String,
    val type: ComposerPropertyType,
    val kind: WordBankKind,
    val value: String? = null,
    val source: String? = null,
)

/**
 * The five kinds of Word Bank items, in display order.
 *
 * Each kind defines its section label and the badge character shown
 * next to items of that kind.
 */
enum class WordBankKind(val label: String, val badge: String) {
    VARIABLE("Variables", "@"),
    STYLE("Styles", "\u25C6"),
    IMPORT("Imports", "$"),
    LOCALIZATION("Localization", "%"),
    ASSET("Assets", "\u2B1A"),
}
