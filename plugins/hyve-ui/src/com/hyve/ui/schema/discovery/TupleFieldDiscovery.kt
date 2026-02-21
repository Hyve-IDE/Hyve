package com.hyve.ui.schema.discovery

import com.hyve.ui.core.domain.UIDocument
import com.hyve.ui.core.domain.properties.PropertyValue
import com.hyve.ui.core.result.Result
import com.hyve.ui.parser.UIParser
import kotlinx.serialization.json.*
import java.io.File

/**
 * Discovers tuple sub-field vocabularies from .ui files.
 *
 * Crawls all elements and style definitions, recording which sub-field keys
 * appear inside tuple-valued properties (e.g., Style, Background). Results
 * are aggregated globally per property name — the same property name always
 * produces the same candidate field list regardless of which element type
 * it appears on.
 *
 * Usage:
 * ```kotlin
 * val discovery = TupleFieldDiscovery()
 * val result = discovery.discoverFromDirectory(File("path/to/Interface"))
 * // result.fieldsByProperty["Style"] -> [TupleFieldInfo("FontSize", "NUMBER", 42, ...), ...]
 * ```
 */
class TupleFieldDiscovery(
    private val progressCallback: ((String) -> Unit)? = null
) {
    /** Key: property name (e.g., "Style", "Background"). Value: mutable stats per sub-field. */
    private val propertyFields = mutableMapOf<String, MutableMap<String, FieldStats>>()
    private var filesProcessed = 0

    /**
     * Discover tuple fields from all .ui files in a directory.
     */
    fun discoverFromDirectory(inputPath: File): TupleFieldResult {
        require(inputPath.exists()) { "Input path does not exist: $inputPath" }
        require(inputPath.isDirectory) { "Input path is not a directory: $inputPath" }

        val uiFiles = inputPath.walkTopDown()
            .filter { it.isFile && it.extension.equals("ui", ignoreCase = true) }
            .toList()

        progress("TupleFieldDiscovery: Found ${uiFiles.size} .ui files")

        filesProcessed = 0
        for (file in uiFiles) {
            processFile(file)
            filesProcessed++
        }

        return buildResult()
    }

    /**
     * Discover tuple fields from multiple directories and/or in-memory sources.
     *
     * @param directories Filesystem directories to scan for .ui files
     * @param inMemorySources Pairs of (filename, content) — e.g., extracted from a zip
     */
    fun discoverFromSources(
        directories: List<File> = emptyList(),
        inMemorySources: List<Pair<String, String>> = emptyList()
    ): TupleFieldResult {
        val uiFiles = directories
            .filter { it.exists() && it.isDirectory }
            .flatMap { dir ->
                dir.walkTopDown()
                    .filter { it.isFile && it.extension.equals("ui", ignoreCase = true) }
                    .toList()
            }

        progress("TupleFieldDiscovery: Found ${uiFiles.size} files + ${inMemorySources.size} in-memory sources")

        filesProcessed = 0
        for (file in uiFiles) {
            processFile(file)
            filesProcessed++
        }
        for ((_, content) in inMemorySources) {
            processSource(content)
            filesProcessed++
        }

        return buildResult()
    }

    /**
     * Discover tuple fields from already-parsed documents.
     * Useful for avoiding double-parsing when SchemaDiscovery has already parsed the same files.
     */
    fun discoverFromDocuments(documents: List<UIDocument>): TupleFieldResult {
        filesProcessed = 0
        for (doc in documents) {
            processDocument(doc)
            filesProcessed++
        }
        return buildResult()
    }

    private fun processFile(file: File) {
        try {
            val source = file.readText()
            val parser = UIParser(source)
            when (val result = parser.parse()) {
                is Result.Success -> processDocument(result.value)
                is Result.Failure -> { /* skip unparseable files */ }
            }
        } catch (_: Exception) {
            // skip files that throw
        }
    }

    private fun processSource(content: String) {
        try {
            val parser = UIParser(content)
            when (val result = parser.parse()) {
                is Result.Success -> processDocument(result.value)
                is Result.Failure -> { /* skip unparseable sources */ }
            }
        } catch (_: Exception) {
            // skip sources that throw
        }
    }

    private fun processDocument(document: UIDocument) {
        // Walk all elements
        document.root.visitDescendants { element ->
            for ((propName, propValue) in element.properties.entries()) {
                if (propValue is PropertyValue.Tuple) {
                    recordTuple(propName.value, propValue, depth = 0)
                }
            }
        }

        // Walk style definitions
        for ((_, style) in document.styles) {
            for ((propName, propValue) in style.properties) {
                if (propValue is PropertyValue.Tuple) {
                    recordTuple(propName.value, propValue, depth = 0)
                }
            }
        }
    }

    /**
     * Record all fields of a tuple under [propertyName].
     * Recurses into nested tuples up to 1 level deep using composite keys (e.g., "Style.Default").
     * Produces at most 2 levels of keys: "P" and "P.B", but not "P.B.C".
     */
    private fun recordTuple(propertyName: String, tuple: PropertyValue.Tuple, depth: Int) {
        val fieldMap = propertyFields.getOrPut(propertyName) { mutableMapOf() }

        for ((fieldKey, fieldValue) in tuple.values) {
            val stats = fieldMap.getOrPut(fieldKey) { FieldStats(fieldKey) }
            stats.occurrences++
            stats.addObservedValue(fieldValue)
            stats.updateInferredType(fieldValue)

            // Recurse into nested tuples (composite key: "Style.Default")
            if (fieldValue is PropertyValue.Tuple && depth < 1) {
                val compositeKey = "$propertyName.$fieldKey"
                recordTuple(compositeKey, fieldValue, depth + 1)
            }
        }
    }

    private fun buildResult(): TupleFieldResult {
        val fieldsByProperty = propertyFields.mapValues { (_, fieldMap) ->
            fieldMap.values
                .sortedByDescending { it.occurrences }
                .map { stats ->
                    TupleFieldInfo(
                        name = stats.name,
                        inferredType = stats.inferredType,
                        occurrences = stats.occurrences,
                        observedValues = stats.observedValues.take(10).toList()
                    )
                }
        }
        return TupleFieldResult(
            fieldsByProperty = fieldsByProperty,
            sourceFiles = filesProcessed
        )
    }

    private fun progress(message: String) {
        progressCallback?.invoke(message)
    }
}

