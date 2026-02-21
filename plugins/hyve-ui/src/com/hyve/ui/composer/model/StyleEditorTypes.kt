// Copyright 2026 Hyve. All rights reserved.
package com.hyve.ui.composer.model

/**
 * A style definition being edited in the Composer.
 *
 * Each style has a type (e.g. "TextButtonStyle") that determines its
 * available properties, and one or more visual states containing property
 * slots. Style data is keyed by [name] in the modal's style data store.
 *
 * @param name The style name including @ prefix (e.g. "@ButtonStyle")
 * @param styleType The style type name (e.g. "TextButtonStyle", "LabelStyle")
 * @param states Visual states in definition order; Default is always first
 */
data class StyleTab(
    val name: String,
    val styleType: String,
    val states: List<StyleState>,
) {
    /** Total number of states in this style. */
    val stateCount: Int get() = states.size
}

/**
 * A visual state within a style definition.
 *
 * Standard states are Default, Hovered, Pressed, Disabled. Custom states
 * (e.g. "Selected", "Active") can be added by the user and appear after
 * the standard ones.
 *
 * @param name The state name (e.g. "Default", "Hovered", "Selected")
 * @param slots Property slots for this state, reusing [PropertySlot]
 */
data class StyleState(
    val name: String,
    val slots: List<PropertySlot>,
) {
    /** Count of slots that have a non-empty fill mode. */
    val filledCount: Int get() = slots.count { it.fillMode != FillMode.EMPTY }
}
