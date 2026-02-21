package com.hyve.ui.schema

import com.hyve.ui.core.domain.anchor.AnchorDimension
import com.hyve.ui.core.domain.anchor.AnchorValue
import com.hyve.ui.core.domain.properties.PropertyValue
import com.hyve.ui.core.id.ElementType
import com.hyve.ui.core.id.PropertyName
import com.hyve.ui.schema.discovery.DiscoveredElement
import com.hyve.ui.schema.discovery.DiscoveredProperty
import com.hyve.ui.schema.discovery.DiscoveryResult
import com.hyve.ui.schema.discovery.TupleFieldInfo
import com.hyve.ui.schema.discovery.TupleFieldResult
import com.intellij.openapi.diagnostic.Logger
import java.io.File

/**
 * Runtime schema registry that loads discovered schema from JSON.
 *
 * This registry provides:
 * - Loading schema from discovery tool output
 * - Querying available properties for element types
 * - Providing default values for property types
 * - Categorizing properties for the property inspector
 *
 * Usage:
 * ```kotlin
 * val registry = RuntimeSchemaRegistry.loadFromFile(File("schema.json"))
 * val elementInfo = registry.getElementInfo("Slider")
 * val validProperties = registry.getPropertiesForElement("Slider")
 * ```
 */
class RuntimeSchemaRegistry private constructor(
    private val discoveryResult: DiscoveryResult?,
    private val elementMap: Map<String, RuntimeElementSchema>
) {
    /**
     * Check if the registry has loaded data
     */
    val isLoaded: Boolean get() = discoveryResult != null

    /**
     * Get all known element types
     */
    fun getAllElementTypes(): List<String> = elementMap.keys.toList()

    /**
     * Get schema information for an element type
     */
    fun getElementSchema(typeName: String): RuntimeElementSchema? = elementMap[typeName]

    /**
     * Get schema information for an element type
     */
    fun getElementSchema(type: ElementType): RuntimeElementSchema? = getElementSchema(type.value)

    /**
     * Get all properties valid for an element type
     */
    fun getPropertiesForElement(typeName: String): List<RuntimePropertySchema> {
        return elementMap[typeName]?.properties ?: emptyList()
    }

    /**
     * Get properties for an element that aren't already set
     */
    fun getUnsetProperties(typeName: String, existingProperties: Set<String>): List<RuntimePropertySchema> {
        return getPropertiesForElement(typeName).filter { it.name !in existingProperties }
    }

    /**
     * Check if a property is known for an element type
     */
    fun isKnownProperty(typeName: String, propertyName: String): Boolean {
        return elementMap[typeName]?.getProperty(propertyName) != null
    }

    /**
     * Get the inferred type for a property
     */
    fun getPropertyType(typeName: String, propertyName: String): PropertyType? {
        return elementMap[typeName]?.getProperty(propertyName)?.type
    }

    /**
     * Get metadata about the discovery (number of files, etc.)
     */
    fun getDiscoveryMetadata(): DiscoveryMetadata? {
        return discoveryResult?.let {
            DiscoveryMetadata(
                version = it.version,
                sourceFiles = it.sourceFiles,
                totalElements = it.totalElements,
                uniqueElementTypes = it.uniqueElementTypes,
                discoveredAt = it.discoveredAt
            )
        }
    }

    companion object {
        private val LOG = Logger.getInstance(RuntimeSchemaRegistry::class.java)

        /**
         * Create an empty registry (fallback when no schema file is available)
         */
        fun empty(): RuntimeSchemaRegistry = RuntimeSchemaRegistry(null, emptyMap())

        /**
         * Load registry from a JSON file
         */
        fun loadFromFile(file: File): RuntimeSchemaRegistry {
            return try {
                if (!file.exists()) {
                    empty()
                } else {
                    loadFromJson(file.readText())
                }
            } catch (e: Exception) {
                LOG.debug("Failed to load schema from file: ${file.path}", e)
                empty()
            }
        }

        /**
         * Load registry from JSON string
         */
        fun loadFromJson(jsonString: String): RuntimeSchemaRegistry {
            return try {
                // Use DiscoveryResult.fromJson() for manual JSON parsing
                // (DiscoveryResult is not @Serializable, it uses manual JSON building)
                val result = DiscoveryResult.fromJson(jsonString)
                val elementMap = result.elements.associate { element ->
                    element.type to RuntimeElementSchema.fromDiscovered(element)
                }
                RuntimeSchemaRegistry(result, elementMap)
            } catch (e: Exception) {
                LOG.debug("Failed to parse schema JSON", e)
                empty()
            }
        }

        /**
         * Load registry from a DiscoveryResult directly.
         * If [tupleResult] is provided, tuple field info is attached to matching properties.
         */
        fun fromDiscoveryResult(result: DiscoveryResult, tupleResult: TupleFieldResult? = null): RuntimeSchemaRegistry {
            val elementMap = result.elements.associate { element ->
                element.type to RuntimeElementSchema.fromDiscovered(element, tupleResult)
            }
            return RuntimeSchemaRegistry(result, elementMap)
        }

        /**
         * Load registry from a resource file in the plugin classpath
         */
        fun loadFromResource(resourcePath: String = "/schema/curated-schema.json"): RuntimeSchemaRegistry {
            return try {
                val stream = RuntimeSchemaRegistry::class.java.getResourceAsStream(resourcePath)
                if (stream == null) {
                    return empty()
                }
                val jsonString = stream.bufferedReader().use { it.readText() }
                loadFromJson(jsonString)
            } catch (e: Exception) {
                LOG.debug("Failed to load schema from resource: $resourcePath", e)
                empty()
            }
        }
    }

    /**
     * Merge another registry on top of this one.
     * Overlay properties override curated properties for the same element type.
     */
    fun merge(overlay: RuntimeSchemaRegistry): RuntimeSchemaRegistry {
        val mergedMap = this.elementMap.toMutableMap()

        overlay.elementMap.forEach { (type, overlaySchema) ->
            val baseSchema = mergedMap[type]
            if (baseSchema != null) {
                // Property-level merge: overlay properties win, but base-only properties are kept
                val overlayPropNames = overlaySchema.properties.map { it.name }.toSet()
                val baseOnlyProps = baseSchema.properties.filter { it.name !in overlayPropNames }
                val mergedProps = overlaySchema.properties + baseOnlyProps
                mergedMap[type] = overlaySchema.copy(properties = mergedProps)
            } else {
                mergedMap[type] = overlaySchema
            }
        }

        val mergedResult = if (overlay.discoveryResult != null) {
            overlay.discoveryResult
        } else {
            this.discoveryResult
        }

        return RuntimeSchemaRegistry(mergedResult, mergedMap)
    }
}

