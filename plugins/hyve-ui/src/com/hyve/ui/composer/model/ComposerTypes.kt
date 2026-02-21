// Copyright 2026 Hyve. All rights reserved.
package com.hyve.ui.composer.model

import com.hyve.ui.core.id.ElementType

/**
 * How a property slot's value is sourced.
 *
 * Determines the prefix/interpretation of the slot's string value:
 * - LITERAL: raw value (e.g. "#ff0000", "14", "true")
 * - VARIABLE: @ reference (e.g. "@primaryColor")
 * - IMPORT: $ reference (e.g. "$Common.@HeaderStyle")
 * - LOCALIZATION: % reference (e.g. "%ui.button.submit")
 * - EXPRESSION: computed value (e.g. "@width * 2")
 * - EMPTY: no value assigned
 */
enum class FillMode {
    LITERAL,
    VARIABLE,
    LOCALIZATION,
    EXPRESSION,
    IMPORT,
    EMPTY
}

/**
 * Property grouping categories for the madlibs form.
 */
enum class SlotCategory(val displayName: String) {
    LAYOUT("Layout"),
    APPEARANCE("Appearance"),
    TEXT("Text"),
    INTERACTION("Interaction"),
    STATE("State"),
    DATA("Data")
}

/**
 * Property types understood by the Composer.
 *
 * These map to the existing [com.hyve.ui.schema.PropertyType] enum but are
 * specific to the Composer's slot-based editing model.
 */
enum class ComposerPropertyType(val displayName: String) {
    TEXT("Text"),
    NUMBER("Number"),
    COLOR("Color"),
    BOOLEAN("Boolean"),
    ANCHOR("Anchor"),
    STYLE("Style"),
    IMAGE("Image"),
    FONT("Font"),
    TUPLE("Tuple"),
    PERCENT("Percent")
}

/**
 * A fillable slot in the Composer's madlibs form.
 *
 * Each slot represents a single property on an element, bundling its name,
 * expected type, UI category, current fill mode, and string value together.
 * This is the fundamental unit of the Composer's editing model.
 *
 * @param name Property name (e.g. "TextColor", "Anchor", "Style")
 * @param type Expected value type for this slot
 * @param category UI category for grouping in the form
 * @param fillMode How the current value is sourced
 * @param value String representation of the current value (empty string when [fillMode] is EMPTY)
 * @param required Whether this property is required by the element schema
 * @param description Optional help text from the schema
 * @param anchorValues For anchor-type slots, the individual axis values
 */
data class PropertySlot(
    val name: String,
    val type: ComposerPropertyType,
    val category: SlotCategory,
    val fillMode: FillMode = FillMode.EMPTY,
    val value: String = "",
    val required: Boolean = false,
    val description: String = "",
    val anchorValues: Map<String, String> = emptyMap(),
    val tupleValues: Map<String, String> = emptyMap()
)

/**
 * The Composer's view of an element being edited.
 *
 * This is a flat, slot-based representation of a UI element — distinct from
 * [com.hyve.ui.core.domain.elements.UIElement] which uses a tree structure
 * with [com.hyve.ui.core.domain.properties.PropertyMap]. Use
 * [ComposerAdapter] to convert between the two representations.
 *
 * @param type The element type (e.g. "Button", "Label")
 * @param id The element's identifier (may be empty)
 * @param slots All property slots for this element, ordered by category
 */
data class ElementDefinition(
    val type: ElementType,
    val id: String,
    val slots: List<PropertySlot>
) {
    /** Count of slots that have a non-empty fill mode. */
    val filledCount: Int get() = slots.count { it.fillMode != FillMode.EMPTY }

    /** Slots grouped by category, preserving category display order. */
    val slotsByCategory: Map<SlotCategory, List<PropertySlot>>
        get() = slots.groupBy { it.category }
            .toSortedMap(compareBy { it.ordinal })
}
