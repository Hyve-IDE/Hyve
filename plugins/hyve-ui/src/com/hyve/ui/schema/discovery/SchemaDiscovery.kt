package com.hyve.ui.schema.discovery

import com.hyve.ui.core.domain.UIDocument
import com.hyve.ui.core.domain.elements.UIElement
import com.hyve.ui.core.domain.properties.PropertyValue
import com.hyve.ui.core.domain.styles.StyleDefinition
import com.hyve.ui.core.id.ElementType
import com.hyve.ui.core.id.PropertyName
import com.hyve.ui.core.result.Result
import com.hyve.ui.parser.ParseError
import com.hyve.ui.parser.UIParser
import com.hyve.ui.schema.ElementCategory
import com.hyve.ui.schema.PropertyType
import kotlinx.serialization.json.*
import java.io.File
import java.time.Instant

/**
 * Schema Discovery Tool - crawls .ui files and extracts element/property information.
 *
 * Parses all .ui files in a directory, aggregates element types and their properties,
 * infers property types from observed values, and generates a comprehensive schema
 * registry JSON file.
 */
class SchemaDiscovery(
    private val progressCallback: ((String) -> Unit)? = null
) {
    private val elementStats = mutableMapOf<String, ElementStats>()
    private val parseErrors = mutableListOf<ParseErrorInfo>()
    private var filesProcessed = 0
    private var totalFiles = 0

    /**
     * Properties derived from typed style definitions (e.g., `SliderStyle` -> `Slider`).
     * Held separately until all files are processed, then merged only into element types
     * that were also seen as real elements (prevents phantom types from style names).
     *
     * Key: element type name (stripped of "Style" suffix).
     * Value: map of property name -> PropertyStats.
     */
    private val pendingStyleProperties = mutableMapOf<String, MutableMap<String, PropertyStats>>()

    /**
     * Discover schema from all .ui files in a directory
     */
    fun discoverFromDirectory(inputPath: File): DiscoveryResult {
        require(inputPath.exists()) { "Input path does not exist: $inputPath" }
        require(inputPath.isDirectory) { "Input path is not a directory: $inputPath" }

        val uiFiles = inputPath.walkTopDown()
            .filter { it.isFile && it.extension.equals("ui", ignoreCase = true) }
            .toList()

        totalFiles = uiFiles.size
        filesProcessed = 0

        progress("Found $totalFiles .ui files to process")

        for (file in uiFiles) {
            processFile(file)
            filesProcessed++
            if (filesProcessed % 50 == 0 || filesProcessed == totalFiles) {
                progress("Processing $filesProcessed/$totalFiles files...")
            }
        }

        return buildResult()
    }

    /**
     * Discover schema from multiple directories and/or in-memory sources.
     *
     * @param directories Filesystem directories to scan for .ui files
     * @param inMemorySources Pairs of (filename, content) — e.g., extracted from a zip
     */
    fun discoverFromSources(
        directories: List<File> = emptyList(),
        inMemorySources: List<Pair<String, String>> = emptyList()
    ): DiscoveryResult {
        val uiFiles = directories
            .filter { it.exists() && it.isDirectory }
            .flatMap { dir ->
                dir.walkTopDown()
                    .filter { it.isFile && it.extension.equals("ui", ignoreCase = true) }
                    .toList()
            }

        totalFiles = uiFiles.size + inMemorySources.size
        filesProcessed = 0

        progress("Found $totalFiles sources to process (${uiFiles.size} files + ${inMemorySources.size} in-memory)")

        for (file in uiFiles) {
            processFile(file)
            filesProcessed++
            if (filesProcessed % 50 == 0) {
                progress("Processing $filesProcessed/$totalFiles sources...")
            }
        }

        for ((name, content) in inMemorySources) {
            processSource(name, content)
            filesProcessed++
        }

        if (totalFiles > 0) {
            progress("Processing $filesProcessed/$totalFiles sources...")
        }

        return buildResult()
    }

    /**
     * Discover schema from a single .ui file
     */
    fun discoverFromFile(file: File): DiscoveryResult {
        require(file.exists()) { "File does not exist: $file" }
        require(file.isFile) { "Path is not a file: $file" }

        totalFiles = 1
        filesProcessed = 0

        processFile(file)
        filesProcessed = 1

        return buildResult()
    }

    /**
     * Process a single .ui file
     */
    private fun processFile(file: File) {
        try {
            val source = file.readText()
            val parser = UIParser(source)

            when (val result = parser.parse()) {
                is Result.Success -> {
                    processDocument(result.value)
                }
                is Result.Failure -> {
                    for (error in result.error) {
                        parseErrors.add(ParseErrorInfo(
                            file = file.absolutePath,
                            line = error.position.line,
                            column = error.position.column,
                            message = error.message
                        ))
                    }
                }
            }
        } catch (e: Exception) {
            parseErrors.add(ParseErrorInfo(
                file = file.absolutePath,
                line = 0,
                column = 0,
                message = "Exception: ${e.message}"
            ))
        }
    }

    /**
     * Process an in-memory .ui source (e.g., extracted from a zip).
     */
    private fun processSource(name: String, content: String) {
        try {
            val parser = UIParser(content)
            when (val result = parser.parse()) {
                is Result.Success -> {
                    processDocument(result.value)
                }
                is Result.Failure -> {
                    for (error in result.error) {
                        parseErrors.add(ParseErrorInfo(
                            file = name,
                            line = error.position.line,
                            column = error.position.column,
                            message = error.message
                        ))
                    }
                }
            }
        } catch (e: Exception) {
            parseErrors.add(ParseErrorInfo(
                file = name,
                line = 0,
                column = 0,
                message = "Exception: ${e.message}"
            ))
        }
    }

    /**
     * Process a parsed UIDocument
     */
    private fun processDocument(document: UIDocument) {
        // Process root element and all descendants
        document.root.visitDescendants { element ->
            processElement(element)
        }

        // Process typed style definitions (e.g., @Foo = SliderStyle(...))
        for ((_, style) in document.styles) {
            processStyleDefinition(style)
        }
    }

    /**
     * Process a single element and aggregate its stats
     */
    private fun processElement(element: UIElement) {
        val typeName = element.type.value
        val stats = elementStats.getOrPut(typeName) {
            ElementStats(
                type = typeName,
                category = inferCategory(typeName, element),
                canHaveChildren = element.children.isNotEmpty()
            )
        }

        stats.occurrences++

        // Update canHaveChildren if we find children
        if (element.children.isNotEmpty()) {
            stats.canHaveChildren = true
        }

        // Process properties
        for ((propName, propValue) in element.properties.entries()) {
            val propStats = stats.properties.getOrPut(propName.value) {
                PropertyStats(name = propName.value)
            }

            propStats.occurrences++
            propStats.addObservedValue(propValue)
            propStats.updateInferredType(propValue)
        }
    }

    /**
     * Process a typed style definition, extracting its properties as candidates
     * for the corresponding element type.
     *
     * For example, `@Foo = SliderStyle(Handle: "x", HandleWidth: 16)` contributes
     * `Handle` and `HandleWidth` as properties of the `Slider` element type.
     *
     * Properties are held in [pendingStyleProperties] and merged into [elementStats]
     * during [buildResult] — only for element types that were also seen as real elements.
     */
    private fun processStyleDefinition(style: StyleDefinition) {
        val typeName = style.typeName ?: return
        if (!typeName.endsWith("Style")) return

        val elementTypeName = typeName.removeSuffix("Style")
        if (elementTypeName.isEmpty()) return

        val propMap = pendingStyleProperties.getOrPut(elementTypeName) { mutableMapOf() }

        for ((propName, propValue) in style.properties) {
            val propStats = propMap.getOrPut(propName.value) {
                PropertyStats(name = propName.value)
            }
            propStats.occurrences++
            propStats.addObservedValue(propValue)
            propStats.updateInferredType(propValue)
        }
    }

    /**
     * Infer element category based on type name and element structure
     */
    private fun inferCategory(typeName: String, element: UIElement): String {
        return when {
            // Containers
            typeName in listOf("Group", "Panel", "Container", "Frame", "Window", "Dialog", "Root") -> "CONTAINER"
            typeName.contains("Scroll", ignoreCase = true) -> "CONTAINER"
            typeName.contains("List", ignoreCase = true) -> "CONTAINER"
            typeName.contains("Grid", ignoreCase = true) -> "CONTAINER"

            // Text elements
            typeName in listOf("Label", "Text", "TextBlock", "Paragraph") -> "TEXT"
            typeName.contains("Label", ignoreCase = true) -> "TEXT"

            // Interactive elements
            typeName in listOf("Button", "TextButton", "ImageButton", "IconButton") -> "INTERACTIVE"
            typeName.contains("Button", ignoreCase = true) -> "INTERACTIVE"
            typeName.contains("Toggle", ignoreCase = true) -> "INTERACTIVE"
            typeName.contains("Tab", ignoreCase = true) -> "INTERACTIVE"

            // Input elements
            typeName in listOf("TextField", "TextInput", "Input", "NumberField", "SearchBox") -> "INPUT"
            typeName.contains("Field", ignoreCase = true) -> "INPUT"
            typeName.contains("Input", ignoreCase = true) -> "INPUT"
            typeName in listOf("Slider", "CheckBox", "RadioButton", "DropdownBox", "ComboBox") -> "INPUT"
            typeName.contains("Dropdown", ignoreCase = true) -> "INPUT"
            typeName.contains("Selector", ignoreCase = true) -> "INPUT"

            // Media elements
            typeName in listOf("Image", "AssetImage", "Icon", "Sprite", "Video", "Animation") -> "MEDIA"
            typeName.contains("Image", ignoreCase = true) -> "MEDIA"
            typeName.contains("Icon", ignoreCase = true) -> "MEDIA"
            typeName.contains("Asset", ignoreCase = true) -> "MEDIA"

            // Layout elements
            typeName in listOf("Spacer", "Divider", "Separator", "HBox", "VBox", "Stack") -> "LAYOUT"

            // Advanced elements
            typeName in listOf("ProgressBar", "Tooltip", "TabPanel", "TabNavigation", "Canvas") -> "ADVANCED"

            // Elements with children are likely containers
            element.children.isNotEmpty() -> "CONTAINER"

            // Default
            else -> "OTHER"
        }
    }

    /**
     * Merge pending style-derived properties into element stats.
     * Only merges for element types that were also seen as real elements.
     */
    private fun mergeStyleProperties() {
        for ((elementType, styleProps) in pendingStyleProperties) {
            val stats = elementStats[elementType] ?: continue // skip phantom types
            for ((propName, stylePropStats) in styleProps) {
                val existing = stats.properties[propName]
                if (existing != null) {
                    // Merge occurrences and observed values
                    existing.occurrences += stylePropStats.occurrences
                    for (v in stylePropStats.observedValues) {
                        if (existing.observedValues.size < 50) existing.observedValues.add(v)
                    }
                    // Don't clobber the inferred type from real elements
                } else {
                    // New property discovered only in styles
                    stats.properties[propName] = stylePropStats
                }
            }
        }
    }

    /**
     * Build the final discovery result
     */
    private fun buildResult(): DiscoveryResult {
        mergeStyleProperties()

        val elements = elementStats.values
            .sortedByDescending { it.occurrences }
            .map { stats ->
                DiscoveredElement(
                    type = stats.type,
                    category = stats.category,
                    canHaveChildren = stats.canHaveChildren,
                    occurrences = stats.occurrences,
                    properties = stats.properties.values
                        .sortedByDescending { it.occurrences }
                        .map { propStats ->
                            DiscoveredProperty(
                                name = propStats.name,
                                type = resolveInferredType(propStats.name, propStats.inferredType),
                                required = false, // We can't determine required from discovery
                                observedValues = propStats.observedValues
                                    .take(10) // Limit to 10 sample values
                                    .toList(),
                                occurrences = propStats.occurrences
                            )
                        }
                )
            }

        return DiscoveryResult(
            version = "1.0.0",
            discoveredAt = Instant.now().toString(),
            sourceFiles = filesProcessed,
            totalElements = elementStats.values.sumOf { it.occurrences },
            uniqueElementTypes = elementStats.size,
            totalProperties = elementStats.values.sumOf { it.properties.size },
            parseErrors = parseErrors.size,
            elements = elements,
            errors = parseErrors.take(100) // Limit errors in output
        )
    }

    /**
     * Apply name-based type overrides for properties where the parser's
     * structural type doesn't match the semantic type. For example, the parser
     * returns Tuple for anchor values that contain variable references, but
     * the property is still semantically an Anchor.
     */
    private fun resolveInferredType(name: String, inferredType: String): String {
        // Anchor properties parsed as TUPLE (due to variable refs in values)
        // or left as UNKNOWN/ANY should be corrected to ANCHOR
        if (name == "Anchor" && inferredType in setOf("TUPLE", "ANY", "UNKNOWN")) {
            return "ANCHOR"
        }
        // Padding has the same tuple-based syntax as Anchor
        if (name == "Padding" && inferredType in setOf("TUPLE", "ANY", "UNKNOWN")) {
            return "ANCHOR"
        }
        return inferredType
    }

    private fun progress(message: String) {
        progressCallback?.invoke(message)
    }
}

