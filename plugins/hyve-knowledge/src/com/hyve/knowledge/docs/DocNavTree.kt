// Copyright 2026 Hyve. All rights reserved.
package com.hyve.knowledge.docs

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.File

/**
 * Navigation tree model for the Hytale Documentation tool window.
 *
 * Built from `meta.json` files in the HytaleModding/site repo.
 * Each meta.json defines:
 * - `title`: Display name for the directory
 * - `icon`: Icon hint (unused in v1, reserved)
 * - `pages`: Ordered list of entries — filenames, directory names,
 *   `"..."` for remaining items, and `"---Section Name---"` for separators
 */
sealed class DocNavNode {

    /** A section separator (non-clickable heading). */
    data class Separator(val title: String) : DocNavNode()

    /** A directory/category with children. */
    data class Directory(
        val title: String,
        val children: List<DocNavNode>,
    ) : DocNavNode()

    /** A single document leaf node. */
    data class Document(
        val title: String,
        val relativePath: String,
        val filePath: File,
    ) : DocNavNode()
}

/**
 * Builds a [DocNavNode.Directory] tree from the cached offline docs directory.
 *
 * Reads `meta.json` files to determine ordering and section separators.
 * Falls back to alphabetical listing when no meta.json is present.
 */
object DocNavTreeBuilder {

    private val gson = Gson()
    private val separatorPattern = Regex("""^---(.+)---$""")

    /**
     * Build the full navigation tree from a locale directory.
     *
     * @param localeDir The root directory for a locale (e.g. `~/.hyve/knowledge/docs-offline/en/`)
     * @return Root directory node containing the full tree, or null if dir doesn't exist
     */
    fun build(localeDir: File): DocNavNode.Directory? {
        if (!localeDir.isDirectory) return null
        val tree = buildDirectory(localeDir, "Hytale Documentation")
        return postProcess(tree)
    }

    /**
     * Post-processing fixups for upstream meta.json quirks:
     * - Moves "NPC Meta" doc into the "NPC Documentation" directory
     * - Deduplicates "Prefabs" — keeps only the first occurrence
     */
    private fun postProcess(root: DocNavNode.Directory): DocNavNode.Directory {
        var result = reparentDoc(root, docTitle = "NPC Meta", intoDirTitle = "NPC Documentation")
        result = deduplicateDoc(result, docTitle = "Prefabs")
        return result
    }

    /** Move a document node from its current parent into a sibling directory. */
    private fun reparentDoc(root: DocNavNode.Directory, docTitle: String, intoDirTitle: String): DocNavNode.Directory {
        return transformChildren(root) { children ->
            val docNode = children.filterIsInstance<DocNavNode.Document>().find {
                it.title.equals(docTitle, ignoreCase = true)
            } ?: return@transformChildren children

            val dirIdx = children.indexOfFirst {
                it is DocNavNode.Directory && it.title.equals(intoDirTitle, ignoreCase = true)
            }
            if (dirIdx < 0) return@transformChildren children

            val dir = children[dirIdx] as DocNavNode.Directory
            val updatedDir = dir.copy(children = dir.children + docNode)
            children.filter { it !== docNode }.map { if (it === dir) updatedDir else it }
        }
    }

    /** Remove duplicate document nodes, keeping only the last occurrence in a depth-first walk. */
    private fun deduplicateDoc(root: DocNavNode.Directory, docTitle: String): DocNavNode.Directory {
        // Count occurrences first, then keep only the last one
        var count = 0
        countDocs(root, docTitle) { count++ }
        if (count <= 1) return root

        var seen = 0
        return filterTree(root) { node ->
            if (node is DocNavNode.Document && node.title.equals(docTitle, ignoreCase = true)) {
                seen++
                seen == count // keep only the last occurrence
            } else {
                true
            }
        }
    }

    private fun countDocs(dir: DocNavNode.Directory, docTitle: String, onFound: () -> Unit) {
        for (child in dir.children) {
            when (child) {
                is DocNavNode.Document -> if (child.title.equals(docTitle, ignoreCase = true)) onFound()
                is DocNavNode.Directory -> countDocs(child, docTitle, onFound)
                is DocNavNode.Separator -> {}
            }
        }
    }

    /** Apply a transform to the children of every Directory in the tree. */
    private fun transformChildren(
        dir: DocNavNode.Directory,
        transform: (List<DocNavNode>) -> List<DocNavNode>,
    ): DocNavNode.Directory {
        val newChildren = transform(dir.children).map { child ->
            if (child is DocNavNode.Directory) transformChildren(child, transform) else child
        }
        return dir.copy(children = newChildren)
    }

    /** Filter nodes from the tree. Returns false from predicate to remove a node. */
    private fun filterTree(
        dir: DocNavNode.Directory,
        predicate: (DocNavNode) -> Boolean,
    ): DocNavNode.Directory {
        val newChildren = dir.children.filter(predicate).map { child ->
            if (child is DocNavNode.Directory) filterTree(child, predicate) else child
        }
        return dir.copy(children = newChildren)
    }

