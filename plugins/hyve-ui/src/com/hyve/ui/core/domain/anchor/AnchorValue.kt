package com.hyve.ui.core.domain.anchor

/**
 * Represents anchor positioning values in Hytale's UI system.
 * Hybrid approach: stores both visual position and anchor expression.
 *
 * Anchors define element positioning relative to parent container.
 * Can use absolute pixels or relative positioning.
 */
data class AnchorValue(
    val left: AnchorDimension? = null,
    val top: AnchorDimension? = null,
    val right: AnchorDimension? = null,
    val bottom: AnchorDimension? = null,
    val width: AnchorDimension? = null,
    val height: AnchorDimension? = null,
    val fieldOrder: List<String> = emptyList()  // original field order from source for round-trip fidelity
) {
    // Note: Vanilla Hytale files allow partial anchors like (Left: 0) or (Height: 35)
    // The missing dimensions are inherited from style or calculated from content.
    // We don't validate completeness here to support round-trip parsing.

    /** Resolve a field name to its dimension value. */
    private fun dimensionFor(field: String): AnchorDimension? = when (field) {
        "Left" -> left
        "Top" -> top
        "Right" -> right
        "Bottom" -> bottom
        "Width" -> width
        "Height" -> height
        else -> null
    }

    override fun toString(): String {
        val order = if (fieldOrder.isNotEmpty()) fieldOrder
                    else listOf("Left", "Top", "Right", "Bottom", "Width", "Height")
        val parts = order.mapNotNull { field ->
            dimensionFor(field)?.let { "$field: $it" }
        }
        return parts.joinToString(", ", prefix = "(", postfix = ")")
    }

    companion object {
        /**
         * Create anchor from absolute pixel values
         */
        fun absolute(
            left: Float? = null,
            top: Float? = null,
            right: Float? = null,
            bottom: Float? = null,
            width: Float? = null,
            height: Float? = null
        ): AnchorValue = AnchorValue(
            left = left?.let { AnchorDimension.Absolute(it) },
            top = top?.let { AnchorDimension.Absolute(it) },
            right = right?.let { AnchorDimension.Absolute(it) },
            bottom = bottom?.let { AnchorDimension.Absolute(it) },
            width = width?.let { AnchorDimension.Absolute(it) },
            height = height?.let { AnchorDimension.Absolute(it) }
        )

        /**
         * Create anchor from relative values (0.0 to 1.0)
         */
        fun relative(
            left: Float? = null,
            top: Float? = null,
            right: Float? = null,
            bottom: Float? = null,
            width: Float? = null,
            height: Float? = null
        ): AnchorValue = AnchorValue(
            left = left?.let { AnchorDimension.Relative(it) },
            top = top?.let { AnchorDimension.Relative(it) },
            right = right?.let { AnchorDimension.Relative(it) },
            bottom = bottom?.let { AnchorDimension.Relative(it) },
            width = width?.let { AnchorDimension.Relative(it) },
            height = height?.let { AnchorDimension.Relative(it) }
        )

        /**
         * Create anchor that fills the parent container completely.
         * Uses 0 offset on all sides.
         */
        fun fill(): AnchorValue = AnchorValue(
            left = AnchorDimension.Absolute(0f),
            top = AnchorDimension.Absolute(0f),
            right = AnchorDimension.Absolute(0f),
            bottom = AnchorDimension.Absolute(0f)
        )
    }
}

/**
 * Represents a single dimension in an anchor (can be absolute pixels or relative ratio)
 */
sealed class AnchorDimension {
    /**
     * Absolute pixel value
     * Example: Left: 10 (10 pixels from left edge)
     */
    data class Absolute(val pixels: Float) : AnchorDimension() {
        override fun toString(): String = if (pixels % 1.0f == 0.0f) {
            pixels.toInt().toString()
        } else {
            pixels.toString()
        }
    }

    /**
     * Relative ratio value (0.0 to 1.0)
     * Example: Width: 100% (stored as 1.0)
     */
    data class Relative(val ratio: Float) : AnchorDimension() {
        init {
            require(ratio in 0.0f..1.0f) { "Relative ratio must be between 0.0 and 1.0" }
        }

        override fun toString(): String {
            // 0% and 0 are semantically identical; Hytale rejects "0%" so emit "0"
            if (ratio == 0.0f) return "0"
            // Round to 2 decimal places to avoid floating point precision issues
            // e.g., 0.3 * 100 = 30.000002 -> rounds to 30.00
            val pct = kotlin.math.round(ratio * 10000.0f) / 100.0f
            return if (pct % 1.0f == 0.0f) {
                "${pct.toInt()}%"
            } else {
                "$pct%"
            }
        }
    }

    /**
     * Get the value as a float (pixels or ratio)
     */
    fun toFloat(): Float = when (this) {
        is Absolute -> pixels
        is Relative -> ratio
    }
}
