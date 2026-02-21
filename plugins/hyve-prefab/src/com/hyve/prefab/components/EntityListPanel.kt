@file:OptIn(ExperimentalFoundationApi::class)

package com.hyve.prefab.components

import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hyve.common.compose.HyveThemeColors
import com.hyve.prefab.domain.EntityId
import com.hyve.prefab.domain.PrefabEntity
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.TextField

/**
 * A row in the flattened entity list.
 */
private sealed class ListRow {
    abstract val key: Any

    data class Header(
        val displayName: String,
        val count: Int,
        val isExpanded: Boolean,
        val entityIds: List<EntityId>,
    ) : ListRow() {
        override val key: Any get() = "group:$displayName"
    }

    data class Entity(
        val entity: PrefabEntity,
        val displayName: String,
        val indented: Boolean,
    ) : ListRow() {
        override val key: Any get() = "e:${entity.id.value}"
    }
}

/**
 * Left panel showing a filterable, grouped list of entities in the prefab.
 * Entities with the same display name are collapsed into expandable groups.
 */
@Composable
fun EntityListPanel(
    entities: List<PrefabEntity>,
    selectedEntityId: EntityId?,
    filterText: String,
    onFilterTextChanged: (String) -> Unit,
    onSelectEntity: (EntityId) -> Unit,
    onDeleteEntity: (PrefabEntity, Int) -> Unit,
    onJumpToSource: ((EntityId) -> Unit)? = null,
    listState: LazyListState,
    modifier: Modifier = Modifier,
) {
    val colors = HyveThemeColors.colors

    val filteredEntities = if (filterText.isBlank()) {
        entities
    } else {
        val lower = filterText.lowercase()
        entities.filter { entity ->
            entity.displayName.lowercase().contains(lower) ||
                entity.entityType.lowercase().contains(lower) ||
                entity.components.keys.any { it.value.lowercase().contains(lower) }
        }
    }

    // Group by cached displayName property (computed once at entity construction).
    // Sort everything deterministically: groups alphabetical, members by ID.
    val sortedGroups: List<Pair<String, List<PrefabEntity>>> =
        filteredEntities
            .groupBy { it.displayName }
            .entries
            .sortedBy { it.key }
            .map { (name, members) -> name to members.sortedBy { it.id.value } }

    // Track which groups are expanded (default: expanded).
    val expandedGroups = remember { mutableStateMapOf<String, Boolean>() }

    // Flatten groups into a single list for LazyColumn.
    val rows: List<ListRow> = buildList {
        for ((name, members) in sortedGroups) {
            if (members.size == 1) {
                add(ListRow.Entity(members.first(), name, indented = false))
            } else {
                val isExpanded = expandedGroups[name] ?: false
                add(ListRow.Header(name, members.size, isExpanded, members.map { it.id }))
                if (isExpanded) {
                    for (entity in members) {
                        add(ListRow.Entity(entity, name, indented = true))
                    }
                }
            }
        }
    }

    Column(modifier = modifier.fillMaxHeight()) {
        // Search bar + header
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            val filterState = rememberTextFieldState("")
            LaunchedEffect(filterState) {
                snapshotFlow { filterState.text.toString() }
                    .collect { onFilterTextChanged(it) }
            }
            // Sync external filterText changes (e.g. from metadata restore)
            LaunchedEffect(filterText) {
                if (filterState.text.toString() != filterText) {
                    filterState.setTextAndPlaceCursorAtEnd(filterText)
                }
            }
            TextField(
                state = filterState,
                placeholder = { Text("Filter entities...") },
                modifier = Modifier.fillMaxWidth().height(28.dp),
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Entities",
                    style = TextStyle(
                        color = colors.textPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                    ),
                )
                Spacer(Modifier.weight(1f))
                Text(
                    text = if (filterText.isBlank()) "${entities.size}"
                           else "Showing ${filteredEntities.size} of ${entities.size}",
                    style = TextStyle(
                        color = colors.textSecondary,
                        fontSize = 11.sp,
                    ),
                )
            }
        }

        // Entity list
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxWidth().weight(1f),
            contentPadding = PaddingValues(horizontal = 4.dp),
        ) {
            items(rows, key = { it.key }) { row ->
                when (row) {
                    is ListRow.Header -> {
                        GroupHeader(
                            displayName = row.displayName,
                            count = row.count,
                            isExpanded = row.isExpanded,
                            hasSelectedChild = selectedEntityId != null && row.entityIds.contains(selectedEntityId),
                            onClick = {
                                expandedGroups[row.displayName] = !row.isExpanded
                                onSelectEntity(row.entityIds.first())
                            },
                        )
                    }
                    is ListRow.Entity -> {
                        EntityListRow(
                            entity = row.entity,
                            displayName = row.displayName,
                            isSelected = row.entity.id == selectedEntityId,
                            onClick = { onSelectEntity(row.entity.id) },
                            onDoubleClick = { onJumpToSource?.invoke(row.entity.id) },
                            indented = row.indented,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GroupHeader(
    displayName: String,
    count: Int,
    isExpanded: Boolean,
    hasSelectedChild: Boolean,
    onClick: () -> Unit,
) {
    val colors = HyveThemeColors.colors
    val focusManager = LocalFocusManager.current
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val accentColor = if (hasSelectedChild) colors.honey else colors.textDisabled
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 1.dp)
            .clip(RoundedCornerShape(4.dp))
            .hoverable(interactionSource)
            .background(if (isHovered) colors.slateLight else colors.slate)
            .clickable { focusManager.clearFocus(); onClick() }
            .padding(start = 4.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Left accent bar — gold when a child is selected
        Box(
            Modifier
                .width(3.dp)
                .height(20.dp)
                .clip(RoundedCornerShape(1.5.dp))
                .background(accentColor)
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = if (isExpanded) "\u25BE" else "\u25B8",
            style = TextStyle(
                color = colors.textPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
            ),
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = displayName,
            style = TextStyle(
                color = colors.textPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
            ),
            modifier = Modifier.weight(1f),
        )
        // Count badge
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(4.dp))
                .background(accentColor.copy(alpha = 0.2f))
                .padding(horizontal = 5.dp, vertical = 1.dp),
        ) {
            Text(
                text = "\u00D7$count",
                style = TextStyle(
                    color = colors.textSecondary,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                ),
            )
        }
    }
}

@Composable
private fun EntityListRow(
    entity: PrefabEntity,
    displayName: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    onDoubleClick: (() -> Unit)? = null,
    indented: Boolean = false,
) {
    val colors = HyveThemeColors.colors
    val focusManager = LocalFocusManager.current
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    val selectedTint = remember(colors.slate, colors.honey) {
        androidx.compose.ui.graphics.lerp(colors.slate, colors.honey, 0.08f)
    }
    val bgColor = when {
        isSelected -> selectedTint
        isHovered -> colors.slate
        else -> JewelTheme.globalColors.panelBackground
    }

    ContextMenuArea(
        items = {
            buildList {
                if (onDoubleClick != null) {
                    add(ContextMenuItem("Open in JSON") { onDoubleClick() })
                }
            }
        },
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 1.dp)
                .then(if (indented) Modifier.padding(start = 12.dp) else Modifier)
                .clip(RoundedCornerShape(4.dp))
                .hoverable(interactionSource)
                .background(bgColor)
                .combinedClickable(
                    onClick = { focusManager.clearFocus(); onClick() },
                    onDoubleClick = { onDoubleClick?.invoke() },
                )
                .padding(start = 4.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Left accent bar — gold when selected, transparent otherwise
            Box(
                Modifier
                    .width(3.dp)
                    .height(20.dp)
                    .clip(RoundedCornerShape(1.5.dp))
                    .background(if (isSelected) colors.honey else Color.Transparent)
            )
            Spacer(Modifier.width(6.dp))

            Text(
                text = displayName,
                style = TextStyle(
                    color = colors.textPrimary,
                    fontSize = 12.sp,
                    fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal,
                ),
                modifier = Modifier.weight(1f),
            )

            if (entity.isComponentBlock) {
                val badgeColor = if (isSelected) colors.honey else colors.textDisabled
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(3.dp))
                        .background(badgeColor.copy(alpha = 0.15f))
                        .padding(horizontal = 4.dp, vertical = 1.dp),
                ) {
                    Text(
                        text = "block",
                        style = TextStyle(
                            color = badgeColor,
                            fontSize = 9.sp,
                        ),
                    )
                }
                Spacer(Modifier.width(4.dp))
            }

            // Component count badge
            Text(
                text = "${entity.components.size}",
                style = TextStyle(
                    color = if (isSelected) colors.honey else colors.textDisabled,
                    fontSize = 10.sp,
                ),
            )
        }
    }
}
