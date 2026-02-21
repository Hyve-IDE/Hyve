package com.hyve.ui.services.items

/**
 * Represents a Hytale item definition parsed from JSON.
 *
 * Items are stored at `Server/Item/Items/` directory in Assets.zip.
 * Each item has an Icon field pointing to its pre-rendered icon.
 */
data class ItemDefinition(
    /** Unique item ID derived from filename (e.g., "Sword_Iron") */
    val id: String,

    /** Display name from TranslationProperties or derived from ID */
    val displayName: String,

    /** Path to the pre-rendered icon (e.g., "Icons/ItemsGenerated/Sword_Iron.png") */
    val iconPath: String,

    /** Category derived from folder structure (e.g., "Weapons", "Tools", "Fish") */
    val category: String,

    /** Full path within the ZIP for reference */
    val jsonPath: String,

    /** Tags from the item JSON (Type tags like "Weapon", "Tool", etc.) */
    val tags: Set<String> = emptySet(),

    /** Item quality (Common, Uncommon, Rare, Epic, Legendary, Developer) */
    val quality: String? = null,

    /** Icon rendering properties for 3D preview positioning */
    val iconProperties: IconProperties? = null
) {
    /**
     * Searchable text combining ID, display name, category, and tags.
     * Used for fuzzy searching in the item picker.
     */
    val searchText: String by lazy {
        buildString {
            append(id.lowercase())
            append(" ")
            append(displayName.lowercase())
            append(" ")
            append(category.lowercase())
            append(" ")
            tags.forEach { append(it.lowercase()).append(" ") }
        }
    }

    /**
     * Human-readable display name (falls back to formatted ID if no translation).
     */
    val displayLabel: String
        get() = if (displayName.startsWith("server.")) {
            // No translation available, format ID nicely
            id.replace('_', ' ')
        } else {
            displayName
        }
}

/**
 * Icon rendering properties from the item JSON.
 * Used by the game for positioning items in the 3D preview.
 */
data class IconProperties(
    /** Scale factor for the icon */
    val scale: Float = 1.0f,

    /** Rotation in degrees [X, Y, Z] */
    val rotation: Triple<Float, Float, Float> = Triple(0f, 0f, 0f),

    /** Translation offset [X, Y] */
    val translation: Pair<Float, Float> = Pair(0f, 0f)
)

/**
 * Result of loading item definitions.
 */
sealed class ItemLoadResult {
    data class Success(val items: List<ItemDefinition>) : ItemLoadResult()
    data class Error(val message: String, val cause: Throwable? = null) : ItemLoadResult()
    data object NotAvailable : ItemLoadResult()
}
