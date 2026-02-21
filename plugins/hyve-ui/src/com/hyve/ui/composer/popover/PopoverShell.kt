// Copyright 2026 Hyve. All rights reserved.
package com.hyve.ui.composer.popover

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hyve.common.compose.HyveOpacity
import com.hyve.common.compose.HyveShapes
import com.hyve.common.compose.HyveSpacing
import com.hyve.common.compose.HyveThemeColors
import com.hyve.common.compose.HyveTypography
import org.jetbrains.jewel.ui.component.Text

/**
 * Shared visual shell for all popover dialogs (spec 06 FR-1).
 *
 * Renders a full-screen backdrop (40% black) with a centered card (320dp wide).
 * Entrance animation: scale 0.95→1.0 + opacity 0→1 over 200ms.
 * Close: backdrop click or Escape key.
 *
 * @param title The popover title text
 * @param onDismiss Called when the popover should close
 * @param content The form content inside the card
 */
@Composable
fun PopoverShell(
    title: String,
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = HyveThemeColors.colors

    // Entrance animation — FR-5
    var appeared by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { appeared = true }

    val animatedAlpha by animateFloatAsState(
        targetValue = if (appeared) 1f else 0f,
        animationSpec = tween(durationMillis = 200),
    )
    val animatedScale by animateFloatAsState(
        targetValue = if (appeared) 1f else 0.95f,
        animationSpec = tween(durationMillis = 200),
    )

    // Full-screen backdrop
    Box(
        modifier = Modifier
            .fillMaxSize()
            .alpha(animatedAlpha)
            .background(colors.deepNight.copy(alpha = HyveOpacity.strong))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { onDismiss() }
            .onPreviewKeyEvent { event ->
                if (event.type == KeyEventType.KeyDown && event.key == Key.Escape) {
                    onDismiss()
                    true
                } else {
                    false
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        // Centered card
        Column(
            modifier = Modifier
                .scale(animatedScale)
                .width(320.dp)
                .clip(HyveShapes.dialog)
                .background(colors.deepNight)
                .border(1.dp, colors.slate, HyveShapes.dialog)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { /* prevent click-through to backdrop */ }
                .padding(20.dp),
        ) {
            // Title
            Text(
                text = title,
                color = colors.textPrimary,
                style = HyveTypography.title.copy(fontWeight = FontWeight.SemiBold),
            )

            Spacer(modifier = Modifier.height(HyveSpacing.lg))

            // Form content
            content()
        }
    }
}
