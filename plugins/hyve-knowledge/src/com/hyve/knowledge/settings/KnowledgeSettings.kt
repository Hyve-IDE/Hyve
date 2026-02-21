// Copyright 2026 Hyve. All rights reserved.
package com.hyve.knowledge.settings

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.StoragePathMacros

@State(name = "HyveKnowledgeSettings", storages = [Storage(StoragePathMacros.NON_ROAMABLE_FILE)])
@Service(Service.Level.APP)
class KnowledgeSettings : PersistentStateComponent<KnowledgeSettings.State> {

    private var myState = State()

    class State {
        var embeddingProvider: String = "ollama"  // "ollama" | "voyage"
        var ollamaBaseUrl: String = "http://localhost:11434"
        // Per-purpose Ollama models
        var ollamaCodeModel: String = "qwen3-embedding:8b"
        var ollamaTextModel: String = "nomic-embed-text-v2-moe"
        // Per-purpose Voyage models
        var voyageApiKey: String = ""
        var voyageCodeModel: String = "voyage-code-3"
        var voyageTextModel: String = "voyage-3-large"
        // Legacy fields for backwards compatibility (mapped to code models)
        var ollamaModel: String = ""
        var voyageModel: String = ""
        // Docs settings
        var docsLanguage: String = "en"
        var docsGithubRepo: String = "HytaleModding/site"
        var docsGithubBranch: String = "main"
        // Paths
        var decompileOutputPath: String = ""  // empty = default
        var indexPath: String = ""            // empty = default
        var autoIndexOnStart: Boolean = false
        // Search
        var resultsPerCorpus: Int = 10
        var maxRelatedConnections: Int = 5
    }

    override fun getState(): State = myState

    override fun loadState(state: State) {
        myState = state
        // Migrate legacy single-model fields to per-purpose fields
        if (state.ollamaModel.isNotBlank() && state.ollamaCodeModel == "qwen3-embedding:8b") {
            state.ollamaCodeModel = state.ollamaModel
            state.ollamaTextModel = state.ollamaModel
            state.ollamaModel = ""
        }
        if (state.voyageModel.isNotBlank() && state.voyageCodeModel == "voyage-code-3") {
            state.voyageCodeModel = state.voyageModel
            state.voyageTextModel = state.voyageModel
            state.voyageModel = ""
        }
        // Migrate old default repo name
        if (state.docsGithubRepo == "HytaleModding/wiki") {
            state.docsGithubRepo = "HytaleModding/site"
        }
    }

    /** Resolved path for decompiled output. Falls back to ~/.hyve/knowledge/decompiled/ */
    fun resolvedDecompilePath(): java.io.File {
        val configured = myState.decompileOutputPath
        if (configured.isNotBlank()) return java.io.File(configured)
        return defaultBasePath().resolve("decompiled").toFile()
    }

    /** Resolved base path for index data. Falls back to ~/.hyve/knowledge/ */
    fun resolvedIndexPath(): java.io.File {
        val configured = myState.indexPath
        if (configured.isNotBlank()) return java.io.File(configured)
        return defaultBasePath().toFile()
    }

    private fun defaultBasePath(): java.nio.file.Path {
        val home = System.getProperty("user.home")
        return java.nio.file.Paths.get(home, ".hyve", "knowledge")
    }

    companion object {
        fun getInstance(): KnowledgeSettings =
            ApplicationManager.getApplication().getService(KnowledgeSettings::class.java)
    }
}
