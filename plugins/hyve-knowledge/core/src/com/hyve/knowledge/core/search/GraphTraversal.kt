// Copyright 2026 Hyve. All rights reserved.
package com.hyve.knowledge.core.search

import com.hyve.knowledge.core.db.KnowledgeDatabase

class GraphTraversal(private val db: KnowledgeDatabase) {

    fun findByRelation(entityName: String, relation: String, limit: Int = 10): List<SearchResult> {
        val targetResults = db.query(
            """SELECT n.id, n.display_name, n.content, n.file_path, n.line_start
               FROM edges e
               JOIN nodes n ON n.id = e.source_id
               WHERE e.edge_type = ?
               AND (e.target_id LIKE ? OR e.target_id LIKE ?)
               LIMIT ?""",
            relation,
            "class:$entityName",
            "class:%.$entityName",
            limit,
        ) { rs ->
            SearchResult(
                nodeId = rs.getString("id"),
                displayName = rs.getString("display_name"),
                snippet = rs.getString("content")?.take(300) ?: "",
                filePath = rs.getString("file_path") ?: "",
                lineStart = rs.getInt("line_start"),
                score = 1.0,
                source = ResultSource.GRAPH,
            )
        }

        if (targetResults.isNotEmpty()) return targetResults

        return db.query(
            """SELECT n.id, n.display_name, n.content, n.file_path, n.line_start
               FROM edges e
               JOIN nodes n ON n.id = e.target_id
               WHERE e.edge_type = ?
               AND (e.source_id LIKE ? OR e.source_id LIKE ?)
               LIMIT ?""",
            relation,
            "class:$entityName",
            "class:%.$entityName",
            limit,
        ) { rs ->
            SearchResult(
                nodeId = rs.getString("id"),
                displayName = rs.getString("display_name"),
                snippet = rs.getString("content")?.take(300) ?: "",
                filePath = rs.getString("file_path") ?: "",
                lineStart = rs.getInt("line_start"),
                score = 0.9,
                source = ResultSource.GRAPH,
            )
        }
    }

    fun findImplementingCode(gamedataNodeId: String, limit: Int = 10): List<SearchResult> {
        return db.query(
            """SELECT n.id, n.display_name, n.content, n.file_path, n.line_start
               FROM edges e
               JOIN nodes n ON n.id = e.target_id
               WHERE e.source_id = ?
                 AND e.edge_type = 'IMPLEMENTED_BY'
                 AND n.node_type = 'JavaClass'
               LIMIT ?""",
            gamedataNodeId,
            limit,
        ) { rs ->
            SearchResult(
                nodeId = rs.getString("id"),
                displayName = rs.getString("display_name"),
                snippet = rs.getString("content")?.take(300) ?: "",
                filePath = rs.getString("file_path") ?: "",
                lineStart = rs.getInt("line_start"),
                score = 1.0,
                source = ResultSource.GRAPH,
                corpus = "code",
            )
        }
    }

    fun findGamedataForCode(codeNodeId: String, limit: Int = 5): List<SearchResult> {
        return db.query(
            """SELECT n.id, n.display_name, n.content, n.embedding_text,
                      n.file_path, n.line_start, n.data_type
               FROM edges e
               JOIN nodes n ON n.id = e.source_id
               WHERE e.target_id = ?
                 AND e.edge_type = 'IMPLEMENTED_BY'
                 AND n.corpus = 'gamedata'
               LIMIT ?""",
            codeNodeId,
            limit,
        ) { rs ->
            SearchResult(
                nodeId = rs.getString("id"),
                displayName = rs.getString("display_name"),
                snippet = rs.getString("embedding_text")?.take(500)
                    ?: rs.getString("content")?.take(500) ?: "",
                filePath = rs.getString("file_path") ?: "",
                lineStart = rs.getInt("line_start"),
                score = 1.0,
                source = ResultSource.GRAPH,
                dataType = rs.getString("data_type"),
                corpus = "gamedata",
            )
        }
    }

