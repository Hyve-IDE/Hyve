// Copyright 2026 Hyve. All rights reserved.
package com.hyve.knowledge.extraction

import com.hyve.knowledge.core.extraction.GameDataChunk
import com.hyve.knowledge.core.extraction.GameDataType
import com.intellij.openapi.diagnostic.Logger
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.util.zip.ZipInputStream
import kotlin.io.path.exists

/**
 * Parses game data entries from Assets.zip.
 *
 * Streams the ZIP to keep memory constant regardless of archive size.
 * Classifies each JSON file into one of the 24 GameDataType categories
 * via ordered regex patterns ported from the TypeScript PATH_TYPE_MAP.
 * Handles JSONC (comments, BOM, trailing commas) with a 4-strategy fallback chain.
 */
object GameDataParser {

    private val log = Logger.getInstance(GameDataParser::class.java)

    data class ParseResult(
        val chunks: List<GameDataChunk>,
        val errors: List<String>,
    )

    private val lenientJson = Json {
        isLenient = true
        ignoreUnknownKeys = true
    }

    // ── Ignored file patterns ────────────────────────────────────

    private val IGNORED_PATTERNS = listOf(
        Regex("BranchInfo\\.json$"),
        Regex("Mountain_GoblinLair/Entry\\.node\\.json$"),
        Regex("Nodes_Cave_Volcanic/Node_01\\.node\\.json$"),
        Regex("Mine Info\\.json$"),
        Regex("Mineshaft/Mines\\.json$"),
        Regex("!Custom\\.[^/]+\\.json$"),
    )

    // ── Path classification rules ────────────────────────────────
    // Order matters: first match wins.

    private data class PathRule(val pattern: Regex, val type: GameDataType)

    private val PATH_RULES = listOf(
        // Items and blocks
        PathRule(Regex("Server/Item/Items/"), GameDataType.ITEM),
        PathRule(Regex("Server/Item/Recipes/"), GameDataType.RECIPE),
        PathRule(Regex("Server/Item/Block/"), GameDataType.BLOCK),
        PathRule(Regex("Server/Item/Interactions/"), GameDataType.INTERACTION),
        PathRule(Regex("Server/Item/RootInteractions/"), GameDataType.INTERACTION),
        PathRule(Regex("Server/Item/Groups/"), GameDataType.ITEM),
        PathRule(Regex("Server/Item/Category/"), GameDataType.ITEM),
        PathRule(Regex("Server/Item/ResourceTypes/"), GameDataType.ITEM),
        PathRule(Regex("Server/Item/Qualities/"), GameDataType.ITEM),
        // NPCs
        PathRule(Regex("Server/Drops/"), GameDataType.DROP),
        PathRule(Regex("Server/NPC/Roles/"), GameDataType.NPC),
        PathRule(Regex("Server/NPC/Groups/"), GameDataType.NPC_GROUP),
        PathRule(Regex("Server/NPC/DecisionMaking/"), GameDataType.NPC_AI),
        PathRule(Regex("Server/NPC/Flocks/"), GameDataType.NPC),
        PathRule(Regex("Server/NPC/Attitude/"), GameDataType.NPC),
        PathRule(Regex("Server/Entity/"), GameDataType.ENTITY),
        PathRule(Regex("Server/Projectiles/"), GameDataType.PROJECTILE),
        // Gameplay
        PathRule(Regex("Server/Farming/"), GameDataType.FARMING),
        PathRule(Regex("Server/BarterShops/"), GameDataType.SHOP),
        PathRule(Regex("Server/Environments/"), GameDataType.ENVIRONMENT),
        PathRule(Regex("Server/Weathers/"), GameDataType.WEATHER),
        PathRule(Regex("Server/Camera/"), GameDataType.CAMERA),
        PathRule(Regex("Server/Objective/"), GameDataType.OBJECTIVE),
        PathRule(Regex("Server/GameplayConfigs/"), GameDataType.GAMEPLAY),
        // World gen — specific paths before catch-all
        PathRule(Regex("Server/HytaleGenerator/Biomes/"), GameDataType.BIOME),
        PathRule(Regex("Server/HytaleGenerator/Assignments/"), GameDataType.WORLDGEN),
        PathRule(Regex("Server/HytaleGenerator/"), GameDataType.WORLDGEN),
        // World — layered rules, specific before general
        PathRule(Regex("Server/World/.+/Zones/.+/Layers/"), GameDataType.TERRAIN_LAYER),
        PathRule(Regex("Server/World/.+/Zones/.+/Cave/"), GameDataType.CAVE),
        PathRule(Regex("Server/World/.+/Zones/Layers/"), GameDataType.TERRAIN_LAYER),
        PathRule(Regex("Server/World/.+/Zones/Masks/"), GameDataType.ZONE),
        PathRule(Regex("Server/World/.+/Zones/Noise/"), GameDataType.WORLDGEN),
        PathRule(Regex("Server/World/.+/Zones/.+/(Tile|Custom|Zone)\\."), GameDataType.ZONE),
        PathRule(Regex("Server/World/.+/Zones/"), GameDataType.ZONE),
        // Prefabs and localization
        PathRule(Regex("Server/Prefabs/"), GameDataType.PREFAB),
        PathRule(Regex("Common/Languages/"), GameDataType.LOCALIZATION),
    )

