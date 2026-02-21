// Copyright 2026 Hyve. All rights reserved.
package com.hyve.ui.rendering.layout

import com.hyve.ui.core.domain.anchor.AnchorDimension
import com.hyve.ui.core.domain.anchor.AnchorValue
import com.hyve.ui.core.domain.elements.UIElement
import com.hyve.ui.core.domain.properties.PropertyMap
import com.hyve.ui.core.domain.properties.PropertyValue
import com.hyve.ui.core.id.ElementType
import com.hyve.ui.schema.SchemaRegistry
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.data.Offset
import org.junit.Test

class LayoutEngineTest {
    private val engine = LayoutEngine(SchemaRegistry.default())
    private val parentBounds = Rect(0f, 0f, 400f, 300f)
    private val delta = Offset.offset(0.5f)

    private fun group(
        layoutMode: String? = null,
        anchor: AnchorValue? = null,
        padding: PropertyValue.Tuple? = null,
        spacing: Float? = null,
        children: List<UIElement> = emptyList()
    ): UIElement {
        val props = mutableListOf<Pair<String, PropertyValue>>()
        if (layoutMode != null) props.add("LayoutMode" to PropertyValue.Text(layoutMode, quoted = false))
        if (anchor != null) props.add("Anchor" to PropertyValue.Anchor(anchor))
        if (padding != null) props.add("Padding" to padding)
        if (spacing != null) props.add("Spacing" to PropertyValue.Number(spacing.toDouble()))
        return UIElement(
            type = ElementType("Group"),
            id = null,
            properties = PropertyMap.of(*props.toTypedArray()),
            children = children
        )
    }

    private fun child(
        width: Float? = null,
        height: Float? = null,
        flexWeight: Float? = null
    ): UIElement {
        val props = mutableListOf<Pair<String, PropertyValue>>()
        val anchorValue = AnchorValue(
            left = null, top = null, right = null, bottom = null,
            width = width?.let { AnchorDimension.Absolute(it) },
            height = height?.let { AnchorDimension.Absolute(it) }
        )
        if (width != null || height != null) props.add("Anchor" to PropertyValue.Anchor(anchorValue))
        if (flexWeight != null) props.add("FlexWeight" to PropertyValue.Number(flexWeight.toDouble()))
        return UIElement(
            type = ElementType("Label"),
            id = null,
            properties = PropertyMap.of(*props.toTypedArray())
        )
    }

    private fun paddingTuple(vararg entries: Pair<String, Double>): PropertyValue.Tuple {
        return PropertyValue.Tuple(entries.associate { (k, v) -> k to PropertyValue.Number(v) })
    }

    // --- Padding (GAP-L01) ---

    @Test
    fun `padding Full shrinks child content area`() {
        val parent = group(
            layoutMode = "Top",
            padding = paddingTuple("Full" to 20.0),
            children = listOf(child(height = 50f))
        )

        val layout = engine.calculateLayout(parent, parentBounds)
        val childBounds = layout[parent.children[0]]!!

        // Content area should be inset by 20 on each side: x=20, width=360
        assertThat(childBounds.x).isCloseTo(20f, delta)
        assertThat(childBounds.width).isCloseTo(360f, delta)
    }

    @Test
    fun `padding Horizontal and Vertical shrinks content area`() {
        val parent = group(
            layoutMode = "Top",
            padding = paddingTuple("Horizontal" to 10.0, "Vertical" to 5.0),
            children = listOf(child(height = 50f))
        )

        val layout = engine.calculateLayout(parent, parentBounds)
        val childBounds = layout[parent.children[0]]!!

        assertThat(childBounds.x).isCloseTo(10f, delta)
        assertThat(childBounds.y).isCloseTo(5f, delta)
        assertThat(childBounds.width).isCloseTo(380f, delta)
    }

    @Test
    fun `padding with individual sides`() {
        val parent = group(
            layoutMode = "Top",
            padding = paddingTuple("Left" to 20.0, "Right" to 30.0, "Top" to 10.0, "Bottom" to 15.0),
            children = listOf(child(height = 50f))
        )

        val layout = engine.calculateLayout(parent, parentBounds)
        val childBounds = layout[parent.children[0]]!!

        assertThat(childBounds.x).isCloseTo(20f, delta)
        assertThat(childBounds.y).isCloseTo(10f, delta)
        assertThat(childBounds.width).isCloseTo(350f, delta) // 400 - 20 - 30
    }