/**
 * Runtime schema for a single element type, derived from discovery
 */
data class RuntimeElementSchema(
    val type: String,
    val category: ElementCategory,
    val canHaveChildren: Boolean,
    val occurrences: Int,
    val properties: List<RuntimePropertySchema>
) {
    private val propertyMap: Map<String, RuntimePropertySchema> by lazy {
        properties.associateBy { it.name }
    }

    /**
     * Get a property schema by name
     */
    fun getProperty(name: String): RuntimePropertySchema? = propertyMap[name]

    /**
     * Get properties grouped by category
     */
    fun getPropertiesByCategory(): Map<PropertyCategory, List<RuntimePropertySchema>> {
        return properties.groupBy { it.category }
    }

    companion object {
        fun fromDiscovered(element: DiscoveredElement, tupleResult: TupleFieldResult? = null): RuntimeElementSchema {
            return RuntimeElementSchema(
                type = element.type,
                category = parseCategory(element.category),
                canHaveChildren = element.canHaveChildren,
                occurrences = element.occurrences,
                properties = element.properties.map { prop ->
                    val tupleFields = if (tupleResult != null && prop.type.uppercase() in setOf("TUPLE", "ANY")) {
                        tupleResult.fieldsByProperty[prop.name] ?: emptyList()
                    } else {
                        prop.tupleFields
                    }
                    RuntimePropertySchema.fromDiscovered(prop, tupleFields)
                }
            )
        }

        private fun parseCategory(categoryStr: String): ElementCategory {
            return try {
                ElementCategory.valueOf(categoryStr)
            } catch (_: Exception) {
                ElementCategory.OTHER
            }
        }
    }
}

