// Copyright 2026 Hyve. All rights reserved.
package com.hyve.ui.components.toolbox

import com.hyve.ui.registry.ElementDisplayNames
import com.hyve.ui.registry.ElementTypeRegistry

/**
 * Human-friendly display names and brief descriptions for Hytale UI element types.
 *
 * Delegates to [ElementTypeRegistry] which is the single source of truth
 * for all element type metadata.
 */
object ElementDisplayInfo {

    /**
     * Get a human-friendly display name for an element type.
     * Falls back to splitting PascalCase into words for unknown types.
     */
    fun displayNameFor(typeName: String): String {
        return ElementTypeRegistry.getOrDefault(typeName).displayName
    }

    /**
     * Get a brief description of what an element type does.
     * Returns null for unknown types.
     */
    fun descriptionFor(typeName: String): String? {
        return ElementTypeRegistry.getOrDefault(typeName).description
    }

    /**
     * Split a PascalCase string into space-separated words.
     * Strips leading underscores.
     */
    fun splitPascalCase(name: String): String {
        return ElementDisplayNames.splitPascalCase(name)
    }
}
