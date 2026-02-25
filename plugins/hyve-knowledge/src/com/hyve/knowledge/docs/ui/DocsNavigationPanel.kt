// Copyright 2026 Hyve. All rights reserved.
package com.hyve.knowledge.docs.ui

import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hyve.common.compose.HyveSpacing
import com.hyve.common.compose.HyveThemeColors
import com.hyve.knowledge.docs.*
import com.hyve.knowledge.settings.KnowledgeSettings
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.TextEditorWithPreview
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.jewel.ui.Orientation
import org.jetbrains.jewel.ui.component.*
import org.jetbrains.jewel.ui.component.Tooltip
import java.io.File

/**
 * Compose panel for the Hytale Documentation tool window.
 *
 * Shows a search bar, language toggle, and a navigable tree of docs
 * mirroring the HytaleModding website sidebar structure.
 */
@Composable
fun DocsNavigationPanel(project: Project) {
    val colors = HyveThemeColors.colors
    val settings = remember { KnowledgeSettings.getInstance() }

    var navTree by remember { mutableStateOf<DocNavNode.Directory?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    val selectedLocale = DocsLocaleHolder.locale
    val searchState = rememberTextFieldState("")
    var searchResults by remember { mutableStateOf<List<SearchHit>>(emptyList()) }

    // Load nav tree — auto-sync if locale not cached
    LaunchedEffect(selectedLocale) {
        isLoading = true
        val tree = withContext(Dispatchers.IO) {
            DocNavTreeBuilder.build(settings.resolvedOfflineDocsPath(selectedLocale))
        }
        if (tree == null) {
            // Locale not cached — auto-sync
            withContext(Dispatchers.IO) {
                DocsSyncService.getInstance().sync(locale = selectedLocale)
            }
            navTree = withContext(Dispatchers.IO) {
                DocNavTreeBuilder.build(settings.resolvedOfflineDocsPath(selectedLocale))
            }
        } else {
            navTree = tree
        }
        isLoading = false
    }

    // Search when query changes
    val query = searchState.text.toString()
    LaunchedEffect(query) {
        if (query.length < 2) {
            searchResults = emptyList()
            return@LaunchedEffect
        }
        searchResults = withContext(Dispatchers.IO) {
            searchDocs(settings.resolvedOfflineDocsPath(selectedLocale), query)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.midnight)
    ) {
        // Toolbar: search only (language + sync moved to Settings > Hyve Knowledge)
        DocsToolbar(searchState = searchState)

        Divider(Orientation.Horizontal)

        // Content area
        when {
            isLoading -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            navTree == null -> {
                EmptyState(
                    message = "Documentation not yet synced",
                    actionLabel = "Sync Now",
                    onAction = {
                        ApplicationManager.getApplication().executeOnPooledThread {
                            DocsSyncService.getInstance().sync()
                            ApplicationManager.getApplication().invokeLater {
                                val localeDir = settings.resolvedOfflineDocsPath(selectedLocale)
                                navTree = DocNavTreeBuilder.build(localeDir)
                            }
                        }
                    }
                )
            }
            query.length >= 2 && searchResults.isNotEmpty() -> {
                SearchResultsList(
                    results = searchResults,
                    onDocClicked = { file -> openDoc(project, file) }
                )
            }
            query.length >= 2 && searchResults.isEmpty() -> {
                Box(Modifier.fillMaxSize().padding(HyveSpacing.md), contentAlignment = Alignment.Center) {
                    Text("No results for \"$query\"", color = colors.textSecondary)
                }
            }
            else -> {
                DocTree(
                    node = navTree!!,
                    onDocClicked = { file -> openDoc(project, file) }
                )
            }
        }
    }
}

@Composable
private fun DocsToolbar(
    searchState: androidx.compose.foundation.text.input.TextFieldState,
) {
    Box(modifier = Modifier.fillMaxWidth().padding(HyveSpacing.sm)) {
        TextField(
            state = searchState,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Search documentation\u2026") },
        )
    }
}

/**
 * Recursive tree rendering for the nav structure.
 */
@Composable
private fun DocTree(
    node: DocNavNode.Directory,
    onDocClicked: (File) -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = HyveSpacing.sm, vertical = HyveSpacing.xs)
    ) {
        node.children.forEachIndexed { idx, child ->
            DocTreeNode(child, onDocClicked, depth = 0, isFirst = idx == 0, parentPath = "")
        }
    }
}

