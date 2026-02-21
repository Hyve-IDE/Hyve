// Copyright 2026 Hyve. All rights reserved.
package com.hyve.ui.editor.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.hyve.common.compose.HyveSpacing

private val LEFT_WIDTH = 200.dp
private val RIGHT_WIDTH = 280.dp
private val COLLAPSED_WIDTH = HyveSpacing.md  // stub arrow strip width

class SidebarState {
    val leftExpandedWidth: Dp = LEFT_WIDTH
    val rightExpandedWidth: Dp = RIGHT_WIDTH
    val collapsedWidth: Dp = COLLAPSED_WIDTH

    var leftTargetWidth by mutableStateOf(LEFT_WIDTH)
        private set

    var rightTargetWidth by mutableStateOf(RIGHT_WIDTH)
        private set

    val isLeftCollapsed get() = leftTargetWidth == COLLAPSED_WIDTH
    val isRightCollapsed get() = rightTargetWidth == COLLAPSED_WIDTH

    fun toggleLeft() {
        leftTargetWidth = if (leftTargetWidth > COLLAPSED_WIDTH) COLLAPSED_WIDTH else LEFT_WIDTH
    }

    fun toggleRight() {
        rightTargetWidth = if (rightTargetWidth > COLLAPSED_WIDTH) COLLAPSED_WIDTH else RIGHT_WIDTH
    }
}

@Composable
fun rememberSidebarState(): SidebarState {
    return remember { SidebarState() }
}