/**
 * Runtime schema for a single property, derived from discovery
 */
data class RuntimePropertySchema(
    val name: String,
    val type: PropertyType,
    val category: PropertyCategory,
    val required: Boolean,
    val occurrences: Int,
    val observedValues: List<String>,
    val tupleFields: List<TupleFieldInfo> = emptyList()
) {
    /**
     * Get a default value for this property type
     */
    fun getDefaultValue(): PropertyValue {
        return when (type) {
            PropertyType.TEXT -> {
                // Use first observed value if available, otherwise empty string
                val defaultText = observedValues.firstOrNull() ?: ""
                PropertyValue.Text(defaultText)
            }
            PropertyType.NUMBER -> {
                // Try to parse first observed value, otherwise default to 0
                val defaultNum = observedValues.firstOrNull()?.toDoubleOrNull() ?: 0.0
                PropertyValue.Number(defaultNum)
            }
            PropertyType.PERCENT -> {
                PropertyValue.Percent(0.5) // Default 50%
            }
            PropertyType.BOOLEAN -> {
                PropertyValue.Boolean(false)
            }
            PropertyType.COLOR -> {
                // Try to find a color in observed values, otherwise white
                val raw = observedValues.firstOrNull { it.startsWith("#") } ?: "#ffffff"
                // Parse alpha if present, e.g. "#0e1219(0.95)" → hex="#0e1219", alpha=0.95
                val parenIdx = raw.indexOf('(')
                if (parenIdx > 0 && raw.endsWith(")")) {
                    val hex = raw.substring(0, parenIdx)
                    val alpha = raw.substring(parenIdx + 1, raw.length - 1).toFloatOrNull()
                    try {
                        PropertyValue.Color(hex, alpha)
                    } catch (_: IllegalArgumentException) {
                        PropertyValue.Color("#ffffff")
                    }
                } else {
                    try {
                        PropertyValue.Color(raw)
                    } catch (_: IllegalArgumentException) {
                        PropertyValue.Color("#ffffff")
                    }
                }
            }
            PropertyType.ANCHOR -> {
                PropertyValue.Anchor(
                    AnchorValue(
                        left = AnchorDimension.Absolute(0f),
                        top = AnchorDimension.Absolute(0f),
                        width = AnchorDimension.Absolute(100f),
                        height = AnchorDimension.Absolute(50f)
                    )
                )
            }
            PropertyType.STYLE -> {
                // Can't easily create a default style reference
                PropertyValue.Text("@DefaultStyle")
            }
            PropertyType.TUPLE -> {
                PropertyValue.Tuple(emptyMap())
            }
            PropertyType.LIST -> {
                PropertyValue.List(emptyList())
            }
            PropertyType.IMAGE_PATH -> {
                PropertyValue.ImagePath("")
            }
            PropertyType.FONT_PATH -> {
                PropertyValue.FontPath("")
            }
            PropertyType.ANY -> {
                // For ANY type, try to infer from observed values
                val firstValue = observedValues.firstOrNull()
                when {
                    firstValue == null -> PropertyValue.Text("")
                    // Check if it's a number (e.g., "1.0", "8.0")
                    firstValue.toDoubleOrNull() != null -> {
                        PropertyValue.Number(firstValue.toDouble())
                    }
                    // Check if it's a boolean
                    firstValue.equals("true", ignoreCase = true) -> PropertyValue.Boolean(true)
                    firstValue.equals("false", ignoreCase = true) -> PropertyValue.Boolean(false)
                    // Check if it's a color (e.g., "#ffffff", "#000000(0.5)")
                    firstValue.startsWith("#") -> PropertyValue.Color(firstValue)
                    // Otherwise treat as text
                    else -> PropertyValue.Text(firstValue)
                }
            }
        }
    }

    companion object {
        fun fromDiscovered(property: DiscoveredProperty, tupleFields: List<TupleFieldInfo> = emptyList()): RuntimePropertySchema {
            return RuntimePropertySchema(
                name = property.name,
                type = parseType(property.type),
                category = inferCategory(property.name),
                required = property.required,
                occurrences = property.occurrences,
                observedValues = property.observedValues,
                tupleFields = tupleFields.ifEmpty { property.tupleFields }
            )
        }

        private fun parseType(typeStr: String): PropertyType {
            return when (typeStr.uppercase()) {
                "TEXT" -> PropertyType.TEXT
                "NUMBER" -> PropertyType.NUMBER
                "PERCENT" -> PropertyType.PERCENT
                "BOOLEAN" -> PropertyType.BOOLEAN
                "COLOR" -> PropertyType.COLOR
                "ANCHOR" -> PropertyType.ANCHOR
                "STYLE" -> PropertyType.STYLE
                "TUPLE" -> PropertyType.TUPLE
                "LIST" -> PropertyType.LIST
                "IMAGE_PATH" -> PropertyType.IMAGE_PATH
                "FONT_PATH" -> PropertyType.FONT_PATH
                "ANY" -> PropertyType.ANY
                "UNKNOWN" -> PropertyType.ANY
                else -> PropertyType.ANY
            }
        }

        private fun inferCategory(propertyName: String): PropertyCategory {
            return when {
                // Layout properties
                propertyName in setOf("Anchor", "Position", "Size", "Width", "Height",
                    "Left", "Top", "Right", "Bottom", "Margin", "Padding",
                    "MinWidth", "MinHeight", "MaxWidth", "MaxHeight") -> PropertyCategory.LAYOUT

                // Appearance properties
                propertyName in setOf("Background", "Color", "ForegroundColor", "BackgroundColor",
                    "BorderColor", "BorderWidth", "BorderRadius", "Opacity", "Alpha",
                    "TrackColor", "FillColor", "HandleColor", "SelectionColor") -> PropertyCategory.APPEARANCE

                // Text properties
                propertyName in setOf("Text", "FontSize", "FontFamily", "Font", "FontPath",
                    "TextAlign", "TextColor", "LineHeight", "LetterSpacing",
                    "RenderBold", "RenderItalic", "Truncate", "WordWrap") -> PropertyCategory.TEXT

                // Value/Data properties
                propertyName in setOf("Value", "MinValue", "MaxValue", "Step", "DefaultValue",
                    "Checked", "Selected", "Items", "SelectedIndex", "Source") -> PropertyCategory.DATA

                // Interaction properties
                propertyName in setOf("OnClick", "OnChange", "OnHover", "OnFocus", "OnBlur",
                    "OnSubmit", "OnSelect", "Enabled", "Focusable", "Clickable") -> PropertyCategory.INTERACTION

                // Visual state properties
                propertyName in setOf("Visible", "Hidden", "Style") -> PropertyCategory.STATE

                // Element-specific settings
                propertyName in setOf("Orientation", "LayoutMode", "ScrollDirection",
                    "HandleSize", "Placeholder", "Mask", "Stretch") -> PropertyCategory.SETTINGS

                else -> PropertyCategory.OTHER
            }
        }
    }
}

/**
 * Categories for organizing properties in the inspector
 */
enum class PropertyCategory(val displayName: String, val order: Int) {
    LAYOUT("Layout", 0),
    APPEARANCE("Appearance", 1),
    TEXT("Text", 2),
    DATA("Data", 3),
    INTERACTION("Interaction", 4),
    STATE("State", 5),
    SETTINGS("Settings", 6),
    OTHER("Other", 7);

    override fun toString(): String = displayName
}

/**
 * Metadata about the schema discovery
 */
data class DiscoveryMetadata(
    val version: String,
    val sourceFiles: Int,
    val totalElements: Int,
    val uniqueElementTypes: Int,
    val discoveredAt: String
)
