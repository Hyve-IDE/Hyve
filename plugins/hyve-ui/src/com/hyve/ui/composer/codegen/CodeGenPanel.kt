// Copyright 2026 Hyve. All rights reserved.
package com.hyve.ui.composer.codegen

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.hyve.ui.composer.model.ElementDefinition
import org.jetbrains.jewel.ui.component.Text

/**
 * Read-only code preview panel showing generated `.ui` markup.
 *
 * Renders the output of [CodeGenerator.generateUiCode] in a scrollable
 * monospace text block. The panel sits below the Element Preview in the
 * right column of the Composer modal, taking up to 40% of the column height.
 *
 * ## Spec Reference
 * - FR-1: Panel Layout (header + scrollable code block)
 * - FR-5: Live Update (re-renders on every recomposition)
 * - FR-6: Read-Only Display (non-editable monospace text)
 *
 * @param element The element definition to generate code for
 * @param modifier Modifier for the outer container
 */
@Composable
fun CodeGenPanel(
    element: ElementDefinition,
    modifier: Modifier = Modifier,
) {
    val colors = HyveThemeColors.colors
    val generatedCode = CodeGenerator.generateUiCode(element)

    Column(
        modifier = modifier.background(colors.deepNight)
    ) {
        // Header — FR-1
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = HyveSpacing.md, vertical = HyveSpacing.sm),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = ".ui Output",
                style = HyveTypography.caption.copy(
                    color = colors.textSecondary,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.5.sp,
                )
            )

            // "read-only" pill badge
            Box(
                modifier = Modifier
                    .background(
                        color = colors.slate.copy(alpha = HyveOpacity.muted),
                        shape = HyveShapes.card
                    )
                    .padding(horizontal = HyveSpacing.smd, vertical = 2.dp)
            ) {
                Text(
                    text = "READ-ONLY",
                    style = HyveTypography.micro.copy(
                        color = colors.textDisabled,
                        fontWeight = FontWeight.Medium,
                        letterSpacing = 0.5.sp,
                    )
                )
            }
        }

        // Scrollable code block — FR-6
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = HyveSpacing.md, end = HyveSpacing.md, bottom = HyveSpacing.sm)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                text = generatedCode,
                style = HyveTypography.itemTitle.copy(
                    color = colors.textSecondary,
                    fontFamily = FontFamily.Monospace,
                    lineHeight = 19.2.sp, // 12px * 1.6
                )
            )
        }
    }
}
