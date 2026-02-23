package com.hyve.ui.components.dialogs

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.hyve.common.compose.HyveShapes
import com.hyve.common.compose.HyveSpacing
import com.hyve.ui.services.assets.AssetLoader
import com.hyve.ui.services.assets.AssetService
import kotlinx.coroutines.launch
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.Orientation
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.TextField
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.IconButton
import org.jetbrains.jewel.ui.component.DefaultButton
import org.jetbrains.jewel.ui.component.OutlinedButton
import org.jetbrains.jewel.ui.component.Divider
import org.jetbrains.jewel.ui.component.CircularProgressIndicator
import org.jetbrains.jewel.ui.icons.AllIconsKeys

/**
 * Modal dialog for browsing and selecting assets from Hytale's Assets.zip.
 *
 * Features:
 * - Tree view of directories (left panel, lazy-loaded)
 * - Grid view of files in selected directory (center)
 * - Thumbnail preview for images (64x64, lazy-loaded)
 * - Search/filter bar
 * - Preview pane for selected image
 * - OK/Cancel buttons
 */
@Composable
fun AssetBrowserDialog(
    assetLoader: AssetLoader,
    onDismiss: () -> Unit,
    onAssetSelected: (String) -> Unit,
    initialPath: String = ""
) {
    // Create asset service from loader
    val assetService = remember(assetLoader) { AssetService(assetLoader) }

    // State
    var selectedDirectory by remember { mutableStateOf("") }
    var selectedFile by remember { mutableStateOf<AssetService.AssetEntry?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var isSearching by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Directory tree and files
    var directoryTree by remember { mutableStateOf<AssetService.DirectoryNode?>(null) }
    var currentFiles by remember { mutableStateOf<List<AssetService.AssetEntry>>(emptyList()) }
    var searchResults by remember { mutableStateOf<List<AssetService.AssetEntry>>(emptyList()) }

    val coroutineScope = rememberCoroutineScope()

    // Load directory tree on mount
    LaunchedEffect(Unit) {
        isLoading = true
        errorMessage = null
        try {
            if (!assetService.isAvailable) {
                errorMessage = "Assets.zip not found or not accessible"
                isLoading = false
                return@LaunchedEffect
            }
            directoryTree = assetService.getDirectoryTree()

            // If initial path provided, navigate to it
            if (initialPath.isNotEmpty()) {
                val parentDir = initialPath.substringBeforeLast('/', "")
                selectedDirectory = parentDir
            }
        } catch (e: Exception) {
            errorMessage = "Failed to load assets: ${e.message}"
        }
        isLoading = false
    }

    // Load files when directory changes
    LaunchedEffect(selectedDirectory) {
        if (!isSearching) {
            currentFiles = assetService.listDirectory(selectedDirectory, imagesOnly = false)
                .filter { it.isImage || it.isDirectory }
        }
    }

    // Search functionality
    LaunchedEffect(searchQuery) {
        if (searchQuery.isBlank()) {
            isSearching = false
            searchResults = emptyList()
        } else {
            isSearching = true
            searchResults = assetService.search(searchQuery, imagesOnly = true, maxResults = 100)
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false
        )
    ) {
        Box(
            modifier = Modifier
                .width(900.dp)
                .height(600.dp)
                .background(JewelTheme.globalColors.panelBackground, HyveShapes.dialog)
                .border(1.dp, JewelTheme.globalColors.borders.normal, HyveShapes.dialog)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header
                DialogHeader(
                    searchQuery = searchQuery,
                    onSearchQueryChange = { searchQuery = it },
                    onDismiss = onDismiss
                )

                Divider(Orientation.Horizontal)

                // Main content
                if (isLoading) {
                    Box(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                } else if (errorMessage != null) {
                    Box(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                key = AllIconsKeys.General.Warning,
                                contentDescription = null,
                                tint = JewelTheme.globalColors.text.error,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(HyveSpacing.lg))
                            Text(
                                text = errorMessage ?: "Unknown error",
                                color = JewelTheme.globalColors.text.error
                            )
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier.weight(1f).fillMaxWidth()
                    ) {
                        // Directory tree (left panel)
                        if (!isSearching) {
                            DirectoryTreePanel(
                                tree = directoryTree,
                                selectedDirectory = selectedDirectory,
                                onDirectorySelected = { path ->
                                    selectedDirectory = path
                                    selectedFile = null
                                },
                                modifier = Modifier.width(220.dp).fillMaxHeight()
                            )

                            Divider(Orientation.Vertical)
                        }

                        // File grid (center)
                        FileGridPanel(
                            files = if (isSearching) searchResults else currentFiles.filter { !it.isDirectory },
                            directories = if (isSearching) emptyList() else currentFiles.filter { it.isDirectory },
                            selectedFile = selectedFile,
                            assetLoader = assetLoader,
                            onFileSelected = { selectedFile = it },
                            onFileDoubleClick = { file ->
                                onAssetSelected(file.path)
                            },
                            onDirectoryDoubleClick = { dir ->
                                selectedDirectory = dir.path
                                selectedFile = null
                            },
                            isSearching = isSearching,
                            modifier = Modifier.weight(1f).fillMaxHeight()
                        )

                        Divider(Orientation.Vertical)

                        // Preview panel (right)
                        PreviewPanel(
                            selectedFile = selectedFile,
                            assetLoader = assetLoader,
                            modifier = Modifier.width(200.dp).fillMaxHeight()
                        )
                    }
                }

                Divider(Orientation.Horizontal)

                // Footer with buttons
                DialogFooter(
                    selectedFile = selectedFile,
                    onCancel = onDismiss,
                    onSelect = {
                        selectedFile?.let { onAssetSelected(it.path) }
                    }
                )
            }
        }
    }
}

