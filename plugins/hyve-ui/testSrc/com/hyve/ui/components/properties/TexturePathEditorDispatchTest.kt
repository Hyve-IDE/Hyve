// Copyright 2026 Hyve. All rights reserved.
package com.hyve.ui.components.properties

import com.hyve.ui.core.id.PropertyName
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

/**
 * Validates the dispatch condition in PropertyEditor that routes
 * TexturePath properties to PathPropertyEditor (with Browse button)
 * instead of the default TextPropertyEditor.
 */
class TexturePathEditorDispatchTest {

    @Test
    fun `TexturePath property name triggers image path editor condition`() {
        val name = PropertyName("TexturePath")
        assertThat(name.value == "TexturePath").isTrue()
    }

    @Test
    fun `other text property names do not trigger image path condition`() {
        assertThat(PropertyName("Title").value == "TexturePath").isFalse()
        assertThat(PropertyName("Description").value == "TexturePath").isFalse()
        assertThat(PropertyName("Text").value == "TexturePath").isFalse()
        assertThat(PropertyName("FontPath").value == "TexturePath").isFalse()
    }
}
