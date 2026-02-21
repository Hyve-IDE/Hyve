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
import com.hyve.ui.services.items.ItemDefinition
import com.hyve.ui.services.items.ItemRegistry
import kotlinx.coroutines.launch
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

/**
 * Modal dialog for browsing and selecting items from Hytale's Assets.zip.
 *
 * Features:
 * - Category list (left panel)
 * - Grid view of items in selected category (center)
 * - Icon thumbnail preview (lazy-loaded from Icons/ItemsGenerated/)
 * - Search/filter bar
 * - Preview pane for selected item
 * - OK/Cancel buttons
 */
@Composable
fun ItemPickerDialog(
    assetLoader: AssetLoader,
    onDismiss: () -> Unit,
    onItemSelected: (String) -> Unit,
    initialItemId: String = ""
) {
    // Create item registry from loader
    val itemRegistry = remember(assetLoader) { ItemRegistry(assetLoader) }

    // State
    var selectedCategory by remember { mutableStateOf<String?>(null) }
    var selectedItem by remember { mutableStateOf<ItemDefinition?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var isSearching by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Items data
    var categories by remember { mutableStateOf<List<String>>(emptyList()) }
    var currentItems by remember { mutableStateOf<List<ItemDefinition>>(emptyList()) }
    var searchResults by remember { mutableStateOf<List<ItemDefinition>>(emptyList()) }

    val coroutineScope = rememberCoroutineScope()

    // Load categories on mount
    LaunchedEffect(Unit) {
        isLoading = true
        errorMessage = null
        try {
            if (!itemRegistry.isAvailable) {
                errorMessage = "Assets.zip not found or not accessible"
                isLoading = false
                return@LaunchedEffect
            }

            categories = itemRegistry.getCategories()

            // If initial item provided, find its category and select it
            if (initialItemId.isNotEmpty()) {
                val item = itemRegistry.getItem(initialItemId)
                if (item != null) {
                    selectedCategory = item.category
                    selectedItem = item
                }
            }
        } catch (e: Exception) {
            errorMessage = "Failed to load items: ${e.message}"
        }
        isLoading = false
    }

    // Load items when category changes
    LaunchedEffect(selectedCategory) {
        if (!isSearching && selectedCategory != null) {
            currentItems = itemRegistry.search("", category = selectedCategory, maxResults = 500)
        } else if (!isSearching && selectedCategory == null) {
            currentItems = itemRegistry.getAllItems().take(500)
        }
    }

    // Search functionality
    LaunchedEffect(searchQuery) {
        if (searchQuery.isBlank()) {
            isSearching = false
            searchResults = emptyList()
        } else {
            isSearching = true
            searchResults = itemRegistry.search(searchQuery, maxResults = 100)
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
                ItemPickerHeader(
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
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator()
                            Spacer(modifier = Modifier.height(HyveSpacing.lg))
                            Text(
                                text = "Loading items...",
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
                    Row(
                        modifier = Modifier.weight(1f).fillMaxWidth()
                    ) {
                        // Category list (left panel)
                        if (!isSearching) {
                            CategoryListPanel(
                                categories = categories,
                                selectedCategory = selectedCategory,
                                onCategorySelected = { category ->
                                    selectedCategory = category
                                    selectedItem = null
                                },
                                modifier = Modifier.width(180.dp).fillMaxHeight()
                            )

                            Divider(Orientation.Vertical)
                        }

                        // Item grid (center)
                        ItemGridPanel(
                            items = if (isSearching) searchResults else currentItems,
                            selectedItem = selectedItem,
                            assetLoader = assetLoader,
                            onItemSelected = { selectedItem = it },
                            onItemDoubleClick = { item ->
                                onItemSelected(item.id)
                            },
                            isSearching = isSearching,
                            modifier = Modifier.weight(1f).fillMaxHeight()
                        )

                        Divider(Orientation.Vertical)

                        // Preview panel (right)
                        ItemPreviewPanel(
                            selectedItem = selectedItem,
                            assetLoader = assetLoader,
                            modifier = Modifier.width(200.dp).fillMaxHeight()
                        )
                    }
                }

                Divider(Orientation.Horizontal)

                // Footer with buttons
                ItemPickerFooter(
                    selectedItem = selectedItem,
                    onCancel = onDismiss,
                    onSelect = {
                        selectedItem?.let { onItemSelected(it.id) }
                    }
                )
            }
        }
    }
}

@Composable
private fun ItemPickerHeader(
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onDismiss: () -> Unit
) {
    // Jewel TextField uses TextFieldState instead of value/onValueChange
    val textFieldState = rememberTextFieldState(searchQuery)

    // Sync external value -> textFieldState
    LaunchedEffect(searchQuery) {
        if (textFieldState.text.toString() != searchQuery) {
            textFieldState.edit {
                replace(0, length, searchQuery)
            }
        }
    }

    // Sync textFieldState -> external value
    LaunchedEffect(textFieldState.text) {
        val newValue = textFieldState.text.toString()
        if (newValue != searchQuery) {
            onSearchQueryChange(newValue)
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(HyveSpacing.lg),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Item Picker",
            color = JewelTheme.globalColors.text.normal,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.width(HyveSpacing.xl))

        // Search field
        TextField(
            state = textFieldState,
            modifier = Modifier.weight(1f),
            placeholder = { Text("Search items...") },
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
private fun CategoryListPanel(
    categories: List<String>,
    selectedCategory: String?,
    onCategorySelected: (String?) -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.background(JewelTheme.globalColors.panelBackground.copy(alpha = 0.5f))
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Text(
                text = "Categories",
                color = JewelTheme.globalColors.text.normal,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(HyveSpacing.md)
            )

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = HyveSpacing.sm)
            ) {
                // "All Items" option
                item(key = "all") {
                    CategoryItem(
                        name = "All Items",
                        isSelected = selectedCategory == null,
                        onClick = { onCategorySelected(null) }
                    )
                }

                // Category items
                items(categories, key = { it }) { category ->
                    CategoryItem(
                        name = category,
                        isSelected = category == selectedCategory,
                        onClick = { onCategorySelected(category) }
                    )
                }
            }
        }
    }
}