    @Test
    fun `padding applies to non-layout containers too`() {
        val innerChild = child(width = 100f, height = 50f)
        val parent = group(
            padding = paddingTuple("Full" to 15.0),
            anchor = AnchorValue(
                left = AnchorDimension.Absolute(0f), top = AnchorDimension.Absolute(0f),
                width = AnchorDimension.Absolute(200f), height = AnchorDimension.Absolute(100f),
                right = null, bottom = null
            ),
            children = listOf(innerChild)
        )

        val layout = engine.calculateLayout(parent, parentBounds)
        val childBounds = layout[innerChild]!!

        // Child should be positioned within the padded content area
        // Parent: 0,0 200x100. Content area: 15,15 170x70.
        // Child anchor: width=100, height=50, no position → centered in content area
        assertThat(childBounds.x).isCloseTo(50f, delta) // 15 + (170-100)/2 = 50
        assertThat(childBounds.y).isCloseTo(25f, delta) // 15 + (70-50)/2 = 25
    }

    // --- FlexWeight (GAP-L02) ---

    @Test
    fun `flexWeight distributes remaining space in Top layout`() {
        val c1 = child(height = 100f)       // fixed 100px
        val c2 = child(flexWeight = 1f)     // gets rest
        val parent = group(layoutMode = "Top", children = listOf(c1, c2))

        val layout = engine.calculateLayout(parent, parentBounds) // parent 300px tall

        assertThat(layout[c1]!!.height).isCloseTo(100f, delta)
        assertThat(layout[c2]!!.height).isCloseTo(200f, delta) // 300 - 100
        assertThat(layout[c2]!!.y).isCloseTo(100f, delta)
    }

    @Test
    fun `flexWeight distributes proportionally among multiple flex children`() {
        val c1 = child(flexWeight = 1f)
        val c2 = child(flexWeight = 2f)
        val c3 = child(height = 60f) // fixed
        val parent = group(layoutMode = "Top", children = listOf(c1, c2, c3))

        val layout = engine.calculateLayout(parent, parentBounds)
        // remaining = 300 - 60 = 240. c1 gets 240/3=80, c2 gets 240*2/3=160

        assertThat(layout[c1]!!.height).isCloseTo(80f, delta)
        assertThat(layout[c2]!!.height).isCloseTo(160f, delta)
        assertThat(layout[c3]!!.height).isCloseTo(60f, delta)
    }

    @Test
    fun `flexWeight works in Left layout`() {
        val c1 = child(width = 100f)
        val c2 = child(flexWeight = 1f)
        val parent = group(layoutMode = "Left", children = listOf(c1, c2))

        val layout = engine.calculateLayout(parent, parentBounds) // parent 400px wide

        assertThat(layout[c1]!!.width).isCloseTo(100f, delta)
        assertThat(layout[c2]!!.width).isCloseTo(300f, delta)
        assertThat(layout[c2]!!.x).isCloseTo(100f, delta)
    }

    // --- LayoutMode: Right (GAP-L03) ---

    @Test
    fun `Right layout positions children right to left`() {
        val c1 = child(width = 80f)
        val c2 = child(width = 120f)
        val parent = group(layoutMode = "Right", children = listOf(c1, c2))

        val layout = engine.calculateLayout(parent, parentBounds)

        // c1 is first, placed at the right edge: x = 400 - 80 = 320
        assertThat(layout[c1]!!.x).isCloseTo(320f, delta)
        assertThat(layout[c1]!!.width).isCloseTo(80f, delta)
        // c2 is next, placed left of c1: x = 320 - 120 = 200
        assertThat(layout[c2]!!.x).isCloseTo(200f, delta)
        assertThat(layout[c2]!!.width).isCloseTo(120f, delta)
    }

    // --- LayoutMode: Bottom (GAP-L04) ---

