package com.hyve.ui.schema

/**
 * Read-only interface for schema discovery and caching.
 *
 * Production implementation: [SchemaService] singleton.
 * Test implementation: FakeSchemaProvider (in testSrc).
 */
interface SchemaProvider {
    fun getOrDiscoverSchema(forceRediscover: Boolean = false): RuntimeSchemaRegistry
    fun canDiscoverSchema(): Boolean
    fun hasCachedSchema(): Boolean
    fun clearCache()
}
