package com.hyve.blockbench.bridge

import com.hyve.blockbench.download.BlockbenchBundle
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.jcef.JBCefBrowser
import com.intellij.ui.jcef.JBCefJSQuery
import org.cef.browser.CefBrowser
import org.cef.browser.CefFrame
import org.cef.handler.CefLoadHandlerAdapter

class BlockbenchBridge(
    private val browser: JBCefBrowser,
    private val file: VirtualFile,
    private val project: Project,
    private val onModifiedChanged: (Boolean) -> Unit,
) {
    private val log = Logger.getInstance(BlockbenchBridge::class.java)

    private val saveQuery = JBCefJSQuery.create(browser).apply {
        addHandler { json ->
            ApplicationManager.getApplication().invokeLater {
                WriteCommandAction.runWriteCommandAction(project) {
                    file.setBinaryContent(json.toByteArray(Charsets.UTF_8))
                }
                onModifiedChanged(false)
            }
            JBCefJSQuery.Response(null, 0, null)
        }
    }

    private val changeQuery = JBCefJSQuery.create(browser).apply {
        addHandler { _ ->
            onModifiedChanged(true)
            JBCefJSQuery.Response(null, 0, null)
        }
    }

    init {
        browser.jbCefClient.addLoadHandler(object : CefLoadHandlerAdapter() {
            override fun onLoadEnd(cefBrowser: CefBrowser, frame: CefFrame, httpStatusCode: Int) {
                if (frame.isMain) {
                    injectHytalePlugin()
                    injectBridge()
                    loadModel()
                }
            }
        }, browser.cefBrowser)
    }

    private fun injectHytalePlugin() {
        val pluginJs = BlockbenchBundle.hytalePluginJs()
        if (!pluginJs.exists()) {
            log.warn("Hytale Blockbench plugin JS not found at ${pluginJs.absolutePath}")
            return
        }
        val js = pluginJs.readText(Charsets.UTF_8).escapeForJS()
        browser.cefBrowser.executeJavaScript(
            "(function() { $js })();",
            "bridge://hytale-plugin",
            0,
        )
    }

    private fun injectBridge() {
        val bridgeJs = """
            (function() {
                // Change detection
                if (typeof Blockbench !== 'undefined') {
                    Blockbench.on('saved_state_changed', function(e) {
                        if (!e.saved) { ${changeQuery.inject("'changed'")} }
                    });
                }

                // Save handler - intercept Ctrl+S
                document.addEventListener('keydown', function(e) {
                    if ((e.ctrlKey || e.metaKey) && e.key === 's') {
                        e.preventDefault();
                        if (typeof Project === 'undefined' || typeof Codecs === 'undefined') return;
                        var isAnim = Project.format && Project.format.id === 'blockyanim';
                        var codec = isAnim ? Codecs.blockyanimation : Codecs.blockymodel;
                        if (codec) {
                            var compiled = JSON.stringify(codec.compile({raw: true}), null, 2);
                            ${saveQuery.inject("compiled")}
                            Project.saved = true;
                        }
                    }
                });
            })();
        """.trimIndent()

        browser.cefBrowser.executeJavaScript(bridgeJs, "bridge://init", 0)
    }

    fun loadModel() {
        val content = String(file.contentsToByteArray(), Charsets.UTF_8)
        val isAnim = file.extension.equals("blockyanim", ignoreCase = true)
        val escaped = content.escapeForJS()
        val formatId = if (isAnim) "blockyanim" else "hytale_character"

        browser.cefBrowser.executeJavaScript(
            """
            (function() {
                var fmt = typeof Formats !== 'undefined' ? (Formats['$formatId'] || Formats['free']) : null;
                if (fmt) { newProject(fmt); }
                try {
                    var data = JSON.parse('$escaped');
                    var codec = ${if (isAnim) "Codecs.blockyanimation" else "Codecs.blockymodel"};
                    if (codec) { codec.parse(data); }
                    if (typeof Project !== 'undefined') { Project.saved = true; }
                } catch(e) {
                    console.error('Hyve: Failed to load model', e);
                }
            })();
            """.trimIndent(),
            "bridge://load",
            0,
        )
    }

    fun dispose() {
        saveQuery.dispose()
        changeQuery.dispose()
    }
}

private fun String.escapeForJS(): String {
    return this
        .replace("\\", "\\\\")
        .replace("'", "\\'")
        .replace("\n", "\\n")
        .replace("\r", "\\r")
        .replace("\t", "\\t")
}
