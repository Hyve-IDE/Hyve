package com.hyve.ui.services.assets

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.file.Path
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

/**
 * Service for browsing and searching assets in Hytale's Assets.zip.
 *
 * Provides:
 * - Directory tree listing (lazy-loaded)
 * - File search/filter by name
 * - File type filtering (images only, etc.)
 *
 * Works alongside AssetLoader which handles actual texture loading.
 */
class AssetService(private val assetLoader: AssetLoader) {

    private var cachedEntries: List<AssetEntry>? = null
    private var cachedDirectoryTree: DirectoryNode? = null
    private val cacheMutex = Mutex()

    /**
     * Represents a file or directory entry in the ZIP.
     */
    data class AssetEntry(
        val path: String,
        val name: String,
        val isDirectory: Boolean,
        val size: Long,
        val extension: String
    ) {
        val isImage: Boolean
            get() = extension.lowercase() in IMAGE_EXTENSIONS

        val parentPath: String
            get() {
                val lastSlash = path.lastIndexOf('/')
                return if (lastSlash > 0) path.substring(0, lastSlash) else ""
            }
    }

    /**
     * Represents a directory in the tree structure.
     */
    data class DirectoryNode(
        val path: String,
        val name: String,
        val children: MutableList<DirectoryNode> = mutableListOf(),
        val files: MutableList<AssetEntry> = mutableListOf()
    ) {
        val displayName: String
            get() = if (name.isEmpty()) "Assets" else name

        val hasContent: Boolean
            get() = children.isNotEmpty() || files.isNotEmpty()

        /**
         * Count of all files (including in subdirectories).
         */
        fun totalFileCount(): Int {
            return files.size + children.sumOf { it.totalFileCount() }
        }

        /**
         * Count of image files (including in subdirectories).
         */
        fun totalImageCount(): Int {
            return files.count { it.isImage } + children.sumOf { it.totalImageCount() }
        }
    }

    /**
     * Check if the asset service is available (i.e., Assets.zip is accessible).
     */
    val isAvailable: Boolean
        get() = assetLoader.isAvailable

    /**
     * Get all entries from the ZIP file (cached after first call).
     */
    suspend fun getAllEntries(): List<AssetEntry> = withContext(Dispatchers.IO) {
        cacheMutex.withLock {
            cachedEntries?.let { return@withLock it }

            val entries = loadEntriesFromZip()
            cachedEntries = entries
            entries
        }
    }

    /**
     * Get the directory tree structure (cached after first call).
     */
    suspend fun getDirectoryTree(): DirectoryNode = withContext(Dispatchers.IO) {
        // First ensure entries are loaded (outside the tree lock to avoid deadlock)
        val entries = getAllEntries()

        cacheMutex.withLock {
            cachedDirectoryTree?.let { return@withLock it }

            val tree = buildDirectoryTree(entries)
            cachedDirectoryTree = tree
            tree
        }
    }

    /**
     * List immediate children of a directory.
     *
     * @param directoryPath Path to the directory (e.g., "Common/UI" or "" for root)
     * @param imagesOnly If true, only return image files
     * @return List of entries that are direct children of the directory
     */
    suspend fun listDirectory(
        directoryPath: String,
        imagesOnly: Boolean = false
    ): List<AssetEntry> = withContext(Dispatchers.IO) {
        val normalizedPath = directoryPath.trimEnd('/')
        val entries = getAllEntries()

        entries.filter { entry ->
            val entryParent = entry.parentPath
            entryParent == normalizedPath && (!imagesOnly || entry.isImage || entry.isDirectory)
        }.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
    }

    /**
     * Search for files matching a query.
     *
     * @param query Search query (matches against file name, case-insensitive)
     * @param imagesOnly If true, only return image files
     * @param maxResults Maximum number of results to return
     * @return List of matching entries
     */
    suspend fun search(
        query: String,
        imagesOnly: Boolean = true,
        maxResults: Int = 100
    ): List<AssetEntry> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()

        val entries = getAllEntries()
        val queryLower = query.lowercase()