    @Test
    fun `Bottom layout positions children bottom to top`() {
        val c1 = child(height = 50f)
        val c2 = child(height = 70f)
        val parent = group(layoutMode = "Bottom", children = listOf(c1, c2))

        val layout = engine.calculateLayout(parent, parentBounds)

        // c1 is first, placed at the bottom: y = 300 - 50 = 250
        assertThat(layout[c1]!!.y).isCloseTo(250f, delta)
        assertThat(layout[c1]!!.height).isCloseTo(50f, delta)
        // c2 is next, placed above c1: y = 250 - 70 = 180
        assertThat(layout[c2]!!.y).isCloseTo(180f, delta)
        assertThat(layout[c2]!!.height).isCloseTo(70f, delta)
    }

    // --- LayoutMode: Center (GAP-L05) ---

    @Test
    fun `Center layout centers children using anchor`() {
        val c = child(width = 200f, height = 100f)
        val parent = group(layoutMode = "Center", children = listOf(c))

        val layout = engine.calculateLayout(parent, parentBounds)

        // Centered: x = (400-200)/2=100, y = (300-100)/2=100
        assertThat(layout[c]!!.x).isCloseTo(100f, delta)
        assertThat(layout[c]!!.y).isCloseTo(100f, delta)
    }

    @Test
    fun `Middle layout still works as before`() {
        val c = child(width = 200f, height = 100f)
        val parent = group(layoutMode = "Middle", children = listOf(c))

        val layout = engine.calculateLayout(parent, parentBounds)

        assertThat(layout[c]!!.x).isCloseTo(100f, delta)
        assertThat(layout[c]!!.y).isCloseTo(100f, delta)
    }

    // --- Combined: Padding + FlexWeight ---

    @Test
    fun `padding and flexWeight work together in Left layout`() {
        val c1 = child(width = 50f)
        val c2 = child(flexWeight = 1f)
        val parent = group(
            layoutMode = "Left",
            padding = paddingTuple("Full" to 10.0),
            children = listOf(c1, c2)
        )

        val layout = engine.calculateLayout(parent, parentBounds)
        // Content area: x=10, width=380. c1 gets 50, c2 gets 330.

        assertThat(layout[c1]!!.x).isCloseTo(10f, delta)
        assertThat(layout[c1]!!.width).isCloseTo(50f, delta)
        assertThat(layout[c2]!!.x).isCloseTo(60f, delta)
        assertThat(layout[c2]!!.width).isCloseTo(330f, delta)
    }

    // --- TopScrolling / BottomScrolling / LeftScrolling (GAP-L07) ---

    @Test
    fun `TopScrolling positions children identically to Top`() {
        val c1 = child(height = 100f)
        val c2 = child(height = 80f)
        val parent = group(layoutMode = "TopScrolling", children = listOf(c1, c2))

        val layout = engine.calculateLayout(parent, parentBounds)

        assertThat(layout[c1]!!.y).isCloseTo(0f, delta)
        assertThat(layout[c1]!!.height).isCloseTo(100f, delta)
        assertThat(layout[c2]!!.y).isCloseTo(100f, delta)
        assertThat(layout[c2]!!.height).isCloseTo(80f, delta)
    }

    @Test
    fun `BottomScrolling positions children identically to Bottom`() {
        val c1 = child(height = 100f)
        val c2 = child(height = 80f)
        val parent = group(layoutMode = "BottomScrolling", children = listOf(c1, c2))

        val layout = engine.calculateLayout(parent, parentBounds)

        assertThat(layout[c1]!!.y).isCloseTo(200f, delta) // 300 - 100
        assertThat(layout[c2]!!.y).isCloseTo(120f, delta) // 200 - 80
    }

    @Test
    fun `LeftScrolling positions children identically to Left`() {
        val c1 = child(width = 100f)
        val c2 = child(width = 80f)
        val parent = group(layoutMode = "LeftScrolling", children = listOf(c1, c2))

        val layout = engine.calculateLayout(parent, parentBounds)

        assertThat(layout[c1]!!.x).isCloseTo(0f, delta)
        assertThat(layout[c1]!!.width).isCloseTo(100f, delta)
        assertThat(layout[c2]!!.x).isCloseTo(100f, delta)
        assertThat(layout[c2]!!.width).isCloseTo(80f, delta)
    }

    // --- Full layout mode (GAP-L06) ---

