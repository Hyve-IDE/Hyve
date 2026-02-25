// Copyright 2026 Hyve. All rights reserved.
package com.hyve.knowledge.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import com.hyve.common.compose.CorpusVisual
import com.hyve.common.compose.HyveSpacing
import com.hyve.common.compose.HyveThemeColors
import com.hyve.common.compose.components.EmptyState
import com.hyve.knowledge.bridge.IntelliJLogProvider
import com.hyve.knowledge.bridge.toConfig
import com.hyve.knowledge.core.db.Corpus
import com.hyve.knowledge.core.db.KnowledgeDatabase
import com.hyve.knowledge.core.index.CorpusIndexManager
import com.hyve.knowledge.core.search.IndexStats
import com.hyve.knowledge.core.search.KnowledgeSearchService
import com.hyve.knowledge.core.search.SearchResult
import com.hyve.knowledge.settings.KnowledgeSettings
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.intellij.openapi.application.ApplicationManager
import java.io.File
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.TextEditorWithPreview
import com.intellij.openapi.project.Project
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.vfs.JarFileSystem
import com.intellij.openapi.vfs.LocalFileSystem
import com.hyve.common.settings.HytaleInstallPath
import kotlinx.coroutines.delay
import org.jetbrains.jewel.ui.Orientation
import org.jetbrains.jewel.ui.component.Divider
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.TextField

