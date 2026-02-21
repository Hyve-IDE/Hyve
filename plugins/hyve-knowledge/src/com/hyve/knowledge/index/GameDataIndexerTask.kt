// Copyright 2026 Hyve. All rights reserved.
package com.hyve.knowledge.index

import com.hyve.common.settings.HytaleInstallPath
import com.hyve.knowledge.bridge.KnowledgeDatabaseFactory
import com.hyve.knowledge.bridge.toConfig
import com.hyve.knowledge.core.db.Corpus
import com.hyve.knowledge.core.index.CorpusIndexManager
import com.hyve.knowledge.core.index.HnswIndex
import com.hyve.knowledge.core.db.KnowledgeDatabase
import com.hyve.knowledge.settings.KnowledgeSettings
import com.hyve.knowledge.core.extraction.GameDataChunk
import com.hyve.knowledge.extraction.GameDataParser
import com.hyve.knowledge.extraction.GameDataTextBuilder
import com.hyve.knowledge.core.extraction.GameDataType
import com.hyve.knowledge.core.search.SystemClassMapping
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*

/**
 * Background task that builds the game data knowledge index from Assets.zip:
 * 1. Quick-scan ZIP entries for hash changes (incremental)
 * 2. If no changes, skip everything (instant)
 * 3. Parse Assets.zip into game data chunks
 * 4. Embed changed chunks via the text embedding provider
 * 5. Store nodes in SQLite (corpus='gamedata', data_type=GameDataType.id)
 * 6. Build HNSW vector index
 * 7. Create typed gamedata edges (REQUIRES_ITEM, PRODUCES_ITEM, etc.)
 */
