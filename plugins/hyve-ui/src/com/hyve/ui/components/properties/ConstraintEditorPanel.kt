package com.hyve.ui.components.properties

import androidx.compose.foundation.TooltipArea
import androidx.compose.foundation.TooltipPlacement
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hyve.common.compose.HyveThemeColors
import com.hyve.common.compose.HyveShapes
import com.hyve.common.compose.HyveSpacing
import com.hyve.common.compose.HyveTypography
import com.hyve.ui.core.domain.anchor.*
import com.hyve.ui.core.domain.elements.UIElement
import com.hyve.ui.core.domain.properties.PropertyValue
import com.hyve.ui.canvas.CanvasState
import com.hyve.ui.rendering.layout.Rect
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Text

/**
 * Constraint editor panel for visual anchor mode control.
 * Renders a constraint visualization box and two dropdowns (horizontal/vertical).
 *
 * Placed inside the property inspector scroll area and optionally in the Composer modal.
 */
@Composable
fun ConstraintEditorPanel(
    element: UIElement,
    canvasState: CanvasState,
    modifier: Modifier = Modifier
) {
    val colors = HyveThemeColors.colors

    // Read current anchor
    val anchor = (element.getProperty("Anchor") as? PropertyValue.Anchor)?.anchor ?: AnchorValue()

    // Detect current constraint modes
    val horizontalMode = AnchorConstraints.detectHorizontalMode(anchor)
    val verticalMode = AnchorConstraints.detectVerticalMode(anchor)

    // Layout management detection
    val parentLayoutMode = canvasState.getParentLayoutMode(element)
    val horizontalDisabled = parentLayoutMode == "Left"
    val verticalDisabled = parentLayoutMode == "Top"

    // Get element and parent bounds for mode switching
    val elementBounds = canvasState.getBounds(element)?.bounds
    val parent = canvasState.findParent(element)
    val parentBounds = if (parent != null) canvasState.getBounds(parent)?.bounds else null

    // Active edges for visualization
    val activeEdges = AnchorConstraints.activeEdges(anchor)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(JewelTheme.globalColors.panelBackground.copy(alpha = 0.5f), HyveShapes.dialog)
            .border(1.dp, colors.slate.copy(alpha = 0.5f), HyveShapes.dialog)
            .padding(HyveSpacing.md)
    ) {
        // Section header
        Text(
            text = "Constraints",
            color = colors.textSecondary,
            style = HyveTypography.caption.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.5.sp)
        )

        Spacer(modifier = Modifier.height(HyveSpacing.sm))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left: Visual constraint box
            ConstraintVisualization(
                activeEdges = activeEdges,
                horizontalMode = horizontalMode,
                verticalMode = verticalMode,
                onToggleEdge = { edge ->
                    if (elementBounds != null && parentBounds != null) {
                        val newAnchor = AnchorConstraints.toggleEdge(anchor, edge, elementBounds, parentBounds)
                        canvasState.updateElementProperty(
                            element = element,
                            propertyName = "Anchor",
                            value = PropertyValue.Anchor(newAnchor),
                            recordUndo = true,
                            allowMerge = false
                        )
                    }
                },
                horizontalDisabled = horizontalDisabled,
                verticalDisabled = verticalDisabled,
                modifier = Modifier.size(80.dp, 60.dp)
            )

            Spacer(modifier = Modifier.width(HyveSpacing.md))

            // Right: Dropdowns
            Column(modifier = Modifier.weight(1f)) {
                ConstraintDropdown(
                    label = "H",
                    currentMode = horizontalMode.displayName,
                    options = HorizontalConstraint.entries.filter { it != HorizontalConstraint.FREE }.map { it.displayName },
                    disabled = horizontalDisabled,
                    disabledLabel = "Layout (|||)",
                    disabledTooltip = "Horizontal axis is positioned by the parent's Left layout.",
                    onSelect = { selectedName ->
                        val mode = HorizontalConstraint.entries.first { it.displayName == selectedName }
                        if (elementBounds != null && parentBounds != null) {
                            val newAnchor = AnchorConstraints.applyHorizontalMode(anchor, mode, elementBounds, parentBounds)
                            canvasState.updateElementProperty(
                                element = element,
                                propertyName = "Anchor",
                                value = PropertyValue.Anchor(newAnchor),
                                recordUndo = true,
                                allowMerge = false
                            )
                        }
                    }
                )

                Spacer(modifier = Modifier.height(HyveSpacing.xs))

                ConstraintDropdown(
                    label = "V",
                    currentMode = verticalMode.displayName,
                    options = VerticalConstraint.entries.filter { it != VerticalConstraint.FREE }.map { it.displayName },
                    disabled = verticalDisabled,
                    disabledLabel = "Layout (\u2261)",
                    disabledTooltip = "Vertical axis is positioned by the parent's Top layout.",
                    onSelect = { selectedName ->
                        val mode = VerticalConstraint.entries.first { it.displayName == selectedName }
                        if (elementBounds != null && parentBounds != null) {
                            val newAnchor = AnchorConstraints.applyVerticalMode(anchor, mode, elementBounds, parentBounds)
                            canvasState.updateElementProperty(
                                element = element,
                                propertyName = "Anchor",
                                value = PropertyValue.Anchor(newAnchor),
                                recordUndo = true,
                                allowMerge = false
                            )
                        }
                    }
                )
            }
        }
    }
}

