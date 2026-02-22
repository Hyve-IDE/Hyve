// Contract: CanvasState feeds resolved tree from VariableAwareParser --
// all @refs/expressions are concrete values (DL-001)
package com.hyve.ui.rendering.layout

import com.hyve.ui.core.domain.elements.UIElement
import com.hyve.ui.core.domain.properties.PropertyValue
import com.hyve.ui.core.domain.anchor.AnchorDimension
import com.hyve.ui.rendering.painter.resolveStyleToTuple
import com.hyve.ui.schema.SchemaRegistry

/**
 * Padding values extracted from a Padding property tuple.
 * Supports: (Full: N), (Horizontal: N, Vertical: N), (Left: N, Right: N, Top: N, Bottom: N)
 */
private data class PaddingValues(
    val left: Float = 0f,
    val right: Float = 0f,
    val top: Float = 0f,
    val bottom: Float = 0f
)

/**
 * Resolve a property from an element, falling back to the Style tuple.
 * In Hytale, style references like $C.@Container carry layout properties
 * (LayoutMode, Padding, Spacing, etc.) inside the resolved Style tuple.
 */
private fun resolveProperty(element: UIElement, name: String): PropertyValue? {
    element.getProperty(name)?.let { return it }
    val styleTuple = resolveStyleToTuple(element.getProperty("Style")) ?: return null
    return styleTuple.values[name]
}

/**
 * Read padding from an element's Padding property.
 * Supports shorthand forms: Full, Horizontal, Vertical.
 */
private fun readPadding(element: UIElement): PaddingValues {
    val padding = resolveStyleToTuple(resolveProperty(element, "Padding")) ?: return PaddingValues()
    val values = padding.values

    fun findFloat(key: String): Float? =
        values.entries.find { it.key.equals(key, ignoreCase = true) }
            ?.value?.let { (it as? PropertyValue.Number)?.value?.toFloat() }

    val full = findFloat("Full")
    if (full != null) return PaddingValues(full, full, full, full)

    val horizontal = findFloat("Horizontal") ?: 0f
    val vertical = findFloat("Vertical") ?: 0f

    return PaddingValues(
        left = findFloat("Left") ?: horizontal,
        right = findFloat("Right") ?: horizontal,
        top = findFloat("Top") ?: vertical,
        bottom = findFloat("Bottom") ?: vertical
    )
}

/**
 * Apply padding to a Rect, shrinking the content area.
 */
private fun Rect.inset(padding: PaddingValues): Rect {
    return Rect(
        x = x + padding.left,
        y = y + padding.top,
        width = (width - padding.left - padding.right).coerceAtLeast(0f),
        height = (height - padding.top - padding.bottom).coerceAtLeast(0f)
    )
}

/** All layout modes that position children automatically */
private val MANAGED_LAYOUT_MODES = setOf(
    "Top", "Left", "Right", "Bottom", "Middle", "Center",
    "TopScrolling", "BottomScrolling", "LeftScrolling",
    "Full", "CenterMiddle", "LeftCenterWrap"
)

/** Stack-based layout modes (children flow in a direction) */
private val STACK_LAYOUT_MODES = setOf(
    "Top", "Left", "Right", "Bottom",
    "TopScrolling", "BottomScrolling", "LeftScrolling"
)

/**
 * Layout engine that calculates absolute pixel bounds for UI elements.
 * Works exclusively with abstract element types from the abstraction layer.
 *
 * Supports:
 * - Anchor-based positioning (absolute and relative)
 * - LayoutMode: Top (vertical stack, top-to-bottom)
 * - LayoutMode: Bottom (vertical stack, bottom-to-top)
 * - LayoutMode: Left (horizontal stack, left-to-right)
 * - LayoutMode: Right (horizontal stack, right-to-left)
 * - LayoutMode: Middle / Center (centered children)
 * - Padding on containers
 * - FlexWeight distribution for stack layouts
 *
 * Key Design:
 * - Works with ABSTRACT types (ScrollView, Button, etc.) not format-specific types
 * - No .ui format knowledge - that's in the parser/exporter layers
 * - Recursive layout calculation for nested elements
 */