@Composable
fun KnowledgeSearchPanel(project: Project) {
    val searchState = rememberTextFieldState("")
    var results by remember { mutableStateOf<List<SearchResult>>(emptyList()) }
    var totalResultCount by remember { mutableStateOf(0) }
    var isSearching by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val searchService = remember {
        val log = IntelliJLogProvider(KnowledgeSearchService::class.java)
        val settings = KnowledgeSettings.getInstance()
        val config = settings.toConfig()
        val dbFile = File(config.resolvedIndexPath(), "knowledge.db")
        val db = KnowledgeDatabase.forFile(dbFile, log)
        val indexManager = CorpusIndexManager(config, log)
        KnowledgeSearchService(db, indexManager, log)
    }

    // Submitted query — only updated when user presses Enter
    var submittedQuery by remember { mutableStateOf("") }

    // Corpus filter — set of enabled corpus IDs (all enabled by default)
    var enabledCorpora by remember {
        mutableStateOf(CorpusVisual.ordered.map { it.corpusId }.toSet())
    }

    // Corpus stats — loaded once on mount, cached
    var corpusStats by remember { mutableStateOf<Map<String, IndexStats>>(emptyMap()) }
    LaunchedEffect(Unit) {
        try {
            val map = withContext(Dispatchers.IO) {
                val m = mutableMapOf<String, IndexStats>()
                for (corpus in Corpus.entries) {
                    try {
                        m[corpus.id] = searchService.getCorpusStats(corpus)
                    } catch (_: Exception) { }
                }
                m
            }
            corpusStats = map
        } catch (_: Exception) { }
    }

    // Resolve enabled Corpus enums from IDs
    val enabledCorpusList by remember {
        derivedStateOf {
            enabledCorpora.mapNotNull { id ->
                Corpus.entries.find { it.id == id }
            }
        }
    }

    // Search triggers on Enter (submittedQuery) or corpus filter change
    LaunchedEffect(submittedQuery, enabledCorpora) {
        if (submittedQuery.isBlank()) {
            results = emptyList()
            totalResultCount = 0
            errorMessage = null
            return@LaunchedEffect
        }
        if (enabledCorpusList.isEmpty()) {
            results = emptyList()
            totalResultCount = 0
            errorMessage = null
            return@LaunchedEffect
        }
        isSearching = true
        errorMessage = null
        try {
            val knowledgeState = com.hyve.knowledge.settings.KnowledgeSettings.getInstance().state
            val perCorpus = knowledgeState.resultsPerCorpus
            val expansionLimit = knowledgeState.maxRelatedConnections
            // Run on IO dispatcher — searchWithExpansion uses runBlocking for embeddings
            // which would freeze the Compose thread if called directly.
            val allResults = withContext(Dispatchers.IO) {
                searchService.searchWithExpansion(
                    submittedQuery, enabledCorpusList, perCorpus, expansionLimit,
                )
            }
            totalResultCount = allResults.size
            results = allResults
        } catch (e: Exception) {
            errorMessage = e.message ?: "Search failed"
            results = emptyList()
            totalResultCount = 0
        } finally {
            isSearching = false
        }
    }

    val colors = HyveThemeColors.colors
    val isIdle = submittedQuery.isBlank() && !isSearching

    val onSubmit: () -> Unit = {
        val query = searchState.text.toString().trim()
        submittedQuery = query
    }

    // Background focus target — keeps focus inside Compose tree when clicking empty space.
    // Using clearFocus() would push focus out to Swing, causing WInputMethod NPE on Windows.
    val backgroundFocus = remember { FocusRequester() }

    // Key handler: Enter submits search, Escape defocuses to background
    val searchKeyHandler = Modifier.onPreviewKeyEvent { event ->
        if (event.type == KeyEventType.KeyDown) {
            when (event.key) {
                Key.Enter -> { onSubmit(); true }
                Key.Escape -> { backgroundFocus.requestFocus(); true }
                else -> false
            }
        } else {
            false
        }
    }

    // Focus requester for the active-mode search field
    val activeFocusRequester = remember { FocusRequester() }

    // Restore focus to the top search field when transitioning from idle to active
    LaunchedEffect(isIdle) {
        if (!isIdle) {
            try { activeFocusRequester.requestFocus() } catch (_: Exception) { }
        }
    }

    val onToggleCorpus: (String) -> Unit = { corpusId ->
        enabledCorpora = if (corpusId in enabledCorpora) {
            enabledCorpora - corpusId
        } else {
            enabledCorpora + corpusId
        }
    }

    Column(
        modifier = Modifier
            .requiredWidthIn(min = 460.dp)
            .fillMaxSize()
            .focusRequester(backgroundFocus)
            .focusable()
            .pointerInput(Unit) {
                detectTapGestures { backgroundFocus.requestFocus() }
            },
    ) {
        // Header separator line (matches Gradle/other tool windows)
        Divider(orientation = Orientation.Horizontal, modifier = Modifier.fillMaxWidth())

        if (isIdle) {
            // ── Idle: centered landing page ──
            KnowledgeIdleDashboard(
                searchState = searchState,
                enabledCorpora = enabledCorpora,
                onToggleCorpus = onToggleCorpus,
                onSubmit = onSubmit,
                searchKeyHandler = searchKeyHandler,
                modifier = Modifier.weight(1f),
            )
        } else {
            // ── Active: search pinned to top, results below ──
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = HyveSpacing.sm),
            ) {
                Spacer(Modifier.height(HyveSpacing.sm))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(colors.slate.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                        .border(1.dp, colors.slateLight.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                ) {
                    TextField(
                        state = searchState,
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(activeFocusRequester)
                            .then(searchKeyHandler),
                        undecorated = true,
                        placeholder = { Text("Search Hytale knowledge base\u2026") },
                    )
                }

                Spacer(Modifier.height(HyveSpacing.sm))

                CorpusFilterChips(
                    enabledCorpora = enabledCorpora,
                    onToggle = onToggleCorpus,
                )

                Spacer(Modifier.height(HyveSpacing.xs))

                // Content area
                Box(modifier = Modifier.weight(1f)) {
                    when {
                        errorMessage != null -> {
                            Column(modifier = Modifier.fillMaxSize().padding(HyveSpacing.lg)) {
                                Text(
                                    text = errorMessage ?: "Unknown error",
                                    color = colors.error,
                                )
                            }
                        }

                        isSearching && results.isEmpty() -> {
                            AnimatedSearchingState()
                        }

                        results.isEmpty() -> {
                            EmptyState(
                                if (enabledCorpusList.isEmpty()) "No corpora selected"
                                else "No results found"
                            )
                        }

                        else -> {
                            GroupedResultsList(
                                results = results,
                                onOpenFullDocument = { result ->
                                    try { backgroundFocus.requestFocus() } catch (_: Exception) { }
                                    val relPath = result.nodeId.removePrefix("docs:")
                                    val offlineFile = com.hyve.knowledge.docs.OfflineDocResolver.resolve(relPath)
                                    if (offlineFile != null) {
                                        openFileAtLine(project, offlineFile.absolutePath, 0, "docs")
                                    }
                                },
                                onResultClick = { result ->
                                    // Move focus to background target before opening a file
                                    // to avoid WInputMethod NPE during Compose→Swing transition
                                    try { backgroundFocus.requestFocus() } catch (_: Exception) { }
                                    // For DOCS corpus, try to open the offline doc instead
                                    if (result.corpus == "docs") {
                                        val relPath = result.nodeId.removePrefix("docs:")
                                        val offlineFile = com.hyve.knowledge.docs.OfflineDocResolver.resolve(relPath)
                                        if (offlineFile != null) {
                                            openFileAtLine(project, offlineFile.absolutePath, 0, "docs")
                                            return@GroupedResultsList
                                        }
                                        // Offline file not available — show sync hint
                                        NotificationGroupManager.getInstance()
                                            .getNotificationGroup("Hyve Knowledge")
                                            .createNotification(
                                                "Sync documentation for full doc viewing",
                                                "Use Tools > Sync Offline Documentation to enable full document preview.",
                                                NotificationType.INFORMATION
                                            )
                                            .notify(project)
                                    }
                                    openFileAtLine(project, result.filePath, result.lineStart, result.corpus)
                                },
                            )
                        }
                    }
                }
            }
        }

        // Status bar — always at bottom
        KnowledgeStatusBar(
            corpusStats = corpusStats,
            resultCount = results.size,
            totalResultCount = totalResultCount,
        )
    }
}

