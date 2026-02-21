package com.hyve.ui.schema

import com.hyve.ui.schema.discovery.DiscoveryResult
import com.hyve.ui.schema.discovery.SchemaDiscovery
import com.hyve.ui.schema.discovery.TupleFieldDiscovery
import com.hyve.ui.schema.discovery.TupleFieldResult
import com.hyve.ui.settings.AssetSettings
import com.intellij.openapi.diagnostic.Logger
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.util.zip.ZipFile

/**
 * Service for managing the runtime schema registry.
 *
 * Handles auto-discovery from the Hytale Client folder and caching
 * of discovered schemas to avoid re-parsing on every editor open.
 *
 * Usage:
 * ```kotlin
 * val registry = SchemaService.getOrDiscoverSchema()
 * ```
 */
object SchemaService : SchemaProvider {

    private val LOG = Logger.getInstance(SchemaService::class.java)

    // Cached registry instance - shared across all editor instances
    @Volatile
    private var cachedRegistry: RuntimeSchemaRegistry? = null

    // Track whether discovery is in progress to avoid concurrent runs
    @Volatile
    private var discoveryInProgress = false

    // Cache file location - stored in user's .hyve directory
    private val cacheFile: File by lazy {
        val hyveDir = File(System.getProperty("user.home"), ".hyve")
        hyveDir.mkdirs()
        File(hyveDir, "ui-schema.json")
    }

    /**
     * Get the cached schema registry, or discover it from the Client folder
     * if not yet cached. Returns an empty registry if discovery is not possible.
     *
     * This is the main entry point for getting the schema registry.
     */
    override fun getOrDiscoverSchema(forceRediscover: Boolean): RuntimeSchemaRegistry {
        // Return cached if available and not forcing rediscovery
        if (!forceRediscover && cachedRegistry != null) {
            return cachedRegistry!!
        }

        // Load curated schema as seed
        val curatedSchema = RuntimeSchemaRegistry.loadFromResource()

        synchronized(this) {
            // Double-check after acquiring lock
            if (!forceRediscover && cachedRegistry != null) {
                return cachedRegistry!!
            }

            // Try to load from cache file first (unless forcing rediscovery)
            if (!forceRediscover) {
                val fromCache = loadFromCache()
                if (fromCache != null && fromCache.isLoaded) {
                    // Merge curated baseline into cached result so curated properties
                    // (like Stretch with observedValues) are always available
                    val merged = if (curatedSchema.isLoaded) curatedSchema.merge(fromCache) else fromCache
                    cachedRegistry = merged
                    return merged
                }
            }

            // Try to discover from Client folder
            val discovered = discoverFromClientFolder(curatedSchema)
            if (discovered != null) {
                cachedRegistry = discovered
                return discovered
            }

            // Fall back to curated schema alone if discovery fails
            if (curatedSchema.isLoaded) {
                cachedRegistry = curatedSchema
                return curatedSchema
            }

            // Fall back to empty registry
            val empty = RuntimeSchemaRegistry.empty()
            cachedRegistry = empty
            return empty
        }
    }

    /**
     * Check if schema discovery is possible (Client folder is configured and valid).
     */
    override fun canDiscoverSchema(): Boolean {
        return AssetSettings.isClientFolderPathConfigured() && AssetSettings.isClientFolderValid()
    }

    /**
     * Check if a cached schema is available.
     */
    override fun hasCachedSchema(): Boolean {
        return cachedRegistry?.isLoaded == true || cacheFile.exists()
    }

    /**
     * Clear the cached schema and cache file.
     */
    override fun clearCache() {
        synchronized(this) {
            cachedRegistry = null
            if (cacheFile.exists()) {
                cacheFile.delete()
            }
        }
    }

    /**
     * Get metadata about the current schema (or null if not loaded).
     */
    fun getSchemaMetadata(): DiscoveryMetadata? {
        return cachedRegistry?.getDiscoveryMetadata()
    }

    /**
     * Try to load schema from the cache file.
     * Returns null if the cache doesn't exist, can't be read, or is stale (fingerprint mismatch).
     */
    private fun loadFromCache(): RuntimeSchemaRegistry? {
        return try {
            if (!cacheFile.exists() || !cacheFile.canRead()) return null

            // Check fingerprint before loading the full registry
            val cachedFingerprint = readCachedFingerprint()
            if (cachedFingerprint.isNotEmpty()) {
                val currentFingerprint = computeCorpusFingerprint()
                if (currentFingerprint.isNotEmpty() && cachedFingerprint != currentFingerprint) {
                    LOG.info("Schema cache fingerprint mismatch — invalidating (cached=$cachedFingerprint, current=$currentFingerprint)")
                    return null
                }
            }

            RuntimeSchemaRegistry.loadFromFile(cacheFile)
        } catch (e: Exception) {
            LOG.debug("Failed to load schema cache from ${cacheFile.path}", e)
            null
        }
    }

    /**
     * Read just the corpusFingerprint field from the cache JSON without full deserialization.
     */
    private fun readCachedFingerprint(): String {
        return try {
            val json = kotlinx.serialization.json.Json.parseToJsonElement(cacheFile.readText())
            json.jsonObject["corpusFingerprint"]?.jsonPrimitive?.content ?: ""
        } catch (_: Exception) {
            ""
        }
    }