class GameDataIndexerTask(
    project: Project,
) : Task.Backgroundable(project, "Building Game Data Index...", true) {

    private val log = Logger.getInstance(GameDataIndexerTask::class.java)
    private var indexedCount = 0
    private var skipped = false

    override fun run(indicator: ProgressIndicator) {
        indicator.isIndeterminate = false

        val db = KnowledgeDatabaseFactory.getInstance()
        val hashTracker = FileHashTracker(db)
        val settings = KnowledgeSettings.getInstance()
        val corpusManager = CorpusIndexManager(settings.toConfig())
        val indexDir = settings.resolvedIndexPath()
        indexDir.mkdirs()

        val zipPath = HytaleInstallPath.assetsZipPath()
        if (zipPath == null) {
            log.warn("GameDataIndexerTask: Hytale install path not configured, aborting")
            return
        }

        // ── Phase 0: Check text builder version ────────────────
        val versionKey = "gamedata:text_builder_version"
        val storedVersion = db.query(
            "SELECT file_hash FROM file_hashes WHERE file_path = ? AND corpus_type = 'gamedata'",
            versionKey,
        ) { it.getString("file_hash") }.firstOrNull()

        val currentVersion = GameDataTextBuilder.TEXT_BUILDER_VERSION.toString()
        if (storedVersion != currentVersion) {
            // On first run storedVersion is null — check if there are existing nodes to clear
            val existingNodeCount = if (storedVersion == null) {
                db.query("SELECT COUNT(*) FROM nodes WHERE corpus = 'gamedata'") { it.getInt(1) }.firstOrNull() ?: 0
            } else 1  // non-null version mismatch always needs clearing

            if (existingNodeCount > 0) {
                log.info("Text builder version changed (${storedVersion ?: "none"} -> $currentVersion), forcing full re-index")
                indicator.text = "Text builder version changed, clearing old index..."
                db.execute("DELETE FROM nodes WHERE corpus = 'gamedata'")
                db.execute("""
                    DELETE FROM edges
                    WHERE (source_id LIKE 'gamedata:%' OR target_id LIKE 'gamedata:%')
                      AND edge_type IN (
                        'RELATES_TO', 'IMPLEMENTED_BY',
                        'REQUIRES_ITEM', 'PRODUCES_ITEM', 'DROPS_ITEM', 'DROPS_ON_DEATH',
                        'OFFERED_IN_SHOP', 'HAS_MEMBER', 'BELONGS_TO_GROUP',
                        'REQUIRES_BENCH', 'TARGETS_GROUP',
                        'SPAWNS_PARTICLE', 'APPLIES_EFFECT', 'REFERENCES_WORLDGEN'
                      )
                """)
                db.execute("DELETE FROM file_hashes WHERE corpus_type = 'gamedata'")
            }
        }

        // ── Phase 1: Parse ──────────────────────────────────────
        indicator.text = "Parsing Assets.zip..."
        indicator.fraction = 0.0

        var lastProgress = 0.0
        val parseResult = GameDataParser.parseAssetsZip(zipPath) { current, total, file ->
            if (indicator.isCanceled) return@parseAssetsZip
            indicator.text2 = file
            val frac = 0.3 * current / total.coerceAtLeast(1)
            if (frac - lastProgress > 0.005) {
                indicator.fraction = frac
                lastProgress = frac
            }
        }

        if (indicator.isCanceled) return

        val allChunks = parseResult.chunks
        log.info("GameDataParser: ${allChunks.size} chunks, ${parseResult.errors.size} errors")

        for (err in parseResult.errors) {
            db.execute(
                "INSERT INTO index_errors (file_path, error_type, message) VALUES (?, 'parse', ?)",
                err.substringBefore(':'), err,
            )
        }

        // ── Phase 2: Detect changes ─────────────────────────────
        indicator.text = "Detecting changes..."
        indicator.fraction = 0.3

        val hashMap = allChunks.associate { it.filePath to it.fileHash }
        val changes = hashTracker.computeChangesFromMap(hashMap, "gamedata")
        log.info("Changes: +${changes.added.size} ~${changes.changed.size} -${changes.deleted.size} =${changes.unchanged.size}")

        if (!changes.hasChanges && changes.unchanged.isNotEmpty()) {
            log.info("No game data changes detected, skipping indexing")
            skipped = true
            indicator.fraction = 1.0
            return
        }

        val changedPaths = changes.added + changes.changed
        val chunksToEmbed = allChunks.filter { it.filePath in changedPaths }

        if (indicator.isCanceled) return

        // ── Phase 3: Embed ──────────────────────────────────────
        val embeddings: List<FloatArray>
        if (chunksToEmbed.isNotEmpty()) {
            indicator.text = "Embedding ${chunksToEmbed.size} game data chunks..."
            indicator.fraction = 0.35

            val provider = corpusManager.getProvider(Corpus.GAMEDATA)
            runBlocking { provider.validate() }

            val texts = chunksToEmbed.map { it.textForEmbedding }
            val batchSize = 32
            val batches = texts.chunked(batchSize)
            val allEmbeddings = mutableListOf<FloatArray>()

            runBlocking {
                for ((batchIdx, batch) in batches.withIndex()) {
                    if (indicator.isCanceled) return@runBlocking
                    indicator.text2 = "Batch ${batchIdx + 1}/${batches.size}"
                    indicator.fraction = 0.35 + (0.35 * batchIdx / batches.size.coerceAtLeast(1))
                    allEmbeddings.addAll(provider.embed(batch))
                }
            }
            embeddings = allEmbeddings
        } else {
            embeddings = emptyList()
        }

        if (indicator.isCanceled) return

        // ── Phase 4: Write index ────────────────────────────────
        indicator.text = "Writing game data index..."
        indicator.fraction = 0.7

        // Remove stale nodes for changed and deleted entries
        val stalePaths = changes.changed + changes.deleted
        if (stalePaths.isNotEmpty()) {
            hashTracker.removeHashes(stalePaths)
            db.inTransaction { conn ->
                val ps = conn.prepareStatement("DELETE FROM nodes WHERE owning_file = ? AND corpus = 'gamedata'")
                for (path in stalePaths) {
                    ps.setString(1, path)
                    ps.addBatch()
                }
                ps.executeBatch()
            }
        }

        // Insert new/changed chunks as nodes
        if (chunksToEmbed.isNotEmpty()) {
            db.inTransaction { conn ->
                val ps = conn.prepareStatement(
                    """INSERT OR REPLACE INTO nodes
                       (id, node_type, display_name, file_path, content, embedding_text, chunk_index, owning_file, corpus, data_type)
                       VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'gamedata', ?)"""
                )
                for ((idx, chunk) in chunksToEmbed.withIndex()) {
                    ps.setString(1, chunk.id)
                    ps.setString(2, "GameData")
                    ps.setString(3, chunk.name)
                    ps.setString(4, chunk.filePath)
                    ps.setString(5, chunk.rawJson.take(8192))
                    ps.setString(6, chunk.textForEmbedding)
                    ps.setInt(7, idx)
                    ps.setString(8, chunk.filePath)
                    ps.setString(9, chunk.type.id)
                    ps.addBatch()
                }
                ps.executeBatch()
            }
        }

        // Save hashes for ALL scanned ZIP entries (not just those that produced chunks)
        if (changes.currentHashes.isNotEmpty()) {
            hashTracker.updateHashes(changes.currentHashes, "gamedata")
        }

        // Store the text builder version sentinel
        hashTracker.updateHashes(mapOf(versionKey to currentVersion), "gamedata")

        // ── Phase 5: Build HNSW ─────────────────────────────────
        indicator.text = "Building game data vector index..."
        indicator.fraction = 0.8

        if (embeddings.isNotEmpty()) {
            val hnswPath = corpusManager.hnswPath(Corpus.GAMEDATA)
            val hnswDimension = embeddings.first().size
            val hnsw = HnswIndex(hnswDimension)
            hnsw.build(embeddings)
            hnsw.save(hnswPath)
            hnsw.close()
            indexedCount = embeddings.size
        }

        // ── Phase 6: Game data relatedId edges ──────────────────
        indicator.text = "Building game data edges..."
        indicator.fraction = 0.9

        buildGameDataEdges(db, allChunks)

        indicator.fraction = 1.0
        log.info("Game data index built: $indexedCount chunks indexed")
    }

    // ── Edge extraction types ────────────────────────────────────

    internal data class EdgeRow(
        val sourceId: String,
        val targetId: String,
        val edgeType: String,
        val metadata: String? = null,
        val targetResolved: Boolean = true,
    )

    /**
     * Build typed gamedata edges replacing the generic RELATES_TO approach.
     * Uses per-type extraction functions and filename stem matching for ID resolution.
     */
    private fun buildGameDataEdges(db: KnowledgeDatabase, chunks: List<GameDataChunk>) {
        if (chunks.isEmpty()) return

        // Scoped delete — protects cross-corpus edges from other indexers
        db.execute("""
            DELETE FROM edges
            WHERE (source_id LIKE 'gamedata:%' OR target_id LIKE 'gamedata:%')
              AND edge_type IN (
                'RELATES_TO', 'IMPLEMENTED_BY',
                'REQUIRES_ITEM', 'PRODUCES_ITEM', 'DROPS_ITEM', 'DROPS_ON_DEATH',
                'OFFERED_IN_SHOP', 'HAS_MEMBER', 'BELONGS_TO_GROUP',
                'REQUIRES_BENCH', 'TARGETS_GROUP',
                'SPAWNS_PARTICLE', 'APPLIES_EFFECT', 'REFERENCES_WORLDGEN'
              )
        """)

        val stemLookup = buildStemLookup(db)
        val allEdges = mutableListOf<EdgeRow>()

        for (chunk in chunks) {
            val json = parseChunkJson(chunk) ?: continue
            when (chunk.type) {
                GameDataType.ITEM -> allEdges += extractItemEdges(chunk, json, stemLookup)
                GameDataType.RECIPE -> allEdges += extractRecipeEdges(chunk, json, stemLookup)
                GameDataType.DROP -> allEdges += extractDropEdges(chunk, json, stemLookup)
                GameDataType.NPC -> allEdges += extractNpcEdges(chunk, json, stemLookup)
                GameDataType.SHOP -> allEdges += extractShopEdges(chunk, json, stemLookup)
                GameDataType.NPC_GROUP -> allEdges += extractGroupEdges(chunk, json, stemLookup)
                GameDataType.OBJECTIVE -> allEdges += extractObjectiveEdges(chunk, json, stemLookup)
                GameDataType.ENTITY -> allEdges += extractEntityEdges(chunk, json, stemLookup)
                GameDataType.BLOCK -> allEdges += extractBlockEdges(chunk, json, stemLookup)
                GameDataType.FARMING -> allEdges += extractFarmingEdges(chunk, json, stemLookup)
                GameDataType.PROJECTILE -> allEdges += extractProjectileEdges(chunk, json)
                GameDataType.WEATHER -> allEdges += extractWeatherEdges(chunk, json)
                GameDataType.INTERACTION -> allEdges += extractInteractionEdges(chunk, json)
                GameDataType.ZONE -> allEdges += extractZoneEdges(chunk, json, stemLookup)
                else -> {}
            }
        }

        if (allEdges.isNotEmpty()) {
            db.inTransaction { conn ->
                val ps = conn.prepareStatement(
                    "INSERT OR IGNORE INTO edges (source_id, target_id, edge_type, target_resolved, metadata) VALUES (?, ?, ?, ?, ?)"
                )
                for (edge in allEdges) {
                    ps.setString(1, edge.sourceId)
                    ps.setString(2, edge.targetId)
                    ps.setString(3, edge.edgeType)
                    ps.setInt(4, if (edge.targetResolved) 1 else 0)
                    ps.setString(5, edge.metadata)
                    ps.addBatch()
                }
                ps.executeBatch()
            }
        }

        val edgeCounts = allEdges.groupBy { it.edgeType }.mapValues { it.value.size }
        log.info("Game data edges: $edgeCounts")

        // ── Cross-corpus IMPLEMENTED_BY edges (gamedata → JavaClass) ──
        buildImplementedByEdges(db, chunks)
    }

    // ── Stem lookup for ID resolution ──────────────────────────

    /**
     * Build filename stem → node ID lookup for gamedata corpus.
     * display_name IS the filename stem (set by GameDataParser.deriveName()).
     */
    private fun buildStemLookup(db: KnowledgeDatabase): Map<String, List<String>> {
        return db.query(
            "SELECT id, display_name FROM nodes WHERE corpus = 'gamedata'"
        ) { rs -> rs.getString("display_name").lowercase() to rs.getString("id") }
            .groupBy({ it.first }, { it.second })
    }

    /**
     * Resolve a filename stem reference to node IDs, returning EdgeRows.
     * Skips self-references. Flags multi-match targets in metadata.
     */
    private fun resolveStem(
        stem: String,
        sourceId: String,
        edgeType: String,
        stemLookup: Map<String, List<String>>,
        metadata: String? = null,
    ): List<EdgeRow> {
        val targets = stemLookup[stem.lowercase()] ?: return emptyList()
        val multiMatch = targets.size > 1
        return targets.filter { it != sourceId }.map { targetId ->
            val finalMeta = if (multiMatch && metadata != null) {
                metadata.trimEnd('}') + ", \"multi_match\": true}"
            } else if (multiMatch) {
                "{\"multi_match\": true}"
            } else {
                metadata
            }
            EdgeRow(sourceId, targetId, edgeType, finalMeta)
        }
    }

    // ── JSON parsing helper ────────────────────────────────────

    private val lenientJson = Json { isLenient = true; ignoreUnknownKeys = true }

    private fun parseChunkJson(chunk: GameDataChunk): JsonObject? {
        return try {
            val text = stripJsoncComments(chunk.rawJson)
            lenientJson.parseToJsonElement(text.trim()).jsonObject
        } catch (e: Exception) {
            log.debug("Failed to re-parse chunk ${chunk.id}: ${e.message}")
            null
        }
    }

    /** Strip single-line and block comments from JSONC text. */
    private fun stripJsoncComments(text: String): String {
        val sb = StringBuilder(text.length)
        var i = 0
        var inString = false
        var escape = false
        while (i < text.length) {
            val c = text[i]
            when {
                escape -> { sb.append(c); escape = false; i++ }
                inString && c == '\\' -> { sb.append(c); escape = true; i++ }
                inString -> { if (c == '"') inString = false; sb.append(c); i++ }
                c == '"' -> { inString = true; sb.append(c); i++ }
                c == '/' && i + 1 < text.length && text[i + 1] == '/' -> {
                    i += 2; while (i < text.length && text[i] != '\n') i++
                }
                c == '/' && i + 1 < text.length && text[i + 1] == '*' -> {
                    i += 2; while (i + 1 < text.length && !(text[i] == '*' && text[i + 1] == '/')) i++; i += 2
                }
                else -> { sb.append(c); i++ }
            }
        }
        return sb.toString().replace(Regex(",\\s*([}\\]])"), "$1")
    }

    // ── Per-type edge extractors ───────────────────────────────

    /** ITEM: Extract REQUIRES_ITEM from inline Recipe.Input[] and SPAWNS_PARTICLE from BlockType.Particles[]. */
    internal fun extractItemEdges(
        chunk: GameDataChunk, obj: JsonObject, stemLookup: Map<String, List<String>>,
    ): List<EdgeRow> {
        val edges = mutableListOf<EdgeRow>()

        // SPAWNS_PARTICLE from BlockType.Particles[]
        obj["BlockType"]?.jsonObjectOrNull()?.let { bt ->
            bt["Particles"]?.jsonArrayOrNull()?.forEach { el ->
                val o = el.jsonObjectOrNull() ?: return@forEach
                o.str("SystemId", "systemId")?.let { systemId ->
                    edges += EdgeRow(chunk.id, "virtual:particle:$systemId", "SPAWNS_PARTICLE",
                        """{"trigger":"block_state"}""", targetResolved = false)
                }
            }
        }

        val recipe = obj["Recipe"]?.jsonObjectOrNull() ?: return edges

        // REQUIRES_BENCH for inline item recipes — benches have no own node type, use virtual targets
        extractBenchRequirementEdges(chunk, recipe, edges)

        for (key in listOf("Input", "inputs", "ingredients")) {
            recipe[key]?.jsonArrayOrNull()?.let { inputs ->
                for (input in inputs) {
                    val o = input.jsonObjectOrNull() ?: continue
                    val itemId = o.str("ItemId", "item", "id")
                    if (itemId != null) {
                        edges += resolveStem(itemId, chunk.id, "REQUIRES_ITEM", stemLookup)
                    } else {
                        // ResourceTypeId → virtual reference (unresolved)
                        o.str("ResourceTypeId")?.let { resId ->
                            edges += EdgeRow(chunk.id, "virtual:resource:$resId", "REQUIRES_ITEM", targetResolved = false)
                        }
                    }
                }
                if (edges.isNotEmpty()) return edges
            }
        }
        return edges
    }

    /** RECIPE: Extract REQUIRES_ITEM + PRODUCES_ITEM from standalone recipe files. */
    internal fun extractRecipeEdges(
        chunk: GameDataChunk, obj: JsonObject, stemLookup: Map<String, List<String>>,
    ): List<EdgeRow> {
        val edges = mutableListOf<EdgeRow>()

        extractBenchRequirementEdges(chunk, obj, edges)

        // Inputs → REQUIRES_ITEM
        for (key in listOf("Input", "inputs", "ingredients")) {
            obj[key]?.jsonArrayOrNull()?.let { inputs ->
                for (input in inputs) {
                    val o = input.jsonObjectOrNull() ?: continue
                    val itemId = o.str("ItemId", "item", "id")
                    if (itemId != null) {
                        edges += resolveStem(itemId, chunk.id, "REQUIRES_ITEM", stemLookup)
                    } else {
                        o.str("ResourceTypeId")?.let { resId ->
                            edges += EdgeRow(chunk.id, "virtual:resource:$resId", "REQUIRES_ITEM", targetResolved = false)
                        }
                    }
                }
                if (edges.isNotEmpty()) break
            }
        }

        // PrimaryOutput → PRODUCES_ITEM (role: primary)
        obj["PrimaryOutput"]?.jsonObjectOrNull()?.let { po ->
            po.str("ItemId", "item", "id")?.let { itemId ->
                edges += resolveStem(itemId, chunk.id, "PRODUCES_ITEM", stemLookup, """{"role": "primary"}""")
            }
        }

        // Output[] → PRODUCES_ITEM (role: secondary)
        for (key in listOf("Output", "outputs")) {
            obj[key]?.jsonArrayOrNull()?.let { outputs ->
                for (output in outputs) {
                    val o = output.jsonObjectOrNull() ?: continue
                    o.str("ItemId", "item", "id")?.let { itemId ->
                        edges += resolveStem(itemId, chunk.id, "PRODUCES_ITEM", stemLookup, """{"role": "secondary"}""")
                    }
                }
                break
            }
        }

        // result.ItemId (legacy)
        obj["result"]?.jsonObjectOrNull()?.let { result ->
            result.str("ItemId", "item", "id")?.let { itemId ->
                edges += resolveStem(itemId, chunk.id, "PRODUCES_ITEM", stemLookup, """{"role": "primary"}""")
            }
        }

        return edges
    }

    /** DROP: Extract DROPS_ITEM from Container hierarchy and flat arrays. */
    internal fun extractDropEdges(
        chunk: GameDataChunk, obj: JsonObject, stemLookup: Map<String, List<String>>,
    ): List<EdgeRow> {
        val edges = mutableListOf<EdgeRow>()
        val itemIds = mutableListOf<String>()

        // Hierarchical Container structure
        obj["Container"]?.jsonObjectOrNull()?.let { extractContainerItemIds(it, itemIds) }

        // Flat fallback arrays
        if (itemIds.isEmpty()) {
            for (key in listOf("Drops", "drops", "entries", "items", "loot")) {
                obj[key]?.jsonArrayOrNull()?.forEach { el ->
                    val o = el.jsonObjectOrNull()
                    val itemId = o?.str("ItemId", "item", "id") ?: (el as? JsonPrimitive)?.content
                    if (!itemId.isNullOrBlank()) itemIds += itemId
                }
            }
        }

        for (itemId in itemIds) {
            edges += resolveStem(itemId, chunk.id, "DROPS_ITEM", stemLookup)
        }
        return edges
    }

    /** NPC: Extract DROPS_ON_DEATH linking NPC → DROP table and SPAWNS_PARTICLE. */
    internal fun extractNpcEdges(
        chunk: GameDataChunk, obj: JsonObject, stemLookup: Map<String, List<String>>,
    ): List<EdgeRow> {
        val edges = mutableListOf<EdgeRow>()
        val modify = obj["Modify"]?.jsonObjectOrNull()

        // SPAWNS_PARTICLE from NPC particle fields
        val particleSources = listOf(obj, modify).filterNotNull()
        for (src in particleSources) {
            src["ApplicationEffects"]?.jsonObjectOrNull()?.let { ae ->
                ae["Particles"]?.jsonArrayOrNull()?.forEach { el ->
                    val systemId = el.jsonObjectOrNull()?.str("SystemId", "systemId")
                        ?: (el as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() }
                    if (systemId != null) {
                        edges += EdgeRow(chunk.id, "virtual:particle:$systemId", "SPAWNS_PARTICLE",
                            """{"trigger":"applied"}""", targetResolved = false)
                    }
                }
            }
        }

        // TARGETS_GROUP for NPC targeting behavior
        val targetGroupSources = listOf(
            obj["TargetGroups"]?.jsonArrayOrNull(),
            modify?.get("TargetGroups")?.jsonArrayOrNull(),
            obj["AcceptedNpcGroups"]?.jsonArrayOrNull(),
        )
        for (arr in targetGroupSources) {
            arr?.forEach { el ->
                val id = el.jsonObjectOrNull()?.str("Id", "id") ?: (el as? JsonPrimitive)?.content
                if (!id.isNullOrBlank()) edges += resolveStem(id, chunk.id, "TARGETS_GROUP", stemLookup)
            }
        }

        // DropList: string reference to a drop table file
        val dropList = obj.str("DropList") ?: modify?.str("DropList")
        if (dropList != null) {
            edges += resolveStem(dropList, chunk.id, "DROPS_ON_DEATH", stemLookup)
        }

        // Drops: string or object with id field
        for (key in listOf("Drops", "drops")) {
            val dropsEl = modify?.get(key) ?: obj[key] ?: continue
            when (dropsEl) {
                is JsonPrimitive -> dropsEl.content.takeIf { it.isNotBlank() }?.let {
                    edges += resolveStem(it, chunk.id, "DROPS_ON_DEATH", stemLookup)
                }
                is JsonObject -> dropsEl.str("Id", "id")?.let {
                    edges += resolveStem(it, chunk.id, "DROPS_ON_DEATH", stemLookup)
                }
                is JsonArray -> dropsEl.forEach { el ->
                    val id = el.jsonObjectOrNull()?.str("Id", "id") ?: (el as? JsonPrimitive)?.content
                    if (!id.isNullOrBlank()) edges += resolveStem(id, chunk.id, "DROPS_ON_DEATH", stemLookup)
                }
            }
            break
        }

        return edges
    }

    /** SHOP: Extract OFFERED_IN_SHOP from TradeSlots and Items. */
    internal fun extractShopEdges(
        chunk: GameDataChunk, obj: JsonObject, stemLookup: Map<String, List<String>>,
    ): List<EdgeRow> {
        val edges = mutableListOf<EdgeRow>()

        // TradeSlots[].Trade.Output.ItemId (fixed trade) or TradeSlots[].Trades[].Output.ItemId (pool)
        obj["TradeSlots"]?.jsonArrayOrNull()?.forEach { slot ->
            val slotObj = slot.jsonObjectOrNull() ?: return@forEach

            // Fixed trade: Trade.Output.ItemId
            slotObj["Trade"]?.jsonObjectOrNull()?.let { trade ->
                trade["Output"]?.jsonObjectOrNull()?.str("ItemId")?.let { itemId ->
                    edges += resolveStem(itemId, chunk.id, "OFFERED_IN_SHOP", stemLookup)
                }
            }

            // Pool trades: Trades[].Output.ItemId
            slotObj["Trades"]?.jsonArrayOrNull()?.forEach { trade ->
                trade.jsonObjectOrNull()?.get("Output")?.jsonObjectOrNull()?.str("ItemId")?.let { itemId ->
                    edges += resolveStem(itemId, chunk.id, "OFFERED_IN_SHOP", stemLookup)
                }
            }
        }

        // Legacy flat: Items[].ItemId
        for (key in listOf("Items", "items")) {
            obj[key]?.jsonArrayOrNull()?.forEach { item ->
                val o = item.jsonObjectOrNull()
                val itemId = o?.str("ItemId", "item", "id") ?: (item as? JsonPrimitive)?.content
                if (!itemId.isNullOrBlank()) {
                    edges += resolveStem(itemId, chunk.id, "OFFERED_IN_SHOP", stemLookup)
                }
            }
        }

        return edges
    }

    /** NPC_GROUP: Extract HAS_MEMBER (group→npc) and BELONGS_TO_GROUP (npc→group). */
    internal fun extractGroupEdges(
        chunk: GameDataChunk, obj: JsonObject, stemLookup: Map<String, List<String>>,
    ): List<EdgeRow> {
        val edges = mutableListOf<EdgeRow>()
        val npcIds = mutableListOf<String>()

        // Members[].NPC / Members[].npc / Members[].id
        for (key in listOf("Members", "members")) {
            obj[key]?.jsonArrayOrNull()?.let { arr ->
                for (member in arr) {
                    val o = member.jsonObjectOrNull()
                    val npcId = o?.str("NPC", "npc", "id") ?: (member as? JsonPrimitive)?.content
                    if (!npcId.isNullOrBlank()) npcIds += npcId
                }
                if (npcIds.isNotEmpty()) break
            }
        }

        // NPCs[].Id / NPCs[].id
        if (npcIds.isEmpty()) {
            for (key in listOf("NPCs", "npcs")) {
                obj[key]?.jsonArrayOrNull()?.let { arr ->
                    for (npc in arr) {
                        val o = npc.jsonObjectOrNull()
                        val id = o?.str("Id", "id") ?: (npc as? JsonPrimitive)?.content
                        if (!id.isNullOrBlank()) npcIds += id
                    }
                    if (npcIds.isNotEmpty()) break
                }
            }
        }

        for (npcId in npcIds) {
            // Forward: group → npc (HAS_MEMBER)
            val resolved = resolveStem(npcId, chunk.id, "HAS_MEMBER", stemLookup)
            edges += resolved
            // Reverse: npc → group (BELONGS_TO_GROUP)
            for (edge in resolved) {
                edges += EdgeRow(edge.targetId, chunk.id, "BELONGS_TO_GROUP", edge.metadata, edge.targetResolved)
            }
        }

        return edges
    }

    private fun extractBenchRequirementEdges(chunk: GameDataChunk, source: JsonObject, edges: MutableList<EdgeRow>) {
        val benchEl = source["BenchRequirement"]
        when (benchEl) {
            is JsonPrimitive -> {
                val id = benchEl.content.takeIf { it.isNotBlank() }
                if (id != null) edges += EdgeRow(chunk.id, "virtual:bench:$id", "REQUIRES_BENCH", targetResolved = false)
            }
            is JsonArray -> {
                benchEl.forEach { el ->
                    val id = el.jsonObjectOrNull()?.str("Id", "id") ?: (el as? JsonPrimitive)?.content
                    if (!id.isNullOrBlank()) edges += EdgeRow(chunk.id, "virtual:bench:$id", "REQUIRES_BENCH", targetResolved = false)
                }
            }
            is JsonObject -> {
                benchEl.str("Id", "id")?.let { id ->
                    edges += EdgeRow(chunk.id, "virtual:bench:$id", "REQUIRES_BENCH", targetResolved = false)
                }
            }
            else -> {}
        }
        if (benchEl == null) {
            source.str("station")?.takeIf { it.isNotBlank() }?.let { id ->
                edges += EdgeRow(chunk.id, "virtual:bench:$id", "REQUIRES_BENCH", targetResolved = false)
            }
        }
    }

    internal fun extractObjectiveEdges(
        chunk: GameDataChunk, obj: JsonObject, stemLookup: Map<String, List<String>>,
    ): List<EdgeRow> {
        val edges = mutableListOf<EdgeRow>()
        for (key in listOf("TaskSets", "taskSets")) {
            obj[key]?.jsonArrayOrNull()?.forEach { setEl ->
                setEl.jsonObjectOrNull()?.let { taskSet ->
                    for (tKey in listOf("Tasks", "tasks")) {
                        taskSet[tKey]?.jsonArrayOrNull()?.forEach { taskEl ->
                            val task = taskEl.jsonObjectOrNull() ?: return@forEach
                            val groupId = task.str("NPCGroupId", "npcGroupId", "GroupId", "groupId")
                            if (groupId != null) {
                                edges += resolveStem(groupId, chunk.id, "TARGETS_GROUP", stemLookup)
                            }
                        }
                    }
                }
            }
        }
        return edges
    }

    internal fun extractEntityEdges(
        chunk: GameDataChunk, obj: JsonObject, stemLookup: Map<String, List<String>>,
    ): List<EdgeRow> {
        val edges = mutableListOf<EdgeRow>()

        // SPAWNS_PARTICLE from ApplicationEffects.Particles[]
        obj["ApplicationEffects"]?.jsonObjectOrNull()?.let { ae ->
            ae["Particles"]?.jsonArrayOrNull()?.forEach { el ->
                val systemId = el.jsonObjectOrNull()?.str("SystemId", "systemId")
                    ?: (el as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() }
                if (systemId != null) {
                    edges += EdgeRow(chunk.id, "virtual:particle:$systemId", "SPAWNS_PARTICLE",
                        """{"trigger":"applied"}""", targetResolved = false)
                }
            }
        }

        for (key in listOf("Drops", "drops")) {
            val dropsEl = obj[key] ?: continue
            when (dropsEl) {
                is JsonPrimitive -> dropsEl.content.takeIf { it.isNotBlank() }?.let {
                    edges += resolveStem(it, chunk.id, "DROPS_ON_DEATH", stemLookup)
                }
                is JsonObject -> dropsEl.str("Id", "id")?.let {
                    edges += resolveStem(it, chunk.id, "DROPS_ON_DEATH", stemLookup)
                }
                else -> {}
            }
            break
        }
        return edges
    }

    internal fun extractBlockEdges(
        chunk: GameDataChunk, obj: JsonObject, stemLookup: Map<String, List<String>>,
    ): List<EdgeRow> {
        val edges = mutableListOf<EdgeRow>()

        // SPAWNS_PARTICLE from Particles.{event} — each key is a trigger event
        obj["Particles"]?.jsonObjectOrNull()?.let { particles ->
            for ((event, value) in particles) {
                val systemId = value.jsonObjectOrNull()?.str("SystemId", "systemId")
                    ?: (value as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() }
                if (systemId != null) {
                    edges += EdgeRow(chunk.id, "virtual:particle:$systemId", "SPAWNS_PARTICLE",
                        """{"trigger":"$event"}""", targetResolved = false)
                }
            }
        }

        for (key in listOf("Drops", "drops", "lootTable")) {
            val dropsEl = obj[key] ?: continue
            when (dropsEl) {
                is JsonPrimitive -> dropsEl.content.takeIf { it.isNotBlank() }?.let {
                    edges += resolveStem(it, chunk.id, "DROPS_ON_DEATH", stemLookup)
                }
                is JsonObject -> dropsEl.str("Id", "id")?.let {
                    edges += resolveStem(it, chunk.id, "DROPS_ON_DEATH", stemLookup)
                }
                else -> {}
            }
            break
        }
        return edges
    }

    internal fun extractFarmingEdges(
        chunk: GameDataChunk, obj: JsonObject, stemLookup: Map<String, List<String>>,
    ): List<EdgeRow> {
        val edges = mutableListOf<EdgeRow>()
        obj["AcceptedNpcGroups"]?.jsonArrayOrNull()?.forEach { el ->
            val id = el.jsonObjectOrNull()?.str("Id", "id") ?: (el as? JsonPrimitive)?.content
            if (!id.isNullOrBlank()) edges += resolveStem(id, chunk.id, "TARGETS_GROUP", stemLookup)
        }
        return edges
    }

    /** PROJECTILE: Extract SPAWNS_PARTICLE from HitParticles and DeathParticles. */
    internal fun extractProjectileEdges(chunk: GameDataChunk, obj: JsonObject): List<EdgeRow> {
        val edges = mutableListOf<EdgeRow>()
        obj["HitParticles"]?.jsonObjectOrNull()?.str("SystemId", "systemId")?.let { systemId ->
            edges += EdgeRow(chunk.id, "virtual:particle:$systemId", "SPAWNS_PARTICLE",
                """{"trigger":"hit"}""", targetResolved = false)
        }
        obj["DeathParticles"]?.jsonObjectOrNull()?.str("SystemId", "systemId")?.let { systemId ->
            edges += EdgeRow(chunk.id, "virtual:particle:$systemId", "SPAWNS_PARTICLE",
                """{"trigger":"death"}""", targetResolved = false)
        }
        return edges
    }

    /** WEATHER: Extract SPAWNS_PARTICLE from Particle.SystemId. */
    internal fun extractWeatherEdges(chunk: GameDataChunk, obj: JsonObject): List<EdgeRow> {
        val edges = mutableListOf<EdgeRow>()
        obj["Particle"]?.jsonObjectOrNull()?.str("SystemId", "systemId")?.let { systemId ->
            edges += EdgeRow(chunk.id, "virtual:particle:$systemId", "SPAWNS_PARTICLE",
                """{"trigger":"ambient"}""", targetResolved = false)
        }
        return edges
    }

    /** INTERACTION: Extract APPLIES_EFFECT from Effects[].EffectId and ApplyEffect action. */
    internal fun extractInteractionEdges(chunk: GameDataChunk, obj: JsonObject): List<EdgeRow> {
        val edges = mutableListOf<EdgeRow>()

        // Effects[].EffectId
        for (key in listOf("Effects", "effects")) {
            obj[key]?.jsonArrayOrNull()?.forEach { el ->
                val effectId = el.jsonObjectOrNull()?.str("EffectId", "effectId")
                if (effectId != null) {
                    edges += EdgeRow(chunk.id, "virtual:effect:$effectId", "APPLIES_EFFECT",
                        targetResolved = false)
                }
            }
        }

        // Action: "ApplyEffect" + EffectId at root level
        val action = obj.str("Action", "action")
        if (action != null && action.contains("Effect", ignoreCase = true)) {
            obj.str("EffectId", "effectId")?.let { effectId ->
                edges += EdgeRow(chunk.id, "virtual:effect:$effectId", "APPLIES_EFFECT",
                    targetResolved = false)
            }
        }

        return edges
    }

    /** ZONE: Extract REFERENCES_WORLDGEN from NoiseMask.File. */
    internal fun extractZoneEdges(
        chunk: GameDataChunk, obj: JsonObject, stemLookup: Map<String, List<String>>,
    ): List<EdgeRow> {
        val edges = mutableListOf<EdgeRow>()

        obj["NoiseMask"]?.jsonObjectOrNull()?.str("File", "file")?.let { file ->
            edges += EdgeRow(chunk.id, "virtual:worldgen:$file", "REFERENCES_WORLDGEN",
                targetResolved = false)
        }

        return edges
    }

    // ── Container traversal helper ─────────────────────────────

    /** Recursively extract ItemIds from Hytale's hierarchical Container structure. */
    private fun extractContainerItemIds(container: JsonObject, out: MutableList<String>) {
        // Direct item: Container.Item.ItemId
        container["Item"]?.jsonObjectOrNull()?.str("ItemId")?.let { out += it; return }

        // Top-level ItemId (flat format)
        container.str("ItemId")?.let { out += it; return }

        // Recurse into sub-containers
        for (key in listOf("Containers", "Multiple", "Choice", "Single", "Items", "Entries")) {
            when (val sub = container[key]) {
                is JsonArray -> sub.forEach { el -> el.jsonObjectOrNull()?.let { extractContainerItemIds(it, out) } }
                is JsonObject -> extractContainerItemIds(sub, out)
                else -> {}
            }
        }
        container["Container"]?.jsonObjectOrNull()?.let { extractContainerItemIds(it, out) }
    }

    // ── Cross-corpus IMPLEMENTED_BY edges ──────────────────────

    private fun buildImplementedByEdges(db: KnowledgeDatabase, chunks: List<GameDataChunk>) {
        val typeIds = chunks.map { it.type.id }.toSet()
        val typeToClassNames = SystemClassMapping.forDataTypes(typeIds)
        val allClassNames = typeToClassNames.values.flatMap { it.classes }.toSet()

        if (allClassNames.isEmpty()) return

        val placeholders = allClassNames.joinToString(",") { "?" }
        val classNameToNodeIds = db.query(
            "SELECT id, display_name FROM nodes WHERE node_type = 'JavaClass' AND display_name IN ($placeholders)",
            *allClassNames.toTypedArray(),
        ) { rs -> rs.getString("display_name") to rs.getString("id") }
            .groupBy({ it.first }, { it.second })

        val codeEdges = mutableListOf<Pair<String, String>>()
        for (chunk in chunks) {
            val info = typeToClassNames[chunk.type.id] ?: continue
            for (className in info.classes) {
                val nodeIds = classNameToNodeIds[className] ?: continue
                for (nodeId in nodeIds) {
                    codeEdges.add(chunk.id to nodeId)
                }
            }
        }

        if (codeEdges.isNotEmpty()) {
            db.inTransaction { conn ->
                val ps = conn.prepareStatement(
                    "INSERT OR IGNORE INTO edges (source_id, target_id, edge_type) VALUES (?, ?, 'IMPLEMENTED_BY')"
                )
                for ((src, tgt) in codeEdges) {
                    ps.setString(1, src)
                    ps.setString(2, tgt)
                    ps.addBatch()
                }
                ps.executeBatch()
            }
            log.info("Game data edges: ${codeEdges.size} IMPLEMENTED_BY links created")
        }
    }

    // ── JsonObject extension helpers ───────────────────────────

    private fun JsonObject.str(vararg keys: String): String? {
        for (key in keys) {
            val v = this[key]?.let { it as? JsonPrimitive }?.takeIf { it.isString }?.content?.takeIf { it.isNotBlank() }
            if (v != null) return v
        }
        return null
    }

    private fun JsonElement.jsonObjectOrNull(): JsonObject? = this as? JsonObject

    private fun JsonElement.jsonArrayOrNull(): JsonArray? = this as? JsonArray

    override fun onSuccess() {
        val message = if (skipped) "No changes detected" else "$indexedCount entries indexed"
        NotificationGroupManager.getInstance()
            .getNotificationGroup("Hyve Knowledge")
            .createNotification(
                "Game data index: ${if (skipped) "up to date" else "built"}",
                message,
                NotificationType.INFORMATION,
            )
            .notify(project)
    }

    override fun onThrowable(error: Throwable) {
        log.error("Game data index build failed", error)
        NotificationGroupManager.getInstance()
            .getNotificationGroup("Hyve Knowledge")
            .createNotification(
                "Game data index build failed",
                error.message ?: "Unknown error",
                NotificationType.ERROR,
            )
            .notify(project)
    }
}
