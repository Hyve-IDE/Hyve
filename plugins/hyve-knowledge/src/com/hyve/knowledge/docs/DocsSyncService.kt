// Copyright 2026 Hyve. All rights reserved.
package com.hyve.knowledge.docs

import com.google.gson.Gson
import com.google.gson.JsonParser
import com.google.gson.reflect.TypeToken
import com.hyve.knowledge.extraction.DocsParser
import com.hyve.knowledge.settings.KnowledgeSettings
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Application-level service that syncs HytaleModding docs from GitHub
 * to a local cache, converting MDX to Markdown for offline browsing.
 *
 * Uses incremental updates: on each sync, compares GitHub file tree SHAs
 * against a local manifest to download only changed files.
 */
@Service(Service.Level.APP)
class DocsSyncService {

    private val log = Logger.getInstance(DocsSyncService::class.java)
    private val syncing = AtomicBoolean(false)
    private val gson = Gson()

    private val IMAGE_EXTENSIONS = setOf(".png", ".jpg", ".jpeg", ".gif", ".svg", ".webp")


    /**
     * Run a sync. Returns a result summary.
     *
     * Thread-safe: concurrent calls are rejected with [SyncResult.AlreadyRunning].
     *
     * @param onProgress Optional callback with (current, total, fileName)
     */
    fun sync(
        locale: String? = null,
        onProgress: ((current: Int, total: Int, fileName: String) -> Unit)? = null,
    ): SyncResult {
        if (!syncing.compareAndSet(false, true)) {
            return SyncResult.AlreadyRunning
        }

        try {
            return doSync(locale, onProgress)
        } finally {
            syncing.set(false)
        }
    }

    /** Whether a sync is currently in progress. */
    val isSyncing: Boolean get() = syncing.get()

