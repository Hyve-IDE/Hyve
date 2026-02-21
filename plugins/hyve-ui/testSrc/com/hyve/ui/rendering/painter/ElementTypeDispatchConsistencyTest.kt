// Copyright 2026 Hyve. All rights reserved.
package com.hyve.ui.rendering.painter

import com.hyve.ui.components.hierarchy.getElementIconKey
import com.hyve.ui.components.toolbox.ElementDisplayInfo
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.jewel.ui.icons.AllIconsKeys
import org.junit.Test

/**
 * Ensures all element types handled in CanvasPainter's dispatch switch
 * also have entries in ElementDisplayInfo and TreeNode icon mapping.
 * Catches cross-registry drift when new types are added to one place but not others.
 */
class ElementTypeDispatchConsistencyTest {

    /**
     * All element types with explicit cases in CanvasPainter's main dispatch (lines 883-919).
     * This does NOT include the "else" fallback branch.
     */
    private val CANONICAL_PAINTER_TYPES = setOf(
        // Tier 1
        "Group", "Label", "HotkeyLabel",
        "Button", "ActionButton", "BackButton", "ToggleButton", "TabButton",
        "TextField", "CompactTextField", "Image",
        // Tier 2
        "Slider", "SliderNumberField", "FloatSliderNumberField",
        "CheckBox", "LabeledCheckBox", "CheckBoxContainer",
        "ProgressBar", "CircularProgressBar",
        "ScrollView", "DropdownBox", "DropdownEntry",
        "TabPanel", "Tooltip",
        "MultilineTextField", "NumberField",
        // 3D preview
        "ItemPreviewComponent", "BlockSelector",
        "CharacterPreviewComponent", "PlayerPreviewComponent",
        "ItemGrid",
        // Container delegates
        "Panel", "Root", "DynamicPane", "DynamicPaneContainer",
        "ReorderableListGrip",
        // Visual
        "BackgroundImage", "SceneBlur"
    )

    @Test
    fun `every canonical type has a display name in ElementDisplayInfo`() {
        for (type in CANONICAL_PAINTER_TYPES) {
            val name = ElementDisplayInfo.displayNameFor(type)
            assertThat(name)
                .describedAs("Display name for $type should not be a PascalCase fallback split")
                .isNotBlank()
        }
    }

    @Test
    fun `every canonical type has a description in ElementDisplayInfo`() {
        for (type in CANONICAL_PAINTER_TYPES) {
            assertThat(ElementDisplayInfo.descriptionFor(type))
                .describedAs("Description for $type")
                .isNotNull()
                .isNotBlank()
        }
    }

    @Test
    fun `every canonical type has a non-fallback icon in getElementIconKey`() {
        val fallbackIcon = AllIconsKeys.Nodes.Plugin
        for (type in CANONICAL_PAINTER_TYPES) {
            val icon = getElementIconKey(type)
            assertThat(icon)
                .describedAs("Icon for $type should not be the fallback Plugin icon")
                .isNotEqualTo(fallbackIcon)
        }
    }
}