/**
 * Compact constraint editor for use in the Composer modal.
 * Same logic, but reads/writes from ElementDefinition anchor slots.
 */
@Composable
fun ConstraintEditorCompact(
    anchor: AnchorValue,
    parentWidth: Float,
    parentHeight: Float,
    onAnchorChange: (AnchorValue) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = HyveThemeColors.colors

    val horizontalMode = AnchorConstraints.detectHorizontalMode(anchor)
    val verticalMode = AnchorConstraints.detectVerticalMode(anchor)
    val activeEdges = AnchorConstraints.activeEdges(anchor)

    // Use a synthetic parent bounds and element bounds derived from anchor
    val parentBounds = Rect(0f, 0f, parentWidth, parentHeight)
    val elementBounds = com.hyve.ui.rendering.layout.AnchorCalculator.calculateBounds(anchor, parentBounds)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(HyveSpacing.sm)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.deepNight.copy(alpha = 0.6f), HyveShapes.dialog)
                .border(1.dp, colors.slate.copy(alpha = 0.4f), HyveShapes.dialog)
                .padding(HyveSpacing.sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ConstraintVisualization(
                activeEdges = activeEdges,
                horizontalMode = horizontalMode,
                verticalMode = verticalMode,
                onToggleEdge = { edge ->
                    onAnchorChange(AnchorConstraints.toggleEdge(anchor, edge, elementBounds, parentBounds))
                },
                horizontalDisabled = false,
                verticalDisabled = false,
                modifier = Modifier.size(50.dp, 40.dp)
            )

            Spacer(modifier = Modifier.width(HyveSpacing.sm))

            Column(modifier = Modifier.weight(1f)) {
                ConstraintDropdown(
                    label = "H",
                    currentMode = horizontalMode.displayName,
                    options = HorizontalConstraint.entries.filter { it != HorizontalConstraint.FREE }.map { it.displayName },
                    disabled = false,
                    onSelect = { selectedName ->
                        val mode = HorizontalConstraint.entries.first { it.displayName == selectedName }
                        onAnchorChange(AnchorConstraints.applyHorizontalMode(anchor, mode, elementBounds, parentBounds))
                    }
                )

                Spacer(modifier = Modifier.height(HyveSpacing.xs))

                ConstraintDropdown(
                    label = "V",
                    currentMode = verticalMode.displayName,
                    options = VerticalConstraint.entries.filter { it != VerticalConstraint.FREE }.map { it.displayName },
                    disabled = false,
                    onSelect = { selectedName ->
                        val mode = VerticalConstraint.entries.first { it.displayName == selectedName }
                        onAnchorChange(AnchorConstraints.applyVerticalMode(anchor, mode, elementBounds, parentBounds))
                    }
                )
            }
        }
    }
}

// --- Internal UI Components ---

/**
 * Visual constraint box: outer parent rect + inner element rect + edge indicators.
 */
