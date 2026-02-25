// Copyright 2026 Hyve. All rights reserved.
package com.hyve.knowledge.docs

import com.hyve.knowledge.settings.KnowledgeSettings
import java.io.File

/**
 * Resolves RAG search result paths to offline documentation files.
 *
 * Maps a `SearchResult.relativePath` (e.g. `guides/plugin/creating-commands.mdx`)
 * to the cached offline doc file (e.g. `~/.hyve/knowledge/docs-offline/en/guides/plugin/creating-commands.md`).
 */
object OfflineDocResolver {

    /**
     * Resolve a docs-corpus relative path to its offline markdown file.
     *
     * @param relativePath The relative path from the search result (e.g. `guides/plugin/creating-commands.mdx`)
     * @return The local File if it exists, or null
     */
    fun resolve(relativePath: String?): File? {
        if (relativePath.isNullOrBlank()) return null

        val settings = KnowledgeSettings.getInstance()
        val locale = settings.state.docsLanguage

        val mdRelPath = relativePath
            .removeSuffix(".mdx")
            .removeSuffix(".md") + ".md"

        // Try current locale first
        val file = File(settings.resolvedOfflineDocsPath(locale), mdRelPath)
        if (file.exists()) return file

        // Fall back to English
        if (locale != "en") {
            val enFile = File(settings.resolvedOfflineDocsPath("en"), mdRelPath)
            if (enFile.exists()) return enFile
        }

        return null
    }
}
