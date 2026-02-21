package com.hyve.prefab.parser

import com.hyve.prefab.domain.ComponentTypeKey
import com.hyve.prefab.exporter.PrefabExporter
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class PrefabParserTest {

    private val minimalPrefab = """
        {
          "version": 8,
          "blockIdVersion": 42,
          "anchorX": 10,
          "anchorY": 20,
          "anchorZ": 30,
          "blocks": [
            {"x": 10, "y": 20, "z": 30, "name": "Stone"},
            {"x": 11, "y": 20, "z": 30, "name": "Stone"},
            {"x": 12, "y": 20, "z": 30, "name": "Dirt"},
            {"x": 10, "y": 21, "z": 30, "name": "Chest", "rotation": 1, "components": {"Inventory": {"slots": 27}}}
          ],
          "fluids": [
            {"x": 10, "y": 19, "z": 30, "name": "Water", "level": 7}
          ],
          "entities": [
            {
              "EntityType": "BlockEntity",
              "Transform": {"Position": {"x": 10.5, "y": 21.0, "z": 30.5}},
              "BlockEntity": {"blockTypeKey": "Chest"},
              "LootTable": {"lootTableId": "tier3_common"}
            },
            {
              "EntityType": "NPC",
              "Transform": {"Position": {"x": 15.0, "y": 22.0, "z": 35.0}, "Rotation": {"yaw": 90.0}},
              "NpcComponent": {"npcId": "village_guard", "level": 5}
            }
          ]
        }
    """.trimIndent()

    @Test
    fun `parse extracts version and anchor`() {
        val doc = PrefabParser.parse(minimalPrefab.toByteArray())
        assertEquals(8, doc.version)
        assertEquals(42, doc.blockIdVersion)
        assertEquals(10, doc.anchor.x)
        assertEquals(20, doc.anchor.y)
        assertEquals(30, doc.anchor.z)
    }

    @Test
    fun `parse counts blocks and histograms types`() {
        val doc = PrefabParser.parse(minimalPrefab.toByteArray())
        assertEquals(4, doc.blockData.blockCount)
        assertEquals(2, doc.blockData.blockTypeCounts["Stone"])
        assertEquals(1, doc.blockData.blockTypeCounts["Dirt"])
        assertEquals(1, doc.blockData.blockTypeCounts["Chest"])
    }

    @Test
    fun `parse counts fluids`() {
        val doc = PrefabParser.parse(minimalPrefab.toByteArray())
        assertEquals(1, doc.fluidSummary.count)
        assertTrue(doc.fluidSummary.typeNames.contains("Water"))
    }

    @Test
    fun `parse extracts entities with components`() {
        val doc = PrefabParser.parse(minimalPrefab.toByteArray())
        assertEquals(2, doc.entities.size)

        val chest = doc.entities[0]
        assertEquals("BlockEntity", chest.entityType)
        assertEquals("BlockEntity: Chest", chest.displayName)
        assertNotNull(chest.position)
        assertEquals(10.5, chest.position!!.x, 0.001)
        assertTrue(chest.components.containsKey(ComponentTypeKey("LootTable")))

        val npc = doc.entities[1]
        assertEquals("NPC", npc.entityType)
        assertEquals(15.0, npc.position!!.x, 0.001)
    }

    @Test
    fun `parse records entity byte range`() {
        val bytes = minimalPrefab.toByteArray()
        val doc = PrefabParser.parse(bytes)
        assertFalse(doc.entitiesByteRange.isEmpty())
        // The byte range should point to the '[' and ']' of the entities array
        assertEquals('['.code.toByte(), bytes[doc.entitiesByteRange.first])
        assertEquals(']'.code.toByte(), bytes[doc.entitiesByteRange.last])
    }

    @Test
    fun `round-trip preserves blocks and fluids`() {
        val bytes = minimalPrefab.toByteArray()
        val doc = PrefabParser.parse(bytes)
        val exported = PrefabExporter.export(doc)

        // Re-parse the exported bytes
        val doc2 = PrefabParser.parse(exported)
        assertEquals(doc.version, doc2.version)
        assertEquals(doc.blockIdVersion, doc2.blockIdVersion)
        assertEquals(doc.anchor, doc2.anchor)
        assertEquals(doc.blockData.blockCount, doc2.blockData.blockCount)
        assertEquals(doc.blockData.blockTypeCounts, doc2.blockData.blockTypeCounts)
        assertEquals(doc.fluidSummary.count, doc2.fluidSummary.count)
        assertEquals(doc.entities.size, doc2.entities.size)
    }

    @Test
    fun `round-trip preserves entity data`() {
        val bytes = minimalPrefab.toByteArray()
        val doc = PrefabParser.parse(bytes)
        val exported = PrefabExporter.export(doc)
        val doc2 = PrefabParser.parse(exported)

        // Verify entity types preserved
        assertEquals("BlockEntity", doc2.entities[0].entityType)
        assertEquals("NPC", doc2.entities[1].entityType)

        // Verify component data preserved
        val lootTable = doc2.entities[0].components[ComponentTypeKey("LootTable")]
        assertNotNull(lootTable)
        assertEquals("tier3_common", lootTable!!["lootTableId"]?.jsonPrimitive?.content)

        val npcComponent = doc2.entities[1].components[ComponentTypeKey("NpcComponent")]
        assertNotNull(npcComponent)
        assertEquals(5, npcComponent!!["level"]?.jsonPrimitive?.int)
    }

    @Test
    fun `blocks are byte-preserved after splice`() {
        val bytes = minimalPrefab.toByteArray()
        val doc = PrefabParser.parse(bytes)
        val exported = PrefabExporter.export(doc)

        // The bytes before the entities array should be identical
        val range = doc.entitiesByteRange
        val beforeEntities = bytes.sliceArray(0 until range.first)
        val beforeEntities2 = exported.sliceArray(0 until range.first)
        assertArrayEquals(beforeEntities, beforeEntities2)

        // The bytes after the entities array should be identical
        val afterEntities = bytes.sliceArray((range.last + 1) until bytes.size)
        val afterEntities2 = exported.sliceArray((exported.size - afterEntities.size) until exported.size)
        assertArrayEquals(afterEntities, afterEntities2)
    }

    @Test
    fun `parse extracts component blocks from blocks with components`() {
        val doc = PrefabParser.parse(minimalPrefab.toByteArray())
        assertEquals(1, doc.componentBlocks.size)

        val chest = doc.componentBlocks[0]
        assertTrue(chest.isComponentBlock)
        assertEquals("Chest", chest.displayName)
        assertNotNull(chest.position)
        assertEquals(10.0, chest.position!!.x, 0.001)
        assertEquals(21.0, chest.position!!.y, 0.001)
        assertEquals(30.0, chest.position!!.z, 0.001)
        assertTrue(chest.components.containsKey(ComponentTypeKey("Inventory")))
    }

    @Test
    fun `component blocks do not affect entities list`() {
        val doc = PrefabParser.parse(minimalPrefab.toByteArray())
        // Entities array still has exactly 2 entries
        assertEquals(2, doc.entities.size)
        // allEntities includes both
        assertEquals(3, doc.allEntities.size)
    }

    @Test
    fun `component blocks are still counted as blocks`() {
        val doc = PrefabParser.parse(minimalPrefab.toByteArray())
        assertEquals(4, doc.blockData.blockCount)
        assertEquals(1, doc.blockData.blockTypeCounts["Chest"])
    }

    @Test
    fun `findEntityById searches both entities and component blocks`() {
        val doc = PrefabParser.parse(minimalPrefab.toByteArray())
        val componentBlock = doc.componentBlocks.first()
        assertNotNull(doc.findEntityById(componentBlock.id))
        // Also finds regular entities
        assertNotNull(doc.findEntityById(doc.entities[0].id))
    }

    @Test
    fun `component block with wrapped Components format`() {
        val wrappedPrefab = """
            {
              "version": 8,
              "blockIdVersion": 1,
              "anchorX": 0, "anchorY": 0, "anchorZ": 0,
              "blocks": [
                {"x": 5, "y": 10, "z": 15, "name": "Furniture_Chest", "components": {
                  "Components": {
                    "PlacedByInteraction": {"WhoPlacedUuid": "abc"},
                    "container": {"Droplist": "tier1", "ItemContainer": {"Capacity": 18}}
                  }
                }}
              ],
              "entities": []
            }
        """.trimIndent()

        val doc = PrefabParser.parse(wrappedPrefab.toByteArray())
        assertEquals(1, doc.componentBlocks.size)
        val chest = doc.componentBlocks[0]
        assertEquals("Furniture_Chest", chest.displayName)
        assertTrue(chest.usesComponentsWrapper)
        assertTrue(chest.components.containsKey(ComponentTypeKey("container")))
        assertTrue(chest.components.containsKey(ComponentTypeKey("PlacedByInteraction")))
    }

    @Test
    fun `round-trip preserves component blocks`() {
        val bytes = minimalPrefab.toByteArray()
        val doc = PrefabParser.parse(bytes)
        val exported = PrefabExporter.export(doc)
        val doc2 = PrefabParser.parse(exported)
        assertEquals(doc.componentBlocks.size, doc2.componentBlocks.size)
        assertEquals(doc.componentBlocks[0].displayName, doc2.componentBlocks[0].displayName)
    }

    @Test
    fun `parse handles missing entities array`() {
        val noEntities = """
            {
              "version": 8,
              "blockIdVersion": 1,
              "anchorX": 0,
              "anchorY": 0,
              "anchorZ": 0,
              "blocks": [
                {"x": 0, "y": 0, "z": 0, "name": "Stone"}
              ]
            }
        """.trimIndent()

        val doc = PrefabParser.parse(noEntities.toByteArray())
        assertEquals(0, doc.entities.size)
        assertTrue(doc.entitiesByteRange.isEmpty())
        assertEquals(1, doc.blockData.blockCount)
    }

    @Test
    fun `parse handles empty entities array`() {
        val emptyEntities = """
            {
              "version": 8,
              "blockIdVersion": 1,
              "anchorX": 0,
              "anchorY": 0,
              "anchorZ": 0,
              "blocks": [],
              "entities": []
            }
        """.trimIndent()

        val doc = PrefabParser.parse(emptyEntities.toByteArray())
        assertEquals(0, doc.entities.size)
        assertEquals(0, doc.blockData.blockCount)
    }

    @Test
    fun `parse captures per-entity source byte ranges`() {
        val bytes = minimalPrefab.toByteArray()
        val doc = PrefabParser.parse(bytes)

        for (entity in doc.entities) {
            val range = entity.sourceByteRange
            assertNotNull("Entity ${entity.displayName} should have sourceByteRange", range)
            range!!

            // Range starts with '{' and ends with '}'
            assertEquals(
                "Range for ${entity.displayName} should start with '{'",
                '{'.code.toByte(), bytes[range.first],
            )
            assertEquals(
                "Range for ${entity.displayName} should end with '}'",
                '}'.code.toByte(), bytes[range.last],
            )

            // Bytes in range are valid JSON
            val entityJson = String(bytes, range.first, range.last - range.first + 1, Charsets.UTF_8)
            val parsed = Json.parseToJsonElement(entityJson)
            assertTrue("Bytes in range should parse as a JSON object", parsed is JsonObject)
        }
    }

    @Test
    fun `component blocks have source byte range pointing to components object`() {
        val bytes = minimalPrefab.toByteArray()
        val doc = PrefabParser.parse(bytes)
        for (block in doc.componentBlocks) {
            val range = block.sourceByteRange
            assertNotNull(
                "Component block ${block.displayName} should have sourceByteRange",
                range,
            )
            range!!

            // Range starts with '{' and ends with '}'
            assertEquals('{'.code.toByte(), bytes[range.first])
            assertEquals('}'.code.toByte(), bytes[range.last])

            // Bytes in range are valid JSON
            val json = String(bytes, range.first, range.last - range.first + 1, Charsets.UTF_8)
            val parsed = Json.parseToJsonElement(json)
            assertTrue("Component block bytes should parse as a JSON object", parsed is JsonObject)
        }
    }

    @Test
    fun `entity source byte ranges do not overlap`() {
        val doc = PrefabParser.parse(minimalPrefab.toByteArray())
        val ranges = doc.entities.mapNotNull { it.sourceByteRange }.sortedBy { it.first }

        for (i in 0 until ranges.size - 1) {
            assertTrue(
                "Range ${ranges[i]} should not overlap with ${ranges[i + 1]}",
                ranges[i].last < ranges[i + 1].first,
            )
        }
    }
}