    @Test
    fun `Full layout mode positions children via anchor`() {
        val c1 = UIElement(
            type = ElementType("Label"), id = null,
            properties = PropertyMap.of(
                "Anchor" to PropertyValue.Anchor(AnchorValue(
                    left = AnchorDimension.Absolute(10f), top = AnchorDimension.Absolute(20f),
                    width = AnchorDimension.Absolute(100f), height = AnchorDimension.Absolute(50f),
                    right = null, bottom = null
                ))
            )
        )
        val c2 = UIElement(
            type = ElementType("Label"), id = null,
            properties = PropertyMap.of(
                "Anchor" to PropertyValue.Anchor(AnchorValue(
                    left = AnchorDimension.Absolute(150f), top = AnchorDimension.Absolute(100f),
                    width = AnchorDimension.Absolute(80f), height = AnchorDimension.Absolute(60f),
                    right = null, bottom = null
                ))
            )
        )
        val parent = group(layoutMode = "Full", children = listOf(c1, c2))

        val layout = engine.calculateLayout(parent, parentBounds)

        assertThat(layout[c1]!!.x).isCloseTo(10f, delta)
        assertThat(layout[c1]!!.y).isCloseTo(20f, delta)
        assertThat(layout[c2]!!.x).isCloseTo(150f, delta)
        assertThat(layout[c2]!!.y).isCloseTo(100f, delta)
    }

    // --- CenterMiddle alias (GAP-L08) ---

    @Test
    fun `CenterMiddle centers children same as Center`() {
        val c = child(width = 100f, height = 50f)
        val parent = group(layoutMode = "CenterMiddle", children = listOf(c))

        val layout = engine.calculateLayout(parent, parentBounds)

        assertThat(layout[c]!!.x).isCloseTo(150f, delta) // (400-100)/2
        assertThat(layout[c]!!.y).isCloseTo(125f, delta) // (300-50)/2
    }

    // --- LeftCenterWrap (GAP-L09) ---

    @Test
    fun `LeftCenterWrap wraps children to next row`() {
        val c1 = child(width = 200f, height = 40f)
        val c2 = child(width = 250f, height = 60f) // c1+c2 = 450 > 400, so c2 wraps
        val c3 = child(width = 100f, height = 30f)
        val parent = group(layoutMode = "LeftCenterWrap", children = listOf(c1, c2, c3))

        val layout = engine.calculateLayout(parent, parentBounds)

        // Row 1: c1 (200x40) only, vertically centered in row height 40
        assertThat(layout[c1]!!.x).isCloseTo(0f, delta)
        assertThat(layout[c1]!!.y).isCloseTo(0f, delta)
        // Row 2: c2 (250x60), c3 (100x30) -> row height 60
        assertThat(layout[c2]!!.x).isCloseTo(0f, delta)
        assertThat(layout[c2]!!.y).isCloseTo(40f, delta) // after row 1
        assertThat(layout[c3]!!.x).isCloseTo(250f, delta)
        assertThat(layout[c3]!!.y).isCloseTo(55f, delta) // 40 + (60-30)/2 = 55, centered in row
    }

    // --- Helper: child with MinWidth/MaxWidth constraints ---

    private fun childWithConstraints(
        width: Float? = null,
        height: Float? = null,
        minWidth: Float? = null,
        maxWidth: Float? = null,
        flexWeight: Float? = null
    ): UIElement {
        val props = mutableListOf<Pair<String, PropertyValue>>()
        val anchorValue = AnchorValue(
            left = null, top = null, right = null, bottom = null,
            width = width?.let { AnchorDimension.Absolute(it) },
            height = height?.let { AnchorDimension.Absolute(it) }
        )
        if (width != null || height != null) props.add("Anchor" to PropertyValue.Anchor(anchorValue))
        if (minWidth != null) props.add("MinWidth" to PropertyValue.Number(minWidth.toDouble()))
        if (maxWidth != null) props.add("MaxWidth" to PropertyValue.Number(maxWidth.toDouble()))
        if (flexWeight != null) props.add("FlexWeight" to PropertyValue.Number(flexWeight.toDouble()))
        return UIElement(
            type = ElementType("Label"),
            id = null,
            properties = PropertyMap.of(*props.toTypedArray())
        )
    }

