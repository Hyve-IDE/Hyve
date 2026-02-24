// Copyright 2026 Hyve. All rights reserved.
package com.hyve.ui.editor

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hyve.common.compose.HyveThemeColors
import com.hyve.ui.editor.components.DraggableDivider
import com.hyve.ui.editor.components.rememberDraggableDividerState
import com.hyve.ui.editor.components.rememberSidebarState
import com.hyve.ui.canvas.CanvasState
import com.hyve.ui.canvas.CanvasView
import com.hyve.ui.canvas.ScreenshotMode
import com.hyve.ui.canvas.rememberCanvasState
import com.hyve.ui.composer.ComposerModal
import com.hyve.ui.composer.model.ElementDefinition
import com.hyve.ui.composer.model.applyTo
import com.hyve.ui.composer.model.toElementDefinition
import com.hyve.ui.state.command.ReplaceElementCommand
import com.hyve.ui.composer.popover.ImportDiscoveryService
import com.hyve.ui.composer.rememberComposerModalState
import com.hyve.ui.composer.wordbank.rememberWordBankState
import com.hyve.ui.components.hierarchy.HierarchyTree
import com.hyve.ui.components.hierarchy.rememberHierarchyTreeState
import com.hyve.ui.components.properties.SchemaPropertyInspector
import com.hyve.ui.components.hotkeys.HotkeyReferencePanel
import com.hyve.ui.components.toolbox.DragPreviewOverlay
import com.hyve.ui.components.toolbox.DropHandler
import com.hyve.ui.components.toolbox.ElementToolbox
import com.hyve.ui.components.toolbox.rememberElementToolboxState
import com.hyve.ui.components.validation.ValidationPanelState
import com.hyve.ui.components.validation.rememberValidationPanelState
import com.hyve.ui.core.domain.UIDocument
import com.hyve.ui.core.domain.elements.UIElement
import com.hyve.ui.core.domain.elements.assignAutoIds
import com.hyve.ui.exporter.UIExporter
import com.hyve.ui.core.domain.properties.PropertyValue
import com.hyve.ui.parser.UIParser
import com.hyve.ui.parser.VariableAwareParser
import com.hyve.ui.state.EditDeltaTracker
import com.hyve.ui.schema.ElementSchema
import com.hyve.ui.schema.RuntimeSchemaRegistry
import com.hyve.ui.services.assets.AssetLoader
import com.hyve.ui.services.items.ItemRegistry
import com.hyve.common.compose.HyveSpacing
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.jewel.ui.Orientation
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.CircularProgressIndicator
import org.jetbrains.jewel.ui.component.Divider
import org.jetbrains.jewel.ui.component.OutlinedButton
import org.jetbrains.jewel.ui.component.Text
import java.io.File

/**
 * Main Compose content for the HyveUI visual editor.
 *
 * This composable provides the visual editing experience for .ui files,
 * including:
 * - Loading state display
 * - Error state with retry option
 * - Main editor UI with canvas, toolbox, and property inspector
 *
 * The editor will be integrated with HyUI Studio's existing editor components
 * once the library is properly connected.
 */
@Composable
fun HyveUIEditorContent(
    state: HyveUIEditorState,
    project: Project,
    file: VirtualFile,
    onModified: () -> Unit,
    onSave: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val isLoading by state.isLoading
    val error by state.error
    val content by state.content

    val colors = HyveThemeColors.colors

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.midnight)
    ) {
        when {
            isLoading -> {
                LoadingState()
            }
            error != null -> {
                ErrorState(
                    error = error!!,
                    onRetry = {
                        state.setLoading(true)
                        state.clearError()
                        // Trigger reload - the editor will handle this
                    }
                )
            }
            content != null -> {
                // Main editor content
                EditorMainContent(
                    content = content!!,
                    project = project,
                    file = file,
                    onContentChange = { newContent ->
                        onModified()
                        state.updateContent(newContent)
                    },
                    onSave = onSave
                )
            }
            else -> {
                EmptyState()
            }
        }
    }
}

@Composable
private fun LoadingState() {
    val colors = HyveThemeColors.colors

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(HyveSpacing.lg)
        ) {
            CircularProgressIndicator()
            Text(
                text = "Loading UI file...",
                color = colors.textSecondary
            )
        }
    }
}

