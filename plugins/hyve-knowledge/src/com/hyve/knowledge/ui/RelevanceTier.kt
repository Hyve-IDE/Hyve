// Copyright 2026 Hyve. All rights reserved.
package com.hyve.knowledge.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import com.hyve.common.compose.HyveThemeColors

enum class RelevanceTier(val label: String) {
    EXACT("Exact"),
    STRONG("Strong"),
    GOOD("Good"),
    WEAK("Weak"),
    ;

    companion object {
        fun fromScore(score: Double): RelevanceTier = when {
            score >= 0.85 -> EXACT
            score >= 0.70 -> STRONG
            score >= 0.50 -> GOOD
            else -> WEAK
        }
    }
}

object RelevanceColors {
    @Composable
    @ReadOnlyComposable
    fun forTier(tier: RelevanceTier): Color {
        val colors = HyveThemeColors.colors
        return when (tier) {
            RelevanceTier.EXACT -> colors.success
            RelevanceTier.STRONG -> colors.info
            RelevanceTier.GOOD -> colors.honey
            RelevanceTier.WEAK -> colors.textDisabled
        }
    }
}
