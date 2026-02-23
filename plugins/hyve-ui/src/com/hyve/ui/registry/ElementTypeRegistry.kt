package com.hyve.ui.registry

import com.hyve.ui.core.domain.properties.PropertyValue
import org.jetbrains.jewel.ui.icons.AllIconsKeys
import org.jetbrains.jewel.ui.icon.IconKey

/**
 * How CanvasPainter should render an element.
 * Each strategy maps to a dedicated draw function in CanvasPainter.
 */
enum class RenderStrategy {
    GROUP,
    LABEL,
    BUTTON,
    TEXT_FIELD,
    IMAGE,
    SLIDER,
    CHECKBOX,
    PROGRESS_BAR,
    SCROLL_VIEW,
    DROPDOWN,
    TAB_PANEL,
    TOOLTIP,
    MULTILINE_TEXT_FIELD,
    NUMBER_FIELD,
    ITEM_PREVIEW,
    BLOCK_SELECTOR,
    CHARACTER_PREVIEW,
    PLAYER_PREVIEW,
    ITEM_GRID,
    SCENE_BLUR,
    UNKNOWN
}

/**
 * Capabilities that an element type can have.
 */
enum class ElementCapability {
    /** Supports inline text editing via double-click */
    TEXT_EDITABLE,
    /** Can contain child elements */
    CONTAINER,
    /** Interactive element that should have an ID for Java event binding */
    INTERACTIVE,
    /** Has a NumberField overlay (e.g. SliderNumberField, FloatSliderNumberField) */
    HAS_NUMBER_FIELD
}

/**
 * All metadata for a single element type, centralized in one place.
 */
data class ElementTypeInfo(
    val typeName: String,
    val displayName: String,
    val description: String?,
    val category: String,
    val hierarchyIcon: IconKey,
    val toolboxIcon: IconKey,
    val defaultSize: Pair<Float, Float>,
    val renderStrategy: RenderStrategy,
    val capabilities: Set<ElementCapability>,
    val isToolboxVisible: Boolean,
    /**
     * Fallback properties applied when creating a new element from the toolbox.
     * Only set if the property is not already present from schema defaults.
     */
    val defaultFallbackProperties: Map<String, PropertyValue> = emptyMap()
)

/**
 * Centralized registry of all known Hytale UI element types.
 *
 * Replaces scattered when(typeName) string branches across CanvasPainter,
 * TreeNode, ElementToolbox, CanvasState, and ElementDisplayInfo.
 *
 * To add a new element type: add ONE entry to [entries] below.
 */
object ElementTypeRegistry {

    private val byName: Map<String, ElementTypeInfo>

    /** All registered element types. */
    val entries: List<ElementTypeInfo>

