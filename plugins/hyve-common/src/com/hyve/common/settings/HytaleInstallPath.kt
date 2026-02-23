// Copyright 2026 Hyve. All rights reserved.
package com.hyve.common.settings

import com.intellij.openapi.application.PathManager
import java.io.File
import java.nio.file.Path
import java.nio.file.Paths
import java.util.prefs.Preferences

/**
 * Single source of truth for the Hytale game installation path.
 *
 * All Hyve plugins derive their paths from this shared setting:
 * - `assetsZipPath()` — `{root}/Assets.zip`
 * - `clientFolderPath()` — `{root}/Client`
 * - `interfaceFolderPath()` — `{root}/Client/Data/Game/Interface`
 * - `serverJarPath()` — `{root}/Server/HytaleServer.jar`
 * - `serverModsPath()` — `{root}/Server/Mods`
 *
 * On first access, checks for an installer seed file at `<ide>/bin/hytale-install.path`
 * and imports + deletes it if found.
 */
object HytaleInstallPath {

    private val prefs = Preferences.userNodeForPackage(HytaleInstallPath::class.java)
    private const val KEY = "hytale.install.path"
    private const val SEED_FILE_NAME = "hytale-install.path"

    // ── Override keys ────────────────────────────────────────────
    const val KEY_ASSETS_ZIP = "hytale.assets.zip.path"
    const val KEY_SERVER_JAR = "hytale.server.jar.path"
    const val KEY_SERVER_MODS = "hytale.server.mods.path"

    init {
        importSeedFile()
    }

    // ── Core API ────────────────────────────────────────────────

    /** Returns the saved install path, falling back to auto-detection. */
    fun get(): Path? {
        val saved = prefs.get(KEY, null)
        if (saved != null) return Paths.get(saved)
        return HytalePathDetector.detect()
    }

    fun set(path: Path) {
        prefs.put(KEY, path.toAbsolutePath().toString())
    }

    fun clear() {
        prefs.remove(KEY)
    }

    fun isConfigured(): Boolean = prefs.get(KEY, null) != null

    fun isValid(): Boolean {
        val path = get() ?: return false
        return HytalePathDetector.isValidInstallPath(path)
    }

    // ── Per-path overrides ──────────────────────────────────────

    fun getOverride(key: String): String? = prefs.get(key, null)

    fun setOverride(key: String, path: String) {
        prefs.put(key, path)
    }

    fun clearOverride(key: String) {
        prefs.remove(key)
    }

    /** Returns true if the given key has a user-specified override (not derived). */
    fun hasOverride(key: String): Boolean = prefs.get(key, null) != null

    // ── Derived paths ───────────────────────────────────────────

    /**
     * Resolves the data root, handling Mac .app bundle structure.
     * On Mac, data lives inside `Client/Hytale.app/Contents/Resources/Data/`.
     * On Windows/Linux, data lives directly under the install root.
     */
    private fun dataRoot(): Path? {
        val root = get() ?: return null
        // Check for Mac .app bundle first
        val appBundle = HytalePathDetector.findAppBundle(root)
        if (appBundle != null) {
            val dataDir = appBundle.resolve("Contents/Resources/Data")
            if (dataDir.isDirectory) return dataDir.toPath()
        }
        // Direct structure (Windows/Linux)
        return root
    }

    fun assetsZipPath(): Path? {
        getOverride(KEY_ASSETS_ZIP)?.let { return Paths.get(it) }
        return dataRoot()?.resolve("Assets.zip")
    }

    fun clientFolderPath(): Path? = get()?.resolve("Client")

    fun interfaceFolderPath(): Path? {
        val root = get() ?: return null
        // Check Mac .app bundle first
        val appBundle = HytalePathDetector.findAppBundle(root)
        if (appBundle != null) {
            val bundleInterface = appBundle.resolve("Contents/Resources/Data/Game/Interface")
            if (bundleInterface.isDirectory) return bundleInterface.toPath()
            // Also check Editor/Interface
            val editorInterface = appBundle.resolve("Contents/Resources/Data/Editor/Interface")
            if (editorInterface.isDirectory) return editorInterface.toPath()
        }
        // Direct structure: Client/Data/Game/Interface
        val direct = root.resolve("Client/Data/Game/Interface")
        if (direct.toFile().isDirectory) return direct
        // Also check Editor/Interface on direct structure
        val editorDirect = root.resolve("Client/Data/Editor/Interface")
        if (editorDirect.toFile().isDirectory) return editorDirect
        return direct
    }

    /**
     * Returns ALL interface directories (Game + Editor) that exist on disk.
     *
     * Unlike [interfaceFolderPath] which returns only the first match,
     * this returns the union so schema discovery can scan the full corpus.
     */
    fun interfaceFolderPaths(): List<Path> {
        val root = get() ?: return emptyList()
        val paths = mutableListOf<Path>()
        val appBundle = HytalePathDetector.findAppBundle(root)
        if (appBundle != null) {
            val res = appBundle.resolve("Contents/Resources/Data")
            addIfDirectory(paths, res.toPath().resolve("Game/Interface"))
            addIfDirectory(paths, res.toPath().resolve("Editor/Interface"))
        } else {
            addIfDirectory(paths, root.resolve("Client/Data/Game/Interface"))
            addIfDirectory(paths, root.resolve("Client/Data/Editor/Interface"))
        }
        return paths
    }

    private fun addIfDirectory(list: MutableList<Path>, path: Path) {
        if (path.toFile().isDirectory) list.add(path)
    }

    fun serverJarPath(): Path? {
        getOverride(KEY_SERVER_JAR)?.let { return Paths.get(it) }
        return dataRoot()?.resolve("Server/HytaleServer.jar")
    }

    fun serverModsPath(): Path? {
        getOverride(KEY_SERVER_MODS)?.let { return Paths.get(it) }
        return dataRoot()?.resolve("Server/Mods")
    }

    // ── Seed file import ────────────────────────────────────────

    /**
     * Checks for `<ide-install-dir>/bin/hytale-install.path`.
     * If found, reads the path (plain text, single line), imports it via [set], and deletes the file.
     */
    fun importSeedFile() {
        if (isConfigured()) return

        val binDir = PathManager.getBinPath()
        val seedFile = File(binDir, SEED_FILE_NAME)
        if (!seedFile.exists()) return

        try {
            val pathStr = seedFile.readText().trim()
            if (pathStr.isNotBlank()) {
                val path = Paths.get(pathStr)
                if (HytalePathDetector.isValidInstallPath(path)) {
                    set(path)
                }
            }
        } finally {
            seedFile.delete()
        }
    }
}
