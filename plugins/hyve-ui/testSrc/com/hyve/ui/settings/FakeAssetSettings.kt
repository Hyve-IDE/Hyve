package com.hyve.ui.settings

import java.nio.file.Path
import java.nio.file.Paths

/**
 * In-memory fake of [AssetSettingsProvider] for unit tests.
 * No Java Preferences, no file system access.
 */
class FakeAssetSettings(
    private var assetsZipPath: Path = Paths.get("/fake/Assets.zip"),
    private var clientFolderPath: Path? = null
) : AssetSettingsProvider {

    override fun getAssetsZipPath(): Path = assetsZipPath

    override fun getClientFolderPath(): Path? = clientFolderPath

    override fun getInterfaceFolderPath(): Path? =
        clientFolderPath?.resolve("Data/Game/Interface")

    override fun getInterfaceFolderPaths(): List<Path> {
        val base = clientFolderPath ?: return emptyList()
        return listOf("Data/Game/Interface", "Data/Editor/Interface")
            .map { base.resolve(it) }
            .filter { it.toFile().isDirectory }
    }

    override fun isClientFolderPathConfigured(): Boolean = clientFolderPath != null

    override fun isClientFolderValid(): Boolean {
        val path = clientFolderPath ?: return false
        return path.toFile().let { it.exists() && it.isDirectory }
    }

    override fun isAssetsZipValid(): Boolean {
        val file = assetsZipPath.toFile()
        return file.exists() && file.canRead() && file.extension == "zip"
    }

    // Test helpers
    fun setAssetsZipPath(path: Path) { assetsZipPath = path }
    fun setClientFolderPath(path: Path?) { clientFolderPath = path }
}
