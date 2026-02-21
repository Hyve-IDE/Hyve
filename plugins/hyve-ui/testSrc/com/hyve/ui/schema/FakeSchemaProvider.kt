package com.hyve.ui.schema

/**
 * In-memory fake of [SchemaProvider] for unit tests.
 * Returns a pre-built or empty [RuntimeSchemaRegistry] without file I/O.
 */
class FakeSchemaProvider(
    private var registry: RuntimeSchemaRegistry = RuntimeSchemaRegistry.empty()
) : SchemaProvider {

    /** Tracks whether [getOrDiscoverSchema] was called. */
    var discoverCalled = false
        private set

    /** Tracks whether [clearCache] was called. */
    var cacheCleared = false
        private set

    override fun getOrDiscoverSchema(forceRediscover: Boolean): RuntimeSchemaRegistry {
        discoverCalled = true
        return registry
    }

    override fun canDiscoverSchema(): Boolean = registry.isLoaded

    override fun hasCachedSchema(): Boolean = registry.isLoaded

    override fun clearCache() {
        cacheCleared = true
    }

    // Test helpers
    fun setRegistry(reg: RuntimeSchemaRegistry) { registry = reg }
}