class LayoutEngine(private val schema: SchemaRegistry) {
    /**
     * Calculate bounds for an element and all its children.
     *
     * Uses IdentityHashMap for O(1) lookup performance. Regular HashMap with UIElement keys
     * would be O(n) per lookup because UIElement is a data class with recursive equals/hashCode.
     *
     * @param element The UI element to calculate bounds for
     * @param parentBounds The parent container's bounds
     * @return Map of element to calculated bounds (uses reference equality)
     */
    fun calculateLayout(
        element: UIElement,
        parentBounds: Rect = Rect.screen()
    ): Map<UIElement, ElementBounds> {
        // Use IdentityHashMap for O(1) lookups based on reference equality
        // This is critical because UIElement is a data class with potentially deep children,
        // and standard HashMap would compute hashCode() recursively on every lookup
        val results = java.util.IdentityHashMap<UIElement, ElementBounds>()
        calculateElementBounds(element, parentBounds, results)
        return results
    }

    /**
     * Calculate bounds for a single element (without children).
     *
     * @param element The UI element
     * @param parentBounds The parent container's bounds
     * @return Calculated bounds
     */
    fun calculateBounds(
        element: UIElement,
        parentBounds: Rect,
        siblings: List<UIElement> = emptyList()
    ): ElementBounds {
        // Check if element has LayoutMode property (direct or via Style tuple)
        val layoutMode = resolveProperty(element, "LayoutMode") as? PropertyValue.Text

        val bounds = when (layoutMode?.value) {
            "Top", "Left", "Right", "Bottom",
            "TopScrolling", "BottomScrolling", "LeftScrolling" ->
                calculateStackLayout(element, parentBounds, Direction.VERTICAL, siblings)
            "Middle", "Center", "CenterMiddle" -> calculateCenterLayout(element, parentBounds)
            "Full", "LeftCenterWrap" -> {
                // These modes position children but element itself uses anchor
                val anchor = element.getProperty("Anchor") as? PropertyValue.Anchor
                if (anchor != null) AnchorCalculator.calculateBounds(anchor.anchor, parentBounds)
                else parentBounds
            }
            else -> {
                // Default: anchor-based layout
                val anchor = element.getProperty("Anchor") as? PropertyValue.Anchor
                if (anchor != null) {
                    AnchorCalculator.calculateBounds(anchor.anchor, parentBounds)
                } else {
                    // No anchor and no layout mode - default to fill parent
                    parentBounds
                }
            }
        }

        // Apply MinWidth/MaxWidth constraints
        val minWidth = (resolveProperty(element, "MinWidth") as? PropertyValue.Number)?.value?.toFloat()
        val maxWidth = (resolveProperty(element, "MaxWidth") as? PropertyValue.Number)?.value?.toFloat()
        val clampedBounds = if (minWidth != null || maxWidth != null) {
            val currentWidth = bounds.width
            val newWidth = currentWidth
                .let { w -> if (minWidth != null && w < minWidth) minWidth else w }
                .let { w -> if (maxWidth != null && w > maxWidth) maxWidth else w }
            if (newWidth != currentWidth) {
                Rect(bounds.x, bounds.y, newWidth, bounds.height)
            } else bounds
        } else bounds

        return ElementBounds(
            elementId = element.id,
            bounds = clampedBounds,
            visible = element.metadata.visible
        )
    }

    /**
     * Recursively calculate bounds for an element and all its children.
     */
    private fun calculateElementBounds(
        element: UIElement,
        parentBounds: Rect,
        results: MutableMap<UIElement, ElementBounds>
    ) {
        // Calculate bounds for this element
        val bounds = calculateBounds(element, parentBounds)
        results[element] = bounds

        // If element is a container with LayoutMode (direct or via Style), children stack/flow
        val layoutMode = (resolveProperty(element, "LayoutMode") as? PropertyValue.Text)?.value
        if (layoutMode != null && layoutMode in MANAGED_LAYOUT_MODES) {
            // Apply padding to get the content area for children
            val padding = readPadding(element)
            val contentArea = bounds.bounds.inset(padding)

            if (layoutMode in STACK_LAYOUT_MODES) {
                layoutStackChildren(element, layoutMode, contentArea, results)
            } else if (layoutMode == "LeftCenterWrap") {
                layoutWrapChildren(element, contentArea, results)
            } else if (layoutMode == "Full") {
                // Full: children use anchor-based positioning (identical to no layout mode)
                element.children.forEach { child ->
                    calculateElementBounds(child, contentArea, results)
                }
            } else {
                // Middle / Center / CenterMiddle: each child is centered within content area
                element.children.forEach { child ->
                    val childAnchor = child.getProperty("Anchor") as? PropertyValue.Anchor
                    val childBounds = if (childAnchor != null) {
                        AnchorCalculator.calculateBounds(childAnchor.anchor, contentArea)
                    } else {
                        contentArea
                    }
                    calculateElementBounds(child, childBounds, results)
                }
            }
        } else {
            // No layout mode - children use anchor-based positioning relative to this element
            // Still apply padding for non-layout containers
            val padding = readPadding(element)
            val contentArea = bounds.bounds.inset(padding)
            element.children.forEach { child ->
                calculateElementBounds(child, contentArea, results)
            }
        }
    }

