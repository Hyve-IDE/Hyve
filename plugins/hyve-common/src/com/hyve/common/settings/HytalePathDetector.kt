// Copyright 2026 Hyve. All rights reserved.
package com.hyve.common.settings

import java.io.File
import java.nio.file.Path
import java.nio.file.Paths

object HytalePathDetector {

    private val CANDIDATE_PATHS: List<() -> Path?> = listOf(
        ::windowsPath,
        ::macOsPath,
        ::macOsPathAlt,
        ::linuxPath,
        ::linuxPathAlt,
    )

    fun detect(): Path? {
        return CANDIDATE_PATHS.asSequence()
            .mapNotNull { it() }
            .firstOrNull { isValidInstallPath(it) }
    }

    fun isValidInstallPath(path: Path): Boolean {
        // Check direct structure (Windows/Linux)
        if (File(path.toFile(), "Server/HytaleServer.jar").exists()) return true
        // Check Mac .app bundle structure
        val appBundle = findAppBundle(path)
        if (appBundle != null) {
            val dataDir = appBundle.resolve("Contents/Resources/Data")
            if (File(dataDir, "Server/HytaleServer.jar").exists()) return true
        }
        return false
    }

    /**
     * On Mac, game data lives inside Hytale.app/Contents/Resources/Data/.
     * This method finds the .app bundle directory if it exists.
     */
    fun findAppBundle(installPath: Path): File? {
        val clientDir = installPath.resolve("Client").toFile()
        if (!clientDir.isDirectory) return null
        return clientDir.listFiles()?.firstOrNull { it.name.endsWith(".app") && it.isDirectory }
    }

    private fun windowsPath(): Path? {
        val appData = System.getenv("APPDATA") ?: return null
        return Paths.get(appData, "Hytale Launcher/install/release/package/game/latest")
    }

    private fun macOsPath(): Path? {
        val home = System.getProperty("user.home") ?: return null
        return Paths.get(home, "Library/Application Support/Hytale Launcher/install/release/package/game/latest")
    }

    /** Mac may install under "Hytale" instead of "Hytale Launcher" */
    private fun macOsPathAlt(): Path? {
        val home = System.getProperty("user.home") ?: return null
        return Paths.get(home, "Library/Application Support/Hytale/install/release/package/game/latest")
    }

    private fun linuxPath(): Path? {
        val home = System.getProperty("user.home") ?: return null
        return Paths.get(home, ".local/share/Hytale Launcher/install/release/package/game/latest")
    }

    /** Linux may install under "Hytale" instead of "Hytale Launcher" */
    private fun linuxPathAlt(): Path? {
        val home = System.getProperty("user.home") ?: return null
        return Paths.get(home, ".local/share/Hytale/install/release/package/game/latest")
    }
}
