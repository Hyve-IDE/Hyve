// Copyright 2026 Hyve. All rights reserved.
package com.hyve.ui.core.domain.anchor

import com.hyve.ui.rendering.layout.Rect
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

class AnchorConstraintsTest {

    // --- Helpers ---

    private fun pixels(dim: AnchorDimension?): Float? = (dim as? AnchorDimension.Absolute)?.pixels

    private val parentBounds = Rect(0f, 0f, 1920f, 1080f)

    // =====================================================================
    // FR-4: Horizontal constraint mode detection
    // =====================================================================

    @Test
    fun `should detect LEFT_WIDTH when Left and Width are present`() {
        val anchor = AnchorValue.absolute(left = 10f, width = 100f)
        assertThat(AnchorConstraints.detectHorizontalMode(anchor)).isEqualTo(HorizontalConstraint.LEFT_WIDTH)
    }

    @Test
    fun `should detect RIGHT_WIDTH when Right and Width are present`() {
        val anchor = AnchorValue.absolute(right = 20f, width = 100f)
        assertThat(AnchorConstraints.detectHorizontalMode(anchor)).isEqualTo(HorizontalConstraint.RIGHT_WIDTH)
    }

    @Test
    fun `should detect LEFT_RIGHT when Left and Right are present`() {
        val anchor = AnchorValue.absolute(left = 10f, right = 20f)
        assertThat(AnchorConstraints.detectHorizontalMode(anchor)).isEqualTo(HorizontalConstraint.LEFT_RIGHT)
    }

    @Test
    fun `should detect CENTER when only Width is present`() {
        val anchor = AnchorValue.absolute(width = 100f)
        assertThat(AnchorConstraints.detectHorizontalMode(anchor)).isEqualTo(HorizontalConstraint.CENTER)
    }

    @Test
    fun `should detect FREE when no horizontal anchors are present`() {
        val anchor = AnchorValue.absolute(top = 10f, height = 50f)
        assertThat(AnchorConstraints.detectHorizontalMode(anchor)).isEqualTo(HorizontalConstraint.FREE)
    }

    @Test
    fun `should treat Left+Right+Width as LEFT_RIGHT (stretch takes precedence)`() {
        val anchor = AnchorValue.absolute(left = 10f, right = 20f, width = 100f)
        assertThat(AnchorConstraints.detectHorizontalMode(anchor)).isEqualTo(HorizontalConstraint.LEFT_RIGHT)
    }

    @Test
    fun `should detect LEFT_WIDTH when only Left is present`() {
        val anchor = AnchorValue.absolute(left = 50f)
        assertThat(AnchorConstraints.detectHorizontalMode(anchor)).isEqualTo(HorizontalConstraint.LEFT_WIDTH)
    }

    @Test
    fun `should detect RIGHT_WIDTH when only Right is present`() {
        val anchor = AnchorValue.absolute(right = 50f)
        assertThat(AnchorConstraints.detectHorizontalMode(anchor)).isEqualTo(HorizontalConstraint.RIGHT_WIDTH)
    }

    // =====================================================================
    // FR-4: Vertical constraint mode detection
    // =====================================================================

    @Test
    fun `should detect TOP_HEIGHT when Top and Height are present`() {
        val anchor = AnchorValue.absolute(top = 100f, height = 50f)
        assertThat(AnchorConstraints.detectVerticalMode(anchor)).isEqualTo(VerticalConstraint.TOP_HEIGHT)
    }

    @Test
    fun `should detect BOTTOM_HEIGHT when Bottom and Height are present`() {
        val anchor = AnchorValue.absolute(bottom = 100f, height = 50f)
        assertThat(AnchorConstraints.detectVerticalMode(anchor)).isEqualTo(VerticalConstraint.BOTTOM_HEIGHT)
    }

    @Test
    fun `should detect TOP_BOTTOM when Top and Bottom are present`() {
        val anchor = AnchorValue.absolute(top = 10f, bottom = 20f)
        assertThat(AnchorConstraints.detectVerticalMode(anchor)).isEqualTo(VerticalConstraint.TOP_BOTTOM)
    }

    @Test
    fun `should detect MIDDLE when only Height is present`() {
        val anchor = AnchorValue.absolute(height = 50f)
        assertThat(AnchorConstraints.detectVerticalMode(anchor)).isEqualTo(VerticalConstraint.MIDDLE)
    }

    @Test
    fun `should detect vertical FREE when no vertical anchors are present`() {
        val anchor = AnchorValue.absolute(left = 10f, width = 100f)
        assertThat(AnchorConstraints.detectVerticalMode(anchor)).isEqualTo(VerticalConstraint.FREE)
    }