    init {
        val all = buildList {
            // ── Containers ──────────────────────────────────────────────
            add(ElementTypeInfo(
                typeName = "Root",
                displayName = "Root",
                description = "Top-level root container for a .ui file",
                category = "Container",
                hierarchyIcon = AllIconsKeys.Nodes.Folder,
                toolboxIcon = AllIconsKeys.Nodes.Module,
                defaultSize = 200f to 150f,
                renderStrategy = RenderStrategy.GROUP,
                capabilities = setOf(ElementCapability.CONTAINER),
                isToolboxVisible = false
            ))
            add(ElementTypeInfo(
                typeName = "Group",
                displayName = "Group",
                description = "Container that groups child UI elements with optional layout",
                category = "Container",
                hierarchyIcon = AllIconsKeys.Nodes.Folder,
                toolboxIcon = AllIconsKeys.Actions.GroupBy,
                defaultSize = 200f to 150f,
                renderStrategy = RenderStrategy.GROUP,
                capabilities = setOf(ElementCapability.CONTAINER),
                isToolboxVisible = true
            ))
            add(ElementTypeInfo(
                typeName = "Panel",
                displayName = "Panel",
                description = "Generic container panel for organizing content",
                category = "Container",
                hierarchyIcon = AllIconsKeys.Nodes.Folder,
                toolboxIcon = AllIconsKeys.Toolwindows.ToolWindowProject,
                defaultSize = 200f to 150f,
                renderStrategy = RenderStrategy.GROUP,
                capabilities = setOf(ElementCapability.CONTAINER),
                isToolboxVisible = true
            ))
            add(ElementTypeInfo(
                typeName = "DynamicPane",
                displayName = "Dynamic Pane",
                description = "Resizable pane within a dynamic layout",
                category = "Container",
                hierarchyIcon = AllIconsKeys.Nodes.Folder,
                toolboxIcon = AllIconsKeys.Actions.SplitVertically,
                defaultSize = 200f to 150f,
                renderStrategy = RenderStrategy.GROUP,
                capabilities = setOf(ElementCapability.CONTAINER),
                isToolboxVisible = true
            ))
            add(ElementTypeInfo(
                typeName = "DynamicPaneContainer",
                displayName = "Dynamic Pane Container",
                description = "Container that holds resizable dynamic panes",
                category = "Container",
                hierarchyIcon = AllIconsKeys.Nodes.Folder,
                toolboxIcon = AllIconsKeys.Actions.SplitHorizontally,
                defaultSize = 200f to 150f,
                renderStrategy = RenderStrategy.GROUP,
                capabilities = setOf(ElementCapability.CONTAINER),
                isToolboxVisible = true
            ))
            add(ElementTypeInfo(
                typeName = "ReorderableListGrip",
                displayName = "Reorderable List Grip",
                description = "Drag-to-reorder list container",
                category = "Container",
                hierarchyIcon = AllIconsKeys.Nodes.Folder,
                toolboxIcon = AllIconsKeys.Actions.MoveUp,
                defaultSize = 200f to 150f,
                renderStrategy = RenderStrategy.GROUP,
                capabilities = setOf(ElementCapability.CONTAINER),
                isToolboxVisible = true
            ))

            // ── Text ────────────────────────────────────────────────────
            add(ElementTypeInfo(
                typeName = "Label",
                displayName = "Label",
                description = "Static or dynamic text display",
                category = "Text",
                hierarchyIcon = AllIconsKeys.Actions.Edit,
                toolboxIcon = AllIconsKeys.FileTypes.Text,
                defaultSize = 100f to 24f,
                renderStrategy = RenderStrategy.LABEL,
                capabilities = setOf(ElementCapability.TEXT_EDITABLE),
                isToolboxVisible = true,
                defaultFallbackProperties = mapOf(
                    "Text" to PropertyValue.Text("Label")
                )
            ))
            add(ElementTypeInfo(
                typeName = "HotkeyLabel",
                displayName = "Hotkey Label",
                description = "Label that displays a keyboard shortcut",
                category = "Text",
                hierarchyIcon = AllIconsKeys.Actions.Edit,
                toolboxIcon = AllIconsKeys.FileTypes.Text,
                defaultSize = 100f to 24f,
                renderStrategy = RenderStrategy.LABEL,
                capabilities = setOf(ElementCapability.TEXT_EDITABLE),
                isToolboxVisible = true,
                defaultFallbackProperties = mapOf(
                    "Text" to PropertyValue.Text("Label")
                )
            ))
            add(ElementTypeInfo(
                typeName = "CodeEditor",
                displayName = "Code Editor",
                description = "Monospace code editing area",
                category = "Text",
                hierarchyIcon = AllIconsKeys.Actions.Edit,
                toolboxIcon = AllIconsKeys.FileTypes.Text,
                defaultSize = 250f to 100f,
                renderStrategy = RenderStrategy.MULTILINE_TEXT_FIELD,
                capabilities = setOf(ElementCapability.TEXT_EDITABLE),
                isToolboxVisible = true
            ))

            // ── Buttons ─────────────────────────────────────────────────
            add(ElementTypeInfo(
                typeName = "Button",
                displayName = "Button",
                description = "Clickable button that triggers actions",
                category = "Interactive",
                hierarchyIcon = AllIconsKeys.Actions.Execute,
                toolboxIcon = AllIconsKeys.Actions.Execute,
                defaultSize = 120f to 40f,
                renderStrategy = RenderStrategy.BUTTON,
                capabilities = setOf(ElementCapability.TEXT_EDITABLE, ElementCapability.INTERACTIVE),
                isToolboxVisible = true,
                defaultFallbackProperties = mapOf(
                    "Text" to PropertyValue.Text("Button")
                )
            ))
            add(ElementTypeInfo(
                typeName = "ActionButton",
                displayName = "Action Button",
                description = "Specialized button for quick actions",
                category = "Interactive",
                hierarchyIcon = AllIconsKeys.Actions.Execute,
                toolboxIcon = AllIconsKeys.Actions.Lightning,
                defaultSize = 120f to 40f,
                renderStrategy = RenderStrategy.BUTTON,
                capabilities = setOf(ElementCapability.TEXT_EDITABLE, ElementCapability.INTERACTIVE),
                isToolboxVisible = true,
                defaultFallbackProperties = mapOf(
                    "Text" to PropertyValue.Text("Button")
                )
            ))
            add(ElementTypeInfo(
                typeName = "BackButton",
                displayName = "Back Button",
                description = "Navigation back button",
                category = "Interactive",
                hierarchyIcon = AllIconsKeys.Actions.Execute,
                toolboxIcon = AllIconsKeys.Actions.Back,
                defaultSize = 120f to 40f,
                renderStrategy = RenderStrategy.BUTTON,
                capabilities = setOf(ElementCapability.TEXT_EDITABLE, ElementCapability.INTERACTIVE),
                isToolboxVisible = true,
                defaultFallbackProperties = mapOf(
                    "Text" to PropertyValue.Text("Button")
                )
            ))
            add(ElementTypeInfo(
                typeName = "TabButton",
                displayName = "Tab Button",
                description = "Button that switches between tab panels",
                category = "Interactive",
                hierarchyIcon = AllIconsKeys.Actions.Execute,
                toolboxIcon = AllIconsKeys.General.ArrowRight,
                defaultSize = 120f to 40f,
                renderStrategy = RenderStrategy.BUTTON,
                capabilities = setOf(ElementCapability.TEXT_EDITABLE, ElementCapability.INTERACTIVE),
                isToolboxVisible = true,
                defaultFallbackProperties = mapOf(
                    "Text" to PropertyValue.Text("Button")
                )
            ))
            add(ElementTypeInfo(
                typeName = "ToggleButton",
                displayName = "Toggle Button",
                description = "Button that toggles between on/off states",
                category = "Interactive",
                hierarchyIcon = AllIconsKeys.Actions.Execute,
                toolboxIcon = AllIconsKeys.Actions.Checked,
                defaultSize = 120f to 40f,
                renderStrategy = RenderStrategy.BUTTON,
                capabilities = setOf(ElementCapability.TEXT_EDITABLE, ElementCapability.INTERACTIVE),
                isToolboxVisible = true,
                defaultFallbackProperties = mapOf(
                    "Text" to PropertyValue.Text("Button")
                )
            ))

            // ── Input Fields ────────────────────────────────────────────
            add(ElementTypeInfo(
                typeName = "TextField",
                displayName = "Text Field",
                description = "Single-line text input field",
                category = "Input",
                hierarchyIcon = AllIconsKeys.Actions.Edit,
                toolboxIcon = AllIconsKeys.Actions.Edit,
                defaultSize = 200f to 32f,
                renderStrategy = RenderStrategy.TEXT_FIELD,
                capabilities = setOf(ElementCapability.TEXT_EDITABLE, ElementCapability.INTERACTIVE),
                isToolboxVisible = true,
                defaultFallbackProperties = mapOf(
                    "Text" to PropertyValue.Text("")
                )
            ))
            add(ElementTypeInfo(
                typeName = "CompactTextField",
                displayName = "Compact Text Field",
                description = "Compact single-line text input",
                category = "Input",
                hierarchyIcon = AllIconsKeys.Actions.Edit,
                toolboxIcon = AllIconsKeys.Actions.Edit,
                defaultSize = 200f to 32f,
                renderStrategy = RenderStrategy.TEXT_FIELD,
                capabilities = setOf(ElementCapability.TEXT_EDITABLE, ElementCapability.INTERACTIVE),
                isToolboxVisible = true,
                defaultFallbackProperties = mapOf(
                    "Text" to PropertyValue.Text("")
                )
            ))
            add(ElementTypeInfo(
                typeName = "MultilineTextField",
                displayName = "Multiline Text Field",
                description = "Multi-line text input area",
                category = "Input",
                hierarchyIcon = AllIconsKeys.Actions.Edit,
                toolboxIcon = AllIconsKeys.FileTypes.Text,
                defaultSize = 250f to 100f,
                renderStrategy = RenderStrategy.MULTILINE_TEXT_FIELD,
                capabilities = setOf(ElementCapability.TEXT_EDITABLE, ElementCapability.INTERACTIVE),
                isToolboxVisible = true
            ))
            add(ElementTypeInfo(
                typeName = "NumberField",
                displayName = "Number Field",
                description = "Numeric input field with validation",
                category = "Input",
                hierarchyIcon = AllIconsKeys.Actions.Edit,
                toolboxIcon = AllIconsKeys.Debugger.Db_primitive,
                defaultSize = 100f to 32f,
                renderStrategy = RenderStrategy.NUMBER_FIELD,
                capabilities = setOf(ElementCapability.INTERACTIVE),
                isToolboxVisible = true
            ))

            // ── Sliders ─────────────────────────────────────────────────
            add(ElementTypeInfo(
                typeName = "Slider",
                displayName = "Slider",
                description = "Draggable slider for selecting a numeric value",
                category = "Slider",
                hierarchyIcon = AllIconsKeys.Actions.Execute,
                toolboxIcon = AllIconsKeys.General.ArrowRight,
                defaultSize = 200f to 24f,
                renderStrategy = RenderStrategy.SLIDER,
                capabilities = setOf(ElementCapability.INTERACTIVE),
                isToolboxVisible = true,
                defaultFallbackProperties = mapOf(
                    "Value" to PropertyValue.Number(0.5)
                )
            ))
            add(ElementTypeInfo(
                typeName = "SliderNumberField",
                displayName = "Slider Number Field",
                description = "Integer input with slider control",
                category = "Slider",
                hierarchyIcon = AllIconsKeys.Actions.Execute,
                toolboxIcon = AllIconsKeys.General.ArrowRight,
                defaultSize = 200f to 24f,
                renderStrategy = RenderStrategy.SLIDER,
                capabilities = setOf(ElementCapability.INTERACTIVE, ElementCapability.HAS_NUMBER_FIELD),
                isToolboxVisible = true,
                defaultFallbackProperties = mapOf(
                    "Value" to PropertyValue.Number(0.5)
                )
            ))
            add(ElementTypeInfo(
                typeName = "FloatSliderNumberField",
                displayName = "Float Slider",
                description = "Decimal number input with slider control",
                category = "Slider",
                hierarchyIcon = AllIconsKeys.Actions.Execute,
                toolboxIcon = AllIconsKeys.General.ArrowRight,
                defaultSize = 200f to 24f,
                renderStrategy = RenderStrategy.SLIDER,
                capabilities = setOf(ElementCapability.INTERACTIVE, ElementCapability.HAS_NUMBER_FIELD),
                isToolboxVisible = true,
                defaultFallbackProperties = mapOf(
                    "Value" to PropertyValue.Number(0.5)
                )
            ))

            // ── Selection Controls ──────────────────────────────────────
            add(ElementTypeInfo(
                typeName = "CheckBox",
                displayName = "Check Box",
                description = "Boolean on/off toggle checkbox",
                category = "Selection",
                hierarchyIcon = AllIconsKeys.Actions.Checked,
                toolboxIcon = AllIconsKeys.Actions.Checked,
                defaultSize = 24f to 24f,
                renderStrategy = RenderStrategy.CHECKBOX,
                capabilities = setOf(ElementCapability.INTERACTIVE),
                isToolboxVisible = true,
                defaultFallbackProperties = mapOf(
                    "Checked" to PropertyValue.Boolean(false)
                )
            ))
            add(ElementTypeInfo(
                typeName = "LabeledCheckBox",
                displayName = "Labeled Check Box",
                description = "Checkbox with an attached text label",
                category = "Selection",
                hierarchyIcon = AllIconsKeys.Actions.Checked,
                toolboxIcon = AllIconsKeys.Actions.Checked,
                defaultSize = 24f to 24f,
                renderStrategy = RenderStrategy.CHECKBOX,
                capabilities = setOf(ElementCapability.INTERACTIVE),
                isToolboxVisible = true,
                defaultFallbackProperties = mapOf(
                    "Checked" to PropertyValue.Boolean(false)
                )
            ))
            add(ElementTypeInfo(
                typeName = "CheckBoxContainer",
                displayName = "Check Box Container",
                description = "Container wrapping a checkbox with content",
                category = "Selection",
                hierarchyIcon = AllIconsKeys.Actions.Checked,
                toolboxIcon = AllIconsKeys.Actions.Checked,
                defaultSize = 24f to 24f,
                renderStrategy = RenderStrategy.CHECKBOX,
                capabilities = setOf(ElementCapability.INTERACTIVE),
                isToolboxVisible = true,
                defaultFallbackProperties = mapOf(
                    "Checked" to PropertyValue.Boolean(false)
                )
            ))
            add(ElementTypeInfo(
                typeName = "DropdownBox",
                displayName = "Dropdown",
                description = "Selection dropdown for choosing from a list",
                category = "Selection",
                hierarchyIcon = AllIconsKeys.General.ChevronDown,
                toolboxIcon = AllIconsKeys.General.ArrowDown,
                defaultSize = 150f to 32f,
                renderStrategy = RenderStrategy.DROPDOWN,
                capabilities = setOf(ElementCapability.INTERACTIVE),
                isToolboxVisible = true
            ))
            add(ElementTypeInfo(
                typeName = "DropdownEntry",
                displayName = "Dropdown Entry",
                description = "Single entry row within a dropdown list",
                category = "Selection",
                hierarchyIcon = AllIconsKeys.General.ChevronDown,
                toolboxIcon = AllIconsKeys.General.ArrowDown,
                defaultSize = 100f to 24f,
                renderStrategy = RenderStrategy.LABEL,
                capabilities = emptySet(),
                isToolboxVisible = true
            ))

            // ── Progress ────────────────────────────────────────────────
            add(ElementTypeInfo(
                typeName = "ProgressBar",
                displayName = "Progress Bar",
                description = "Horizontal bar showing completion progress",
                category = "Progress",
                hierarchyIcon = AllIconsKeys.Actions.Execute,
                toolboxIcon = AllIconsKeys.General.ArrowRight,
                defaultSize = 200f to 20f,
                renderStrategy = RenderStrategy.PROGRESS_BAR,
                capabilities = emptySet(),
                isToolboxVisible = true,
                defaultFallbackProperties = mapOf(
                    "Value" to PropertyValue.Number(0.5)
                )
            ))
            add(ElementTypeInfo(
                typeName = "CircularProgressBar",
                displayName = "Circular Progress Bar",
                description = "Circular/radial progress indicator",
                category = "Progress",
                hierarchyIcon = AllIconsKeys.Actions.Execute,
                toolboxIcon = AllIconsKeys.Actions.Refresh,
                defaultSize = 200f to 20f,
                renderStrategy = RenderStrategy.PROGRESS_BAR,
                capabilities = emptySet(),
                isToolboxVisible = true,
                defaultFallbackProperties = mapOf(
                    "Value" to PropertyValue.Number(0.5)
                )
            ))

            // ── Scrolling & Layout ──────────────────────────────────────
            add(ElementTypeInfo(
                typeName = "ScrollView",
                displayName = "Scroll View",
                description = "Scrollable container for content that exceeds visible area",
                category = "Container",
                hierarchyIcon = AllIconsKeys.Actions.MoveDown,
                toolboxIcon = AllIconsKeys.Actions.MoveDown,
                defaultSize = 300f to 200f,
                renderStrategy = RenderStrategy.SCROLL_VIEW,
                capabilities = setOf(ElementCapability.CONTAINER),
                isToolboxVisible = true
            ))
            add(ElementTypeInfo(
                typeName = "ItemGrid",
                displayName = "Item Grid",
                description = "Grid of inventory-style item slots",
                category = "Container",
                hierarchyIcon = AllIconsKeys.Graph.Grid,
                toolboxIcon = AllIconsKeys.Graph.Grid,
                defaultSize = 304f to 152f,
                renderStrategy = RenderStrategy.ITEM_GRID,
                capabilities = setOf(ElementCapability.CONTAINER),
                isToolboxVisible = true
            ))

            // ── Media & Graphics ────────────────────────────────────────
            add(ElementTypeInfo(
                typeName = "Image",
                displayName = "Image",
                description = "Displays a texture, icon, or graphic",
                category = "Media",
                hierarchyIcon = AllIconsKeys.FileTypes.Image,
                toolboxIcon = AllIconsKeys.FileTypes.Image,
                defaultSize = 100f to 100f,
                renderStrategy = RenderStrategy.IMAGE,
                capabilities = emptySet(),
                isToolboxVisible = true,
                defaultFallbackProperties = mapOf(
                    "Source" to PropertyValue.ImagePath(""),
                    "Stretch" to PropertyValue.Text("Fill", quoted = false)
                )
            ))
            add(ElementTypeInfo(
                typeName = "BackgroundImage",
                displayName = "Background Image",
                description = "Full-area background image layer",
                category = "Media",
                hierarchyIcon = AllIconsKeys.FileTypes.Image,
                toolboxIcon = AllIconsKeys.FileTypes.Image,
                defaultSize = 100f to 100f,
                renderStrategy = RenderStrategy.IMAGE,
                capabilities = emptySet(),
                isToolboxVisible = true,
                defaultFallbackProperties = mapOf(
                    "Image" to PropertyValue.ImagePath(""),
                    "Stretch" to PropertyValue.Text("Fill", quoted = false)
                )
            ))
            add(ElementTypeInfo(
                typeName = "SceneBlur",
                displayName = "Scene Blur",
                description = "Blurs the 3D scene behind the UI",
                category = "Media",
                hierarchyIcon = AllIconsKeys.FileTypes.Image,
                toolboxIcon = AllIconsKeys.FileTypes.Image,
                defaultSize = 100f to 50f,
                renderStrategy = RenderStrategy.SCENE_BLUR,
                capabilities = emptySet(),
                isToolboxVisible = true
            ))

            // ── 3D Previews ─────────────────────────────────────────────
            add(ElementTypeInfo(
                typeName = "CharacterPreviewComponent",
                displayName = "Character Preview",
                description = "3D character model preview viewport",
                category = "Preview",
                hierarchyIcon = AllIconsKeys.FileTypes.Image,
                toolboxIcon = AllIconsKeys.Debugger.Db_watch,
                defaultSize = 100f to 50f,
                renderStrategy = RenderStrategy.CHARACTER_PREVIEW,
                capabilities = emptySet(),
                isToolboxVisible = true
            ))
            add(ElementTypeInfo(
                typeName = "PlayerPreviewComponent",
                displayName = "Player Preview",
                description = "3D player model preview viewport",
                category = "Preview",
                hierarchyIcon = AllIconsKeys.FileTypes.Image,
                toolboxIcon = AllIconsKeys.Debugger.Db_watch,
                defaultSize = 100f to 50f,
                renderStrategy = RenderStrategy.PLAYER_PREVIEW,
                capabilities = emptySet(),
                isToolboxVisible = true
            ))
            add(ElementTypeInfo(
                typeName = "ItemPreviewComponent",
                displayName = "Item Preview",
                description = "3D item model or icon preview",
                category = "Preview",
                hierarchyIcon = AllIconsKeys.FileTypes.Image,
                toolboxIcon = AllIconsKeys.Nodes.Package,
                defaultSize = 100f to 50f,
                renderStrategy = RenderStrategy.ITEM_PREVIEW,
                capabilities = emptySet(),
                isToolboxVisible = true
            ))
            add(ElementTypeInfo(
                typeName = "BlockSelector",
                displayName = "Block Selector",
                description = "Block type selector with 3D preview",
                category = "Preview",
                hierarchyIcon = AllIconsKeys.FileTypes.Image,
                toolboxIcon = AllIconsKeys.Nodes.Package,
                defaultSize = 304f to 152f,
                renderStrategy = RenderStrategy.BLOCK_SELECTOR,
                capabilities = emptySet(),
                isToolboxVisible = true
            ))

            // ── Navigation ──────────────────────────────────────────────
            add(ElementTypeInfo(
                typeName = "TabPanel",
                displayName = "Tab Panel",
                description = "Tabbed container that switches between child panels",
                category = "Navigation",
                hierarchyIcon = AllIconsKeys.Nodes.Folder,
                toolboxIcon = AllIconsKeys.General.ArrowRight,
                defaultSize = 300f to 200f,
                renderStrategy = RenderStrategy.TAB_PANEL,
                capabilities = setOf(ElementCapability.CONTAINER),
                isToolboxVisible = true
            ))
            add(ElementTypeInfo(
                typeName = "Tooltip",
                displayName = "Tooltip",
                description = "Hover-triggered overlay displaying contextual information",
                category = "Navigation",
                hierarchyIcon = AllIconsKeys.General.Information,
                toolboxIcon = AllIconsKeys.General.Information,
                defaultSize = 150f to 50f,
                renderStrategy = RenderStrategy.TOOLTIP,
                capabilities = setOf(ElementCapability.CONTAINER),
                isToolboxVisible = true
            ))

            // ── Color Controls ────────────────────────────────────────────
            add(ElementTypeInfo(
                typeName = "ColorPicker",
                displayName = "Color Picker",
                description = "Inline color selection control",
                category = "Input",
                hierarchyIcon = AllIconsKeys.Actions.Edit,
                toolboxIcon = AllIconsKeys.Actions.Colors,
                defaultSize = 200f to 200f,
                renderStrategy = RenderStrategy.GROUP,
                capabilities = setOf(ElementCapability.INTERACTIVE),
                isToolboxVisible = true
            ))
            add(ElementTypeInfo(
                typeName = "ColorPickerDropdownBox",
                displayName = "Color Picker Dropdown",
                description = "Dropdown that opens a color picker panel",
                category = "Input",
                hierarchyIcon = AllIconsKeys.Actions.Edit,
                toolboxIcon = AllIconsKeys.Actions.Colors,
                defaultSize = 150f to 32f,
                renderStrategy = RenderStrategy.DROPDOWN,
                capabilities = setOf(ElementCapability.INTERACTIVE),
                isToolboxVisible = true
            ))

            // ── Additional Slider ────────────────────────────────────────
            add(ElementTypeInfo(
                typeName = "FloatSlider",
                displayName = "Float Slider (Simple)",
                description = "Decimal slider without number field",
                category = "Slider",
                hierarchyIcon = AllIconsKeys.Actions.Execute,
                toolboxIcon = AllIconsKeys.General.ArrowRight,
                defaultSize = 200f to 24f,
                renderStrategy = RenderStrategy.SLIDER,
                capabilities = setOf(ElementCapability.INTERACTIVE),
                isToolboxVisible = true,
                defaultFallbackProperties = mapOf(
                    "Value" to PropertyValue.Number(0.5)
                )
            ))

            // ── Additional Media ─────────────────────────────────────────
            add(ElementTypeInfo(
                typeName = "Sprite",
                displayName = "Sprite",
                description = "Animated sprite or sprite-sheet image",
                category = "Media",
                hierarchyIcon = AllIconsKeys.FileTypes.Image,
                toolboxIcon = AllIconsKeys.FileTypes.Image,
                defaultSize = 100f to 100f,
                renderStrategy = RenderStrategy.IMAGE,
                capabilities = emptySet(),
                isToolboxVisible = true,
                defaultFallbackProperties = mapOf(
                    "Source" to PropertyValue.ImagePath("")
                )
            ))

            // ── Hytale Aliases (hidden — parser maps these to canonical types) ──
            add(ElementTypeInfo(
                typeName = "TextButton",
                displayName = "Text Button",
                description = "Hytale internal name for Button",
                category = "Interactive",
                hierarchyIcon = AllIconsKeys.Actions.Execute,
                toolboxIcon = AllIconsKeys.Actions.Execute,
                defaultSize = 120f to 40f,
                renderStrategy = RenderStrategy.BUTTON,
                capabilities = setOf(ElementCapability.TEXT_EDITABLE, ElementCapability.INTERACTIVE),
                isToolboxVisible = false
            ))
            add(ElementTypeInfo(
                typeName = "AssetImage",
                displayName = "Asset Image",
                description = "Hytale internal name for Image",
                category = "Media",
                hierarchyIcon = AllIconsKeys.FileTypes.Image,
                toolboxIcon = AllIconsKeys.FileTypes.Image,
                defaultSize = 100f to 100f,
                renderStrategy = RenderStrategy.IMAGE,
                capabilities = emptySet(),
                isToolboxVisible = false
            ))
            add(ElementTypeInfo(
                typeName = "TabNavigation",
                displayName = "Tab Navigation",
                description = "Hytale internal name for Tab Panel",
                category = "Navigation",
                hierarchyIcon = AllIconsKeys.Nodes.Folder,
                toolboxIcon = AllIconsKeys.General.ArrowRight,
                defaultSize = 300f to 200f,
                renderStrategy = RenderStrategy.TAB_PANEL,
                capabilities = setOf(ElementCapability.CONTAINER),
                isToolboxVisible = false
            ))

            // ── Internal/Parser Types ───────────────────────────────────
            add(ElementTypeInfo(
                typeName = "_VariableRefElement",
                displayName = "Variable Ref",
                description = "Internal variable reference placeholder",
                category = "Internal",
                hierarchyIcon = AllIconsKeys.Nodes.Plugin,
                toolboxIcon = AllIconsKeys.Nodes.Variable,
                defaultSize = 100f to 50f,
                renderStrategy = RenderStrategy.UNKNOWN,
                capabilities = emptySet(),
                isToolboxVisible = false
            ))
            add(ElementTypeInfo(
                typeName = "_StylePrefixedElement",
                displayName = "Style Prefix",
                description = "Internal style-prefixed element definition",
                category = "Internal",
                hierarchyIcon = AllIconsKeys.Nodes.Plugin,
                toolboxIcon = AllIconsKeys.FileTypes.Css,
                defaultSize = 100f to 50f,
                renderStrategy = RenderStrategy.UNKNOWN,
                capabilities = emptySet(),
                isToolboxVisible = false
            ))
            add(ElementTypeInfo(
                typeName = "_IdOnlyBlock",
                displayName = "ID Block",
                description = "Internal ID-only block (no visual)",
                category = "Internal",
                hierarchyIcon = AllIconsKeys.Nodes.Plugin,
                toolboxIcon = AllIconsKeys.Nodes.Tag,
                defaultSize = 100f to 50f,
                renderStrategy = RenderStrategy.UNKNOWN,
                capabilities = emptySet(),
                isToolboxVisible = false
            ))
        }

        entries = all
        byName = all.associateBy { it.typeName }
    }