/**
 * Mutable stats collected during discovery for a single tuple sub-field.
 */
private class FieldStats(
    val name: String,
    var occurrences: Int = 0,
    var inferredType: String = "UNKNOWN",
    val observedValues: MutableSet<String> = mutableSetOf()
) {
    fun addObservedValue(value: PropertyValue) {
        val strValue = when (value) {
            is PropertyValue.Text -> value.value
            is PropertyValue.Number -> value.value.let {
                if (it % 1.0 == 0.0) it.toInt().toString() else it.toString()
            }
            is PropertyValue.Percent -> value.toString()
            is PropertyValue.Boolean -> value.value.toString()
            is PropertyValue.Color -> value.toString()
            else -> null // Don't track complex values
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
            // Skip reference/fill-mode types — they don't define structural type
            is PropertyValue.Style,
            is PropertyValue.VariableRef,
            is PropertyValue.LocalizedText,
            is PropertyValue.Spread,
            is PropertyValue.Expression -> return
            is PropertyValue.Unknown -> return
            is PropertyValue.Null -> return
        }

        if (inferredType == "UNKNOWN") {
            inferredType = newType
        } else if (inferredType != newType) {
            inferredType = "ANY"
        }
    }
}

// ============================================================================
// Result types
// ============================================================================

/**
 * Result of tuple field discovery across a corpus of .ui files.
 */
data class TupleFieldResult(
    /** Key: property name (e.g., "Style", "Background"). Value: known sub-fields sorted by occurrence. */
    val fieldsByProperty: Map<String, List<TupleFieldInfo>>,
    val sourceFiles: Int
)

/**
 * Discovered information about a single tuple sub-field.
 *
 * Used in both the discovery result and at runtime in [RuntimePropertySchema.tupleFields].
 */
data class TupleFieldInfo(
    val name: String,
    /** Inferred type: "NUMBER", "TEXT", "BOOLEAN", "COLOR", "TUPLE", "ANY", etc. */
    val inferredType: String,
    val occurrences: Int,
    val observedValues: List<String> = emptyList()
) {
    fun toJsonObject(): JsonObject = buildJsonObject {
        put("name", name)
        put("type", inferredType)
        put("occurrences", occurrences)
        if (observedValues.isNotEmpty()) {
            putJsonArray("observedValues") {
                observedValues.forEach { add(it) }
            }
        }
    }

    companion object {
        fun fromJsonObject(json: JsonObject): TupleFieldInfo = TupleFieldInfo(
            name = json["name"]?.jsonPrimitive?.content ?: "",
            inferredType = json["type"]?.jsonPrimitive?.content ?: "ANY",
            occurrences = json["occurrences"]?.jsonPrimitive?.int ?: 0,
            observedValues = json["observedValues"]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList()
        )
    }
}
