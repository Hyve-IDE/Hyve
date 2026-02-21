// Copyright 2026 Hyve. All rights reserved.
package com.hyve.common.compose

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

object HyveShapes {
    /** Result cards, section cards */
    val card = RoundedCornerShape(4.dp)

    /** Count pills, status badges */
    val badge = RoundedCornerShape(4.dp)

    /** Filter chips (pill shape) */
    val chip = RoundedCornerShape(12.dp)

    /** Checkboxes, tiny elements */
    val small = RoundedCornerShape(2.dp)

    /** Text inputs, color swatches */
    val input = RoundedCornerShape(3.dp)

    /** Popovers, modals */
    val dialog = RoundedCornerShape(8.dp)

    /** Left accent bar end caps */
    val accentBar = RoundedCornerShape(1.5.dp)
}
