// Copyright 2026 Hyve. All rights reserved.
package com.hyve.knowledge.core.db

enum class Corpus(
    val id: String,
    val displayName: String,
    val hnswFileName: String,
    val embeddingPurpose: EmbeddingPurpose,
) {
    CODE("code", "Server Code", "code.hnsw", EmbeddingPurpose.CODE),
    CLIENT("client", "Client UI", "client.hnsw", EmbeddingPurpose.TEXT),
    GAMEDATA("gamedata", "Game Data", "gamedata.hnsw", EmbeddingPurpose.TEXT),
    DOCS("docs", "Modding Docs", "docs.hnsw", EmbeddingPurpose.TEXT),
}

enum class EmbeddingPurpose { CODE, TEXT }
