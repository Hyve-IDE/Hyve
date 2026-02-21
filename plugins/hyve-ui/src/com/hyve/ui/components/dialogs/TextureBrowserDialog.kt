package com.hyve.ui.components.dialogs

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
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
import com.hyve.ui.services.assets.AssetService.AssetEntry
import com.hyve.ui.services.assets.AssetService.DirectoryNode
import java.nio.file.Path
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import androidx.compose.foundation.text.input.rememberTextFieldState
import org.jetbrains.jewel.ui.Orientation
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.TextField
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.IconButton
import org.jetbrains.jewel.ui.component.DefaultButton
import org.jetbrains.jewel.ui.component.OutlinedButton
import org.jetbrains.jewel.ui.component.Divider
import org.jetbrains.jewel.ui.component.CircularProgressIndicator
import org.jetbrains.jewel.ui.icons.AllIconsKeys

private enum class AssetSource { PROJECT, HYTALE }

private data class TextureCategory(
    val displayName: String,
    val path: String,
    val imageCount: Int,
    val source: AssetSource = AssetSource.HYTALE
)

@Composable
fun TextureBrowserDialog(
    assetLoader: AssetLoader,
    projectResourcesPath: Path? = null,
    onDismiss: () -> Unit,
    onTextureSelected: (String) -> Unit,
    initialPath: String = ""
) {
    val assetService = remember(assetLoader) { AssetService(assetLoader) }

    var selectedCategory by remember { mutableStateOf<TextureCategory?>(null) }
    var selectedTexture by remember { mutableStateOf<AssetEntry?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var isSearching by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    var projectCategories by remember { mutableStateOf<List<TextureCategory>>(emptyList()) }
    var hytaleCategories by remember { mutableStateOf<List<TextureCategory>>(emptyList()) }
    var categories by remember { mutableStateOf<List<TextureCategory>>(emptyList()) }
    var currentTextures by remember { mutableStateOf<List<AssetEntry>>(emptyList()) }
    var searchResults by remember { mutableStateOf<List<AssetEntry>>(emptyList()) }

    // Whether the project section should be shown at all (even if empty)
    val hasProjectSection = projectResourcesPath != null

    // Resolve the project/mod display name from manifest.json
    var projectSectionTitle by remember { mutableStateOf("Project") }

    // Extract categories from both sources
    LaunchedEffect(Unit) {
        isLoading = true
        errorMessage = null
        try {
            // Read mod name from manifest.json (projectRoot = resources/..)
            if (projectResourcesPath != null) {
                val projectRoot = projectResourcesPath.parent
                if (projectRoot != null) {
                    val manifestFile = projectRoot.resolve("manifest.json").toFile()
                    if (manifestFile.exists() && manifestFile.canRead()) {
                        try {
                            val json = Json { ignoreUnknownKeys = true }
                            val obj = json.parseToJsonElement(manifestFile.readText()).jsonObject
                            val name = obj["Name"]?.jsonPrimitive?.content
                            if (!name.isNullOrBlank()) {
                                projectSectionTitle = name
                            }
                        } catch (_: Exception) {
                            // manifest parse failed — keep default "Project"
                        }
                    }
                }
            }

            val projCats = mutableListOf<TextureCategory>()
            val hytaleCats = mutableListOf<TextureCategory>()

            // Scan project resources
            if (projectResourcesPath != null && projectResourcesPath.toFile().exists()) {
                val projTree = assetService.scanFilesystemTree(projectResourcesPath)
                fun collectProjectCategories(node: DirectoryNode) {
                    val directImageCount = node.files.count { it.isImage }
                    if (directImageCount > 0) {
                        projCats.add(
                            TextureCategory(
                                displayName = node.path.replace("/", " / "),
                                path = node.path,
                                imageCount = directImageCount,
                                source = AssetSource.PROJECT
                            )
                        )
                    }
                    node.children.forEach { collectProjectCategories(it) }
                }
                projTree.children.forEach { collectProjectCategories(it) }
            }

            // Scan Hytale assets ZIP
            if (assetService.isAvailable) {
                val tree = assetService.getDirectoryTree()
                fun collectHytaleCategories(node: DirectoryNode) {
                    val directImageCount = node.files.count { it.isImage }
                    if (directImageCount > 0) {
                        hytaleCats.add(
                            TextureCategory(
                                displayName = node.path.replace("/", " / "),
                                path = node.path,
                                imageCount = directImageCount,
                                source = AssetSource.HYTALE
                            )
                        )
                    }
                    node.children.forEach { collectHytaleCategories(it) }
                }
                tree.children.forEach { collectHytaleCategories(it) }
            }

            projectCategories = projCats.sortedBy { it.path.lowercase() }
            hytaleCategories = hytaleCats.sortedBy { it.path.lowercase() }
            categories = projCats + hytaleCats

            if (!hasProjectSection && hytaleCats.isEmpty()) {
                errorMessage = "No texture sources available"
            }

            // Pre-select category from initialPath
            if (initialPath.isNotEmpty()) {
                val parentPath = initialPath.substringBeforeLast('/', "")
                val matchCat = categories.find { it.path == parentPath }
                if (matchCat != null) {
                    selectedCategory = matchCat
                }
            }
        } catch (e: Exception) {
            errorMessage = "Failed to load textures: ${e.message}"
        }
        isLoading = false
    }

    // Load textures when category changes
    LaunchedEffect(selectedCategory) {
        if (isSearching) return@LaunchedEffect
        val cat = selectedCategory
        if (cat != null) {
            currentTextures = when (cat.source) {
                AssetSource.PROJECT -> {
                    if (projectResourcesPath != null) {
                        assetService.listFilesystemDirectory(projectResourcesPath, cat.path, imagesOnly = true)
                    } else emptyList()
                }
                AssetSource.HYTALE -> {
                    assetService.listDirectory(cat.path, imagesOnly = true)
                        .filter { !it.isDirectory }
                }
            }
        } else {
            // "All Images" — merge project + ZIP, capped at 500
            val projImages = if (projectResourcesPath != null && projectResourcesPath.toFile().exists()) {
                assetService.scanFilesystemTree(projectResourcesPath).let { tree ->
                    val files = mutableListOf<AssetEntry>()
                    fun collect(node: DirectoryNode) {
                        files.addAll(node.files.filter { it.isImage })
                        node.children.forEach { collect(it) }
                    }
                    collect(tree)
                    files
                }
            } else emptyList()

            val zipImages = if (assetService.isAvailable) {
                val entries = assetService.getAllEntries()
                entries.filter { it.isImage && !it.isDirectory }
            } else emptyList()

            // Project first, deduplicate by path
            val seen = mutableSetOf<String>()
            val merged = mutableListOf<AssetEntry>()
            for (entry in projImages) {
                if (seen.add(entry.path)) merged.add(entry)
            }
            for (entry in zipImages) {
                if (seen.add(entry.path)) merged.add(entry)
            }
            currentTextures = merged.take(500)
        }

        // Pre-select the texture matching initialPath
        if (selectedTexture == null && initialPath.isNotEmpty()) {
            selectedTexture = currentTextures.find { it.path == initialPath }
        }
    }

    // Search both sources
    LaunchedEffect(searchQuery) {
        if (searchQuery.isBlank()) {
            isSearching = false
            searchResults = emptyList()
        } else {
            isSearching = true
            val projResults = if (projectResourcesPath != null && projectResourcesPath.toFile().exists()) {
                assetService.searchFilesystem(projectResourcesPath, searchQuery, imagesOnly = true, maxResults = 100)
            } else emptyList()

            val zipResults = if (assetService.isAvailable) {
                assetService.search(searchQuery, imagesOnly = true, maxResults = 100)
            } else emptyList()

            // Project first, deduplicate
            val seen = mutableSetOf<String>()
            val merged = mutableListOf<AssetEntry>()
            for (entry in projResults) {
                if (seen.add(entry.path)) merged.add(entry)
            }
            for (entry in zipResults) {
                if (seen.add(entry.path)) merged.add(entry)
            }
            searchResults = merged.take(100)
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .width(900.dp)
                .height(600.dp)
                .background(JewelTheme.globalColors.panelBackground, HyveShapes.dialog)
                .border(1.dp, JewelTheme.globalColors.borders.normal, HyveShapes.dialog)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                TextureBrowserHeader(
                    searchQuery = searchQuery,
                    onSearchQueryChange = { searchQuery = it },
                    onDismiss = onDismiss
                )

                Divider(Orientation.Horizontal)

                if (isLoading) {
                    Box(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator()
                            Spacer(modifier = Modifier.height(HyveSpacing.lg))
                            Text(
                                text = "Loading textures...",
                                color = JewelTheme.globalColors.text.normal
                            )
                        }
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
                    Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        if (!isSearching) {
                            TextureCategoryPanel(
                                projectCategories = projectCategories,
                                hytaleCategories = hytaleCategories,
                                hasProjectSection = hasProjectSection,
                                projectSectionTitle = projectSectionTitle,
                                selectedCategory = selectedCategory,
                                onCategorySelected = { cat ->
                                    selectedCategory = cat
                                    selectedTexture = null
                                },
                                modifier = Modifier.width(200.dp).fillMaxHeight()
                            )
                            Divider(Orientation.Vertical)
                        }

                        TextureGridPanel(
                            textures = if (isSearching) searchResults else currentTextures,
                            selectedTexture = selectedTexture,
                            assetLoader = assetLoader,
                            onClick = { selectedTexture = it },
                            onDoubleClick = { onTextureSelected(it.path) },
                            isSearching = isSearching,
                            modifier = Modifier.weight(1f).fillMaxHeight()
                        )

                        Divider(Orientation.Vertical)

                        TexturePreviewPanel(
                            selectedTexture = selectedTexture,
                            assetLoader = assetLoader,
                            modifier = Modifier.width(220.dp).fillMaxHeight()
                        )
                    }
                }

                Divider(Orientation.Horizontal)

                TextureBrowserFooter(
                    selectedTexture = selectedTexture,
                    onCancel = onDismiss,
                    onSelect = {
                        selectedTexture?.let { onTextureSelected(it.path) }
                    }
                )
            }
        }
    }
}