    fun findByName(entityName: String, limit: Int = 10): List<SearchResult> {
        return db.query(
            """SELECT id, display_name, content, file_path, line_start
               FROM nodes
               WHERE display_name = ? OR display_name LIKE ? OR display_name LIKE ?
               ORDER BY
                 CASE WHEN display_name = ? THEN 0
                      WHEN display_name LIKE ? THEN 1
                      ELSE 2
                 END
               LIMIT ?""",
            entityName,
            "$entityName#%",
            "%.$entityName",
            entityName,
            "$entityName#%",
            limit,
        ) { rs ->
            SearchResult(
                nodeId = rs.getString("id"),
                displayName = rs.getString("display_name"),
                snippet = rs.getString("content")?.take(300) ?: "",
                filePath = rs.getString("file_path") ?: "",
                lineStart = rs.getInt("line_start"),
                score = 1.0,
                source = ResultSource.GRAPH,
            )
        }
    }

    fun findRecipeInputs(itemNodeId: String, limit: Int = 10): List<SearchResult> {
        val forward = db.query(
            """SELECT n.id, n.display_name, n.content, n.embedding_text, n.file_path, n.line_start, n.data_type
               FROM edges e
               JOIN nodes n ON n.id = e.target_id
               WHERE e.source_id = ?
                 AND e.edge_type = 'REQUIRES_ITEM'
                 AND n.corpus = 'gamedata'
               LIMIT ?""",
            itemNodeId,
            limit,
        ) { rs ->
            SearchResult(
                nodeId = rs.getString("id"),
                displayName = rs.getString("display_name"),
                snippet = rs.getString("embedding_text")?.take(300) ?: rs.getString("content")?.take(300) ?: "",
                filePath = rs.getString("file_path") ?: "",
                lineStart = rs.getInt("line_start"),
                score = 1.0,
                source = ResultSource.GRAPH,
                dataType = rs.getString("data_type"),
                corpus = "gamedata",
            )
        }
        val reverse = db.query(
            """SELECT n.id, n.display_name, n.content, n.embedding_text, n.file_path, n.line_start, n.data_type
               FROM edges e
               JOIN nodes n ON n.id = e.source_id
               WHERE e.target_id = ?
                 AND e.edge_type = 'REQUIRES_ITEM'
                 AND n.corpus = 'gamedata'
               LIMIT ?""",
            itemNodeId,
            limit,
        ) { rs ->
            SearchResult(
                nodeId = rs.getString("id"),
                displayName = rs.getString("display_name"),
                snippet = rs.getString("embedding_text")?.take(300) ?: rs.getString("content")?.take(300) ?: "",
                filePath = rs.getString("file_path") ?: "",
                lineStart = rs.getInt("line_start"),
                score = 0.9,
                source = ResultSource.GRAPH,
                dataType = rs.getString("data_type"),
                corpus = "gamedata",
            )
        }
        return (forward + reverse).distinctBy { it.nodeId }
    }

    fun findDropsFrom(entityNodeId: String, limit: Int = 10): List<SearchResult> {
        return db.query(
            """SELECT n.id, n.display_name, n.content, n.embedding_text, n.file_path, n.line_start, n.data_type
               FROM edges e1
               JOIN edges e2 ON e2.source_id = e1.target_id AND e2.edge_type = 'DROPS_ITEM'
               JOIN nodes n ON n.id = e2.target_id
               WHERE e1.source_id = ?
                 AND e1.edge_type = 'DROPS_ON_DEATH'
                 AND n.corpus = 'gamedata'
               LIMIT ?""",
            entityNodeId,
            limit,
        ) { rs ->
            SearchResult(
                nodeId = rs.getString("id"),
                displayName = rs.getString("display_name"),
                snippet = rs.getString("embedding_text")?.take(300) ?: rs.getString("content")?.take(300) ?: "",
                filePath = rs.getString("file_path") ?: "",
                lineStart = rs.getInt("line_start"),
                score = 1.0,
                source = ResultSource.GRAPH,
                dataType = rs.getString("data_type"),
                corpus = "gamedata",
            )
        }
    }

    fun findShopsSellingItem(itemNodeId: String, limit: Int = 10): List<SearchResult> {
        return db.query(
            """SELECT n.id, n.display_name, n.content, n.embedding_text, n.file_path, n.line_start, n.data_type
               FROM edges e
               JOIN nodes n ON n.id = e.source_id
               WHERE e.target_id = ?
                 AND e.edge_type = 'OFFERED_IN_SHOP'
                 AND n.corpus = 'gamedata'
               LIMIT ?""",
            itemNodeId,
            limit,
        ) { rs ->
            SearchResult(
                nodeId = rs.getString("id"),
                displayName = rs.getString("display_name"),
                snippet = rs.getString("embedding_text")?.take(300) ?: rs.getString("content")?.take(300) ?: "",
                filePath = rs.getString("file_path") ?: "",
                lineStart = rs.getInt("line_start"),
                score = 1.0,
                source = ResultSource.GRAPH,
                dataType = rs.getString("data_type"),
                corpus = "gamedata",
            )
        }
    }