    @Test
    fun `should treat Top+Bottom+Height as TOP_BOTTOM (stretch takes precedence)`() {
        val anchor = AnchorValue.absolute(top = 10f, bottom = 20f, height = 50f)
        assertThat(AnchorConstraints.detectVerticalMode(anchor)).isEqualTo(VerticalConstraint.TOP_BOTTOM)
    }

    // =====================================================================
    // FR-2: Apply horizontal constraint mode
    // =====================================================================

    @Test
    fun `should switch from LEFT_WIDTH to RIGHT_WIDTH preserving position`() {
        // Element at left=50, width=200 in 1920-wide parent
        val anchor = AnchorValue.absolute(left = 50f, top = 10f, width = 200f, height = 40f)
        val elementBounds = Rect(50f, 10f, 200f, 40f)

        val result = AnchorConstraints.applyHorizontalMode(anchor, HorizontalConstraint.RIGHT_WIDTH, elementBounds, parentBounds)

        assertThat(pixels(result.right)).isEqualTo(1670f) // 1920 - 50 - 200
        assertThat(pixels(result.width)).isEqualTo(200f)
        assertThat(result.left).isNull()
        // Vertical fields preserved
        assertThat(pixels(result.top)).isEqualTo(10f)
        assertThat(pixels(result.height)).isEqualTo(40f)
    }

    @Test
    fun `should switch from LEFT_WIDTH to LEFT_RIGHT preserving position`() {
        val anchor = AnchorValue.absolute(left = 50f, top = 10f, width = 200f, height = 40f)
        val elementBounds = Rect(50f, 10f, 200f, 40f)

        val result = AnchorConstraints.applyHorizontalMode(anchor, HorizontalConstraint.LEFT_RIGHT, elementBounds, parentBounds)

        assertThat(pixels(result.left)).isEqualTo(50f)
        assertThat(pixels(result.right)).isEqualTo(1670f) // 1920 - 50 - 200
        assertThat(result.width).isNull()
    }

    @Test
    fun `should switch to CENTER keeping only Width`() {
        val anchor = AnchorValue.absolute(left = 50f, top = 10f, width = 200f, height = 40f)
        val elementBounds = Rect(50f, 10f, 200f, 40f)

        val result = AnchorConstraints.applyHorizontalMode(anchor, HorizontalConstraint.CENTER, elementBounds, parentBounds)

        assertThat(result.left).isNull()
        assertThat(result.right).isNull()
        assertThat(pixels(result.width)).isEqualTo(200f)
    }

    @Test
    fun `should switch to FREE removing all horizontal anchors`() {
        val anchor = AnchorValue.absolute(left = 50f, top = 10f, width = 200f, height = 40f)
        val elementBounds = Rect(50f, 10f, 200f, 40f)

        val result = AnchorConstraints.applyHorizontalMode(anchor, HorizontalConstraint.FREE, elementBounds, parentBounds)

        assertThat(result.left).isNull()
        assertThat(result.right).isNull()
        assertThat(result.width).isNull()
        // Vertical fields preserved
        assertThat(pixels(result.top)).isEqualTo(10f)
        assertThat(pixels(result.height)).isEqualTo(40f)
    }

    @Test
    fun `should clamp negative Right to zero`() {
        // Element extends past parent right edge
        val anchor = AnchorValue.absolute(left = 1800f, width = 200f)
        val elementBounds = Rect(1800f, 0f, 200f, 100f)

        val result = AnchorConstraints.applyHorizontalMode(anchor, HorizontalConstraint.RIGHT_WIDTH, elementBounds, parentBounds)

        assertThat(pixels(result.right)).isEqualTo(0f) // Clamped from -80
        assertThat(pixels(result.width)).isEqualTo(200f)
    }

    // =====================================================================
    // FR-3: Apply vertical constraint mode
    // =====================================================================

    @Test
    fun `should switch from TOP_HEIGHT to BOTTOM_HEIGHT preserving position`() {
        // Element at top=100, height=50 in 1080-high parent
        val anchor = AnchorValue.absolute(left = 10f, top = 100f, width = 200f, height = 50f)
        val elementBounds = Rect(10f, 100f, 200f, 50f)

        val result = AnchorConstraints.applyVerticalMode(anchor, VerticalConstraint.BOTTOM_HEIGHT, elementBounds, parentBounds)

        assertThat(pixels(result.bottom)).isEqualTo(930f) // 1080 - 100 - 50
        assertThat(pixels(result.height)).isEqualTo(50f)
        assertThat(result.top).isNull()
        // Horizontal fields preserved
        assertThat(pixels(result.left)).isEqualTo(10f)
        assertThat(pixels(result.width)).isEqualTo(200f)
    }

