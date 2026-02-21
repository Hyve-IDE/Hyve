// Copyright 2026 Hyve. All rights reserved.
package com.hyve.ui.components.validation

import androidx.compose.runtime.*

/**
 * Status of a texture/asset loading operation.
 */
enum class AssetLoadStatus {
    /** Asset is currently being loaded */
    LOADING,
    /** Asset was loaded successfully */
    LOADED,
    /** Asset was not found in any searched path */
    NOT_FOUND,
    /** Asset loading failed with an error */
    FAILED
}

/**
 * Information about a texture referenced in the document.
 */
data class TextureStatus(
    /** The original path as specified in the .ui file */
    val originalPath: String,
    /** The resolved path used for loading (after relative path resolution) */
    val resolvedPath: String,
    /** Current loading status */
    val status: AssetLoadStatus,
    /** Error message if failed */
    val errorMessage: String? = null,
    /** The element ID that references this texture */
    val elementId: String? = null,
    /** The element type that references this texture */
    val elementType: String? = null,
    /** Property name that contains this texture reference */
    val propertyName: String? = null
)

/**
 * Information about a resolved variable.
 */
data class VariableStatus(
    /** Variable name (e.g., "@DefaultSlotSize") */
    val name: String,
    /** The resolved value (as a string representation) */
    val resolvedValue: String?,
    /** Whether resolution was successful */
    val isResolved: Boolean,
    /** Error or warning message */
    val message: String? = null,
    /** Source file where the variable is defined */
    val sourceFile: String? = null
)

/**
 * Information about an imported file.
 */
data class ImportStatus(
    /** Import alias (e.g., "$InGame") */
    val alias: String,
    /** The import path as specified in the file */
    val path: String,
    /** The resolved absolute path */
    val resolvedPath: String?,
    /** Whether the import was resolved successfully */
    val isResolved: Boolean,
    /** Error message if not resolved */
    val errorMessage: String? = null
)

/**
 * State management for the Validation Panel.
 *
 * Tracks:
 * - Texture loading status for all textures in the document
 * - Variable resolution status from the parser
 * - Import resolution status
 */
class ValidationPanelState {
    // --- Texture Status ---

    private val _textureStatuses = mutableStateMapOf<String, TextureStatus>()
    val textureStatuses: Map<String, TextureStatus>
        get() = _textureStatuses.toMap()

    /**
     * Report that a texture load has started.
     */
    fun reportTextureLoading(
        originalPath: String,
        resolvedPath: String,
        elementId: String? = null,
        elementType: String? = null,
        propertyName: String? = null
    ) {
        _textureStatuses[resolvedPath] = TextureStatus(
            originalPath = originalPath,
            resolvedPath = resolvedPath,
            status = AssetLoadStatus.LOADING,
            elementId = elementId,
            elementType = elementType,
            propertyName = propertyName
        )
    }

    /**
     * Report that a texture load completed successfully.
     */
    fun reportTextureLoaded(resolvedPath: String) {
        _textureStatuses[resolvedPath]?.let { current ->
            _textureStatuses[resolvedPath] = current.copy(status = AssetLoadStatus.LOADED)
        }
    }

    /**
     * Report that a texture was not found.
     */
    fun reportTextureNotFound(resolvedPath: String) {
        _textureStatuses[resolvedPath]?.let { current ->
            _textureStatuses[resolvedPath] = current.copy(
                status = AssetLoadStatus.NOT_FOUND,
                errorMessage = "Asset not found in Assets.zip or Client folder"
            )
        }
    }

    /**
     * Report that a texture load failed with an error.
     */
    fun reportTextureFailed(resolvedPath: String, error: String) {
        _textureStatuses[resolvedPath]?.let { current ->
            _textureStatuses[resolvedPath] = current.copy(
                status = AssetLoadStatus.FAILED,
                errorMessage = error
            )
        }
    }

    /**
     * Clear all texture statuses (call when document changes).
     */
    fun clearTextureStatuses() {
        _textureStatuses.clear()
    }

    // --- Variable Status ---

    private val _variableStatuses = mutableStateListOf<VariableStatus>()
    val variableStatuses: List<VariableStatus>
        get() = _variableStatuses.toList()

    /**
     * Set variable statuses from parse warnings.
     */
    fun setVariableStatuses(statuses: List<VariableStatus>) {
        _variableStatuses.clear()
        _variableStatuses.addAll(statuses)
    }

    /**
     * Add a variable status.
     */
    fun addVariableStatus(status: VariableStatus) {
        _variableStatuses.add(status)
    }

