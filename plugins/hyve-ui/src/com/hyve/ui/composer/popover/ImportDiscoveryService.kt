// Copyright 2026 Hyve. All rights reserved.
package com.hyve.ui.composer.popover

import com.hyve.ui.composer.model.ComposerPropertyType
import com.hyve.ui.composer.model.ImportableExport
import com.hyve.ui.composer.model.ImportableFile
import com.hyve.ui.core.domain.properties.PropertyValue
import com.hyve.ui.core.domain.styles.StyleDefinition
import com.hyve.ui.core.id.PropertyName
import com.hyve.ui.core.result.Result
import com.hyve.ui.parser.UIParser
import java.io.File

/**
 * Discovers importable `.ui` files from the project file system.
 *
 * Scans a directory for `.ui` files, parses each with [UIParser], and
 * extracts style definitions as [ImportableExport] items. Files that
 * fail to parse are silently skipped.
 *
 * ## Spec Reference
 * - FR-4: Add Import Popover — file discovery
 */
class ImportDiscoveryService {

    /**
     * Discover importable files from a project directory.
     *
     * @param projectDir The root directory to scan for `.ui` files
     * @param excludeFileName Optional file name to exclude (the currently edited file)
     * @return List of [ImportableFile], sorted alphabetically by name, excluding files with no exports
     */
    fun discoverImports(projectDir: File, excludeFileName: String? = null): List<ImportableFile> {
        if (!projectDir.exists() || !projectDir.isDirectory) return emptyList()

        return projectDir.walkTopDown()
            .filter { it.isFile && it.extension.equals("ui", ignoreCase = true) }
            .filter { excludeFileName == null || it.name != excludeFileName }
            .mapNotNull { file -> parseFileExports(file) }
            .filter { it.exports.isNotEmpty() }
            .sortedBy { it.name }
            .toList()
    }

    private fun parseFileExports(file: File): ImportableFile? {
        return try {
            val source = file.readText()
            val parser = UIParser(source)
            when (val result = parser.parse()) {
                is Result.Success -> {
                    val doc = result.value
                    val exports = doc.styles.map { (styleName, styleDef) ->
                        ImportableExport(
                            name = styleName.value,
                            type = inferExportType(styleDef),
                        )
                    }.sortedBy { it.name }

                    ImportableFile(
                        name = file.nameWithoutExtension,
                        fileName = file.name,
                        exports = exports,
                    )
                }
                is Result.Failure -> null
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Infer the [ComposerPropertyType] for a style export.
     *
     * Type-constructor styles and multi-property tuple styles are [ComposerPropertyType.STYLE].
     * Single-value styles (`@Size = 64`) infer their type from the value.
     */
    internal fun inferExportType(styleDef: StyleDefinition): ComposerPropertyType {
        // Type constructor styles (e.g. TextButtonStyle(...)) → STYLE
        if (styleDef.typeName != null || styleDef.elementType != null) {
            return ComposerPropertyType.STYLE
        }

        // Single-value styles: check the _value property
        val singleValue = styleDef.properties[PropertyName("_value")]
        if (singleValue != null) {
            return when (singleValue) {
                is PropertyValue.Color -> ComposerPropertyType.COLOR
                is PropertyValue.Number -> ComposerPropertyType.NUMBER
                is PropertyValue.Text -> ComposerPropertyType.TEXT
                is PropertyValue.Boolean -> ComposerPropertyType.BOOLEAN
                is PropertyValue.Percent -> ComposerPropertyType.PERCENT
                is PropertyValue.ImagePath -> ComposerPropertyType.IMAGE
                is PropertyValue.FontPath -> ComposerPropertyType.FONT
                else -> ComposerPropertyType.STYLE
            }
        }

        // Multi-property tuple styles → STYLE
        return ComposerPropertyType.STYLE
    }
}
