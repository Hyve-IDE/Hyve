// Copyright 2026 Hyve. All rights reserved.
package com.hyve.ui.composer.preview

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.TooltipArea
import androidx.compose.foundation.TooltipPlacement
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hyve.common.compose.HyveColors
import com.hyve.common.compose.HyveColorsLight
import com.hyve.common.compose.HyveOpacity
import com.hyve.common.compose.HyveShapes
import com.hyve.common.compose.HyveSpacing
import com.hyve.common.compose.HyveThemeColors
import com.hyve.common.compose.HyveTypography
import com.hyve.ui.canvas.CanvasState
import com.hyve.ui.composer.model.ElementDefinition
import com.hyve.ui.composer.model.applyTo
import com.hyve.ui.core.domain.elements.UIElement
import com.hyve.ui.rendering.layout.ElementBounds
import com.hyve.ui.rendering.layout.LayoutEngine
import com.hyve.ui.rendering.layout.Rect
import com.hyve.ui.rendering.painter.CanvasPainter
import com.hyve.ui.schema.SchemaRegistry
import com.hyve.ui.services.assets.AssetLoader

/**
 * The right-side Preview Panel of the Composer modal.
 *
 * Renders a live visual approximation of the element being edited using the
 * same [CanvasPainter] + [LayoutEngine] pipeline as the main canvas, ensuring
 * pixel-perfect consistency.
 *
 * When the element has children and the "Show children" toggle is active,
 * the preview renders the full element subtree.
 *
 * @param element The element definition being edited (with live edits)
 * @param sourceElement The original UIElement (with children) for rendering
 * @param assetLoader Optional asset loader for textures
 * @param showCode Whether the code output panel is currently visible
 * @param onCodeToggle Callback to toggle code output visibility
 * @param modifier Modifier for the outer container
 */
