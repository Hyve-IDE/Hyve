package com.hyve.ui.core.domain.properties

import com.hyve.ui.core.id.PropertyName

/**
 * Type-safe immutable property storage for UI elements.
 * Uses composition over inheritance - elements are differentiated by their properties,
 * not by their class hierarchy.
 */
data class PropertyMap(
    private val values: Map<PropertyName, PropertyValue>
) {
    /**
     * Get property value by name, or null if not present
     */
    operator fun get(name: PropertyName): PropertyValue? = values[name]

    /**
     * Get property value by string name (convenience)
     */
    operator fun get(name: String): PropertyValue? = values[PropertyName(name)]

    /**
     * Get property value or return a default
     */
    fun getOrDefault(name: PropertyName, default: PropertyValue): PropertyValue =
        values[name] ?: default

    /**
     * Get property value or return a default (convenience overload)
     */
    fun getOrDefault(name: String, default: PropertyValue): PropertyValue =
        values[PropertyName(name)] ?: default

    /**
     * Set a property value (returns new PropertyMap, immutable)
     */
    fun set(name: PropertyName, value: PropertyValue): PropertyMap =
        copy(values = values + (name to value))

    /**
     * Set a property value by string name (convenience)
     */
    fun set(name: String, value: PropertyValue): PropertyMap =
        set(PropertyName(name), value)

    /**
     * Remove a property (returns new PropertyMap, immutable)
     */
    fun remove(name: PropertyName): PropertyMap =
        copy(values = values - name)

    /**
     * Check if property exists
     */
    fun contains(name: PropertyName): Boolean = name in values

    /**
     * Check if property exists (convenience overload)
     */
    fun contains(name: String): Boolean = PropertyName(name) in values

    /**
     * Get all property names
     */
    fun keys(): Set<PropertyName> = values.keys

    /**
     * Get all property values
     */
    fun values(): Collection<PropertyValue> = values.values

    /**
     * Get all property entries
     */
    fun entries(): Set<Map.Entry<PropertyName, PropertyValue>> = values.entries

    /**
     * Get property count
     */
    fun size(): Int = values.size

    /**
     * Check if empty
     */
    fun isEmpty(): Boolean = values.isEmpty()

    /**
     * Check if not empty
     */
    fun isNotEmpty(): Boolean = values.isNotEmpty()

    /**
     * Merge with another PropertyMap (other takes precedence on conflicts)
     */
    fun merge(other: PropertyMap): PropertyMap =
        PropertyMap(values + other.values)

    /**
     * Filter properties by predicate
     */
    fun filter(predicate: (Map.Entry<PropertyName, PropertyValue>) -> Boolean): PropertyMap =
        PropertyMap(values.filterTo(mutableMapOf(), predicate))

    /**
     * Map property values
     */
    fun mapValues(transform: (Map.Entry<PropertyName, PropertyValue>) -> PropertyValue): PropertyMap =
        PropertyMap(values.mapValues { transform(it) })

    override fun toString(): String {
        if (values.isEmpty()) return "{}"
        val props = values.entries.joinToString(", ") { (k, v) -> "${k.value}: $v" }
        return "{ $props }"
    }

    companion object {
        /**
         * Create empty PropertyMap
         */
        fun empty(): PropertyMap = PropertyMap(emptyMap())

        /**
         * Create PropertyMap from pairs
         */
        @JvmName("ofStrings")
        fun of(vararg pairs: Pair<String, PropertyValue>): PropertyMap =
            PropertyMap(pairs.associate { (k, v) -> PropertyName(k) to v })

        /**
         * Create PropertyMap from PropertyName pairs
         */
        fun of(vararg pairs: Pair<PropertyName, PropertyValue>): PropertyMap =
            PropertyMap(pairs.toMap())
    }
}
