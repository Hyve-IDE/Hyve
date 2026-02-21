package com.hyve.ui.schema.tier2

import com.hyve.ui.core.id.ElementType
import com.hyve.ui.core.id.PropertyName
import com.hyve.ui.schema.*

/**
 * Schema for Slider element - numeric value slider control.
 *
 * Slider elements allow users to select a numeric value by dragging a handle
 * along a track. They support minimum/maximum values, step increments, and
 * value change events.
 *
 * Common properties:
 * - Value: Current slider value
 * - MinValue: Minimum allowed value
 * - MaxValue: Maximum allowed value
 * - Step: Value increment when dragging
 */
object SliderSchema {
    fun create(): ElementSchema = ElementSchema(
        type = ElementType("Slider"),
        category = ElementCategory.INPUT,
        description = "Slider control for selecting numeric values within a range",
        canHaveChildren = false,
        properties = listOf(
            // Value properties
            PropertySchema(
                name = PropertyName("Value"),
                type = PropertyType.NUMBER,
                required = false,
                description = "Current value of the slider"
            ),
            PropertySchema(
                name = PropertyName("MinValue"),
                type = PropertyType.NUMBER,
                required = false,
                description = "Minimum allowed value"
            ),
            PropertySchema(
                name = PropertyName("MaxValue"),
                type = PropertyType.NUMBER,
                required = false,
                description = "Maximum allowed value"
            ),
            PropertySchema(
                name = PropertyName("Step"),
                type = PropertyType.NUMBER,
                required = false,
                description = "Value increment/decrement step"
            ),

            // Event handling
            PropertySchema(
                name = PropertyName("OnChange"),
                type = PropertyType.TEXT,
                required = false,
                description = "Event triggered when value changes"
            ),

            // Visual styling
            PropertySchema(
                name = PropertyName("TrackColor"),
                type = PropertyType.COLOR,
                required = false,
                description = "Color of the slider track"
            ),
            PropertySchema(
                name = PropertyName("FillColor"),
                type = PropertyType.COLOR,
                required = false,
                description = "Color of the filled portion of the track"
            ),
            PropertySchema(
                name = PropertyName("HandleColor"),
                type = PropertyType.COLOR,
                required = false,
                description = "Color of the slider handle"
            ),
            PropertySchema(
                name = PropertyName("HandleSize"),
                type = PropertyType.NUMBER,
                required = false,
                description = "Size of the slider handle"
            ),

            // Layout
            CommonPropertySchemas.anchor("Position and size of the slider"),
            PropertySchema(
                name = PropertyName("Orientation"),
                type = PropertyType.TEXT,
                required = false,
                description = "Slider orientation: Horizontal, Vertical"
            ),

            // State
            CommonPropertySchemas.enabled("Whether the slider is interactive"),
            CommonPropertySchemas.visible(),

            // Style reference
            CommonPropertySchemas.style()
        ),
        examples = listOf(
            """
            Slider #VolumeSlider {
                Value: 0.7;
                MinValue: 0.0;
                MaxValue: 1.0;
                Step: 0.1;
                Anchor: (Left: 10, Top: 10, Width: 200, Height: 20);
                OnChange: "update_volume";
            }
            """.trimIndent()
        )
    )
}
