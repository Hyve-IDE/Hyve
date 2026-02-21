// Copyright 2026 Hyve. All rights reserved.
package com.hyve.knowledge.core.search

import com.hyve.knowledge.core.db.KnowledgeDatabase

class QueryRouter(private val db: KnowledgeDatabase) {

    private val EXTENDS_PATTERN = Regex(
        """(?:what|which|classes?|types?)\s+(?:extends?|inherits?|subclass(?:es)?(?:\s+of)?)\s+(\w+)""",
        RegexOption.IGNORE_CASE,
    )
    private val IMPLEMENTS_PATTERN = Regex(
        """(?:what|which|classes?|types?)\s+(?:implements?)\s+(\w+)""",
        RegexOption.IGNORE_CASE,
    )
    private val CALLS_PATTERN = Regex(
        """(?:what|who|which)\s+(?:calls?|invokes?)\s+(\w+(?:\.\w+)?)""",
        RegexOption.IGNORE_CASE,
    )
    private val METHODS_OF_PATTERN = Regex(
        """(?:methods?|functions?)\s+(?:of|in|on)\s+(\w+)""",
        RegexOption.IGNORE_CASE,
    )
    private val FIND_CLASS_PATTERN = Regex(
        """(?:find|show|get|where\s+is)\s+(?:class\s+)?(\w+)""",
        RegexOption.IGNORE_CASE,
    )

    private val CRAFT_PATTERN = Regex(
        """(?:how|what)\s+(?:to\s+)?(?:craft|make|produce|create)\s+(\w+)""",
        RegexOption.IGNORE_CASE,
    )
    private val DROP_FROM_PATTERN = Regex(
        """(?:what|which)\s+(?:drops?|loot)\s+(?:from|by)\s+(\w+)""",
        RegexOption.IGNORE_CASE,
    )
    private val USES_ITEM_PATTERN = Regex(
        """(?:what|which)\s+(?:uses?|requires?|needs?)\s+(\w+)""",
        RegexOption.IGNORE_CASE,
    )
    private val BUY_PATTERN = Regex(
        """(?:where|who)\s+(?:to\s+)?(?:buy|sells?|trade)\s+(\w+)""",
        RegexOption.IGNORE_CASE,
    )
    private val UI_SHOWS_PATTERN = Regex(
        """(?:what|which)\s+(?:ui|screen|panel|view)\s+(?:shows?|displays?|contains?|for)\s+(\w+)""",
        RegexOption.IGNORE_CASE,
    )

    fun route(query: String): RouteResult {
        EXTENDS_PATTERN.find(query)?.let { match ->
            return RouteResult(QueryStrategy.GRAPH, match.groupValues[1], "EXTENDS")
        }
        IMPLEMENTS_PATTERN.find(query)?.let { match ->
            return RouteResult(QueryStrategy.GRAPH, match.groupValues[1], "IMPLEMENTS")
        }
        CALLS_PATTERN.find(query)?.let { match ->
            return RouteResult(QueryStrategy.GRAPH, match.groupValues[1], "CALLS")
        }
        METHODS_OF_PATTERN.find(query)?.let { match ->
            return RouteResult(QueryStrategy.GRAPH, match.groupValues[1], "CONTAINS")
        }

        CRAFT_PATTERN.find(query)?.let { match ->
            return RouteResult(QueryStrategy.HYBRID, match.groupValues[1], "REQUIRES_ITEM")
        }
        DROP_FROM_PATTERN.find(query)?.let { match ->
            return RouteResult(QueryStrategy.GRAPH, match.groupValues[1], "DROPS_ON_DEATH")
        }
        USES_ITEM_PATTERN.find(query)?.let { match ->
            return RouteResult(QueryStrategy.GRAPH, match.groupValues[1], "REQUIRES_ITEM")
        }
        BUY_PATTERN.find(query)?.let { match ->
            return RouteResult(QueryStrategy.GRAPH, match.groupValues[1], "OFFERED_IN_SHOP")
        }
        UI_SHOWS_PATTERN.find(query)?.let { match ->
            return RouteResult(QueryStrategy.GRAPH, match.groupValues[1], "UI_BINDS_TO")
        }

        FIND_CLASS_PATTERN.find(query)?.let { match ->
            val name = match.groupValues[1]
            if (entityExists(name)) {
                return RouteResult(QueryStrategy.HYBRID, name)
            }
        }

        val potentialClassNames = Regex("""\b[A-Z]\w{2,}\b""").findAll(query)
            .map { it.value }
            .toList()

        for (name in potentialClassNames) {
            if (entityExists(name)) {
                return RouteResult(QueryStrategy.HYBRID, name)
            }
        }

        return RouteResult(QueryStrategy.VECTOR)
    }

    private fun entityExists(name: String): Boolean {
        val count = db.query(
            "SELECT COUNT(*) FROM nodes WHERE display_name = ? OR display_name LIKE ?",
            name, "%.$name",
        ) { it.getInt(1) }
        return (count.firstOrNull() ?: 0) > 0
    }
}