    fun findDocsReferences(docsNodeId: String, limit: Int = 10): List<SearchResult> {
        return db.query(
            """SELECT n.id, n.display_name, n.content, n.embedding_text, n.file_path, n.line_start, n.data_type, n.corpus
               FROM edges e
               JOIN nodes n ON n.id = e.target_id
               WHERE e.source_id = ?
                 AND e.edge_type = 'DOCS_REFERENCES'
               LIMIT ?""",
            docsNodeId,
            limit,
        ) { rs ->
            val corpus = rs.getString("corpus") ?: "code"
            SearchResult(
                nodeId = rs.getString("id"),
                displayName = rs.getString("display_name"),
                snippet = rs.getString("embedding_text")?.take(300)
                    ?: rs.getString("content")?.take(300) ?: "",
                filePath = rs.getString("file_path") ?: "",
                lineStart = rs.getInt("line_start"),
                score = 1.0,
                source = ResultSource.GRAPH,
                dataType = rs.getString("data_type"),
                corpus = corpus,
            )
        }
    }

    fun findUIBindings(clientNodeId: String, limit: Int = 10): List<SearchResult> {
        return db.query(
            """SELECT n.id, n.display_name, n.content, n.embedding_text,
                      n.file_path, n.line_start, n.data_type
               FROM edges e
               JOIN nodes n ON n.id = e.target_id
               WHERE e.source_id = ?
                 AND e.edge_type = 'UI_BINDS_TO'
                 AND n.corpus = 'gamedata'
               LIMIT ?""",
            clientNodeId,
            limit,
        ) { rs ->
            SearchResult(
                nodeId = rs.getString("id"),
                displayName = rs.getString("display_name"),
                snippet = rs.getString("embedding_text")?.take(500)
                    ?: rs.getString("content")?.take(500) ?: "",
                filePath = rs.getString("file_path") ?: "",
                lineStart = rs.getInt("line_start"),
                score = 1.0,
                source = ResultSource.GRAPH,
                dataType = rs.getString("data_type"),
                corpus = "gamedata",
            )
        }
    }

    fun findUIForGamedata(gamedataNodeId: String, limit: Int = 5): List<SearchResult> {
        return db.query(
            """SELECT n.id, n.display_name, n.content, n.embedding_text,
                      n.file_path, n.line_start
               FROM edges e
               JOIN nodes n ON n.id = e.source_id
               WHERE e.target_id = ?
                 AND e.edge_type = 'UI_BINDS_TO'
                 AND n.corpus = 'client'
               LIMIT ?""",
            gamedataNodeId,
            limit,
        ) { rs ->
            SearchResult(
                nodeId = rs.getString("id"),
                displayName = rs.getString("display_name"),
                snippet = rs.getString("embedding_text")?.take(500)
                    ?: rs.getString("content")?.take(500) ?: "",
                filePath = rs.getString("file_path") ?: "",
                lineStart = rs.getInt("line_start"),
                score = 1.0,
                source = ResultSource.GRAPH,
                corpus = "client",
            )
        }
    }

    fun findGroupMembers(groupNodeId: String, limit: Int = 10): List<SearchResult> {
        return db.query(
            """SELECT n.id, n.display_name, n.content, n.embedding_text, n.file_path, n.line_start, n.data_type
               FROM edges e
               JOIN nodes n ON n.id = e.target_id
               WHERE e.source_id = ?
                 AND e.edge_type = 'HAS_MEMBER'
                 AND n.corpus = 'gamedata'
               LIMIT ?""",
            groupNodeId,
            limit,
        ) { rs ->
            SearchResult(
                nodeId = rs.getString("id"),
                displayName = rs.getString("display_name"),
                snippet = rs.getString("embedding_text")?.take(300) ?: rs.getString("content")?.take(300) ?: "",
                filePath = rs.getString("file_path") ?: "",
                lineStart = rs.getInt("line_start"),
                score = 1.0,
                source = ResultSource.GRAPH,
                dataType = rs.getString("data_type"),
                corpus = "gamedata",
            )
        }
    }
}
