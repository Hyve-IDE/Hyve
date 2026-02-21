// Copyright 2026 Hyve. All rights reserved.
package com.hyve.knowledge.extraction

import com.hyve.knowledge.settings.KnowledgeSettings
import com.intellij.openapi.diagnostic.Logger
import java.io.File
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.security.MessageDigest
import java.time.Duration

enum class DocsType(val id: String) {
    GUIDE("guide"),
    REFERENCE("reference"),
    FAQ("faq"),
    EXAMPLE("example");
}

data class DocsChunk(
    val id: String,
    val type: DocsType,
    val title: String,
    val filePath: String,
    val relativePath: String,
    val fileHash: String,
    val content: String,
    val category: String?,
    val description: String?,
    val textForEmbedding: String,
)

data class DocsFrontmatter(
    val title: String?,
    val description: String?,
    val category: String?,
)

data class DocsParseResult(
    val chunks: List<DocsChunk>,
    val errors: List<String>,
)

/**
 * Fetches and parses modding documentation from GitHub.
 *
 * Ported from TypeScript docs-parser.ts. Fetches file tree via GitHub Trees API,
 * downloads raw content, caches locally, extracts frontmatter, strips MDX syntax,
 * and classifies docs by type.
 */
object DocsParser {

    private val log = Logger.getInstance(DocsParser::class.java)

    private val http: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build()

    // ── Type classification ─────────────────────────────────────

    fun classifyType(relativePath: String, content: String): DocsType {
        val lower = relativePath.lowercase()
        return when {
            "reference" in lower || "api" in lower -> DocsType.REFERENCE
            "faq" in lower || "troubleshoot" in lower -> DocsType.FAQ
            isExampleHeavy(relativePath, content) -> DocsType.EXAMPLE
            else -> DocsType.GUIDE
        }
    }

    private fun isExampleHeavy(relativePath: String, content: String): Boolean {
        val lower = relativePath.lowercase()
        if ("example" in lower || "sample" in lower || "tutorial" in lower) return true
        val codeBlockCount = "```".toRegex().findAll(content).count() / 2
        return codeBlockCount >= 6
    }

    // ── Frontmatter extraction ──────────────────────────────────

    fun extractFrontmatter(content: String): Pair<DocsFrontmatter, String> {
        val trimmed = content.trimStart()
        if (!trimmed.startsWith("---")) {
            return DocsFrontmatter(null, null, null) to content
        }

        val endIdx = trimmed.indexOf("---", 3)
        if (endIdx == -1) {
            return DocsFrontmatter(null, null, null) to content
        }

        val yaml = trimmed.substring(3, endIdx).trim()
        val body = trimmed.substring(endIdx + 3).trimStart()

        val title = extractYamlField(yaml, "title")
        val description = extractYamlField(yaml, "description")
        val category = extractYamlField(yaml, "category")

        return DocsFrontmatter(title, description, category) to body
    }

    private fun extractYamlField(yaml: String, field: String): String? {
        val regex = Regex("""^$field:\s*["']?(.+?)["']?\s*$""", RegexOption.MULTILINE)
        return regex.find(yaml)?.groupValues?.get(1)?.trim()
    }

    // ── MDX stripping ───────────────────────────────────────────

    fun stripMdx(content: String): String {
        var result = content

        // Remove import statements
        result = result.replace(Regex("""^import\s+.+from\s+['"].+['"]\s*;?\s*$""", RegexOption.MULTILINE), "")

        // Remove self-closing JSX tags: <Component ... />
        result = result.replace(Regex("""<[A-Z][A-Za-z0-9.]*[^>]*/>\s*"""), "")

        // Remove JSX component open/close tags but keep inner content
        result = result.replace(Regex("""<([A-Z][A-Za-z0-9.]*)[^>]*>"""), "")
        result = result.replace(Regex("""</([A-Z][A-Za-z0-9.]*)>"""), "")

        // Collapse multiple blank lines
        result = result.replace(Regex("""\n{3,}"""), "\n\n")

        return result.trim()
    }

    // ── Embedding text ──────────────────────────────────────────

    fun buildEmbeddingText(chunk: DocsChunk): String {
        return buildString {
            append("Hytale Modding Docs: ${chunk.title}")
            append("\nType: ${chunk.type.id}")
            chunk.category?.let { append("\nCategory: $it") }
            chunk.description?.let { append("\nDescription: $it") }
            append("\nPath: ${chunk.relativePath}")
            append("\n")
            append(keywordsForType(chunk.type))
            append("\n\n")
            append(chunk.content)
        }
    }

    private fun keywordsForType(type: DocsType): String = when (type) {
        DocsType.GUIDE -> "Keywords: tutorial how to guide walkthrough"
        DocsType.REFERENCE -> "Keywords: API reference documentation method class"
        DocsType.FAQ -> "Keywords: FAQ troubleshoot common problem question answer"
        DocsType.EXAMPLE -> "Keywords: example sample code snippet demo"
    }

    // ── GitHub fetching ─────────────────────────────────────────

