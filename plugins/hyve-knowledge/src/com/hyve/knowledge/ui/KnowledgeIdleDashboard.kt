// Copyright 2026 Hyve. All rights reserved.
package com.hyve.knowledge.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hyve.common.compose.*
import kotlinx.coroutines.delay
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.TextField

private val TYPEWRITER_QUERIES = listOf(
    "How does the inventory system work?",
    "What classes extend AbstractEntity?",
    "How are crafting recipes defined?",
    "What does the hotbar UI look like?",
    "How do I register a custom command?",
    "What NPCs spawn in the forest biome?",
    "How does the damage calculation work?",
    "Where is the player health bar defined?",
)

/**
 * Idle landing page with centered search bar and typewriter placeholder.
 * Filter chips sit below the search field like Claude's category buttons.
 */
@Composable
fun KnowledgeIdleDashboard(
    searchState: TextFieldState,
    enabledCorpora: Set<String>,
    onToggleCorpus: (String) -> Unit,
    onSubmit: () -> Unit,
    searchKeyHandler: Modifier = Modifier,
    modifier: Modifier = Modifier,
) {
    val colors = HyveThemeColors.colors
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()

    Column(
        modifier = modifier.fillMaxSize(),
    ) {
        Spacer(Modifier.weight(1f))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = HyveSpacing.xl),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(HyveSpacing.lg),
        ) {
            // Title
            Text(
                text = "Hytale Knowledge Base",
                fontSize = 22.sp,
                fontWeight = FontWeight.SemiBold,
                color = colors.textPrimary,
            )

            // Search field with typewriter placeholder — constrained width, custom container
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.65f)
                    .background(colors.slate.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
                    .border(1.dp, colors.slateLight.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            ) {
                TextField(
                    state = searchState,
                    modifier = Modifier.fillMaxWidth().then(searchKeyHandler),
                    interactionSource = interactionSource,
                    undecorated = true,
                    placeholder = {
                        // Typewriter animates when unfocused; hides when user clicks in
                        if (!isFocused) {
                            TypewriterPlaceholder()
                        }
                    },
                )
            }

            // Filter chips — centered beneath search
            CorpusFilterChips(
                enabledCorpora = enabledCorpora,
                onToggle = onToggleCorpus,
            )
        }

        Spacer(Modifier.weight(1f))
    }
}

/**
 * Animated typewriter placeholder that cycles through example queries.
 * Types character by character, pauses, then backspaces and moves to the next.
 */
@Composable
private fun TypewriterPlaceholder() {
    val colors = HyveThemeColors.colors
    var displayText by remember { mutableStateOf("") }
    var isPaused by remember { mutableStateOf(false) }

    // Cursor blink (only visible during pause)
    val infiniteTransition = rememberInfiniteTransition()
    val cursorAlpha by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(530),
            repeatMode = RepeatMode.Reverse,
        ),
    )

    LaunchedEffect(Unit) {
        var index = 0
        while (true) {
            val query = TYPEWRITER_QUERIES[index % TYPEWRITER_QUERIES.size]

            // Type forward
            isPaused = false
            for (i in 1..query.length) {
                displayText = query.substring(0, i)
                delay(45)
            }

            // Pause with blinking cursor
            isPaused = true
            delay(4000)

            // Backspace
            isPaused = false
            for (i in query.length downTo 0) {
                displayText = query.substring(0, i)
                delay(20)
            }

            delay(400)
            index++
        }
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = displayText,
            color = colors.textDisabled,
        )
        // Blinking cursor bar
        Box(
            Modifier
                .width(1.dp)
                .height(14.dp)
                .alpha(if (isPaused) cursorAlpha else 1f)
                .background(colors.textDisabled)
        )
    }
}