    // --- MinWidth / MaxWidth constraints ---

    @Test
    fun `MinWidth prevents undersized child in Top layout`() {
        val c = childWithConstraints(width = 50f, height = 40f, minWidth = 100f)
        val parent = group(layoutMode = "Top", children = listOf(c))
        val layout = engine.calculateLayout(parent, parentBounds)
        // Width should be clamped up to MinWidth=100
        assertThat(layout[c]!!.width).isCloseTo(100f, delta)
    }

    @Test
    fun `MaxWidth caps oversized child in Top layout`() {
        val c = childWithConstraints(width = 300f, height = 40f, maxWidth = 150f)
        val parent = group(layoutMode = "Top", children = listOf(c))
        val layout = engine.calculateLayout(parent, parentBounds)
        assertThat(layout[c]!!.width).isCloseTo(150f, delta)
    }

    @Test
    fun `MinWidth + MaxWidth together clamp width`() {
        // Width 50 < MinWidth 80, so clamps to 80. MaxWidth 200 has no effect.
        val c = childWithConstraints(width = 50f, height = 40f, minWidth = 80f, maxWidth = 200f)
        val parent = group(layoutMode = "Top", children = listOf(c))
        val layout = engine.calculateLayout(parent, parentBounds)
        assertThat(layout[c]!!.width).isCloseTo(80f, delta)
    }

    @Test
    fun `MinWidth has no effect when child already wider`() {
        val c = childWithConstraints(width = 200f, height = 40f, minWidth = 100f)
        val parent = group(layoutMode = "Top", children = listOf(c))
        val layout = engine.calculateLayout(parent, parentBounds)
        assertThat(layout[c]!!.width).isCloseTo(200f, delta)
    }

    @Test
    fun `MaxWidth has no effect when child already narrower`() {
        val c = childWithConstraints(width = 100f, height = 40f, maxWidth = 200f)
        val parent = group(layoutMode = "Top", children = listOf(c))
        val layout = engine.calculateLayout(parent, parentBounds)
        assertThat(layout[c]!!.width).isCloseTo(100f, delta)
    }

    @Test
    fun `MinWidth works in Left layout (horizontal)`() {
        val c = childWithConstraints(width = 30f, height = 40f, minWidth = 80f)
        val parent = group(layoutMode = "Left", children = listOf(c))
        val layout = engine.calculateLayout(parent, parentBounds)
        assertThat(layout[c]!!.width).isCloseTo(80f, delta)
    }

    @Test
    fun `MinWidth works on anchor-based non-layout children`() {
        val c = childWithConstraints(width = 50f, height = 40f, minWidth = 120f)
        // No layoutMode, so child uses anchor-based positioning
        val parent = group(children = listOf(c))
        val layout = engine.calculateLayout(parent, parentBounds)
        assertThat(layout[c]!!.width).isCloseTo(120f, delta)
    }

    // --- Spacing ---

    @Test
    fun `Spacing adds gaps in Top layout`() {
        val c1 = child(height = 50f)
        val c2 = child(height = 50f)
        val c3 = child(height = 50f)
        val parent = group(layoutMode = "Top", spacing = 10f, children = listOf(c1, c2, c3))
        val layout = engine.calculateLayout(parent, parentBounds)

        assertThat(layout[c1]!!.y).isCloseTo(0f, delta)
        assertThat(layout[c2]!!.y).isCloseTo(60f, delta) // 50 + 10
        assertThat(layout[c3]!!.y).isCloseTo(120f, delta) // 60 + 50 + 10
    }

    @Test
    fun `Spacing adds gaps in Left layout`() {
        val c1 = child(width = 80f)
        val c2 = child(width = 100f)
        val parent = group(layoutMode = "Left", spacing = 15f, children = listOf(c1, c2))
        val layout = engine.calculateLayout(parent, parentBounds)

        assertThat(layout[c1]!!.x).isCloseTo(0f, delta)
        assertThat(layout[c2]!!.x).isCloseTo(95f, delta) // 80 + 15
    }