        entries
            .filter { entry ->
                !entry.isDirectory &&
                        entry.name.lowercase().contains(queryLower) &&
                        (!imagesOnly || entry.isImage)
            }
            .sortedWith(
                compareBy(
                    // Exact name match first
                    { !it.name.equals(query, ignoreCase = true) },
                    // Name starts with query second
                    { !it.name.lowercase().startsWith(queryLower) },
                    // Then alphabetically
                    { it.name.lowercase() }
                )
            )
            .take(maxResults)
    }

    /**
     * Get all subdirectory paths starting from a given path.
     */
    suspend fun getSubdirectories(directoryPath: String): List<String> = withContext(Dispatchers.IO) {
        val normalizedPath = directoryPath.trimEnd('/')
        val tree = getDirectoryTree()

        // Find the directory node
        val node = findDirectoryNode(tree, normalizedPath)
        node?.children?.map { it.path }?.sorted() ?: emptyList()
    }

    /**
     * Find a directory node in the tree by path.
     */
    private fun findDirectoryNode(root: DirectoryNode, path: String): DirectoryNode? {
        if (path.isEmpty() || root.path == path) return root

        val parts = path.split("/")
        var current = root

        for (part in parts) {
            if (part.isEmpty()) continue
            val child = current.children.find { it.name == part }
            if (child != null) {
                current = child
            } else {
                return null
            }
        }

        return current
    }

    /**
     * Load all entries from the ZIP file.
     */
    private suspend fun loadEntriesFromZip(): List<AssetEntry> {
        if (!assetLoader.isAvailable) {
            return emptyList()
        }

        val zipPath = assetLoader.zipPath

        return try {
            val file = zipPath.toFile()
            if (!file.exists() || !file.canRead()) {
                return emptyList()
            }

            val zipFile = ZipFile(file)
            zipFile.use { zip ->
                zip.entries().toList().mapNotNull { entry ->
                    parseZipEntry(entry)
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    /**
     * Parse a ZipEntry into an AssetEntry.
     */
    private fun parseZipEntry(entry: ZipEntry): AssetEntry? {
        // Normalize path - remove "assets/" prefix if present
        var path = entry.name.replace('\\', '/')
        if (path.startsWith("assets/")) {
            path = path.removePrefix("assets/")
        }

        // Skip empty paths and meta directories
        if (path.isEmpty() || path.startsWith("__MACOSX") || path.startsWith(".")) {
            return null
        }

        val isDirectory = entry.isDirectory || path.endsWith("/")
        val cleanPath = path.trimEnd('/')

        val name = cleanPath.substringAfterLast('/')
        val extension = if (isDirectory) "" else name.substringAfterLast('.', "")

        return AssetEntry(
            path = cleanPath,
            name = name,
            isDirectory = isDirectory,
            size = entry.size,
            extension = extension
        )
    }

    /**
     * Build a directory tree from flat entries.
     */
    private fun buildDirectoryTree(entries: List<AssetEntry>): DirectoryNode {
        val root = DirectoryNode(path = "", name = "")

        // Collect all unique directory paths
        val directories = mutableSetOf<String>()
        entries.forEach { entry ->
            if (entry.isDirectory) {
                directories.add(entry.path)
            }
            // Also add parent directories from file paths
            var parent = entry.parentPath
            while (parent.isNotEmpty()) {
                directories.add(parent)
                parent = parent.substringBeforeLast('/', "")
            }
        }

        // Create nodes for all directories
        val nodeMap = mutableMapOf<String, DirectoryNode>()
        nodeMap[""] = root

        directories.sorted().forEach { dirPath ->
            val parts = dirPath.split("/")
            var currentPath = ""
            var parent = root

            for (part in parts) {
                if (part.isEmpty()) continue
                val newPath = if (currentPath.isEmpty()) part else "$currentPath/$part"

                val node = nodeMap.getOrPut(newPath) {
                    DirectoryNode(path = newPath, name = part).also {
                        parent.children.add(it)
                    }
                }
                currentPath = newPath
                parent = node
            }
        }

        // Sort children alphabetically
        fun sortChildren(node: DirectoryNode) {
            node.children.sortBy { it.name.lowercase() }
            node.children.forEach { sortChildren(it) }
        }
        sortChildren(root)

        // Add files to their parent directories
        entries.filter { !it.isDirectory }.forEach { file ->
            val parentNode = nodeMap[file.parentPath] ?: root
            parentNode.files.add(file)
        }

        // Sort files in each directory
        nodeMap.values.forEach { node ->
            node.files.sortBy { it.name.lowercase() }
        }

        return root
    }

    /**
     * Build a directory tree from a project's resources/ folder on disk.
     *
     * @param resourcesRoot The project's resources/ directory
     * @return A DirectoryNode tree, or empty root if path doesn't exist
     */
    suspend fun scanFilesystemTree(resourcesRoot: Path): DirectoryNode = withContext(Dispatchers.IO) {
        val rootFile = resourcesRoot.toFile()
        if (!rootFile.exists() || !rootFile.isDirectory) {
            return@withContext DirectoryNode(path = "", name = "")
        }

        val entries = rootFile.walkTopDown()
            .filter { it.isFile }
            .mapNotNull { file ->
                val relativePath = resourcesRoot.relativize(file.toPath())
                    .toString().replace('\\', '/')
                val name = file.name
                val extension = name.substringAfterLast('.', "")
                AssetEntry(
                    path = relativePath,
                    name = name,
                    isDirectory = false,
                    size = file.length(),
                    extension = extension
                )
            }
            .toList()

        buildDirectoryTree(entries)
    }

    /**
     * List immediate file children of a directory on disk.
     *
     * @param resourcesRoot The project's resources/ directory
     * @param directoryPath Relative path within resources (e.g., "Common/UI")
     * @param imagesOnly If true, only return image files
     * @return List of asset entries for direct children
     */
    suspend fun listFilesystemDirectory(
        resourcesRoot: Path,
        directoryPath: String,
        imagesOnly: Boolean = false
    ): List<AssetEntry> = withContext(Dispatchers.IO) {
        val dir = resourcesRoot.resolve(directoryPath).toFile()
        if (!dir.exists() || !dir.isDirectory) return@withContext emptyList()

        dir.listFiles()
            ?.filter { it.isFile }
            ?.filter { file ->
                if (imagesOnly) {
                    file.extension.lowercase() in IMAGE_EXTENSIONS
                } else true
            }
            ?.map { file ->
                val relativePath = resourcesRoot.relativize(file.toPath())
                    .toString().replace('\\', '/')
                AssetEntry(
                    path = relativePath,
                    name = file.name,
                    isDirectory = false,
                    size = file.length(),
                    extension = file.extension
                )
            }
            ?.sortedBy { it.name.lowercase() }
            ?: emptyList()
    }

    /**
     * Search for files in a project's resources/ folder.
     *
     * @param resourcesRoot The project's resources/ directory
     * @param query Search query (matches against file name, case-insensitive)
     * @param imagesOnly If true, only return image files
     * @param maxResults Maximum number of results to return
     * @return List of matching entries, sorted by relevance
     */
    suspend fun searchFilesystem(
        resourcesRoot: Path,
        query: String,
        imagesOnly: Boolean = true,
        maxResults: Int = 100
    ): List<AssetEntry> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()

        val rootFile = resourcesRoot.toFile()
        if (!rootFile.exists() || !rootFile.isDirectory) return@withContext emptyList()

        val queryLower = query.lowercase()

        rootFile.walkTopDown()
            .filter { it.isFile }
            .filter { file ->
                file.name.lowercase().contains(queryLower) &&
                    (!imagesOnly || file.extension.lowercase() in IMAGE_EXTENSIONS)
            }
            .take(maxResults * 2) // take extra for sorting then trim
            .map { file ->
                val relativePath = resourcesRoot.relativize(file.toPath())
                    .toString().replace('\\', '/')
                AssetEntry(
                    path = relativePath,
                    name = file.name,
                    isDirectory = false,
                    size = file.length(),
                    extension = file.extension
                )
            }
            .sortedWith(
                compareBy(
                    { !it.name.equals(query, ignoreCase = true) },
                    { !it.name.lowercase().startsWith(queryLower) },
                    { it.name.lowercase() }
                )
            )
            .take(maxResults)
            .toList()
    }

    /**
     * Clear cached data to force reload.
     */
    suspend fun clearCache() {
        cacheMutex.withLock {
            cachedEntries = null
            cachedDirectoryTree = null
        }
    }

    companion object {
        /**
         * Supported image file extensions.
         */
        val IMAGE_EXTENSIONS = setOf("png", "jpg", "jpeg", "gif", "bmp", "webp")
    }
}
