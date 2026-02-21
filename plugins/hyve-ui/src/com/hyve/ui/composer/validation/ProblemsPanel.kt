// Copyright 2026 Hyve. All rights reserved.
package com.hyve.ui.composer.validation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hyve.common.compose.HyveOpacity
import com.hyve.common.compose.HyveShapes
import com.hyve.common.compose.HyveSpacing
import com.hyve.common.compose.HyveThemeColors
import com.hyve.common.compose.HyveTypography
import com.hyve.common.compose.components.HyveTriangleDownIcon
import com.hyve.common.compose.components.HyveTriangleRightIcon
import com.hyve.ui.composer.model.Problem
import com.hyve.ui.composer.model.ProblemSeverity
import org.jetbrains.jewel.ui.component.Text

/**
 * Collapsible Problems Panel that displays validation errors and warnings.
 *
 * Hidden entirely when [problems] is empty. Shows a header with collapse
 * toggle, severity count badges, and an optionally expanded scrollable list.
 *
 * ## Spec Reference
 * - FR-1: Panel Layout (header + scrollable list, max 140dp height)
 * - FR-9: Problem Ordering (caller is responsible for sorting)
 *
 * @param problems Sorted list of validation problems
 * @param expanded Whether the problem list is currently expanded
 * @param onToggleExpanded Called when the header is clicked
 * @param modifier Modifier for the outer container
 */
@Composable
fun ProblemsPanel(
    problems: List<Problem>,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (problems.isEmpty()) return

    val colors = HyveThemeColors.colors
    val errorCount = problems.count { it.severity == ProblemSeverity.ERROR }
    val warningCount = problems.count { it.severity == ProblemSeverity.WARNING }

    Column(modifier = modifier.background(colors.midnight)) {
        // Header button — FR-1
        val headerInteraction = remember { MutableInteractionSource() }
        val isHeaderHovered by headerInteraction.collectIsHoveredAsState()

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .hoverable(headerInteraction)
                .clickable(
                    interactionSource = headerInteraction,
                    indication = null,
                ) { onToggleExpanded() }
                .padding(horizontal = HyveSpacing.md, vertical = HyveSpacing.smd),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(HyveSpacing.sm),
        ) {
            // Collapse arrow
            if (expanded) {
                HyveTriangleDownIcon(
                    color = if (isHeaderHovered) colors.textPrimary else colors.textSecondary,
                    modifier = Modifier.size(10.dp),
                )
            } else {
                HyveTriangleRightIcon(
                    color = if (isHeaderHovered) colors.textPrimary else colors.textSecondary,
                    modifier = Modifier.size(10.dp),
                )
            }

            // "Problems" label
            Text(
                text = "Problems",
                style = HyveTypography.caption.copy(
                    color = if (isHeaderHovered) colors.textPrimary else colors.textSecondary,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.5.sp,
                )
            )

            // Error count badge (only when > 0)
            if (errorCount > 0) {
                CountBadge(
                    count = errorCount,
                    backgroundColor = colors.error.copy(alpha = HyveOpacity.medium),
                    textColor = colors.error,
                )
            }

            // Warning count badge (only when > 0)
            if (warningCount > 0) {
                CountBadge(
                    count = warningCount,
                    backgroundColor = colors.warning.copy(alpha = HyveOpacity.medium),
                    textColor = colors.warning,
                )
            }
        }

        // Expanded problem list — FR-1 (max 140dp height, scrollable)
        if (expanded) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 140.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = HyveSpacing.md, vertical = HyveSpacing.xs),
            ) {
                for (problem in problems) {
                    ProblemRow(problem)
                }
            }
        }
    }
}

/**
 * A count badge pill (e.g. error count, warning count).
 */
@Composable
private fun CountBadge(
    count: Int,
    backgroundColor: Color,
    textColor: Color,
) {
    Box(
        modifier = Modifier
            .background(backgroundColor, HyveShapes.card)
            .padding(horizontal = HyveSpacing.smd, vertical = 1.dp),
    ) {
        Text(
            text = count.toString(),
            style = HyveTypography.badge.copy(
                color = textColor,
                fontWeight = FontWeight.SemiBold,
            )
        )
    }
}

/**
 * A single problem row: severity dot + message + optional property badge.
 */
@Composable
private fun ProblemRow(problem: Problem) {
    val colors = HyveThemeColors.colors
    val rowInteraction = remember { MutableInteractionSource() }
    val isHovered by rowInteraction.collectIsHoveredAsState()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .hoverable(rowInteraction)
            .background(if (isHovered) colors.textPrimary.copy(alpha = HyveOpacity.faint) else Color.Transparent)
            .padding(horizontal = HyveSpacing.xs, vertical = HyveSpacing.inputVPad),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(HyveSpacing.sm),
    ) {
        // Severity dot
        val dotColor = when (problem.severity) {
            ProblemSeverity.ERROR -> Color(0xFFF87171)
            ProblemSeverity.WARNING -> Color(0xFFFBBF24)
        }
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(HyveShapes.input)
                .background(dotColor)
        )

        // Message
        Text(
            text = problem.message,
            style = HyveTypography.caption.copy(color = colors.textSecondary),
            modifier = Modifier.weight(1f),
        )

        // Property badge (optional)
        if (problem.property != null) {
            Box(
                modifier = Modifier
                    .background(colors.slate, HyveShapes.input)
                    .padding(horizontal = 5.dp, vertical = 1.dp),
            ) {
                Text(
                    text = problem.property,
                    style = HyveTypography.caption.copy(
                        color = colors.textSecondary,
                        fontFamily = FontFamily.Monospace,
                    )
                )
            }
        }
    }
}
