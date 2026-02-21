// Copyright 2026 Hyve. All rights reserved.
package com.hyve.ui.services.localization

/**
 * Represents a localization key from a .lang file.
 *
 * Per spec 20-LOCALIZATION-BROWSER.md FR-003.
 *
 * @property fullKey The complete key path (e.g., "client.ui.inventory.title")
 * @property translations Map of language code to translated value
 * @property sourceFile The .lang file where this key is defined
 * @property sourceMod Mod name if from a mod, null for vanilla
 */
data class LocalizationKey(
    val fullKey: String,
    val translations: Map<String, String>,
    val sourceFile: String,
    val sourceMod: String?
) {
    /**
     * Get the key's namespace/prefix (everything before the last dot).
     * For "client.ui.inventory.title", returns "client.ui.inventory"
     */
    val prefix: String
        get() = fullKey.substringBeforeLast('.', "")

    /**
     * Get the key's simple name (after the last dot).
     * For "client.ui.inventory.title", returns "title"
     */
    val simpleName: String
        get() = fullKey.substringAfterLast('.')

    /**
     * Get the translation for a specific language, or null if missing.
     */
    fun getTranslation(languageCode: String): String? = translations[languageCode]

    /**
     * Check if this key has a translation for the given language.
     */
    fun hasTranslation(languageCode: String): Boolean = translations.containsKey(languageCode)

    /**
     * Get all available language codes for this key.
     */
    val availableLanguages: Set<String>
        get() = translations.keys

    /**
     * Check if this key is from vanilla (not a mod).
     */
    val isVanilla: Boolean
        get() = sourceMod == null
}

/**
 * Represents a node in the localization key tree.
 *
 * Per spec 20-LOCALIZATION-BROWSER.md FR-003.
 * Keys are grouped by prefix hierarchy.
 *
 * @property segment The current segment name (e.g., "inventory")
 * @property fullPath The full path to this node (e.g., "client.ui.inventory")
 * @property children Child tree nodes
 * @property keys Leaf keys at this level
 */
data class LocalizationTreeNode(
    val segment: String,
    val fullPath: String,
    val children: List<LocalizationTreeNode>,
    val keys: List<LocalizationKey>
) {
    /**
     * Get total key count including all descendants.
     */
    val totalKeyCount: Int
        get() = keys.size + children.sumOf { it.totalKeyCount }

    /**
     * Check if this node or any descendants have keys.
     */
    val hasKeys: Boolean
        get() = keys.isNotEmpty() || children.any { it.hasKeys }
}

/**
 * Source type for a localization key.
 * Used to show different icons/colors in the browser.
 */
enum class LocalizationSource {
    /** Key from vanilla Hytale .lang files */
    VANILLA,
    /** Key from another mod's .lang files */
    OTHER_MOD,
    /** Key from the current mod being edited */
    CURRENT_MOD
}
