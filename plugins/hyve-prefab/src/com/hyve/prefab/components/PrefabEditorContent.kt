package com.hyve.prefab.components

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hyve.common.compose.HyveThemeColors
import com.hyve.common.compose.components.EmptyState
import com.hyve.prefab.domain.EntityId
import androidx.compose.runtime.State
import com.hyve.prefab.editor.PrefabEditorState
import com.hyve.prefab.editor.PrefabHotkeys
import com.hyve.prefab.state.DeleteEntityCommand
import com.hyve.prefab.state.SetComponentFieldCommand
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import com.hyve.prefab.editor.PrefabComponentMeta
import com.hyve.prefab.editor.PrefabEditorMetadata
import com.hyve.prefab.editor.PrefabEditorViewState
import com.hyve.prefab.editor.PrefabMetadataIO
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.withContext
import java.io.File
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.CircularProgressIndicator

/**
 * Main Compose content for the Prefab Editor.
 *
 * Layout:
 * ```
 * ┌─────────────────┬─────────────────────────┐
 * │  [Filter]       │  Entity Inspector       │
 * │  Entity List    │  (right, fills)         │
 * │  (left, 280dp)  │                         │
 * ├─────────────────┴─────────────────────────┤
 * │  Stats Bar                                │
 * └───────────────────────────────────────────┘
 * ```
 *
 * Hotkeys: Ctrl+S (save), Ctrl+Z (undo), Ctrl+Y / Ctrl+Shift+Z (redo)
 */
