package com.hyve.prefab.editor

import com.intellij.openapi.diagnostic.Logger
import kotlinx.serialization.json.*
import java.io.File

/**
 * Lightweight JSON sidecar for persisting editor-only state between sessions.
 *
 * Meta files are stored under `.idea/hyve-prefab-meta/` in the project root, mirroring
 * the relative path of the `.prefab.json` file.
 */
data class PrefabEditorMetadata(
    val editorState: PrefabEditorViewState = PrefabEditorViewState(),
    val componentStates: Map<String, PrefabComponentMeta> = emptyMap(),
    val emptyComponentsExpanded: Boolean = false,
)

data class PrefabEditorViewState(
    val selectedEntityId: String? = null,
    val filterText: String = "",
    val scrollY: Float = 0f,
    val listScrollIndex: Int = 0,
    val listScrollOffset: Int = 0,
    val sourceEntityId: String? = null,
)

data class PrefabComponentMeta(
    val collapsed: Boolean = false,
)

object PrefabMetadataIO {

    private val LOG = Logger.getInstance(PrefabMetadataIO::class.java)

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    private const val META_DIR = ".idea/hyve-prefab-meta"

    /**
     * Resolve the sidecar path for a given prefab file.
     *
     * When [projectBasePath] is provided, the meta file is stored under
     * `.idea/hyve-prefab-meta/` mirroring the file's relative path within the project.
     * Falls back to a sibling `.meta` file if the file is outside the project.
     */
    fun sidecarFile(prefabFile: File, projectBasePath: String? = null): File {
        if (projectBasePath != null) {
            val projectDir = File(projectBasePath)
            val relativePath = try {
                prefabFile.relativeTo(projectDir).path
            } catch (_: IllegalArgumentException) {
                null
            }
            if (relativePath != null) {
                return File(projectDir, "$META_DIR/$relativePath.meta")
            }
        }
        return File(prefabFile.absolutePath + ".meta")
    }

    /**
     * Load editor metadata from the sidecar file.
     * Returns defaults if the sidecar doesn't exist or can't be parsed.
     */
    fun load(prefabFile: File, projectBasePath: String? = null): PrefabEditorMetadata {
        val metaFile = sidecarFile(prefabFile, projectBasePath)
        if (!metaFile.exists()) return PrefabEditorMetadata()

        return try {
            val root = json.parseToJsonElement(metaFile.readText()).jsonObject
            val editorState = root["editorState"]?.jsonObject?.let { parseViewState(it) }
                ?: PrefabEditorViewState()
            val componentStates = root["componentStates"]?.jsonObject?.let { obj ->
                obj.mapValues { (_, v) -> parseComponentMeta(v.jsonObject) }
            } ?: emptyMap()
            val emptyExpanded = root["emptyComponentsExpanded"]?.jsonPrimitive?.booleanOrNull ?: false
            PrefabEditorMetadata(editorState, componentStates, emptyExpanded)
        } catch (e: Exception) {
            LOG.warn("Failed to load prefab editor metadata for ${prefabFile.name}", e)
            PrefabEditorMetadata()
        }
    }

    /**
     * Save editor metadata to the sidecar file.
     */
    fun save(prefabFile: File, metadata: PrefabEditorMetadata, projectBasePath: String? = null) {
        val metaFile = sidecarFile(prefabFile, projectBasePath)
        try {
            metaFile.parentFile.mkdirs()
            val root = buildJsonObject {
                put("editorState", buildJsonObject {
                    metadata.editorState.selectedEntityId?.let { put("selectedEntityId", it) }
                    put("filterText", metadata.editorState.filterText)
                    put("scrollY", metadata.editorState.scrollY)
                    put("listScrollIndex", metadata.editorState.listScrollIndex)
                    put("listScrollOffset", metadata.editorState.listScrollOffset)
                    metadata.editorState.sourceEntityId?.let { put("sourceEntityId", it) }
                })
                put("componentStates", buildJsonObject {
                    for ((key, meta) in metadata.componentStates) {
                        put(key, buildJsonObject {
                            put("collapsed", meta.collapsed)
                        })
                    }
                })
                put("emptyComponentsExpanded", metadata.emptyComponentsExpanded)
            }
            metaFile.writeText(json.encodeToString(JsonElement.serializer(), root))
        } catch (e: Exception) {
            LOG.warn("Failed to save prefab editor metadata for ${prefabFile.name}", e)
        }
    }

    private fun parseViewState(obj: JsonObject): PrefabEditorViewState {
        return PrefabEditorViewState(
            selectedEntityId = obj["selectedEntityId"]?.jsonPrimitive?.contentOrNull,
            filterText = obj["filterText"]?.jsonPrimitive?.contentOrNull ?: "",
            scrollY = obj["scrollY"]?.jsonPrimitive?.floatOrNull ?: 0f,
            listScrollIndex = obj["listScrollIndex"]?.jsonPrimitive?.intOrNull ?: 0,
            listScrollOffset = obj["listScrollOffset"]?.jsonPrimitive?.intOrNull ?: 0,
            sourceEntityId = obj["sourceEntityId"]?.jsonPrimitive?.contentOrNull,
        )
    }

    private fun parseComponentMeta(obj: JsonObject): PrefabComponentMeta {
        return PrefabComponentMeta(
            collapsed = obj["collapsed"]?.jsonPrimitive?.booleanOrNull ?: false,
        )
    }
}
