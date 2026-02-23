// Copyright 2026 Hyve. All rights reserved.
package com.hyve.ui.components.hotkeys

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import org.jetbrains.jewel.ui.component.VerticalScrollbar as JewelVerticalScrollbar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hyve.common.compose.HyveThemeColors
import com.hyve.ui.settings.Hotkeys
import org.jetbrains.jewel.ui.component.Text

/**
 * Modal overlay showing all keyboard shortcuts grouped by category.
 * Reads bindings from [Hotkeys.getAllBindings] so it stays in sync automatically.
 */
@Composable
fun HotkeyReferencePanel(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = HyveThemeColors.colors
    val bindings = Hotkeys.getAllBindings()

    // Semi-transparent scrim — click to dismiss
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.5f))
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center
    ) {
        // Panel card
        val scrollState = rememberScrollState()
        Box(
            modifier = Modifier
                .widthIn(min = 420.dp, max = 520.dp)
                .heightIn(max = 620.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(colors.deepNight)
                .border(1.dp, colors.slate, RoundedCornerShape(12.dp))
                .clickable(enabled = false, onClick = {}) // block scrim click-through
        ) {
            Column {
                // ── Header bar ──
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(colors.midnight)
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Keyboard Shortcuts",
                        color = colors.honey,
                        style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    )
                    // Close button
                    val closeInteraction = remember { MutableInteractionSource() }
                    val closeHovered by closeInteraction.collectIsHoveredAsState()
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .hoverable(closeInteraction)
                            .clip(CircleShape)
                            .background(
                                if (closeHovered) colors.slate.copy(alpha = 0.6f)
                                else Color.Transparent
                            )
                            .clickable(onClick = onDismiss),
                        contentAlignment = Alignment.Center
                    ) {
                        // X icon drawn on a small canvas
                        Canvas(modifier = Modifier.size(10.dp)) {
                            val pad = 1f
                            val stroke = 1.5f
                            val fg = if (closeHovered) colors.textPrimary else colors.textSecondary
                            drawLine(fg, Offset(pad, pad), Offset(size.width - pad, size.height - pad), strokeWidth = stroke)
                            drawLine(fg, Offset(size.width - pad, pad), Offset(pad, size.height - pad), strokeWidth = stroke)
                        }
                    }
                }

                // Thin separator under header
                Box(Modifier.fillMaxWidth().height(1.dp).background(colors.slate))

                // ── Scrollable body ──
                Box(modifier = Modifier.weight(1f)) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(scrollState)
                            .padding(horizontal = 20.dp, vertical = 16.dp)
                    ) {
                        bindings.forEach { (category, entries) ->
                            // Category header
                            Text(
                                text = category.uppercase(),
                                color = colors.textDisabled,
                                style = TextStyle(
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp
                                )
                            )

                            Spacer(Modifier.height(8.dp))

                            entries.forEach { (name, binding) ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp)
                                        .padding(start = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = name,
                                        color = colors.textPrimary,
                                        style = TextStyle(fontSize = 13.sp)
                                    )
                                    // Key badge
                                    Box(
                                        modifier = Modifier
                                            .background(
                                                colors.slate.copy(alpha = 0.4f),
                                                RoundedCornerShape(4.dp)
                                            )
                                            .padding(horizontal = 8.dp, vertical = 3.dp)
                                    ) {
                                        Text(
                                            text = binding.displayString(),
                                            color = colors.honeyLight,
                                            style = TextStyle(
                                                fontSize = 11.sp,
                                                fontFamily = FontFamily.Monospace,
                                                fontWeight = FontWeight.Medium
                                            )
                                        )
                                    }
                                }
                            }

                            Spacer(Modifier.height(14.dp))

                            // Divider between categories
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .height(1.dp)
                                    .background(colors.slate.copy(alpha = 0.3f))
                            )

                            Spacer(Modifier.height(14.dp))
                        }
                    }

                    // Scrollbar (Jewel-themed for visibility)
                    JewelVerticalScrollbar(
                        scrollState = scrollState,
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .fillMaxHeight()
                            .padding(vertical = 4.dp, horizontal = 2.dp)
                    )
                }

                // ── Footer ──
                Box(Modifier.fillMaxWidth().height(1.dp).background(colors.slate.copy(alpha = 0.3f)))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(colors.midnight)
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Press Ctrl+/ or Esc to close",
                        color = colors.textDisabled,
                        style = TextStyle(fontSize = 11.sp)
                    )
                }
            }
        }
    }
}