@Composable
fun PreviewPanel(
    element: ElementDefinition,
    sourceElement: UIElement? = null,
    assetLoader: AssetLoader? = null,
    showCode: Boolean = false,
    onCodeToggle: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val colors = HyveThemeColors.colors
    val hasChildren = sourceElement != null && sourceElement.children.isNotEmpty()
    var showChildren by remember { mutableStateOf(hasChildren) }
    var lightBackground by remember { mutableStateOf(false) }

    // Build the UIElement to render: apply current edits from ElementDefinition
    // back onto the source UIElement, preserving its children.
    val previewElement: UIElement? = remember(element, sourceElement, showChildren) {
        if (sourceElement == null) return@remember null
        val updated = element.applyTo(sourceElement)
        if (showChildren) {
            // Keep children from source element
            updated
        } else {
            // Strip children for element-only preview
            updated.copy(children = emptyList())
        }
    }

    // Layout engine for computing element bounds
    val layoutEngine = remember { LayoutEngine(SchemaRegistry.default()) }

    // Text measurer for CanvasPainter
    val textMeasurer = rememberTextMeasurer()

    // Track texture loading to trigger recomposition when async textures finish
    var textureLoadCounter by remember { mutableStateOf(0) }

    // CanvasPainter instance (reusable, shares texture cache)
    val painter = remember(textMeasurer, assetLoader) {
        CanvasPainter(
            textMeasurer = textMeasurer,
            assetLoader = assetLoader,
            onTextureLoaded = { textureLoadCounter++ }
        )
    }

    // Create a lightweight CanvasState for preview rendering
    val previewState = remember { CanvasState(layoutEngine = layoutEngine) }

    // Update the preview state when the element changes
    LaunchedEffect(previewElement) {
        previewState.setRootElement(previewElement)
    }

    Column(
        modifier = modifier.background(colors.midnight)
    ) {
        // Header: "Preview" label + toggle buttons
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = HyveSpacing.md, vertical = HyveSpacing.mld),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            org.jetbrains.jewel.ui.component.Text(
                text = "Preview",
                style = HyveTypography.caption.copy(
                    color = colors.textSecondary,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.5.sp,
                )
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(HyveSpacing.xs),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Children toggle — only when element has children
                if (hasChildren) {
                    ChildrenToggleButton(
                        active = showChildren,
                        onClick = { showChildren = !showChildren }
                    )
                }

                BackgroundToggleButton(
                    light = lightBackground,
                    onClick = { lightBackground = !lightBackground }
                )

                CodeToggleButton(
                    active = showCode,
                    onClick = onCodeToggle
                )
            }
        }

        // Viewport: Canvas rendering using CanvasPainter
        val canvasBg = if (lightBackground) HyveColorsLight.CanvasBg else HyveColors.CanvasBg
        val canvasBorder = if (lightBackground) HyveColorsLight.CanvasBorder else HyveColors.CanvasBorder
        val dotColor = if (lightBackground)
            HyveColorsLight.CanvasGrid.copy(alpha = HyveOpacity.subtle)
        else
            HyveColors.CanvasGrid.copy(alpha = HyveOpacity.subtle)

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = HyveSpacing.sm, end = HyveSpacing.sm, bottom = HyveSpacing.sm)
                .clip(HyveShapes.dialog)
                .background(canvasBg)
                .border(1.dp, canvasBorder, HyveShapes.dialog),
            contentAlignment = Alignment.Center
        ) {
            // Dot grid overlay
            DotGrid(dotColor = dotColor, modifier = Modifier.fillMaxSize())

            if (previewElement != null) {
                Spacer(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(HyveSpacing.sm)
                        .drawWithCache {
                            // Read reactive state inside drawWithCache to invalidate
                            // the cache when async textures load or children toggle
                            @Suppress("UNUSED_VARIABLE")
                            val textureVersion = textureLoadCounter
                            @Suppress("UNUSED_VARIABLE")
                            val childrenVisible = showChildren

                            // Compute tight fit bounds from the layout
                            val layout = previewState.calculatedLayout.value
                            val fitResult = computeFitBounds(previewElement, layout)

                            // Leave a small margin so the element doesn't press against edges
                            val fitW = size.width * 0.85f
                            val fitH = size.height * 0.85f
                            val scaleX = if (fitResult.rect.width > 0f) fitW / fitResult.rect.width else 1f
                            val scaleY = if (fitResult.rect.height > 0f) fitH / fitResult.rect.height else 1f
                            val naturalZoom = minOf(scaleX, scaleY, MAX_PREVIEW_ZOOM)
                            // Apply minimum zoom floor for full-screen elements
                            val fitZoom = if (fitResult.applyMinZoom) maxOf(naturalZoom, MIN_PREVIEW_ZOOM) else naturalZoom

                            // Center the viewport on the fit rect's center (not its top-left).
                            // For full-screen elements with a zoom floor, this shows the center
                            // area at readable size instead of trying to fit everything.
                            val cx = fitResult.rect.x + fitResult.rect.width / 2f
                            val cy = fitResult.rect.y + fitResult.rect.height / 2f
                            val panX = size.width / 2f - cx * fitZoom
                            val panY = size.height / 2f - cy * fitZoom

                            // Use unclamped setter — preview zoom can be well below
                            // CanvasState.MIN_ZOOM when fitting large element trees
                            previewState.setViewportTransform(fitZoom, Offset(panX, panY))

                            onDrawBehind {
                                with(painter) {
                                    drawPreview(previewState)
                                }
                            }
                        }
                )
            }
        }
    }
}

/**
 * Decorative dot grid overlay for the preview canvas.
 */
@Composable
private fun DotGrid(dotColor: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val spacing = 16.dp.toPx()
        val radius = 1f
        val color = dotColor

        var x = spacing
        while (x < size.width) {
            var y = spacing
            while (y < size.height) {
                drawCircle(
                    color = color,
                    radius = radius,
                    center = Offset(x, y)
                )
                y += spacing
            }
            x += spacing
        }
    }
}

