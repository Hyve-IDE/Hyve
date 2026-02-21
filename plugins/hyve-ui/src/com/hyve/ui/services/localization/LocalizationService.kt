// Copyright 2026 Hyve. All rights reserved.
package com.hyve.ui.services.localization

import com.hyve.ui.settings.AssetSettings
import com.hyve.ui.settings.AssetSettingsProvider
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import kotlin.io.path.exists
import kotlin.io.path.extension
import kotlin.io.path.isDirectory
import kotlin.io.path.name

/**
 * Service for loading and managing localization keys from .lang files.
 *
 * Per spec 20-LOCALIZATION-BROWSER.md and UI_EDITOR_KNOWLEDGE.md:
 * - Loads keys from Server/Languages/{lang-code}/ (all .lang files)
 * - Supports both vanilla and mod .lang files
 * - Caches loaded data for performance
 * - Provides hierarchical tree view of keys
 */
class LocalizationService(
    private val assetSettings: AssetSettingsProvider = AssetSettings
) {

    /** All loaded localization keys, keyed by full key name */
    private val allKeys = ConcurrentHashMap<String, LocalizationKey>()

    /** Cache of loaded .lang files by path */
    private val loadedFiles = ConcurrentHashMap<Path, Set<String>>()

    /** Available language codes discovered from files */
    private val availableLanguages = mutableSetOf<String>()

    /** Whether the service has been initialized */
    @Volatile
    private var initialized = false

    /**
     * Initialize the service by loading all .lang files from configured paths.
     *
     * Per spec FR-003: Keys from vanilla and mod .lang files.
     */
    fun initialize() {
        if (initialized) return
        synchronized(this) {
            if (initialized) return

            // Load vanilla .lang files from Client folder if configured
            val clientFolder = assetSettings.getClientFolderPath()
            if (clientFolder != null && clientFolder.exists()) {
                loadLanguagesFromFolder(clientFolder, sourceMod = null)
            }

            initialized = true
        }
    }

    /**
     * Load localization keys from a project's Server/Languages folder.
     *
     * @param projectRoot Root of the project containing Server/Languages
     * @param modName Name of the mod (for source tracking)
     */
    fun loadFromProject(projectRoot: Path, modName: String? = null) {
        val languagesFolder = projectRoot.resolve("Server/Languages")
        if (languagesFolder.exists() && languagesFolder.isDirectory()) {
            loadLanguagesFromFolder(languagesFolder, modName)
        }
    }

    /**
     * Load localization keys from a Languages folder.
     *
     * Directory structure:
     * Languages/
     *   en-US/
     *     *.lang files
     *   fr-FR/
     *     *.lang files
     *   fallback.lang (optional)
     */
    private fun loadLanguagesFromFolder(languagesFolder: Path, sourceMod: String?) {
        if (!languagesFolder.exists() || !languagesFolder.isDirectory()) return

        // Find all language directories
        Files.list(languagesFolder).use { stream ->
            stream.filter { it.isDirectory() && isLanguageCode(it.name) }
                .forEach { langDir ->
                    val langCode = langDir.name
                    availableLanguages.add(langCode)
                    loadLanguageDirectory(langDir, langCode, sourceMod)
                }
        }
    }

    /**
     * Load all .lang files from a language directory.
     */
    private fun loadLanguageDirectory(langDir: Path, langCode: String, sourceMod: String?) {
        Files.walk(langDir).use { stream ->
            stream.filter { it.extension == "lang" && !it.isDirectory() }
                .forEach { langFile ->
                    loadLangFile(langFile, langCode, sourceMod)
                }
        }
    }

    /**
     * Load a single .lang file.
     */
    private fun loadLangFile(langFile: Path, langCode: String, sourceMod: String?) {
        // Skip if already loaded
        if (loadedFiles.containsKey(langFile)) return

        try {
            val entries = LangFileParser.parse(langFile)
            val keysFromFile = mutableSetOf<String>()

            // Derive key prefix from file path
            // e.g., Server/Languages/en-US/ui/settings/general.lang -> ui.settings.general
            val keyPrefix = deriveKeyPrefix(langFile, langCode)

            entries.forEach { (key, value) ->
                // Build full key: prefix + key
                val fullKey = if (keyPrefix.isNotEmpty()) "$keyPrefix.$key" else key
                keysFromFile.add(fullKey)

                // Update or create the LocalizationKey
                val existing = allKeys[fullKey]
                if (existing != null) {
                    // Add translation to existing key
                    val updatedTranslations = existing.translations.toMutableMap()
                    updatedTranslations[langCode] = value
                    allKeys[fullKey] = existing.copy(translations = updatedTranslations)
                } else {
                    // Create new key
                    allKeys[fullKey] = LocalizationKey(
                        fullKey = fullKey,
                        translations = mapOf(langCode to value),
                        sourceFile = langFile.toString(),
                        sourceMod = sourceMod
                    )
                }
            }

            loadedFiles[langFile] = keysFromFile
        } catch (_: LangParseException) {
            // Skip malformed lang files
        } catch (_: Exception) {
            // Skip unreadable lang files
        }
    }

    /**
     * Derive the key prefix from the file path.
     *
     * e.g., Server/Languages/en-US/ui/settings/general.lang -> ui.settings.general
     */
    private fun deriveKeyPrefix(langFile: Path, langCode: String): String {
        val relativePath = try {
            // Find the language code directory in the path
            var current = langFile.parent
            while (current != null && current.name != langCode) {
                current = current.parent
            }
            if (current != null) {
                current.relativize(langFile).toString()
            } else {
                langFile.name
            }
        } catch (_: Exception) {
            langFile.name
        }

        // Remove .lang extension and convert path separators to dots
        return relativePath
            .removeSuffix(".lang")
            .replace(File.separator, ".")
            .replace("/", ".")
    }

    /**
     * Check if a directory name looks like a language code (e.g., en-US, fr-FR).
     */
    private fun isLanguageCode(name: String): Boolean {
        // Pattern: xx-XX or xx_XX where x is a letter
        return name.matches(Regex("^[a-z]{2}[-_][A-Z]{2}$"))
    }

    // ============================================================
    // Query Methods
    // ============================================================

    /**
     * Get all localization keys.
     */
    fun getAllKeys(): List<LocalizationKey> {
        ensureInitialized()
        return allKeys.values.toList().sortedBy { it.fullKey }
    }

    /**
     * Get a specific localization key by its full key.
     */
    fun getKey(fullKey: String): LocalizationKey? {
        ensureInitialized()
        return allKeys[fullKey]
    }

    /**
     * Check if a key exists.
     */
    fun keyExists(fullKey: String): Boolean {
        ensureInitialized()
        return allKeys.containsKey(fullKey)
    }

    /**
     * Search for keys matching a query.
     *
     * Per spec FR-004: Matches in key name OR translation text.
     *
     * @param query The search query
     * @param languageCode Language code for translation search
     * @return List of matching keys
     */
    fun search(query: String, languageCode: String = "en-US"): List<LocalizationKey> {
        ensureInitialized()
        if (query.isBlank()) return getAllKeys()

        val queryLower = query.lowercase()
        return allKeys.values.filter { key ->
            // Match in key name
            key.fullKey.lowercase().contains(queryLower) ||
            // Match in translation
            key.getTranslation(languageCode)?.lowercase()?.contains(queryLower) == true
        }.sortedBy { it.fullKey }
    }

    /**
     * Get all available language codes.
     */
    fun getAvailableLanguages(): Set<String> {
        ensureInitialized()
        return availableLanguages.toSet()
    }

    /**
     * Build a hierarchical tree of localization keys.
     *
     * Per spec FR-003: Keys grouped by prefix hierarchy.
     */
    fun buildKeyTree(): LocalizationTreeNode {
        ensureInitialized()
        return buildTreeNode("", allKeys.values.toList())
    }

    /**
     * Build a tree node for a specific prefix.
     */
    private fun buildTreeNode(prefix: String, keys: List<LocalizationKey>): LocalizationTreeNode {
        // Group keys by their next segment after the prefix
        val keysAtThisLevel = mutableListOf<LocalizationKey>()
        val childGroups = mutableMapOf<String, MutableList<LocalizationKey>>()

        keys.forEach { key ->
            val relativePath = if (prefix.isEmpty()) key.fullKey else key.fullKey.removePrefix("$prefix.")
            val nextDot = relativePath.indexOf('.')

            if (nextDot == -1) {
                // Key is at this level
                keysAtThisLevel.add(key)
            } else {
                // Key belongs to a child group
                val nextSegment = relativePath.substring(0, nextDot)
                childGroups.getOrPut(nextSegment) { mutableListOf() }.add(key)
            }
        }

        // Build child nodes
        val children = childGroups.map { (segment, childKeys) ->
            val childPrefix = if (prefix.isEmpty()) segment else "$prefix.$segment"
            buildTreeNode(childPrefix, childKeys).copy(segment = segment)
        }.sortedBy { it.segment }

        return LocalizationTreeNode(
            segment = prefix.substringAfterLast('.', prefix),
            fullPath = prefix,
            children = children,
            keys = keysAtThisLevel.sortedBy { it.simpleName }
        )
    }

    /**
     * Get the translation for a key in a specific language.
     *
     * @param fullKey The full key path
     * @param languageCode The language code (default: en-US)
     * @return The translation, or null if not found
     */
    fun getTranslation(fullKey: String, languageCode: String = "en-US"): String? {
        ensureInitialized()
        return allKeys[fullKey]?.getTranslation(languageCode)
    }

    // ============================================================
    // Key Creation
    // ============================================================

    /**
     * Create a new localization key.
     *
     * Per spec FR-008: Create new keys with proper file placement.
     *
     * @param fullKey The full key path
     * @param defaultValue The default value for en-US
     * @param projectRoot The project root directory
     * @param modName The mod name for the file
     * @return Result indicating success or failure
     */
    fun createKey(
        fullKey: String,
        defaultValue: String,
        projectRoot: Path,
        modName: String
    ): CreateKeyResult {
        // Validate key name
        if (!isValidKeyName(fullKey)) {
            return CreateKeyResult.Error("Invalid key name. Use only letters, numbers, dots, and underscores.")
        }

        // Check for duplicates
        if (allKeys.containsKey(fullKey)) {
            return CreateKeyResult.Error("Key '$fullKey' already exists.")
        }

        // Determine target file path
        // Keys like "mymod.ui.button.submit" go to Server/Languages/en-US/mymod.lang
        val targetFile = projectRoot
            .resolve("Server/Languages/en-US")
            .resolve("$modName.lang")

        // Create parent directories if needed
        try {
            Files.createDirectories(targetFile.parent)
        } catch (e: Exception) {
            return CreateKeyResult.Error("Failed to create directories: ${e.message}")
        }

        // Determine the key to write (may need to strip mod prefix)
        val keyToWrite = if (fullKey.startsWith("$modName.")) {
            fullKey.removePrefix("$modName.")
        } else {
            fullKey
        }

        // Append to file
        try {
            val line = "$keyToWrite = \"$defaultValue\"\n"
            Files.writeString(
                targetFile,
                line,
                java.nio.file.StandardOpenOption.CREATE,
                java.nio.file.StandardOpenOption.APPEND
            )

            // Add to in-memory cache
            val newKey = LocalizationKey(
                fullKey = fullKey,
                translations = mapOf("en-US" to defaultValue),
                sourceFile = targetFile.toString(),
                sourceMod = modName
            )
            allKeys[fullKey] = newKey

            return CreateKeyResult.Success(newKey, targetFile)
        } catch (e: Exception) {
            return CreateKeyResult.Error("Failed to write key: ${e.message}")
        }
    }

    /**
     * Validate a key name.
     *
     * Per spec FR-009: No special characters except dots and underscores.
     */
    fun isValidKeyName(key: String): Boolean {
        if (key.isBlank()) return false
        // Allow alphanumeric, dots, and underscores
        return key.matches(Regex("^[a-zA-Z][a-zA-Z0-9._]*$"))
    }

    /**
     * Refresh the cache by reloading all files.
     */
    fun refresh() {
        allKeys.clear()
        loadedFiles.clear()
        availableLanguages.clear()
        initialized = false
        initialize()
    }

    /**
     * Clear all cached data.
     */
    fun clear() {
        allKeys.clear()
        loadedFiles.clear()
        availableLanguages.clear()
        initialized = false
    }

    private fun ensureInitialized() {
        if (!initialized) {
            initialize()
        }
    }

    companion object {
        /** Default language for previews */
        const val DEFAULT_LANGUAGE = "en-US"

        /** Maximum recent keys to track */
        const val MAX_RECENT_KEYS = 10
    }
}

/**
 * Result of creating a new localization key.
 */
sealed interface CreateKeyResult {
    data class Success(val key: LocalizationKey, val filePath: Path) : CreateKeyResult
    data class Error(val message: String) : CreateKeyResult
}