    /**
     * Clear all variable statuses.
     */
    fun clearVariableStatuses() {
        _variableStatuses.clear()
    }

    // --- Import Status ---

    private val _importStatuses = mutableStateListOf<ImportStatus>()
    val importStatuses: List<ImportStatus>
        get() = _importStatuses.toList()

    /**
     * Set import statuses.
     */
    fun setImportStatuses(statuses: List<ImportStatus>) {
        _importStatuses.clear()
        _importStatuses.addAll(statuses)
    }

    /**
     * Add an import status.
     */
    fun addImportStatus(status: ImportStatus) {
        _importStatuses.add(status)
    }

    /**
     * Clear all import statuses.
     */
    fun clearImportStatuses() {
        _importStatuses.clear()
    }

    // --- Parse Warnings ---

    private val _parseWarnings = mutableStateListOf<String>()
    val parseWarnings: List<String>
        get() = _parseWarnings.toList()

    /**
     * Set parse warnings from the last parse operation.
     */
    fun setParseWarnings(warnings: List<String>) {
        _parseWarnings.clear()
        _parseWarnings.addAll(warnings)

        // Parse warnings to extract variable and import info
        for (warning in warnings) {
            when {
                warning.contains("Undefined variable") || warning.contains("undefined") -> {
                    val varName = extractVariableName(warning)
                    if (varName != null) {
                        addVariableStatus(VariableStatus(
                            name = varName,
                            resolvedValue = null,
                            isResolved = false,
                            message = warning
                        ))
                    }
                }
                warning.contains("circular") -> {
                    val varName = extractVariableName(warning)
                    if (varName != null) {
                        addVariableStatus(VariableStatus(
                            name = varName,
                            resolvedValue = null,
                            isResolved = false,
                            message = "Circular reference detected: $warning"
                        ))
                    }
                }
                warning.contains("import") || warning.contains("Import") -> {
                    // Try to extract import info from warning
                    val alias = extractImportAlias(warning)
                    if (alias != null) {
                        addImportStatus(ImportStatus(
                            alias = alias,
                            path = "",
                            resolvedPath = null,
                            isResolved = false,
                            errorMessage = warning
                        ))
                    }
                }
            }
        }
    }

    /**
     * Clear all parse warnings.
     */
    fun clearParseWarnings() {
        _parseWarnings.clear()
    }

    /**
     * Extract variable name from a warning message.
     */
    private fun extractVariableName(warning: String): String? {
        // Try to find @VarName or $Alias patterns
        val atPattern = Regex("""@(\w+)""")
        val dollarPattern = Regex("""\$(\w+)""")

        atPattern.find(warning)?.let { return "@${it.groupValues[1]}" }
        dollarPattern.find(warning)?.let { return "\$${it.groupValues[1]}" }

        return null
    }

    /**
     * Extract import alias from a warning message.
     */
    private fun extractImportAlias(warning: String): String? {
        val dollarPattern = Regex("""\$(\w+)""")
        dollarPattern.find(warning)?.let { return "\$${it.groupValues[1]}" }
        return null
    }

    // --- Summary Statistics ---

    /**
     * Get count of textures by status.
     */
    val textureCountByStatus: Map<AssetLoadStatus, Int>
        get() = _textureStatuses.values.groupingBy { it.status }.eachCount()

    /**
     * Get total number of texture references.
     */
    val totalTextureCount: Int
        get() = _textureStatuses.size

    /**
     * Get count of failed/not found textures.
     */
    val failedTextureCount: Int
        get() = _textureStatuses.values.count {
            it.status == AssetLoadStatus.NOT_FOUND || it.status == AssetLoadStatus.FAILED
        }

    /**
     * Get count of unresolved variables.
     */
    val unresolvedVariableCount: Int
        get() = _variableStatuses.count { !it.isResolved }

    /**
     * Get count of failed imports.
     */
    val failedImportCount: Int
        get() = _importStatuses.count { !it.isResolved }

    /**
     * Check if there are any issues to display.
     */
    val hasIssues: Boolean
        get() = failedTextureCount > 0 || unresolvedVariableCount > 0 || failedImportCount > 0

    /**
     * Clear all validation data.
     */
    fun clearAll() {
        clearTextureStatuses()
        clearVariableStatuses()
        clearImportStatuses()
        clearParseWarnings()
    }
}

/**
 * Remember validation panel state across recompositions.
 */
@Composable
fun rememberValidationPanelState(): ValidationPanelState {
    return remember { ValidationPanelState() }
}
