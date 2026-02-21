// Copyright 2026 Hyve. All rights reserved.
package com.hyve.ui.editor.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.hyve.common.compose.HyveThemeColors
import java.awt.Cursor

class DraggableDividerState(initialRatio: Float = 0.4f) {
    var weightRatio by mutableFloatStateOf(initialRatio.coerceIn(0.15f, 0.85f))
        private set
    var panelHeightPx by mutableFloatStateOf(1f)
        private set

    fun adjustRatio(newRatio: Float) {
        weightRatio = newRatio.coerceIn(0.15f, 0.85f)
    }

    fun setHeight(px: Float) {
        panelHeightPx = px.coerceAtLeast(1f)
    }

    fun adjustByPixelDelta(deltaY: Float) {
        adjustRatio(weightRatio + (deltaY / panelHeightPx))
    }

    fun reset() {
        weightRatio = 0.4f
    }
}

@Composable
fun rememberDraggableDividerState(initialRatio: Float = 0.4f): DraggableDividerState {
    return remember { DraggableDividerState(initialRatio) }
}

@Composable
fun DraggableDivider(
    onDrag: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = HyveThemeColors.colors

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(4.dp)
            .pointerHoverIcon(PointerIcon(Cursor(Cursor.N_RESIZE_CURSOR)))
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    change.consume()
                    onDrag(dragAmount.y)
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(colors.slate)
        )
    }
}