@Composable
private fun DocTreeNode(
    node: DocNavNode,
    onDocClicked: (File) -> Unit,
    depth: Int,
    isFirst: Boolean = false,
    parentPath: String = "",
) {
    val colors = HyveThemeColors.colors

    when (node) {
        is DocNavNode.Separator -> {
            if (!isFirst) {
                Spacer(Modifier.height(12.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp)
                        .height(1.dp)
                        .background(colors.slate)
                )
            }

            Spacer(Modifier.height(12.dp))

            Text(
                text = node.title.uppercase(),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = (depth * 14 + 4).dp, bottom = 6.dp),
                fontWeight = FontWeight.Bold,
                fontSize = 10.sp,
                letterSpacing = 0.8.sp,
                color = colors.honeyDark,
            )
        }

        is DocNavNode.Directory -> {
            val pathKey = "$parentPath/${node.title}"
            val expanded = DocsTreeState.expandedPaths[pathKey] ?: false
            val interactionSource = remember { MutableInteractionSource() }
            val isHovered by interactionSource.collectIsHoveredAsState()

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(4.dp))
                    .hoverable(interactionSource)
                    .background(if (isHovered) colors.twilight else Color.Transparent)
                    .clickable { DocsTreeState.expandedPaths[pathKey] = !expanded }
                    .padding(start = (depth * 14).dp, top = 5.dp, bottom = 5.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (expanded) "\u25BE" else "\u25B8",
                    fontSize = 10.sp,
                    color = colors.textPrimary,
                    modifier = Modifier.width(16.dp)
                )
                Tooltip(tooltip = { Text(node.title) }) {
                    Text(
                        text = node.title,
                        fontSize = 13.sp,
                        color = colors.textPrimary,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            if (expanded) {
                Row(
                    modifier = Modifier
                        .padding(start = (depth * 14 + 8).dp)
                        .height(IntrinsicSize.Min)
                ) {
                    Box(
                        Modifier
                            .width(1.dp)
                            .fillMaxHeight()
                            .background(colors.slateLight.copy(alpha = 0.25f))
                    )
                    Column(modifier = Modifier.padding(start = 8.dp).weight(1f)) {
                        node.children.forEachIndexed { idx, child ->
                            DocTreeNode(child, onDocClicked, depth + 1, isFirst = idx == 0, parentPath = pathKey)
                        }
                    }
                }
            }
        }

        is DocNavNode.Document -> {
            val interactionSource = remember { MutableInteractionSource() }
            val isHovered by interactionSource.collectIsHoveredAsState()

            Tooltip(tooltip = { Text(node.title) }) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(4.dp))
                        .hoverable(interactionSource)
                        .background(if (isHovered) colors.twilight else Color.Transparent)
                        .clickable { onDocClicked(node.filePath) }
                        .padding(start = (depth * 14).dp, top = 3.dp, bottom = 3.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = node.title,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Normal,
                        color = if (isHovered) colors.textPrimary else colors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchResultsList(
    results: List<SearchHit>,
    onDocClicked: (File) -> Unit,
) {
    val colors = HyveThemeColors.colors
    val scrollState = rememberScrollState()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(HyveSpacing.sm),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        for (hit in results) {
            val interactionSource = remember { MutableInteractionSource() }
            val isHovered by interactionSource.collectIsHoveredAsState()

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(4.dp))
                    .hoverable(interactionSource)
                    .background(if (isHovered) colors.twilight else Color.Transparent)
                    .clickable { onDocClicked(hit.file) }
                    .padding(HyveSpacing.xs)
            ) {
                Text(hit.title, fontSize = 12.sp, fontWeight = FontWeight.Medium, color = colors.textPrimary)
                if (hit.snippet.isNotBlank()) {
                    Text(
                        hit.snippet,
                        fontSize = 10.sp,
                        color = colors.textSecondary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyState(
    message: String,
    actionLabel: String,
    onAction: () -> Unit,
) {
    val colors = HyveThemeColors.colors
    Column(
        modifier = Modifier.fillMaxSize().padding(HyveSpacing.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(message, color = colors.textSecondary, fontSize = 13.sp)
        Spacer(Modifier.height(HyveSpacing.sm))
        OutlinedButton(onClick = onAction) {
            Text(actionLabel)
        }
    }
}

// ── Helpers ─────────────────────────────────────────────

private fun openDoc(project: Project, file: File) {
    ApplicationManager.getApplication().executeOnPooledThread {
        val vf = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(file) ?: return@executeOnPooledThread
        ApplicationManager.getApplication().invokeLater {
            val editors = FileEditorManager.getInstance(project).openFile(vf, true)
            // Switch to preview-only — no need to show raw markdown source
            for (editor in editors) {
                if (editor is TextEditorWithPreview) {
                    editor.setLayout(TextEditorWithPreview.Layout.SHOW_PREVIEW)
                    break
                }
            }
        }
    }
}

data class SearchHit(val title: String, val snippet: String, val file: File)

/**
 * Simple full-text search across cached markdown files.
 */
private fun searchDocs(localeDir: File, query: String): List<SearchHit> {
    if (!localeDir.isDirectory) return emptyList()
    val queryLower = query.lowercase()
    val hits = mutableListOf<SearchHit>()

    localeDir.walkTopDown()
        .filter { it.isFile && it.extension == "md" }
        .forEach { file ->
            try {
                val content = file.readText(Charsets.UTF_8)
                val contentLower = content.lowercase()
                val idx = contentLower.indexOf(queryLower)
                if (idx >= 0) {
                    val title = extractTitleFromContent(content) ?: file.nameWithoutExtension
                    val snippetStart = (idx - 40).coerceAtLeast(0)
                    val snippetEnd = (idx + query.length + 80).coerceAtMost(content.length)
                    val snippet = content.substring(snippetStart, snippetEnd)
                        .replace('\n', ' ').trim()

                    hits.add(SearchHit(title, snippet, file))
                }
            } catch (_: Exception) {}
        }

    return hits.take(50)
}

private fun extractTitleFromContent(content: String): String? {
    val lines = content.lineSequence().take(10).toList()
    if (lines.isEmpty() || lines[0].trim() != "---") return null
    for (line in lines.drop(1)) {
        if (line.trim() == "---") break
        val match = Regex("""^title:\s*["']?(.+?)["']?\s*$""").matchEntire(line.trim())
        if (match != null) return match.groupValues[1]
    }
    return null
}

/**
 * Session-level persistence of expanded directory paths in the docs tree.
 * Survives tool window close/reopen within the same IDE session.
 */
internal object DocsTreeState {
    val expandedPaths = mutableStateMapOf<String, Boolean>()
}

/**
 * Shared mutable locale state. Updated by the gear menu language picker,
 * read by the Compose panel — changes trigger recomposition + auto-sync.
 */
internal object DocsLocaleHolder {
    var locale by mutableStateOf(KnowledgeSettings.getInstance().state.docsLanguage)
}

internal val LOCALE_DISPLAY_NAMES = linkedMapOf(
    "en" to "English",
    "de-DE" to "Deutsch (German)",
    "fr-FR" to "Fran\u00e7ais (French)",
    "es-ES" to "Espa\u00f1ol (Spanish)",
    "it-IT" to "Italiano (Italian)",
    "pt-PT" to "Portugu\u00eas (Portuguese)",
    "pt-BR" to "Portugu\u00eas Brasil (Brazilian Portuguese)",
    "nl-NL" to "Nederlands (Dutch)",
    "pl-PL" to "Polski (Polish)",
    "sv-SE" to "Svenska (Swedish)",
    "da-DK" to "Dansk (Danish)",
    "cs-CZ" to "\u010ce\u0161tina (Czech)",
    "hu-HU" to "Magyar (Hungarian)",
    "ro-RO" to "Rom\u00e2n\u0103 (Romanian)",
    "ru-RU" to "\u0420\u0443\u0441\u0441\u043a\u0438\u0439 (Russian)",
    "uk-UA" to "\u0423\u043a\u0440\u0430\u0457\u043d\u0441\u044c\u043a\u0430 (Ukrainian)",
    "tr-TR" to "T\u00fcrk\u00e7e (Turkish)",
    "sq-AL" to "Shqip (Albanian)",
    "af-ZA" to "Afrikaans",
    "lt-LT" to "Lietuvi\u0173 (Lithuanian)",
    "lv-LV" to "Latvie\u0161u (Latvian)",
    "ja-JP" to "\u65e5\u672c\u8a9e (Japanese)",
    "ar-SA" to "\u0627\u0644\u0639\u0631\u0628\u064a\u0629 (Arabic)",
    "hi-IN" to "\u0939\u093f\u0928\u094d\u0926\u0940 (Hindi)",
    "id-ID" to "Bahasa Indonesia (Indonesian)",
    "vi-VN" to "Ti\u1ebfng Vi\u1ec7t (Vietnamese)",
)