/**
 * Centered "Searching" with animated dots that cycle . → .. → ...
 */
@Composable
private fun AnimatedSearchingState() {
    var dotCount by remember { mutableStateOf(1) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(400)
            dotCount = dotCount % 3 + 1
        }
    }
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = androidx.compose.ui.Alignment.Center,
    ) {
        Text(
            text = "Searching" + ".".repeat(dotCount),
            style = com.hyve.common.compose.HyveTypography.placeholder,
        )
    }
}

private fun openFileAtLine(project: Project, filePath: String, line: Int, corpus: String = "code") {
    // Resolve VFS off-EDT (slow VFS traversal is prohibited on the event dispatch thread),
    // then switch to EDT only for editor open + caret navigation.
    ApplicationManager.getApplication().executeOnPooledThread {
        val vf = com.intellij.openapi.application.ReadAction.compute<com.intellij.openapi.vfs.VirtualFile?, Throwable> {
            when (corpus) {
                "gamedata" -> resolveGamedataFile(filePath)
                else -> LocalFileSystem.getInstance().findFileByPath(filePath)
            }
        } ?: return@executeOnPooledThread

        ApplicationManager.getApplication().invokeLater {
            val editors = FileEditorManager.getInstance(project).openFile(vf, true)
            // For offline docs, show preview-only (no raw markdown source)
            if (corpus == "docs") {
                for (editor in editors) {
                    if (editor is TextEditorWithPreview) {
                        editor.setLayout(TextEditorWithPreview.Layout.SHOW_PREVIEW)
                        break
                    }
                }
            }
            val editor = editors.firstOrNull()
            if (editor is com.intellij.openapi.fileEditor.TextEditor && line > 0) {
                val logicalPosition = com.intellij.openapi.editor.LogicalPosition(line - 1, 0)
                editor.editor.caretModel.moveToLogicalPosition(logicalPosition)
                editor.editor.scrollingModel.scrollToCaret(com.intellij.openapi.editor.ScrollType.CENTER)
            }
        }
    }
}

/**
 * Resolves a gamedata file path (ZIP-relative, e.g. "Server/Item/Items/Torch.json")
 * to a VirtualFile inside Assets.zip via IntelliJ's JarFileSystem.
 */
private fun resolveGamedataFile(zipRelativePath: String): com.intellij.openapi.vfs.VirtualFile? {
    val assetsZip = HytaleInstallPath.assetsZipPath() ?: return null
    val zipVf = LocalFileSystem.getInstance().findFileByPath(assetsZip.toString()) ?: return null
    val zipRoot = JarFileSystem.getInstance().getJarRootForLocalFile(zipVf) ?: return null
    return zipRoot.findFileByRelativePath(zipRelativePath)
}