    private fun doSync(
        locale: String?,
        onProgress: ((Int, Int, String) -> Unit)?,
    ): SyncResult {
        val settings = KnowledgeSettings.getInstance()
        val state = settings.state
        val repoSlug = state.docsGithubRepo
        val branch = state.docsGithubBranch
        val resolvedLocale = locale ?: state.docsLanguage

        val parts = repoSlug.split("/")
        if (parts.size != 2) {
            return SyncResult.Error("Invalid repo format: $repoSlug")
        }
        val owner = parts[0]
        val repo = parts[1]

        val outDir = settings.resolvedOfflineDocsPath(resolvedLocale)
        outDir.mkdirs()

        // Load existing manifest — invalidate if cache format version changed
        val manifestFile = File(settings.resolvedOfflineDocsPath(), "manifest-$resolvedLocale.json")
        val rawManifest = loadManifest(manifestFile)
        val oldManifest = if (rawManifest[MANIFEST_VERSION_KEY] == CACHE_FORMAT_VERSION) {
            rawManifest - MANIFEST_VERSION_KEY
        } else {
            emptyMap() // force full re-download
        }

        // Fetch GitHub file tree
        val tree: List<GitTreeEntry>
        try {
            tree = fetchTreeWithSha(owner, repo, branch)
        } catch (e: Exception) {
            log.warn("Failed to fetch GitHub tree: ${e.message}")
            return if (oldManifest.isNotEmpty()) {
                SyncResult.Error("GitHub rate limit or network error. Using cached docs.")
            } else {
                SyncResult.Error("Failed to fetch docs: ${e.message}")
            }
        }

        // Filter to docs + images + meta.json for the selected locale
        val prefix = "content/docs/$resolvedLocale/"
        val docFiles = tree.filter { it.path.startsWith(prefix) &&
            (it.path.endsWith(".md") || it.path.endsWith(".mdx") || it.path.endsWith("meta.json")
                || IMAGE_EXTENSIONS.any { ext -> it.path.endsWith(ext, ignoreCase = true) })
        }

        if (docFiles.isEmpty()) {
            return SyncResult.Error("No docs found for locale '$resolvedLocale'")
        }

        // Determine what changed
        val newManifest = mutableMapOf<String, String>() // relativePath -> sha
        val toDownload = mutableListOf<GitTreeEntry>()

        for (entry in docFiles) {
            val relativePath = entry.path.removePrefix(prefix)
            newManifest[relativePath] = entry.sha
            if (oldManifest[relativePath] != entry.sha) {
                toDownload.add(entry)
            }
        }

        // Determine deletions
        val currentRelPaths = docFiles.map { it.path.removePrefix(prefix) }.toSet()
        val deletions = oldManifest.keys.filter { it !in currentRelPaths }

        log.info("Docs sync: ${toDownload.size} to download, ${deletions.size} to delete " +
            "(${docFiles.size} total for '$resolvedLocale')")

        if (toDownload.isEmpty() && deletions.isEmpty()) {
            return SyncResult.Success(downloaded = 0, deleted = 0, total = docFiles.size)
        }

        // Download changed files
        var downloaded = 0
        for ((idx, entry) in toDownload.withIndex()) {
            val relativePath = entry.path.removePrefix(prefix)
            onProgress?.invoke(idx + 1, toDownload.size, relativePath.substringAfterLast('/'))

            try {
                if (IMAGE_EXTENSIONS.any { relativePath.endsWith(it, ignoreCase = true) }) {
                    // Binary image — download as bytes, preserve original path
                    val bytes = fetchBinaryContent(owner, repo, branch, entry.path)
                    val outFile = File(outDir, relativePath)
                    outFile.parentFile?.mkdirs()
                    atomicWrite(outFile, bytes)
                } else if (relativePath.endsWith("meta.json")) {
                    // meta.json files are stored as-is
                    val rawContent = DocsParser.fetchRawContent(owner, repo, branch, entry.path)
                    val outFile = File(outDir, relativePath)
                    outFile.parentFile?.mkdirs()
                    atomicWriteText(outFile, rawContent)
                } else {
                    // Convert MDX to MD, rewrite image paths and doc links
                    val rawContent = DocsParser.fetchRawContent(owner, repo, branch, entry.path)
                    val mdContent = MdxConverter.convert(rawContent)
                    val imageBase = "https://raw.githubusercontent.com/$owner/$repo/$branch/public"
                    val withImages = rewriteImagePaths(mdContent, imageBase)
                    val finalContent = rewriteDocLinks(withImages)
                    val mdRelPath = relativePath
                        .removeSuffix(".mdx").removeSuffix(".md") + ".md"
                    val outFile = File(outDir, mdRelPath)
                    outFile.parentFile?.mkdirs()
                    atomicWriteText(outFile, finalContent)
                }
                downloaded++
            } catch (e: Exception) {
                log.warn("Failed to fetch $relativePath: ${e.message}")
            }
        }

        // Delete removed files
        var deleted = 0
        for (relPath in deletions) {
            if (IMAGE_EXTENSIONS.any { relPath.endsWith(it, ignoreCase = true) } || relPath.endsWith("meta.json")) {
                // Images and meta.json keep original extension
                val file = File(outDir, relPath)
                if (file.exists() && file.delete()) deleted++
            } else {
                // Docs get .md rename
                val mdRelPath = relPath.removeSuffix(".mdx").removeSuffix(".md") + ".md"
                val file = File(outDir, mdRelPath)
                if (file.exists() && file.delete()) deleted++
                // Also try the original extension
                val origFile = File(outDir, relPath)
                if (origFile.exists()) origFile.delete()
            }
        }

        // Save manifest with cache format version
        saveManifest(manifestFile, newManifest + (MANIFEST_VERSION_KEY to CACHE_FORMAT_VERSION))

        return SyncResult.Success(downloaded = downloaded, deleted = deleted, total = docFiles.size)
    }

    /**
     * Fetch the GitHub tree API and extract path + sha for each blob.
     */
    private fun fetchTreeWithSha(owner: String, repo: String, branch: String): List<GitTreeEntry> {
        val url = "https://api.github.com/repos/$owner/$repo/git/trees/$branch?recursive=1"
        val request = java.net.http.HttpRequest.newBuilder()
            .uri(java.net.URI.create(url))
            .header("Accept", "application/vnd.github.v3+json")
            .header("User-Agent", "HyveIDE-Docs")
            .timeout(java.time.Duration.ofSeconds(30))
            .GET()
            .build()

        val client = java.net.http.HttpClient.newBuilder()
            .connectTimeout(java.time.Duration.ofSeconds(10))
            .followRedirects(java.net.http.HttpClient.Redirect.NORMAL)
            .build()

        val response = client.send(request, java.net.http.HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() == 403 || response.statusCode() == 429) {
            throw RateLimitException("GitHub API rate limited (${response.statusCode()})")
        }
        if (response.statusCode() != 200) {
            throw RuntimeException("GitHub Trees API returned ${response.statusCode()}")
        }

        val body = response.body()
        val json = JsonParser.parseString(body).asJsonObject
        val treeArray = json.getAsJsonArray("tree") ?: return emptyList()

        return treeArray.mapNotNull { elem ->
            val obj = elem.asJsonObject
            val type = obj.get("type")?.asString ?: return@mapNotNull null
            if (type != "blob") return@mapNotNull null
            val path = obj.get("path")?.asString ?: return@mapNotNull null
            val sha = obj.get("sha")?.asString ?: return@mapNotNull null
            GitTreeEntry(path, sha)
        }
    }