@Composable
private fun CategoryItem(
    name: String,
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
            .padding(horizontal = HyveSpacing.md, vertical = HyveSpacing.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            key = if (name == "All Items") AllIconsKeys.Nodes.Package else AllIconsKeys.Nodes.Folder,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = if (isSelected) JewelTheme.globalColors.outlines.focused else JewelTheme.globalColors.text.info
        )

        Spacer(modifier = Modifier.width(HyveSpacing.sm))

        Text(
            text = name,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (isSelected) JewelTheme.globalColors.text.normal else JewelTheme.globalColors.text.normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun ItemGridPanel(
    items: List<ItemDefinition>,
    selectedItem: ItemDefinition?,
    assetLoader: AssetLoader,
    onItemSelected: (ItemDefinition) -> Unit,
    onItemDoubleClick: (ItemDefinition) -> Unit,
    isSearching: Boolean,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.background(JewelTheme.globalColors.panelBackground)) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header with count
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
                            text = "Search results: ${items.size} items found",
                            color = JewelTheme.globalColors.text.info
                        )
                    } else {
                        Text(
                            text = "${items.size} items",
                            color = JewelTheme.globalColors.text.info
                        )
                    }
                }
            }

            if (items.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            key = if (isSearching) AllIconsKeys.Actions.Search else AllIconsKeys.Nodes.Package,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = JewelTheme.globalColors.text.info.copy(alpha = 0.5f)
                        )
                        Spacer(modifier = Modifier.height(HyveSpacing.sm))
                        Text(
                            text = if (isSearching) "No items found" else "No items in this category",
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
                    items(items, key = { it.id }) { item ->
                        ItemGridTile(
                            item = item,
                            isSelected = selectedItem?.id == item.id,
                            assetLoader = assetLoader,
                            onClick = { onItemSelected(item) },
                            onDoubleClick = { onItemDoubleClick(item) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ItemGridTile(
    item: ItemDefinition,
    isSelected: Boolean,
    assetLoader: AssetLoader,
    onClick: () -> Unit,
    onDoubleClick: () -> Unit
) {
    var clickCount by remember { mutableStateOf(0) }
    var lastClickTime by remember { mutableStateOf(0L) }

    // Lazy load icon
    var iconBitmap by remember { mutableStateOf<ImageBitmap?>(null) }
    var isLoadingIcon by remember { mutableStateOf(true) }

    LaunchedEffect(item.iconPath) {
        isLoadingIcon = true
        iconBitmap = assetLoader.loadTexture(item.iconPath)
        isLoadingIcon = false
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
                color = if (isSelected) JewelTheme.globalColors.outlines.focused else JewelTheme.globalColors.borders.normal.copy(alpha = 0.2f),
                shape = HyveShapes.dialog
            )
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
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(HyveSpacing.xs),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Icon
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clip(HyveShapes.card)
                    .background(JewelTheme.globalColors.panelBackground.copy(alpha = 0.5f)),
                contentAlignment = Alignment.Center
            ) {
                when {
                    isLoadingIcon -> {
                        CircularProgressIndicator()
                    }
                    iconBitmap != null -> {
                        Image(
                            bitmap = iconBitmap!!,
                            contentDescription = item.displayLabel,
                            modifier = Modifier.fillMaxSize().padding(HyveSpacing.xxs),
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

            // Item name
            Text(
                text = item.displayLabel,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                color = if (isSelected) JewelTheme.globalColors.text.normal else JewelTheme.globalColors.text.normal
            )
        }
    }
}

@Composable
private fun ItemPreviewPanel(
    selectedItem: ItemDefinition?,
    assetLoader: AssetLoader,
    modifier: Modifier = Modifier
) {
    var previewImage by remember { mutableStateOf<ImageBitmap?>(null) }
    var isLoading by remember { mutableStateOf(false) }

    LaunchedEffect(selectedItem) {
        if (selectedItem != null) {
            isLoading = true
            previewImage = assetLoader.loadTexture(selectedItem.iconPath)
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
                color = JewelTheme.globalColors.text.normal,
                fontWeight = FontWeight.SemiBold
            )

            Spacer(modifier = Modifier.height(HyveSpacing.md))

            if (selectedItem == null) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .clip(HyveShapes.dialog)
                        .background(JewelTheme.globalColors.panelBackground),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Select an item\nto preview",
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
                        isLoading -> {
                            CircularProgressIndicator()
                        }
                        previewImage != null -> {
                            Image(
                                bitmap = previewImage!!,
                                contentDescription = selectedItem.displayLabel,
                                modifier = Modifier.fillMaxSize().padding(HyveSpacing.sm),
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

                // Item info
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.Start
                ) {
                    Text(
                        text = selectedItem.displayLabel,
                        fontWeight = FontWeight.Medium,
                        color = JewelTheme.globalColors.text.normal,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(HyveSpacing.xs))

                    Text(
                        text = "ID: ${selectedItem.id}",
                        color = JewelTheme.globalColors.text.info.copy(alpha = 0.7f)
                    )

                    Text(
                        text = "Category: ${selectedItem.category}",
                        color = JewelTheme.globalColors.text.info.copy(alpha = 0.7f)
                    )

                    if (selectedItem.quality != null) {
                        Text(
                            text = "Quality: ${selectedItem.quality}",
                            color = getQualityColor(selectedItem.quality)
                        )
                    }

                    if (selectedItem.tags.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(HyveSpacing.xs))
                        Text(
                            text = "Tags: ${selectedItem.tags.joinToString(", ")}",
                            color = JewelTheme.globalColors.text.info.copy(alpha = 0.7f),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun getQualityColor(quality: String): Color {
    return when (quality.lowercase()) {
        "common" -> Color(0xFF9E9E9E)
        "uncommon" -> Color(0xFF4CAF50)
        "rare" -> Color(0xFF2196F3)
        "epic" -> Color(0xFF9C27B0)
        "legendary" -> Color(0xFFFF9800)
        "developer" -> Color(0xFFE91E63)
        else -> JewelTheme.globalColors.text.info
    }
}

@Composable
private fun ItemPickerFooter(
    selectedItem: ItemDefinition?,
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
        if (selectedItem != null) {
            Text(
                text = selectedItem.id,
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
            enabled = selectedItem != null
        ) {
            Text("Select")
        }
    }
}

/**
 * State holder for managing the Item Picker dialog.
 */
@Composable
fun rememberItemPickerState(): ItemPickerState {
    return remember { ItemPickerState() }
}

class ItemPickerState {
    var isOpen by mutableStateOf(false)
        private set

    private var onSelectCallback: ((String) -> Unit)? = null
    private var initialItemIdValue: String = ""

    val initialItemId: String
        get() = initialItemIdValue

    fun open(initialItemId: String = "", onSelect: (String) -> Unit) {
        this.initialItemIdValue = initialItemId
        this.onSelectCallback = onSelect
        isOpen = true
    }

    fun close() {
        isOpen = false
        onSelectCallback = null
        initialItemIdValue = ""
    }

    fun select(itemId: String) {
        onSelectCallback?.invoke(itemId)
        close()
    }
}
