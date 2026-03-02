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
    private var currentVersion: String? = null

    fun getInstance(): KnowledgeDatabase {
        val settings = KnowledgeSettings.getInstance()
        val activeVersion = settings.state.activeVersion

        val existing = instance
        if (existing != null && currentVersion == activeVersion) return existing

        // Version changed or first access — close old and open new
        if (existing != null) {
            log.info("Active version changed ($currentVersion -> $activeVersion), reopening database")
            existing.close()
        }

        val dbFile = File(settings.resolvedIndexPath(), "knowledge.db")
        val db = KnowledgeDatabase.forFile(dbFile, log)
        instance = db
        currentVersion = activeVersion
        return db
    }

    fun resetInstance() {
        instance?.close()
        instance = null
        currentVersion = null
    }
}