@Composable
private fun ConstraintVisualization(
    activeEdges: Set<String>,
    horizontalMode: HorizontalConstraint,
    verticalMode: VerticalConstraint,
    onToggleEdge: (String) -> Unit,
    horizontalDisabled: Boolean,
    verticalDisabled: Boolean,
    modifier: Modifier = Modifier
) {
    val colors = HyveThemeColors.colors
    val accentColor = colors.honey
    val mutedColor = colors.slate

    // Inner rect position based on constraint mode
    val innerX = when (horizontalMode) {
        HorizontalConstraint.LEFT_WIDTH -> 0.15f
        HorizontalConstraint.RIGHT_WIDTH -> 0.55f
        HorizontalConstraint.LEFT_RIGHT -> 0.15f
        HorizontalConstraint.CENTER -> 0.3f
        HorizontalConstraint.FREE -> 0.3f
    }
    val innerWidth = when (horizontalMode) {
        HorizontalConstraint.LEFT_RIGHT -> 0.7f
        else -> 0.4f
    }
    val innerY = when (verticalMode) {
        VerticalConstraint.TOP_HEIGHT -> 0.15f
        VerticalConstraint.BOTTOM_HEIGHT -> 0.5f
        VerticalConstraint.TOP_BOTTOM -> 0.15f
        VerticalConstraint.MIDDLE -> 0.3f
        VerticalConstraint.FREE -> 0.3f
    }
    val innerHeight = when (verticalMode) {
        VerticalConstraint.TOP_BOTTOM -> 0.7f
        else -> 0.4f
    }

    Box(modifier = modifier) {
        // Draw the constraint visualization
        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawBehind {
                    val w = size.width
                    val h = size.height
                    val pad = 2f

                    // Outer parent rect
                    drawRect(
                        color = mutedColor.copy(alpha = 0.3f),
                        topLeft = Offset(pad, pad),
                        size = androidx.compose.ui.geometry.Size(w - 2 * pad, h - 2 * pad),
                        style = Stroke(width = 1f)
                    )

                    // Inner element rect
                    val ix = pad + innerX * (w - 2 * pad)
                    val iy = pad + innerY * (h - 2 * pad)
                    val iw = innerWidth * (w - 2 * pad)
                    val ih = innerHeight * (h - 2 * pad)

                    drawRect(
                        color = accentColor.copy(alpha = 0.15f),
                        topLeft = Offset(ix, iy),
                        size = androidx.compose.ui.geometry.Size(iw, ih)
                    )
                    drawRect(
                        color = accentColor.copy(alpha = 0.6f),
                        topLeft = Offset(ix, iy),
                        size = androidx.compose.ui.geometry.Size(iw, ih),
                        style = Stroke(width = 1f)
                    )

                    // Edge indicators (lines from parent edge to inner element)
                    val dashedEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 4f))

                    // Left edge
                    drawEdgeLine(
                        active = "left" in activeEdges,
                        start = Offset(pad, h / 2),
                        end = Offset(ix, h / 2),
                        accentColor = accentColor,
                        mutedColor = mutedColor,
                        dashedEffect = dashedEffect
                    )
                    // Right edge
                    drawEdgeLine(
                        active = "right" in activeEdges,
                        start = Offset(ix + iw, h / 2),
                        end = Offset(w - pad, h / 2),
                        accentColor = accentColor,
                        mutedColor = mutedColor,
                        dashedEffect = dashedEffect
                    )
                    // Top edge
                    drawEdgeLine(
                        active = "top" in activeEdges,
                        start = Offset(w / 2, pad),
                        end = Offset(w / 2, iy),
                        accentColor = accentColor,
                        mutedColor = mutedColor,
                        dashedEffect = dashedEffect
                    )
                    // Bottom edge
                    drawEdgeLine(
                        active = "bottom" in activeEdges,
                        start = Offset(w / 2, iy + ih),
                        end = Offset(w / 2, h - pad),
                        accentColor = accentColor,
                        mutedColor = mutedColor,
                        dashedEffect = dashedEffect
                    )
                }
        )

        // Clickable edge regions (overlays)
        if (!horizontalDisabled) {
            // Left edge click area
            EdgeClickArea(
                edge = "left",
                active = "left" in activeEdges,
                onToggle = onToggleEdge,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .width(20.dp)
                    .fillMaxHeight()
            )
            // Right edge click area
            EdgeClickArea(
                edge = "right",
                active = "right" in activeEdges,
                onToggle = onToggleEdge,
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .width(20.dp)
                    .fillMaxHeight()
            )
        }
        if (!verticalDisabled) {
            // Top edge click area
            EdgeClickArea(
                edge = "top",
                active = "top" in activeEdges,
                onToggle = onToggleEdge,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .height(16.dp)
            )
            // Bottom edge click area
            EdgeClickArea(
                edge = "bottom",
                active = "bottom" in activeEdges,
                onToggle = onToggleEdge,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(16.dp)
            )
        }
    }
}