    @Test
    fun `should switch from TOP_HEIGHT to TOP_BOTTOM preserving position`() {
        val anchor = AnchorValue.absolute(left = 10f, top = 100f, width = 200f, height = 50f)
        val elementBounds = Rect(10f, 100f, 200f, 50f)

        val result = AnchorConstraints.applyVerticalMode(anchor, VerticalConstraint.TOP_BOTTOM, elementBounds, parentBounds)

        assertThat(pixels(result.top)).isEqualTo(100f)
        assertThat(pixels(result.bottom)).isEqualTo(930f)
        assertThat(result.height).isNull()
    }

    @Test
    fun `should switch to MIDDLE keeping only Height`() {
        val anchor = AnchorValue.absolute(left = 10f, top = 100f, width = 200f, height = 50f)
        val elementBounds = Rect(10f, 100f, 200f, 50f)

        val result = AnchorConstraints.applyVerticalMode(anchor, VerticalConstraint.MIDDLE, elementBounds, parentBounds)

        assertThat(result.top).isNull()
        assertThat(result.bottom).isNull()
        assertThat(pixels(result.height)).isEqualTo(50f)
    }

    @Test
    fun `should switch to vertical FREE removing all vertical anchors`() {
        val anchor = AnchorValue.absolute(left = 10f, top = 100f, width = 200f, height = 50f)
        val elementBounds = Rect(10f, 100f, 200f, 50f)

        val result = AnchorConstraints.applyVerticalMode(anchor, VerticalConstraint.FREE, elementBounds, parentBounds)

        assertThat(result.top).isNull()
        assertThat(result.bottom).isNull()
        assertThat(result.height).isNull()
        // Horizontal fields preserved
        assertThat(pixels(result.left)).isEqualTo(10f)
        assertThat(pixels(result.width)).isEqualTo(200f)
    }

    // =====================================================================
    // FR-2/FR-3: Round-trip preservation
    // =====================================================================

    @Test
    fun `should preserve position when cycling through all horizontal modes`() {
        val original = AnchorValue.absolute(left = 50f, top = 10f, width = 200f, height = 40f)
        val elementBounds = Rect(50f, 10f, 200f, 40f)

        // Left+Width → Right+Width → Left+Right → Center → Left+Width
        val step1 = AnchorConstraints.applyHorizontalMode(original, HorizontalConstraint.RIGHT_WIDTH, elementBounds, parentBounds)
        val step2 = AnchorConstraints.applyHorizontalMode(step1, HorizontalConstraint.LEFT_RIGHT, elementBounds, parentBounds)
        val step3 = AnchorConstraints.applyHorizontalMode(step2, HorizontalConstraint.CENTER, elementBounds, parentBounds)
        val step4 = AnchorConstraints.applyHorizontalMode(step3, HorizontalConstraint.LEFT_WIDTH, elementBounds, parentBounds)

        assertThat(pixels(step4.left)).isEqualTo(50f)
        assertThat(pixels(step4.width)).isEqualTo(200f)
        assertThat(step4.right).isNull()
    }

    // =====================================================================
    // FR-6: Toggle edge anchors
    // =====================================================================

    @Test
    fun `should add right edge to Left+Width element switching to Left+Right`() {
        val anchor = AnchorValue.absolute(left = 50f, width = 200f, top = 10f, height = 40f)
        val elementBounds = Rect(50f, 10f, 200f, 40f)

        val result = AnchorConstraints.toggleEdge(anchor, "right", elementBounds, parentBounds)

        assertThat(pixels(result.left)).isEqualTo(50f)
        assertThat(pixels(result.right)).isEqualTo(1670f)
        assertThat(result.width).isNull() // Width removed when switching to Left+Right
        assertThat(AnchorConstraints.detectHorizontalMode(result)).isEqualTo(HorizontalConstraint.LEFT_RIGHT)
    }

    @Test
    fun `should remove left edge from Left+Right switching to Right+Width`() {
        val anchor = AnchorValue.absolute(left = 50f, right = 1670f, top = 10f, height = 40f)
        val elementBounds = Rect(50f, 10f, 200f, 40f)

        val result = AnchorConstraints.toggleEdge(anchor, "left", elementBounds, parentBounds)

        assertThat(result.left).isNull()
        assertThat(pixels(result.right)).isEqualTo(1670f)
        assertThat(pixels(result.width)).isEqualTo(200f) // Width added to preserve size
        assertThat(AnchorConstraints.detectHorizontalMode(result)).isEqualTo(HorizontalConstraint.RIGHT_WIDTH)
    }

