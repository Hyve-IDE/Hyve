// Copyright 2026 Hyve. All rights reserved.
package com.hyve.ui.rendering.painter

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.hyve.ui.core.domain.anchor.AnchorDimension
import com.hyve.ui.core.domain.elements.UIElement
import com.hyve.ui.core.domain.properties.PropertyValue
import com.hyve.ui.core.domain.styles.StyleReference
import com.hyve.ui.rendering.layout.ElementBounds
import com.hyve.ui.canvas.CanvasState
import com.hyve.ui.canvas.ScreenshotMode
import com.hyve.ui.components.validation.ValidationPanelState
import com.hyve.ui.registry.ElementTypeRegistry
import com.hyve.ui.registry.RenderStrategy
import com.hyve.ui.services.assets.AssetLoader
import com.hyve.ui.services.items.ItemDefinition
import com.hyve.ui.services.items.ItemRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Parse a hex color string with optional alpha.
 * Delegates to the canonical implementation in ColorConversions.
 */
internal fun parseHexColor(input: String, default: Color): Color =
    com.hyve.ui.components.colorpicker.parseHexColor(input, default)

/**
 * Convert PropertyValue.Color to Compose Color.
 */
private fun PropertyValue.Color.toComposeColor(): Color =
    com.hyve.ui.components.colorpicker.parseHexColor(hex).copy(alpha = alpha ?: 1.0f)

/**
 * Extract a Color from any PropertyValue. Handles Color, Text (hex string), and Unknown.
 */
internal fun colorFromValue(value: PropertyValue?, default: Color): Color {
    return when (value) {
        is PropertyValue.Color -> try { value.toComposeColor() } catch (_: Exception) { default }
        is PropertyValue.Text -> parseHexColor(value.value, default)
        is PropertyValue.Unknown -> parseHexColor(value.raw, default)
        else -> default
    }
}

/**
 * Get a value from a PropertyValue.Tuple by key (case-insensitive).
 */
internal fun PropertyValue.Tuple.get(key: String): PropertyValue? {
    return values.entries.find { it.key.equals(key, ignoreCase = true) }?.value
}

/**
 * Resolve a property value to a Tuple for style rendering.
 * With the dual-document architecture (DL-001), the resolved tree already contains
 * concrete Tuples where @StyleRef references were. This function handles:
 * - PropertyValue.Tuple: returned as-is (normal case with resolved tree)
 * - PropertyValue.Style with Inline ref: converted to Tuple
 * - PropertyValue.Style with Local/Imported ref: null (unresolved fallback)
 * - Everything else: null
 */
internal fun resolveStyleToTuple(value: PropertyValue?): PropertyValue.Tuple? {
    return when (value) {
        is PropertyValue.Tuple -> value
        is PropertyValue.Style -> {
            when (val ref = value.reference) {
                is StyleReference.Inline -> {
                    PropertyValue.Tuple(ref.properties.mapKeys { it.key.value })
                }
                // Local/Imported refs should already be resolved by VariableAwareParser.
                // If they appear here, it means resolution failed — return null for graceful degradation.
                else -> null
            }
        }
        else -> null
    }
}

/**
 * Read a Color property from a UIElement, with fallback to default.
 * Handles PropertyValue.Color, hex strings in PropertyValue.Text and Unknown,
 * including the Hytale #RRGGBB(alpha) format.
 * Never crashes — returns default on any error or type mismatch.
 */
internal fun UIElement.colorProperty(name: String, default: Color): Color {
    return colorFromValue(getProperty(name), default)
}

/**
 * Read a Number property as Float, with fallback to default.
 * Never crashes — returns default on any error or type mismatch.
 */
internal fun UIElement.numberProperty(name: String, default: Float): Float {
    return when (val prop = getProperty(name)) {
        is PropertyValue.Number -> prop.value.toFloat()
        else -> default
    }
}

/** Read a Number property with an alias fallback name (e.g. "MinValue" with alias "Min"). */
internal fun UIElement.numberPropertyOrAlias(name: String, alias: String, default: Float): Float {
    val prop = getProperty(name) ?: getProperty(alias)
    return when (prop) {
        is PropertyValue.Number -> prop.value.toFloat()
        else -> default
    }
}

/**
 * Read a Boolean property, with fallback to default.
 * Never crashes — returns default on any error or type mismatch.
 */
internal fun UIElement.booleanProperty(name: String, default: Boolean): Boolean {
    return when (val prop = getProperty(name)) {
        is PropertyValue.Boolean -> prop.value
        else -> default
    }
}

/**
 * Read a Text property, with fallback to default.
 * Never crashes — returns default on any error or type mismatch.
 */
internal fun UIElement.textProperty(name: String, default: String): String {
    return when (val prop = getProperty(name)) {
        is PropertyValue.Text -> prop.value
        else -> default
    }
}

/**
 * Extract mask texture path from MaskTexturePath property.
 * Returns texture path string or null.
 * Handles PropertyValue.Text, PropertyValue.ImagePath, and PropertyValue.Unknown
 * with image extension (.png, .jpg, .jpeg, .tga).
 */
/**
 * Resolve ProgressBar fill direction from explicit FillDirection, or from Direction + Alignment shorthands.
 */
internal fun resolveProgressBarFillDirection(
    explicitFillDir: String, direction: String, alignment: String
): String {
    if (explicitFillDir.isNotEmpty()) return explicitFillDir
    val isVertical = alignment.equals("Vertical", ignoreCase = true)
    return when {
        isVertical && direction == "Start" -> "TopToBottom"
        isVertical && direction == "End" -> "BottomToTop"
        isVertical -> "BottomToTop" // default vertical
        direction == "Start" -> "LeftToRight"
        direction == "End" -> "RightToLeft"
        else -> "LeftToRight"
    }
}

/**
 * Apply password character masking to text field content.
 * Returns raw text unmodified when passwordChar is empty or text equals placeholderText.
 */
internal fun applyPasswordMask(
    rawText: String, passwordChar: String, placeholderText: String
): String {
    return if (passwordChar.isNotEmpty() && rawText.isNotEmpty() && rawText != placeholderText) {
        passwordChar.first().toString().repeat(rawText.length)
    } else rawText
}

internal fun UIElement.maskTexturePath(): String? {
    return when (val prop = getProperty("MaskTexturePath")) {
        is PropertyValue.Text -> {
            val value = prop.value
            if (value.isNotBlank() && (value.contains("/") || value.contains("\\") || value.endsWith(".png"))) value else null
        }
        is PropertyValue.ImagePath -> prop.path.takeIf { it.isNotBlank() }
        is PropertyValue.Unknown -> {
            if (prop.raw.matches(Regex(".*\\.(png|jpg|jpeg|tga)$", RegexOption.IGNORE_CASE))) prop.raw else null
        }
        else -> null
    }
}

/**
 * Draw a nine-patch (9-slice) image to the canvas.
 * Divides the source image into 9 regions using border insets:
 * - 4 corners (fixed size), 4 edges (stretched in one axis), 1 center (stretched both axes)
 * Used by 62 vanilla .ui files for button and panel backgrounds.
 */
private fun DrawScope.drawNinePatch(
    image: ImageBitmap,
    dstX: Float, dstY: Float,
    dstWidth: Float, dstHeight: Float,
    horizontalBorder: Float, verticalBorder: Float
) {
    val srcWidth = image.width.toFloat()
    val srcHeight = image.height.toFloat()

    // Guard: if destination is too small for borders, fall back to simple stretch
    if (dstWidth < 2 * horizontalBorder || dstHeight < 2 * verticalBorder) {
        drawImage(image, dstOffset = IntOffset(dstX.toInt(), dstY.toInt()),
            dstSize = IntSize(dstWidth.toInt(), dstHeight.toInt()))
        return
    }

    val hB = horizontalBorder.coerceIn(0f, srcWidth / 2f)
    val vB = verticalBorder.coerceIn(0f, srcHeight / 2f)
    val cW = (dstWidth - 2 * hB).coerceAtLeast(0f)
    val cH = (dstHeight - 2 * vB).coerceAtLeast(0f)
    val sCW = (srcWidth - 2 * hB).coerceAtLeast(0f)
    val sCH = (srcHeight - 2 * vB).coerceAtLeast(0f)

    val hI = hB.toInt(); val vI = vB.toInt()
    val sW = srcWidth.toInt(); val sH = srcHeight.toInt()
    val dX = dstX.toInt(); val dY = dstY.toInt()

    // Top row
    drawImage(image, IntOffset(0, 0), IntSize(hI, vI), IntOffset(dX, dY), IntSize(hI, vI))
    drawImage(image, IntOffset(hI, 0), IntSize(sCW.toInt(), vI), IntOffset(dX + hI, dY), IntSize(cW.toInt(), vI))
    drawImage(image, IntOffset(sW - hI, 0), IntSize(hI, vI), IntOffset((dstX + dstWidth - hB).toInt(), dY), IntSize(hI, vI))
    // Middle row
    drawImage(image, IntOffset(0, vI), IntSize(hI, sCH.toInt()), IntOffset(dX, dY + vI), IntSize(hI, cH.toInt()))
    drawImage(image, IntOffset(hI, vI), IntSize(sCW.toInt(), sCH.toInt()), IntOffset(dX + hI, dY + vI), IntSize(cW.toInt(), cH.toInt()))
    drawImage(image, IntOffset(sW - hI, vI), IntSize(hI, sCH.toInt()), IntOffset((dstX + dstWidth - hB).toInt(), dY + vI), IntSize(hI, cH.toInt()))
    // Bottom row
    drawImage(image, IntOffset(0, sH - vI), IntSize(hI, vI), IntOffset(dX, (dstY + dstHeight - vB).toInt()), IntSize(hI, vI))
    drawImage(image, IntOffset(hI, sH - vI), IntSize(sCW.toInt(), vI), IntOffset(dX + hI, (dstY + dstHeight - vB).toInt()), IntSize(cW.toInt(), vI))
    drawImage(image, IntOffset(sW - hI, sH - vI), IntSize(hI, vI), IntOffset((dstX + dstWidth - hB).toInt(), (dstY + dstHeight - vB).toInt()), IntSize(hI, vI))
}

/**
 * Helper for computing Stretch mode source/dest rects in drawImage.
 */
private data class StretchRects(
    val srcOffset: IntOffset,
    val srcSize: IntSize,
    val dstOffset: IntOffset,
    val dstSize: IntSize
)

/**
 * Painter for rendering UI elements on the Compose canvas.
 *
 * Supports element type rendering:
 * - Tier 1: Group, Label, Button, TextField, Image (full property-driven)
 * - Tier 2: Slider, CheckBox, ProgressBar, ScrollView, DropdownBox,
 *   TabPanel, Tooltip, MultilineTextField, NumberField (property-driven)
 * - Fallback: Specialized placeholders for known Hytale types, generic for unknown
 *
 * Rendering strategy:
 * 1. Draw background/shape
 * 2. Draw content (text, image, etc.)
 * 3. Draw selection highlight if selected
 * 4. Draw children recursively
 */