    private fun buildDirectory(dir: File, fallbackTitle: String): DocNavNode.Directory {
        val metaFile = File(dir, "meta.json")
        val meta = if (metaFile.exists()) parseMeta(metaFile) else null

        val title = meta?.title?.takeIf { !it.startsWith("{") } ?: fallbackTitle
        val pages = meta?.pages

        val children = if (pages != null) {
            buildFromPages(dir, pages)
        } else {
            buildAlphabetical(dir)
        }

        return DocNavNode.Directory(title, children)
    }

    private fun buildFromPages(dir: File, pages: List<String>): List<DocNavNode> {
        val result = mutableListOf<DocNavNode>()
        val listed = mutableSetOf<String>()

        for (entry in pages) {
            // Section separator: "---Name---"
            val sepMatch = separatorPattern.matchEntire(entry)
            if (sepMatch != null) {
                val sepTitle = sepMatch.groupValues[1]
                // Skip unresolved i18n tokens (e.g. "{meta.established_information.separator}")
                if (!sepTitle.startsWith("{")) {
                    result.add(DocNavNode.Separator(sepTitle))
                }
                continue
            }

            // Wildcard: "..." means add remaining unlisted items alphabetically
            if (entry == "...") {
                val remaining = dir.listFiles()
                    ?.filter { it.name !in listed && it.name != "meta.json" && it.name != "nav-tree.json" }
                    ?.filter { it.isDirectory || it.extension == "md" }
                    ?.sortedBy { it.nameWithoutExtension }
                    ?: emptyList()

                for (file in remaining) {
                    listed.add(file.name)
                    result.add(buildNode(file))
                }
                continue
            }

            // Prefixed with "...": expand a directory inline
            if (entry.startsWith("...")) {
                val dirName = entry.removePrefix("...")
                val subDir = File(dir, dirName)
                if (subDir.isDirectory) {
                    listed.add(dirName)
                    val subTree = buildDirectory(subDir, prettifyName(dirName))
                    // Inline the children rather than wrapping in a directory node
                    result.addAll(subTree.children)
                }
                continue
            }

            // Specific file reference (with or without extension)
            val name = entry.removeSuffix(".mdx").removeSuffix(".md")
            val mdFile = File(dir, "$name.md")
            val subDir = File(dir, name)

            when {
                mdFile.exists() -> {
                    listed.add(mdFile.name)
                    listed.add(entry)
                    result.add(buildDocNode(mdFile))
                }
                subDir.isDirectory -> {
                    listed.add(name)
                    listed.add(entry)
                    result.add(buildDirectory(subDir, prettifyName(name)))
                }
            }
        }

        return result
    }

    private fun buildAlphabetical(dir: File): List<DocNavNode> {
        val files = dir.listFiles()
            ?.filter { it.name != "meta.json" && it.name != "nav-tree.json" }
            ?.filter { it.isDirectory || it.extension == "md" }
            ?.sortedBy { it.nameWithoutExtension }
            ?: emptyList()

        return files.map { buildNode(it) }
    }

    private fun buildNode(file: File): DocNavNode {
        return if (file.isDirectory) {
            buildDirectory(file, prettifyName(file.name))
        } else {
            buildDocNode(file)
        }
    }

    private fun buildDocNode(file: File): DocNavNode.Document {
        val title = extractTitle(file) ?: prettifyName(file.nameWithoutExtension)
        val relativePath = file.name // Relative within the tree; full path stored in filePath
        return DocNavNode.Document(title, relativePath, file)
    }

    /**
     * Extract the title from a markdown file's frontmatter.
     */
    private fun extractTitle(file: File): String? {
        try {
            val lines = file.readLines(Charsets.UTF_8)
            if (lines.isEmpty() || lines[0].trim() != "---") return null
            for (i in 1 until lines.size) {
                val line = lines[i].trim()
                if (line == "---") break
                val match = Regex("""^title:\s*["']?(.+?)["']?\s*$""").matchEntire(line)
                if (match != null) return match.groupValues[1]
            }
        } catch (_: Exception) {}
        return null
    }

    private fun prettifyName(name: String): String {
        // Remove leading number prefix like "00-", "01-"
        val stripped = name.replace(Regex("""^\d+-"""), "")
        return stripped
            .replace('-', ' ')
            .replace('_', ' ')
            .split(' ')
            .joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }
    }

    private fun parseMeta(file: File): MetaJson? {
        return try {
            val json = JsonParser.parseString(file.readText(Charsets.UTF_8)).asJsonObject
            val title = json.get("title")?.asString
            val icon = json.get("icon")?.asString
            val pages = json.getAsJsonArray("pages")?.map { it.asString }
            MetaJson(title, icon, pages)
        } catch (_: Exception) {
            null
        }
    }

    private data class MetaJson(
        val title: String?,
        val icon: String?,
        val pages: List<String>?,
    )
}
