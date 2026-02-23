// Copyright 2026 Hyve. All rights reserved.
package com.hyve.ui.canvas

import androidx.compose.ui.geometry.Offset
import com.hyve.ui.rendering.layout.Rect
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.data.Offset as AssertJOffset
import org.junit.Test

/**
 * Tests for snap guide engine pure functions.
 */
class SnapGuideEngineTest {

    // --- calculateSnap: edge alignment ---

    @Test
    fun `left-to-left edge snap`() {
        val moving = Rect(102f, 200f, 100f, 50f) // left=102
        val target = Rect(100f, 50f, 80f, 40f)    // left=100
        val result = calculateSnap(moving, listOf(target))

        assertThat(result.snapDelta.x).isCloseTo(-2f, AssertJOffset.offset(0.001f))
        assertThat(result.guides).anyMatch { it.axis == SnapAxis.VERTICAL && it.position == 100f }
    }

    @Test
    fun `left-to-right edge snap`() {
        // Moving element's left edge (200) aligns with target's right edge (203)
        val moving = Rect(200f, 100f, 60f, 40f)   // left=200
        val target = Rect(100f, 100f, 103f, 40f)   // right=203
        val result = calculateSnap(moving, listOf(target))

        assertThat(result.snapDelta.x).isCloseTo(3f, AssertJOffset.offset(0.001f))
        assertThat(result.guides).anyMatch { it.axis == SnapAxis.VERTICAL && it.position == 203f }
    }

    @Test
    fun `right-to-right edge snap`() {
        val moving = Rect(100f, 200f, 100f, 50f)  // right=200
        val target = Rect(120f, 50f, 80f, 40f)     // right=200 (exact match)
        val result = calculateSnap(moving, listOf(target))

        // Right edges both at 200 — exact match, delta should be 0
        assertThat(result.snapDelta.x).isCloseTo(0f, AssertJOffset.offset(0.001f))
        assertThat(result.guides).anyMatch { it.axis == SnapAxis.VERTICAL && it.position == 200f }
    }

    @Test
    fun `top-to-top edge snap`() {
        val moving = Rect(100f, 53f, 60f, 40f)    // top=53
        val target = Rect(300f, 50f, 80f, 40f)     // top=50
        val result = calculateSnap(moving, listOf(target))

        assertThat(result.snapDelta.y).isCloseTo(-3f, AssertJOffset.offset(0.001f))
        assertThat(result.guides).anyMatch { it.axis == SnapAxis.HORIZONTAL && it.position == 50f }
    }

    @Test
    fun `bottom-to-bottom edge snap`() {
        // Moving: top=200, center=224, bottom=248
        // Target: top=300, center=325, bottom=350
        // Closest V match: bottom 248 vs 250 => dist=2 (but center 224 vs 325 = 101, top 200 vs 300 = 100)
        val moving = Rect(100f, 200f, 60f, 48f)   // bottom=248
        val target = Rect(300f, 300f, 80f, 50f)    // bottom=350 — far away from center/top
        // Moving bottom=248, target top=300, dist=52; moving top=200 vs target top=300 dist=100
        // But: moving bottom=248, target bottom=350, dist=102 — all beyond threshold
        // Let me use a target that only aligns on bottom edge
        val movingB = Rect(100f, 200f, 60f, 100f)   // top=200, center=250, bottom=300
        val targetB = Rect(300f, 50f, 80f, 252f)     // top=50, center=176, bottom=302
        // Moving bottom=300 vs target bottom=302, dist=2
        // Moving center=250 vs target center=176, dist=74
        // Moving top=200 vs target top=50, dist=150
        // So bottom-to-bottom is closest
        val result = calculateSnap(movingB, listOf(targetB))

        assertThat(result.snapDelta.y).isCloseTo(2f, AssertJOffset.offset(0.001f))
        assertThat(result.guides).anyMatch { it.axis == SnapAxis.HORIZONTAL && it.position == 302f }
    }

    // --- calculateSnap: center alignment ---

    @Test
    fun `center-to-center horizontal snap`() {
        val moving = Rect(100f, 200f, 100f, 50f)  // centerX=150
        val target = Rect(110f, 50f, 80f, 40f)     // centerX=150 (exact)
        val result = calculateSnap(moving, listOf(target))

        // Centers both at 150 — exact match
        assertThat(result.snapDelta.x).isCloseTo(0f, AssertJOffset.offset(0.001f))
    }

    @Test
    fun `center-to-center vertical snap`() {
        val moving = Rect(100f, 200f, 60f, 40f)   // centerY=220
        val target = Rect(300f, 197f, 80f, 50f)    // centerY=222
        val result = calculateSnap(moving, listOf(target))

        assertThat(result.snapDelta.y).isCloseTo(2f, AssertJOffset.offset(0.001f))
    }

    // --- calculateSnap: both axes simultaneously ---

    @Test
    fun `both axes snap simultaneously to different targets`() {
        val moving = Rect(103f, 203f, 60f, 40f)
        val targetH = Rect(100f, 500f, 80f, 40f)   // left=100, far away vertically
        val targetV = Rect(500f, 200f, 80f, 40f)    // top=200, far away horizontally
        val result = calculateSnap(moving, listOf(targetH, targetV))

        assertThat(result.snapDelta.x).isCloseTo(-3f, AssertJOffset.offset(0.001f))
        assertThat(result.snapDelta.y).isCloseTo(-3f, AssertJOffset.offset(0.001f))
        // Should have at least one guide per axis
        assertThat(result.guides.filter { it.axis == SnapAxis.VERTICAL }).isNotEmpty()
        assertThat(result.guides.filter { it.axis == SnapAxis.HORIZONTAL }).isNotEmpty()
    }

