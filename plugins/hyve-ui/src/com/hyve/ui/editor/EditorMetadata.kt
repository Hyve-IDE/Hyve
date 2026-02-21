package com.hyve.ui.editor

import com.intellij.openapi.diagnostic.Logger
import kotlinx.serialization.json.*
import java.io.File

/**
 * Lightweight JSON sidecar for persisting editor-only state between sessions.
 *
 * Meta files are stored under `.idea/hyve-ui-meta/` in the project root, mirroring
 * the relative path of the `.ui` file. This keeps design-time data (grid toggle,
 * zoom, per-element previewItemId, etc.) out of the user's source tree entirely.
 */
data class EditorMetadata(
    val editorState: EditorViewState = EditorViewState(),
    val elementMetadata: Map<String, ElementEditorData> = emptyMap()
)

data class EditorViewState(
    val showGrid: Boolean = true,
    val darkCanvas: Boolean = false,
    val zoom: Float = 1.0f,
    val scrollX: Float = 0f,
    val scrollY: Float = 0f,
    val showScreenshot: Boolean = false,
    val screenshotOpacity: Float = 0.3f,
    val screenshotMode: String = "NO_HUD"
)

data class ElementEditorData(
    val previewItemId: String? = null
)

object EditorMetadataIO {

    private val LOG = Logger.getInstance(EditorMetadataIO::class.java)

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    private const val META_DIR = ".idea/hyve-ui-meta"

    /**
     * Resolve the sidecar path for a given `.ui` file.
     *
     * When [projectBasePath] is provided, the meta file is stored under
     * `.idea/hyve-ui-meta/` mirroring the file's relative path within the project.
     * Falls back to a sibling `.ui.meta` file if the ui file is outside the project
     * or no project path is given.
     */
    fun sidecarFile(uiFile: File, projectBasePath: String? = null): File {
        if (projectBasePath != null) {
            val projectDir = File(projectBasePath)
            val relativePath = try {
                uiFile.relativeTo(projectDir).path
            } catch (_: IllegalArgumentException) {
                null // uiFile is outside the project tree
            }
            if (relativePath != null) {
                return File(projectDir, "$META_DIR/$relativePath.meta")
            }
        }
        return File(uiFile.absolutePath + ".meta")
    }

    /**
     * Load editor metadata from the sidecar file.
     * Returns defaults if the sidecar doesn't exist or can't be parsed.
     */
    fun load(uiFile: File, projectBasePath: String? = null): EditorMetadata {
        val metaFile = sidecarFile(uiFile, projectBasePath)

        // Migrate: if new location doesn't exist but old sibling .meta does, read from old location
        if (!metaFile.exists() && projectBasePath != null) {
            val legacyFile = File(uiFile.absolutePath + ".meta")
            if (legacyFile.exists()) {
                LOG.info("Migrating legacy sidecar ${legacyFile.name} to ${metaFile.path}")
                metaFile.parentFile.mkdirs()
                legacyFile.copyTo(metaFile, overwrite = true)
                legacyFile.delete()
            }
        }
        if (!metaFile.exists()) return EditorMetadata()

        return try {
            val root = json.parseToJsonElement(metaFile.readText()).jsonObject
            val editorState = root["editorState"]?.jsonObject?.let { parseEditorViewState(it) }
                ?: EditorViewState()
            val elementMetadata = root["elementMetadata"]?.jsonObject?.let { obj ->
                obj.mapValues { (_, v) -> parseElementEditorData(v.jsonObject) }
            } ?: emptyMap()
            EditorMetadata(editorState, elementMetadata)
        } catch (e: Exception) {
            LOG.debug("Failed to load editor metadata sidecar for ${uiFile.name}", e)
            EditorMetadata()
        }
    }

    /**
     * Save editor metadata to the sidecar file.
     */
    fun save(uiFile: File, metadata: EditorMetadata, projectBasePath: String? = null) {
        val metaFile = sidecarFile(uiFile, projectBasePath)
        try {
            metaFile.parentFile.mkdirs()
            val root = buildJsonObject {
                put("editorState", buildJsonObject {
                    put("showGrid", metadata.editorState.showGrid)
                    put("darkCanvas", metadata.editorState.darkCanvas)
                    put("zoom", metadata.editorState.zoom)
                    put("scrollX", metadata.editorState.scrollX)
                    put("scrollY", metadata.editorState.scrollY)
                    put("showScreenshot", metadata.editorState.showScreenshot)
                    put("screenshotOpacity", metadata.editorState.screenshotOpacity)
                    put("screenshotMode", metadata.editorState.screenshotMode)
                })
                put("elementMetadata", buildJsonObject {
                    for ((id, data) in metadata.elementMetadata) {
                        put(id, buildJsonObject {
                            data.previewItemId?.let { put("previewItemId", it) }
                        })
                    }
                })
            }
            metaFile.writeText(json.encodeToString(JsonElement.serializer(), root))
        } catch (e: Exception) {
            LOG.debug("Failed to save editor metadata sidecar for ${uiFile.name}", e)
        }
    }

    private fun parseEditorViewState(obj: JsonObject): EditorViewState {
        return EditorViewState(
            showGrid = obj["showGrid"]?.jsonPrimitive?.booleanOrNull ?: true,
            darkCanvas = obj["darkCanvas"]?.jsonPrimitive?.booleanOrNull ?: false,
            zoom = obj["zoom"]?.jsonPrimitive?.floatOrNull ?: 1.0f,
            scrollX = obj["scrollX"]?.jsonPrimitive?.floatOrNull ?: 0f,
            scrollY = obj["scrollY"]?.jsonPrimitive?.floatOrNull ?: 0f,
            showScreenshot = obj["showScreenshot"]?.jsonPrimitive?.booleanOrNull ?: false,
            screenshotOpacity = obj["screenshotOpacity"]?.jsonPrimitive?.floatOrNull ?: 0.3f,
            screenshotMode = obj["screenshotMode"]?.jsonPrimitive?.contentOrNull ?: "NO_HUD"
        )
    }

    private fun parseElementEditorData(obj: JsonObject): ElementEditorData {
        return ElementEditorData(
            previewItemId = obj["previewItemId"]?.jsonPrimitive?.contentOrNull
        )
    }
}