@Composable
private fun ErrorState(
    error: String,
    onRetry: () -> Unit
) {
    val colors = HyveThemeColors.colors

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(HyveSpacing.lg)
        ) {
            Text(
                text = "Failed to load UI file",
                color = colors.error
            )
            Text(
                text = error,
                color = colors.textSecondary
            )
            OutlinedButton(onClick = onRetry) {
                Text(
                    text = "Retry",
                    color = colors.honey
                )
            }
        }
    }
}

@Composable
private fun EmptyState() {
    val colors = HyveThemeColors.colors

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "Empty UI file",
            color = colors.textSecondary
        )
    }
}

/**
 * Main editor content with canvas and panels.
 *
 * This integrates the HyUI Studio canvas for visual editing of .ui files.
 */
@Composable
private fun EditorMainContent(
    content: String,
    project: Project,
    file: VirtualFile,
    onContentChange: (String) -> Unit,
    onSave: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val colors = HyveThemeColors.colors
    val validationState = rememberValidationPanelState()
    val deps = LocalEditorDependencies.current

    // Track status messages from canvas operations
    var statusMessage by remember { mutableStateOf<String?>(null) }

    // Create canvas state with status callback
    val canvasState = rememberCanvasState(
        onStatusChange = { message -> statusMessage = message }
    )

    // Create toolbox state for drag-to-canvas
    val toolboxState = rememberElementToolboxState()

    // Create hierarchy tree state
    val hierarchyState = rememberHierarchyTreeState()

    // Derive project root (still needed for property inspector)
    val projectRoot = remember(project) {
        project.basePath?.let { java.nio.file.Path.of(it) }
    }

    val projectResourcesPath = deps.projectResourcesPath

    val currentFilePath = remember(file) {
        file.toNioPath()
    }

    // Initialize asset loader with project resources for overlay resolution
    val assetLoader = remember(projectResourcesPath) {
        val assetsPath = deps.assetSettings.getAssetsZipPath()
        AssetLoader(assetsPath, projectResourcesPath = projectResourcesPath)
    }

    val itemRegistry = remember(assetLoader) {
        ItemRegistry(assetLoader)
    }

    // Initialize RuntimeSchemaRegistry - auto-discovers from Client folder if configured
    // Uses cached schema if available, otherwise discovers in background
    var runtimeRegistry by remember { mutableStateOf<RuntimeSchemaRegistry?>(null) }

    LaunchedEffect(Unit) {
        // Run schema discovery/loading on a background thread
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            runtimeRegistry = deps.schemaProvider.getOrDiscoverSchema()
        }
    }

    // Update canvas state with runtime registry when it becomes available
    LaunchedEffect(runtimeRegistry) {
        canvasState.setRuntimeRegistry(runtimeRegistry)
    }

    // Set up drop handler for toolbox -> canvas drag-drop
    LaunchedEffect(canvasState) {
        toolboxState.dropHandler = DropHandler { schema: ElementSchema, windowPosition: Offset ->
            // Convert window position to screen position relative to canvas
            // The schema is dropped at this position
            canvasState.addElementAtScreenPosition(
                schema = schema,
                screenX = windowPosition.x,
                screenY = windowPosition.y
            )
        }
    }

    // Parse the .ui content and set up canvas
    var parseError by remember { mutableStateOf<String?>(null) }
    var currentDocument by remember { mutableStateOf<UIDocument?>(null) }
    var rawDocument by remember { mutableStateOf<UIDocument?>(null) }
    val editDeltaTracker = remember { EditDeltaTracker() }
    var sidecarApplied by remember { mutableStateOf(false) }

    // Set tracker on canvas state
    LaunchedEffect(canvasState, editDeltaTracker) {
        canvasState.setEditDeltaTracker(editDeltaTracker)
    }

    // Read IDE theme outside LaunchedEffect (composable context required)
    val ideIsDark = JewelTheme.isDark

    LaunchedEffect(content) {
        try {
            parseError = null
            val warnings = mutableListOf<String>()

            // Dual parse: raw document for export, resolved document for rendering

            // Parse raw document (UIParser preserves @refs, spreads, expressions)
            val rawParser = UIParser(content) { warning ->
                warnings.add(warning.toString())
            }
            val rawResult = rawParser.parse()
            when (rawResult) {
                is com.hyve.ui.core.result.Result.Failure -> {
                    parseError = rawResult.error.joinToString("\n") { it.toString() }
                    return@LaunchedEffect
                }
                is com.hyve.ui.core.result.Result.Success -> {
                    // Assign auto-IDs to ID-less elements so the delta system
                    // can track structural changes (move, delete, reparent).
                    // Auto-IDs are stripped during export to keep .ui files clean.
                    rawDocument = rawResult.value.let { doc ->
                        doc.copy(root = doc.root.assignAutoIds())
                    }
                }
            }

            // Parse resolved document (VariableAwareParser resolves all references)
            // Fallback to raw document on failure (DL-008)
            val resolvedDoc = try {
                // Build import search paths: vanilla Interface dir as fallback for $Common etc.
                val importSearchPaths = buildList {
                    deps.assetSettings.getInterfaceFolderPath()?.let { interfacePath ->
                        if (interfacePath.toFile().isDirectory) add(interfacePath)
                    }
                }
                val resolvedParser = VariableAwareParser.forSourceWithPath(content, currentFilePath, importSearchPaths) { warning ->
                    warnings.add(warning)
                }
                when (val result = resolvedParser.parse()) {
                    is com.hyve.ui.core.result.Result.Success -> result.value.document
                    is com.hyve.ui.core.result.Result.Failure -> {
                        warnings.add("Variable resolution failed, using raw document for rendering")
                        null
                    }
                }
            } catch (e: Exception) {
                warnings.add("Variable resolution error: ${e.message}")
                null
            }

            // Assign auto-IDs to resolved document (same depth-first scheme as raw)
            // so deltas recorded against the visual tree match the raw document.
            val document = (resolvedDoc ?: rawDocument!!).let { doc ->
                doc.copy(root = doc.root.assignAutoIds())
            }
            currentDocument = document
            canvasState.setRootElement(document.root)
            canvasState.setSourceFilePath(File(file.path))

            if (warnings.isNotEmpty()) {
                validationState.setParseWarnings(warnings)
            }

            // Clear delta tracker on re-parse (new baseline)
            editDeltaTracker.clear()

            // Load sidecar metadata only on initial load (not on re-parse from canvas edits)
            if (!sidecarApplied) {
                sidecarApplied = true
                val uiFile = File(file.path)
                val sidecarExists = EditorMetadataIO.sidecarFile(uiFile, project.basePath).exists()
                val sidecar = EditorMetadataIO.load(uiFile, project.basePath)
                canvasState.setShowGrid(sidecar.editorState.showGrid)
                // Default dark canvas to IDE theme when no sidecar exists yet
                val darkDefault = if (sidecarExists) sidecar.editorState.darkCanvas else ideIsDark
                canvasState.setDarkCanvas(darkDefault)
                canvasState.setZoom(sidecar.editorState.zoom)
                canvasState.setPanOffset(
                    Offset(sidecar.editorState.scrollX, sidecar.editorState.scrollY)
                )
                canvasState.setShowScreenshot(sidecar.editorState.showScreenshot)
                canvasState.setScreenshotOpacity(sidecar.editorState.screenshotOpacity)
                val mode = ScreenshotMode.entries.find { it.name == sidecar.editorState.screenshotMode }
                    ?: ScreenshotMode.NO_HUD
                canvasState.setScreenshotMode(mode)
                // Apply per-element metadata (previewItemId)
                val root = canvasState.rootElement.value
                if (root != null && sidecar.elementMetadata.isNotEmpty()) {
                    val updated = root.mapDescendants { el ->
                        val elId = el.id?.value ?: return@mapDescendants el
                        val data = sidecar.elementMetadata[elId] ?: return@mapDescendants el
                        if (data.previewItemId != null) {
                            el.copy(metadata = el.metadata.copy(previewItemId = data.previewItemId))
                        } else el
                    }
                    canvasState.setRootElement(updated)
                }
            }
        } catch (e: Exception) {
            parseError = "Parse error: ${e.message}"
        }
    }

    // Track modifications from canvas state to notify the editor.
    // Uses snapshotFlow on treeVersion to reliably detect every tree edit.
    var lastExportedContent by remember { mutableStateOf(content) }

    // Export pipeline: apply deltas to raw document, preserving @refs and syntax
    LaunchedEffect(Unit) {
        snapshotFlow { canvasState.treeVersion.value }
            .collect { version ->
                if (version > 0L && editDeltaTracker.hasChanges()) {
                    val raw = rawDocument
                    if (raw != null) {
                        try {
                            val withDeltas = editDeltaTracker.applyTo(raw)
                            val exporter = UIExporter()
                            val exportResult = exporter.export(withDeltas)
                            when (exportResult) {
                                is com.hyve.ui.core.result.Result.Success -> {
                                    val exported = exportResult.value
                                    if (exported != lastExportedContent) {
                                        lastExportedContent = exported
                                        onContentChange(exported)
                                    }
                                }
                                is com.hyve.ui.core.result.Result.Failure -> {
                                    onContentChange(lastExportedContent)
                                }
                            }
                        } catch (_: Exception) {
                            onContentChange(lastExportedContent)
                        }
                    }
                }
            }
    }

    // Persist editor-only state to .ui.meta sidecar (debounced)
    // Helper to build current sidecar metadata from canvas state
    fun buildSidecarMetadata(): EditorMetadata? {
        val root = canvasState.rootElement.value ?: return null
        val elementMeta = mutableMapOf<String, ElementEditorData>()
        root.visitDescendants { el ->
            val id = el.id?.value ?: return@visitDescendants
            val itemId = el.metadata.previewItemId
            if (itemId != null) {
                elementMeta[id] = ElementEditorData(previewItemId = itemId)
            }
        }
        val pan = canvasState.panOffset.value
        return EditorMetadata(
            editorState = EditorViewState(
                showGrid = canvasState.showGrid.value,
                darkCanvas = canvasState.darkCanvas.value,
                zoom = canvasState.zoom.value,
                scrollX = pan.x,
                scrollY = pan.y,
                showScreenshot = canvasState.showScreenshot.value,
                screenshotOpacity = canvasState.screenshotOpacity.value,
                screenshotMode = canvasState.screenshotMode.value.name
            ),
            elementMetadata = elementMeta
        )
    }

    // Debounced auto-save of sidecar metadata
    @OptIn(FlowPreview::class)
    LaunchedEffect(Unit) {
        snapshotFlow {
            // Read all tracked state so changes trigger the flow
            canvasState.treeVersion.value
            canvasState.showGrid.value
            canvasState.darkCanvas.value
            canvasState.zoom.value
            canvasState.panOffset.value
            canvasState.showScreenshot.value
            canvasState.screenshotOpacity.value
            canvasState.screenshotMode.value
        }
            .debounce(500L)
            .collect {
                val meta = buildSidecarMetadata() ?: return@collect
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    EditorMetadataIO.save(File(file.path), meta, project.basePath)
                }
            }
    }

    // Save sidecar immediately on disposal (tab switch, editor close)
    // so pending debounced changes aren't lost
    DisposableEffect(canvasState) {
        onDispose {
            val meta = buildSidecarMetadata()
            if (meta != null) {
                EditorMetadataIO.save(File(file.path), meta, project.basePath)
            }
        }
    }

    // Autosave document content after edits (debounced).
    // Mirrors the sidecar debounce pattern above but with a longer delay
    // since this involves VFS I/O and DocumentListener sync.
    @OptIn(FlowPreview::class)
    LaunchedEffect(Unit) {
        snapshotFlow { canvasState.treeVersion.value }
            .debounce(1500L)
            .collect { version ->
                if (version > 0L) {
                    onSave()
                }
            }
    }

    // Save document immediately on disposal (tab close, editor switch)
    // so pending debounced changes aren't lost.
    DisposableEffect(Unit) {
        onDispose {
            val app = ApplicationManager.getApplication()
            val save = Runnable { onSave() }
            if (app.isDispatchThread) {
                save.run()
            } else {
                app.invokeAndWait(save)
            }
        }
    }

    // Composer modal state — set to non-null to open the composer for an element
    var composerElement by remember { mutableStateOf<ElementDefinition?>(null) }
    // Track the original UIElement so we can apply changes back on close
    var composerSourceElement by remember { mutableStateOf<UIElement?>(null) }

    // Shared callback to open the Composer for a given element (spec 10 FR-1/FR-4/FR-5)
    val openComposerForElement: (UIElement) -> Unit = { element ->
        val registry = runtimeRegistry?.takeIf { it.isLoaded }
        val schema = registry?.getElementSchema(element.type)
        if (schema != null) {
            composerSourceElement = element
            composerElement = element.toElementDefinition(schema)
        }
    }

    // Global hotkey handler — intercepts in preview (capture) phase so hotkeys
    // work regardless of which child panel has focus.
    val editorFocusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { editorFocusRequester.requestFocus() }

    // Hotkey reference panel state
    var showHotkeyReference by remember { mutableStateOf(false) }

    // Sidebar and divider state
    val sidebarState = rememberSidebarState()
    val dividerState = rememberDraggableDividerState()

    // Main layout with drag preview overlay
    Box(modifier = modifier.fillMaxSize()
        .focusRequester(editorFocusRequester)
        .focusable()
        .onPreviewKeyEvent { event ->
            // Handle Ctrl+/ for hotkey reference panel before other hotkeys
            if (event.type == KeyEventType.KeyDown &&
                event.key == Key.Slash &&
                event.isCtrlPressed
            ) {
                showHotkeyReference = !showHotkeyReference
                return@onPreviewKeyEvent true
            }
            // Dismiss hotkey panel on Escape
            if (showHotkeyReference &&
                event.type == KeyEventType.KeyDown &&
                event.key == Key.Escape
            ) {
                showHotkeyReference = false
                return@onPreviewKeyEvent true
            }
            handleEditorKeyEvent(
                event = event,
                canvasState = canvasState,
                composerOpen = composerElement != null,
                onSave = onSave,
                onOpenComposer = openComposerForElement
            )
        }
    ) {
        Row(
            modifier = Modifier.fillMaxSize()
        ) {
            // Left panel: Element Toolbox with animated width collapse
            val animatedLeftWidth by animateDpAsState(
                targetValue = sidebarState.leftTargetWidth,
                animationSpec = tween(durationMillis = 200)
            )

            Box(
                modifier = Modifier
                    .width(animatedLeftWidth)
                    .fillMaxHeight()
                    .clipToBounds()
            ) {
                // Collapsed stub sits behind the content; becomes visible as content clips away
                CollapsedSidebarStub(
                    isLeft = true,
                    onClick = { sidebarState.toggleLeft() }
                )
                // Full-width content overlays the stub when expanded
                if (animatedLeftWidth > sidebarState.collapsedWidth) {
                    ElementToolbox(
                        canvasState = canvasState,
                        toolboxState = toolboxState,
                        runtimeRegistry = runtimeRegistry?.takeIf { it.isLoaded },
                        onCollapse = { sidebarState.toggleLeft() },
                        modifier = Modifier.requiredWidth(sidebarState.leftExpandedWidth).fillMaxHeight()
                    )
                }
            }

            // Center: Visual Canvas
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clipToBounds()
            ) {
                if (parseError != null) {
                    // Show parse error
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(colors.deepNight),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(HyveSpacing.sm)
                        ) {
                            Text(
                                text = "Failed to parse UI file",
                                color = colors.error
                            )
                            Text(
                                text = parseError!!,
                                color = colors.textSecondary
                            )
                        }
                    }
                } else {
                    // Show the interactive canvas
                    CanvasView(
                        state = canvasState,
                        validationState = validationState,
                        assetLoader = assetLoader,
                        itemRegistry = itemRegistry,
                        onOpenComposer = openComposerForElement,
                        composerOpen = composerElement != null,
                        onToggleHotkeyReference = { showHotkeyReference = !showHotkeyReference },
                        modifier = Modifier.fillMaxSize()
                    )
                }

                // Status bar at the bottom
                if (statusMessage != null) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(HyveSpacing.sm)
                            .background(colors.slate.copy(alpha = 0.9f))
                            .padding(horizontal = HyveSpacing.sm, vertical = HyveSpacing.xs)
                    ) {
                        Text(
                            text = statusMessage!!,
                            color = colors.textSecondary
                        )
                    }
                }
            }

            // Right panel: Hierarchy Tree and Property Inspector with animated width collapse
            val animatedRightWidth by animateDpAsState(
                targetValue = sidebarState.rightTargetWidth,
                animationSpec = tween(durationMillis = 200)
            )

            Box(
                modifier = Modifier
                    .width(animatedRightWidth)
                    .fillMaxHeight()
                    .clipToBounds(),
                contentAlignment = Alignment.TopEnd
            ) {
                // Collapsed stub sits behind the content; becomes visible as content clips away
                CollapsedSidebarStub(
                    isLeft = false,
                    onClick = { sidebarState.toggleRight() },
                    modifier = Modifier.align(Alignment.TopStart)
                )
                // Full-width content overlays the stub when expanded
                if (animatedRightWidth > sidebarState.collapsedWidth) {
                    Column(
                        modifier = Modifier
                            .requiredWidth(sidebarState.rightExpandedWidth)
                            .onSizeChanged { size -> dividerState.setHeight(size.height.toFloat()) }
                            .fillMaxHeight()
                    ) {
                        HierarchyTree(
                            canvasState = canvasState,
                            state = hierarchyState,
                            onOpenComposer = openComposerForElement,
                            onCollapse = { sidebarState.toggleRight() },
                            modifier = Modifier
                                .weight(dividerState.weightRatio)
                                .fillMaxWidth()
                        )

                        DraggableDivider(
                            onDrag = { deltaY ->
                                dividerState.adjustByPixelDelta(deltaY)
                            }
                        )

                        SchemaPropertyInspector(
                            canvasState = canvasState,
                            runtimeRegistry = runtimeRegistry?.takeIf { it.isLoaded },
                            document = currentDocument,
                            assetLoader = assetLoader,
                            itemRegistry = itemRegistry,
                            projectRoot = projectRoot,
                            currentFilePath = currentFilePath,
                            onOpenComposer = openComposerForElement,
                            modifier = Modifier
                                .weight(1f - dividerState.weightRatio)
                                .fillMaxWidth()
                        )
                    }
                }
            }
        }

        // Property Composer modal overlay
        composerElement?.let { element ->
            val composerState = rememberComposerModalState(element)
            val wordBankState = rememberWordBankState()

            // Discover importable .ui files from the same directory (spec 06 FR-4)
            // Runs off-EDT to avoid freezing the UI on large directories.
            val importDiscoveryService = remember { ImportDiscoveryService() }
            var importableFiles by remember { mutableStateOf(emptyList<com.hyve.ui.composer.model.ImportableFile>()) }
            LaunchedEffect(file) {
                importableFiles = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                    val parentDir = file.parent?.let { File(it.path) }
                    if (parentDir != null) {
                        importDiscoveryService.discoverImports(parentDir, excludeFileName = file.name)
                    } else {
                        emptyList()
                    }
                }
            }

            ComposerModal(
                state = composerState,
                wordBankState = wordBankState,
                sourceElement = composerSourceElement,
                importableFiles = importableFiles,
                assetLoader = assetLoader,
                onClose = {
                    // Apply changes from the Composer back to the canvas (spec 10 FR-3)
                    val source = composerSourceElement
                    if (source != null) {
                        val updatedDefinition = composerState.element.value
                        val updatedElement = updatedDefinition.applyTo(source)

                        // No-change detection: UIElement is a data class with structural equality
                        if (updatedElement != source) {
                            // Check if element still exists in the tree (may have been deleted while Composer was open)
                            val root = canvasState.rootElement.value
                            val stillExists = if (source.id != null && root != null) {
                                root.findDescendantById(source.id) != null
                            } else {
                                root != null
                            }

                            if (stillExists) {
                                // Use command system for single-step undo support
                                val command = ReplaceElementCommand.forElement(source, updatedElement)
                                canvasState.executeCommand(command, allowMerge = false)
                            }
                        }
                    }
                    composerElement = null
                    composerSourceElement = null
                }
            )
        }

        // Drag preview overlay (rendered on top of everything)
        DragPreviewOverlay(toolboxState = toolboxState)

        // Hotkey reference panel overlay
        if (showHotkeyReference) {
            HotkeyReferencePanel(
                onDismiss = { showHotkeyReference = false }
            )
        }
    }
}

/**
 * Thin collapsed sidebar stub shown when a sidebar is fully collapsed.
 * Clicking re-expands the panel.
 */
@Composable
private fun CollapsedSidebarStub(
    isLeft: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = HyveThemeColors.colors
    val chevronText = if (isLeft) "\u25B6" else "\u25C0" // point toward the panel to expand

    Box(
        modifier = modifier
            .width(HyveSpacing.md)
            .fillMaxHeight()
            .background(colors.deepNight)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = chevronText,
            color = colors.textPrimary.copy(alpha = 0.6f),
            style = TextStyle(fontSize = 8.sp)
        )
    }
}

