// Copyright 2026 Hyve. All rights reserved.
package com.hyve.ui.editor

import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

/**
 * Tests for the autosave debounce logic used in HyveUIEditorContent.
 *
 * The autosave feature observes `canvasState.treeVersion` via a debounced flow
 * and calls `onSave()` after 1500ms of quiet. These tests verify the flow
 * contract using a shorter debounce delay:
 * - version 0 (initial load) does not trigger save
 * - version > 0 triggers save after debounce
 * - rapid edits coalesce into a single save
 * - separate edit bursts trigger separate saves
 */
@OptIn(FlowPreview::class)
class AutosaveFlowTest {

    /** Debounce delay for tests (short to keep tests fast). */
    private val debounceMs = 50L

    /**
     * Launches a coroutine that mirrors the autosave logic:
     * ```
     * snapshotFlow { canvasState.treeVersion.value }
     *     .debounce(1500L)
     *     .collect { version -> if (version > 0L) onSave() }
     * ```
     * Uses MutableStateFlow in place of snapshotFlow (both are conflated).
     */
    private fun kotlinx.coroutines.CoroutineScope.launchAutosave(
        versionFlow: MutableStateFlow<Long>,
        onSave: () -> Unit
    ) = launch {
        versionFlow
            .debounce(debounceMs)
            .collect { version ->
                if (version > 0L) onSave()
            }
    }

    @Test
    fun `version 0 should not trigger save`() = runBlocking {
        val saveCount = AtomicInteger(0)
        val versionFlow = MutableStateFlow(0L)

        val job = launchAutosave(versionFlow) { saveCount.incrementAndGet() }

        delay(debounceMs * 3)
        assertThat(saveCount.get()).isEqualTo(0)
        job.cancel()
    }

    @Test
    fun `version greater than 0 should trigger save after debounce`() = runBlocking {
        val saveCount = AtomicInteger(0)
        val versionFlow = MutableStateFlow(0L)

        val job = launchAutosave(versionFlow) { saveCount.incrementAndGet() }

        versionFlow.value = 1L
        delay(debounceMs * 3)

        assertThat(saveCount.get()).isEqualTo(1)
        job.cancel()
    }

    @Test
    fun `rapid edits should coalesce into single save`() = runBlocking {
        val saveCount = AtomicInteger(0)
        val versionFlow = MutableStateFlow(0L)

        val job = launchAutosave(versionFlow) { saveCount.incrementAndGet() }

        // Rapid edits within the debounce window
        versionFlow.value = 1L
        delay(debounceMs / 5)
        versionFlow.value = 2L
        delay(debounceMs / 5)
        versionFlow.value = 3L

        // Wait for debounce to settle
        delay(debounceMs * 3)

        assertThat(saveCount.get()).isEqualTo(1)
        job.cancel()
    }

    @Test
    fun `separate edit bursts should trigger separate saves`() = runBlocking {
        val saveCount = AtomicInteger(0)
        val versionFlow = MutableStateFlow(0L)

        val job = launchAutosave(versionFlow) { saveCount.incrementAndGet() }

        // First burst
        versionFlow.value = 1L
        delay(debounceMs * 3)
        assertThat(saveCount.get()).isEqualTo(1)

        // Second burst (after debounce settled)
        versionFlow.value = 2L
        delay(debounceMs * 3)
        assertThat(saveCount.get()).isEqualTo(2)

        job.cancel()
    }
}