    @Test
    fun `Spacing adds gaps in Bottom layout (reversed)`() {
        val c1 = child(height = 50f)
        val c2 = child(height = 50f)
        val parent = group(layoutMode = "Bottom", spacing = 10f, children = listOf(c1, c2))
        val layout = engine.calculateLayout(parent, parentBounds)

        // Bottom layout: c1 at bottom edge, c2 above c1 with spacing
        assertThat(layout[c1]!!.y).isCloseTo(250f, delta) // 300 - 50
        assertThat(layout[c2]!!.y).isCloseTo(190f, delta) // 250 - 10 - 50
    }

    @Test
    fun `Spacing adds gaps in Right layout (reversed)`() {
        val c1 = child(width = 80f)
        val c2 = child(width = 100f)
        val parent = group(layoutMode = "Right", spacing = 10f, children = listOf(c1, c2))
        val layout = engine.calculateLayout(parent, parentBounds)

        // Right layout: c1 at right edge, c2 left of c1 with spacing
        assertThat(layout[c1]!!.x).isCloseTo(320f, delta) // 400 - 80
        assertThat(layout[c2]!!.x).isCloseTo(210f, delta) // 320 - 10 - 100
    }

    @Test
    fun `Spacing zero is same as no spacing`() {
        val c1 = child(height = 50f)
        val c2 = child(height = 50f)
        val withZero = group(layoutMode = "Top", spacing = 0f, children = listOf(c1, c2))
        val layout = engine.calculateLayout(withZero, parentBounds)

        assertThat(layout[c1]!!.y).isCloseTo(0f, delta)
        assertThat(layout[c2]!!.y).isCloseTo(50f, delta)
    }

    @Test
    fun `single child has no spacing gap`() {
        val c = child(height = 50f)
        val parent = group(layoutMode = "Top", spacing = 20f, children = listOf(c))
        val layout = engine.calculateLayout(parent, parentBounds)

        assertThat(layout[c]!!.y).isCloseTo(0f, delta)
        assertThat(layout[c]!!.height).isCloseTo(50f, delta)
    }

    @Test
    fun `Spacing + padding combined`() {
        val c1 = child(height = 40f)
        val c2 = child(height = 40f)
        val parent = group(
            layoutMode = "Top",
            padding = paddingTuple("Full" to 10.0),
            spacing = 5f,
            children = listOf(c1, c2)
        )
        val layout = engine.calculateLayout(parent, parentBounds)

        // Content area starts at y=10 after padding
        assertThat(layout[c1]!!.y).isCloseTo(10f, delta)
        assertThat(layout[c2]!!.y).isCloseTo(55f, delta) // 10 + 40 + 5
    }

    @Test
    fun `Spacing + FlexWeight reduces available space`() {
        val c1 = child(height = 60f)
        val c2 = child(flexWeight = 1f)
        val parent = group(layoutMode = "Top", spacing = 20f, children = listOf(c1, c2))
        val layout = engine.calculateLayout(parent, parentBounds)

        // Total space 300. Fixed: 60. Spacing: 20 (1 gap). Remaining: 300 - 60 - 20 = 220
        assertThat(layout[c1]!!.height).isCloseTo(60f, delta)
        assertThat(layout[c2]!!.height).isCloseTo(220f, delta)
        assertThat(layout[c2]!!.y).isCloseTo(80f, delta) // 60 + 20
    }

    @Test
    fun `Spacing in TopScrolling works like Top`() {
        val c1 = child(height = 50f)
        val c2 = child(height = 50f)
        val parent = group(layoutMode = "TopScrolling", spacing = 10f, children = listOf(c1, c2))
        val layout = engine.calculateLayout(parent, parentBounds)

        assertThat(layout[c1]!!.y).isCloseTo(0f, delta)
        assertThat(layout[c2]!!.y).isCloseTo(60f, delta) // 50 + 10
    }

    @Test
    fun `Spacing in LeftScrolling works like Left`() {
        val c1 = child(width = 80f)
        val c2 = child(width = 100f)
        val parent = group(layoutMode = "LeftScrolling", spacing = 10f, children = listOf(c1, c2))
        val layout = engine.calculateLayout(parent, parentBounds)

        assertThat(layout[c1]!!.x).isCloseTo(0f, delta)
        assertThat(layout[c2]!!.x).isCloseTo(90f, delta) // 80 + 10
    }
}