    // ── Public API ───────────────────────────────────────────────

    /**
     * Parse all game data entries from the Assets.zip at the given path.
     * Streams the ZIP to keep memory constant.
     *
     * @param zipPath Path to Assets.zip
     * @param onProgress Optional progress callback with (current, total, entryName)
     */
    fun parseAssetsZip(
        zipPath: Path,
        onProgress: ((current: Int, total: Int, file: String) -> Unit)? = null,
    ): ParseResult {
        val chunks = mutableListOf<GameDataChunk>()
        val errors = mutableListOf<String>()

        if (!zipPath.exists()) {
            return ParseResult(emptyList(), listOf("Assets.zip not found at: $zipPath"))
        }

        // First pass: count entries for progress reporting
        val totalEntries = countJsonEntries(zipPath)

        // Second pass: parse entries
        var current = 0
        Files.newInputStream(zipPath).buffered().use { fileStream ->
            ZipInputStream(fileStream).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    val name = entry.name
                    if (!entry.isDirectory && name.endsWith(".json")) {
                        current++
                        onProgress?.invoke(current, totalEntries, name)

                        if (!shouldIgnore(name)) {
                            val type = classifyPath(name)
                            if (type != null) {
                                try {
                                    val bytes = zip.readBytes()
                                    val chunk = parseEntry(name, type, bytes)
                                    if (chunk != null) chunks.add(chunk)
                                } catch (e: Exception) {
                                    val msg = "Failed to parse $name: ${e.message}"
                                    log.debug(msg)
                                    errors.add(msg)
                                }
                            }
                        }
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }
        }

        log.info("GameDataParser: parsed ${chunks.size} chunks, ${errors.size} errors from $zipPath")
        return ParseResult(chunks, errors)
    }

    // Note: callers resolve the zip path via HytaleInstallPath.assetsZipPath() themselves.

    // ── Path classification ──────────────────────────────────────

    private fun shouldIgnore(path: String): Boolean =
        IGNORED_PATTERNS.any { it.containsMatchIn(path) }

    private fun classifyPath(path: String): GameDataType? =
        PATH_RULES.firstOrNull { it.pattern.containsMatchIn(path) }?.type

    // ── Entry parsing ────────────────────────────────────────────

    private fun parseEntry(entryPath: String, type: GameDataType, bytes: ByteArray): GameDataChunk? {
        val hash = sha256(bytes)
        val raw = decodeContent(bytes)
        val jsonObj = parseJsonWithFallbacks(raw, entryPath) ?: return null

        val name = deriveName(entryPath, type, jsonObj)
        val id = "gamedata:${entryPath.replace('/', ':')}"
        val tags = extractTags(type, jsonObj)
        val relatedIds = extractRelatedIds(jsonObj)
        val text = GameDataTextBuilder.buildText(
            GameDataChunk(id, type, name, entryPath, hash, raw, tags, relatedIds, ""),
            jsonObj,
        )

        return GameDataChunk(
            id = id,
            type = type,
            name = name,
            filePath = entryPath,
            fileHash = hash,
            rawJson = raw,
            tags = tags,
            relatedIds = relatedIds,
            textForEmbedding = text,
        )
    }

