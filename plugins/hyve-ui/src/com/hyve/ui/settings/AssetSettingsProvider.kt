package com.hyve.ui.settings

import java.nio.file.Path

/**
 * Read-only interface for asset settings.
 *
 * Production implementation: [AssetSettings] singleton.
 * Test implementation: FakeAssetSettings (in testSrc).
 */
interface AssetSettingsProvider {
    fun getAssetsZipPath(): Path
    fun getClientFolderPath(): Path?
    fun getInterfaceFolderPath(): Path?
    /** Returns ALL interface directories (Game + Editor) that exist on disk. */
    fun getInterfaceFolderPaths(): List<Path>
    fun isClientFolderPathConfigured(): Boolean
    fun isClientFolderValid(): Boolean
    fun isAssetsZipValid(): Boolean
}
