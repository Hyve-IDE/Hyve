// Copyright 2026 Hyve. All rights reserved.
package com.hyve.common.settings

import java.io.File
import java.nio.file.Path
import java.util.jar.JarFile

/**
 * Detects the Hytale game version from the server JAR manifest.
 *
 * The JAR at `Server/HytaleServer.jar` contains `META-INF/MANIFEST.MF` with:
 * - `Implementation-Patchline`: "release" or "pre-release"
 * - `Implementation-Version`: "YYYY.MM.DD-shortHash" (e.g. "2026.02.19-1a311a592")
 * - `Implementation-Revision-Id`: full 40-char git commit hash
 */
object HytaleVersionDetector {

    data class HytaleVersionInfo(
        val patchline: String,
        val date: String,
        val shortHash: String,
        val fullRevision: String,
        val rawVersion: String,
    ) {
        /** Filesystem-safe slug: e.g. "release_2026.02.19-1a311a592" */
        val slug: String get() = "${patchline}_${rawVersion}"

        /** Human-readable display: e.g. "release/2026.02.19-1a311a592" */
        val displayName: String get() = "$patchline/$rawVersion"
    }

    /**
     * Parses the manifest from the given server JAR path.
     * Returns `null` if the JAR doesn't exist, has no manifest, or is missing required fields.
     */
    fun detect(serverJarPath: Path): HytaleVersionInfo? {
        val jarFile = serverJarPath.toFile()
        if (!jarFile.exists()) return null

        return try {
            JarFile(jarFile).use { jar ->
                val manifest = jar.manifest ?: return null
                val attrs = manifest.mainAttributes

                val patchline = attrs.getValue("Implementation-Patchline") ?: return null
                val version = attrs.getValue("Implementation-Version") ?: return null
                val revision = attrs.getValue("Implementation-Revision-Id") ?: ""

                // Split "2026.02.19-1a311a592" on first '-' to get date and shortHash
                val dashIdx = version.indexOf('-')
                if (dashIdx < 0) return null

                val date = version.substring(0, dashIdx)
                val shortHash = version.substring(dashIdx + 1)

                HytaleVersionInfo(
                    patchline = patchline,
                    date = date,
                    shortHash = shortHash,
                    fullRevision = revision,
                    rawVersion = version,
                )
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Detects the version from the currently configured Hytale install path.
     * Delegates to [HytaleInstallPath.serverJarPath] to find the JAR.
     */
    fun detectFromInstall(): HytaleVersionInfo? {
        val jarPath = HytaleInstallPath.serverJarPath() ?: return null
        return detect(jarPath)
    }

    /**
     * Result of sibling patchline discovery.
     *
     * @param versionInfo the detected version metadata
     * @param installPath the root install path for this patchline
     *   (e.g. `.../install/pre-release/package/game/latest`)
     */
    data class SiblingPatchline(
        val versionInfo: HytaleVersionInfo,
        val installPath: Path,
    )

    /**
     * Discovers other patchline installs alongside the current one.
     *
     * Given an install path like `.../install/release/package/game/latest`,
     * walks up to the `install/` directory and scans for sibling patchline
     * directories (e.g. `pre-release`). Each sibling is validated to have
     * a `Server/HytaleServer.jar` (or Mac .app bundle equivalent) and its
     * manifest is read for version info.
     *
     * @param currentInstallPath the currently configured install root
     * @return list of sibling patchlines (excludes the current one)
     */
    fun discoverSiblingPatchlines(currentInstallPath: Path? = HytaleInstallPath.get()): List<SiblingPatchline> {
        val root = currentInstallPath?.toFile() ?: return emptyList()

        // Walk up to find the "install" ancestor directory.
        // Expected structure: {launcher}/install/{patchline}/package/game/latest
        val installDir = findInstallAncestor(root) ?: return emptyList()

        // The path segments after "install/" tell us the suffix to append to each sibling
        val suffix = root.toRelativeString(installDir)
            .split(File.separatorChar, '/')
            .drop(1) // drop the patchline name itself
            .joinToString(File.separator)

        val currentPatchline = root.toRelativeString(installDir)
            .split(File.separatorChar, '/')[0]

        val siblings = mutableListOf<SiblingPatchline>()
        val patchlineDirs = installDir.listFiles()?.filter { it.isDirectory } ?: return emptyList()

        for (dir in patchlineDirs) {
            if (dir.name == currentPatchline) continue

            val siblingRoot = if (suffix.isNotEmpty()) File(dir, suffix) else dir
            if (!siblingRoot.isDirectory) continue
            if (!HytalePathDetector.isValidInstallPath(siblingRoot.toPath())) continue

            // Resolve the server JAR (handles Mac .app bundle)
            val jarPath = resolveServerJar(siblingRoot)
            val versionInfo = jarPath?.let { detect(it) } ?: continue

            siblings.add(SiblingPatchline(versionInfo, siblingRoot.toPath()))
        }

        return siblings
    }

    /**
     * Walks up from [dir] looking for a parent named "install".
     * Returns the `install/` directory, or null if not found.
     */
    private fun findInstallAncestor(dir: File): File? {
        var current = dir
        // Walk up at most 10 levels to avoid infinite loops on weird paths
        repeat(10) {
            val parent = current.parentFile ?: return null
            if (parent.name == "install") return parent
            current = parent
        }
        return null
    }

    /**
     * Resolves the server JAR path from an install root,
     * handling both direct and Mac .app bundle layouts.
     */
    private fun resolveServerJar(installRoot: File): Path? {
        // Direct structure
        val direct = File(installRoot, "Server/HytaleServer.jar")
        if (direct.exists()) return direct.toPath()

        // Mac .app bundle
        val appBundle = HytalePathDetector.findAppBundle(installRoot.toPath())
        if (appBundle != null) {
            val bundleJar = File(appBundle, "Contents/Resources/Data/Server/HytaleServer.jar")
            if (bundleJar.exists()) return bundleJar.toPath()
        }
        return null
    }
}