/**
 * Mutable stats collected during discovery for an element type
 */
private class ElementStats(
    val type: String,
    val category: String,
    var canHaveChildren: Boolean,
    var occurrences: Int = 0,
    val properties: MutableMap<String, PropertyStats> = mutableMapOf()
)

/**
 * Mutable stats collected during discovery for a property
 */
private class PropertyStats(
    val name: String,
    var occurrences: Int = 0,
    var inferredType: String = "UNKNOWN",
    val observedValues: MutableSet<String> = mutableSetOf()
) {
    fun addObservedValue(value: PropertyValue) {
        // Only add string representation for certain types to avoid huge lists
        val strValue = when (value) {
            is PropertyValue.Text -> value.value
            is PropertyValue.Number -> value.value.toString()
            is PropertyValue.Percent -> value.toString()
            is PropertyValue.Boolean -> value.value.toString()
            is PropertyValue.Color -> value.toString()
            else -> null // Don't track complex values like tuples/anchors
        }
        if (strValue != null && observedValues.size < 50) {
            observedValues.add(strValue)
        }
    }

    fun updateInferredType(value: PropertyValue) {
        val newType = when (value) {
            is PropertyValue.Text -> "TEXT"
            is PropertyValue.Number -> "NUMBER"
            is PropertyValue.Percent -> "PERCENT"
            is PropertyValue.Boolean -> "BOOLEAN"
            is PropertyValue.Color -> "COLOR"
            is PropertyValue.Anchor -> "ANCHOR"
            is PropertyValue.Tuple -> "TUPLE"
            is PropertyValue.List -> "LIST"
            is PropertyValue.ImagePath -> "IMAGE_PATH"
            is PropertyValue.FontPath -> "FONT_PATH"
            // Reference/fill-mode types don't define the property's structural type.
            // A property can hold a variable ref AND be an Anchor — the ref is how
            // it's filled, not what type the property is. Skip these so they don't
            // clobber the inferred base type.
            is PropertyValue.Style,
            is PropertyValue.VariableRef,
            is PropertyValue.LocalizedText,
            is PropertyValue.Spread,
            is PropertyValue.Expression -> return
            is PropertyValue.Unknown -> return
            is PropertyValue.Null -> return
        }

        // Type inference rules:
        // - If current is UNKNOWN, use the new type
        // - If types match, keep it
        // - If types differ, prefer more specific (e.g., ANCHOR over TUPLE)
        // - If truly mixed, use ANY
        if (inferredType == "UNKNOWN") {
            inferredType = newType
        } else if (inferredType != newType) {
            inferredType = when {
                // TUPLE and ANCHOR are the same syntax — prefer ANCHOR
                setOf(inferredType, newType) == setOf("TUPLE", "ANCHOR") -> "ANCHOR"
                // NUMBER+TEXT mix (e.g., enums that accept both)
                setOf(inferredType, newType) == setOf("NUMBER", "TEXT") -> "ANY"
                else -> "ANY"
            }
        }
    }
}

