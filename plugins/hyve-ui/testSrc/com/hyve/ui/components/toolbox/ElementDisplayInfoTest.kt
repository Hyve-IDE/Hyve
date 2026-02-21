// Copyright 2026 Hyve. All rights reserved.
package com.hyve.ui.components.toolbox

import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

class ElementDisplayInfoTest {

    // --- displayNameFor: known types ---

    @Test
    fun `should return friendly name for MultilineTextField`() {
        assertThat(ElementDisplayInfo.displayNameFor("MultilineTextField"))
            .isEqualTo("Multiline Text Field")
    }

    @Test
    fun `should return friendly name for CharacterPreviewComponent`() {
        assertThat(ElementDisplayInfo.displayNameFor("CharacterPreviewComponent"))
            .isEqualTo("Character Preview")
    }

    @Test
    fun `should return friendly name for FloatSliderNumberField`() {
        assertThat(ElementDisplayInfo.displayNameFor("FloatSliderNumberField"))
            .isEqualTo("Float Slider")
    }

    @Test
    fun `should return friendly name for DropdownBox`() {
        assertThat(ElementDisplayInfo.displayNameFor("DropdownBox"))
            .isEqualTo("Dropdown")
    }

    @Test
    fun `should return simple names unchanged`() {
        assertThat(ElementDisplayInfo.displayNameFor("Button")).isEqualTo("Button")
        assertThat(ElementDisplayInfo.displayNameFor("Label")).isEqualTo("Label")
        assertThat(ElementDisplayInfo.displayNameFor("Image")).isEqualTo("Image")
    }

    @Test
    fun `should return friendly name for internal elements`() {
        assertThat(ElementDisplayInfo.displayNameFor("_VariableRefElement"))
            .isEqualTo("Variable Ref")
        assertThat(ElementDisplayInfo.displayNameFor("_IdOnlyBlock"))
            .isEqualTo("ID Block")
    }

    // --- displayNameFor: unknown types (fallback) ---

    @Test
    fun `should split unknown PascalCase into words`() {
        assertThat(ElementDisplayInfo.displayNameFor("SomeNewWidget"))
            .isEqualTo("Some New Widget")
    }

    @Test
    fun `should handle single word unknown type`() {
        assertThat(ElementDisplayInfo.displayNameFor("Foobar"))
            .isEqualTo("Foobar")
    }

    @Test
    fun `should strip leading underscore in fallback`() {
        assertThat(ElementDisplayInfo.displayNameFor("_CustomThing"))
            .isEqualTo("Custom Thing")
    }

    // --- descriptionFor ---

    @Test
    fun `should return description for known types`() {
        assertThat(ElementDisplayInfo.descriptionFor("Button"))
            .isEqualTo("Clickable button that triggers actions")
    }

    @Test
    fun `should return description for ScrollView`() {
        assertThat(ElementDisplayInfo.descriptionFor("ScrollView"))
            .isEqualTo("Scrollable container for content that exceeds visible area")
    }

    @Test
    fun `should return null for unknown type`() {
        assertThat(ElementDisplayInfo.descriptionFor("CompletelyUnknownType"))
            .isNull()
    }

    @Test
    fun `every type with a display name should also have a description`() {
        // All explicitly registered display names should have matching descriptions
        val knownTypes = listOf(
            "Group", "Panel", "Root", "DynamicPane", "DynamicPaneContainer",
            "ScrollView", "ItemGrid", "ReorderableListGrip",
            "Label", "HotkeyLabel", "CodeEditor",
            "Button", "ActionButton", "BackButton", "TabButton", "ToggleButton",
            "TextField", "CompactTextField", "MultilineTextField", "NumberField",
            "FloatSliderNumberField", "SliderNumberField",
            "CheckBox", "CheckBoxContainer", "LabeledCheckBox",
            "DropdownBox", "DropdownEntry",
            "Slider", "ProgressBar", "CircularProgressBar",
            "Image", "BackgroundImage", "SceneBlur",
            "CharacterPreviewComponent", "PlayerPreviewComponent",
            "ItemPreviewComponent", "BlockSelector",
            "TabPanel", "Tooltip",
            "_VariableRefElement", "_StylePrefixedElement", "_IdOnlyBlock"
        )
        for (type in knownTypes) {
            assertThat(ElementDisplayInfo.descriptionFor(type))
                .describedAs("Description for $type")
                .isNotNull()
                .isNotBlank()
        }
    }