@Composable
private fun TextureBrowserHeader(
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val textFieldState = rememberTextFieldState(searchQuery)

    LaunchedEffect(searchQuery) {
        if (textFieldState.text.toString() != searchQuery) {
            textFieldState.edit { replace(0, length, searchQuery) }
        }
    }

    LaunchedEffect(textFieldState.text) {
        val newValue = textFieldState.text.toString()
        if (newValue != searchQuery) {
            onSearchQueryChange(newValue)
        }
    }

    Row(
        modifier = Modifier.fillMaxWidth().padding(HyveSpacing.lg),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Texture Browser",
            color = JewelTheme.globalColors.text.normal,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.width(HyveSpacing.xl))

        TextField(
            state = textFieldState,
            modifier = Modifier.weight(1f),
            placeholder = { Text("Search textures...") },
            leadingIcon = {
                Icon(
                    key = AllIconsKeys.Actions.Search,
                    contentDescription = "Search"
                )
            },
            trailingIcon = {
                if (textFieldState.text.isNotEmpty()) {
                    IconButton(onClick = {
                        textFieldState.edit { replace(0, length, "") }
                        onSearchQueryChange("")
                    }) {
                        Icon(
                            key = AllIconsKeys.Actions.Close,
                            contentDescription = "Clear search"
                        )
                    }
                }
            }
        )

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
private fun TextureCategoryPanel(
    projectCategories: List<TextureCategory>,
    hytaleCategories: List<TextureCategory>,
    hasProjectSection: Boolean,
    projectSectionTitle: String,
    selectedCategory: TextureCategory?,
    onCategorySelected: (TextureCategory?) -> Unit,
    modifier: Modifier = Modifier
) {
    var projectExpanded by remember { mutableStateOf(true) }
    var hytaleExpanded by remember { mutableStateOf(!hasProjectSection) }

    Box(modifier = modifier.background(JewelTheme.globalColors.panelBackground.copy(alpha = 0.5f))) {
        Column(modifier = Modifier.fillMaxSize()) {
            Text(
                text = "Folders",
                color = JewelTheme.globalColors.text.normal,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(HyveSpacing.md)
            )

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = HyveSpacing.sm)
            ) {
                // "All Images" always visible
                item(key = "all") {
                    TextureCategoryItem(
                        name = "All Images",
                        imageCount = null,
                        isSelected = selectedCategory == null,
                        onClick = { onCategorySelected(null) }
                    )
                }

                // Project/mod section — always shown when project has resources/ path
                if (hasProjectSection) {
                    item(key = "section-project") {
                        SectionHeader(
                            title = projectSectionTitle,
                            expanded = projectExpanded,
                            onClick = { projectExpanded = !projectExpanded }
                        )
                    }

                    if (projectExpanded) {
                        if (projectCategories.isEmpty()) {
                            item(key = "proj-empty") {
                                Text(
                                    text = "No images in resources/",
                                    color = JewelTheme.globalColors.text.info.copy(alpha = 0.5f),
                                    modifier = Modifier.padding(horizontal = HyveSpacing.xl, vertical = HyveSpacing.smd)
                                )
                            }
                        } else {
                            items(projectCategories, key = { "proj-${it.path}" }) { cat ->
                                TextureCategoryItem(
                                    name = cat.displayName,
                                    imageCount = cat.imageCount,
                                    isSelected = selectedCategory == cat,
                                    onClick = { onCategorySelected(cat) }
                                )
                            }
                        }
                    }
                }

                // Hytale section
                if (hytaleCategories.isNotEmpty()) {
                    item(key = "section-hytale") {
                        SectionHeader(
                            title = "Hytale",
                            expanded = hytaleExpanded,
                            onClick = { hytaleExpanded = !hytaleExpanded }
                        )
                    }

                    if (hytaleExpanded) {
                        items(hytaleCategories, key = { "hytale-${it.path}" }) { cat ->
                            TextureCategoryItem(
                                name = cat.displayName,
                                imageCount = cat.imageCount,
                                isSelected = selectedCategory == cat,
                                onClick = { onCategorySelected(cat) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(
    title: String,
    expanded: Boolean,
    onClick: () -> Unit
) {
    val chevron = if (expanded) "\u25BC" else "\u25B6" // down / right triangle

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .background(JewelTheme.globalColors.panelBackground.copy(alpha = 0.3f))
            .padding(horizontal = HyveSpacing.md, vertical = HyveSpacing.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = chevron,
            color = JewelTheme.globalColors.text.info,
            modifier = Modifier.width(16.dp)
        )
        Spacer(modifier = Modifier.width(HyveSpacing.xs))
        Text(
            text = title,
            fontWeight = FontWeight.SemiBold,
            color = JewelTheme.globalColors.text.normal
        )
    }
}

@Composable
private fun TextureCategoryItem(
    name: String,
    imageCount: Int?,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .background(
                if (isSelected) JewelTheme.globalColors.outlines.focused.copy(alpha = 0.2f)
                else Color.Transparent
            )
            .padding(horizontal = HyveSpacing.md, vertical = HyveSpacing.smd),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            key = if (name == "All Images") AllIconsKeys.Nodes.Package else AllIconsKeys.Nodes.Folder,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = if (isSelected) JewelTheme.globalColors.outlines.focused else JewelTheme.globalColors.text.info
        )

        Spacer(modifier = Modifier.width(HyveSpacing.smd))

        Text(
            text = name,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
            color = JewelTheme.globalColors.text.normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )

        if (imageCount != null) {
            Spacer(modifier = Modifier.width(HyveSpacing.xs))
            Text(
                text = "$imageCount",
                color = JewelTheme.globalColors.text.info.copy(alpha = 0.6f)
            )
        }
    }
}

@Composable
private fun TextureGridPanel(
    textures: List<AssetEntry>,
    selectedTexture: AssetEntry?,
    assetLoader: AssetLoader,
    onClick: (AssetEntry) -> Unit,
    onDoubleClick: (AssetEntry) -> Unit,
    isSearching: Boolean,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.background(JewelTheme.globalColors.panelBackground)) {
        Column(modifier = Modifier.fillMaxSize()) {
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
                            text = "Search results: ${textures.size} textures found",
                            color = JewelTheme.globalColors.text.info
                        )
                    } else {
                        Text(
                            text = "${textures.size} textures",
                            color = JewelTheme.globalColors.text.info
                        )
                    }
                }
            }

            if (textures.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            key = if (isSearching) AllIconsKeys.Actions.Search else AllIconsKeys.FileTypes.Image,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = JewelTheme.globalColors.text.info.copy(alpha = 0.5f)
                        )
                        Spacer(modifier = Modifier.height(HyveSpacing.sm))
                        Text(
                            text = if (isSearching) "No textures found" else "No images in this folder",
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
                    items(textures, key = { it.path }) { texture ->
                        TextureGridTile(
                            texture = texture,
                            isSelected = selectedTexture?.path == texture.path,
                            assetLoader = assetLoader,
                            onClick = { onClick(texture) },
                            onDoubleClick = { onDoubleClick(texture) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TextureGridTile(
    texture: AssetEntry,
    isSelected: Boolean,
    assetLoader: AssetLoader,
    onClick: () -> Unit,
    onDoubleClick: () -> Unit
) {
    var lastClickTime by remember { mutableStateOf(0L) }

    var thumbnailBitmap by remember { mutableStateOf<ImageBitmap?>(null) }
    var isLoadingThumbnail by remember { mutableStateOf(true) }

    LaunchedEffect(texture.path) {
        isLoadingThumbnail = true
        thumbnailBitmap = assetLoader.loadTexture(texture.path)
        isLoadingThumbnail = false
    }

    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(HyveShapes.dialog)
            .background(
                if (isSelected) JewelTheme.globalColors.outlines.focused.copy(alpha = 0.1f)
                else JewelTheme.globalColors.panelBackground
            )
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = if (isSelected) JewelTheme.globalColors.outlines.focused
                else JewelTheme.globalColors.borders.normal.copy(alpha = 0.2f),
                shape = HyveShapes.dialog
            )
            .clickable {
                val now = System.currentTimeMillis()
                if (now - lastClickTime < 400) {
                    onDoubleClick()
                } else {
                    onClick()
                }
                lastClickTime = now
            }
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(HyveSpacing.xs),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clip(HyveShapes.card)
                    .background(JewelTheme.globalColors.panelBackground.copy(alpha = 0.5f)),
                contentAlignment = Alignment.Center
            ) {
                when {
                    isLoadingThumbnail -> CircularProgressIndicator()
                    thumbnailBitmap != null -> {
                        Image(
                            bitmap = thumbnailBitmap!!,
                            contentDescription = texture.name,
                            modifier = Modifier.fillMaxSize().padding(HyveSpacing.xxs),
                            contentScale = ContentScale.Fit
                        )
                    }
                    else -> {
                        Icon(
                            key = AllIconsKeys.FileTypes.Image,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp),
                            tint = JewelTheme.globalColors.text.info.copy(alpha = 0.5f)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(HyveSpacing.xs))

            Text(
                text = texture.name,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                color = JewelTheme.globalColors.text.normal
            )
        }
    }
}

@Composable
private fun TexturePreviewPanel(
    selectedTexture: AssetEntry?,
    assetLoader: AssetLoader,
    modifier: Modifier = Modifier
) {
    var previewImage by remember { mutableStateOf<ImageBitmap?>(null) }
    var isLoading by remember { mutableStateOf(false) }

    LaunchedEffect(selectedTexture) {
        if (selectedTexture != null) {
            isLoading = true
            previewImage = assetLoader.loadTexture(selectedTexture.path)
            isLoading = false
        } else {
            previewImage = null
        }
    }

    Box(modifier = modifier.background(JewelTheme.globalColors.panelBackground.copy(alpha = 0.5f))) {
        Column(
            modifier = Modifier.fillMaxSize().padding(HyveSpacing.md),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Preview",
                color = JewelTheme.globalColors.text.normal,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(modifier = Modifier.height(HyveSpacing.md))

            if (selectedTexture == null) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .clip(HyveShapes.dialog)
                        .background(JewelTheme.globalColors.panelBackground),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Select a texture\nto preview",
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
                        .background(JewelTheme.globalColors.panelBackground),
                    contentAlignment = Alignment.Center
                ) {
                    when {
                        isLoading -> CircularProgressIndicator()
                        previewImage != null -> {
                            Image(
                                bitmap = previewImage!!,
                                contentDescription = selectedTexture.name,
                                modifier = Modifier.fillMaxSize().padding(HyveSpacing.sm),
                                contentScale = ContentScale.Fit
                            )
                        }
                        else -> {
                            Icon(
                                key = AllIconsKeys.FileTypes.Image,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = JewelTheme.globalColors.text.info.copy(alpha = 0.5f)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(HyveSpacing.md))

                // Texture info
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.Start
                ) {
                    Text(
                        text = selectedTexture.name,
                        fontWeight = FontWeight.Medium,
                        color = JewelTheme.globalColors.text.normal,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(HyveSpacing.xs))

                    Text(
                        text = selectedTexture.path,
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

                    if (selectedTexture.size > 0) {
                        Spacer(modifier = Modifier.height(HyveSpacing.xxs))
                        val sizeKb = selectedTexture.size / 1024.0
                        Text(
                            text = if (sizeKb >= 1024) "%.1f MB".format(sizeKb / 1024) else "%.1f KB".format(sizeKb),
                            color = JewelTheme.globalColors.text.info.copy(alpha = 0.7f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TextureBrowserFooter(
    selectedTexture: AssetEntry?,
    onCancel: () -> Unit,
    onSelect: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(HyveSpacing.lg),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (selectedTexture != null) {
            Text(
                text = selectedTexture.path,
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
            enabled = selectedTexture != null
        ) {
            Text("Select")
        }
    }
}