    @Test
    fun `should remove right edge from Left+Right switching to Left+Width`() {
        val anchor = AnchorValue.absolute(left = 50f, right = 1670f, top = 10f, height = 40f)
        val elementBounds = Rect(50f, 10f, 200f, 40f)

        val result = AnchorConstraints.toggleEdge(anchor, "right", elementBounds, parentBounds)

        assertThat(pixels(result.left)).isEqualTo(50f)
        assertThat(result.right).isNull()
        assertThat(pixels(result.width)).isEqualTo(200f) // Width added to preserve size
        assertThat(AnchorConstraints.detectHorizontalMode(result)).isEqualTo(HorizontalConstraint.LEFT_WIDTH)
    }

    @Test
    fun `should add bottom edge to Top+Height element switching to Top+Bottom`() {
        val anchor = AnchorValue.absolute(left = 10f, width = 200f, top = 100f, height = 50f)
        val elementBounds = Rect(10f, 100f, 200f, 50f)

        val result = AnchorConstraints.toggleEdge(anchor, "bottom", elementBounds, parentBounds)

        assertThat(pixels(result.top)).isEqualTo(100f)
        assertThat(pixels(result.bottom)).isEqualTo(930f)
        assertThat(result.height).isNull() // Height removed when switching to Top+Bottom
        assertThat(AnchorConstraints.detectVerticalMode(result)).isEqualTo(VerticalConstraint.TOP_BOTTOM)
    }

    @Test
    fun `should add left edge to Width-only (center) element`() {
        val anchor = AnchorValue.absolute(width = 200f, top = 10f, height = 40f)
        val elementBounds = Rect(860f, 10f, 200f, 40f) // Centered in 1920

        val result = AnchorConstraints.toggleEdge(anchor, "left", elementBounds, parentBounds)

        assertThat(pixels(result.left)).isEqualTo(860f)
        assertThat(pixels(result.width)).isEqualTo(200f)
        assertThat(AnchorConstraints.detectHorizontalMode(result)).isEqualTo(HorizontalConstraint.LEFT_WIDTH)
    }

    // =====================================================================
    // Active edges detection
    // =====================================================================

    @Test
    fun `should return active edges for Left+Width+Top+Height anchor`() {
        val anchor = AnchorValue.absolute(left = 10f, width = 100f, top = 20f, height = 50f)
        assertThat(AnchorConstraints.activeEdges(anchor)).containsExactlyInAnyOrder("left", "top")
    }

    @Test
    fun `should return all four edges for fill anchor`() {
        val anchor = AnchorValue.fill()
        assertThat(AnchorConstraints.activeEdges(anchor)).containsExactlyInAnyOrder("left", "top", "right", "bottom")
    }

    @Test
    fun `should return empty set for empty anchor`() {
        val anchor = AnchorValue()
        assertThat(AnchorConstraints.activeEdges(anchor)).isEmpty()
    }

    // =====================================================================
    // Detection with relative (percentage) anchors
    // =====================================================================

    @Test
    fun `should detect LEFT_WIDTH with relative anchors`() {
        val anchor = AnchorValue.relative(left = 0.1f, width = 0.5f)
        assertThat(AnchorConstraints.detectHorizontalMode(anchor)).isEqualTo(HorizontalConstraint.LEFT_WIDTH)
    }

    @Test
    fun `should detect LEFT_RIGHT with relative anchors`() {
        val anchor = AnchorValue.relative(left = 0.1f, right = 0.1f)
        assertThat(AnchorConstraints.detectHorizontalMode(anchor)).isEqualTo(HorizontalConstraint.LEFT_RIGHT)
    }

    // =====================================================================
    // Edge case: completely empty anchor
    // =====================================================================

    @Test
    fun `should detect FREE for both axes on empty anchor`() {
        val anchor = AnchorValue()
        assertThat(AnchorConstraints.detectHorizontalMode(anchor)).isEqualTo(HorizontalConstraint.FREE)
        assertThat(AnchorConstraints.detectVerticalMode(anchor)).isEqualTo(VerticalConstraint.FREE)
    }

    // =====================================================================
    // Edge case: non-origin parent bounds
    // =====================================================================

    @Test
    fun `should calculate correct values with non-origin parent bounds`() {
        val parentOffset = Rect(100f, 200f, 800f, 600f)
        val anchor = AnchorValue.absolute(left = 50f, width = 200f, top = 30f, height = 100f)
        // Element is at absolute position (150, 230) with size 200x100
        val elementBounds = Rect(150f, 230f, 200f, 100f)

        val result = AnchorConstraints.applyHorizontalMode(anchor, HorizontalConstraint.RIGHT_WIDTH, elementBounds, parentOffset)

        // Right = (100 + 800) - (150 + 200) = 550
        assertThat(pixels(result.right)).isEqualTo(550f)
        assertThat(pixels(result.width)).isEqualTo(200f)
        assertThat(result.left).isNull()
    }
}