    /** Default info for unknown element types. */
    private val UNKNOWN = ElementTypeInfo(
        typeName = "",
        displayName = "",
        description = null,
        category = "Unknown",
        hierarchyIcon = AllIconsKeys.Nodes.Plugin,
        toolboxIcon = AllIconsKeys.Nodes.Folder,
        defaultSize = 100f to 50f,
        renderStrategy = RenderStrategy.UNKNOWN,
        capabilities = emptySet(),
        isToolboxVisible = true
    )

    /**
     * Get type info by name. Returns null for unknown types.
     */
    operator fun get(typeName: String): ElementTypeInfo? = byName[typeName]

    /**
     * Get type info, falling back to a default for unknown types.
     * The fallback's [ElementTypeInfo.typeName] and [ElementTypeInfo.displayName]
     * are set to the queried name.
     */
    fun getOrDefault(typeName: String): ElementTypeInfo {
        return byName[typeName] ?: UNKNOWN.copy(
            typeName = typeName,
            displayName = ElementDisplayNames.splitPascalCase(typeName)
        )
    }

    /** Check if a type is registered. */
    fun isKnown(typeName: String): Boolean = typeName in byName

    /** All registered type names. */
    val typeNames: Set<String> get() = byName.keys

    /** All types visible in the element toolbox. */
    val toolboxTypes: List<ElementTypeInfo> by lazy {
        entries.filter { it.isToolboxVisible }
    }
}

/**
 * Utility for generating display names from PascalCase type names.
 */
internal object ElementDisplayNames {
    /**
     * Split a PascalCase or camelCase string into space-separated words.
     * e.g. "MultilineTextField" -> "Multiline Text Field"
     */
    fun splitPascalCase(name: String): String {
        val cleaned = name.trimStart('_')
        if (cleaned.isEmpty()) return name

        return buildString {
            for ((i, ch) in cleaned.withIndex()) {
                if (i > 0 && ch.isUpperCase() && cleaned[i - 1].isLowerCase()) {
                    append(' ')
                }
                append(ch)
            }
        }
    }
}
