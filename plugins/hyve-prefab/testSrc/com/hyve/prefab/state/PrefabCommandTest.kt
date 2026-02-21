package com.hyve.prefab.state

import com.hyve.prefab.domain.*
import kotlinx.serialization.json.*
import org.junit.Assert.*
import org.junit.Test

class PrefabCommandTest {

    private fun createTestDoc(): PrefabDocument {
        val entity1 = PrefabEntity.fromJsonObject(
            Json.parseToJsonElement("""
                {
                    "EntityType": "BlockEntity",
                    "Transform": {"Position": {"x": 1.0, "y": 2.0, "z": 3.0}},
                    "BlockEntity": {"blockTypeKey": "Chest"},
                    "LootTable": {"lootTableId": "tier1_common"}
                }
            """.trimIndent()).jsonObject,
            EntityId(0),
        )
        val entity2 = PrefabEntity.fromJsonObject(
            Json.parseToJsonElement("""
                {
                    "EntityType": "NPC",
                    "Transform": {"Position": {"x": 10.0, "y": 20.0, "z": 30.0}},
                    "NpcComponent": {"npcId": "guard", "level": 5}
                }
            """.trimIndent()).jsonObject,
            EntityId(1),
        )

        return PrefabDocument(
            version = 8,
            blockIdVersion = 1,
            anchor = BlockPos(0, 0, 0),
            entities = listOf(entity1, entity2),
            fluidSummary = PrefabFluidSummary(0, emptySet()),
            blockData = StreamedBlockData(0, emptyMap()),
            rawBytes = ByteArray(0),
            entitiesByteRange = IntRange.EMPTY,
            entityIdCounter = 2,
        )
    }

    @Test
    fun `SetComponentFieldCommand updates field`() {
        val doc = createTestDoc()
        val cmd = SetComponentFieldCommand(
            entityId = EntityId(0),
            componentKey = ComponentTypeKey("LootTable"),
            fieldPath = "lootTableId",
            oldValue = JsonPrimitive("tier1_common"),
            newValue = JsonPrimitive("tier3_rare"),
        )

        val newDoc = cmd.execute(doc)
        assertNotNull(newDoc)
        val lootTable = newDoc!!.findEntityById(EntityId(0))!!
            .components[ComponentTypeKey("LootTable")]!!
        assertEquals("tier3_rare", lootTable["lootTableId"]?.jsonPrimitive?.content)
    }

    @Test
    fun `SetComponentFieldCommand undo reverts field`() {
        val doc = createTestDoc()
        val cmd = SetComponentFieldCommand(
            entityId = EntityId(0),
            componentKey = ComponentTypeKey("LootTable"),
            fieldPath = "lootTableId",
            oldValue = JsonPrimitive("tier1_common"),
            newValue = JsonPrimitive("tier3_rare"),
        )

        val newDoc = cmd.execute(doc)!!
        val undoneDoc = cmd.undo(newDoc)
        assertNotNull(undoneDoc)
        val lootTable = undoneDoc!!.findEntityById(EntityId(0))!!
            .components[ComponentTypeKey("LootTable")]!!
        assertEquals("tier1_common", lootTable["lootTableId"]?.jsonPrimitive?.content)
    }

    @Test
    fun `SetComponentFieldCommand works with nested paths`() {
        val doc = createTestDoc()
        val cmd = SetComponentFieldCommand(
            entityId = EntityId(0),
            componentKey = ComponentTypeKey("Transform"),
            fieldPath = "Position.x",
            oldValue = JsonPrimitive(1.0),
            newValue = JsonPrimitive(99.0),
        )

        val newDoc = cmd.execute(doc)
        assertNotNull(newDoc)
        val pos = newDoc!!.findEntityById(EntityId(0))!!.position!!
        assertEquals(99.0, pos.x, 0.001)
    }

    @Test
    fun `SetComponentFieldCommand merge combines rapid edits`() {
        val cmd1 = SetComponentFieldCommand(
            entityId = EntityId(0),
            componentKey = ComponentTypeKey("LootTable"),
            fieldPath = "lootTableId",
            oldValue = JsonPrimitive("a"),
            newValue = JsonPrimitive("ab"),
        )
        val cmd2 = SetComponentFieldCommand(
            entityId = EntityId(0),
            componentKey = ComponentTypeKey("LootTable"),
            fieldPath = "lootTableId",
            oldValue = JsonPrimitive("ab"),
            newValue = JsonPrimitive("abc"),
        )

        assertTrue(cmd1.canMergeWith(cmd2))
        val merged = cmd1.mergeWith(cmd2)
        assertNotNull(merged)
        assertTrue(merged is SetComponentFieldCommand)
        assertEquals(JsonPrimitive("a"), (merged as SetComponentFieldCommand).oldValue)
        assertEquals(JsonPrimitive("abc"), merged.newValue)
    }

    @Test
    fun `AddEntityCommand adds and undo removes`() {
        val doc = createTestDoc()
        val newEntity = PrefabEntity.fromJsonObject(
            Json.parseToJsonElement("""
                {"EntityType": "Spawner", "SpawnerComponent": {"spawnerId": "zombie"}}
            """.trimIndent()).jsonObject,
            EntityId(2),
        )
        val cmd = AddEntityCommand(newEntity)

        val newDoc = cmd.execute(doc)
        assertEquals(3, newDoc.entities.size)
        assertNotNull(newDoc.findEntityById(EntityId(2)))

        val undoneDoc = cmd.undo(newDoc)
        assertEquals(2, undoneDoc.entities.size)
        assertNull(undoneDoc.findEntityById(EntityId(2)))
    }

    @Test
    fun `DeleteEntityCommand removes and undo restores at correct index`() {
        val doc = createTestDoc()
        val entity = doc.entities[0]
        val cmd = DeleteEntityCommand(entity, 0)

        val newDoc = cmd.execute(doc)
        assertEquals(1, newDoc.entities.size)
        assertNull(newDoc.findEntityById(EntityId(0)))

        val undoneDoc = cmd.undo(newDoc)
        assertEquals(2, undoneDoc.entities.size)
        assertEquals(EntityId(0), undoneDoc.entities[0].id)
    }
}