    /**
     * Layout children in a stack direction (Top/Bottom/Left/Right) with FlexWeight support.
     *
     * Two-pass algorithm:
     * 1. Measure fixed-size children (those with explicit anchor dimensions)
     * 2. Distribute remaining space among FlexWeight children proportionally
     */
    private fun layoutStackChildren(
        element: UIElement,
        layoutMode: String,
        contentArea: Rect,
        results: MutableMap<UIElement, ElementBounds>
    ) {
        val isVertical = layoutMode in setOf("Top", "Bottom", "TopScrolling", "BottomScrolling")
        val isReversed = layoutMode in setOf("Bottom", "Right", "BottomScrolling")
        val spacing = (resolveProperty(element, "Spacing") as? PropertyValue.Number)?.value?.toFloat() ?: 0f
        val totalSpace = if (isVertical) contentArea.height else contentArea.width

        // Pass 1: measure fixed children and collect flex weights
        data class ChildInfo(val child: UIElement, val fixedSize: Float, val flexWeight: Float)

        val childInfos = element.children.map { child ->
            val flexWeight = (child.getProperty("FlexWeight") as? PropertyValue.Number)?.value?.toFloat() ?: 0f
            val childAnchor = child.getProperty("Anchor") as? PropertyValue.Anchor

            val fixedSize = if (flexWeight > 0f) {
                0f // Flex children get their size from remaining space
            } else if (isVertical) {
                childAnchor?.anchor?.height?.let {
                    when (it) {
                        is AnchorDimension.Absolute -> it.pixels
                        is AnchorDimension.Relative -> it.ratio * contentArea.height
                    }
                } ?: 0f
            } else {
                childAnchor?.anchor?.width?.let {
                    when (it) {
                        is AnchorDimension.Absolute -> it.pixels
                        is AnchorDimension.Relative -> it.ratio * contentArea.width
                    }
                } ?: 0f
            }

            ChildInfo(child, fixedSize, flexWeight)
        }

        val totalFixed = childInfos.sumOf { it.fixedSize.toDouble() }.toFloat()
        val totalWeight = childInfos.sumOf { it.flexWeight.toDouble() }.toFloat()
        val totalSpacing = if (childInfos.size > 1) spacing * (childInfos.size - 1) else 0f
        val remainingSpace = (totalSpace - totalFixed - totalSpacing).coerceAtLeast(0f)

        // Pass 2: position children
        // For reversed modes, start from the end
        var currentOffset = if (isReversed) totalSpace else 0f

        childInfos.forEach { (child, fixedSize, flexWeight) ->
            val childSize = if (flexWeight > 0f && totalWeight > 0f) {
                remainingSpace * (flexWeight / totalWeight)
            } else {
                fixedSize
            }

            val childRect = if (isVertical) {
                val y = if (isReversed) {
                    currentOffset -= childSize
                    contentArea.y + currentOffset
                } else {
                    val pos = contentArea.y + currentOffset
                    currentOffset += childSize
                    pos
                }
                Rect(contentArea.x, y, contentArea.width, childSize)
            } else {
                val x = if (isReversed) {
                    currentOffset -= childSize
                    contentArea.x + currentOffset
                } else {
                    val pos = contentArea.x + currentOffset
                    currentOffset += childSize
                    pos
                }
                Rect(x, contentArea.y, childSize, contentArea.height)
            }

            // Add inter-child spacing
            if (spacing > 0f) {
                if (isReversed) currentOffset -= spacing else currentOffset += spacing
            }

            calculateElementBounds(child, childRect, results)
        }
    }

