// Copyright 2026 Hyve. All rights reserved.
package com.hyve.common.compose

/**
 * Named opacity constants for consistent alpha values across Hyve UI.
 */
object HyveOpacity {
    /** Barely visible tints, inactive backgrounds */
    const val faint = 0.04f

    /** Dot grids, faint hover backgrounds */
    const val subtle = 0.08f

    /** Medium backgrounds, soft fills */
    const val light = 0.12f

    /** Borders, badge backgrounds */
    const val medium = 0.2f

    /** Hover overlays, soft active states */
    const val moderate = 0.3f

    /** Backdrop overlays, drag states */
    const val strong = 0.4f

    /** Disabled/inactive text, dimmed elements */
    const val muted = 0.5f
}
