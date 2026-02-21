package com.hyve.prefab.editor

import com.hyve.prefab.parser.PrefabParser
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class PrefabDocumentCacheTest {

    @get:Rule
    val tempDir = TemporaryFolder()

    private lateinit var cache: PrefabDocumentCache

    private val minimalPrefab = """
        {
          "version": 8,
          "blockIdVersion": 1,
          "anchorX": 0, "anchorY": 0, "anchorZ": 0,
          "blocks": [{"x": 0, "y": 0, "z": 0, "name": "Stone"}],
          "entities": [
            {"EntityType": "NPC", "Transform": {"Position": {"x": 1.0, "y": 2.0, "z": 3.0}}}
          ]
        }
    """.trimIndent()

    @Before
    fun setUp() {
        cache = PrefabDocumentCache()
    }

    private fun writePrefabFile(name: String = "test.prefab.json", content: String = minimalPrefab): File {
        val file = tempDir.newFile(name)
        file.writeText(content)
        return file
    }

    @Test
    fun `get returns null on empty cache`() {
        val file = writePrefabFile()
        assertNull(cache.get(file))
    }

    @Test
    fun `put then get returns cached document`() {
        val file = writePrefabFile()
        val doc = PrefabParser.parse(file.readBytes())

        cache.put(file, doc)

        val cached = cache.get(file)
        assertNotNull(cached)
        assertSame(doc, cached)
    }

    @Test
    fun `cache hit returns same instance`() {
        val file = writePrefabFile()
        val doc = PrefabParser.parse(file.readBytes())
        cache.put(file, doc)

        val first = cache.get(file)
        val second = cache.get(file)
        assertSame(first, second)
    }

    @Test
    fun `stale entry returns null when file length changes`() {
        val file = writePrefabFile()
        val doc = PrefabParser.parse(file.readBytes())
        cache.put(file, doc)

        // Append data to change file length
        file.appendText("\n    ")

        assertNull(cache.get(file))
    }

    @Test
    fun `stale entry returns null when file lastModified changes`() {
        val file = writePrefabFile()
        val doc = PrefabParser.parse(file.readBytes())
        cache.put(file, doc)

        // Rewrite with same content but force a different lastModified
        Thread.sleep(50) // ensure filesystem timestamp granularity
        file.writeText(minimalPrefab)

        assertNull(cache.get(file))
    }

    @Test
    fun `invalidate removes entry`() {
        val file = writePrefabFile()
        val doc = PrefabParser.parse(file.readBytes())
        cache.put(file, doc)

        cache.invalidate(file.canonicalPath)

        assertNull(cache.get(file))
    }

    @Test
    fun `different files are cached independently`() {
        val file1 = writePrefabFile("a.prefab.json")
        val file2 = writePrefabFile("b.prefab.json")
        val doc1 = PrefabParser.parse(file1.readBytes())
        val doc2 = PrefabParser.parse(file2.readBytes())

        cache.put(file1, doc1)
        cache.put(file2, doc2)

        assertSame(doc1, cache.get(file1))
        assertSame(doc2, cache.get(file2))
    }

    @Test
    fun `LRU eviction removes oldest entries beyond limit`() {
        // The cache limit is 8 — insert 9 files, the first should be evicted
        val files = (1..9).map { i ->
            val file = writePrefabFile("file$i.prefab.json")
            val doc = PrefabParser.parse(file.readBytes())
            cache.put(file, doc)
            file
        }

        // First file should have been evicted
        assertNull("First file should be evicted", cache.get(files[0]))

        // Files 2-9 should still be cached
        for (i in 1..8) {
            assertNotNull("File ${i + 1} should still be cached", cache.get(files[i]))
        }
    }

    @Test
    fun `LRU access promotes entry preventing eviction`() {
        // Insert files 1-8
        val files = (1..8).map { i ->
            val file = writePrefabFile("file$i.prefab.json")
            val doc = PrefabParser.parse(file.readBytes())
            cache.put(file, doc)
            file
        }

        // Access file 1 to promote it in LRU order
        cache.get(files[0])

        // Insert file 9 — should evict file 2 (oldest non-promoted), not file 1
        val file9 = writePrefabFile("file9.prefab.json")
        val doc9 = PrefabParser.parse(file9.readBytes())
        cache.put(file9, doc9)

        assertNotNull("File 1 should survive (was promoted by get)", cache.get(files[0]))
        assertNull("File 2 should be evicted (oldest after file 1 was promoted)", cache.get(files[1]))
    }

    @Test
    fun `put updates existing entry for same file`() {
        val file = writePrefabFile()
        val doc1 = PrefabParser.parse(file.readBytes())
        cache.put(file, doc1)

        // Rewrite file with different content
        Thread.sleep(50)
        val newContent = minimalPrefab.replace("\"version\": 8", "\"version\": 9")
        file.writeText(newContent)
        val doc2 = PrefabParser.parse(file.readBytes())
        cache.put(file, doc2)

        val cached = cache.get(file)
        assertSame(doc2, cached)
        assertEquals(9, cached!!.version)
    }

    @Test
    fun `get with deleted file returns null`() {
        val file = writePrefabFile()
        val doc = PrefabParser.parse(file.readBytes())
        cache.put(file, doc)

        file.delete()

        // File.length() returns 0 and lastModified() returns 0 for deleted files
        assertNull(cache.get(file))
    }
}
