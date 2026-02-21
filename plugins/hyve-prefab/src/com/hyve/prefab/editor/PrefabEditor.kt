package com.hyve.prefab.editor

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import com.hyve.common.compose.HyveComposeFileEditorWithContext
import com.hyve.prefab.domain.EntityId
import com.hyve.prefab.exporter.PrefabExporter
import com.hyve.prefab.parser.PrefabParser
import com.hyve.prefab.components.PrefabEditorContent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.OnePixelSplitter
import java.awt.Dimension
import java.beans.PropertyChangeListener
import java.beans.PropertyChangeSupport
import java.io.File
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * IntelliJ FileEditor for Hytale .prefab.json files.
 *
 * Embeds the Prefab visual editor using Compose Desktop via [HyveComposeFileEditorWithContext].
 * Provides streaming parse, entity list, component inspector, and byte-splice save.
 *
 * Layout: `[Entity JSON Editor | Visual Prefab Editor]`
 * The JSON editor panel starts hidden and appears when the user double-clicks an entity.
 */
class PrefabEditor(
    project: Project,
    file: VirtualFile,
) : HyveComposeFileEditorWithContext(project, file) {

    internal val editorState = PrefabEditorState()
    private val propertyChangeSupport = PropertyChangeSupport(this)
    private val _sourceOpenEntityId = mutableStateOf<EntityId?>(null)

    /** Zero-size placeholder used as splitter's left component when source is hidden. */
    private val emptyPanel = JPanel().apply {
        minimumSize = Dimension(0, 0)
        preferredSize = Dimension(0, 0)
    }
    private var splitter: OnePixelSplitter? = null

    private val excerptManager = EntityExcerptManager(
        project = project,
        prefabFile = virtualFile,
        editorState = editorState,
        onFileSaved = {
            propertyChangeSupport.firePropertyChange(PROP_MODIFIED, true, false)
        },
        onSourceOpened = { showSourcePanel() },
        onSourceClosed = { _sourceOpenEntityId.value = null; hideSourcePanel() },
    ).also { Disposer.register(this, it) }

    init {
        loadDocument()
    }

    override fun getName(): String = "Hyve Prefab Editor"

    override fun getComponent(): JComponent {
        splitter?.let { return it }

        // Splitter is the permanent root — compose panel is always secondComponent.
        // firstComponent swaps between emptyPanel (hidden) and editorPanel (showing source).
        val sp = OnePixelSplitter(false, 0f).apply {
            setHonorComponentsMinimumSize(false)
            firstComponent = emptyPanel
            secondComponent = super.getComponent()
        }
        splitter = sp
        return sp
    }

    private fun showSourcePanel() {
        val sp = splitter ?: return
        sp.firstComponent = excerptManager.editorPanel
        sp.proportion = 0.35f
    }

    private fun hideSourcePanel() {
        val sp = splitter ?: return
        sp.firstComponent = emptyPanel
        sp.proportion = 0f
    }

    @Composable
    override fun EditorContent() {
        PrefabEditorContent(
            state = editorState,
            onSave = { saveDocument() },
            onJumpToSource = { entityId ->
                _sourceOpenEntityId.value = entityId
                excerptManager.openEntity(entityId)
            },
            filePath = virtualFile.path,
            projectBasePath = project.basePath,
            sourceOpenEntityId = _sourceOpenEntityId,
        )
    }

    /**
     * Load and parse the prefab file asynchronously.
     */
    private fun loadDocument() {
        editorState.setLoading(true)

        ApplicationManager.getApplication().executeOnPooledThread {
            try {
                val diskFile = File(virtualFile.path)
                val cache = PrefabDocumentCache.getInstance()

                // Try cache first — skips full parse if file hasn't changed
                val cached = cache.get(diskFile)
                if (cached != null) {
                    ApplicationManager.getApplication().invokeLater {
                        editorState.setDocument(cached)
                    }
                    return@executeOnPooledThread
                }

                // Cache miss — read directly from filesystem to bypass VFS FileTooBigException
                val bytes = diskFile.readBytes()
                val doc = PrefabParser.parse(bytes)
                cache.put(diskFile, doc)

                ApplicationManager.getApplication().invokeLater {
                    editorState.setDocument(doc)
                }
            } catch (e: Exception) {
                val errorMsg = "${e.javaClass.simpleName}: ${e.message ?: "unknown error"}"
                ApplicationManager.getApplication().invokeLater {
                    editorState.setError(errorMsg)
                }
            }
        }
    }

    /**
     * Save the document by exporting all entities into the original file.
     */
    fun saveDocument() {
        // Incorporate any pending excerpt editor changes first
        excerptManager.saveBackIfModified()

        val doc = editorState.document.value ?: return

        try {
            val bytes = PrefabExporter.export(doc)
            // Write directly to filesystem to bypass VFS FileTooBigException on large prefab files
            File(virtualFile.path).writeBytes(bytes)
            virtualFile.refresh(false, false)
            // Re-parse to update byte offsets for future saves
            val newDoc = PrefabParser.parse(bytes)
            PrefabDocumentCache.getInstance().put(File(virtualFile.path), newDoc)
            editorState.setDocument(newDoc)
            editorState.markSaved()
            propertyChangeSupport.firePropertyChange(PROP_MODIFIED, true, false)
        } catch (e: Exception) {
            com.intellij.notification.Notifications.Bus.notify(
                com.intellij.notification.Notification(
                    "Hyve Prefab Editor",
                    "Save Failed",
                    "Failed to save ${virtualFile.name}: ${e.message}",
                    com.intellij.notification.NotificationType.ERROR,
                ),
                project,
            )
        }
    }

    override fun isModified(): Boolean = editorState.isDirty.value

    override fun isValid(): Boolean = virtualFile.isValid

    override fun addPropertyChangeListener(listener: PropertyChangeListener) {
        propertyChangeSupport.addPropertyChangeListener(listener)
    }

    override fun removePropertyChangeListener(listener: PropertyChangeListener) {
        propertyChangeSupport.removePropertyChangeListener(listener)
    }

    companion object {
        private const val PROP_MODIFIED = "modified"
    }
}