@OptIn(FlowPreview::class)
@Composable
fun PrefabEditorContent(
    state: PrefabEditorState,
    onSave: () -> Unit,
    onJumpToSource: ((EntityId) -> Unit)? = null,
    filePath: String = "",
    projectBasePath: String? = null,
    sourceOpenEntityId: State<EntityId?> = mutableStateOf(null),
) {
    val colors = HyveThemeColors.colors
    val focusRequester = remember { FocusRequester() }

    // -- Sidecar metadata persistence --
    val componentCollapsedStates = remember { mutableStateMapOf<String, Boolean>() }
    var emptyComponentsExpanded by remember { mutableStateOf(false) }
    val inspectorScrollState = rememberScrollState()
    val listScrollState = rememberLazyListState()
    var metadataApplied by remember { mutableStateOf(false) }

    val doc = state.document.value

    LaunchedEffect(doc) {
        if (doc != null && !metadataApplied && filePath.isNotEmpty()) {
            metadataApplied = true
            val metadata = withContext(Dispatchers.IO) {
                PrefabMetadataIO.load(File(filePath), projectBasePath)
            }
            metadata.editorState.selectedEntityId?.toLongOrNull()?.let { id ->
                val entityId = EntityId(id)
                if (doc.findEntityById(entityId) != null) {
                    state.selectEntity(entityId)
                }
            }
            if (metadata.editorState.filterText.isNotEmpty()) {
                state.setFilterText(metadata.editorState.filterText)
            }
            for ((key, meta) in metadata.componentStates) {
                componentCollapsedStates[key] = meta.collapsed
            }
            emptyComponentsExpanded = metadata.emptyComponentsExpanded
            // Restore source panel (open entity JSON editor)
            metadata.editorState.sourceEntityId?.toLongOrNull()?.let { id ->
                val sourceId = EntityId(id)
                if (doc.findEntityById(sourceId) != null) {
                    onJumpToSource?.invoke(sourceId)
                }
            }
            inspectorScrollState.scrollTo(metadata.editorState.scrollY.toInt())
            listScrollState.scrollToItem(
                metadata.editorState.listScrollIndex,
                metadata.editorState.listScrollOffset,
            )
        }
    }

    fun buildMetadata(): PrefabEditorMetadata = PrefabEditorMetadata(
        editorState = PrefabEditorViewState(
            selectedEntityId = state.selectedEntityId.value?.value?.toString(),
            filterText = state.filterText.value,
            scrollY = inspectorScrollState.value.toFloat(),
            listScrollIndex = listScrollState.firstVisibleItemIndex,
            listScrollOffset = listScrollState.firstVisibleItemScrollOffset,
            sourceEntityId = sourceOpenEntityId.value?.value?.toString(),
        ),
        componentStates = componentCollapsedStates.mapValues { (_, collapsed) ->
            PrefabComponentMeta(collapsed = collapsed)
        },
        emptyComponentsExpanded = emptyComponentsExpanded,
    )

    LaunchedEffect(Unit) {
        snapshotFlow {
            listOf(
                state.selectedEntityId.value?.value,
                state.filterText.value,
                inspectorScrollState.value,
                listScrollState.firstVisibleItemIndex,
                listScrollState.firstVisibleItemScrollOffset,
                emptyComponentsExpanded,
                componentCollapsedStates.toMap(),
                sourceOpenEntityId.value,
            )
        }
            .debounce(500L)
            .collect {
                if (filePath.isNotEmpty() && metadataApplied) {
                    withContext(Dispatchers.IO) {
                        PrefabMetadataIO.save(File(filePath), buildMetadata(), projectBasePath)
                    }
                }
            }
    }

    DisposableEffect(Unit) {
        onDispose {
            if (filePath.isNotEmpty() && metadataApplied) {
                PrefabMetadataIO.save(File(filePath), buildMetadata(), projectBasePath)
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxSize()
            .background(JewelTheme.globalColors.panelBackground)
            .focusRequester(focusRequester)
            .focusable()
            .onPreviewKeyEvent { event ->
                when (PrefabHotkeys.match(event)) {
                    PrefabHotkeys.Action.SAVE -> { onSave(); true }
                    PrefabHotkeys.Action.UNDO -> { state.undo(); true }
                    PrefabHotkeys.Action.REDO -> { state.redo(); true }
                    null -> false
                }
            },
    ) {
        // Request focus so keyboard shortcuts work immediately
        LaunchedEffect(Unit) { focusRequester.requestFocus() }

        val isLoading = state.isLoading.value
        val error = state.error.value

        when {
            isLoading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = "Parsing prefab...",
                            style = TextStyle(
                                color = colors.textSecondary,
                                fontSize = 13.sp,
                            ),
                        )
                    }
                }
            }

            error != null -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "Parse Error",
                            style = TextStyle(
                                color = colors.error,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                            ),
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = error,
                            style = TextStyle(
                                color = colors.textSecondary,
                                fontSize = 12.sp,
                            ),
                        )
                    }
                }
            }

            doc != null -> {
                // Main content area
                Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    // Entity list (left panel)
                    EntityListPanel(
                        entities = doc.allEntities,
                        selectedEntityId = state.selectedEntityId.value,
                        filterText = state.filterText.value,
                        onFilterTextChanged = { state.setFilterText(it) },
                        onSelectEntity = { state.selectEntity(it) },
                        onDeleteEntity = { entity, index ->
                            state.executeCommand(DeleteEntityCommand(entity, index))
                        },
                        onJumpToSource = onJumpToSource,
                        listState = listScrollState,
                        modifier = Modifier.width(280.dp)
                            .background(JewelTheme.globalColors.panelBackground),
                    )

                    // Divider
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .width(1.dp)
                            .background(colors.slateLight),
                    )

                    // Entity inspector (right panel) + stats bar
                    Column(modifier = Modifier.weight(1f)) {
                        val selectedId = state.selectedEntityId.value
                        val selectedEntity = selectedId?.let { doc.findEntityById(it) }

                        if (selectedEntity != null) {
                            EntityInspector(
                                entity = selectedEntity,
                                onFieldChanged = { entityId, componentKey, fieldPath, oldValue, newValue ->
                                    state.executeCommand(
                                        SetComponentFieldCommand(
                                            entityId = entityId,
                                            componentKey = componentKey,
                                            fieldPath = fieldPath,
                                            oldValue = oldValue,
                                            newValue = newValue,
                                        )
                                    )
                                },
                                onJumpToSource = onJumpToSource,
                                scrollState = inspectorScrollState,
                                componentCollapsedStates = componentCollapsedStates,
                                emptyComponentsExpanded = emptyComponentsExpanded,
                                onEmptyComponentsExpandedChanged = { emptyComponentsExpanded = it },
                                modifier = Modifier.weight(1f),
                            )
                        } else {
                            EmptyState(
                                text = "Select an entity to inspect",
                                modifier = Modifier.weight(1f),
                            )
                        }

                        PrefabStatsBar(doc = doc)
                    }
                }
            }
        }
    }
}
