// Copyright 2026 Hyve. All rights reserved.
package com.hyve.knowledge.bridge

import com.hyve.knowledge.core.db.KnowledgeDatabase
import com.hyve.knowledge.core.logging.LogProvider
import com.hyve.knowledge.settings.KnowledgeSettings
import java.io.File

/**
 * IDE-side singleton factory for KnowledgeDatabase.
 * Bridges the core's constructor-injected KnowledgeDatabase with the IDE's
 * KnowledgeSettings PersistentStateComponent.
 */
object KnowledgeDatabaseFactory {
    private val log: LogProvider = IntelliJLogProvider(KnowledgeDatabaseFactory::class.java)
    private var instance: KnowledgeDatabase? = null

    fun getInstance(): KnowledgeDatabase {
        val existing = instance
        if (existing != null) return existing
        val settings = KnowledgeSettings.getInstance()
        val dbFile = File(settings.resolvedIndexPath(), "knowledge.db")
        val db = KnowledgeDatabase.forFile(dbFile, log)
        instance = db
        return db
    }

    fun resetInstance() {
        instance?.close()
        instance = null
    }
}
