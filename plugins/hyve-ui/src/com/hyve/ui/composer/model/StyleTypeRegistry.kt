// Copyright 2026 Hyve. All rights reserved.
package com.hyve.ui.composer.model

/**
 * Hardcoded mapping of style types to their property slots.
 *
 * Each [StyleType] defines a fixed set of properties that appear in every
 * visual state of a style definition. The mapping comes from spec 04 FR-2.
 *
 * ## Usage
 * ```kotlin
 * val tab = StyleTypeRegistry.createStyleTab("@ButtonStyle", StyleType.TEXT_BUTTON_STYLE)
 * ```
 */
object StyleTypeRegistry {

    private val STANDARD_STATE_NAMES = listOf("Default", "Hovered", "Pressed", "Disabled")

    /**
     * Returns empty property slots for the given style type.
     * All slots have `category = APPEARANCE` and `fillMode = EMPTY`.
     */
    fun slotsForStyleType(styleType: StyleType): List<PropertySlot> = when (styleType) {
        StyleType.TEXT_BUTTON_STYLE -> listOf(
            slot("Background", ComposerPropertyType.IMAGE),
            slot("LabelStyle", ComposerPropertyType.STYLE),
            slot("TextColor", ComposerPropertyType.COLOR),
            slot("BackgroundColor", ComposerPropertyType.COLOR),
        )
        StyleType.LABEL_STYLE -> listOf(
            slot("TextColor", ComposerPropertyType.COLOR),
            slot("FontSize", ComposerPropertyType.NUMBER),
            slot("FontName", ComposerPropertyType.FONT),
        )
        StyleType.TEXT_FIELD_STYLE -> listOf(
            slot("Background", ComposerPropertyType.IMAGE),
            slot("TextColor", ComposerPropertyType.COLOR),
            slot("PlaceholderColor", ComposerPropertyType.COLOR),
            slot("BorderColor", ComposerPropertyType.COLOR),
        )
        StyleType.CHECK_BOX_STYLE -> listOf(
            slot("Background", ComposerPropertyType.IMAGE),
            slot("CheckColor", ComposerPropertyType.COLOR),
            slot("BorderColor", ComposerPropertyType.COLOR),
        )
        StyleType.SCROLLBAR_STYLE -> listOf(
            slot("TrackColor", ComposerPropertyType.COLOR),
            slot("ThumbColor", ComposerPropertyType.COLOR),
            slot("ThumbWidth", ComposerPropertyType.NUMBER),
        )
        StyleType.SLIDER_STYLE -> listOf(
            slot("TrackColor", ComposerPropertyType.COLOR),
            slot("FillColor", ComposerPropertyType.COLOR),
            slot("HandleColor", ComposerPropertyType.COLOR),
            slot("HandleSize", ComposerPropertyType.NUMBER),
        )
        StyleType.DROPDOWN_BOX_STYLE -> listOf(
            slot("Background", ComposerPropertyType.IMAGE),
            slot("TextColor", ComposerPropertyType.COLOR),
            slot("BorderColor", ComposerPropertyType.COLOR),
            slot("ArrowColor", ComposerPropertyType.COLOR),
        )
        StyleType.TAB_PANEL_STYLE -> listOf(
            slot("TabBackground", ComposerPropertyType.IMAGE),
            slot("ActiveTabColor", ComposerPropertyType.COLOR),
            slot("InactiveTabColor", ComposerPropertyType.COLOR),
        )
        StyleType.PROGRESS_BAR_STYLE -> listOf(
            slot("BarColor", ComposerPropertyType.COLOR),
            slot("BackgroundColor", ComposerPropertyType.COLOR),
            slot("BarTexturePath", ComposerPropertyType.IMAGE),
        )
        StyleType.TOOLTIP_STYLE -> listOf(
            slot("Background", ComposerPropertyType.IMAGE),
            slot("TextColor", ComposerPropertyType.COLOR),
            slot("BorderColor", ComposerPropertyType.COLOR),
        )
    }

    /**
     * Build a complete [StyleTab] for a new style.
     *
     * Creates 4 standard states (Default, Hovered, Pressed, Disabled), each
     * populated with the same set of empty slots for the given style type.
     *
     * @param name The style name including `@` prefix (e.g. "@ButtonStyle")
     * @param styleType The selected style type
     */
    fun createStyleTab(name: String, styleType: StyleType): StyleTab {
        val templateSlots = slotsForStyleType(styleType)
        val states = STANDARD_STATE_NAMES.map { stateName ->
            StyleState(
                name = stateName,
                slots = templateSlots.map { it.copy() },
            )
        }
        return StyleTab(
            name = name,
            styleType = styleType.displayName,
            states = states,
        )
    }

    private fun slot(name: String, type: ComposerPropertyType) = PropertySlot(
        name = name,
        type = type,
        category = SlotCategory.APPEARANCE,
        fillMode = FillMode.EMPTY,
        value = "",
    )
}
