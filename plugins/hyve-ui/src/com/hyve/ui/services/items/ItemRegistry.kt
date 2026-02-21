package com.hyve.ui.services.items

import com.hyve.ui.services.assets.AssetLoader
import com.intellij.openapi.diagnostic.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

/**
 * Service for loading and searching Hytale item definitions from Assets.zip.
 *
 * Items are stored at `Server/Item/Items/` directory in Assets.zip.
 * Each item has an Icon field pointing to its pre-rendered icon at `Icons/ItemsGenerated/`.
 *
 * Features:
 * - Lazy loading of item definitions from ZIP
 * - Caching of parsed items
 * - Search by name, ID, tags, or category
 * - Category grouping
 */
class ItemRegistry(private val assetLoader: AssetLoader) {

    private var cachedItems: List<ItemDefinition>? = null
    private var cachedCategories: Map<String, List<ItemDefinition>>? = null
    private val cacheMutex = Mutex()

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    /**
     * Check if the item registry is available.
     */
    val isAvailable: Boolean
        get() = assetLoader.isAvailable

    /**
     * Get all loaded items (loads on first call).
     */
    suspend fun getAllItems(): List<ItemDefinition> {
        // Check cache first (quick check without loading)
        cacheMutex.withLock {
            cachedItems?.let { return it }
        }

        // Load items outside the lock to avoid blocking
        val items = loadItemsFromZip()

        // Store in cache
        cacheMutex.withLock {
            // Double-check in case another coroutine loaded while we were working
            cachedItems?.let { return it }
            cachedItems = items
        }

        return items
    }

    /**
     * Get items grouped by category.
     */
    suspend fun getItemsByCategory(): Map<String, List<ItemDefinition>> {
        cacheMutex.withLock {
            cachedCategories?.let { return it }
        }

        val items = getAllItems()
        val categories = items.groupBy { it.category }.toSortedMap()

        cacheMutex.withLock {
            cachedCategories?.let { return it }
            cachedCategories = categories
        }

        return categories
    }

    /**
     * Get all unique categories.
     */
    suspend fun getCategories(): List<String> {
        return getItemsByCategory().keys.toList()
    }

    /**
     * Get item by ID.
     */
    suspend fun getItem(itemId: String): ItemDefinition? {
        return getAllItems().find { it.id.equals(itemId, ignoreCase = true) }
    }

    /**
     * Search items by query (matches ID, display name, category, tags).
     *
     * @param query Search query (case-insensitive)
     * @param category Optional category filter
     * @param maxResults Maximum results to return
     * @return Sorted list of matching items
     */
    suspend fun search(
        query: String,
        category: String? = null,
        maxResults: Int = 100
    ): List<ItemDefinition> {
        if (query.isBlank() && category == null) {
            return getAllItems().take(maxResults)
        }

        val items = getAllItems()
        val queryLower = query.lowercase()

        return items
            .filter { item ->
                // Category filter
                val matchesCategory = category == null || item.category.equals(category, ignoreCase = true)
                if (!matchesCategory) return@filter false

                // Query filter (if provided)
                if (query.isBlank()) return@filter true

                item.searchText.contains(queryLower)
            }
            .sortedWith(
                compareBy(
                    // Exact ID match first
                    { !it.id.equals(query, ignoreCase = true) },
                    // ID starts with query second
                    { !it.id.lowercase().startsWith(queryLower) },
                    // Display name contains query third
                    { !it.displayLabel.lowercase().contains(queryLower) },
                    // Then alphabetically by ID
                    { it.id.lowercase() }
                )
            )
            .take(maxResults)
    }

    /**
     * Load all item definitions from the ZIP file.
     */
    private suspend fun loadItemsFromZip(): List<ItemDefinition> = withContext(Dispatchers.IO) {
        if (!assetLoader.isAvailable) {
            return@withContext emptyList()
        }

        val zipPath = assetLoader.zipPath

        try {
            val file = zipPath.toFile()
            if (!file.exists() || !file.canRead()) {
                return@withContext emptyList()
            }

            val items = mutableListOf<ItemDefinition>()

            // Open our own ZipFile for enumeration (read-only, safe to have multiple open)
            ZipFile(file).use { zip ->
                val allEntries = zip.entries().toList()

                // Look for item entries - must be in Server/Item/Items/ path specifically
                val itemEntries = allEntries.filter { entry ->
                    val name = entry.name.replace('\\', '/')
                    !entry.isDirectory &&
                            name.endsWith(".json") &&
                            !name.contains("Template_") &&
                            (name.contains("Server/Item/Items/") || name.contains("server/Item/Items/"))
                }

                for (entry in itemEntries) {
                    try {
                        val jsonContent = zip.getInputStream(entry).use {
                            it.readBytes().toString(Charsets.UTF_8)
                        }

                        val itemDef = parseItemJson(entry.name, jsonContent)
                        if (itemDef != null) {
                            items.add(itemDef)
                        }
                    } catch (_: Exception) {
                        // Skip malformed files silently
                    }
                }
            }

            items.sortedBy { it.id.lowercase() }
        } catch (e: Exception) {
            LOG.warn("Failed to enumerate items from Assets.zip", e)
            emptyList()
        }
    }