class CanvasPainter(
    private val textMeasurer: TextMeasurer,
    private val validationState: ValidationPanelState? = null,
    private val assetLoader: AssetLoader? = null,
    private val itemRegistry: ItemRegistry? = null,
    private val onTextureLoaded: (() -> Unit)? = null
) {
    // Coroutine scope for async asset loading
    private val loadingScope = CoroutineScope(Dispatchers.Default)

    // Cache for loaded textures (path -> ImageBitmap)
    private val loadedTextures = mutableMapOf<String, ImageBitmap?>()

    // Track paths currently being loaded to avoid duplicate requests
    private val loadingPaths = mutableSetOf<String>()

    // Cache for loaded item definitions (itemId -> ItemDefinition?)
    private val itemCache = mutableMapOf<String, ItemDefinition?>()

    // Track item IDs currently being loaded to avoid duplicate lookups
    private val loadingItems = mutableSetOf<String>()

    // Cache of loaded screenshot images keyed by ScreenshotMode
    private val screenshotImages = mutableMapOf<ScreenshotMode, ImageBitmap?>()

    /**
     * Load a screenshot image for the given mode, caching the result.
     */
    private fun getScreenshotImage(mode: ScreenshotMode): ImageBitmap? {
        return screenshotImages.getOrPut(mode) {
            try {
                val inputStream = this::class.java.getResourceAsStream(mode.resourcePath)
                inputStream?.use { stream ->
                    val bytes = stream.readBytes()
                    org.jetbrains.skia.Image.makeFromEncoded(bytes).toComposeImageBitmap()
                }
            } catch (_: Exception) {
                null
            }
        }
    }

    // Text measurement cache to avoid expensive re-measurement during pan/zoom
    // Key: text + fontSize (as Int to avoid float precision issues)
    // Using LinkedHashMap with access-order for LRU behavior
    private val textMeasurementCache = object : LinkedHashMap<TextCacheKey, TextLayoutResult>(
        128, 0.75f, true
    ) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<TextCacheKey, TextLayoutResult>?): Boolean {
            return size > 256 // Keep max 256 cached measurements
        }
    }

    /**
     * Cache key for text measurements.
     * Uses fontSize as Int (multiplied by 100) to avoid float comparison issues.
     */
    private data class TextCacheKey(
        val text: String,
        val fontSizeScaled: Int, // fontSize * 100 as Int
        val color: Long, // Color as Long for efficient comparison
        val fontWeight: Int = 400, // FontWeight value (400=Normal, 700=Bold)
        val fontStyle: Int = 0, // 0=Normal, 1=Italic
        val fontFamily: Int = 0, // 0=SansSerif, 1=Serif, 2=Monospace
        val letterSpacingScaled: Int = 0, // letterSpacing * 100 as Int
        val maxWidthPx: Int = Int.MAX_VALUE // max width for wrapping (MAX_VALUE = no wrap)
    )

    /**
     * Measure text with caching. Uses the cache to avoid re-measuring unchanged text.
     */
    private fun measureTextCached(
        text: String,
        style: TextStyle,
        maxWidthPx: Int = Int.MAX_VALUE
    ): TextLayoutResult {
        val key = TextCacheKey(
            text = text,
            fontSizeScaled = (style.fontSize.value * 100).toInt(),
            color = style.color.value.toLong(),
            fontWeight = style.fontWeight?.weight ?: 400,
            fontStyle = if (style.fontStyle == FontStyle.Italic) 1 else 0,
            fontFamily = when (style.fontFamily) {
                FontFamily.Serif -> 1; FontFamily.Monospace -> 2; else -> 0
            },
            letterSpacingScaled = if (style.letterSpacing == TextUnit.Unspecified) 0
                else (style.letterSpacing.value * 100).toInt(),
            maxWidthPx = maxWidthPx
        )
        return textMeasurementCache.getOrPut(key) {
            if (maxWidthPx < Int.MAX_VALUE) {
                textMeasurer.measure(
                    text, style,
                    constraints = androidx.compose.ui.unit.Constraints(maxWidth = maxWidthPx)
                )
            } else {
                textMeasurer.measure(text, style)
            }
        }
    }

    /**
     * Clear the text measurement cache.
     * Call this when the document changes significantly.
     */
    fun clearTextCache() {
        textMeasurementCache.clear()
    }

    /**
     * Measure and draw text without position-dependent constraints.
     * Compose's drawText(textMeasurer, text, topLeft, style) defaults to constraining
     * text within (drawScope.width - topLeft.x), causing text to squish near viewport edges.
     * This helper pre-measures at natural size and draws the TextLayoutResult directly.
     */
    private fun DrawScope.drawTextStable(
        text: String,
        topLeft: Offset,
        style: TextStyle,
        maxWidthPx: Int = Int.MAX_VALUE
    ) {
        val layout = measureTextCached(text, style, maxWidthPx)
        drawText(layout, topLeft = topLeft)
    }
    companion object {
        // Canvas background (ensures pointer events work on empty areas)
        val CANVAS_BACKGROUND = Color(0xFFF5F5F5)
        val CANVAS_BACKGROUND_DARK = Color(0xFF0A0A12)

        // Default colors
        val DEFAULT_BACKGROUND = Color(0xFFEEEEEE)
        val DEFAULT_BORDER = Color(0xFF999999)
        val DEFAULT_TEXT = Color(0xFF000000)
        val SELECTION_COLOR = Color(0xFF2196F3)
        val SELECTION_STROKE_WIDTH = 2f

        // Grid styling
        val GRID_COLOR = Color(0xFFCCCCCC)
        val GRID_MAJOR_COLOR = Color(0xFF999999)
        val GRID_COLOR_DARK = Color(0xFF2A2A3E)
        val GRID_MAJOR_COLOR_DARK = Color(0xFF3E3E58)
        const val GRID_SIZE = 20f
        const val GRID_MAJOR_INTERVAL = 5

        // Resize handle styling
        const val HANDLE_SIZE = 8f
        val HANDLE_COLOR = Color.White
        val HANDLE_BORDER_COLOR = SELECTION_COLOR

        // Button styling
        const val BUTTON_CORNER_RADIUS = 4f
        val BUTTON_BACKGROUND = Color(0xFF2196F3)
        val BUTTON_TEXT_COLOR = Color.White

        // TextField styling
        val TEXTFIELD_BACKGROUND = Color.White
        val TEXTFIELD_BORDER = Color(0xFFCCCCCC)

        // Viewport indicator styling
        val VIEWPORT_BORDER_COLOR = Color(0xFF4CAF50) // Green border for safe zone
        val VIEWPORT_LABEL_COLOR = Color(0xFF4CAF50)
        const val VIEWPORT_BORDER_WIDTH = 2f

        // Canvas bounds indicator (outside workspace area)
        val CANVAS_BOUNDS_COLOR = Color(0x20000000) // Semi-transparent overlay for out-of-bounds
        val CANVAS_BOUNDS_COLOR_DARK = Color(0x30000000)
    }

    /**
     * Draw just the element tree for preview purposes.
     *
     * Unlike [drawCanvas], this skips all canvas chrome (background, grid,
     * viewport indicator, screenshot overlay, bounds overlay). Used by the
     * Composer modal's preview panel to reuse the exact same rendering logic
     * as the main canvas.
     */
    fun DrawScope.drawPreview(state: CanvasState) {
        val root = state.rootElement.value
        val layout = state.calculatedLayout.value

        if (root != null) {
            drawElementTree(root, layout, state)
        }
    }

    /**
     * Draw the entire canvas including grid and all elements
     */
    fun DrawScope.drawCanvas(
        state: CanvasState,
        showGrid: Boolean = true,
        darkCanvas: Boolean = false,
        showScreenshot: Boolean = false,
        screenshotOpacity: Float = 0.3f,
        screenshotMode: ScreenshotMode = ScreenshotMode.NO_HUD
    ) {
        // Draw canvas background first - this ensures pointer events work everywhere
        drawRect(
            color = if (darkCanvas) CANVAS_BACKGROUND_DARK else CANVAS_BACKGROUND,
            topLeft = Offset.Zero,
            size = size
        )

        // Draw out-of-bounds overlay (areas outside canvas bounds)
        drawCanvasBoundsOverlay(state, darkCanvas)

        // Draw screenshot reference overlay (if enabled)
        if (showScreenshot) {
            drawScreenshotOverlay(state, screenshotOpacity, screenshotMode)
        }

        // Draw grid if enabled
        if (showGrid) {
            drawGrid(state, darkCanvas)
        }

        // Draw viewport indicator (1920x1080 safe zone)
        drawViewportIndicator(state)

        // Draw all elements
        val root = state.rootElement.value
        val layout = state.calculatedLayout.value

        if (root != null) {
            drawElementTree(root, layout, state)
        }
    }

    /**
     * Draw the screenshot reference image scaled to fit the viewport with opacity control.
     * The image is positioned within the 1920x1080 viewport area.
     */
    private fun DrawScope.drawScreenshotOverlay(state: CanvasState, opacity: Float, mode: ScreenshotMode) {
        val image = getScreenshotImage(mode) ?: return

        val zoom = state.zoom.value
        val pan = state.panOffset.value

        // Calculate viewport position in screen coordinates
        val viewportScreenX = 0f * zoom + pan.x
        val viewportScreenY = 0f * zoom + pan.y
        val viewportScreenWidth = CanvasState.VIEWPORT_WIDTH * zoom
        val viewportScreenHeight = CanvasState.VIEWPORT_HEIGHT * zoom

        // Calculate image scaling to fit viewport while maintaining aspect ratio
        val imageAspect = image.width.toFloat() / image.height.toFloat()
        val viewportAspect = CanvasState.VIEWPORT_WIDTH / CanvasState.VIEWPORT_HEIGHT

        val (srcWidth, srcHeight, srcOffsetX, srcOffsetY) = if (imageAspect > viewportAspect) {
            // Image is wider - crop horizontally (fit to height)
            val cropWidth = (image.height * viewportAspect).toInt()
            val offsetX = (image.width - cropWidth) / 2
            intArrayOf(cropWidth, image.height, offsetX, 0)
        } else {
            // Image is taller - crop vertically (fit to width)
            val cropHeight = (image.width / viewportAspect).toInt()
            val offsetY = (image.height - cropHeight) / 2
            intArrayOf(image.width, cropHeight, 0, offsetY)
        }

        // Draw the image with opacity
        drawImage(
            image = image,
            srcOffset = IntOffset(srcOffsetX, srcOffsetY),
            srcSize = IntSize(srcWidth, srcHeight),
            dstOffset = IntOffset(viewportScreenX.toInt(), viewportScreenY.toInt()),
            dstSize = IntSize(viewportScreenWidth.toInt(), viewportScreenHeight.toInt()),
            alpha = opacity
        )
    }

    /**
     * Draw overlay for areas outside the canvas bounds
     */
    private fun DrawScope.drawCanvasBoundsOverlay(state: CanvasState, darkCanvas: Boolean = false) {
        val zoom = state.zoom.value
        val pan = state.panOffset.value
        val boundsColor = if (darkCanvas) CANVAS_BOUNDS_COLOR_DARK else CANVAS_BOUNDS_COLOR

        // Convert canvas bounds to screen coordinates
        val minX = CanvasState.CANVAS_MIN_X * zoom + pan.x
        val minY = CanvasState.CANVAS_MIN_Y * zoom + pan.y
        val maxX = CanvasState.CANVAS_MAX_X * zoom + pan.x
        val maxY = CanvasState.CANVAS_MAX_Y * zoom + pan.y

        // Draw semi-transparent overlay for out-of-bounds areas
        // Left side
        if (minX > 0) {
            drawRect(
                color = boundsColor,
                topLeft = Offset.Zero,
                size = Size(minX, size.height)
            )
        }
        // Right side
        if (maxX < size.width) {
            drawRect(
                color = boundsColor,
                topLeft = Offset(maxX, 0f),
                size = Size(size.width - maxX, size.height)
            )
        }
        // Top side (between left and right bounds)
        if (minY > 0) {
            val leftEdge = maxOf(0f, minX)
            val rightEdge = minOf(size.width, maxX)
            if (rightEdge > leftEdge) {
                drawRect(
                    color = boundsColor,
                    topLeft = Offset(leftEdge, 0f),
                    size = Size(rightEdge - leftEdge, minY)
                )
            }
        }
        // Bottom side (between left and right bounds)
        if (maxY < size.height) {
            val leftEdge = maxOf(0f, minX)
            val rightEdge = minOf(size.width, maxX)
            if (rightEdge > leftEdge) {
                drawRect(
                    color = boundsColor,
                    topLeft = Offset(leftEdge, maxY),
                    size = Size(rightEdge - leftEdge, size.height - maxY)
                )
            }
        }
    }

    /**
     * Draw the viewport indicator showing the 1920x1080 safe zone
     */
    private fun DrawScope.drawViewportIndicator(state: CanvasState) {
        val zoom = state.zoom.value
        val pan = state.panOffset.value

        // Viewport is at (0,0) to (1920,1080) in canvas coordinates
        val viewportScreenX = 0f * zoom + pan.x
        val viewportScreenY = 0f * zoom + pan.y
        val viewportScreenWidth = CanvasState.VIEWPORT_WIDTH * zoom
        val viewportScreenHeight = CanvasState.VIEWPORT_HEIGHT * zoom

        // Check if viewport is at least partially visible on screen
        val viewportRight = viewportScreenX + viewportScreenWidth
        val viewportBottom = viewportScreenY + viewportScreenHeight
        val isVisible = viewportRight > 0 && viewportScreenX < size.width &&
                        viewportBottom > 0 && viewportScreenY < size.height

        if (!isVisible) return

        // Draw viewport border
        drawRect(
            color = VIEWPORT_BORDER_COLOR,
            topLeft = Offset(viewportScreenX, viewportScreenY),
            size = Size(viewportScreenWidth, viewportScreenHeight),
            style = Stroke(width = VIEWPORT_BORDER_WIDTH)
        )

        // Draw corner markers for better visibility
        val markerLength = 20f * zoom
        val corners = listOf(
            Offset(viewportScreenX, viewportScreenY), // Top-left
            Offset(viewportScreenX + viewportScreenWidth, viewportScreenY), // Top-right
            Offset(viewportScreenX, viewportScreenY + viewportScreenHeight), // Bottom-left
            Offset(viewportScreenX + viewportScreenWidth, viewportScreenY + viewportScreenHeight) // Bottom-right
        )

        corners.forEachIndexed { index, corner ->
            val (hDir, vDir) = when (index) {
                0 -> 1f to 1f   // Top-left: extend right and down
                1 -> -1f to 1f  // Top-right: extend left and down
                2 -> 1f to -1f  // Bottom-left: extend right and up
                else -> -1f to -1f // Bottom-right: extend left and up
            }

            // Horizontal marker
            drawLine(
                color = VIEWPORT_BORDER_COLOR,
                start = corner,
                end = Offset(corner.x + markerLength * hDir, corner.y),
                strokeWidth = VIEWPORT_BORDER_WIDTH * 2
            )
            // Vertical marker
            drawLine(
                color = VIEWPORT_BORDER_COLOR,
                start = corner,
                end = Offset(corner.x, corner.y + markerLength * vDir),
                strokeWidth = VIEWPORT_BORDER_WIDTH * 2
            )
        }

        // Draw "1920x1080" label ABOVE the viewport (hovering over the top edge)
        val labelStyle = TextStyle(
            color = VIEWPORT_LABEL_COLOR,
            fontSize = 11.sp,
            background = Color(0xCCFFFFFF)
        )
        val labelText = "1920x1080 Viewport"
        val labelLayout = measureTextCached(labelText, labelStyle)
        val labelX = viewportScreenX + 8f
        val labelY = viewportScreenY - labelLayout.size.height - 4f  // Position above viewport with 4px gap

        // Only draw if label position is reasonably on screen
        if (labelX >= 0 && labelX < size.width - 100 && labelY >= -labelLayout.size.height && labelY < size.height) {
            drawText(
                textMeasurer = textMeasurer,
                text = labelText,
                topLeft = Offset(labelX, labelY),
                style = labelStyle
            )
        }
    }

    /**
     * Draw grid overlay - scales with zoom, fixed position on screen
     * Grid represents canvas coordinates (pixels in Hytale UI space)
     *
     * Optimizations:
     * - Skip minor grid lines when zoomed out (too dense to be useful)
     * - Fade out minor lines progressively as zoom decreases
     * - Maximum line count cap to prevent performance issues
     */
    private fun DrawScope.drawGrid(state: CanvasState, darkCanvas: Boolean = false) {
        val zoom = state.zoom.value
        val pan = state.panOffset.value
        val gridColor = if (darkCanvas) GRID_COLOR_DARK else GRID_COLOR
        val gridMajorColor = if (darkCanvas) GRID_MAJOR_COLOR_DARK else GRID_MAJOR_COLOR

        // Grid size in screen pixels (scales with zoom)
        val gridSize = GRID_SIZE * zoom

        // Performance optimization: determine if we should draw minor grid lines
        // When gridSize is small (zoomed out), minor lines become too dense
        val minGridSizeForMinorLines = 8f // Don't draw minor lines if grid cells are smaller than 8 screen pixels
        val shouldDrawMinorLines = gridSize >= minGridSizeForMinorLines

        // Calculate minor line alpha based on zoom level (fade out as we zoom out)
        val minorLineAlpha = if (shouldDrawMinorLines) {
            ((gridSize - minGridSizeForMinorLines) / minGridSizeForMinorLines).coerceIn(0f, 1f)
        } else {
            0f
        }

        // Calculate where grid lines should start based on pan offset
        // This creates an "infinite grid" effect that stays aligned with canvas coordinates
        val startX = pan.x % gridSize
        val startY = pan.y % gridSize

        // Calculate the grid index offset for major line detection
        val indexOffsetX = ((pan.x / gridSize).toInt() % GRID_MAJOR_INTERVAL + GRID_MAJOR_INTERVAL) % GRID_MAJOR_INTERVAL
        val indexOffsetY = ((pan.y / gridSize).toInt() % GRID_MAJOR_INTERVAL + GRID_MAJOR_INTERVAL) % GRID_MAJOR_INTERVAL

        // Performance cap: limit total number of lines to prevent excessive draw calls
        val maxLinesPerAxis = 200
        val effectiveGridSize = if (size.width / gridSize > maxLinesPerAxis || size.height / gridSize > maxLinesPerAxis) {
            // If we'd draw too many lines, use major grid spacing only
            gridSize * GRID_MAJOR_INTERVAL
        } else {
            gridSize
        }

        // When using enlarged grid, all lines are effectively "major"
        val usingEnlargedGrid = effectiveGridSize != gridSize

        // Vertical lines
        var x = if (usingEnlargedGrid) {
            // Align to major grid when using enlarged grid
            val majorGridSize = gridSize * GRID_MAJOR_INTERVAL
            pan.x % majorGridSize
        } else {
            startX
        }
        var index = 0
        while (x < size.width) {
            val isMajor = if (usingEnlargedGrid) {
                true // All lines are major when using enlarged grid
            } else {
                val adjustedIndex = (index + GRID_MAJOR_INTERVAL - indexOffsetX) % GRID_MAJOR_INTERVAL
                adjustedIndex == 0
            }

            // Skip minor lines if we shouldn't draw them
            if (!isMajor && !shouldDrawMinorLines) {
                x += effectiveGridSize
                index++
                continue
            }

            val color = if (isMajor) {
                gridMajorColor
            } else {
                gridColor.copy(alpha = gridColor.alpha * minorLineAlpha)
            }

            drawLine(
                color = color,
                start = Offset(x, 0f),
                end = Offset(x, size.height),
                strokeWidth = if (isMajor) 1f else 0.5f
            )
            x += effectiveGridSize
            index++
        }

        // Horizontal lines
        var y = if (usingEnlargedGrid) {
            val majorGridSize = gridSize * GRID_MAJOR_INTERVAL
            pan.y % majorGridSize
        } else {
            startY
        }
        index = 0
        while (y < size.height) {
            val isMajor = if (usingEnlargedGrid) {
                true
            } else {
                val adjustedIndex = (index + GRID_MAJOR_INTERVAL - indexOffsetY) % GRID_MAJOR_INTERVAL
                adjustedIndex == 0
            }

            // Skip minor lines if we shouldn't draw them
            if (!isMajor && !shouldDrawMinorLines) {
                y += effectiveGridSize
                index++
                continue
            }

            val color = if (isMajor) {
                gridMajorColor
            } else {
                gridColor.copy(alpha = gridColor.alpha * minorLineAlpha)
            }

            drawLine(
                color = color,
                start = Offset(0f, y),
                end = Offset(size.width, y),
                strokeWidth = if (isMajor) 1f else 0.5f
            )
            y += effectiveGridSize
            index++
        }

    }


    /**
     * Recursively draw element tree with viewport frustum culling.
     *
     * Frustum culling skips drawing elements that are completely outside the visible
     * screen area, improving performance when many elements are off-screen (e.g., when
     * zoomed in or panned to show only part of the UI).
     *
     * Note: We still recurse into children even if parent is culled, because children
     * may be positioned outside their parent's bounds and still be visible.
     *
     * IMPORTANT: Uses state.getBounds() instead of layout[element] directly to apply
     * drag/resize offsets for smooth visual feedback during drag operations.
     */
    private fun DrawScope.drawElementTree(
        element: UIElement,
        layout: Map<UIElement, ElementBounds>,
        state: CanvasState
    ) {
        // Use getBounds() to apply drag/resize offsets for visual feedback during drag
        val bounds = state.getBounds(element) ?: layout[element] ?: return
        if (!bounds.visible) return

        // Convert canvas coordinates to screen coordinates
        val screenPos = state.canvasToScreen(bounds.x, bounds.y)
        val zoom = state.zoom.value
        val screenWidth = bounds.width * zoom
        val screenHeight = bounds.height * zoom

        // Skip drawing if element has invalid dimensions (negative or too small)
        // This can happen when elements are reparented and their anchors are relative to a smaller parent
        if (screenWidth <= 0f || screenHeight <= 0f || bounds.width <= 0f || bounds.height <= 0f) {
            // Still process children in case they're positioned outside parent bounds
            element.children.forEach { child ->
                drawElementTree(child, layout, state)
            }
            return
        }

        // === Viewport Frustum Culling ===
        // Check if the element is completely outside the visible screen area.
        // An element is off-screen if:
        // - Its right edge is to the left of the screen (screenPos.x + screenWidth < 0)
        // - Its left edge is to the right of the screen (screenPos.x > size.width)
        // - Its bottom edge is above the screen (screenPos.y + screenHeight < 0)
        // - Its top edge is below the screen (screenPos.y > size.height)
        val isOffScreen = screenPos.x + screenWidth < 0f ||
                screenPos.x > size.width ||
                screenPos.y + screenHeight < 0f ||
                screenPos.y > size.height

        // Only draw if element is at least partially visible
        if (!isOffScreen) {
            // Opacity: apply alpha layer wrapper when Opacity < 1.0
            val opacity = element.numberProperty("Opacity", 1f)
            if (opacity < 1f) {
                drawIntoCanvas { canvas ->
                    val paint = Paint().apply { alpha = opacity.coerceIn(0f, 1f) }
                    canvas.saveLayer(
                        androidx.compose.ui.geometry.Rect(screenPos.x, screenPos.y,
                            screenPos.x + screenWidth, screenPos.y + screenHeight),
                        paint
                    )
                }
            }

            // Check for MaskTexturePath BEFORE drawing element content
            val maskPath = element.maskTexturePath()
            val maskTexture = if (maskPath != null && assetLoader != null && assetLoader.canLoadTextures) {
                getOrLoadTexture(maskPath)
            } else null
            val elementRect = androidx.compose.ui.geometry.Rect(
                screenPos.x, screenPos.y,
                screenPos.x + screenWidth, screenPos.y + screenHeight
            )
            if (maskTexture != null) {
                drawIntoCanvas { canvas -> canvas.saveLayer(elementRect, Paint()) }
            }

            // Draw element based on type — dispatch via RenderStrategy enum
            when (ElementTypeRegistry.getOrDefault(element.type.value).renderStrategy) {
                RenderStrategy.GROUP -> drawGroup(element, screenPos, screenWidth, screenHeight, state)
                RenderStrategy.LABEL -> drawLabel(element, screenPos, screenWidth, screenHeight, state)
                RenderStrategy.BUTTON -> drawButton(element, screenPos, screenWidth, screenHeight, state)
                RenderStrategy.TEXT_FIELD -> drawTextField(element, screenPos, screenWidth, screenHeight, state)
                RenderStrategy.IMAGE -> drawImage(element, screenPos, screenWidth, screenHeight, state)
                RenderStrategy.SLIDER -> drawSlider(element, screenPos, screenWidth, screenHeight, state)
                RenderStrategy.CHECKBOX -> drawCheckBox(element, screenPos, screenWidth, screenHeight, state)
                RenderStrategy.PROGRESS_BAR -> drawProgressBar(element, screenPos, screenWidth, screenHeight, state)
                RenderStrategy.SCROLL_VIEW -> drawScrollView(element, screenPos, screenWidth, screenHeight, state)
                RenderStrategy.DROPDOWN -> drawDropdownBox(element, screenPos, screenWidth, screenHeight, state)
                RenderStrategy.TAB_PANEL -> drawTabPanel(element, screenPos, screenWidth, screenHeight, state)
                RenderStrategy.TOOLTIP -> drawTooltip(element, screenPos, screenWidth, screenHeight, state)
                RenderStrategy.MULTILINE_TEXT_FIELD -> drawMultilineTextField(element, screenPos, screenWidth, screenHeight, state)
                RenderStrategy.NUMBER_FIELD -> drawNumberField(element, screenPos, screenWidth, screenHeight, state)
                RenderStrategy.ITEM_PREVIEW -> drawItemPreviewComponent(element, screenPos, screenWidth, screenHeight, state)
                RenderStrategy.BLOCK_SELECTOR -> drawBlockSelector(element, screenPos, screenWidth, screenHeight, state)
                RenderStrategy.CHARACTER_PREVIEW -> drawCharacterPreview(element, screenPos, screenWidth, screenHeight, state)
                RenderStrategy.PLAYER_PREVIEW -> drawPlayerPreview(element, screenPos, screenWidth, screenHeight, state)
                RenderStrategy.ITEM_GRID -> drawItemGrid(element, screenPos, screenWidth, screenHeight, state)
                RenderStrategy.SCENE_BLUR -> drawSceneBlur(element, screenPos, screenWidth, screenHeight, state)
                RenderStrategy.UNKNOWN -> drawUnknownElement(element, screenPos, screenWidth, screenHeight, state)
            }

            // Apply mask AFTER element content drawn (DstIn keeps dest where source alpha > 0)
            if (maskTexture != null) {
                drawImage(
                    image = maskTexture,
                    dstOffset = IntOffset(screenPos.x.toInt(), screenPos.y.toInt()),
                    dstSize = IntSize(screenWidth.toInt(), screenHeight.toInt()),
                    blendMode = BlendMode.DstIn
                )
                drawIntoCanvas { canvas -> canvas.restore() }
            }

            // Restore canvas state BEFORE selection highlight (so selection is always visible)
            if (opacity < 1f) {
                drawIntoCanvas { canvas -> canvas.restore() }
            }

            // Draw selection highlight if selected (outside opacity layer)
            if (state.isSelected(element)) {
                val allowedHandles = state.allowedResizeHandles(element)
                val parentLayout = state.getParentLayoutMode(element)
                val dragAxes = state.getDragAxes(element)
                drawSelectionHighlight(screenPos, screenWidth, screenHeight, allowedHandles, parentLayout, dragAxes)
            }
        }

        // Always draw children (they may be visible even if parent is off-screen or culled)
        element.children.forEach { child ->
            drawElementTree(child, layout, state)
        }
    }

    /**
     * Draw a Group element (container with optional background)
     */
    private fun DrawScope.drawGroup(
        element: UIElement,
        position: Offset,
        width: Float,
        height: Float,
        state: CanvasState
    ) {
        // Skip drawing if group has invalid dimensions
        if (width <= 0f || height <= 0f) return

        // Get background from properties - can be a color or a texture path
        val background = element.getProperty("Background")
        var drewBackground = false

        when (background) {
            is PropertyValue.Color -> {
                val bgColor = background.toComposeColor()
                if (bgColor != Color.Transparent) {
                    drawRect(
                        color = bgColor,
                        topLeft = position,
                        size = Size(width, height)
                    )
                    drewBackground = true
                }
            }
            is PropertyValue.Text -> {
                val texturePath = background.value
                if (texturePath.isNotBlank()) {
                    val texture = getOrLoadTexture(texturePath)
                    if (texture != null) {
                        drawImage(
                            image = texture,
                            dstOffset = IntOffset(position.x.toInt(), position.y.toInt()),
                            dstSize = IntSize(width.toInt(), height.toInt())
                        )
                    } else {
                        drawRect(
                            color = Color(0xFFE0E0E0),
                            topLeft = position,
                            size = Size(width, height)
                        )
                    }
                    drewBackground = true
                }
            }
            is PropertyValue.ImagePath -> {
                val texturePath = background.path
                if (texturePath.isNotBlank()) {
                    val texture = getOrLoadTexture(texturePath)
                    if (texture != null) {
                        drawImage(
                            image = texture,
                            dstOffset = IntOffset(position.x.toInt(), position.y.toInt()),
                            dstSize = IntSize(width.toInt(), height.toInt())
                        )
                    } else {
                        drawRect(
                            color = Color(0xFFE0E0E0),
                            topLeft = position,
                            size = Size(width, height)
                        )
                    }
                    drewBackground = true
                }
            }
            is PropertyValue.Tuple -> {
                // Tuple background: either (Color: #hex(alpha)) or (TexturePath: "...", Border: N)
                val colorVal = background.get("Color")
                val texturePath = (background.get("TexturePath") as? PropertyValue.Text)?.value
                    ?: (background.get("TexturePath") as? PropertyValue.ImagePath)?.path

                if (texturePath != null) {
                    val texture = getOrLoadTexture(texturePath)
                    if (texture != null) {
                        // Nine-patch 9-slice rendering if Border keys present
                        val borderNum = (background.get("Border") as? PropertyValue.Number)?.value?.toFloat()
                        val hBorder = (background.get("HorizontalBorder") as? PropertyValue.Number)?.value?.toFloat()
                        val vBorder = (background.get("VerticalBorder") as? PropertyValue.Number)?.value?.toFloat()

                        if (borderNum != null || hBorder != null || vBorder != null) {
                            val zoom = state.zoom.value
                            drawNinePatch(
                                image = texture,
                                dstX = position.x, dstY = position.y,
                                dstWidth = width, dstHeight = height,
                                horizontalBorder = (hBorder ?: borderNum ?: 0f) * zoom,
                                verticalBorder = (vBorder ?: borderNum ?: 0f) * zoom
                            )
                        } else {
                            // Plain texture: simple stretch
                            drawImage(image = texture,
                                dstOffset = IntOffset(position.x.toInt(), position.y.toInt()),
                                dstSize = IntSize(width.toInt(), height.toInt()))
                        }
                    } else {
                        drawRect(color = Color(0xFFE0E0E0), topLeft = position, size = Size(width, height))
                    }
                    drewBackground = true
                } else if (colorVal != null) {
                    val bgColor = colorFromValue(colorVal, Color.Transparent)
                    if (bgColor != Color.Transparent) {
                        drawRect(
                            color = bgColor,
                            topLeft = position,
                            size = Size(width, height)
                        )
                        drewBackground = true
                    }
                }
            }
            else -> { /* No background */ }
        }

        // Groups with no background are invisible containers in Hytale — don't draw borders.
        // Selection highlighting (handled elsewhere) is sufficient for editor visibility.
    }

    /**
     * Draw a Label element (text)
     */
    private fun DrawScope.drawLabel(
        element: UIElement,
        position: Offset,
        width: Float,
        height: Float,
        state: CanvasState
    ) {
        // Skip text if this element is being text-edited (the overlay handles it)
        if (state.textEditingElement.value == element) return

        // Skip text if element has invalid dimensions (prevents negative constraint errors)
        if (width <= 1f || height <= 1f) return

        // Apply element Padding to inset the text area
        val padTuple = element.getProperty("Padding") as? PropertyValue.Tuple
        val padLeft = (padTuple?.get("Left") ?: padTuple?.get("Horizontal") ?: padTuple?.get("Full"))
            .let { (it as? PropertyValue.Number)?.value?.toFloat() ?: 0f } * state.zoom.value
        val padRight = (padTuple?.get("Right") ?: padTuple?.get("Horizontal") ?: padTuple?.get("Full"))
            .let { (it as? PropertyValue.Number)?.value?.toFloat() ?: 0f } * state.zoom.value
        val padTop = (padTuple?.get("Top") ?: padTuple?.get("Vertical") ?: padTuple?.get("Full"))
            .let { (it as? PropertyValue.Number)?.value?.toFloat() ?: 0f } * state.zoom.value
        val padBottom = (padTuple?.get("Bottom") ?: padTuple?.get("Vertical") ?: padTuple?.get("Full"))
            .let { (it as? PropertyValue.Number)?.value?.toFloat() ?: 0f } * state.zoom.value
        @Suppress("NAME_SHADOWING")
        val position = Offset(position.x + padLeft, position.y + padTop)
        @Suppress("NAME_SHADOWING")
        val width = (width - padLeft - padRight).coerceAtLeast(0f)
        @Suppress("NAME_SHADOWING")
        val height = (height - padTop - padBottom).coerceAtLeast(0f)
        if (width <= 1f || height <= 1f) return

        var text = (element.getProperty("Text") as? PropertyValue.Text)?.value ?: ""

        // Read Style tuple for label styling (GAP-C03/C10/C05)
        // Label Style: (FontSize: 18, TextColor: #96a9be, RenderBold: true, RenderUppercase: true, ...)
        // Handles both inline tuples and @StyleRef references via resolveStyleToTuple
        val style = resolveStyleToTuple(element.getProperty("Style"))

        // TextColor: from Style tuple, flat Color property, or default
        val textColor = style?.get("TextColor")?.let { colorFromValue(it, DEFAULT_TEXT) }
            ?: (element.getProperty("Color") as? PropertyValue.Color)?.toComposeColor()
            ?: DEFAULT_TEXT

        // FontSize: from Style tuple or flat property, default 14
        val fontSize = (style?.get("FontSize") as? PropertyValue.Number)?.value?.toFloat()
            ?: element.numberProperty("FontSize", 14f)

        // Text styling flags
        val renderBold = (style?.get("RenderBold") as? PropertyValue.Boolean)?.value
            ?: element.booleanProperty("RenderBold", false)
        val renderItalic = (style?.get("RenderItalics") as? PropertyValue.Boolean)?.value
            ?: element.booleanProperty("RenderItalics", false)
        val renderUppercase = (style?.get("RenderUppercase") as? PropertyValue.Boolean)?.value
            ?: element.booleanProperty("RenderUppercase", false)

        // FontName — "Default" = Nunito Sans (SansSerif), "Secondary" = Lexend (Serif as proxy)
        val fontName = (style?.get("FontName") as? PropertyValue.Text)?.value
            ?: element.textProperty("FontName", "")
        val fontFamily = when (fontName.lowercase()) {
            "secondary" -> FontFamily.Serif
            "monospace" -> FontFamily.Monospace
            else -> FontFamily.SansSerif
        }

        // LetterSpacing
        val letterSpacing = (style?.get("LetterSpacing") as? PropertyValue.Number)?.value?.toFloat()
            ?: element.numberProperty("LetterSpacing", Float.NaN)

        // Alignment — "Alignment" is a shorthand for HorizontalAlignment in Hytale .ui files
        val hAlign = (style?.get("HorizontalAlignment") as? PropertyValue.Text)?.value
            ?: (style?.get("Alignment") as? PropertyValue.Text)?.value
            ?: element.textProperty("HorizontalAlignment", "").ifEmpty {
                element.textProperty("Alignment", "")
            }
        val vAlign = (style?.get("VerticalAlignment") as? PropertyValue.Text)?.value
            ?: element.textProperty("VerticalAlignment", "")

        // Wrap — text wrapping within element bounds
        val wrap = (style?.get("Wrap") as? PropertyValue.Boolean)?.value
            ?: element.booleanProperty("Wrap", false)

        // Apply uppercase transform
        if (renderUppercase) {
            text = text.uppercase()
        }

        // Build text style
        val zoom = state.zoom.value
        val textStyle = TextStyle(
            color = textColor,
            fontSize = (fontSize * zoom).sp,
            fontFamily = fontFamily,
            fontWeight = if (renderBold) FontWeight.Bold else FontWeight.Normal,
            fontStyle = if (renderItalic) FontStyle.Italic else FontStyle.Normal,
            letterSpacing = if (letterSpacing.isNaN()) TextUnit.Unspecified else (letterSpacing * zoom).sp
        )

        val wrapMaxWidth = if (wrap) width.toInt().coerceAtLeast(1) else Int.MAX_VALUE
        val textLayoutResult = measureTextCached(text, textStyle, wrapMaxWidth)
        val textWidth = textLayoutResult.size.width.toFloat()
        val textHeight = textLayoutResult.size.height.toFloat()

        // Horizontal alignment within bounds
        val textX = when (hAlign.lowercase()) {
            "center", "middle" -> position.x + (width - textWidth) / 2f
            "end", "right" -> position.x + width - textWidth
            else -> position.x // Start/Left (default)
        }

        // Vertical alignment within bounds
        val textY = when (vAlign.lowercase()) {
            "center", "middle" -> position.y + (height - textHeight) / 2f
            "end", "bottom" -> position.y + height - textHeight
            else -> position.y // Top (default)
        }

        // OutlineSize/OutlineColor: 8-direction text stroke outline (GAP-C06)
        val outlineSize = (style?.get("OutlineSize") as? PropertyValue.Number)?.value?.toFloat()
            ?: element.numberProperty("OutlineSize", 0f)
        val outlineColor = style?.get("OutlineColor")?.let { colorFromValue(it, Color.Black) }
            ?: element.colorProperty("OutlineColor", Color.Black)

        if (outlineSize > 0f) {
            val offsetPx = outlineSize * zoom
            val offsets = listOf(
                Offset(-offsetPx, 0f), Offset(offsetPx, 0f),
                Offset(0f, -offsetPx), Offset(0f, offsetPx),
                Offset(-offsetPx, -offsetPx), Offset(offsetPx, -offsetPx),
                Offset(-offsetPx, offsetPx), Offset(offsetPx, offsetPx)
            )
            for (off in offsets) {
                drawText(textLayoutResult, color = outlineColor,
                    topLeft = Offset(textX + off.x, textY + off.y))
            }
        }

        // Draw main text on top (use pre-measured result to avoid position-dependent re-constraint)
        drawText(textLayoutResult, color = textColor, topLeft = Offset(textX, textY))
    }

    /**
     * Draw a Button element (rounded rect with text)
     */
    private fun DrawScope.drawButton(
        element: UIElement,
        position: Offset,
        width: Float,
        height: Float,
        state: CanvasState
    ) {
        // Skip drawing entirely if button has invalid dimensions (prevents Size/Constraints errors)
        if (width <= 0f || height <= 0f) return
        val zoom = state.zoom.value

        // Read Style tuple for button styling (GAP-C04/C08)
        // Button Style: (Default: (LabelStyle: (...), Background: #fff(0.15)), Hovered: (...), ...)
        // Handles both inline tuples and @StyleRef references via resolveStyleToTuple
        val styleTuple = resolveStyleToTuple(element.getProperty("Style"))
        val defaultState = styleTuple?.get("Default") as? PropertyValue.Tuple
        val labelStyle = resolveStyleToTuple(defaultState?.get("LabelStyle"))

        // Background: from Style.Default.Background, flat Background property, or default
        val background = element.getProperty("Background")
        val styleBackground = defaultState?.get("Background")
        var drewBackground = false

        // Try texture backgrounds first (flat property takes precedence)
        when (background) {
            is PropertyValue.Text -> {
                val texturePath = background.value
                if (texturePath.isNotBlank()) {
                    val texture = getOrLoadTexture(texturePath)
                    if (texture != null) {
                        drawImage(
                            image = texture,
                            dstOffset = IntOffset(position.x.toInt(), position.y.toInt()),
                            dstSize = IntSize(width.toInt(), height.toInt())
                        )
                    } else {
                        drawRect(
                            color = Color(0xFFE0E0E0),
                            topLeft = position,
                            size = Size(width, height)
                        )
                    }
                    drewBackground = true
                }
            }
            is PropertyValue.ImagePath -> {
                val texturePath = background.path
                if (texturePath.isNotBlank()) {
                    val texture = getOrLoadTexture(texturePath)
                    if (texture != null) {
                        drawImage(
                            image = texture,
                            dstOffset = IntOffset(position.x.toInt(), position.y.toInt()),
                            dstSize = IntSize(width.toInt(), height.toInt())
                        )
                    } else {
                        drawRect(
                            color = Color(0xFFE0E0E0),
                            topLeft = position,
                            size = Size(width, height)
                        )
                    }
                    drewBackground = true
                }
            }
            else -> { /* Use color or default */ }
        }

        // Try tuple background (nine-patch or color) from Style.Default.Background
        if (!drewBackground && styleBackground is PropertyValue.Tuple) {
            val tupleTexturePath = (styleBackground.get("TexturePath") as? PropertyValue.Text)?.value
                ?: (styleBackground.get("TexturePath") as? PropertyValue.ImagePath)?.path
            val tupleColorVal = styleBackground.get("Color")

            if (tupleTexturePath != null) {
                val texture = getOrLoadTexture(tupleTexturePath)
                if (texture != null) {
                    val borderNum = (styleBackground.get("Border") as? PropertyValue.Number)?.value?.toFloat()
                    val hBorder = (styleBackground.get("HorizontalBorder") as? PropertyValue.Number)?.value?.toFloat()
                    val vBorder = (styleBackground.get("VerticalBorder") as? PropertyValue.Number)?.value?.toFloat()

                    if (borderNum != null || hBorder != null || vBorder != null) {
                        drawNinePatch(
                            image = texture, dstX = position.x, dstY = position.y,
                            dstWidth = width, dstHeight = height,
                            horizontalBorder = (hBorder ?: borderNum ?: 0f) * zoom,
                            verticalBorder = (vBorder ?: borderNum ?: 0f) * zoom
                        )
                    } else {
                        drawImage(image = texture,
                            dstOffset = IntOffset(position.x.toInt(), position.y.toInt()),
                            dstSize = IntSize(width.toInt(), height.toInt()))
                    }
                } else {
                    drawRect(color = Color(0xFFE0E0E0), topLeft = position, size = Size(width, height))
                }
                drewBackground = true
            } else if (tupleColorVal != null) {
                val bgColor = colorFromValue(tupleColorVal, BUTTON_BACKGROUND)
                drawRoundRect(color = bgColor, topLeft = position, size = Size(width, height),
                    cornerRadius = CornerRadius(BUTTON_CORNER_RADIUS * state.zoom.value))
                drewBackground = true
            }
        }

        // If no texture background, try color from flat prop → Style.Default.Background → default
        if (!drewBackground) {
            val backgroundColor = (background as? PropertyValue.Color)?.toComposeColor()
                ?: styleBackground?.let { colorFromValue(it, BUTTON_BACKGROUND) }
                ?: BUTTON_BACKGROUND

            drawRoundRect(
                color = backgroundColor,
                topLeft = position,
                size = Size(width, height),
                cornerRadius = CornerRadius(BUTTON_CORNER_RADIUS * state.zoom.value)
            )
        }

        // Icon: texture image on the button (from flat Icon property or Style.Default.Icon)
        val iconProp = element.getProperty("Icon") ?: defaultState?.get("Icon")
        val iconAnchorProp = element.getProperty("IconAnchor") as? PropertyValue.Anchor
            ?: (defaultState?.get("IconAnchor") as? PropertyValue.Anchor)
        val iconOpacity = (defaultState?.get("IconOpacity") as? PropertyValue.Number)?.value?.toFloat()
            ?: element.numberProperty("IconOpacity", 1f)

        if (iconProp != null) {
            val iconPath = when (iconProp) {
                is PropertyValue.Text -> iconProp.value
                is PropertyValue.ImagePath -> iconProp.path
                is PropertyValue.Tuple -> (iconProp.get("Texture") as? PropertyValue.Text)?.value
                    ?: (iconProp.get("TexturePath") as? PropertyValue.Text)?.value
                else -> null
            }
            val iconW = when {
                iconProp is PropertyValue.Tuple -> (iconProp.get("Width") as? PropertyValue.Number)?.value?.toFloat()
                iconAnchorProp != null -> iconAnchorProp.anchor.width?.let {
                    when (it) { is AnchorDimension.Absolute -> it.pixels; is AnchorDimension.Relative -> it.ratio * width }
                }
                else -> null
            } ?: (height * 0.6f) // default to 60% of button height
            val iconH = when {
                iconProp is PropertyValue.Tuple -> (iconProp.get("Height") as? PropertyValue.Number)?.value?.toFloat()
                iconAnchorProp != null -> iconAnchorProp.anchor.height?.let {
                    when (it) { is AnchorDimension.Absolute -> it.pixels; is AnchorDimension.Relative -> it.ratio * height }
                }
                else -> null
            } ?: iconW

            if (iconPath != null && iconPath.isNotBlank()) {
                val texture = getOrLoadTexture(iconPath)
                if (texture != null) {
                    val iw = iconW * zoom
                    val ih = iconH * zoom
                    val ix = position.x + (width - iw) / 2
                    val iy = position.y + (height - ih) / 2
                    if (iconOpacity < 1f) {
                        drawIntoCanvas { canvas ->
                            val paint = Paint().apply { alpha = iconOpacity }
                            canvas.drawImageRect(
                                texture,
                                srcOffset = IntOffset.Zero,
                                srcSize = IntSize(texture.width, texture.height),
                                dstOffset = IntOffset(ix.toInt(), iy.toInt()),
                                dstSize = IntSize(iw.toInt(), ih.toInt()),
                                paint = paint
                            )
                        }
                    } else {
                        drawImage(
                            image = texture,
                            dstOffset = IntOffset(ix.toInt(), iy.toInt()),
                            dstSize = IntSize(iw.toInt(), ih.toInt())
                        )
                    }
                }
            }
        }

        // Skip text if this element is being text-edited (the overlay handles it)
        if (state.textEditingElement.value == element) return

        // Skip text if button is too small to display text
        if (width <= 1f || height <= 1f) return

        // Text color: from Style.Default.LabelStyle.TextColor or default white
        val buttonTextColor = labelStyle?.get("TextColor")?.let { colorFromValue(it, BUTTON_TEXT_COLOR) }
            ?: BUTTON_TEXT_COLOR

        // FontSize: from Style.Default.LabelStyle.FontSize or default 14
        val fontSize = (labelStyle?.get("FontSize") as? PropertyValue.Number)?.value?.toFloat() ?: 14f

        // Bold/italic/uppercase from label style
        val renderBold = (labelStyle?.get("RenderBold") as? PropertyValue.Boolean)?.value ?: false
        val renderItalic = (labelStyle?.get("RenderItalics") as? PropertyValue.Boolean)?.value ?: false
        val renderUppercase = (labelStyle?.get("RenderUppercase") as? PropertyValue.Boolean)?.value ?: false

        // FontName from label style
        val fontName = (labelStyle?.get("FontName") as? PropertyValue.Text)?.value ?: ""
        val fontFamily = when (fontName.lowercase()) {
            "secondary" -> FontFamily.Serif
            "monospace" -> FontFamily.Monospace
            else -> FontFamily.SansSerif
        }

        // LetterSpacing from label style
        val letterSpacing = (labelStyle?.get("LetterSpacing") as? PropertyValue.Number)?.value?.toFloat()

        // Alignment from label style
        val hAlign = (labelStyle?.get("HorizontalAlignment") as? PropertyValue.Text)?.value
            ?: (labelStyle?.get("Alignment") as? PropertyValue.Text)?.value ?: "Center"
        val vAlign = (labelStyle?.get("VerticalAlignment") as? PropertyValue.Text)?.value ?: "Center"

        // Draw text
        var text = (element.getProperty("Text") as? PropertyValue.Text)?.value ?: "Button"
        if (renderUppercase) text = text.uppercase()

        val textStyle = TextStyle(
            color = buttonTextColor,
            fontSize = (fontSize * zoom).sp,
            fontFamily = fontFamily,
            fontWeight = if (renderBold) FontWeight.Bold else FontWeight.Normal,
            fontStyle = if (renderItalic) FontStyle.Italic else FontStyle.Normal,
            letterSpacing = if (letterSpacing != null) (letterSpacing * zoom).sp else TextUnit.Unspecified
        )

        val textLayoutResult = measureTextCached(text, textStyle)
        val textX = when (hAlign.lowercase()) {
            "center", "middle" -> position.x + (width - textLayoutResult.size.width) / 2f
            "end", "right" -> position.x + width - textLayoutResult.size.width
            else -> position.x
        }
        val textY = when (vAlign.lowercase()) {
            "center", "middle" -> position.y + (height - textLayoutResult.size.height) / 2f
            "end", "bottom" -> position.y + height - textLayoutResult.size.height
            else -> position.y
        }

        // LabelMaskTexturePath: gradient mask applied to button label text
        val labelMaskPath = (defaultState?.get("LabelMaskTexturePath") as? PropertyValue.Text)?.value
        val labelMaskTexture = if (labelMaskPath != null && assetLoader != null && assetLoader.canLoadTextures) {
            getOrLoadTexture(labelMaskPath)
        } else null

        val textRect = androidx.compose.ui.geometry.Rect(
            textX, textY,
            textX + textLayoutResult.size.width, textY + textLayoutResult.size.height
        )
        if (labelMaskTexture != null) {
            drawIntoCanvas { canvas -> canvas.saveLayer(textRect, Paint()) }
        }

        drawText(textLayoutResult, color = buttonTextColor, topLeft = Offset(textX, textY))

        // Apply mask: draw mask texture with DstIn blend (keeps text only where mask has alpha)
        if (labelMaskTexture != null) {
            drawIntoCanvas { canvas ->
                val paint = Paint().apply {
                    blendMode = BlendMode.DstIn
                }
                canvas.drawImageRect(
                    labelMaskTexture,
                    srcOffset = IntOffset.Zero,
                    srcSize = IntSize(labelMaskTexture.width, labelMaskTexture.height),
                    dstOffset = IntOffset(textX.toInt(), textY.toInt()),
                    dstSize = IntSize(textLayoutResult.size.width, textLayoutResult.size.height),
                    paint = paint
                )
                canvas.restore()
            }
        }
    }

    /**
     * Draw a TextField element (input box)
     */
    private fun DrawScope.drawTextField(
        element: UIElement,
        position: Offset,
        width: Float,
        height: Float,
        state: CanvasState
    ) {
        if (width <= 0f || height <= 0f) return
        val zoom = state.zoom.value

        var backgroundDrawn = false

        // Try element's Background property first (e.g. Background: $Common.@InputBoxBackground)
        // This is a PatchStyle tuple with TexturePath + Border for nine-patch rendering
        val bgProp = element.getProperty("Background")
        val bgTuple = resolveStyleToTuple(bgProp)
        if (bgTuple != null) {
            val texPath = (bgTuple.get("TexturePath") as? PropertyValue.Text)?.value
                ?: (bgTuple.get("TexturePath") as? PropertyValue.ImagePath)?.path
            val border = (bgTuple.get("Border") as? PropertyValue.Number)?.value?.toFloat() ?: 0f
            val hBorder = (bgTuple.get("HorizontalBorder") as? PropertyValue.Number)?.value?.toFloat()
            val vBorder = (bgTuple.get("VerticalBorder") as? PropertyValue.Number)?.value?.toFloat()

            if (texPath != null) {
                val bgTexture = getOrLoadTexture(texPath)
                if (bgTexture != null && (border > 0f || hBorder != null || vBorder != null)) {
                    drawNinePatch(
                        image = bgTexture,
                        dstX = position.x, dstY = position.y,
                        dstWidth = width, dstHeight = height,
                        horizontalBorder = (hBorder ?: border) * zoom,
                        verticalBorder = (vBorder ?: border) * zoom
                    )
                    backgroundDrawn = true
                } else if (bgTexture != null) {
                    drawImage(
                        image = bgTexture,
                        dstOffset = IntOffset(position.x.toInt(), position.y.toInt()),
                        dstSize = IntSize(width.toInt(), height.toInt())
                    )
                    backgroundDrawn = true
                }
            } else {
                // Tuple with Color but no texture
                val bgColor = bgTuple.get("Color")?.let { colorFromValue(it, Color.Transparent) }
                if (bgColor != null && bgColor != Color.Transparent) {
                    drawRoundRect(color = bgColor, topLeft = position, size = Size(width, height),
                        cornerRadius = CornerRadius(2f * zoom))
                    backgroundDrawn = true
                }
            }
        }

        // Check for Decoration or TextFieldDecoration property
        val decoration = resolveStyleToTuple(element.getProperty("Decoration"))
            ?: resolveStyleToTuple(element.getProperty("TextFieldDecoration"))
        val decorDefault = decoration?.get("Default") as? PropertyValue.Tuple

        // Draw decoration background (nine-patch) if present and no Background was drawn
        var textOffset = 0f
        if (decorDefault != null) {
            if (!backgroundDrawn) {
                val decorBg = decorDefault.get("Background") as? PropertyValue.Tuple
                if (decorBg != null) {
                    val texPath = (decorBg.get("TexturePath") as? PropertyValue.Text)?.value
                    val border = (decorBg.get("Border") as? PropertyValue.Number)?.value?.toFloat() ?: 0f
                    if (texPath != null) {
                        val bgTexture = getOrLoadTexture(texPath)
                        if (bgTexture != null && border > 0f) {
                            drawNinePatch(
                                image = bgTexture,
                                dstX = position.x, dstY = position.y,
                                dstWidth = width, dstHeight = height,
                                horizontalBorder = border * zoom,
                                verticalBorder = border * zoom
                            )
                            backgroundDrawn = true
                        } else if (bgTexture != null) {
                            drawImage(
                                image = bgTexture,
                                dstOffset = IntOffset(position.x.toInt(), position.y.toInt()),
                                dstSize = IntSize(width.toInt(), height.toInt())
                            )
                            backgroundDrawn = true
                        }
                    }
                }
                // Also try decoration Background as plain color
                if (!backgroundDrawn) {
                    val decorBgColor = decorDefault.get("Background")?.let { colorFromValue(it, Color.Transparent) }
                    if (decorBgColor != null && decorBgColor != Color.Transparent) {
                        drawRoundRect(color = decorBgColor, topLeft = position, size = Size(width, height),
                            cornerRadius = CornerRadius(2f * zoom))
                        backgroundDrawn = true
                    }
                }
            }

            // Draw decoration icon if present
            val iconTuple = decorDefault.get("Icon") as? PropertyValue.Tuple
            if (iconTuple != null) {
                val iconTexture = (iconTuple.get("Texture") as? PropertyValue.Text)?.value
                    ?: (iconTuple.get("Texture") as? PropertyValue.Tuple)?.let {
                        (it.get("TexturePath") as? PropertyValue.Text)?.value
                    }
                val iconW = (iconTuple.get("Width") as? PropertyValue.Number)?.value?.toFloat() ?: 16f
                val iconH = (iconTuple.get("Height") as? PropertyValue.Number)?.value?.toFloat() ?: 16f
                val iconOffset = (iconTuple.get("Offset") as? PropertyValue.Number)?.value?.toFloat() ?: 4f

                if (iconTexture != null) {
                    val tex = getOrLoadTexture(iconTexture)
                    if (tex != null) {
                        val iW = iconW * zoom
                        val iH = iconH * zoom
                        val iX = position.x + iconOffset * zoom
                        val iY = position.y + (height - iH) / 2f
                        drawImage(
                            image = tex,
                            dstOffset = IntOffset(iX.toInt(), iY.toInt()),
                            dstSize = IntSize(iW.toInt(), iH.toInt())
                        )
                        textOffset = iconOffset * zoom + iW + 2f * zoom
                    }
                }
            }
        }

        // Draw the text field core, offset by icon width if decoration present
        if (textOffset > 0f) {
            drawTextFieldCore(element, Offset(position.x + textOffset, position.y), width - textOffset, height, state, textKey = "Text", skipBackground = backgroundDrawn)
        } else {
            drawTextFieldCore(element, position, width, height, state, textKey = "Text", skipBackground = backgroundDrawn)
        }
    }

    /**
     * Shared core for TextField-style rendering. Used by drawTextField,
     * drawMultilineTextField, and drawNumberField.
     */
    private fun DrawScope.drawTextFieldCore(
        element: UIElement,
        position: Offset,
        width: Float,
        height: Float,
        state: CanvasState,
        textKey: String = "Text",
        skipBackground: Boolean = false
    ) {
        val zoom = state.zoom.value

        // Draw default background only if caller didn't already draw a styled one
        if (!skipBackground) {
            drawRoundRect(
                color = TEXTFIELD_BACKGROUND,
                topLeft = position,
                size = Size(width, height),
                cornerRadius = CornerRadius(2f * zoom)
            )

            drawRoundRect(
                color = TEXTFIELD_BORDER,
                topLeft = position,
                size = Size(width, height),
                cornerRadius = CornerRadius(2f * zoom),
                style = Stroke(width = 1f * zoom)
            )
        }

        // Skip text if this element is being text-edited (the overlay handles it)
        if (state.textEditingElement.value == element) return

        // Skip text if element is too small to display text
        if (width <= 1f || height <= 1f) return

        // Draw text — read Style or PlaceholderStyle for text field appearance
        val text = element.textProperty(textKey, "")
        val fieldStyle = resolveStyleToTuple(element.getProperty("Style"))
        val placeholderStyle = resolveStyleToTuple(element.getProperty("PlaceholderStyle"))
        // Use PlaceholderStyle when showing placeholder text (empty Text field), Style otherwise
        val activeStyle = if (text.isEmpty() || textKey == "PlaceholderText") placeholderStyle ?: fieldStyle else fieldStyle
        val textColor = activeStyle?.get("TextColor")?.let { colorFromValue(it, DEFAULT_TEXT) } ?: DEFAULT_TEXT
        val fontSize = (activeStyle?.get("FontSize") as? PropertyValue.Number)?.value?.toFloat() ?: 14f
        val renderBold = (activeStyle?.get("RenderBold") as? PropertyValue.Boolean)?.value ?: false

        val textStyle = TextStyle(
            color = textColor,
            fontSize = (fontSize * zoom).sp,
            fontWeight = if (renderBold) FontWeight.Bold else FontWeight.Normal
        )

        val textPos = position + Offset(4f * zoom, 4f * zoom)

        // Show placeholder text when main text is empty
        val rawDisplayText = if (text.isEmpty() && textKey == "Text") {
            element.textProperty("PlaceholderText", "")
        } else text

        // Apply PasswordChar masking
        val passwordChar = element.textProperty("PasswordChar", "")
        val displayText = applyPasswordMask(rawDisplayText, passwordChar, element.textProperty("PlaceholderText", ""))

        if (displayText.isNotEmpty()) {
            val textLayout = measureTextCached(displayText, textStyle)
            drawText(textLayout, color = textColor, topLeft = textPos)
        }
    }

    /**
     * Draw an Image element.
     *
     * Loads and renders actual textures from Assets.zip if AssetLoader is available.
     * Falls back to placeholder if texture is not found or still loading.
     */
    private fun DrawScope.drawImage(
        element: UIElement,
        position: Offset,
        width: Float,
        height: Float,
        state: CanvasState
    ) {
        // Skip drawing if image has invalid dimensions
        if (width <= 0f || height <= 0f) return

        // Get the image path — Image elements use "Source", BackgroundImage elements use "Image"
        fun extractPath(prop: PropertyValue?): String = when (prop) {
            is PropertyValue.Text -> prop.value
            is PropertyValue.ImagePath -> prop.path
            else -> ""
        }
        val imagePath = extractPath(element.getProperty("Source")).ifBlank {
            extractPath(element.getProperty("Image"))
        }

        // Try to load and draw actual texture if AssetLoader is available
        if (imagePath.isNotBlank() && assetLoader != null && assetLoader.canLoadTextures) {
            val texture = getOrLoadTexture(imagePath)
            if (texture != null) {
                val imgW = texture.width.toFloat()
                val imgH = texture.height.toFloat()
                val stretch = element.textProperty("Stretch", "Fill")

                // Compute source and destination rects based on Stretch mode (WPF spec)
                val (srcOff, srcSz, dstOff, dstSz) = when (stretch) {
                    "None" -> {
                        // Draw at natural size, centered, clip if larger than bounds
                        val drawW = minOf(imgW, width)
                        val drawH = minOf(imgH, height)
                        val srcX = if (imgW > width) (imgW - width) / 2f else 0f
                        val srcY = if (imgH > height) (imgH - height) / 2f else 0f
                        val dstX = position.x + if (imgW < width) (width - imgW) / 2f else 0f
                        val dstY = position.y + if (imgH < height) (height - imgH) / 2f else 0f
                        StretchRects(
                            IntOffset(srcX.toInt(), srcY.toInt()),
                            IntSize(drawW.toInt(), drawH.toInt()),
                            IntOffset(dstX.toInt(), dstY.toInt()),
                            IntSize(drawW.toInt(), drawH.toInt())
                        )
                    }
                    "Uniform" -> {
                        // Scale to fit entirely within bounds, preserving aspect ratio (letterbox)
                        val scale = minOf(width / imgW, height / imgH)
                        val scaledW = imgW * scale
                        val scaledH = imgH * scale
                        val dstX = position.x + (width - scaledW) / 2f
                        val dstY = position.y + (height - scaledH) / 2f
                        StretchRects(
                            IntOffset(0, 0),
                            IntSize(imgW.toInt(), imgH.toInt()),
                            IntOffset(dstX.toInt(), dstY.toInt()),
                            IntSize(scaledW.toInt(), scaledH.toInt())
                        )
                    }
                    "UniformToFill" -> {
                        // Scale to fill bounds, preserving aspect ratio (crop)
                        val scale = maxOf(width / imgW, height / imgH)
                        val scaledW = imgW * scale
                        val scaledH = imgH * scale
                        // Source rect: crop the center of the image
                        val srcW = width / scale
                        val srcH = height / scale
                        val srcX = (imgW - srcW) / 2f
                        val srcY = (imgH - srcH) / 2f
                        StretchRects(
                            IntOffset(srcX.toInt(), srcY.toInt()),
                            IntSize(srcW.toInt(), srcH.toInt()),
                            IntOffset(position.x.toInt(), position.y.toInt()),
                            IntSize(width.toInt(), height.toInt())
                        )
                    }
                    else -> {
                        // "Fill" (default): stretch to fill entire bounding box
                        StretchRects(
                            IntOffset(0, 0),
                            IntSize(imgW.toInt(), imgH.toInt()),
                            IntOffset(position.x.toInt(), position.y.toInt()),
                            IntSize(width.toInt(), height.toInt())
                        )
                    }
                }

                drawImage(
                    image = texture,
                    srcOffset = srcOff,
                    srcSize = srcSz,
                    dstOffset = dstOff,
                    dstSize = dstSz
                )

                // Tint: color overlay using the tint color's native alpha (GAP-C07)
                val tint = element.colorProperty("Tint", Color.Transparent)
                if (tint != Color.Transparent) {
                    drawRect(
                        color = tint,
                        topLeft = Offset(dstOff.x.toFloat(), dstOff.y.toFloat()),
                        size = Size(dstSz.width.toFloat(), dstSz.height.toFloat()),
                        blendMode = BlendMode.SrcAtop
                    )
                }
                return
            }
            // Texture is loading or failed - fall through to placeholder
        }

        // Draw placeholder rect with image path text
        drawRect(
            color = Color(0xFFE0E0E0),
            topLeft = position,
            size = Size(width, height)
        )

        drawRect(
            color = DEFAULT_BORDER,
            topLeft = position,
            size = Size(width, height),
            style = Stroke(width = 1f)
        )

        // Draw "Image" label
        val textPos = position + Offset(4f, 4f)
        val displayText = when {
            imagePath.isBlank() -> "Image: (no source)"
            imagePath in loadingPaths -> "Loading: $imagePath"
            else -> "Image: $imagePath"
        }

        val textStyle = TextStyle(
            color = Color.Gray,
            fontSize = (12 * state.zoom.value).sp
        )

        drawTextStable(text = displayText, topLeft = textPos, style = textStyle)
    }

    /**
     * Get a texture from cache or start loading it asynchronously.
     * Returns the texture if available, null if loading or failed.
     */
    private fun getOrLoadTexture(path: String): ImageBitmap? {
        // Check cache first
        if (path in loadedTextures) {
            val cached = loadedTextures[path]
            return cached
        }

        // Check if already loading
        if (path in loadingPaths) {
            return null
        }

        // Start async load
        loadingPaths.add(path)
        loadingScope.launch {
            val texture = assetLoader?.loadTexture(path)
            loadedTextures[path] = texture
            loadingPaths.remove(path)
            // Trigger repaint when texture is loaded
            onTextureLoaded?.invoke()
        }

        return null
    }

    /**
     * Get an item definition from cache or start loading it asynchronously.
     * Returns the ItemDefinition if available, null if loading or not found.
     * Follows the same sentinel pattern as [getOrLoadTexture].
     */
    private fun getOrLoadItem(itemId: String): ItemDefinition? {
        // Check cache first
        if (itemId in itemCache) {
            return itemCache[itemId]
        }

        // Check if already loading
        if (itemId in loadingItems) {
            return null
        }

        // Start async load
        loadingItems.add(itemId)
        loadingScope.launch {
            val item = itemRegistry?.getItem(itemId)
            itemCache[itemId] = item
            loadingItems.remove(itemId)
            // Trigger repaint when item is loaded (icon will load on next frame)
            onTextureLoaded?.invoke()
        }

        return null
    }

    // ==================== Tier 2 Element Renderers ====================

    /**
     * Draw a Slider element with property-driven track, fill, and handle.
     */
    private fun DrawScope.drawSlider(
        element: UIElement,
        position: Offset,
        width: Float,
        height: Float,
        state: CanvasState
    ) {
        if (width <= 0f || height <= 0f) return
        val zoom = state.zoom.value

        // Read SliderStyle compound tuple for texture-based rendering
        val sliderStyle = resolveStyleToTuple(element.getProperty("SliderStyle"))
        val sliderBg = sliderStyle?.get("Background") as? PropertyValue.Tuple
        val sliderHandlePath = (sliderStyle?.get("Handle") as? PropertyValue.Text)?.value

        val value = element.numberProperty("Value", 0.5f)
        val minValue = element.numberPropertyOrAlias("MinValue", "Min", 0f)
        val maxValue = element.numberPropertyOrAlias("MaxValue", "Max", 1f)
        val trackColor = element.colorProperty("TrackColor", Color(0xFF2D2D44))
        // Fill bar: check top-level FillColor first, then SliderStyle.Fill
        val sliderFill = sliderStyle?.get("Fill")
        val hasFillColor = element.getProperty("FillColor") != null || sliderFill != null
        val fillColor = when {
            element.getProperty("FillColor") != null -> element.colorProperty("FillColor", Color(0xFFF7A800.toInt()))
            sliderFill != null -> colorFromValue(sliderFill, Color(0xFFF7A800.toInt()))
            else -> Color.Transparent
        }
        val handleColor = element.colorProperty("HandleColor", Color(0xFFF7A800.toInt()))
        val handleW = (sliderStyle?.get("HandleWidth") as? PropertyValue.Number)?.value?.toFloat()
            ?: element.numberProperty("HandleSize", 10f)
        val handleH = (sliderStyle?.get("HandleHeight") as? PropertyValue.Number)?.value?.toFloat()
            ?: handleW
        val handleSize = handleW * zoom
        val orientation = element.textProperty("Orientation", "Horizontal")

        // Compute fill ratio with edge-case clamping
        val ratio = if (maxValue <= minValue) 0f
        else ((value - minValue) / (maxValue - minValue)).coerceIn(0f, 1f)

        val isVertical = orientation == "Vertical"
        val trackThickness = minOf(6f * zoom, if (isVertical) width * 0.4f else height * 0.4f)

        // Resolve nine-patch track texture from SliderStyle.Background
        val trackTexPath = (sliderBg?.get("TexturePath") as? PropertyValue.Text)?.value
        val trackBorder = (sliderBg?.get("Border") as? PropertyValue.Number)?.value?.toFloat() ?: 0f
        val trackTexture = if (trackTexPath != null) getOrLoadTexture(trackTexPath) else null

        if (isVertical) {
            val trackX = position.x + (width - trackThickness) / 2
            val trackY = position.y + handleSize / 2
            val trackLength = (height - handleSize).coerceAtLeast(0f)

            // Background track
            if (trackTexture != null && trackBorder > 0f) {
                drawNinePatch(
                    image = trackTexture,
                    dstX = trackX, dstY = trackY,
                    dstWidth = trackThickness, dstHeight = trackLength,
                    horizontalBorder = trackBorder * zoom,
                    verticalBorder = trackBorder * zoom
                )
            } else {
                drawRoundRect(
                    color = trackColor,
                    topLeft = Offset(trackX, trackY),
                    size = Size(trackThickness, trackLength),
                    cornerRadius = CornerRadius(trackThickness / 2)
                )
            }
            // Filled portion (from bottom up) — only when FillColor is explicitly set
            if (hasFillColor) {
                val fillLength = trackLength * ratio
                if (fillLength > 0f) {
                    drawRoundRect(
                        color = fillColor,
                        topLeft = Offset(trackX, trackY + trackLength - fillLength),
                        size = Size(trackThickness, fillLength),
                        cornerRadius = CornerRadius(trackThickness / 2)
                    )
                }
            }
            // Handle
            val handleCenterY = trackY + trackLength * (1f - ratio)
            val handleTexture = if (sliderHandlePath != null) getOrLoadTexture(sliderHandlePath) else null
            if (handleTexture != null) {
                val hw = handleW * zoom
                val hh = handleH * zoom
                drawImage(
                    image = handleTexture,
                    dstOffset = IntOffset((position.x + width / 2 - hw / 2).toInt(), (handleCenterY - hh / 2).toInt()),
                    dstSize = IntSize(hw.toInt(), hh.toInt())
                )
            } else {
                drawCircle(
                    color = handleColor,
                    radius = handleSize / 2,
                    center = Offset(position.x + width / 2, handleCenterY)
                )
            }
        } else {
            val trackX = position.x + handleSize / 2
            val trackY = position.y + (height - trackThickness) / 2
            val trackLength = (width - handleSize).coerceAtLeast(0f)

            // Background track
            if (trackTexture != null && trackBorder > 0f) {
                drawNinePatch(
                    image = trackTexture,
                    dstX = trackX, dstY = trackY,
                    dstWidth = trackLength, dstHeight = trackThickness,
                    horizontalBorder = trackBorder * zoom,
                    verticalBorder = trackBorder * zoom
                )
            } else {
                drawRoundRect(
                    color = trackColor,
                    topLeft = Offset(trackX, trackY),
                    size = Size(trackLength, trackThickness),
                    cornerRadius = CornerRadius(trackThickness / 2)
                )
            }
            // Filled portion — only when FillColor is explicitly set
            if (hasFillColor) {
                val fillLength = trackLength * ratio
                if (fillLength > 0f) {
                    drawRoundRect(
                        color = fillColor,
                        topLeft = Offset(trackX, trackY),
                        size = Size(fillLength, trackThickness),
                        cornerRadius = CornerRadius(trackThickness / 2)
                    )
                }
            }
            // Handle
            val handleCenterX = trackX + trackLength * ratio
            val handleTexture = if (sliderHandlePath != null) getOrLoadTexture(sliderHandlePath) else null
            if (handleTexture != null) {
                val hw = handleW * zoom
                val hh = handleH * zoom
                drawImage(
                    image = handleTexture,
                    dstOffset = IntOffset((handleCenterX - hw / 2).toInt(), (position.y + height / 2 - hh / 2).toInt()),
                    dstSize = IntSize(hw.toInt(), hh.toInt())
                )
            } else {
                drawCircle(
                    color = handleColor,
                    radius = handleSize / 2,
                    center = Offset(handleCenterX, position.y + height / 2)
                )
            }
        }

        // NumberField overlay for SliderNumberField / FloatSliderNumberField
        if (element.type.value.contains("NumberField")) {
            val nfStyle = resolveStyleToTuple(element.getProperty("NumberFieldStyle"))
            val nfAnchor = element.getProperty("NumberFieldContainerAnchor") as? PropertyValue.Anchor
            val nfTextColor = nfStyle?.get("TextColor")?.let { colorFromValue(it, DEFAULT_TEXT) } ?: DEFAULT_TEXT
            val nfFontSize = (nfStyle?.get("FontSize") as? PropertyValue.Number)?.value?.toFloat() ?: 12f
            val nfBold = (nfStyle?.get("RenderBold") as? PropertyValue.Boolean)?.value ?: false
            val nfBg = nfStyle?.get("Background")?.let { colorFromValue(it, Color.Transparent) } ?: Color.Transparent

            // Position: NumberFieldContainerAnchor places the number field to the RIGHT
            // of the slider, extending beyond the element's bounds. In vanilla usage:
            //   NumberFieldContainerAnchor: (Left: 15, Width: 30, Height: 15)
            // means: 15px gap after the slider's right edge, then a 30x15 number field.
            val nfW: Float
            val nfH: Float
            val nfX: Float
            val nfY: Float
            if (nfAnchor != null) {
                val anchor = nfAnchor.anchor
                nfW = (anchor.width?.let {
                    when (it) { is AnchorDimension.Absolute -> it.pixels; is AnchorDimension.Relative -> it.ratio * width / zoom }
                } ?: 30f) * zoom
                nfH = (anchor.height?.let {
                    when (it) { is AnchorDimension.Absolute -> it.pixels; is AnchorDimension.Relative -> it.ratio * height / zoom }
                } ?: (height / zoom)) * zoom
                val leftGap = anchor.left?.let {
                    when (it) { is AnchorDimension.Absolute -> it.pixels; is AnchorDimension.Relative -> it.ratio * width / zoom }
                } ?: 0f
                // Place number field AFTER the slider's right edge, with leftGap spacing
                nfX = position.x + width + leftGap * zoom
                nfY = position.y + (height - nfH) / 2f
            } else {
                nfW = 40f * zoom
                nfH = height
                nfX = position.x + width + 8f * zoom
                nfY = position.y
            }

            // Background
            if (nfBg.alpha > 0f) {
                drawRect(color = nfBg, topLeft = Offset(nfX, nfY), size = Size(nfW, nfH))
            }

            // Value text
            val valueText = value.let { v -> if (v == v.toLong().toFloat()) v.toLong().toString() else "%.1f".format(v) }
            val nfTextStyle = TextStyle(
                color = nfTextColor,
                fontSize = (nfFontSize * zoom).sp,
                fontWeight = if (nfBold) FontWeight.Bold else FontWeight.Normal
            )
            val nfLayout = measureTextCached(valueText, nfTextStyle)
            val txX = nfX + (nfW - nfLayout.size.width) / 2
            val txY = nfY + (nfH - nfLayout.size.height) / 2
            drawText(nfLayout, topLeft = Offset(txX, txY))
        }
    }

    /**
     * Draw a CheckBox element with property-driven box, checkmark, and optional label.
     */
    private fun DrawScope.drawCheckBox(
        element: UIElement,
        position: Offset,
        width: Float,
        height: Float,
        state: CanvasState
    ) {
        if (width <= 0f || height <= 0f) return
        val zoom = state.zoom.value

        val checked = element.booleanProperty("Checked", false)
        val text = element.textProperty("Text", "")
        val boxColor = element.colorProperty("BoxColor", Color.Transparent)
        val checkColor = element.colorProperty("CheckColor", Color(0xFFF7A800.toInt()))
        val borderColor = element.colorProperty("BorderColor", Color(0xFF8A8A9A.toInt()))
        val boxSize = element.numberProperty("BoxSize", 14f) * zoom
        val fontSize = element.numberProperty("FontSize", 12f)
        val textColor = element.colorProperty("Color", DEFAULT_TEXT)
        val spacing = element.numberProperty("Spacing", 6f) * zoom

        // Read CheckBoxStyle compound tuple: (Checked: (DefaultBackground: ...), Unchecked: (DefaultBackground: ...))
        val checkBoxStyle = resolveStyleToTuple(element.getProperty("Style"))
        val checkedState = checkBoxStyle?.get("Checked") as? PropertyValue.Tuple
        val uncheckedState = checkBoxStyle?.get("Unchecked") as? PropertyValue.Tuple
        val activeState = if (checked) checkedState else uncheckedState
        val stateBackground = activeState?.get("DefaultBackground") as? PropertyValue.Tuple
        val stateBgTexture = (stateBackground?.get("TexturePath") as? PropertyValue.Text)?.value
        val stateBgColor = stateBackground?.get("Color")?.let { colorFromValue(it, Color.Transparent) }

        val boxX = position.x + 2f * zoom
        val boxY = position.y + (height - boxSize) / 2
        val hasStyle = checkBoxStyle != null

        // Box fill — use style background color if available
        val fillColor = stateBgColor ?: boxColor
        if (fillColor != Color.Transparent) {
            drawRoundRect(
                color = fillColor,
                topLeft = Offset(boxX, boxY),
                size = Size(boxSize, boxSize),
                cornerRadius = CornerRadius(2f * zoom)
            )
        }

        // Box border — only draw when no CheckBoxStyle is controlling rendering
        if (!hasStyle) {
            drawRoundRect(
                color = borderColor,
                topLeft = Offset(boxX, boxY),
                size = Size(boxSize, boxSize),
                cornerRadius = CornerRadius(2f * zoom),
                style = Stroke(width = 1f * zoom)
            )
        }

        // Checkmark — use texture from style if available, otherwise draw checkmark lines
        if (checked) {
            if (stateBgTexture != null) {
                val checkTex = getOrLoadTexture(stateBgTexture)
                if (checkTex != null) {
                    drawImage(
                        image = checkTex,
                        dstOffset = IntOffset(boxX.toInt(), boxY.toInt()),
                        dstSize = IntSize(boxSize.toInt(), boxSize.toInt())
                    )
                } else {
                    drawCheckmark(boxX, boxY, boxSize, checkColor, zoom)
                }
            } else {
                drawCheckmark(boxX, boxY, boxSize, checkColor, zoom)
            }
        } else if (stateBgTexture != null) {
            // Unchecked state may also have a texture (e.g., disabled background)
            val uncheckedTex = getOrLoadTexture(stateBgTexture)
            if (uncheckedTex != null) {
                drawImage(
                    image = uncheckedTex,
                    dstOffset = IntOffset(boxX.toInt(), boxY.toInt()),
                    dstSize = IntSize(boxSize.toInt(), boxSize.toInt())
                )
            }
        }

        // Label text
        if (text.isNotEmpty() && width > boxSize + spacing + 10f * zoom) {
            val textStyle = TextStyle(
                color = textColor,
                fontSize = (fontSize * zoom).sp
            )
            val textPos = Offset(boxX + boxSize + spacing, position.y + (height - fontSize * zoom) / 2)
            drawTextStable(text = text, topLeft = textPos, style = textStyle)
        }
    }

    /** Draw a checkmark (tick) shape inside a box */
    private fun DrawScope.drawCheckmark(boxX: Float, boxY: Float, boxSize: Float, color: Color, zoom: Float) {
        drawLine(
            color = color,
            start = Offset(boxX + boxSize * 0.2f, boxY + boxSize * 0.5f),
            end = Offset(boxX + boxSize * 0.4f, boxY + boxSize * 0.75f),
            strokeWidth = 2f * zoom
        )
        drawLine(
            color = color,
            start = Offset(boxX + boxSize * 0.4f, boxY + boxSize * 0.75f),
            end = Offset(boxX + boxSize * 0.8f, boxY + boxSize * 0.25f),
            strokeWidth = 2f * zoom
        )
    }

    /**
     * Draw a ProgressBar element with property-driven fill, direction, and optional percentage text.
     */
    private fun DrawScope.drawProgressBar(
        element: UIElement,
        position: Offset,
        width: Float,
        height: Float,
        state: CanvasState
    ) {
        if (width <= 0f || height <= 0f) return
        val zoom = state.zoom.value

        val value = element.numberProperty("Value", 0.65f)
        val minValue = element.numberPropertyOrAlias("MinValue", "Min", 0f)
        val maxValue = element.numberPropertyOrAlias("MaxValue", "Max", 1f)
        val fillColor = element.colorProperty("FillColor", Color(0xFF60A5FA.toInt()))
        val bgColor = element.colorProperty("Background", Color(0xFF2D2D44))
        val borderColor = element.colorProperty("BorderColor", Color.Transparent)
        val cornerRadius = element.numberProperty("CornerRadius", 0f) * zoom
        // FillDirection is the explicit property; Direction + Alignment are vanilla shorthands
        val explicitFillDir = element.textProperty("FillDirection", "")
        val direction = element.textProperty("Direction", "")
        val alignment = element.textProperty("Alignment", "").ifEmpty {
            element.textProperty("HorizontalAlignment", "")
        }
        val fillDirection = resolveProgressBarFillDirection(explicitFillDir, direction, alignment)
        val showPercentage = element.booleanProperty("ShowPercentage", false)

        val ratio = if (maxValue <= minValue) 0f
        else ((value - minValue) / (maxValue - minValue)).coerceIn(0f, 1f)

        val cr = CornerRadius(cornerRadius)

        // Background
        drawRoundRect(color = bgColor, topLeft = position, size = Size(width, height), cornerRadius = cr)

        // Filled portion
        val fillRect = when (fillDirection) {
            "RightToLeft" -> {
                val fillW = width * ratio
                Pair(Offset(position.x + width - fillW, position.y), Size(fillW, height))
            }
            "TopToBottom" -> {
                val fillH = height * ratio
                Pair(position, Size(width, fillH))
            }
            "BottomToTop" -> {
                val fillH = height * ratio
                Pair(Offset(position.x, position.y + height - fillH), Size(width, fillH))
            }
            else -> { // LeftToRight (default)
                val fillW = width * ratio
                Pair(position, Size(fillW, height))
            }
        }

        if (fillRect.second.width > 0f && fillRect.second.height > 0f) {
            // Use BarTexturePath texture if available, otherwise solid FillColor
            val barTexturePath = element.textProperty("BarTexturePath", "")
            val barTexture = if (barTexturePath.isNotBlank()) getOrLoadTexture(barTexturePath) else null
            if (barTexture != null) {
                drawImage(
                    image = barTexture,
                    dstOffset = IntOffset(fillRect.first.x.toInt(), fillRect.first.y.toInt()),
                    dstSize = IntSize(fillRect.second.width.toInt(), fillRect.second.height.toInt())
                )
            } else {
                drawRoundRect(
                    color = fillColor,
                    topLeft = fillRect.first,
                    size = fillRect.second,
                    cornerRadius = cr
                )
            }
        }

        // Border
        if (borderColor != Color.Transparent) {
            drawRoundRect(
                color = borderColor,
                topLeft = position,
                size = Size(width, height),
                cornerRadius = cr,
                style = Stroke(width = 1f * zoom)
            )
        }

        // EffectTexturePath: overlay effect on the progress bar (shimmer/glow)
        val effectPath = element.textProperty("EffectTexturePath", "")
        if (effectPath.isNotBlank() && fillRect.second.width > 0f) {
            val effectTexture = getOrLoadTexture(effectPath)
            if (effectTexture != null) {
                val effectW = element.numberProperty("EffectWidth", effectTexture.width.toFloat()) * zoom
                val effectH = element.numberProperty("EffectHeight", effectTexture.height.toFloat()) * zoom
                val effectOffset = element.numberProperty("EffectOffset", 0f) * zoom
                // Position effect at the end of the fill, offset by EffectOffset
                val effectX = when (fillDirection) {
                    "RightToLeft" -> fillRect.first.x - effectOffset
                    else -> fillRect.first.x + fillRect.second.width - effectW + effectOffset
                }
                val effectY = position.y + (height - effectH) / 2
                drawImage(
                    image = effectTexture,
                    dstOffset = IntOffset(effectX.toInt(), effectY.toInt()),
                    dstSize = IntSize(effectW.toInt(), effectH.toInt())
                )
            }
        }

        // Percentage text
        if (showPercentage && width > 20f && height > 10f) {
            val percentText = "${(ratio * 100).toInt()}%"
            val textColorProp = element.colorProperty("TextColor", Color.White)
            val fontSizeProp = element.numberProperty("FontSize", 12f)
            val textStyle = TextStyle(color = textColorProp, fontSize = (fontSizeProp * zoom).sp)
            val textLayout = measureTextCached(percentText, textStyle)
            val textX = position.x + (width - textLayout.size.width) / 2
            val textY = position.y + (height - textLayout.size.height) / 2
            if (textX >= 0 && textY >= 0) {
                drawTextStable(text = percentText, topLeft = Offset(textX, textY), style = textStyle)
            }
        }
    }

    /**
     * Draw a ScrollView element with background, border, and optional scrollbar indicators.
     */
    private fun DrawScope.drawScrollView(
        element: UIElement,
        position: Offset,
        width: Float,
        height: Float,
        state: CanvasState
    ) {
        if (width <= 0f || height <= 0f) return
        val zoom = state.zoom.value

        val bgColor = element.colorProperty("Background", Color.Transparent)
        val borderColor = element.colorProperty("BorderColor", Color(0xFF5D5D5D))
        val scrollDirection = element.textProperty("ScrollDirection", "Vertical")
        val showScrollbars = element.booleanProperty("ShowScrollbars", true)
                             || element.booleanProperty("ShowScrollbar", true)

        // Read ScrollbarStyle compound tuple for scrollbar appearance
        val scrollbarStyleTuple = resolveStyleToTuple(element.getProperty("ScrollbarStyle"))
        val barTexturePath = (scrollbarStyleTuple?.get("BarTexturePath") as? PropertyValue.Text)?.value
        val barTexture = if (barTexturePath != null && assetLoader != null && assetLoader.canLoadTextures) {
            getOrLoadTexture(barTexturePath)
        } else null

        val scrollbarColor = element.colorProperty("ScrollbarColor", Color(0xFF8A8A9A.toInt()))
        val scrollbarWidth = element.numberProperty("ScrollbarWidth", 6f) * zoom

        // Background
        if (bgColor != Color.Transparent) {
            drawRect(color = bgColor, topLeft = position, size = Size(width, height))
        }

        // Border
        drawRect(
            color = borderColor,
            topLeft = position,
            size = Size(width, height),
            style = Stroke(width = 1f * zoom)
        )

        // Scrollbar indicators (decorative)
        if (showScrollbars) {
            val scrollbarLength = 0.3f
            val padding = 2f * zoom

            if (scrollDirection == "Vertical" || scrollDirection == "Both") {
                // Vertical scrollbar on right edge
                val sbHeight = height * scrollbarLength
                drawRoundRect(
                    color = scrollbarColor,
                    topLeft = Offset(position.x + width - scrollbarWidth - padding, position.y + padding),
                    size = Size(scrollbarWidth, sbHeight),
                    cornerRadius = CornerRadius(scrollbarWidth / 2)
                )
            }
            if (scrollDirection == "Horizontal" || scrollDirection == "Both") {
                // Horizontal scrollbar on bottom edge
                val sbWidth = width * scrollbarLength
                drawRoundRect(
                    color = scrollbarColor,
                    topLeft = Offset(position.x + padding, position.y + height - scrollbarWidth - padding),
                    size = Size(sbWidth, scrollbarWidth),
                    cornerRadius = CornerRadius(scrollbarWidth / 2)
                )
            }
        }
    }

    /**
     * Draw a DropdownBox element with background, text, and dropdown arrow.
     */
    private fun DrawScope.drawDropdownBox(
        element: UIElement,
        position: Offset,
        width: Float,
        height: Float,
        state: CanvasState
    ) {
        if (width <= 0f || height <= 0f) return
        val zoom = state.zoom.value

        // Read Style compound tuple: (LabelStyle: (...), PanelWidth: N, PanelAlign: X, ...)
        val dropdownStyle = resolveStyleToTuple(element.getProperty("Style"))
        val dropdownLabelStyle = resolveStyleToTuple(dropdownStyle?.get("LabelStyle"))
        val panelWidth = (dropdownStyle?.get("PanelWidth") as? PropertyValue.Number)?.value?.toFloat()
            ?: element.numberProperty("PanelWidth", width / zoom)
        val panelAlign = (dropdownStyle?.get("PanelAlign") as? PropertyValue.Text)?.value
            ?: element.textProperty("PanelAlign", "Bottom")

        val placeholder = element.textProperty("Placeholder", "Select...")
        val selectedValue = element.textProperty("SelectedValue", "")
        val bgColor = element.colorProperty("Background", Color.White)
        val borderColor = element.colorProperty("BorderColor", Color(0xFFCCCCCC.toInt()))
        val cornerRadius = element.numberProperty("CornerRadius", 4f) * zoom
        val textColor = dropdownLabelStyle?.get("TextColor")?.let { colorFromValue(it, Color(0xFF333333)) }
            ?: element.colorProperty("Color", Color(0xFF333333))
        val fontSize = (dropdownLabelStyle?.get("FontSize") as? PropertyValue.Number)?.value?.toFloat()
            ?: element.numberProperty("FontSize", 12f)
        val renderBold = (dropdownLabelStyle?.get("RenderBold") as? PropertyValue.Boolean)?.value ?: false
        val renderUppercase = (dropdownLabelStyle?.get("RenderUppercase") as? PropertyValue.Boolean)?.value ?: false
        val enabled = element.booleanProperty("Enabled", true)

        val alpha = if (enabled) 1f else 0.5f
        val cr = CornerRadius(cornerRadius)

        // Background: try nine-patch from Style.DefaultBackground first, then flat color
        var backgroundDrawn = false
        val defaultBg = dropdownStyle?.get("DefaultBackground") as? PropertyValue.Tuple
        if (defaultBg != null) {
            val texPath = (defaultBg.get("TexturePath") as? PropertyValue.Text)?.value
                ?: (defaultBg.get("TexturePath") as? PropertyValue.ImagePath)?.path
            val border = (defaultBg.get("Border") as? PropertyValue.Number)?.value?.toFloat() ?: 0f
            val hBorder = (defaultBg.get("HorizontalBorder") as? PropertyValue.Number)?.value?.toFloat()
            val vBorder = (defaultBg.get("VerticalBorder") as? PropertyValue.Number)?.value?.toFloat()

            if (texPath != null) {
                val bgTexture = getOrLoadTexture(texPath)
                if (bgTexture != null && (border > 0f || hBorder != null || vBorder != null)) {
                    drawNinePatch(
                        image = bgTexture,
                        dstX = position.x, dstY = position.y,
                        dstWidth = width, dstHeight = height,
                        horizontalBorder = (hBorder ?: border) * zoom,
                        verticalBorder = (vBorder ?: border) * zoom
                    )
                    backgroundDrawn = true
                } else if (bgTexture != null) {
                    drawImage(
                        image = bgTexture,
                        dstOffset = IntOffset(position.x.toInt(), position.y.toInt()),
                        dstSize = IntSize(width.toInt(), height.toInt())
                    )
                    backgroundDrawn = true
                }
            }
        }

        if (!backgroundDrawn) {
            drawRoundRect(
                color = bgColor.copy(alpha = bgColor.alpha * alpha),
                topLeft = position,
                size = Size(width, height),
                cornerRadius = cr
            )

            // Border (only when using flat color background)
            drawRoundRect(
                color = borderColor.copy(alpha = borderColor.alpha * alpha),
                topLeft = position,
                size = Size(width, height),
                cornerRadius = cr,
                style = Stroke(width = 1f * zoom)
            )
        }

        // Overlay texture from Style
        val overlayTuple = dropdownStyle?.get("Overlay") as? PropertyValue.Tuple
        val overlayPath = (overlayTuple?.get("Default") as? PropertyValue.Text)?.value
            ?: (dropdownStyle?.get("Overlay") as? PropertyValue.Text)?.value
        if (overlayPath != null) {
            val overlayTex = getOrLoadTexture(overlayPath)
            if (overlayTex != null) {
                drawImage(
                    image = overlayTex,
                    dstOffset = IntOffset(position.x.toInt(), position.y.toInt()),
                    dstSize = IntSize(width.toInt(), height.toInt()),
                    alpha = alpha
                )
            }
        }

        // Dropdown arrow: try texture from style, fallback to drawn lines
        val arrowTexPath = (dropdownStyle?.get("DefaultArrowTexturePath") as? PropertyValue.Text)?.value
        val arrowW = (dropdownStyle?.get("ArrowWidth") as? PropertyValue.Number)?.value?.toFloat() ?: 10f
        val arrowH = (dropdownStyle?.get("ArrowHeight") as? PropertyValue.Number)?.value?.toFloat() ?: arrowW
        val hPadding = (dropdownStyle?.get("HorizontalPadding") as? PropertyValue.Number)?.value?.toFloat() ?: 8f
        val arrowTexture = if (arrowTexPath != null) getOrLoadTexture(arrowTexPath) else null
        val arrowSize = if (arrowTexture != null) arrowW * zoom else minOf(10f * zoom, height * 0.35f)

        if (arrowTexture != null) {
            val aw = arrowW * zoom
            val ah = arrowH * zoom
            val ax = position.x + width - aw - hPadding * zoom
            val ay = position.y + (height - ah) / 2
            drawImage(
                image = arrowTexture,
                dstOffset = IntOffset(ax.toInt(), ay.toInt()),
                dstSize = IntSize(aw.toInt(), ah.toInt()),
                alpha = alpha
            )
        } else {
            val arrowX = position.x + width - arrowSize - 8f * zoom
            val arrowY = position.y + (height - arrowSize * 0.6f) / 2
            val arrowColor = textColor.copy(alpha = textColor.alpha * alpha * 0.6f)

            drawLine(
                color = arrowColor,
                start = Offset(arrowX, arrowY),
                end = Offset(arrowX + arrowSize / 2, arrowY + arrowSize * 0.6f),
                strokeWidth = 1.5f * zoom
            )
            drawLine(
                color = arrowColor,
                start = Offset(arrowX + arrowSize / 2, arrowY + arrowSize * 0.6f),
                end = Offset(arrowX + arrowSize, arrowY),
                strokeWidth = 1.5f * zoom
            )
        }

        // Text
        if (width > 30f * zoom && height > 10f) {
            var displayText = if (selectedValue.isNotEmpty()) selectedValue else placeholder
            if (renderUppercase) displayText = displayText.uppercase()
            val maxTextWidth = (width - arrowSize - 20f * zoom).coerceAtLeast(0f)
            val textStyle = TextStyle(
                color = textColor.copy(alpha = textColor.alpha * alpha),
                fontSize = (fontSize * zoom).sp,
                fontWeight = if (renderBold) FontWeight.Bold else FontWeight.Normal
            )

            // Truncate with ellipsis if needed
            val textLayout = measureTextCached(displayText, textStyle)
            val finalText = if (textLayout.size.width > maxTextWidth && displayText.length > 3) {
                // Binary-ish truncation: try progressively shorter strings
                var truncated = displayText
                while (truncated.length > 1) {
                    truncated = truncated.dropLast(1)
                    val measured = measureTextCached("$truncated...", textStyle)
                    if (measured.size.width <= maxTextWidth) break
                }
                "$truncated..."
            } else {
                displayText
            }

            val textPos = Offset(position.x + 8f * zoom, position.y + (height - textLayout.size.height) / 2)
            drawTextStable(text = finalText, topLeft = textPos, style = textStyle)
        }
    }

    /**
     * Draw a TabPanel element with a tab bar and content area.
     */
    private fun DrawScope.drawTabPanel(
        element: UIElement,
        position: Offset,
        width: Float,
        height: Float,
        state: CanvasState
    ) {
        if (width <= 0f || height <= 0f) return
        val zoom = state.zoom.value

        // Read TabStyle/SelectedTabStyle compound tuples
        val tabStyleTuple = resolveStyleToTuple(element.getProperty("TabStyle"))
        val selectedTabStyleTuple = resolveStyleToTuple(element.getProperty("SelectedTabStyle"))
        val tabDefault = tabStyleTuple?.get("Default") as? PropertyValue.Tuple
        val selectedTabDefault = selectedTabStyleTuple?.get("Default") as? PropertyValue.Tuple

        // Extract label styles from tab style tuples
        val tabLabelStyle = resolveStyleToTuple(tabDefault?.get("LabelStyle"))
        val selectedTabLabelStyle = resolveStyleToTuple(selectedTabDefault?.get("LabelStyle"))

        val tabBarHeight = element.numberProperty("TabBarHeight", 28f) * zoom
        val tabBarBg = element.colorProperty("TabBarBackground", Color(0xFF2D2D2D))
        val tabBg = tabDefault?.get("Background")?.let { colorFromValue(it, Color.Transparent) }
            ?: element.colorProperty("TabBackground", Color.Transparent)
        val tabActiveBg = selectedTabDefault?.get("Background")?.let { colorFromValue(it, Color(0xFF3D3D3D)) }
            ?: element.colorProperty("TabActiveBackground", Color(0xFF3D3D3D))
        val tabTextColor = tabLabelStyle?.get("TextColor")?.let { colorFromValue(it, Color(0xFF8A8A8A.toInt())) }
            ?: element.colorProperty("TabTextColor", Color(0xFF8A8A8A.toInt()))
        val tabActiveTextColor = selectedTabLabelStyle?.get("TextColor")?.let { colorFromValue(it, Color.White) }
            ?: element.colorProperty("TabActiveTextColor", Color.White)
        val tabFontSize = (tabLabelStyle?.get("FontSize") as? PropertyValue.Number)?.value?.toFloat()
            ?: element.numberProperty("TabFontSize", 11f)
        val tabBold = (tabLabelStyle?.get("RenderBold") as? PropertyValue.Boolean)?.value ?: false
        val tabUppercase = (tabLabelStyle?.get("RenderUppercase") as? PropertyValue.Boolean)?.value ?: false
        val tabOverlayPath = (tabDefault?.get("Overlay") as? PropertyValue.Text)?.value
        val selectedTabOverlayPath = (selectedTabDefault?.get("Overlay") as? PropertyValue.Text)?.value
        val selectedTab = element.numberProperty("SelectedTab", 0f).toInt()
        val panelBg = element.colorProperty("PanelBackground", Color.Transparent)
        val borderColor = element.colorProperty("BorderColor", Color(0xFF5D5D5D))

        val childCount = element.children.size.coerceAtLeast(1)
        val activeIndex = if (selectedTab in 0 until childCount) selectedTab else 0

        // Tab bar background
        drawRect(
            color = tabBarBg,
            topLeft = position,
            size = Size(width, tabBarHeight)
        )

        // Draw individual tabs
        val tabWidth = width / childCount
        for (i in 0 until childCount) {
            val tabX = position.x + i * tabWidth
            val isActive = i == activeIndex
            val bg = if (isActive) tabActiveBg else tabBg

            if (bg != Color.Transparent) {
                drawRect(color = bg, topLeft = Offset(tabX, position.y), size = Size(tabWidth, tabBarHeight))
            }

            // Draw tab overlay texture if present
            val overlayPath = if (isActive) selectedTabOverlayPath ?: tabOverlayPath else tabOverlayPath
            if (overlayPath != null) {
                val overlayTex = getOrLoadTexture(overlayPath)
                if (overlayTex != null) {
                    drawImage(
                        image = overlayTex,
                        dstOffset = IntOffset(tabX.toInt(), position.y.toInt()),
                        dstSize = IntSize(tabWidth.toInt(), tabBarHeight.toInt())
                    )
                }
            }

            // Tab label
            val label = if (i < element.children.size) {
                element.children[i].id?.value ?: "Tab ${i + 1}"
            } else {
                "Tab ${i + 1}"
            }

            // Determine style for this tab (active vs inactive)
            val isActiveBold = if (isActive) {
                (selectedTabLabelStyle?.get("RenderBold") as? PropertyValue.Boolean)?.value ?: tabBold
            } else tabBold
            val isActiveUppercase = if (isActive) {
                (selectedTabLabelStyle?.get("RenderUppercase") as? PropertyValue.Boolean)?.value ?: tabUppercase
            } else tabUppercase
            val activeTabFontSize = if (isActive) {
                (selectedTabLabelStyle?.get("FontSize") as? PropertyValue.Number)?.value?.toFloat() ?: tabFontSize
            } else tabFontSize

            val displayLabel = if (isActiveUppercase) label.uppercase() else label

            val textStyle = TextStyle(
                color = if (isActive) tabActiveTextColor else tabTextColor,
                fontSize = (activeTabFontSize * zoom).sp,
                fontWeight = if (isActiveBold) FontWeight.Bold else FontWeight.Normal
            )

            if (tabWidth > 20f && tabBarHeight > 10f) {
                // Draw tab icon if available
                val tabChild = if (i < element.children.size) element.children[i] else null
                val iconPath = tabChild?.textProperty("Icon", "")?.ifEmpty { null }
                val iconSelectedPath = tabChild?.textProperty("IconSelected", "")?.ifEmpty { null }
                val iconToShow = if (isActive && iconSelectedPath != null) iconSelectedPath else iconPath
                val iconTexture = if (iconToShow != null) getOrLoadTexture(iconToShow) else null

                var labelX = tabX
                var labelWidth = tabWidth

                if (iconTexture != null) {
                    val iconOpacity = tabChild?.numberProperty("IconOpacity", 1f) ?: 1f
                    val iconSize = minOf(tabBarHeight * 0.6f, 16f * zoom)
                    val iconX = tabX + 4f * zoom
                    val iconY = position.y + (tabBarHeight - iconSize) / 2f
                    if (iconOpacity < 1f) {
                        drawImage(
                            image = iconTexture,
                            dstOffset = IntOffset(iconX.toInt(), iconY.toInt()),
                            dstSize = IntSize(iconSize.toInt(), iconSize.toInt()),
                            alpha = iconOpacity
                        )
                    } else {
                        drawImage(
                            image = iconTexture,
                            dstOffset = IntOffset(iconX.toInt(), iconY.toInt()),
                            dstSize = IntSize(iconSize.toInt(), iconSize.toInt())
                        )
                    }
                    labelX = iconX + iconSize + 2f * zoom
                    labelWidth = tabWidth - (labelX - tabX)
                }

                val textLayout = measureTextCached(displayLabel, textStyle)
                val textX = labelX + (labelWidth - textLayout.size.width) / 2
                val textY = position.y + (tabBarHeight - textLayout.size.height) / 2
                if (textX >= 0 && textY >= 0) {
                    drawTextStable(text = displayLabel, topLeft = Offset(textX, textY), style = textStyle)
                }
            }
        }

        // Content area below tab bar
        val contentY = position.y + tabBarHeight
        val contentHeight = (height - tabBarHeight).coerceAtLeast(0f)
        if (panelBg != Color.Transparent && contentHeight > 0f) {
            drawRect(color = panelBg, topLeft = Offset(position.x, contentY), size = Size(width, contentHeight))
        }

        // Outer border
        drawRect(
            color = borderColor,
            topLeft = position,
            size = Size(width, height),
            style = Stroke(width = 1f * zoom)
        )
    }

    /**
     * Draw a Tooltip element with text and optional title/arrow.
     */
    private fun DrawScope.drawTooltip(
        element: UIElement,
        position: Offset,
        width: Float,
        height: Float,
        state: CanvasState
    ) {
        if (width <= 0f || height <= 0f) return
        val zoom = state.zoom.value

        val text = element.textProperty("Text", "Tooltip")
        val title = element.textProperty("Title", "")
        val bgColor = element.colorProperty("Background", Color(0xFF2D2D2D))
        val borderColor = element.colorProperty("BorderColor", Color(0xFF5D5D5D))
        val cornerRadius = element.numberProperty("CornerRadius", 4f) * zoom
        val textColor = element.colorProperty("Color", Color(0xFFEAEAEA.toInt()))
        val fontSize = element.numberProperty("FontSize", 11f)
        val showArrow = element.booleanProperty("ShowArrow", false)

        val cr = CornerRadius(cornerRadius)

        // Background
        drawRoundRect(color = bgColor, topLeft = position, size = Size(width, height), cornerRadius = cr)

        // Border
        drawRoundRect(
            color = borderColor,
            topLeft = position,
            size = Size(width, height),
            cornerRadius = cr,
            style = Stroke(width = 1f * zoom)
        )

        // Arrow (pointing down from bottom center)
        if (showArrow) {
            val arrowSize = 6f * zoom
            val centerX = position.x + width / 2
            val bottomY = position.y + height
            val path = Path().apply {
                moveTo(centerX - arrowSize, bottomY)
                lineTo(centerX, bottomY + arrowSize)
                lineTo(centerX + arrowSize, bottomY)
                close()
            }
            drawPath(path = path, color = bgColor)
        }

        // Text content
        if (width > 10f && height > 10f) {
            val padding = 6f * zoom
            var textY = position.y + padding

            // Title (rendered slightly larger)
            if (title.isNotEmpty()) {
                val titleStyle = TextStyle(color = textColor, fontSize = ((fontSize + 1) * zoom).sp)
                val titlePos = Offset(position.x + padding, textY)
                drawTextStable(text = title, topLeft = titlePos, style = titleStyle)
                val titleLayout = measureTextCached(title, titleStyle)
                textY += titleLayout.size.height + 2f * zoom
            }

            // Body text
            val bodyStyle = TextStyle(color = textColor, fontSize = (fontSize * zoom).sp)
            val bodyPos = Offset(position.x + padding, textY)
            drawTextStable(text = text, topLeft = bodyPos, style = bodyStyle)
        }
    }

    /**
     * Draw a MultilineTextField — delegates to TextField core with faint multi-line indicators.
     */
    private fun DrawScope.drawMultilineTextField(
        element: UIElement,
        position: Offset,
        width: Float,
        height: Float,
        state: CanvasState
    ) {
        if (width <= 0f || height <= 0f) return
        val zoom = state.zoom.value

        // Reuse TextField core with PlaceholderText key
        drawTextFieldCore(element, position, width, height, state, textKey = "PlaceholderText")

        // Draw faint horizontal lines to indicate multi-line
        val lineSpacing = 20f * zoom
        val startY = position.y + 24f * zoom
        val lineColor = Color(0x18000000)
        for (i in 1..3) {
            val lineY = startY + i * lineSpacing
            if (lineY < position.y + height - 4f * zoom) {
                drawLine(
                    color = lineColor,
                    start = Offset(position.x + 6f * zoom, lineY),
                    end = Offset(position.x + width - 6f * zoom, lineY),
                    strokeWidth = 1f
                )
            }
        }
    }

    /**
     * Draw a NumberField — delegates to TextField core with optional up/down buttons.
     */
    private fun DrawScope.drawNumberField(
        element: UIElement,
        position: Offset,
        width: Float,
        height: Float,
        state: CanvasState
    ) {
        if (width <= 0f || height <= 0f) return
        val zoom = state.zoom.value

        val showButtons = element.booleanProperty("ShowButtons", false)
        val buttonAreaWidth = if (showButtons) 16f * zoom else 0f

        // Draw the TextField core (shrunk if buttons shown)
        drawTextFieldCore(element, position, width - buttonAreaWidth, height, state, textKey = "Value")

        // Up/down buttons (decorative)
        if (showButtons && buttonAreaWidth > 4f) {
            val btnX = position.x + width - buttonAreaWidth
            val halfH = height / 2

            // Button area background
            drawRect(
                color = Color(0xFFF0F0F0.toInt()),
                topLeft = Offset(btnX, position.y),
                size = Size(buttonAreaWidth, height)
            )

            // Divider between buttons
            drawLine(
                color = TEXTFIELD_BORDER,
                start = Offset(btnX, position.y + halfH),
                end = Offset(btnX + buttonAreaWidth, position.y + halfH),
                strokeWidth = 1f
            )

            // Left border of button area
            drawLine(
                color = TEXTFIELD_BORDER,
                start = Offset(btnX, position.y),
                end = Offset(btnX, position.y + height),
                strokeWidth = 1f
            )

            // Up arrow (top half)
            val arrowW = 5f * zoom
            val arrowH = 3f * zoom
            val upCenterX = btnX + buttonAreaWidth / 2
            val upCenterY = position.y + halfH / 2
            drawLine(color = DEFAULT_TEXT, start = Offset(upCenterX - arrowW / 2, upCenterY + arrowH / 2),
                end = Offset(upCenterX, upCenterY - arrowH / 2), strokeWidth = 1f * zoom)
            drawLine(color = DEFAULT_TEXT, start = Offset(upCenterX, upCenterY - arrowH / 2),
                end = Offset(upCenterX + arrowW / 2, upCenterY + arrowH / 2), strokeWidth = 1f * zoom)

            // Down arrow (bottom half)
            val downCenterY = position.y + halfH + halfH / 2
            drawLine(color = DEFAULT_TEXT, start = Offset(upCenterX - arrowW / 2, downCenterY - arrowH / 2),
                end = Offset(upCenterX, downCenterY + arrowH / 2), strokeWidth = 1f * zoom)
            drawLine(color = DEFAULT_TEXT, start = Offset(upCenterX, downCenterY + arrowH / 2),
                end = Offset(upCenterX + arrowW / 2, downCenterY - arrowH / 2), strokeWidth = 1f * zoom)
        }
    }

    // ==================== 3D Preview Element Renderers ====================

    // Shared colors for 3D preview viewports
    private val VIEWPORT_BG = Color(0xFF1A1A2E)
    private val VIEWPORT_BORDER = Color(0xFF3D3D54)
    private val BADGE_COLOR = Color(0xFF5D5D5D)
    private val SLOT_BORDER = Color(0xFF4A4A5A)
    private val SLOT_FILL = Color(0xFF22223A)
    private val ITEM_ICON_COLOR = Color(0xFF8AA85D)  // muted green
    private val BLOCK_COLOR = Color(0xFF5D8AA8)       // steel blue
    private val CHARACTER_COLOR = Color(0xFF8A5DA8)   // muted purple
    private val PLAYER_COLOR = Color(0xFF5DA88A)      // muted teal

    /**
     * Draw an ItemPreviewComponent element.
     * Loads actual pre-rendered item icons from Assets.zip via ItemRegistry + AssetLoader.
     * Falls back to a styled placeholder if icons are unavailable.
     */
    private fun DrawScope.drawItemPreviewComponent(
        element: UIElement,
        position: Offset,
        width: Float,
        height: Float,
        state: CanvasState
    ) {
        if (width <= 0f || height <= 0f) return
        val zoom = state.zoom.value

        val itemId = element.metadata.previewItemId ?: ""

        // Try to load and draw actual item icon
        if (itemId.isNotBlank() && itemRegistry != null && assetLoader != null && assetLoader.canLoadTextures) {
            val item = getOrLoadItem(itemId)
            if (item != null) {
                val texture = getOrLoadTexture(item.iconPath)
                if (texture != null) {
                    // Draw dark viewport background
                    drawRect(color = VIEWPORT_BG, topLeft = position, size = Size(width, height))
                    drawRect(color = VIEWPORT_BORDER, topLeft = position, size = Size(width, height), style = Stroke(width = 1f))

                    // Draw icon centered, preserving aspect ratio
                    val imgW = texture.width.toFloat()
                    val imgH = texture.height.toFloat()
                    val padding = 4f * zoom
                    val availW = width - 2 * padding
                    val availH = height - 2 * padding
                    val scale = minOf(availW / imgW, availH / imgH).coerceAtMost(1f * zoom)
                    val drawW = imgW * scale
                    val drawH = imgH * scale
                    val drawX = position.x + (width - drawW) / 2
                    val drawY = position.y + (height - drawH) / 2

                    drawImage(
                        image = texture,
                        dstOffset = IntOffset(drawX.toInt(), drawY.toInt()),
                        dstSize = IntSize(drawW.toInt(), drawH.toInt())
                    )

                    // [3D] badge
                    draw3DBadge(position, zoom)
                    return
                }
                // Texture loading — fall through to styled placeholder
            }
            // Item loading — fall through to styled placeholder
        }

        // Styled fallback placeholder (FR-2)
        drawItemPreviewFallback(element, position, width, height, state, itemId)
    }

    /**
     * Styled fallback for ItemPreviewComponent when icons can't be loaded.
     */
    private fun DrawScope.drawItemPreviewFallback(
        element: UIElement,
        position: Offset,
        width: Float,
        height: Float,
        state: CanvasState,
        itemId: String
    ) {
        val zoom = state.zoom.value

        // Dark viewport background
        drawRect(color = VIEWPORT_BG, topLeft = position, size = Size(width, height))
        drawRect(color = VIEWPORT_BORDER, topLeft = position, size = Size(width, height), style = Stroke(width = 1f))

        val label = if (itemId.isBlank()) "No Item" else itemId

        // Very small elements: just show item ID text
        if (width < 32f * zoom || height < 32f * zoom) {
            drawCenteredLabel(label, position, width, height, Color(0xFF9D9D9D), zoom)
            draw3DBadge(position, zoom)
            return
        }

        // Inventory slot outline — centered, capped at 48px
        val slotSide = minOf(48f * zoom, width * 0.5f, height * 0.5f)
        val slotX = position.x + (width - slotSide) / 2
        val slotY = position.y + (height - slotSide) / 2 - 8f * zoom  // shift up for label below

        drawRoundRect(
            color = SLOT_FILL,
            topLeft = Offset(slotX, slotY),
            size = Size(slotSide, slotSide),
            cornerRadius = CornerRadius(4f * zoom)
        )
        drawRoundRect(
            color = SLOT_BORDER,
            topLeft = Offset(slotX, slotY),
            size = Size(slotSide, slotSide),
            cornerRadius = CornerRadius(4f * zoom),
            style = Stroke(width = 1f)
        )

        // Diamond/gem icon inside slot
        val iconSize = slotSide * 0.4f
        val cx = slotX + slotSide / 2
        val cy = slotY + slotSide / 2
        val path = Path().apply {
            moveTo(cx, cy - iconSize / 2)          // top
            lineTo(cx + iconSize / 2, cy)           // right
            lineTo(cx, cy + iconSize / 2)           // bottom
            lineTo(cx - iconSize / 2, cy)           // left
            close()
        }
        drawPath(path, color = ITEM_ICON_COLOR, style = Stroke(width = 1.5f * zoom))

        // Item ID label below slot
        val labelY = slotY + slotSide + 4f * zoom
        val labelHeight = height - (labelY - position.y)
        if (labelHeight > 0) {
            drawCenteredLabel(label, Offset(position.x, labelY), width, labelHeight, Color(0xFF9D9D9D), zoom)
        }

        // [3D] badge
        draw3DBadge(position, zoom)
    }

    /**
     * Draw a BlockSelector element with isometric cube wireframe.
     */
    private fun DrawScope.drawBlockSelector(
        element: UIElement,
        position: Offset,
        width: Float,
        height: Float,
        state: CanvasState
    ) {
        if (width <= 0f || height <= 0f) return
        val zoom = state.zoom.value

        // Dark viewport background
        drawRect(color = VIEWPORT_BG, topLeft = position, size = Size(width, height))
        drawRect(color = VIEWPORT_BORDER, topLeft = position, size = Size(width, height), style = Stroke(width = 1f))

        val block = element.textProperty("Block", "")
        val label = if (block.isBlank()) "No Block" else block

        // Very small elements: just text
        if (width < 40f * zoom || height < 40f * zoom) {
            drawCenteredLabel(label, position, width, height, Color(0xFF9D9D9D), zoom)
            draw3DBadge(position, zoom)
            return
        }

        // Isometric cube wireframe — centered, capped at 60px face width
        val faceW = minOf(60f * zoom, width * 0.35f, height * 0.35f)
        val faceH = faceW * 0.5f  // isometric squash
        val cx = position.x + width / 2
        val cy = position.y + height / 2 - 10f * zoom  // shift up for label

        // Top face (parallelogram)
        val topLeft = Offset(cx - faceW / 2, cy - faceH / 2)
        val topCenter = Offset(cx, cy - faceH)
        val topRight = Offset(cx + faceW / 2, cy - faceH / 2)
        val topBottom = Offset(cx, cy)

        // Left face
        val leftBottom = Offset(cx - faceW / 2, cy + faceH / 2)

        // Right face
        val rightBottom = Offset(cx + faceW / 2, cy + faceH / 2)
        val bottom = Offset(cx, cy + faceH)

        val strokeWidth = 1.5f * zoom

        // Top face
        drawLine(BLOCK_COLOR, topLeft, topCenter, strokeWidth)
        drawLine(BLOCK_COLOR, topCenter, topRight, strokeWidth)
        drawLine(BLOCK_COLOR, topRight, topBottom, strokeWidth)
        drawLine(BLOCK_COLOR, topBottom, topLeft, strokeWidth)

        // Left face
        drawLine(BLOCK_COLOR, topLeft, leftBottom, strokeWidth)
        drawLine(BLOCK_COLOR, leftBottom, bottom, strokeWidth)
        drawLine(BLOCK_COLOR, bottom, topBottom, strokeWidth)

        // Right face
        drawLine(BLOCK_COLOR, topRight, rightBottom, strokeWidth)
        drawLine(BLOCK_COLOR, rightBottom, bottom, strokeWidth)

        // Block name label below cube
        val labelY = cy + faceH + 4f * zoom
        val labelHeight = (position.y + height) - labelY
        if (labelHeight > 0) {
            drawCenteredLabel(label, Offset(position.x, labelY), width, labelHeight, Color(0xFF9D9D9D), zoom)
        }

        // [3D] badge
        draw3DBadge(position, zoom)
    }

    /**
     * Draw a CharacterPreviewComponent element with humanoid silhouette.
     */
    private fun DrawScope.drawCharacterPreview(
        element: UIElement,
        position: Offset,
        width: Float,
        height: Float,
        state: CanvasState
    ) {
        drawHumanoidPreview(position, width, height, state, CHARACTER_COLOR, "Character")
    }

    /**
     * Draw a PlayerPreviewComponent element with humanoid silhouette.
     */
    private fun DrawScope.drawPlayerPreview(
        element: UIElement,
        position: Offset,
        width: Float,
        height: Float,
        state: CanvasState
    ) {
        drawHumanoidPreview(position, width, height, state, PLAYER_COLOR, "Player")
    }

    /**
     * Shared renderer for humanoid silhouette previews (Character, Player).
     */
    private fun DrawScope.drawHumanoidPreview(
        position: Offset,
        width: Float,
        height: Float,
        state: CanvasState,
        accentColor: Color,
        label: String
    ) {
        if (width <= 0f || height <= 0f) return
        val zoom = state.zoom.value

        // Dark viewport background
        drawRect(color = VIEWPORT_BG, topLeft = position, size = Size(width, height))
        drawRect(color = VIEWPORT_BORDER, topLeft = position, size = Size(width, height), style = Stroke(width = 1f))

        // Silhouette — capped at 50px height
        val silH = minOf(50f * zoom, height * 0.5f, width * 0.6f)
        val cx = position.x + width / 2
        val cy = position.y + height / 2 - 8f * zoom  // shift up for label

        val strokeWidth = 1.5f * zoom

        // Head (circle)
        val headR = silH * 0.18f
        val headCy = cy - silH / 2 + headR
        drawCircle(accentColor, radius = headR, center = Offset(cx, headCy), style = Stroke(width = strokeWidth))

        // Torso (trapezoid: wider at shoulders, narrower at waist)
        val shoulderW = silH * 0.4f
        val waistW = silH * 0.25f
        val torsoTop = headCy + headR + 2f * zoom
        val torsoBottom = cy + silH / 2

        val torsoPath = Path().apply {
            moveTo(cx - shoulderW / 2, torsoTop)        // top-left
            lineTo(cx + shoulderW / 2, torsoTop)        // top-right
            lineTo(cx + waistW / 2, torsoBottom)         // bottom-right
            lineTo(cx - waistW / 2, torsoBottom)         // bottom-left
            close()
        }
        drawPath(torsoPath, color = accentColor, style = Stroke(width = strokeWidth))

        // Label below silhouette
        val labelY = cy + silH / 2 + 6f * zoom
        val labelHeight = (position.y + height) - labelY
        if (labelHeight > 0) {
            drawCenteredLabel(label, Offset(position.x, labelY), width, labelHeight, Color(0xFF9D9D9D), zoom)
        }

        // [3D] badge
        draw3DBadge(position, zoom)
    }

    /**
     * Draw an ItemGrid element with property-driven slot grid.
     */
    private fun DrawScope.drawItemGrid(
        element: UIElement,
        position: Offset,
        width: Float,
        height: Float,
        state: CanvasState
    ) {
        if (width <= 0f || height <= 0f) return
        val zoom = state.zoom.value

        // Background
        val background = element.getProperty("Background")
        var drewBackground = false

        when (background) {
            is PropertyValue.Color -> {
                val bgColor = background.toComposeColor()
                if (bgColor.alpha > 0f) {
                    drawRect(color = bgColor, topLeft = position, size = Size(width, height))
                    drewBackground = true
                }
            }
            else -> { /* No background specified */ }
        }

        // Read ItemGridStyle compound tuple (contains SlotSize, SlotSpacing, SlotIconSize, SlotBackground, etc.)
        val gridStyle = resolveStyleToTuple(element.getProperty("Style"))

        // Read properties — prefer direct properties, fall back to Style tuple
        val slotsPerRow = element.numberProperty("SlotsPerRow", 9f).toInt().coerceAtLeast(1)
        val slotSizeCanvas = (element.getProperty("SlotSize") as? PropertyValue.Number)?.value?.toFloat()
            ?: (gridStyle?.get("SlotSize") as? PropertyValue.Number)?.value?.toFloat() ?: 74f
        val slotSpacingCanvas = (element.getProperty("SlotSpacing") as? PropertyValue.Number)?.value?.toFloat()
            ?: (gridStyle?.get("SlotSpacing") as? PropertyValue.Number)?.value?.toFloat() ?: 4f
        val slotBorderColor = element.colorProperty("SlotBorderColor", SLOT_BORDER)
        val slotBgColor = element.colorProperty("SlotBackground", SLOT_FILL)
        val slotBgTexturePath = (element.getProperty("SlotBackground") as? PropertyValue.Text)?.value
            ?: (gridStyle?.get("SlotBackground") as? PropertyValue.Text)?.value
            ?: (gridStyle?.get("SlotBackground") as? PropertyValue.ImagePath)?.path
        val slotBgTexture = if (slotBgTexturePath != null) getOrLoadTexture(slotBgTexturePath) else null
        val slotIconSizeCanvas = (element.getProperty("SlotIconSize") as? PropertyValue.Number)?.value?.toFloat()
            ?: (gridStyle?.get("SlotIconSize") as? PropertyValue.Number)?.value?.toFloat() ?: 0f
        val paddingCanvas = 2f

        // Convert to screen coordinates
        val slotSize = slotSizeCanvas * zoom
        val slotSpacing = slotSpacingCanvas * zoom
        val padding = paddingCanvas * zoom

        val availableWidth = width - (2 * padding)
        val availableHeight = height - (2 * padding)

        // If too small for even one slot, render as single slot
        if (availableWidth < slotSize || availableHeight < slotSize) {
            drawRoundRect(color = slotBgColor, topLeft = position, size = Size(width, height), cornerRadius = CornerRadius(4f * zoom))
            drawRoundRect(color = slotBorderColor, topLeft = position, size = Size(width, height), cornerRadius = CornerRadius(4f * zoom), style = Stroke(width = 1f))
            return
        }

        val fittingSlotsPerRow = if (slotSize + slotSpacing > 0) {
            ((availableWidth + slotSpacing) / (slotSize + slotSpacing)).toInt().coerceAtLeast(1)
        } else 1
        val actualSlotsPerRow = minOf(slotsPerRow, fittingSlotsPerRow)

        val numRows = if (slotSize + slotSpacing > 0) {
            ((availableHeight + slotSpacing) / (slotSize + slotSpacing)).toInt().coerceAtLeast(1)
        } else 1

        // Cap total rendered slots at 100
        val totalSlots = (actualSlotsPerRow * numRows).coerceAtMost(100)
        val cappedRows = (totalSlots + actualSlotsPerRow - 1) / actualSlotsPerRow

        val startX = position.x + padding
        val startY = position.y + padding

        for (row in 0 until cappedRows) {
            val slotsInRow = if (row == cappedRows - 1) totalSlots - row * actualSlotsPerRow else actualSlotsPerRow
            for (col in 0 until slotsInRow) {
                val slotX = startX + col * (slotSize + slotSpacing)
                val slotY = startY + row * (slotSize + slotSpacing)

                if (slotX + slotSize > position.x + width + 0.5f || slotY + slotSize > position.y + height + 0.5f) continue

                // Slot background: use texture if available, otherwise color
                if (slotBgTexture != null) {
                    drawImage(
                        image = slotBgTexture,
                        dstOffset = IntOffset(slotX.toInt(), slotY.toInt()),
                        dstSize = IntSize(slotSize.toInt(), slotSize.toInt())
                    )
                } else {
                    drawRoundRect(
                        color = slotBgColor,
                        topLeft = Offset(slotX, slotY),
                        size = Size(slotSize, slotSize),
                        cornerRadius = CornerRadius(4f * zoom)
                    )
                    drawRoundRect(
                        color = slotBorderColor,
                        topLeft = Offset(slotX, slotY),
                        size = Size(slotSize, slotSize),
                        cornerRadius = CornerRadius(4f * zoom),
                        style = Stroke(width = 1f)
                    )
                }

                // Draw icon placeholder if SlotIconSize is set
                if (slotIconSizeCanvas > 0f) {
                    val iconSize = slotIconSizeCanvas * zoom
                    val iconX = slotX + (slotSize - iconSize) / 2
                    val iconY = slotY + (slotSize - iconSize) / 2
                    drawRoundRect(
                        color = slotBorderColor.copy(alpha = 0.3f),
                        topLeft = Offset(iconX, iconY),
                        size = Size(iconSize, iconSize),
                        cornerRadius = CornerRadius(2f * zoom),
                        style = Stroke(width = 0.5f)
                    )
                }
            }
        }

        if (!drewBackground) {
            drawRect(color = DEFAULT_BORDER, topLeft = position, size = Size(width, height), style = Stroke(width = 1f))
        }
    }

    /**
     * Draw a SceneBlur element with frosted-glass effect.
     */
    private fun DrawScope.drawSceneBlur(
        element: UIElement,
        position: Offset,
        width: Float,
        height: Float,
        state: CanvasState
    ) {
        if (width <= 0f || height <= 0f) return
        val zoom = state.zoom.value

        // Frosted-glass fill
        drawRect(
            color = Color.White.copy(alpha = 0.08f),
            topLeft = position,
            size = Size(width, height)
        )

        // Border
        drawRect(
            color = Color.White.copy(alpha = 0.15f),
            topLeft = position,
            size = Size(width, height),
            style = Stroke(width = 1f)
        )

        // Centered "Blur" label
        drawCenteredLabel("Blur", position, width, height, Color(0xFF9D9D9D), zoom)
    }

    // ==================== 3D Preview Helper Methods ====================

    /**
     * Draw a [3D] badge in the top-left corner of an element.
     */
    private fun DrawScope.draw3DBadge(position: Offset, zoom: Float) {
        val badgeText = "[3D]"
        val badgeStyle = TextStyle(
            color = BADGE_COLOR,
            fontSize = (9 * zoom).sp
        )
        val badgeLayout = measureTextCached(badgeText, badgeStyle)
        val badgeX = position.x + 3f * zoom
        val badgeY = position.y + 2f * zoom
        drawText(badgeLayout, topLeft = Offset(badgeX, badgeY))
    }

    /**
     * Draw a centered text label within a given rect.
     */
    private fun DrawScope.drawCenteredLabel(
        text: String,
        position: Offset,
        width: Float,
        height: Float,
        color: Color,
        zoom: Float
    ) {
        val style = TextStyle(color = color, fontSize = (11 * zoom).sp)
        val layout = measureTextCached(text, style)
        val textX = position.x + (width - layout.size.width) / 2
        val textY = position.y + (height - layout.size.height) / 2
        drawText(layout, topLeft = Offset(textX, textY))
    }

    /**
     * Draw unknown element type with specialized placeholders for known Hytale elements.
     */
    private fun DrawScope.drawUnknownElement(
        element: UIElement,
        position: Offset,
        width: Float,
        height: Float,
        state: CanvasState
    ) {
        if (width <= 0f || height <= 0f) return

        // Orange border to indicate unknown
        drawRect(
            color = Color(0xFFFF9800),
            topLeft = position,
            size = Size(width, height),
            style = Stroke(width = 1f)
        )

        // Type name
        val textPos = position + Offset(2f, 2f)
        val textStyle = TextStyle(
            color = Color(0xFFFF9800),
            fontSize = (10 * state.zoom.value).sp
        )
        drawTextStable(text = element.type.value, topLeft = textPos, style = textStyle)
    }

    /**
     * Draw selection highlight around element with resize handles.
     * Only draws handles that are in the allowedHandles set.
     * Draws a layout badge if parentLayoutMode is non-null.
     */
    private fun DrawScope.drawSelectionHighlight(
        position: Offset,
        width: Float,
        height: Float,
        allowedHandles: Set<CanvasState.ResizeHandle> = CanvasState.ResizeHandle.entries.toSet(),
        parentLayoutMode: String? = null,
        dragAxes: Pair<Boolean, Boolean> = Pair(true, true)
    ) {
        val (canH, canV) = dragAxes

        // Draw selection border
        drawRect(
            color = SELECTION_COLOR,
            topLeft = position - Offset(SELECTION_STROKE_WIDTH, SELECTION_STROKE_WIDTH),
            size = Size(width + SELECTION_STROKE_WIDTH * 2, height + SELECTION_STROKE_WIDTH * 2),
            style = Stroke(width = SELECTION_STROKE_WIDTH)
        )

        // Map handles to their positions
        val handlePositions = mapOf(
            CanvasState.ResizeHandle.TOP_LEFT to (position + Offset(0f, 0f)),
            CanvasState.ResizeHandle.TOP_CENTER to (position + Offset(width / 2, 0f)),
            CanvasState.ResizeHandle.TOP_RIGHT to (position + Offset(width, 0f)),
            CanvasState.ResizeHandle.LEFT_CENTER to (position + Offset(0f, height / 2)),
            CanvasState.ResizeHandle.RIGHT_CENTER to (position + Offset(width, height / 2)),
            CanvasState.ResizeHandle.BOTTOM_LEFT to (position + Offset(0f, height)),
            CanvasState.ResizeHandle.BOTTOM_CENTER to (position + Offset(width / 2, height)),
            CanvasState.ResizeHandle.BOTTOM_RIGHT to (position + Offset(width, height))
        )

        // Only draw allowed handles
        for ((handle, handlePos) in handlePositions) {
            if (handle !in allowedHandles) continue
            drawRect(
                color = HANDLE_COLOR,
                topLeft = handlePos - Offset(HANDLE_SIZE / 2, HANDLE_SIZE / 2),
                size = Size(HANDLE_SIZE, HANDLE_SIZE)
            )
            drawRect(
                color = HANDLE_BORDER_COLOR,
                topLeft = handlePos - Offset(HANDLE_SIZE / 2, HANDLE_SIZE / 2),
                size = Size(HANDLE_SIZE, HANDLE_SIZE),
                style = Stroke(width = 1f)
            )
        }

        // Draw layout badge for layout-managed elements (FR-6)
        if (parentLayoutMode != null) {
            drawLayoutBadge(position, parentLayoutMode)
        }

        // Draw directional arrows for axis-restricted elements
        if ((canH || canV) && !(canH && canV)) {
            drawMoveArrows(position, width, height, canH, canV)
        }
    }

    /**
     * Draw arrow indicators on element edges showing allowed movement directions.
     * Horizontal: arrows on left and right edges. Vertical: arrows on top and bottom edges.
     */
    private fun DrawScope.drawMoveArrows(
        position: Offset,
        width: Float,
        height: Float,
        canH: Boolean,
        canV: Boolean
    ) {
        val arrowColor = Color(0xFFE0E0E0) // soft white — stands out against dark canvas
        val arrowSize = 10f
        val arrowStroke = 2.5f
        val gap = 22f // distance from element edge

        if (canH) {
            // Left arrow: ◄
            val leftTip = position + Offset(-gap, height / 2)
            drawLine(arrowColor, leftTip + Offset(arrowSize, -arrowSize), leftTip, strokeWidth = arrowStroke, cap = StrokeCap.Round)
            drawLine(arrowColor, leftTip + Offset(arrowSize, arrowSize), leftTip, strokeWidth = arrowStroke, cap = StrokeCap.Round)

            // Right arrow: ►
            val rightTip = position + Offset(width + gap, height / 2)
            drawLine(arrowColor, rightTip + Offset(-arrowSize, -arrowSize), rightTip, strokeWidth = arrowStroke, cap = StrokeCap.Round)
            drawLine(arrowColor, rightTip + Offset(-arrowSize, arrowSize), rightTip, strokeWidth = arrowStroke, cap = StrokeCap.Round)
        }

        if (canV) {
            // Top arrow: ▲
            val topTip = position + Offset(width / 2, -gap)
            drawLine(arrowColor, topTip + Offset(-arrowSize, arrowSize), topTip, strokeWidth = arrowStroke, cap = StrokeCap.Round)
            drawLine(arrowColor, topTip + Offset(arrowSize, arrowSize), topTip, strokeWidth = arrowStroke, cap = StrokeCap.Round)

            // Bottom arrow: ▼
            val bottomTip = position + Offset(width / 2, height + gap)
            drawLine(arrowColor, bottomTip + Offset(-arrowSize, -arrowSize), bottomTip, strokeWidth = arrowStroke, cap = StrokeCap.Round)
            drawLine(arrowColor, bottomTip + Offset(arrowSize, -arrowSize), bottomTip, strokeWidth = arrowStroke, cap = StrokeCap.Round)
        }
    }

    /**
     * Draw a small layout direction badge at the top-left of a selection box.
     * Shows ≡ for Top (vertical stack) or ||| for Left (horizontal stack).
     */
    private fun DrawScope.drawLayoutBadge(position: Offset, layoutMode: String) {
        val badgeColor = Color(0x998A8A9A) // #8A8A9A at 60% opacity
        val badgeSize = 16f
        val badgeOffset = Offset(-badgeSize - 4f, -badgeSize - 4f)
        val badgeTopLeft = position + badgeOffset

        // Badge background (Fill is the default style)
        drawRect(
            color = Color(0x2D2D2D).copy(alpha = 0.8f),
            topLeft = badgeTopLeft,
            size = Size(badgeSize, badgeSize)
        )
        drawRect(
            color = badgeColor,
            topLeft = badgeTopLeft,
            size = Size(badgeSize, badgeSize),
            style = Stroke(width = 1f)
        )

        // Draw lines indicating layout direction
        val lineColor = badgeColor
        val cx = badgeTopLeft.x + badgeSize / 2
        val cy = badgeTopLeft.y + badgeSize / 2
        val lineLen = 8f
        val spacing = 3f

        if (layoutMode == "Top") {
            // Three horizontal lines (≡) for vertical stack
            for (i in -1..1) {
                val y = cy + i * spacing
                drawLine(lineColor, Offset(cx - lineLen / 2, y), Offset(cx + lineLen / 2, y), strokeWidth = 1.5f)
            }
        } else {
            // Three vertical lines (|||) for horizontal stack
            for (i in -1..1) {
                val x = cx + i * spacing
                drawLine(lineColor, Offset(x, cy - lineLen / 2), Offset(x, cy + lineLen / 2), strokeWidth = 1.5f)
            }
        }
    }

    /**
     * Clear the loaded texture cache.
     * Call this when the asset loader changes or assets need to be reloaded.
     */
    fun clearTextureCache() {
        loadedTextures.clear()
        loadingPaths.clear()
        itemCache.clear()
        loadingItems.clear()
    }
}