/** Minimum zoom for full-screen elements — keeps text readable (~35% of native). */
private const val MIN_PREVIEW_ZOOM = 0.35f

/** Maximum zoom — upscale small elements to fill viewport but avoid excessive pixelation. */
private const val MAX_PREVIEW_ZOOM = 4f

/**
 * Result of [computeFitBounds]: the rect to fit and whether to apply a zoom floor.
 */
private data class FitBoundsResult(
    val rect: Rect,
    /** When true, the caller should clamp zoom to at least [MIN_PREVIEW_ZOOM]. */
    val applyMinZoom: Boolean = false,
)

/**
 * Compute a tight axis-aligned bounding box for preview zoom fitting.
 *
 * The root element often fills the entire 1920x1080 screen rect (via fill-parent anchor).
 * Zooming to fit that makes elements invisible in the ~230x300 preview panel. Instead:
 * 1. If root has a "meaningful" size (not close to screen), use root bounds directly
 * 2. If root fills screen AND has non-screen-fill children, compute tight AABB of those children
 * 3. Fallback (leaf filling screen): return root bounds with [applyMinZoom] = true.
 *    The caller centres the viewport on the element's centre at a readable zoom level,
 *    so text is visible regardless of alignment.
 */
private fun computeFitBounds(
    root: UIElement,
    layout: Map<UIElement, ElementBounds>,
): FitBoundsResult {
    val screenW = Rect.screen().width
    val screenH = Rect.screen().height
    val rootBounds = layout[root]?.bounds
        ?: return FitBoundsResult(Rect(0f, 0f, 400f, 300f)) // No layout yet

    // Threshold: if root is within 90% of screen size, treat as "fills screen"
    val fillsScreen = rootBounds.width >= screenW * 0.9f &&
        rootBounds.height >= screenH * 0.9f

    if (!fillsScreen) {
        return FitBoundsResult(rootBounds)
    }

    // Root fills screen — try tight AABB of children that don't also fill screen
    if (root.children.isNotEmpty()) {
        val childBounds = root.children.mapNotNull { child ->
            val cb = layout[child]?.bounds ?: return@mapNotNull null
            if (cb.width >= screenW * 0.9f && cb.height >= screenH * 0.9f) return@mapNotNull null
            cb
        }

        if (childBounds.isNotEmpty()) {
            var minX = Float.MAX_VALUE
            var minY = Float.MAX_VALUE
            var maxX = Float.MIN_VALUE
            var maxY = Float.MIN_VALUE
            for (b in childBounds) {
                if (b.x < minX) minX = b.x
                if (b.y < minY) minY = b.y
                if (b.x + b.width > maxX) maxX = b.x + b.width
                if (b.y + b.height > maxY) maxY = b.y + b.height
            }
            return FitBoundsResult(Rect(minX, minY, maxX - minX, maxY - minY))
        }
    }

    // Leaf element or container where all children also fill screen —
    // return actual bounds but flag for minimum zoom. The caller will
    // centre the viewport on the element centre at a readable zoom level.
    return FitBoundsResult(rootBounds, applyMinZoom = true)
}

