package com.hyve.ui.editor

import com.hyve.ui.schema.FakeSchemaProvider
import com.hyve.ui.settings.FakeAssetSettings
import java.nio.file.Path

/**
 * Factory for creating [EditorDependencies] wired with test fakes.
 * No singletons, no file system, no Preferences.
 */
fun testEditorDependencies(
    assetSettings: FakeAssetSettings = FakeAssetSettings(),
    schemaProvider: FakeSchemaProvider = FakeSchemaProvider(),
    projectResourcesPath: Path? = null
) = EditorDependencies(
    assetSettings = assetSettings,
    schemaProvider = schemaProvider,
    projectResourcesPath = projectResourcesPath
)