    private fun decodeContent(bytes: ByteArray): String {
        // Strip UTF-8 BOM if present
        val start = if (bytes.size >= 3 && bytes[0] == 0xEF.toByte() &&
            bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()
        ) 3 else 0
        return String(bytes, start, bytes.size - start, Charsets.UTF_8)
    }

    // ── JSONC parsing with 4-strategy fallback ───────────────────

    private fun parseJsonWithFallbacks(raw: String, path: String): JsonObject? {
        // Strategy 1: strip comments, parse normally
        tryParse(stripComments(raw))?.let { return it }

        // Strategy 2: fix unclosed /* comments (add missing */)
        val fixedComments = fixUnclosedBlockComments(stripComments(raw))
        tryParse(fixedComments)?.let { return it }

        // Strategy 3: strip invalid bytes before first { or [
        val stripped = stripInvalidStart(raw)
        tryParse(stripComments(stripped))?.let { return it }

        // Strategy 4: JSON Lines (split on newline-separated objects)
        val firstLine = tryJsonLines(raw)
        if (firstLine != null) return firstLine

        log.debug("All parse strategies failed for: $path")
        return null
    }

    private fun tryParse(text: String): JsonObject? {
        return try {
            val element = lenientJson.parseToJsonElement(text.trim())
            element.jsonObject
        } catch (_: Exception) {
            null
        }
    }

    private fun stripComments(text: String): String {
        val sb = StringBuilder(text.length)
        var i = 0
        var inString = false
        var escape = false

        while (i < text.length) {
            val c = text[i]
            when {
                escape -> {
                    sb.append(c)
                    escape = false
                    i++
                }
                inString && c == '\\' -> {
                    sb.append(c)
                    escape = true
                    i++
                }
                inString -> {
                    if (c == '"') inString = false
                    sb.append(c)
                    i++
                }
                c == '"' -> {
                    inString = true
                    sb.append(c)
                    i++
                }
                c == '/' && i + 1 < text.length && text[i + 1] == '/' -> {
                    // Line comment: skip to end of line
                    i += 2
                    while (i < text.length && text[i] != '\n') i++
                }
                c == '/' && i + 1 < text.length && text[i + 1] == '*' -> {
                    // Block comment: skip to */
                    i += 2
                    while (i + 1 < text.length && !(text[i] == '*' && text[i + 1] == '/')) i++
                    i += 2 // skip closing */
                }
                else -> {
                    sb.append(c)
                    i++
                }
            }
        }

        return sb.toString().replace(Regex(",\\s*([}\\]])"), "$1")
    }

    private fun fixUnclosedBlockComments(text: String): String {
        val opens = Regex("/\\*").findAll(text).count()
        val closes = Regex("\\*/").findAll(text).count()
        val count = opens - closes
        return if (count > 0) text + " */".repeat(count) else text
    }

    private fun stripInvalidStart(text: String): String {
        val firstBrace = text.indexOf('{').takeIf { it >= 0 } ?: Int.MAX_VALUE
        val firstBracket = text.indexOf('[').takeIf { it >= 0 } ?: Int.MAX_VALUE
        val start = minOf(firstBrace, firstBracket)
        return if (start == Int.MAX_VALUE) text else text.substring(start)
    }

    private fun tryJsonLines(text: String): JsonObject? {
        val parts = text.trim().split(Regex("}\\s*\\{"))
        if (parts.size <= 1) return null
        val first = parts[0].trim().let { if (it.startsWith("{")) "$it}" else it }
        return tryParse(first)
    }

    // ── Metadata extraction ──────────────────────────────────────

