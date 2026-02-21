// Copyright 2026 Hyve. All rights reserved.
package com.hyve.ui.editor

import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

/**
 * Basic structural tests for M-003 (Visual/text editor toggle).
 *
 * Note: HyveUITextEditorWithPreviewProvider cannot be instantiated in a plain
 * JUnit test because its superclass (TextEditorWithPreviewProvider) calls
 * TextEditorProvider.getInstance() which requires the IntelliJ extension point
 * system. Full integration tests require the IntelliJ platform runtime.
 */
class HyveUITextEditorWithPreviewProviderTest {

    @Test
    fun `save handler should instantiate without error`() {
        val handler = HyveUIEditorSaveHandler()
        assertThat(handler).isNotNull
    }
}
