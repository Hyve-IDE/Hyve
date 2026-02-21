// Copyright 2026 Hyve. All rights reserved.
package com.hyve.ui.editor.components

import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

class SidebarStateTest {

    @Test
    fun `initial state has both sidebars expanded`() {
        val state = SidebarState()
        assertThat(state.isLeftCollapsed).isFalse()
        assertThat(state.isRightCollapsed).isFalse()
    }

    @Test
    fun `toggleLeft collapses left sidebar`() {
        val state = SidebarState()
        state.toggleLeft()
        assertThat(state.isLeftCollapsed).isTrue()
        assertThat(state.isRightCollapsed).isFalse()
    }

    @Test
    fun `toggleRight collapses right sidebar`() {
        val state = SidebarState()
        state.toggleRight()
        assertThat(state.isLeftCollapsed).isFalse()
        assertThat(state.isRightCollapsed).isTrue()
    }

    @Test
    fun `double toggle returns to original expanded state`() {
        val state = SidebarState()
        state.toggleLeft()
        state.toggleLeft()
        assertThat(state.isLeftCollapsed).isFalse()

        state.toggleRight()
        state.toggleRight()
        assertThat(state.isRightCollapsed).isFalse()
    }
}