// ============================================================================
// Serializable output model
// ============================================================================

/**
 * Complete discovery result that can be serialized to JSON.
 *
 * Note: Uses manual JSON building because the kotlinx.serialization compiler
 * plugin may not be applied during IDE builds.
 */
data class DiscoveryResult(
    val version: String,
    val discoveredAt: String,
    val sourceFiles: Int,
    val totalElements: Int,
    val uniqueElementTypes: Int,
    val totalProperties: Int,
    val parseErrors: Int,
    val elements: List<DiscoveredElement>,
    val errors: List<ParseErrorInfo> = emptyList(),
    /** Lightweight corpus fingerprint: "fileCount:totalBytes:newestModifiedMs". Empty = unchecked. */
    val corpusFingerprint: String = ""
) {
    /**
     * Export to JSON string using manual JSON building
     */
    fun toJson(prettyPrint: Boolean = true): String {
        val jsonObject = buildJsonObject {
            put("version", version)
            put("discoveredAt", discoveredAt)
            put("sourceFiles", sourceFiles)
            put("totalElements", totalElements)
            put("uniqueElementTypes", uniqueElementTypes)
            put("totalProperties", totalProperties)
            put("parseErrors", parseErrors)
            if (corpusFingerprint.isNotEmpty()) {
                put("corpusFingerprint", corpusFingerprint)
            }
            putJsonArray("elements") {
                elements.forEach { element ->
                    add(element.toJsonObject())
                }
            }
            putJsonArray("errors") {
                errors.forEach { error ->
                    add(error.toJsonObject())
                }
            }
        }
        val jsonConfig = if (prettyPrint) jsonPretty else jsonCompact
        return jsonConfig.encodeToString(JsonObject.serializer(), jsonObject)
    }

    /**
     * Export to JSON file
     */
    fun toJsonFile(outputPath: File, prettyPrint: Boolean = true) {
        outputPath.writeText(toJson(prettyPrint))
    }

    companion object {
        private val jsonPretty = Json { prettyPrint = true }
        private val jsonCompact = Json { prettyPrint = false }

        /**
         * Load from JSON string using manual parsing
         */
        fun fromJson(jsonString: String): DiscoveryResult {
            val json = Json.parseToJsonElement(jsonString).jsonObject
            return DiscoveryResult(
                version = json["version"]?.jsonPrimitive?.content ?: "1.0.0",
                discoveredAt = json["discoveredAt"]?.jsonPrimitive?.content ?: "",
                sourceFiles = json["sourceFiles"]?.jsonPrimitive?.int ?: 0,
                totalElements = json["totalElements"]?.jsonPrimitive?.int ?: 0,
                uniqueElementTypes = json["uniqueElementTypes"]?.jsonPrimitive?.int ?: 0,
                totalProperties = json["totalProperties"]?.jsonPrimitive?.int ?: 0,
                parseErrors = json["parseErrors"]?.jsonPrimitive?.int ?: 0,
                elements = json["elements"]?.jsonArray?.map { DiscoveredElement.fromJsonObject(it.jsonObject) } ?: emptyList(),
                errors = json["errors"]?.jsonArray?.map { ParseErrorInfo.fromJsonObject(it.jsonObject) } ?: emptyList(),
                corpusFingerprint = json["corpusFingerprint"]?.jsonPrimitive?.content ?: ""
            )
        }

        /**
         * Load from JSON file
         */
        fun fromJsonFile(file: File): DiscoveryResult {
            return fromJson(file.readText())
        }
    }
}

