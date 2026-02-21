package com.hyve.ui.settings

import com.hyve.common.settings.HytaleInstallPath
import java.nio.file.Path

/**
 * Manages asset-related settings by delegating to the shared [HytaleInstallPath].
 *
 * All paths are derived from the single Hytale install root:
 * - Assets.zip = `{root}/Assets.zip`
 * - Client folder = `{root}/Client`
 * - Interface folder = `{root}/Client/Data/Game/Interface`
 */
object AssetSettings : AssetSettingsProvider {

    override fun getAssetsZipPath(): Path {
        return HytaleInstallPath.assetsZipPath()
            ?: Path.of("Assets.zip") // fallback so callers always get a non-null Path
    }

    override fun getClientFolderPath(): Path? {
        return HytaleInstallPath.clientFolderPath()
    }

    override fun getInterfaceFolderPath(): Path? {
        return HytaleInstallPath.interfaceFolderPath()
    }

    override fun getInterfaceFolderPaths(): List<Path> {
        return HytaleInstallPath.interfaceFolderPaths()
    }

    override fun isClientFolderPathConfigured(): Boolean {
        return HytaleInstallPath.get() != null
    }

    override fun isClientFolderValid(): Boolean {
        val path = getClientFolderPath() ?: return false
        val file = path.toFile()
        return file.exists() && file.isDirectory && file.canRead()
    }

    override fun isAssetsZipValid(): Boolean {
        val path = HytaleInstallPath.assetsZipPath() ?: return false
        val file = path.toFile()
        return file.exists() && file.canRead() && file.extension == "zip"
    }
}