    private fun loadManifest(file: File): Map<String, String> {
        if (!file.exists()) return emptyMap()
        return try {
            val type = object : TypeToken<Map<String, String>>() {}.type
            gson.fromJson(file.readText(Charsets.UTF_8), type) ?: emptyMap()
        } catch (_: Exception) {
            emptyMap()
        }
    }

    private fun saveManifest(file: File, manifest: Map<String, String>) {
        file.parentFile?.mkdirs()
        atomicWriteText(file, gson.toJson(manifest))
    }

    /**
     * Rewrites root-relative image paths (e.g. `/assets/foo.png`) to absolute
     * GitHub raw URLs so the IntelliJ markdown preview can load them.
     */
    private fun rewriteImagePaths(markdown: String, imageBaseUrl: String): String {
        return markdown.replace(Regex("""!\[([^\]]*)\]\((/[^)]+)\)""")) { match ->
            "![${match.groupValues[1]}]($imageBaseUrl${match.groupValues[2]})"
        }
    }

    /**
     * Rewrites relative doc links to include `.md` extension so the JCEF
     * markdown preview resolves them to actual files on disk.
     *
     * Handles: `./path/to/doc` → `./path/to/doc.md`
     *          `../path/to/doc#anchor` → `../path/to/doc.md#anchor`
     * Leaves alone: external URLs, image links, already-extensioned paths, anchor-only links.
     */
    private val docLinkPattern = Regex("""\[([^\]]*)\]\((\.\.?/[^)]+)\)""")

    private fun rewriteDocLinks(markdown: String): String {
        return docLinkPattern.replace(markdown) { match ->
            val text = match.groupValues[1]
            val href = match.groupValues[2]

            // Split off fragment anchor if present
            val fragmentIdx = href.indexOf('#')
            val path = if (fragmentIdx >= 0) href.substring(0, fragmentIdx) else href
            val fragment = if (fragmentIdx >= 0) href.substring(fragmentIdx) else ""

            // Only append .md if path has no file extension
            val lastSegment = path.substringAfterLast('/')
            val needsExtension = !lastSegment.contains('.')

            if (needsExtension) {
                "[$text]($path.md$fragment)"
            } else {
                match.value // leave as-is
            }
        }
    }

    private fun atomicWrite(outFile: File, content: ByteArray) {
        val tmpFile = File(outFile.parentFile, outFile.name + ".tmp")
        try {
            tmpFile.writeBytes(content)
            // On Windows, renameTo fails if target exists — delete first
            if (outFile.exists()) outFile.delete()
            if (!tmpFile.renameTo(outFile)) {
                // Fallback: copy + delete
                tmpFile.copyTo(outFile, overwrite = true)
                tmpFile.delete()
            }
        } catch (e: Exception) {
            tmpFile.delete()
            throw e
        }
    }

    private fun atomicWriteText(outFile: File, text: String) {
        atomicWrite(outFile, text.toByteArray(Charsets.UTF_8))
    }

    private fun fetchBinaryContent(owner: String, repo: String, branch: String, path: String): ByteArray {
        val url = "https://raw.githubusercontent.com/$owner/$repo/$branch/$path"
        val request = java.net.http.HttpRequest.newBuilder()
            .uri(java.net.URI.create(url))
            .header("User-Agent", "HyveIDE-Docs")
            .timeout(java.time.Duration.ofSeconds(20))
            .GET()
            .build()

        val client = java.net.http.HttpClient.newBuilder()
            .connectTimeout(java.time.Duration.ofSeconds(10))
            .followRedirects(java.net.http.HttpClient.Redirect.NORMAL)
            .build()

        val response = client.send(request, java.net.http.HttpResponse.BodyHandlers.ofByteArray())
        if (response.statusCode() != 200) {
            throw RuntimeException("Failed to fetch $path (${response.statusCode()})")
        }
        return response.body()
    }

    private data class GitTreeEntry(val path: String, val sha: String)

    class RateLimitException(message: String) : RuntimeException(message)

    companion object {
        /** Bump when the on-disk format changes (e.g. image URL rewriting) to force re-sync. */
        private const val CACHE_FORMAT_VERSION = "3"
        private const val MANIFEST_VERSION_KEY = "__cache_format_version__"

        fun getInstance(): DocsSyncService =
            ApplicationManager.getApplication().getService(DocsSyncService::class.java)
    }
}

sealed class SyncResult {
    data class Success(val downloaded: Int, val deleted: Int, val total: Int) : SyncResult()
    data class Error(val message: String) : SyncResult()
    data object AlreadyRunning : SyncResult()
}