/**
 * Toggle button for showing/hiding children in context preview.
 * Icon: parent box + child box inside.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChildrenToggleButton(
    active: Boolean,
    onClick: () -> Unit
) {
    val colors = HyveThemeColors.colors
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    val borderColor = when {
        active -> colors.honey
        isHovered -> colors.slateLight
        else -> colors.slate
    }
    val bgColor = when {
        active -> colors.honey.copy(alpha = HyveOpacity.light)
        isHovered -> colors.slate.copy(alpha = HyveOpacity.moderate)
        else -> androidx.compose.ui.graphics.Color.Transparent
    }
    val iconColor = when {
        active -> colors.honey
        isHovered -> colors.textPrimary
        else -> colors.textSecondary
    }

    TooltipArea(
        tooltip = {
            Box(
                modifier = Modifier
                    .background(colors.deepNight, HyveShapes.card)
                    .border(1.dp, colors.slate, HyveShapes.card)
                    .padding(horizontal = HyveSpacing.sm, vertical = HyveSpacing.xs)
            ) {
                org.jetbrains.jewel.ui.component.Text(
                    text = if (active) "Hide children" else "Show children",
                    style = HyveTypography.caption.copy(
                        color = colors.textSecondary,
                    )
                )
            }
        },
        tooltipPlacement = TooltipPlacement.CursorPoint(offset = DpOffset(0.dp, 16.dp)),
        delayMillis = 400,
    ) {
        Box(
            modifier = Modifier
                .hoverable(interactionSource)
                .clip(HyveShapes.card)
                .background(bgColor, HyveShapes.card)
                .border(1.dp, borderColor, HyveShapes.card)
                .clickable(onClick = onClick)
                .padding(horizontal = HyveSpacing.smd, vertical = HyveSpacing.xxs),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.size(12.dp)) {
                val w = size.width
                val h = size.height
                val strokeW = 1.4.dp.toPx()

                // Outer parent rectangle
                drawRect(
                    color = iconColor,
                    topLeft = Offset(0f, 0f),
                    size = androidx.compose.ui.geometry.Size(w, h),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = strokeW)
                )

                // Inner child rectangle
                val inset = w * 0.25f
                drawRect(
                    color = iconColor,
                    topLeft = Offset(inset, inset),
                    size = androidx.compose.ui.geometry.Size(w - inset * 2, h - inset * 2),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(width = strokeW)
                )
            }
        }
    }
}

/**
 * Light/dark background toggle for the preview canvas.
 * Sun icon (light) / moon icon (dark).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BackgroundToggleButton(
    light: Boolean,
    onClick: () -> Unit
) {
    val colors = HyveThemeColors.colors
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    val borderColor = when {
        light -> colors.honey
        isHovered -> colors.slateLight
        else -> colors.slate
    }
    val bgColor = when {
        light -> colors.honey.copy(alpha = HyveOpacity.light)
        isHovered -> colors.slate.copy(alpha = HyveOpacity.moderate)
        else -> Color.Transparent
    }
    val iconColor = when {
        light -> colors.honey
        isHovered -> colors.textPrimary
        else -> colors.textSecondary
    }

    TooltipArea(
        tooltip = {
            Box(
                modifier = Modifier
                    .background(colors.deepNight, HyveShapes.card)
                    .border(1.dp, colors.slate, HyveShapes.card)
                    .padding(horizontal = HyveSpacing.sm, vertical = HyveSpacing.xs)
            ) {
                org.jetbrains.jewel.ui.component.Text(
                    text = if (light) "Dark background" else "Light background",
                    style = HyveTypography.caption.copy(
                        color = colors.textSecondary,
                    )
                )
            }
        },
        tooltipPlacement = TooltipPlacement.CursorPoint(offset = DpOffset(0.dp, 16.dp)),
        delayMillis = 400,
    ) {
        Box(
            modifier = Modifier
                .hoverable(interactionSource)
                .clip(HyveShapes.card)
                .background(bgColor, HyveShapes.card)
                .border(1.dp, borderColor, HyveShapes.card)
                .clickable(onClick = onClick)
                .padding(horizontal = HyveSpacing.smd, vertical = HyveSpacing.xxs),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.size(12.dp)) {
                val w = size.width
                val h = size.height
                val cx = w / 2f
                val cy = h / 2f
                val strokeW = 1.4.dp.toPx()

                if (light) {
                    // Sun icon: circle + rays
                    val sunR = w * 0.22f
                    drawCircle(color = iconColor, radius = sunR, center = Offset(cx, cy),
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = strokeW))
                    // 8 rays
                    val rayInner = w * 0.34f
                    val rayOuter = w * 0.48f
                    for (i in 0 until 8) {
                        val angle = Math.toRadians(i * 45.0)
                        val cos = kotlin.math.cos(angle).toFloat()
                        val sin = kotlin.math.sin(angle).toFloat()
                        drawLine(
                            color = iconColor,
                            start = Offset(cx + cos * rayInner, cy + sin * rayInner),
                            end = Offset(cx + cos * rayOuter, cy + sin * rayOuter),
                            strokeWidth = strokeW,
                            cap = StrokeCap.Round
                        )
                    }
                } else {
                    // Moon icon: crescent via two overlapping arcs
                    val moonR = w * 0.35f
                    drawCircle(color = iconColor, radius = moonR, center = Offset(cx, cy),
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = strokeW))
                    // Mask circle to create crescent — filled with background color
                    val maskOffset = w * 0.22f
                    drawCircle(
                        color = HyveColors.CanvasBg,
                        radius = moonR * 0.85f,
                        center = Offset(cx + maskOffset, cy - maskOffset * 0.3f)
                    )
                }
            }
        }
    }
}

/**
 * Terminal icon (`>_`) toggle button for the code output panel.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CodeToggleButton(
    active: Boolean,
    onClick: () -> Unit
) {
    val colors = HyveThemeColors.colors
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    val borderColor = when {
        active -> colors.honey
        isHovered -> colors.slateLight
        else -> colors.slate
    }
    val bgColor = when {
        active -> colors.honey.copy(alpha = HyveOpacity.light)
        isHovered -> colors.slate.copy(alpha = HyveOpacity.moderate)
        else -> androidx.compose.ui.graphics.Color.Transparent
    }
    val iconColor = when {
        active -> colors.honey
        isHovered -> colors.textPrimary
        else -> colors.textSecondary
    }

    TooltipArea(
        tooltip = {
            Box(
                modifier = Modifier
                    .background(colors.deepNight, HyveShapes.card)
                    .border(1.dp, colors.slate, HyveShapes.card)
                    .padding(horizontal = HyveSpacing.sm, vertical = HyveSpacing.xs)
            ) {
                org.jetbrains.jewel.ui.component.Text(
                    text = "Toggle .ui output",
                    style = HyveTypography.caption.copy(
                        color = colors.textSecondary,
                    )
                )
            }
        },
        tooltipPlacement = TooltipPlacement.CursorPoint(offset = DpOffset(0.dp, 16.dp)),
        delayMillis = 400,
    ) {
        Box(
            modifier = Modifier
                .hoverable(interactionSource)
                .clip(HyveShapes.card)
                .background(bgColor, HyveShapes.card)
                .border(1.dp, borderColor, HyveShapes.card)
                .clickable(onClick = onClick)
                .padding(horizontal = HyveSpacing.smd, vertical = HyveSpacing.xxs),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.size(12.dp)) {
                val w = size.width
                val h = size.height
                val strokeW = 1.6.dp.toPx()
                // ">" chevron on the left
                drawLine(
                    color = iconColor,
                    start = Offset(w * 0.1f, h * 0.2f),
                    end = Offset(w * 0.45f, h * 0.5f),
                    strokeWidth = strokeW,
                    cap = StrokeCap.Round
                )
                drawLine(
                    color = iconColor,
                    start = Offset(w * 0.45f, h * 0.5f),
                    end = Offset(w * 0.1f, h * 0.8f),
                    strokeWidth = strokeW,
                    cap = StrokeCap.Round
                )
                // "_" underscore on the right
                drawLine(
                    color = iconColor,
                    start = Offset(w * 0.5f, h * 0.8f),
                    end = Offset(w * 0.9f, h * 0.8f),
                    strokeWidth = strokeW,
                    cap = StrokeCap.Round
                )
            }
        }
    }
}