@Composable
private fun DialogHeader(
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val searchState = rememberTextFieldState(searchQuery)

    // Sync external changes to the text field state
    LaunchedEffect(searchQuery) {
        if (searchState.text.toString() != searchQuery) {
            searchState.setTextAndPlaceCursorAtEnd(searchQuery)
        }
    }

    // Notify parent when text changes
    LaunchedEffect(searchState.text) {
        val currentText = searchState.text.toString()
        if (currentText != searchQuery) {
            onSearchQueryChange(currentText)
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(HyveSpacing.lg),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Asset Browser",
            fontWeight = FontWeight.Bold,
            color = JewelTheme.globalColors.text.normal
        )

        Spacer(modifier = Modifier.width(HyveSpacing.xl))

        // Search icon
        Icon(
            key = AllIconsKeys.Actions.Search,
            contentDescription = "Search",
            modifier = Modifier.size(16.dp)
        )

        Spacer(modifier = Modifier.width(HyveSpacing.sm))

        // Search field
        TextField(
            state = searchState,
            modifier = Modifier.weight(1f),
            placeholder = { Text("Search images...") }
        )

        // Clear button
        if (searchQuery.isNotEmpty()) {
            Spacer(modifier = Modifier.width(HyveSpacing.sm))
            IconButton(onClick = { onSearchQueryChange("") }) {
                Icon(
                    key = AllIconsKeys.Actions.Close,
                    contentDescription = "Clear search"
                )
            }
        }

        Spacer(modifier = Modifier.width(HyveSpacing.lg))

        IconButton(onClick = onDismiss) {
            Icon(
                key = AllIconsKeys.Actions.Close,
                contentDescription = "Close"
            )
        }
    }
}

