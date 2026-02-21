package com.hyve.ui.services.assets

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import com.intellij.openapi.diagnostic.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.jetbrains.skia.Image
import java.io.File
import java.nio.file.Path
import java.util.zip.ZipFile

/**
 * Loads textures and assets from Hytale's Assets.zip file.
 *
 * Features:
 * - Lazy loading of textures on demand
 * - LRU cache to limit memory usage
 * - Thread-safe access via coroutines
 * - Graceful fallback for missing assets
 *
 * Usage:
 * ```kotlin
 * val loader = AssetLoader(Path.of("D:/Roaming/.../Assets.zip"))
 * val texture = loader.loadTexture("Common/UI/Icons/shop_icon.png")
 * ```
 */
class AssetLoader(
    private val assetsZipPath: Path,
    private val projectResourcesPath: Path? = null,
    private val maxCacheSize: Int = 100
) {
    private var zipFile: ZipFile? = null
    private val zipMutex = Mutex()

    // LRU cache for loaded textures
    private val textureCache = object : LinkedHashMap<String, ImageBitmap>(
        maxCacheSize, 0.75f, true
    ) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ImageBitmap>?): Boolean {
            return size > maxCacheSize
        }
    }
    private val cacheMutex = Mutex()

    // Track failed loads to avoid repeated attempts
    private val failedPaths = mutableSetOf<String>()
    private val failedMutex = Mutex()

    /**
     * Check if the assets ZIP file exists and is readable.
     */
    val isAvailable: Boolean
        get() = assetsZipPath.toFile().let { it.exists() && it.canRead() && it.extension == "zip" }

    /**
     * Check if textures can be loaded from any source (ZIP or project resources).
     */
    val canLoadTextures: Boolean
        get() = isAvailable || (projectResourcesPath != null && projectResourcesPath.toFile().exists())

    /**
     * Get the path to the assets ZIP file.
     */
    val zipPath: Path get() = assetsZipPath

    /**
     * Get the project resources path (if set).
     */
    val projectPath: Path? get() = projectResourcesPath

    /**
     * Load a texture from the assets ZIP.
     *
     * @param relativePath Path relative to the ZIP root (e.g., "Common/UI/Icons/icon.png")
     * @return The loaded ImageBitmap, or null if not found
     */
    suspend fun loadTexture(relativePath: String): ImageBitmap? = withContext(Dispatchers.IO) {
        // Normalize path separators
        val normalizedPath = relativePath.replace('\\', '/')

        // Check if this path previously failed
        failedMutex.withLock {
            if (normalizedPath in failedPaths) {
                return@withContext null
            }
        }

        // Check cache first
        cacheMutex.withLock {
            textureCache[normalizedPath]?.let {
                return@withContext it
            }
        }

        // Try project resources first (mod assets override vanilla)
        val projectBitmap = loadFromProjectResources(normalizedPath)
        if (projectBitmap != null) {
            cacheMutex.withLock { textureCache[normalizedPath] = projectBitmap }
            return@withContext projectBitmap
        }

        // Load from ZIP
        try {
            val zip = getOrOpenZip() ?: return@withContext null

            // Try multiple possible paths in ZIP
            // Hytale stores assets under different prefixes:
            // - Direct path (as provided)
            // - assets/ prefix (common in ZIP)
            // - Common/ prefix (for shared resources like Icons/ItemsGenerated)
            // - Client/ prefix (for client-specific resources)
            // - Client/Data/Game/ prefix (for Interface and other game UI assets)
            val possibleZipPaths = listOf(
                normalizedPath,
                "assets/$normalizedPath",
                "Common/$normalizedPath",
                "assets/Common/$normalizedPath",
                "Client/$normalizedPath",
                "assets/Client/$normalizedPath",
                "Client/Data/Game/$normalizedPath",
                "assets/Client/Data/Game/$normalizedPath",
                // UI textures referenced in .ui files resolve relative to Interface/
                "Client/Data/Game/Interface/$normalizedPath",
                "assets/Client/Data/Game/Interface/$normalizedPath"
            )

            var entry: java.util.zip.ZipEntry? = null
            var matchedZipPath: String? = null
            for (tryPath in possibleZipPaths) {
                entry = zip.getEntry(tryPath)
                if (entry != null) { matchedZipPath = tryPath; break }
            }

            // If not found in ZIP, try loading from Client folder on disk
            if (entry == null) {
                val clientDir = assetsZipPath.parent?.resolve("Client")
                if (clientDir != null) {
                    // Generate @2x variant path for high-DPI textures
                    // e.g., "ActiveSlot.png" -> "ActiveSlot@2x.png"
                    val at2xPath = if (normalizedPath.contains('.')) {
                        val dotIndex = normalizedPath.lastIndexOf('.')
                        normalizedPath.substring(0, dotIndex) + "@2x" + normalizedPath.substring(dotIndex)
                    } else {
                        normalizedPath + "@2x"
                    }

                    // Try various paths relative to Client folder, including @2x variants
                    val possibleClientPaths = listOf(
                        clientDir.resolve(normalizedPath),
                        clientDir.resolve("Data/Game/$normalizedPath"),
                        clientDir.resolve("Data/$normalizedPath"),
                        // UI textures referenced in .ui files resolve relative to Interface/
                        clientDir.resolve("Data/Game/Interface/$normalizedPath"),
                        // Try @2x variants (Hytale uses these for high-DPI textures)
                        clientDir.resolve(at2xPath),
                        clientDir.resolve("Data/Game/$at2xPath"),
                        clientDir.resolve("Data/$at2xPath"),
                        clientDir.resolve("Data/Game/Interface/$at2xPath")
                    )

                    for (clientPath in possibleClientPaths) {
                        val file = clientPath.toFile()
                        if (file.exists() && file.canRead()) {
                            try {
                                val bytes = file.readBytes()
                                val skiaImage = Image.makeFromEncoded(bytes)
                                val bitmap = skiaImage.toComposeImageBitmap()

                                // Cache the result
                                cacheMutex.withLock {
                                    textureCache[normalizedPath] = bitmap
                                }

                                return@withContext bitmap
                            } catch (_: Exception) {
                                // Continue trying other paths
                            }
                        }
                    }
                }

                failedMutex.withLock { failedPaths.add(normalizedPath) }
                return@withContext null
            }

            val bytes = zip.getInputStream(entry).use { it.readBytes() }
            val skiaImage = Image.makeFromEncoded(bytes)
            val bitmap = skiaImage.toComposeImageBitmap()

            // Cache the result
            cacheMutex.withLock {
                textureCache[normalizedPath] = bitmap
            }

            bitmap
        } catch (_: Exception) {
            failedMutex.withLock { failedPaths.add(normalizedPath) }
            null
        }
    }

    /**
     * Read raw bytes from a file in the assets ZIP.
     *
     * @param relativePath Path relative to the ZIP root (e.g., "Server/Item/Items/Sword.json")
     * @return The file contents as bytes, or null if not found
     */
    suspend fun readBytes(relativePath: String): ByteArray? = withContext(Dispatchers.IO) {
        val normalizedPath = relativePath.replace('\\', '/')

        try {
            val zip = getOrOpenZip() ?: return@withContext null

            val entry = zip.getEntry(normalizedPath)
                ?: zip.getEntry("assets/$normalizedPath")
                ?: return@withContext null

            zip.getInputStream(entry).use { it.readBytes() }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Read a text file from the assets ZIP.
     *
     * @param relativePath Path relative to the ZIP root
     * @return The file contents as string, or null if not found
     */
    suspend fun readText(relativePath: String): String? {
        return readBytes(relativePath)?.toString(Charsets.UTF_8)
    }

    /**
     * Check if a texture exists in the assets ZIP without loading it.
     *
     * @param relativePath Path relative to the ZIP root
     * @return True if the entry exists
     */
    suspend fun hasTexture(relativePath: String): Boolean = withContext(Dispatchers.IO) {
        val normalizedPath = relativePath.replace('\\', '/')

        try {
            val zip = getOrOpenZip() ?: return@withContext false
            zip.getEntry(normalizedPath) != null || zip.getEntry("assets/$normalizedPath") != null
        } catch (_: Exception) {
            false
        }
    }

    /**
     * List all entries in a directory within the ZIP.
     *
     * @param directoryPath Path to the directory (e.g., "Common/UI/Icons")
     * @return List of entry names in the directory
     */
    suspend fun listDirectory(directoryPath: String): List<String> = withContext(Dispatchers.IO) {
        val normalizedPath = directoryPath.replace('\\', '/').trimEnd('/')

        try {
            val zip = getOrOpenZip() ?: return@withContext emptyList()

            zip.entries().toList()
                .filter { entry ->
                    val name = entry.name
                    name.startsWith("$normalizedPath/") || name.startsWith("assets/$normalizedPath/")
                }
                .map { entry ->
                    entry.name.removePrefix("assets/").removePrefix("$normalizedPath/")
                }
                .filter { it.isNotEmpty() && !it.contains('/') } // Only direct children
        } catch (_: Exception) {
            emptyList()
        }
    }

    /**
     * Clear the texture cache.
     */
    suspend fun clearCache() {
        cacheMutex.withLock {
            textureCache.clear()
        }
        failedMutex.withLock {
            failedPaths.clear()
        }
    }

    /**
     * Close the ZIP file and release resources.
     */
    suspend fun close() {
        zipMutex.withLock {
            zipFile?.close()
            zipFile = null
        }
        clearCache()
    }

    /**
     * Try to load a texture from the project's resources/ folder.
     * Returns null if projectResourcesPath is not set or the file is not found.
     */
    private fun loadFromProjectResources(normalizedPath: String): ImageBitmap? {
        val resPath = projectResourcesPath ?: return null
        if (!resPath.toFile().exists()) return null

        // Build @2x variant path
        val at2xPath = if (normalizedPath.contains('.')) {
            val dotIndex = normalizedPath.lastIndexOf('.')
            normalizedPath.substring(0, dotIndex) + "@2x" + normalizedPath.substring(dotIndex)
        } else {
            normalizedPath + "@2x"
        }

        // Try direct path, then Common/ prefix, then @2x variants of each
        val candidates = listOf(
            resPath.resolve(normalizedPath),
            resPath.resolve("Common/$normalizedPath"),
            resPath.resolve(at2xPath),
            resPath.resolve("Common/$at2xPath")
        )

        for (candidate in candidates) {
            val file = candidate.toFile()
            if (file.exists() && file.canRead()) {
                try {
                    val bytes = file.readBytes()
                    val skiaImage = Image.makeFromEncoded(bytes)
                    return skiaImage.toComposeImageBitmap()
                } catch (_: Exception) {
                    // Continue trying other paths
                }
            }
        }
        return null
    }

    /**
     * Get the open ZIP file or open it if not already open.
     */
    private suspend fun getOrOpenZip(): ZipFile? = zipMutex.withLock {
        if (zipFile == null) {
            val file = assetsZipPath.toFile()
            if (!file.exists() || !file.canRead()) {
                return@withLock null
            }

            try {
                zipFile = ZipFile(file)
            } catch (e: Exception) {
                LOG.warn("Failed to open Assets.zip at ${file.path}", e)
                return@withLock null
            }
        }
        zipFile
    }

    companion object {
        private val LOG = Logger.getInstance(AssetLoader::class.java)

        // Supported image extensions
        val SUPPORTED_EXTENSIONS = setOf("png", "jpg", "jpeg", "gif", "bmp")

        /**
         * Check if a path is a supported image file.
         */
        fun isSupportedImage(path: String): Boolean {
            val extension = path.substringAfterLast('.', "").lowercase()
            return extension in SUPPORTED_EXTENSIONS
        }
    }
}

/**
 * Result of an asset load operation.
 */
sealed class AssetLoadResult {
    data class Success(val bitmap: ImageBitmap) : AssetLoadResult()
    data class NotFound(val path: String) : AssetLoadResult()
    data class Error(val path: String, val message: String) : AssetLoadResult()
    object ZipNotAvailable : AssetLoadResult()
}