    /**
     * Parse a single item JSON into an ItemDefinition.
     */
    private fun parseItemJson(path: String, jsonContent: String): ItemDefinition? {
        val jsonObject = try {
            json.parseToJsonElement(jsonContent).jsonObject
        } catch (_: Exception) {
            return null
        }

        // Extract Icon path - required for our purposes
        val iconPath = jsonObject["Icon"]?.jsonPrimitive?.contentOrNull
        if (iconPath.isNullOrBlank()) {
            return null // Skip items without icons
        }

        // Extract item ID from filename
        val normalizedPath = path.replace('\\', '/').removePrefix("assets/")
        val fileName = normalizedPath.substringAfterLast('/')
        val itemId = fileName.removeSuffix(".json")

        // Extract category from folder structure - look for Items/ in path
        val category = extractCategory(normalizedPath)

        // Extract display name from TranslationProperties
        val translationProps = jsonObject["TranslationProperties"]?.jsonObject
        val displayName = translationProps?.get("Name")?.jsonPrimitive?.contentOrNull ?: itemId

        // Extract quality
        val quality = jsonObject["Quality"]?.jsonPrimitive?.contentOrNull

        // Extract tags
        val tags = mutableSetOf<String>()
        jsonObject["Tags"]?.jsonObject?.forEach { (_, value) ->
            when (value) {
                is JsonArray -> value.forEach { element ->
                    element.jsonPrimitive.contentOrNull?.let { tags.add(it) }
                }
                is JsonPrimitive -> value.contentOrNull?.let { tags.add(it) }
                else -> {}
            }
        }

        // Extract icon properties
        val iconProperties = parseIconProperties(jsonObject["IconProperties"])

        return ItemDefinition(
            id = itemId,
            displayName = displayName,
            iconPath = iconPath,
            category = category,
            jsonPath = normalizedPath,
            tags = tags,
            quality = quality,
            iconProperties = iconProperties
        )
    }

    /**
     * Extract category from path like "Server/Item/Items/Weapons/Sword.json" -> "Weapons"
     */
    private fun extractCategory(path: String): String {
        // Find the Items/ part and get the folder after it
        val itemsIndex = path.indexOf("/Items/")
        if (itemsIndex >= 0) {
            val afterItems = path.substring(itemsIndex + 7) // Skip "/Items/"
            val parts = afterItems.split('/')
            if (parts.size > 1) {
                return parts[0] // Return first folder after Items/
            }
        }
        return "Uncategorized"
    }

    /**
     * Parse IconProperties from JSON.
     */
    private fun parseIconProperties(element: JsonElement?): IconProperties? {
        if (element == null || element !is JsonObject) return null

        val obj = element.jsonObject
        val scale = obj["Scale"]?.jsonPrimitive?.floatOrNull ?: 1.0f

        val rotation = obj["Rotation"]?.jsonArray?.let { arr ->
            if (arr.size >= 3) {
                Triple(
                    arr[0].jsonPrimitive.floatOrNull ?: 0f,
                    arr[1].jsonPrimitive.floatOrNull ?: 0f,
                    arr[2].jsonPrimitive.floatOrNull ?: 0f
                )
            } else null
        } ?: Triple(0f, 0f, 0f)

        val translation = obj["Translation"]?.jsonArray?.let { arr ->
            if (arr.size >= 2) {
                Pair(
                    arr[0].jsonPrimitive.floatOrNull ?: 0f,
                    arr[1].jsonPrimitive.floatOrNull ?: 0f
                )
            } else null
        } ?: Pair(0f, 0f)

        return IconProperties(scale, rotation, translation)
    }

    /**
     * Clear the cache to force reload.
     */
    suspend fun clearCache() {
        cacheMutex.withLock {
            cachedItems = null
            cachedCategories = null
        }
    }

    companion object {
        private val LOG = Logger.getInstance(ItemRegistry::class.java)
    }
}