/**
 * Discovered element type with all its properties
 */
data class DiscoveredElement(
    val type: String,
    val category: String,
    val canHaveChildren: Boolean,
    val occurrences: Int,
    val properties: List<DiscoveredProperty>
) {
    fun toJsonObject(): JsonObject = buildJsonObject {
        put("type", type)
        put("category", category)
        put("canHaveChildren", canHaveChildren)
        put("occurrences", occurrences)
        putJsonArray("properties") {
            properties.forEach { prop ->
                add(prop.toJsonObject())
            }
        }
    }

    companion object {
        fun fromJsonObject(json: JsonObject): DiscoveredElement = DiscoveredElement(
            type = json["type"]?.jsonPrimitive?.content ?: "",
            category = json["category"]?.jsonPrimitive?.content ?: "OTHER",
            canHaveChildren = json["canHaveChildren"]?.jsonPrimitive?.boolean ?: false,
            occurrences = json["occurrences"]?.jsonPrimitive?.int ?: 0,
            properties = json["properties"]?.jsonArray?.map { DiscoveredProperty.fromJsonObject(it.jsonObject) } ?: emptyList()
        )
    }
}

/**
 * Discovered property with type inference
 */
data class DiscoveredProperty(
    val name: String,
    val type: String,
    val required: Boolean,
    val observedValues: List<String>,
    val occurrences: Int,
    val tupleFields: List<TupleFieldInfo> = emptyList()
) {
    fun toJsonObject(): JsonObject = buildJsonObject {
        put("name", name)
        put("type", type)
        put("required", required)
        putJsonArray("observedValues") {
            observedValues.forEach { add(it) }
        }
        put("occurrences", occurrences)
        if (tupleFields.isNotEmpty()) {
            putJsonArray("tupleFields") {
                tupleFields.forEach { add(it.toJsonObject()) }
            }
        }
    }

    companion object {
        fun fromJsonObject(json: JsonObject): DiscoveredProperty = DiscoveredProperty(
            name = json["name"]?.jsonPrimitive?.content ?: "",
            type = json["type"]?.jsonPrimitive?.content ?: "ANY",
            required = json["required"]?.jsonPrimitive?.boolean ?: false,
            observedValues = json["observedValues"]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList(),
            occurrences = json["occurrences"]?.jsonPrimitive?.int ?: 0,
            tupleFields = json["tupleFields"]?.jsonArray?.map { TupleFieldInfo.fromJsonObject(it.jsonObject) } ?: emptyList()
        )
    }
}

/**
 * Parse error information
 */
data class ParseErrorInfo(
    val file: String,
    val line: Int,
    val column: Int,
    val message: String
) {
    fun toJsonObject(): JsonObject = buildJsonObject {
        put("file", file)
        put("line", line)
        put("column", column)
        put("message", message)
    }

    companion object {
        fun fromJsonObject(json: JsonObject): ParseErrorInfo = ParseErrorInfo(
            file = json["file"]?.jsonPrimitive?.content ?: "",
            line = json["line"]?.jsonPrimitive?.int ?: 0,
            column = json["column"]?.jsonPrimitive?.int ?: 0,
            message = json["message"]?.jsonPrimitive?.content ?: ""
        )
    }
}