    // --- splitPascalCase ---

    @Test
    fun `splitPascalCase should split standard PascalCase`() {
        assertThat(ElementDisplayInfo.splitPascalCase("HelloWorld"))
            .isEqualTo("Hello World")
    }

    @Test
    fun `splitPascalCase should handle single word`() {
        assertThat(ElementDisplayInfo.splitPascalCase("Button"))
            .isEqualTo("Button")
    }

    @Test
    fun `splitPascalCase should handle multiple transitions`() {
        assertThat(ElementDisplayInfo.splitPascalCase("MyBigFancyWidget"))
            .isEqualTo("My Big Fancy Widget")
    }

    @Test
    fun `splitPascalCase should strip leading underscores`() {
        assertThat(ElementDisplayInfo.splitPascalCase("_Private"))
            .isEqualTo("Private")
    }

    @Test
    fun `splitPascalCase should handle empty string`() {
        assertThat(ElementDisplayInfo.splitPascalCase(""))
            .isEqualTo("")
    }

    @Test
    fun `splitPascalCase should preserve consecutive uppercase`() {
        // "UIElement" → "UIElement" (no split between consecutive uppercase)
        assertThat(ElementDisplayInfo.splitPascalCase("UIElement"))
            .isEqualTo("UIElement")
    }

    // --- Wave additions: HotkeyLabel, BackButton, CompactTextField, DropdownEntry ---

    @Test
    fun `should return friendly name for HotkeyLabel`() {
        assertThat(ElementDisplayInfo.displayNameFor("HotkeyLabel"))
            .isEqualTo("Hotkey Label")
    }

    @Test
    fun `should return friendly name for BackButton`() {
        assertThat(ElementDisplayInfo.displayNameFor("BackButton"))
            .isEqualTo("Back Button")
    }

    @Test
    fun `should return friendly name for CompactTextField`() {
        assertThat(ElementDisplayInfo.displayNameFor("CompactTextField"))
            .isEqualTo("Compact Text Field")
    }

    @Test
    fun `should return friendly name for DropdownEntry`() {
        assertThat(ElementDisplayInfo.displayNameFor("DropdownEntry"))
            .isEqualTo("Dropdown Entry")
    }

    @Test
    fun `should return description for HotkeyLabel`() {
        assertThat(ElementDisplayInfo.descriptionFor("HotkeyLabel")).isNotNull().isNotBlank()
    }

    @Test
    fun `should return description for BackButton`() {
        assertThat(ElementDisplayInfo.descriptionFor("BackButton")).isNotNull().isNotBlank()
    }

    @Test
    fun `should return description for CompactTextField`() {
        assertThat(ElementDisplayInfo.descriptionFor("CompactTextField")).isNotNull().isNotBlank()
    }

    @Test
    fun `should return description for DropdownEntry`() {
        assertThat(ElementDisplayInfo.descriptionFor("DropdownEntry")).isNotNull().isNotBlank()
    }

    // --- toolbox blacklist ---

    @Test
    fun `internal underscore types should be blacklisted`() {
        assertThat(isToolboxBlacklisted("_VariableRefElement")).isTrue()
        assertThat(isToolboxBlacklisted("_StylePrefixedElement")).isTrue()
        assertThat(isToolboxBlacklisted("_IdOnlyBlock")).isTrue()
    }

    @Test
    fun `Root should be blacklisted`() {
        assertThat(isToolboxBlacklisted("Root")).isTrue()
    }

    @Test
    fun `normal elements should not be blacklisted`() {
        assertThat(isToolboxBlacklisted("Button")).isFalse()
        assertThat(isToolboxBlacklisted("Label")).isFalse()
        assertThat(isToolboxBlacklisted("ScrollView")).isFalse()
        assertThat(isToolboxBlacklisted("SceneBlur")).isFalse()
        assertThat(isToolboxBlacklisted("CodeEditor")).isFalse()
    }
}
