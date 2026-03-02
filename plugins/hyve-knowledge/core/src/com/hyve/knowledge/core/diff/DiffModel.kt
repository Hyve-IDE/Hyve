// Copyright 2026 Hyve. All rights reserved.
package com.hyve.knowledge.core.diff

import kotlinx.serialization.Serializable

@Serializable
data class VersionDiff(
    val versionA: String,
    val versionB: String,
    val computedAt: String,
    val summary: DiffSummary,
    val entries: List<DiffEntry>,
)

@Serializable
data class DiffSummary(
    val totalAdded: Int,
    val totalRemoved: Int,
    val totalChanged: Int,
    val byCorpus: Map<String, CorpusDiffSummary>,
    val skippedCorpora: Map<String, String> = emptyMap(),
)

@Serializable
data class CorpusDiffSummary(
    val added: Int,
    val removed: Int,
    val changed: Int,
)

@Serializable
data class DiffEntry(
    val nodeId: String,
    val displayName: String,
    val corpus: String,
    val dataType: String? = null,
    val nodeType: String,
    val changeType: ChangeType,
    val filePath: String? = null,
    val detail: DiffDetail? = null,
)

@Serializable
sealed class DiffDetail {
    @Serializable
    data class Code(
        val signatureChanged: Boolean = false,
        val oldSignature: String? = null,
        val newSignature: String? = null,
        val bodyChanged: Boolean = false,
    ) : DiffDetail()

    @Serializable
    data class GameData(
        val fieldChanges: List<FieldChange> = emptyList(),
    ) : DiffDetail()

    @Serializable
    data class Client(
        val contentChanged: Boolean = true,
    ) : DiffDetail()
}

@Serializable
data class FieldChange(
    val field: String,
    val oldValue: String? = null,
    val newValue: String? = null,
    val changeType: ChangeType,
)

@Serializable
enum class ChangeType { ADDED, REMOVED, CHANGED }
