package com.hyve.ui.schema.tier3

import com.hyve.ui.core.id.ElementType
import com.hyve.ui.core.id.PropertyName
import com.hyve.ui.schema.*

/**
 * Schema for TabPanel element - tabbed interface container.
 *
 * TabPanel elements provide a tabbed interface where users can switch
 * between multiple content panels. Only one panel is visible at a time,
 * with tabs at the top for navigation.
 *
 * Common properties:
 * - Tabs: List of tab names
 * - SelectedTab: Index of currently selected tab
 * - OnTabChange: Event triggered when tab selection changes
 */
object TabPanelSchema {
    fun create(): ElementSchema = ElementSchema(
        type = ElementType("TabPanel"),
        category = ElementCategory.CONTAINER,
        description = "Tabbed interface container for switching between multiple content panels",
        canHaveChildren = true,
        properties = listOf(
            // Tab configuration
            PropertySchema(
                name = PropertyName("Tabs"),
                type = PropertyType.LIST,
                required = false,
                description = "List of tab names"
            ),
            PropertySchema(
                name = PropertyName("SelectedTab"),
                type = PropertyType.NUMBER,
                required = false,
                description = "Index of currently selected tab (0-based)"
            ),

            // Event handling
            PropertySchema(
                name = PropertyName("OnTabChange"),
                type = PropertyType.TEXT,
                required = false,
                description = "Event triggered when tab selection changes"
            ),

            // Tab bar styling
            PropertySchema(
                name = PropertyName("TabBarHeight"),
                type = PropertyType.NUMBER,
                required = false,
                description = "Height of the tab bar"
            ),
            PropertySchema(
                name = PropertyName("TabBarBackground"),
                type = PropertyType.COLOR,
                required = false,
                description = "Background color of the tab bar"
            ),
            PropertySchema(
                name = PropertyName("TabBarPosition"),
                type = PropertyType.TEXT,
                required = false,
                description = "Position of tab bar: Top, Bottom, Left, Right"
            ),

            // Tab styling
            PropertySchema(
                name = PropertyName("TabBackground"),
                type = PropertyType.COLOR,
                required = false,
                description = "Background color of inactive tabs"
            ),
            PropertySchema(
                name = PropertyName("TabActiveBackground"),
                type = PropertyType.COLOR,
                required = false,
                description = "Background color of active tab"
            ),
            PropertySchema(
                name = PropertyName("TabHoverBackground"),
                type = PropertyType.COLOR,
                required = false,
                description = "Background color when hovering over tab"
            ),
            PropertySchema(
                name = PropertyName("TabTextColor"),
                type = PropertyType.COLOR,
                required = false,
                description = "Text color of inactive tabs"
            ),
            PropertySchema(
                name = PropertyName("TabActiveTextColor"),
                type = PropertyType.COLOR,
                required = false,
                description = "Text color of active tab"
            ),
            PropertySchema(
                name = PropertyName("TabFontSize"),
                type = PropertyType.NUMBER,
                required = false,
                description = "Font size of tab text"
            ),
            PropertySchema(
                name = PropertyName("TabPadding"),
                type = PropertyType.TUPLE,
                required = false,
                description = "Padding inside each tab"
            ),
            PropertySchema(
                name = PropertyName("TabSpacing"),
                type = PropertyType.NUMBER,
                required = false,
                description = "Gap between tabs"
            ),

            // Content panel styling
            PropertySchema(
                name = PropertyName("PanelBackground"),
                type = PropertyType.COLOR,
                required = false,
                description = "Background color of content panels"
            ),
            PropertySchema(
                name = PropertyName("PanelPadding"),
                type = PropertyType.TUPLE,
                required = false,
                description = "Padding inside content panels"
            ),

            // Border
            CommonPropertySchemas.borderColor(),
            CommonPropertySchemas.borderWidth(),

            // Layout
            CommonPropertySchemas.anchor("Position and size of the tab panel"),

            // State
            CommonPropertySchemas.enabled("Whether tabs are interactive"),
            CommonPropertySchemas.visible(),

            // Style reference
            CommonPropertySchemas.style()
        ),
        examples = listOf(
            """
            TabPanel #SettingsTabs {
                Tabs: ["General", "Graphics", "Audio", "Controls"];
                SelectedTab: 0;
                Anchor: (Left: 0, Top: 0, Width: 600, Height: 400);
                TabBarHeight: 40;
                OnTabChange: "settings_tab_changed";
            }
            """.trimIndent()
        )
    )
}
