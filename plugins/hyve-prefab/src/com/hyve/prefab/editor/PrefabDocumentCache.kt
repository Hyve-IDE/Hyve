package com.hyve.prefab.editor

import com.hyve.prefab.domain.PrefabDocument
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import java.io.File
import java.lang.ref.SoftReference
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedDeque

/**
 * Application-level cache for parsed [PrefabDocument] results.
 *
 * Survives editor tab close/reopen cycles so that reopening the same unchanged
 * prefab file is near-instant instead of requiring a full re-parse.
 *
 * Entries are keyed by canonical file path and validated against filesystem
 * metadata (length + lastModified). [SoftReference] allows the JVM to reclaim
 * large documents under memory pressure.
 */
@Service(Service.Level.APP)
class PrefabDocumentCache {

    private data class CacheEntry(
        val document: PrefabDocument,
        val fileLength: Long,
        val lastModified: Long,
    )

    private val cache = ConcurrentHashMap<String, SoftReference<CacheEntry>>()
    private val accessOrder = ConcurrentLinkedDeque<String>()

    /**
     * Returns a cached [PrefabDocument] if the file has not changed since it was cached,
     * or `null` on miss / stale entry / GC-collected entry.
     */
    fun get(file: File): PrefabDocument? {
        val key = file.canonicalPath
        val ref = cache[key] ?: return null
        val entry = ref.get() ?: run {
            cache.remove(key)
            accessOrder.remove(key)
            return null
        }

        // Validate against current filesystem state
        if (file.length() != entry.fileLength || file.lastModified() != entry.lastModified) {
            cache.remove(key)
            accessOrder.remove(key)
            return null
        }

        // Promote in LRU order
        accessOrder.remove(key)
        accessOrder.addLast(key)
        return entry.document
    }

    /**
     * Stores a parsed document in the cache, recording the file's current
     * length and lastModified for future validation.
     */
    fun put(file: File, document: PrefabDocument) {
        val key = file.canonicalPath
        val entry = CacheEntry(
            document = document,
            fileLength = file.length(),
            lastModified = file.lastModified(),
        )
        cache[key] = SoftReference(entry)

        // Update LRU order
        accessOrder.remove(key)
        accessOrder.addLast(key)

        // Evict oldest entries beyond the limit
        while (accessOrder.size > MAX_ENTRIES) {
            val oldest = accessOrder.pollFirst() ?: break
            cache.remove(oldest)
        }
    }

    /**
     * Removes a specific entry from the cache.
     */
    fun invalidate(path: String) {
        cache.remove(path)
        accessOrder.remove(path)
    }

    companion object {
        private const val MAX_ENTRIES = 8

        fun getInstance(): PrefabDocumentCache = service()
    }
}
