package com.hyve.ui.core.domain.elements

import com.hyve.ui.core.domain.properties.PropertyMap
import com.hyve.ui.core.domain.properties.PropertyValue
import com.hyve.ui.core.id.ElementId
import com.hyve.ui.core.id.ElementType
import com.hyve.ui.core.id.PropertyName

/**
 * Core domain model for UI elements.
 *
 * Uses composition over inheritance - elements are differentiated by their type
 * and properties, not by class hierarchy. This allows extensible schema discovery
 * without refactoring.
 *
 * All elements share the same structure:
 * - type: What kind of element (Group, Label, Button, etc.)
 * - id: Optional unique identifier
 * - properties: Type-safe property storage
 * - children: Nested elements
 * - metadata: Editor-specific data (not exported to .ui files)
 *
 * Example:
 * ```kotlin
 * val button = UIElement(
 *     type = ElementType("Button"),
 *     id = ElementId("SubmitButton"),
 *     properties = PropertyMap.of(
 *         "Text" to PropertyValue.Text("Submit"),
 *         "Anchor" to PropertyValue.Anchor(AnchorValue.absolute(left = 10f, top = 20f))
 *     )
 * )
 * ```
 */
data class UIElement(
    val type: ElementType,
    val id: ElementId?,
    val properties: PropertyMap,
    val children: List<UIElement> = emptyList(),
    val metadata: ElementMetadata = ElementMetadata()
) {
    /**
     * Get property value by name
     */
    fun getProperty(name: PropertyName): PropertyValue? = properties[name]

    /**
     * Get property value by string name (convenience)
     */
    fun getProperty(name: String): PropertyValue? = properties[name]

    /**
     * Set property value (returns new UIElement, immutable)
     */
    fun setProperty(name: PropertyName, value: PropertyValue): UIElement =
        copy(properties = properties.set(name, value))

    /**
     * Set property value by string name (convenience)
     */
    fun setProperty(name: String, value: PropertyValue): UIElement =
        setProperty(PropertyName(name), value)

    /**
     * Remove property (returns new UIElement, immutable)
     */
    fun removeProperty(name: PropertyName): UIElement =
        copy(properties = properties.remove(name))

    /**
     * Add child element (returns new UIElement, immutable)
     */
    fun addChild(child: UIElement, index: Int = children.size): UIElement {
        val newChildren = children.toMutableList()
        newChildren.add(index, child)
        return copy(children = newChildren)
    }

    /**
     * Remove child element (returns new UIElement, immutable)
     */
    fun removeChild(child: UIElement): UIElement =
        copy(children = children - child)

    /**
     * Remove child element by index (returns new UIElement, immutable)
     */
    fun removeChildAt(index: Int): UIElement {
        require(index in children.indices) { "Index $index out of bounds for children size ${children.size}" }
        return copy(children = children.filterIndexed { i, _ -> i != index })
    }

    /**
     * Replace child element (returns new UIElement, immutable)
     */
    fun replaceChild(oldChild: UIElement, newChild: UIElement): UIElement {
        val index = children.indexOf(oldChild)
        require(index >= 0) { "Child element not found" }
        return replaceChildAt(index, newChild)
    }

    /**
     * Replace child element by index (returns new UIElement, immutable)
     */
    fun replaceChildAt(index: Int, newChild: UIElement): UIElement {
        require(index in children.indices) { "Index $index out of bounds for children size ${children.size}" }
        val newChildren = children.toMutableList()
        newChildren[index] = newChild
        return copy(children = newChildren)
    }

    /**
     * Check if element can have children (based on type)
     * TODO: Move to SchemaRegistry when implemented
     */
    fun canHaveChildren(): Boolean {
        // For now, simple heuristic - containers can have children
        val containerTypes = setOf("Group", "ScrollView", "TabPanel", "Panel")
        return type.value in containerTypes
    }

    /**
     * Find child element by ID (shallow search)
     */
    fun findChildById(id: ElementId): UIElement? =
        children.firstOrNull { it.id == id }

    /**
     * Find descendant element by ID (deep search)
     */
    fun findDescendantById(id: ElementId): UIElement? {
        if (this.id == id) return this
        children.forEach { child ->
            child.findDescendantById(id)?.let { return it }
        }
        return null
    }

    /**
     * Visit all descendants with callback (depth-first)
     */
    fun visitDescendants(visitor: (UIElement) -> Unit) {
        visitor(this)
        children.forEach { it.visitDescendants(visitor) }
    }

    /**
     * Map all descendants (depth-first)
     */
    fun mapDescendants(transform: (UIElement) -> UIElement): UIElement {
        val transformedChildren = children.map { it.mapDescendants(transform) }
        return transform(copy(children = transformedChildren))
    }

    /**
     * Get display name for editor (shows type and ID if present)
     */
    fun displayName(): String = if (id != null) {
        "${type.value} #${id.value}"
    } else {
        type.value
    }

    override fun toString(): String = displayName()

    companion object {
        /**
         * Create empty root element (for new documents)
         */
        fun root(): UIElement = UIElement(
            type = ElementType("Root"),
            id = null,
            properties = PropertyMap.empty()
        )
    }
}

/**
 * Editor-specific metadata for UI elements.
 * Not exported to .ui files - used only within HyUI Studio.
 */
data class ElementMetadata(
    val sourceComponent: ComponentReference? = null,  // For Feature 7 (component library)
    val visible: Boolean = true,                      // For Feature 3 (hierarchy visibility toggle)
    val locked: Boolean = false,                      // For Feature 3 (hierarchy lock toggle)
    val collapsed: Boolean = false,                   // For Feature 3 (hierarchy collapse state)
    val previewItemId: String? = null                 // Design-time item ID for ItemPreviewComponent
)

/**
 * Reference to a component from a component library
 * Format: "ComponentName@1.0.0" from "library-name.hyuilib"
 */
data class ComponentReference(
    val libraryName: String,
    val componentName: String,
    val version: String
) {
    override fun toString(): String = "$componentName@$version from $libraryName"
}
