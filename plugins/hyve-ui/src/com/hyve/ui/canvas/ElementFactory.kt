// Copyright 2026 Hyve. All rights reserved.
package com.hyve.ui.canvas

import com.hyve.ui.core.domain.anchor.AnchorValue
import com.hyve.ui.core.domain.elements.UIElement
import com.hyve.ui.core.domain.properties.PropertyMap
import com.hyve.ui.core.domain.properties.PropertyValue
import com.hyve.ui.core.id.ElementId
import com.hyve.ui.registry.ElementTypeRegistry
import com.hyve.ui.schema.ElementSchema
import com.hyve.ui.schema.RuntimeSchemaRegistry

/**
 * Creates new UIElements with appropriate defaults.
 *
 * Handles:
 * - Unique ID generation
 * - Default size from [ElementTypeRegistry]
 * - Default properties from [RuntimeSchemaRegistry] + type-specific fallbacks
 *
 * Extracted from CanvasState to make element creation independently testable.
 */
class ElementFactory {

    private var elementCounter = 0

    /**
     * Generate a unique element ID for a given type.
     */
    fun generateUniqueId(typeName: String): ElementId {
        elementCounter++
        return ElementId("${typeName}_$elementCounter")
    }

    /**
     * Get default size for an element type from the registry.
     */
    fun getDefaultSize(typeName: String): Pair<Float, Float> {
        return ElementTypeRegistry.getOrDefault(typeName).defaultSize
    }

    /**
     * Create a new UIElement from a schema at the given canvas position.
     *
     * @param schema The element schema defining the type
     * @param canvasX X position in canvas coordinates
     * @param canvasY Y position in canvas coordinates
     * @param runtimeRegistry Optional runtime schema for observed-value defaults
     * @return The created element with ID, anchor, and default properties
     */
    fun createElement(
        schema: ElementSchema,
        canvasX: Float,
        canvasY: Float,
        runtimeRegistry: RuntimeSchemaRegistry? = null
    ): UIElement {
        val elementId = generateUniqueId(schema.type.value)
        val defaultSize = getDefaultSize(schema.type.value)

        val anchor = AnchorValue.absolute(
            left = canvasX,
            top = canvasY,
            width = defaultSize.first,
            height = defaultSize.second
        )

        val properties = buildDefaultProperties(schema, anchor, runtimeRegistry)

        return UIElement(
            type = schema.type,
            id = elementId,
            properties = properties,
            children = emptyList()
        )
    }

    /**
     * Build default properties for an element based on its schema.
     *
     * Properties are added from two sources:
     * 1. RuntimeSchemaRegistry - properties that are required or have high occurrence
     *    in the game files, with defaults derived from observed values
     * 2. ElementTypeRegistry - essential fallback properties that make elements
     *    immediately useful (e.g., Text for Label, Source for Image)
     */
    fun buildDefaultProperties(
        schema: ElementSchema,
        anchor: AnchorValue,
        runtimeRegistry: RuntimeSchemaRegistry? = null
    ): PropertyMap {
        var props = PropertyMap.of(
            "Anchor" to PropertyValue.Anchor(anchor)
        )

        // First, add schema-driven defaults from RuntimeSchemaRegistry
        val runtimeSchema = runtimeRegistry?.getElementSchema(schema.type.value)
        if (runtimeSchema != null) {
            val elementOccurrences = runtimeSchema.occurrences.coerceAtLeast(1)

            for (propSchema in runtimeSchema.properties) {
                // Skip Anchor - we already set it above
                if (propSchema.name == "Anchor") continue

                // Add property if it's required OR appears in >70% of instances
                val occurrenceRate = propSchema.occurrences.toFloat() / elementOccurrences
                if (propSchema.required || occurrenceRate > 0.7f) {
                    val defaultValue = propSchema.getDefaultValue()
                    // Skip empty/null-like defaults that don't add value
                    val isUsefulDefault = when (defaultValue) {
                        is PropertyValue.Text -> defaultValue.value.isNotBlank()
                        is PropertyValue.ImagePath -> false // Don't add empty paths
                        is PropertyValue.FontPath -> false
                        is PropertyValue.Tuple -> defaultValue.values.isNotEmpty()
                        is PropertyValue.List -> defaultValue.values.isNotEmpty()
                        PropertyValue.Null -> false
                        else -> true
                    }
                    if (isUsefulDefault) {
                        props = props.set(propSchema.name, defaultValue)
                    }
                }
            }
        }

        // Then, add fallback properties from the type registry for essential visual properties.
        // These ensure elements are immediately usable even without schema data.
        val typeInfo = ElementTypeRegistry.getOrDefault(schema.type.value)
        for ((name, value) in typeInfo.defaultFallbackProperties) {
            if (!props.contains(name)) {
                props = props.set(name, value)
            }
        }

        return props
    }
}
