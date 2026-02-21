package com.hyve.prefab.editor

import com.hyve.prefab.domain.EntityId
import com.hyve.prefab.parser.PrefabParser
import com.intellij.icons.AllIcons
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.ScrollType
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.fileEditor.FileDocumentManagerListener
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import java.awt.BorderLayout
import java.awt.Cursor
import java.awt.Font
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.BorderFactory
import javax.swing.JPanel

/**
 * Manages an embedded JSON editor panel for viewing/editing individual entities.
 *
 * When the user double-clicks an entity in the visual editor, this manager:
 * 1. Extracts the entity's JSON from the raw file bytes
 * 2. Pretty-prints it into an embedded editor with JSON syntax highlighting
 * 3. Signals the parent to reveal the editor panel in the split view
 *
 * When the user saves (Ctrl+S), the edited JSON is byte-spliced back
 * into the real prefab file at the entity's original byte position.
 */
class EntityExcerptManager(
    private val project: Project,
    private val prefabFile: VirtualFile,
    private val editorState: PrefabEditorState,
    private val onFileSaved: () -> Unit,
    private val onSourceOpened: () -> Unit,
    private val onSourceClosed: () -> Unit,
) : Disposable {

    private var editor: Editor? = null
    private var currentEntityId: EntityId? = null
    private var currentByteRange: IntRange? = null
    private var lastSavedContent: String? = null
    private var currentDisplayName: String = ""
    private var suppressDirtyCheck = false

    private val headerLabel = JBLabel("").apply {
        font = font.deriveFont(Font.BOLD)
        icon = AllIcons.FileTypes.Json
    }

    private val closeButton = JBLabel(AllIcons.Actions.Close).apply {
        cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        toolTipText = "Close source view"
        border = JBUI.Borders.empty(0, 4)
        addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent?) {
                onSourceClosed()
            }
        })
    }

    private val headerPanel = JPanel(BorderLayout()).apply {
        border = BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 0, 1, 0, JBColor.border()),
            JBUI.Borders.empty(6, 8),
        )
        add(headerLabel, BorderLayout.CENTER)
        add(closeButton, BorderLayout.EAST)
    }

    /** The panel to embed in the split view. Contains a header and the JSON editor. */
    val editorPanel: JPanel = JPanel(BorderLayout()).apply {
        add(headerPanel, BorderLayout.NORTH)
    }

    private val connection = project.messageBus.connect(this)

    init {
        connection.subscribe(FileDocumentManagerListener.TOPIC, object : FileDocumentManagerListener {
            override fun beforeAllDocumentsSaving() {
                saveBackIfModified()
            }
        })
    }

    /**
     * Show the given entity's JSON in the embedded editor panel.
     * If an entity is already shown with unsaved changes, saves it back first.
     */
    fun openEntity(entityId: EntityId) {
        val doc = editorState.document.value ?: return
        val entity = doc.findEntityById(entityId) ?: return
        val byteRange = entity.sourceByteRange ?: return

        // Save any pending changes from the previous entity
        saveBackIfModified()

        // Extract and pretty-print the entity JSON
        val rawJson = String(doc.rawBytes, byteRange.first, byteRange.last - byteRange.first + 1, Charsets.UTF_8)
        val jsonElement = prettyJson.parseToJsonElement(rawJson)
        val formatted = prettyJson.encodeToString(JsonObject.serializer(), jsonElement as JsonObject)

        currentEntityId = entityId
        currentByteRange = byteRange
        lastSavedContent = formatted
        currentDisplayName = entity.displayName

        headerLabel.text = currentDisplayName

        val ed = getOrCreateEditor()
        suppressDirtyCheck = true
        ApplicationManager.getApplication().runWriteAction {
            ed.document.setText(formatted)
        }
        suppressDirtyCheck = false

        // Scroll to top
        ed.caretModel.moveToOffset(0)
        ed.scrollingModel.scrollToCaret(ScrollType.MAKE_VISIBLE)

        onSourceOpened()
    }

    private fun getOrCreateEditor(): Editor {
        editor?.let { return it }

        val document = EditorFactory.getInstance().createDocument("")
        val jsonFileType = FileTypeManager.getInstance().getFileTypeByExtension("json")
        val ed = EditorFactory.getInstance().createEditor(
            document, project, jsonFileType, false,
        )

        ed.settings.apply {
            isLineNumbersShown = true
            isWhitespacesShown = false
            isFoldingOutlineShown = true
            isAutoCodeFoldingEnabled = true
        }

        document.addDocumentListener(object : DocumentListener {
            override fun documentChanged(event: DocumentEvent) {
                if (suppressDirtyCheck) return
                val isDirty = document.text != lastSavedContent
                headerLabel.text = if (isDirty) "$currentDisplayName *" else currentDisplayName
            }
        }, this)

        editorPanel.add(ed.component, BorderLayout.CENTER)
        editorPanel.revalidate()
        editor = ed
        return ed
    }

    /**
     * If the embedded editor content has been modified since last save,
     * splice it back into the real file.
     */
    internal fun saveBackIfModified() {
        val ed = editor ?: return
        val byteRange = currentByteRange ?: return
        val doc = editorState.document.value ?: return

        val currentContent = ed.document.text
        if (currentContent == lastSavedContent) return

        lastSavedContent = currentContent
        headerLabel.text = currentDisplayName

        // Byte-splice the edited entity JSON into the real file
        val rawBytes = doc.rawBytes
        val newEntityBytes = currentContent.toByteArray(Charsets.UTF_8)

        val beforeLen = byteRange.first
        val afterStart = byteRange.last + 1
        val afterLen = rawBytes.size - afterStart

        val result = ByteArray(beforeLen + newEntityBytes.size + afterLen)
        System.arraycopy(rawBytes, 0, result, 0, beforeLen)
        System.arraycopy(newEntityBytes, 0, result, beforeLen, newEntityBytes.size)
        System.arraycopy(rawBytes, afterStart, result, beforeLen + newEntityBytes.size, afterLen)

        // Write directly to filesystem to bypass VFS FileTooBigException on large prefab files
        java.io.File(prefabFile.path).writeBytes(result)
        prefabFile.refresh(false, false)

        val newDoc = PrefabParser.parse(result)
        editorState.setDocument(newDoc)
        editorState.markSaved()
        onFileSaved()

        // Update the tracked byte range from the re-parsed entity
        val entityId = currentEntityId
        if (entityId != null) {
            currentByteRange = newDoc.findEntityById(entityId)?.sourceByteRange
        }
    }

    override fun dispose() {
        connection.disconnect()
        val ed = editor
        if (ed != null) {
            EditorFactory.getInstance().releaseEditor(ed)
            editor = null
        }
    }

    companion object {
        @OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
        private val prettyJson = Json { prettyPrint = true; prettyPrintIndent = "  " }
    }
}