    /**
     * Layout children in LeftCenterWrap mode: horizontal flow with row wrapping.
     * Children flow left-to-right. When total width exceeds container, wrap to next row.
     * Each row is vertically centered.
     */
    private fun layoutWrapChildren(
        element: UIElement,
        contentArea: Rect,
        results: MutableMap<UIElement, ElementBounds>
    ) {
        // Measure all children first
        data class ChildMeasure(val child: UIElement, val width: Float, val height: Float)
        val measured = element.children.map { child ->
            val childAnchor = child.getProperty("Anchor") as? PropertyValue.Anchor
            val w = childAnchor?.anchor?.width?.let {
                when (it) { is AnchorDimension.Absolute -> it.pixels; is AnchorDimension.Relative -> it.ratio * contentArea.width }
            } ?: 0f
            val h = childAnchor?.anchor?.height?.let {
                when (it) { is AnchorDimension.Absolute -> it.pixels; is AnchorDimension.Relative -> it.ratio * contentArea.height }
            } ?: 0f
            ChildMeasure(child, w, h)
        }

        // Group into rows
        val rows = mutableListOf<MutableList<ChildMeasure>>()
        var currentRow = mutableListOf<ChildMeasure>()
        var currentRowWidth = 0f

        for (cm in measured) {
            if (currentRowWidth + cm.width > contentArea.width && currentRow.isNotEmpty()) {
                rows.add(currentRow)
                currentRow = mutableListOf()
                currentRowWidth = 0f
            }
            currentRow.add(cm)
            currentRowWidth += cm.width
        }
        if (currentRow.isNotEmpty()) rows.add(currentRow)

        // Position rows, vertically centering each child within its row
        var currentY = contentArea.y
        for (row in rows) {
            val rowHeight = row.maxOfOrNull { it.height } ?: 0f
            var currentX = contentArea.x
            for (cm in row) {
                val childY = currentY + (rowHeight - cm.height) / 2f
                calculateElementBounds(cm.child, Rect(currentX, childY, cm.width, cm.height), results)
                currentX += cm.width
            }
            currentY += rowHeight
        }
    }

    /**
     * Calculate bounds for stacking layout (Top, Left, Right, Bottom).
     * Children are positioned sequentially in the specified direction.
     *
     * @param element The container element
     * @param parentBounds The parent's bounds
     * @param direction Stack direction
     * @param siblings Sibling elements (for calculating this element's position in the stack)
     * @return Calculated bounds
     */
    private fun calculateStackLayout(
        element: UIElement,
        parentBounds: Rect,
        direction: Direction,
        siblings: List<UIElement>
    ): Rect {
        // For stack layout, the element itself still uses anchor for positioning
        // The LayoutMode affects how its CHILDREN are positioned
        val anchor = element.getProperty("Anchor") as? PropertyValue.Anchor
        return if (anchor != null) {
            AnchorCalculator.calculateBounds(anchor.anchor, parentBounds)
        } else {
            // No anchor - default to fill parent
            parentBounds
        }
    }

    /**
     * Calculate bounds for centered layout (Middle / Center).
     * Element is centered within parent bounds.
     *
     * @param element The element to center
     * @param parentBounds The parent's bounds
     * @return Calculated bounds
     */
    private fun calculateCenterLayout(
        element: UIElement,
        parentBounds: Rect
    ): Rect {
        // Get element's size from Anchor (Width/Height)
        val anchor = element.getProperty("Anchor") as? PropertyValue.Anchor
        if (anchor == null) {
            // No anchor - default to fill parent
            return parentBounds
        }

        // Calculate width and height
        val width = anchor.anchor.width?.let {
            when (it) {
                is AnchorDimension.Absolute -> it.pixels
                is AnchorDimension.Relative -> it.ratio * parentBounds.width
            }
        } ?: parentBounds.width

        val height = anchor.anchor.height?.let {
            when (it) {
                is AnchorDimension.Absolute -> it.pixels
                is AnchorDimension.Relative -> it.ratio * parentBounds.height
            }
        } ?: parentBounds.height

        // Center within parent
        val x = parentBounds.x + (parentBounds.width - width) / 2f
        val y = parentBounds.y + (parentBounds.height - height) / 2f

        return Rect(x, y, width, height)
    }

    /**
     * Layout direction for stack layouts
     */
    enum class Direction {
        VERTICAL,   // Top-to-bottom (LayoutMode: Top) or bottom-to-top (Bottom)
        HORIZONTAL  // Left-to-right (LayoutMode: Left) or right-to-left (Right)
    }

    companion object {
        /** Set of layout mode values that manage child positioning */
        val MANAGED_MODES = MANAGED_LAYOUT_MODES

        /** Set of stack layout modes where children flow in a direction */
        val STACK_MODES = STACK_LAYOUT_MODES
    }
}
