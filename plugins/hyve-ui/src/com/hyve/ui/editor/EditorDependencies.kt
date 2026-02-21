package com.hyve.ui.editor

import androidx.compose.runtime.compositionLocalOf
import com.hyve.ui.schema.SchemaProvider
import com.hyve.ui.schema.SchemaService
import com.hyve.ui.settings.AssetSettings
import com.hyve.ui.settings.AssetSettingsProvider
import java.nio.file.Path

/**
 * Container for editor dependencies that are normally provided by singletons.
 *
 * In production, [createDefaultEditorDependencies] wires the real singletons.
 * In tests, use [FakeAssetSettings] and [FakeSchemaProvider] (in testSrc)
 * via [testEditorDependencies].
 */
data class EditorDependencies(
    val assetSettings: AssetSettingsProvider,
    val schemaProvider: SchemaProvider,
    val projectResourcesPath: Path? = null
)

/**
 * CompositionLocal providing [EditorDependencies] to the editor subtree.
 *
 * Must be provided via [CompositionLocalProvider] before any composable reads it.
 */
val LocalEditorDependencies = compositionLocalOf<EditorDependencies> {
    error("EditorDependencies not provided — wrap editor content in CompositionLocalProvider(LocalEditorDependencies provides ...)")
}

/**
 * Create the default production [EditorDependencies] wired to the real singletons.
 */
fun createDefaultEditorDependencies(projectResourcesPath: Path? = null) =
    EditorDependencies(
        assetSettings = AssetSettings,
        schemaProvider = SchemaService,
        projectResourcesPath = projectResourcesPath
    )
