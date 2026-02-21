// Copyright 2026 Hyve. All rights reserved.
package com.hyve.knowledge.extraction

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class JavaChunkerTest {

    @TempDir
    lateinit var tempDir: File

    @Test
    fun `chunks a simple class with methods`() {
        val source = """
            package com.hypixel.hytale.items;

            import java.util.List;

            public class BaseItem {
                private int id;
                private String name;

                public BaseItem(int id, String name) {
                    this.id = id;
                    this.name = name;
                }

                public int getId() {
                    return this.id;
                }

                public String getName() {
                    return this.name;
                }
            }
        """.trimIndent()

        val file = File(tempDir, "BaseItem.java")
        file.writeText(source)

        val chunks = JavaChunker.chunkFile(file)

        assertEquals(3, chunks.size, "Expected 3 chunks: 1 constructor + 2 methods")

        val constructor = chunks.first { it.methodName == "<init>" }
        assertEquals("com.hypixel.hytale.items.BaseItem", constructor.className)
        assertEquals("com.hypixel.hytale.items", constructor.packageName)
        assertTrue(constructor.content.contains("this.id = id"))

        val getId = chunks.first { it.methodName == "getId" }
        assertEquals("com.hypixel.hytale.items.BaseItem#getId", getId.id)
        assertTrue(getId.embeddingText.contains("// Class: BaseItem"))
    }

    @Test
    fun `skips package-info files`() {
        val file = File(tempDir, "package-info.java")
        file.writeText("package com.hypixel.hytale;")
        val chunks = JavaChunker.chunkFile(file)
        assertTrue(chunks.isEmpty())
    }

    @Test
    fun `handles parse errors gracefully`() {
        val file = File(tempDir, "Broken.java")
        file.writeText("this is not valid java {{{")
        val chunks = JavaChunker.chunkFile(file)
        assertTrue(chunks.isEmpty())
    }

    @Test
    fun `chunks enum methods`() {
        val source = """
            package com.hypixel.hytale.items;

            public enum ItemRarity {
                COMMON, RARE, LEGENDARY;

                public String displayName() {
                    return name().toLowerCase();
                }
            }
        """.trimIndent()

        val file = File(tempDir, "ItemRarity.java")
        file.writeText(source)

        val chunks = JavaChunker.chunkFile(file)
        assertEquals(1, chunks.size)
        assertEquals("displayName", chunks[0].methodName)
        assertEquals(3, chunks[0].fields.size) // COMMON, RARE, LEGENDARY
    }

    @Test
    fun `chunkDirectory filters by path`() {
        val pkg = File(tempDir, "com/hypixel/hytale")
        pkg.mkdirs()
        File(pkg, "Foo.java").writeText("""
            package com.hypixel.hytale;
            public class Foo { public void bar() {} }
        """.trimIndent())

        val otherPkg = File(tempDir, "com/other")
        otherPkg.mkdirs()
        File(otherPkg, "Baz.java").writeText("""
            package com.other;
            public class Baz { public void qux() {} }
        """.trimIndent())

        val chunks = JavaChunker.chunkDirectory(tempDir, pathFilter = { it.startsWith("com/hypixel/hytale/") })
        assertEquals(1, chunks.size)
        assertEquals("bar", chunks[0].methodName)
    }
}