    private fun deriveName(path: String, type: GameDataType, obj: JsonObject): String {
        val fileStem = path.substringAfterLast('/').removeSuffix(".json")
            .removeSuffix(".node").removeSuffix(".prefab")

        return when (type) {
            // Prefabs: always use filename stem (never return generic "PREFAB")
            GameDataType.PREFAB -> fileStem

            // Items, recipes, drops: filename IS the ID (e.g. Furniture_Human_Ruins_Torch)
            GameDataType.ITEM, GameDataType.RECIPE, GameDataType.DROP -> fileStem

            // NPCs: skip type/Type fields (always "Generic"/"Variant"), try name/Name/id/Id
            GameDataType.NPC -> {
                for (key in listOf("name", "Name", "id", "Id", "key", "Key")) {
                    val str = (obj[key] as? JsonPrimitive)?.content
                    if (!str.isNullOrBlank()) return str
                }
                fileStem
            }

            // Others: try common name fields, fall back to filename
            else -> {
                for (key in listOf("name", "Name", "id", "Id", "key", "Key")) {
                    val str = (obj[key] as? JsonPrimitive)?.content
                    if (!str.isNullOrBlank()) return str
                }
                fileStem
            }
        }
    }

    private fun extractTags(type: GameDataType, obj: JsonObject): List<String> {
        val tags = mutableListOf(type.id)

        // Handle Tags as flat array or object with sub-arrays
        val tagsEl = obj["Tags"] ?: obj["tags"]
        when (tagsEl) {
            is JsonArray -> {
                tagsEl.forEach { el ->
                    val str = (el as? JsonPrimitive)?.content
                    if (!str.isNullOrBlank()) tags.add(str)
                }
            }
            is JsonObject -> {
                // Hytale object form: {Type:["Furniture"], Family:["Human"]}
                for ((category, values) in tagsEl) {
                    when (values) {
                        is JsonArray -> values.forEach { v ->
                            (v as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() }?.let {
                                tags.add("$category:$it")
                            }
                        }
                        is JsonPrimitive -> values.content.takeIf { it.isNotBlank() }?.let {
                            tags.add("$category:$it")
                        }
                        else -> {}
                    }
                }
            }
            else -> {}
        }

        // Extract Categories array
        val catsEl = obj["Categories"] ?: obj["categories"]
        when (catsEl) {
            is JsonArray -> catsEl.forEach { el ->
                (el as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() }?.let { tags.add(it) }
            }
            else -> {}
        }

        // Legacy single category field
        val category = (obj["category"] ?: obj["Category"]) as? JsonPrimitive
        category?.content?.takeIf { it.isNotBlank() }?.let { tags.add(it) }

        return tags
    }

    private fun extractRelatedIds(obj: JsonObject): List<String> {
        val ids = mutableListOf<String>()
        for (key in listOf("Items", "items", "Drops", "drops", "Recipes", "recipes",
                           "Requires", "requires", "Rewards", "rewards", "Loot", "loot",
                           "Input", "Output", "PrimaryOutput")) {
            val el = obj[key] ?: continue
            when (el) {
                is JsonArray -> el.forEach { item ->
                    when (item) {
                        is JsonPrimitive -> item.content.takeIf { it.isNotBlank() }?.let { ids.add(it) }
                        is JsonObject -> {
                            // Handle object-form items: {ItemId: "..."} or {item: "..."}
                            val itemId = (item["ItemId"] as? JsonPrimitive)?.content
                                ?: (item["item"] as? JsonPrimitive)?.content
                                ?: (item["id"] as? JsonPrimitive)?.content
                                ?: (item["Id"] as? JsonPrimitive)?.content
                            itemId?.takeIf { it.isNotBlank() }?.let { ids.add(it) }
                        }
                        else -> {}
                    }
                }
                is JsonObject -> {
                    // PrimaryOutput as single object: {ItemId: "..."}
                    val itemId = (el["ItemId"] as? JsonPrimitive)?.content
                        ?: (el["item"] as? JsonPrimitive)?.content
                    itemId?.takeIf { it.isNotBlank() }?.let { ids.add(it) }
                }
                else -> {}
            }
        }
        return ids
    }

    // ── Utilities ────────────────────────────────────────────────

    private fun sha256(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(bytes)
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun countJsonEntries(zipPath: Path): Int {
        var count = 0
        Files.newInputStream(zipPath).buffered().use { fs ->
            ZipInputStream(fs).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    if (!entry.isDirectory && entry.name.endsWith(".json")) count++
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }
        }
        return count
    }
}
