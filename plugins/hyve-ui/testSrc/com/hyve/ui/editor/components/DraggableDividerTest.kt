// Copyright 2026 Hyve. All rights reserved.
package com.hyve.ui.editor.components

import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

class DraggableDividerTest {

    @Test
    fun `initial ratio is 0_4`() {
        val state = DraggableDividerState()
        assertThat(state.weightRatio).isEqualTo(0.4f)
    }

    @Test
    fun `adjustRatio clamps to min 0_15`() {
        val state = DraggableDividerState()
        state.adjustRatio(0.05f)
        assertThat(state.weightRatio).isEqualTo(0.15f)
    }

    @Test
    fun `adjustRatio clamps to max 0_85`() {
        val state = DraggableDividerState()
        state.adjustRatio(0.95f)
        assertThat(state.weightRatio).isEqualTo(0.85f)
    }

    @Test
    fun `adjustRatio updates value correctly for normal inputs`() {
        val state = DraggableDividerState()
        state.adjustRatio(0.6f)
        assertThat(state.weightRatio).isEqualTo(0.6f)
    }

    @Test
    fun `reset returns to default 0_4`() {
        val state = DraggableDividerState()
        state.adjustRatio(0.7f)
        assertThat(state.weightRatio).isEqualTo(0.7f)
        state.reset()
        assertThat(state.weightRatio).isEqualTo(0.4f)
    }
}