    // --- calculateSnap: threshold boundary ---

    @Test
    fun `exactly at threshold snaps`() {
        val moving = Rect(105f, 200f, 60f, 40f)   // left=105
        val target = Rect(100f, 50f, 80f, 40f)     // left=100, distance=5
        val result = calculateSnap(moving, listOf(target), threshold = 5f)

        assertThat(result.snapDelta.x).isCloseTo(-5f, AssertJOffset.offset(0.001f))
    }

    @Test
    fun `beyond threshold does not snap`() {
        val moving = Rect(106f, 200f, 60f, 40f)   // left=106
        val target = Rect(100f, 50f, 80f, 40f)     // left=100, distance=6
        val result = calculateSnap(moving, listOf(target), threshold = 5f)

        // Distance 6 > threshold 5, but center/right might be closer
        // Moving: left=106, center=136, right=166
        // Target: left=100, center=140, right=180
        // Closest: center 136 vs 140 = 4 (within threshold)
        assertThat(result.snapDelta.x).isCloseTo(4f, AssertJOffset.offset(0.001f))
    }

    @Test
    fun `well beyond threshold no snap at all`() {
        val moving = Rect(200f, 200f, 60f, 40f)
        val target = Rect(100f, 100f, 20f, 20f)
        // Moving: left=200, center=230, right=260
        // Target: left=100, center=110, right=120
        // Min distance = 200-120 = 80 (way beyond threshold)
        val result = calculateSnap(moving, listOf(target), threshold = 5f)

        assertThat(result).isEqualTo(SnapResult.NONE)
    }

    // --- calculateSnap: empty targets ---

    @Test
    fun `empty targets returns NONE`() {
        val moving = Rect(100f, 200f, 60f, 40f)
        val result = calculateSnap(moving, emptyList())
        assertThat(result).isEqualTo(SnapResult.NONE)
    }

    // --- calculateSnap: closest target wins ---

    @Test
    fun `closest target wins when multiple within threshold`() {
        val moving = Rect(100f, 200f, 60f, 40f)   // left=100
        val target1 = Rect(103f, 50f, 80f, 40f)    // left=103, dist=3
        val target2 = Rect(101f, 300f, 80f, 40f)   // left=101, dist=1
        val result = calculateSnap(moving, listOf(target1, target2))

        // target2 is closer (dist=1)
        assertThat(result.snapDelta.x).isCloseTo(1f, AssertJOffset.offset(0.001f))
    }

    // --- calculateSnap: multiple guides at same distance ---

    @Test
    fun `multiple targets at same position merge into one guide with widest span`() {
        val moving = Rect(103f, 200f, 60f, 40f)   // left=103, y range: 200-240
        val target1 = Rect(100f, 50f, 80f, 40f)    // left=100, y range: 50-90
        val target2 = Rect(100f, 300f, 90f, 40f)   // left=100, y range: 300-340
        val result = calculateSnap(moving, listOf(target1, target2))

        assertThat(result.snapDelta.x).isCloseTo(-3f, AssertJOffset.offset(0.001f))
        // Both targets at x=100 merge into a single guide spanning from 50 to 340
        val verticalGuides = result.guides.filter { it.axis == SnapAxis.VERTICAL && it.position == 100f }
        assertThat(verticalGuides).hasSize(1)
        assertThat(verticalGuides.first().spanStart).isCloseTo(50f, AssertJOffset.offset(0.001f))
        assertThat(verticalGuides.first().spanEnd).isCloseTo(340f, AssertJOffset.offset(0.001f))
    }

    // --- calculateSnap: guide span ---

    @Test
    fun `guide span covers both elements`() {
        val moving = Rect(102f, 200f, 100f, 50f)  // y range: 200-250
        val target = Rect(100f, 50f, 80f, 40f)     // y range: 50-90
        val result = calculateSnap(moving, listOf(target))

        val vGuide = result.guides.first { it.axis == SnapAxis.VERTICAL }
        assertThat(vGuide.spanStart).isCloseTo(50f, AssertJOffset.offset(0.001f))
        assertThat(vGuide.spanEnd).isCloseTo(250f, AssertJOffset.offset(0.001f))
    }

    // --- boundingBoxOf ---

    @Test
    fun `boundingBoxOf single rect returns same rect`() {
        val rect = Rect(10f, 20f, 30f, 40f)
        val result = boundingBoxOf(listOf(rect))!!
        assertThat(result.x).isEqualTo(10f)
        assertThat(result.y).isEqualTo(20f)
        assertThat(result.width).isEqualTo(30f)
        assertThat(result.height).isEqualTo(40f)
    }

    @Test
    fun `boundingBoxOf multiple rects returns union`() {
        val r1 = Rect(10f, 20f, 30f, 40f)   // right=40, bottom=60
        val r2 = Rect(50f, 10f, 20f, 100f)  // right=70, bottom=110
        val result = boundingBoxOf(listOf(r1, r2))!!

        assertThat(result.x).isEqualTo(10f)
        assertThat(result.y).isEqualTo(10f)
        assertThat(result.right).isEqualTo(70f)
        assertThat(result.bottom).isEqualTo(110f)
    }

    @Test
    fun `boundingBoxOf empty list returns null`() {
        assertThat(boundingBoxOf(emptyList())).isNull()
    }

    // --- SnapResult.NONE ---

    @Test
    fun `SnapResult NONE has zero delta and empty guides`() {
        assertThat(SnapResult.NONE.snapDelta).isEqualTo(Offset.Zero)
        assertThat(SnapResult.NONE.guides).isEmpty()
    }
}