@Composable
private fun DirectoryTreePanel(
    tree: AssetService.DirectoryNode?,
    selectedDirectory: String,
    onDirectorySelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.background(JewelTheme.globalColors.panelBackground.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Text(
                text = "Directories",
                fontWeight = FontWeight.SemiBold,
                color = JewelTheme.globalColors.text.normal,
                modifier = Modifier.padding(HyveSpacing.md)
            )

            if (tree != null) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = HyveSpacing.sm)
                ) {
                    item(key = "root") {
                        DirectoryItem(
                            node = tree,
                            selectedPath = selectedDirectory,
                            onSelected = onDirectorySelected,
                            depth = 0
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DirectoryItem(
    node: AssetService.DirectoryNode,
    selectedPath: String,
    onSelected: (String) -> Unit,
    depth: Int
) {
    var isExpanded by remember { mutableStateOf(depth < 1 || selectedPath.startsWith(node.path)) }
    val isSelected = node.path == selectedPath
    val hasChildren = node.children.isNotEmpty()

    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onSelected(node.path) }
                .background(
                    if (isSelected) JewelTheme.globalColors.outlines.focused.copy(alpha = 0.2f)
                    else Color.Transparent
                )
                .padding(
                    start = (12 + depth * 16).dp,
                    top = HyveSpacing.smd,
                    bottom = HyveSpacing.smd,
                    end = HyveSpacing.sm
                ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Expand/collapse button
            if (hasChildren) {
                IconButton(
                    onClick = { isExpanded = !isExpanded },
                    modifier = Modifier.size(20.dp)
                ) {
                    Icon(
                        key = if (isExpanded) AllIconsKeys.General.ChevronDown else AllIconsKeys.General.ChevronRight,
                        contentDescription = if (isExpanded) "Collapse" else "Expand",
                        modifier = Modifier.size(16.dp)
                    )
                }
            } else {
                Spacer(modifier = Modifier.width(20.dp))
            }

            Spacer(modifier = Modifier.width(HyveSpacing.xs))

            Icon(
                key = AllIconsKeys.Nodes.Folder,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = if (isSelected) JewelTheme.globalColors.outlines.focused else JewelTheme.globalColors.text.info
            )

            Spacer(modifier = Modifier.width(HyveSpacing.sm))

            Text(
                text = node.displayName,
                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                color = if (isSelected) JewelTheme.globalColors.text.normal else JewelTheme.globalColors.text.normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )

            // Show image count badge
            val imageCount = node.totalImageCount()
            if (imageCount > 0) {
                Text(
                    text = "$imageCount",
                    color = JewelTheme.globalColors.text.info.copy(alpha = 0.6f)
                )
            }
        }

        // Children
        if (isExpanded && hasChildren) {
            node.children.forEach { child ->
                key(child.path) {
                    DirectoryItem(
                        node = child,
                        selectedPath = selectedPath,
                        onSelected = onSelected,
                        depth = depth + 1
                    )
                }
            }
        }
    }
}

@Composable
private fun FileGridPanel(
    files: List<AssetService.AssetEntry>,
    directories: List<AssetService.AssetEntry>,
    selectedFile: AssetService.AssetEntry?,
    assetLoader: AssetLoader,
    onFileSelected: (AssetService.AssetEntry) -> Unit,
    onFileDoubleClick: (AssetService.AssetEntry) -> Unit,
    onDirectoryDoubleClick: (AssetService.AssetEntry) -> Unit,
    isSearching: Boolean,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.background(JewelTheme.globalColors.panelBackground)) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Breadcrumb or search results header
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(JewelTheme.globalColors.panelBackground.copy(alpha = 0.3f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = HyveSpacing.md, vertical = HyveSpacing.sm),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isSearching) {
                        Icon(
                            key = AllIconsKeys.Actions.Search,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = JewelTheme.globalColors.text.info
                        )
                        Spacer(modifier = Modifier.width(HyveSpacing.sm))
                        Text(
                            text = "Search results: ${files.size} images found",
                            color = JewelTheme.globalColors.text.info
                        )
                    } else {
                        Text(
                            text = "${directories.size} folders, ${files.size} images",
                            color = JewelTheme.globalColors.text.info
                        )
                    }
                }
            }

            if (files.isEmpty() && directories.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            key = if (isSearching) AllIconsKeys.Actions.Search else AllIconsKeys.Nodes.Folder,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = JewelTheme.globalColors.text.info.copy(alpha = 0.5f)
                        )
                        Spacer(modifier = Modifier.height(HyveSpacing.sm))
                        Text(
                            text = if (isSearching) "No images found" else "No images in this folder",
                            color = JewelTheme.globalColors.text.info.copy(alpha = 0.7f)
                        )
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 80.dp),
                    contentPadding = PaddingValues(HyveSpacing.sm),
                    horizontalArrangement = Arrangement.spacedBy(HyveSpacing.sm),
                    verticalArrangement = Arrangement.spacedBy(HyveSpacing.sm),
                    modifier = Modifier.fillMaxSize()
                ) {
                    // Directories first (if not searching)
                    if (!isSearching) {
                        items(directories, key = { "dir_${it.path}" }) { dir ->
                            DirectoryGridItem(
                                directory = dir,
                                onDoubleClick = { onDirectoryDoubleClick(dir) }
                            )
                        }
                    }

                    // Then files
                    items(files, key = { "file_${it.path}" }) { file ->
                        FileGridItem(
                            file = file,
                            isSelected = selectedFile?.path == file.path,
                            assetLoader = assetLoader,
                            onClick = { onFileSelected(file) },
                            onDoubleClick = { onFileDoubleClick(file) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DirectoryGridItem(
    directory: AssetService.AssetEntry,
    onDoubleClick: () -> Unit
) {
    var clickCount by remember { mutableStateOf(0) }
    var lastClickTime by remember { mutableStateOf(0L) }

    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clickable {
                val currentTime = System.currentTimeMillis()
                if (currentTime - lastClickTime < 400) {
                    onDoubleClick()
                    clickCount = 0
                } else {
                    clickCount = 1
                }
                lastClickTime = currentTime
            }
            .background(JewelTheme.globalColors.panelBackground.copy(alpha = 0.5f), HyveShapes.dialog)
            .border(1.dp, JewelTheme.globalColors.borders.normal.copy(alpha = 0.2f), HyveShapes.dialog)
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(HyveSpacing.xs),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                key = AllIconsKeys.Nodes.Folder,
                contentDescription = null,
                modifier = Modifier.size(32.dp),
                tint = JewelTheme.globalColors.outlines.focused.copy(alpha = 0.8f)
            )
            Spacer(modifier = Modifier.height(HyveSpacing.xs))
            Text(
                text = directory.name,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                color = JewelTheme.globalColors.text.normal
            )
        }
    }
}

@Composable
private fun FileGridItem(
    file: AssetService.AssetEntry,
    isSelected: Boolean,
    assetLoader: AssetLoader,
    onClick: () -> Unit,
    onDoubleClick: () -> Unit
) {
    var clickCount by remember { mutableStateOf(0) }
    var lastClickTime by remember { mutableStateOf(0L) }

    // Lazy load thumbnail
    var thumbnail by remember { mutableStateOf<ImageBitmap?>(null) }
    var isLoadingThumbnail by remember { mutableStateOf(true) }

    LaunchedEffect(file.path) {
        isLoadingThumbnail = true
        thumbnail = assetLoader.loadTexture(file.path)
        isLoadingThumbnail = false
    }

    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clickable {
                val currentTime = System.currentTimeMillis()
                if (currentTime - lastClickTime < 400) {
                    onDoubleClick()
                    clickCount = 0
                } else {
                    clickCount = 1
                    onClick()
                }
                lastClickTime = currentTime
            }
            .background(
                if (isSelected) JewelTheme.globalColors.outlines.focused.copy(alpha = 0.2f)
                else JewelTheme.globalColors.panelBackground,
                HyveShapes.dialog
            )
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = if (isSelected) JewelTheme.globalColors.outlines.focused
                else JewelTheme.globalColors.borders.normal.copy(alpha = 0.2f),
                shape = HyveShapes.dialog
            )
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(HyveSpacing.xs),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Thumbnail
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clip(HyveShapes.card)
                    .background(JewelTheme.globalColors.panelBackground),
                contentAlignment = Alignment.Center
            ) {
                when {
                    isLoadingThumbnail -> {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    thumbnail != null -> {
                        Image(
                            bitmap = thumbnail!!,
                            contentDescription = file.name,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit
                        )
                    }
                    else -> {
                        Icon(
                            key = AllIconsKeys.General.Error,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                            tint = JewelTheme.globalColors.text.info.copy(alpha = 0.5f)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(HyveSpacing.xs))

            // File name
            Text(
                text = file.name,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                color = if (isSelected) JewelTheme.globalColors.text.normal else JewelTheme.globalColors.text.normal
            )
        }
    }
}

@Composable
private fun PreviewPanel(
    selectedFile: AssetService.AssetEntry?,
    assetLoader: AssetLoader,
    modifier: Modifier = Modifier
) {
    var previewImage by remember { mutableStateOf<ImageBitmap?>(null) }
    var isLoading by remember { mutableStateOf(false) }

    LaunchedEffect(selectedFile) {
        if (selectedFile != null && selectedFile.isImage) {
            isLoading = true
            previewImage = assetLoader.loadTexture(selectedFile.path)
            isLoading = false
        } else {
            previewImage = null
        }
    }

    Box(
        modifier = modifier.background(JewelTheme.globalColors.panelBackground.copy(alpha = 0.5f))
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(HyveSpacing.md),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Preview",
                fontWeight = FontWeight.SemiBold,
                color = JewelTheme.globalColors.text.normal
            )

            Spacer(modifier = Modifier.height(HyveSpacing.md))

            if (selectedFile == null) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .clip(HyveShapes.dialog)
                        .background(JewelTheme.globalColors.panelBackground),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Select an image\nto preview",
                        color = JewelTheme.globalColors.text.info.copy(alpha = 0.6f),
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                // Preview image
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .clip(HyveShapes.dialog)
                        .background(
                            // Checkerboard pattern for transparency
                            JewelTheme.globalColors.panelBackground
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    when {
                        isLoading -> {
                            CircularProgressIndicator(modifier = Modifier.size(32.dp))
                        }
                        previewImage != null -> {
                            Image(
                                bitmap = previewImage!!,
                                contentDescription = selectedFile.name,
                                modifier = Modifier.fillMaxSize().padding(HyveSpacing.xs),
                                contentScale = ContentScale.Fit
                            )
                        }
                        else -> {
                            Icon(
                                key = AllIconsKeys.General.Error,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = JewelTheme.globalColors.text.info.copy(alpha = 0.5f)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(HyveSpacing.md))

                // File info
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.Start
                ) {
                    Text(
                        text = selectedFile.name,
                        fontWeight = FontWeight.Medium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        color = JewelTheme.globalColors.text.normal
                    )

                    Spacer(modifier = Modifier.height(HyveSpacing.xs))

                    Text(
                        text = selectedFile.path,
                        color = JewelTheme.globalColors.text.info.copy(alpha = 0.7f),
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis
                    )

                    if (previewImage != null) {
                        Spacer(modifier = Modifier.height(HyveSpacing.xs))
                        Text(
                            text = "${previewImage!!.width} x ${previewImage!!.height}",
                            color = JewelTheme.globalColors.text.info.copy(alpha = 0.7f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DialogFooter(
    selectedFile: AssetService.AssetEntry?,
    onCancel: () -> Unit,
    onSelect: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(HyveSpacing.lg),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (selectedFile != null) {
            Text(
                text = selectedFile.path,
                color = JewelTheme.globalColors.text.info,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.width(HyveSpacing.lg))
        } else {
            Spacer(modifier = Modifier.weight(1f))
        }

        OutlinedButton(onClick = onCancel) {
            Text("Cancel")
        }

        Spacer(modifier = Modifier.width(HyveSpacing.sm))

        DefaultButton(
            onClick = onSelect,
            enabled = selectedFile != null
        ) {
            Text("Select")
        }
    }
}

/**
 * State holder for managing the Asset Browser dialog.
 */
@Composable
fun rememberAssetBrowserState(): AssetBrowserState {
    return remember { AssetBrowserState() }
}

class AssetBrowserState {
    var isOpen by mutableStateOf(false)
        private set

    private var onSelectCallback: ((String) -> Unit)? = null
    private var initialPathValue: String = ""

    val initialPath: String
        get() = initialPathValue

    fun open(initialPath: String = "", onSelect: (String) -> Unit) {
        this.initialPathValue = initialPath
        this.onSelectCallback = onSelect
        isOpen = true
    }

    fun close() {
        isOpen = false
        onSelectCallback = null
        initialPathValue = ""
    }

    fun select(path: String) {
        onSelectCallback?.invoke(path)
        close()
    }
}