private fun DrawScope.drawEdgeLine(
    active: Boolean,
    start: Offset,
    end: Offset,
    accentColor: Color,
    mutedColor: Color,
    dashedEffect: PathEffect
) {
    if (active) {
        drawLine(
            color = accentColor,
            start = start,
            end = end,
            strokeWidth = 2f
        )
    } else {
        drawLine(
            color = mutedColor.copy(alpha = 0.6f),
            start = start,
            end = end,
            strokeWidth = 1f,
            pathEffect = dashedEffect
        )
    }
}

/**
 * Invisible clickable region for an edge in the constraint visualization.
 * Shows a subtle highlight on hover.
 */
@Composable
private fun EdgeClickArea(
    edge: String,
    active: Boolean,
    onToggle: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val colors = HyveThemeColors.colors

    Box(
        modifier = modifier
            .hoverable(interactionSource)
            .clickable { onToggle(edge) }
            .background(
                if (isHovered) colors.honey.copy(alpha = 0.08f) else Color.Transparent
            )
    )
}

/**
 * Compact constraint mode dropdown.
 * Shows a label + current mode, clicking opens a selection list.
 */
@Composable
private fun ConstraintDropdown(
    label: String,
    currentMode: String,
    options: List<String>,
    disabled: Boolean,
    disabledLabel: String = "",
    disabledTooltip: String = "",
    onSelect: (String) -> Unit
) {
    val colors = HyveThemeColors.colors
    var expanded by remember { mutableStateOf(false) }

    val content = @Composable {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Axis label
            Box(
                modifier = Modifier
                    .size(18.dp)
                    .background(
                        if (disabled) colors.slate.copy(alpha = 0.3f)
                        else colors.honey.copy(alpha = 0.15f),
                        HyveShapes.input
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label,
                    color = if (disabled) colors.textDisabled else colors.honey,
                    style = HyveTypography.badge.copy(fontWeight = FontWeight.Bold)
                )
            }

            Spacer(modifier = Modifier.width(HyveSpacing.smd))

            // Mode selector
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(HyveShapes.card)
                    .background(
                        if (disabled) Color.Transparent
                        else colors.slate.copy(alpha = 0.3f),
                        HyveShapes.card
                    )
                    .then(
                        if (!disabled) Modifier.clickable { expanded = !expanded }
                        else Modifier
                    )
                    .padding(horizontal = HyveSpacing.smd, vertical = 3.dp)
            ) {
                Text(
                    text = if (disabled) disabledLabel else currentMode,
                    color = if (disabled) colors.textDisabled else colors.textPrimary,
                    style = HyveTypography.caption
                )
            }
        }
    }

    Box {
        if (disabled && disabledTooltip.isNotEmpty()) {
            TooltipArea(
                tooltip = {
                    Box(
                        modifier = Modifier
                            .background(JewelTheme.globalColors.panelBackground, HyveShapes.card)
                            .border(1.dp, JewelTheme.globalColors.borders.normal, HyveShapes.card)
                            .padding(horizontal = HyveSpacing.sm, vertical = HyveSpacing.xs)
                    ) {
                        Text(
                            text = disabledTooltip,
                            color = JewelTheme.globalColors.text.normal,
                            style = HyveTypography.itemTitle
                        )
                    }
                },
                delayMillis = 500,
                tooltipPlacement = TooltipPlacement.CursorPoint(offset = DpOffset(0.dp, 16.dp))
            ) {
                content()
            }
        } else {
            content()
        }

        // Dropdown overlay — rendered in a Popup so it escapes scroll clipping
        if (expanded && !disabled) {
            Popup(
                onDismissRequest = { expanded = false },
                properties = PopupProperties(focusable = true)
            ) {
                Box(
                    modifier = Modifier
                        .width(160.dp)
                        .background(colors.midnight, HyveShapes.card)
                        .border(1.dp, colors.slate, HyveShapes.card)
                ) {
                    Column {
                        options.forEach { option ->
                            val isSelected = option == currentMode
                            val itemInteraction = remember { MutableInteractionSource() }
                            val itemHovered by itemInteraction.collectIsHoveredAsState()

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .hoverable(itemInteraction)
                                    .clickable {
                                        onSelect(option)
                                        expanded = false
                                    }
                                    .background(
                                        when {
                                            isSelected -> colors.honey.copy(alpha = 0.12f)
                                            itemHovered -> colors.slate.copy(alpha = 0.5f)
                                            else -> Color.Transparent
                                        }
                                    )
                                    .padding(horizontal = HyveSpacing.sm, vertical = HyveSpacing.xs)
                            ) {
                                Text(
                                    text = option,
                                    color = if (isSelected) colors.honey else colors.textPrimary,
                                    style = HyveTypography.caption
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