    /**
     * Compute a lightweight corpus fingerprint from all interface directories + Assets.zip.
     * Format: "fileCount:totalBytes:newestModifiedMs"
     */
    private fun computeCorpusFingerprint(): String {
        var fileCount = 0L
        var totalBytes = 0L
        var newestModified = 0L

        // Filesystem directories
        for (dir in AssetSettings.getInterfaceFolderPaths()) {
            val dirFile = dir.toFile()
            if (!dirFile.isDirectory) continue
            dirFile.walkTopDown()
                .filter { it.isFile && it.extension.equals("ui", ignoreCase = true) }
                .forEach { f ->
                    fileCount++
                    totalBytes += f.length()
                    val mod = f.lastModified()
                    if (mod > newestModified) newestModified = mod
                }
        }

        // Assets.zip
        val zipFile = AssetSettings.getAssetsZipPath().toFile()
        if (zipFile.exists() && zipFile.canRead()) {
            fileCount++
            totalBytes += zipFile.length()
            val mod = zipFile.lastModified()
            if (mod > newestModified) newestModified = mod
        }

        return if (fileCount > 0) "$fileCount:$totalBytes:$newestModified" else ""
    }

    /**
     * Discover schema from all available sources and save to cache.
     *
     * Sources:
     * 1. All interface directories (Game/Interface + Editor/Interface)
     * 2. .ui files inside Assets.zip
     */
    private fun discoverFromClientFolder(curatedBaseline: RuntimeSchemaRegistry): RuntimeSchemaRegistry? {
        val interfaceDirs = AssetSettings.getInterfaceFolderPaths()
            .map { it.toFile() }
            .filter { it.exists() && it.isDirectory }

        // Fall back to singular path for backward compat
        if (interfaceDirs.isEmpty()) {
            val singlePath = AssetSettings.getInterfaceFolderPath()
            if (singlePath == null || !singlePath.toFile().exists()) {
                return null
            }
            // Use single-path fallback
            return discoverFromSourcesInternal(
                listOf(singlePath.toFile()), emptyList(), curatedBaseline
            )
        }

        val zipSources = extractUiFromAssetsZip()
        return discoverFromSourcesInternal(interfaceDirs, zipSources, curatedBaseline)
    }

    private fun discoverFromSourcesInternal(
        directories: List<File>,
        inMemorySources: List<Pair<String, String>>,
        curatedBaseline: RuntimeSchemaRegistry
    ): RuntimeSchemaRegistry? {
        // Prevent concurrent discovery
        if (discoveryInProgress) {
            return null
        }

        discoveryInProgress = true
        try {
            val discovery = SchemaDiscovery()
            val result = discovery.discoverFromSources(directories, inMemorySources)

            // Run tuple field discovery on the same sources
            val tupleDiscovery = TupleFieldDiscovery()
            val tupleResult = tupleDiscovery.discoverFromSources(directories, inMemorySources)

            // Save to cache file (tuple fields are embedded in DiscoveredProperty JSON)
            val discoveredRegistry = RuntimeSchemaRegistry.fromDiscoveryResult(result, tupleResult)
            try {
                val fingerprint = computeCorpusFingerprint()
                val enrichedResult = enrichResultWithTupleFields(result, tupleResult)
                    .copy(corpusFingerprint = fingerprint)
                enrichedResult.toJsonFile(cacheFile)
            } catch (e: Exception) {
                LOG.debug("Failed to write schema cache to ${cacheFile.path}", e)
            }

            // Merge curated baseline with discovery overlay
            return curatedBaseline.merge(discoveredRegistry)
        } catch (e: Exception) {
            LOG.warn("Schema discovery failed", e)
            return null
        } finally {
            discoveryInProgress = false
        }
    }

    /**
     * Extract .ui file entries from Assets.zip as in-memory (name, content) pairs.
     */
    private fun extractUiFromAssetsZip(): List<Pair<String, String>> {
        val zipPath = AssetSettings.getAssetsZipPath()
        val zipFile = zipPath.toFile()
        if (!zipFile.exists() || !zipFile.canRead()) return emptyList()

        return try {
            ZipFile(zipFile).use { zip ->
                zip.entries().asSequence()
                    .filter { it.name.endsWith(".ui", ignoreCase = true) && !it.isDirectory }
                    .map { entry ->
                        entry.name to zip.getInputStream(entry).bufferedReader().readText()
                    }
                    .toList()
            }
        } catch (e: Exception) {
            LOG.debug("Failed to read .ui files from Assets.zip", e)
            emptyList()
        }
    }

    /**
     * Force re-discovery of the schema from the Client folder.
     * This clears the cache and runs discovery again.
     */
    fun rediscover(): RuntimeSchemaRegistry {
        clearCache()
        return getOrDiscoverSchema(forceRediscover = true)
    }

    /**
     * Enrich a DiscoveryResult by attaching tuple field info to TUPLE/ANY-typed properties.
     * This produces a result whose JSON serialization includes tupleFields arrays,
     * so the cache file preserves the tuple field data.
     */
    private fun enrichResultWithTupleFields(result: DiscoveryResult, tupleResult: TupleFieldResult): DiscoveryResult {
        val enrichedElements = result.elements.map { element ->
            val enrichedProps = element.properties.map { prop ->
                if (prop.type.uppercase() in setOf("TUPLE", "ANY")) {
                    val fields = tupleResult.fieldsByProperty[prop.name] ?: emptyList()
                    if (fields.isNotEmpty()) prop.copy(tupleFields = fields) else prop
                } else {
                    prop
                }
            }
            element.copy(properties = enrichedProps)
        }
        return result.copy(elements = enrichedElements)
    }
}