    fun fetchTree(owner: String, repo: String, branch: String): List<String> {
        val url = "https://api.github.com/repos/$owner/$repo/git/trees/$branch?recursive=1"
        val request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("Accept", "application/vnd.github.v3+json")
            .header("User-Agent", "HyveIDE-Knowledge")
            .timeout(Duration.ofSeconds(30))
            .GET()
            .build()

        val response = http.send(request, HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() != 200) {
            throw RuntimeException("GitHub Trees API returned ${response.statusCode()}: ${response.body().take(200)}")
        }

        val body = response.body()
        // Extract "path" values from the tree JSON without a full JSON parser
        val pathRegex = Regex(""""path"\s*:\s*"([^"]+)"""")
        return pathRegex.findAll(body)
            .map { it.groupValues[1] }
            .filter { it.endsWith(".md") || it.endsWith(".mdx") }
            .toList()
    }

    fun fetchRawContent(owner: String, repo: String, branch: String, path: String): String {
        val url = "https://raw.githubusercontent.com/$owner/$repo/$branch/$path"
        val request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("User-Agent", "HyveIDE-Knowledge")
            .timeout(Duration.ofSeconds(20))
            .GET()
            .build()

        val response = http.send(request, HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() != 200) {
            throw RuntimeException("Failed to fetch $path (${response.statusCode()})")
        }
        return response.body()
    }

    // ── Local cache ─────────────────────────────────────────────

    fun cacheDir(): File {
        val home = System.getProperty("user.home")
        return File(home, ".hyve/knowledge/docs")
    }

    fun cachedFile(relativePath: String): File {
        return File(cacheDir(), relativePath.replace('/', File.separatorChar))
    }

    fun readCache(relativePath: String): String? {
        val file = cachedFile(relativePath)
        return if (file.exists()) file.readText(Charsets.UTF_8) else null
    }

    fun writeCache(relativePath: String, content: String) {
        val file = cachedFile(relativePath)
        file.parentFile?.mkdirs()
        file.writeText(content, Charsets.UTF_8)
    }

    // ── Main parse entry point ──────────────────────────────────

    fun parseDocs(
        onProgress: ((current: Int, total: Int, file: String) -> Unit)? = null,
    ): DocsParseResult {
        val settings = KnowledgeSettings.getInstance().state
        val repoSlug = settings.docsGithubRepo
        val branch = settings.docsGithubBranch
        val locale = settings.docsLanguage

        val parts = repoSlug.split("/")
        if (parts.size != 2) {
            return DocsParseResult(emptyList(), listOf("Invalid docsGithubRepo format: $repoSlug"))
        }
        val owner = parts[0]
        val repo = parts[1]

        val chunks = mutableListOf<DocsChunk>()
        val errors = mutableListOf<String>()

        val allPaths: List<String>
        try {
            allPaths = fetchTree(owner, repo, branch)
        } catch (e: Exception) {
            return DocsParseResult(emptyList(), listOf("Failed to fetch GitHub file tree: ${e.message}"))
        }

        val docsPaths = allPaths.filter { path ->
            path.startsWith("content/docs/$locale/")
                && !path.startsWith("content/docs/$locale/contributing/")
                && path != "content/docs/$locale/index.mdx"
                && path != "content/docs/$locale/meta.json"
        }
        log.info("Found ${docsPaths.size} docs files for locale '$locale'")

        for ((idx, path) in docsPaths.withIndex()) {
            onProgress?.invoke(idx + 1, docsPaths.size, path.substringAfterLast('/'))
            try {
                val rawContent = try {
                    val fresh = fetchRawContent(owner, repo, branch, path)
                    writeCache(path, fresh)
                    fresh
                } catch (e: Exception) {
                    log.warn("Fetch failed for $path, trying cache: ${e.message}")
                    readCache(path) ?: throw e
                }

                if (rawContent.isBlank()) continue

                val (frontmatter, body) = extractFrontmatter(rawContent)
                val strippedBody = stripMdx(body)
                val hash = computeSHA256(rawContent.toByteArray())
                val relativePath = path.removePrefix("content/docs/$locale/")
                val type = classifyType(relativePath, strippedBody)
                val title = frontmatter.title ?: relativePath.substringAfterLast('/').removeSuffix(".md").removeSuffix(".mdx")
                    .replace('-', ' ').replaceFirstChar { it.uppercase() }
                val category = frontmatter.category ?: relativePath.split('/').firstOrNull()

                val chunk = DocsChunk(
                    id = "docs:$relativePath",
                    type = type,
                    title = title,
                    filePath = cachedFile(path).absolutePath,
                    relativePath = relativePath,
                    fileHash = hash,
                    content = strippedBody,
                    category = category,
                    description = frontmatter.description,
                    textForEmbedding = "",
                )
                chunks.add(chunk.copy(textForEmbedding = buildEmbeddingText(chunk)))
            } catch (e: Exception) {
                errors.add("$path: ${e.message}")
            }
        }

        return DocsParseResult(chunks, errors)
    }

    private fun computeSHA256(data: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(data).joinToString("") { "%02x".format(it) }
    }
}
