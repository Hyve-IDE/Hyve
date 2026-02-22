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
import org.cef.handler.CefDisplayHandlerAdapter
import org.cef.handler.CefLoadHandlerAdapter
import java.util.Base64

class BlockbenchBridge(
    private val browser: JBCefBrowser,
    private val file: VirtualFile,
    private val project: Project,
    private val baseUrl: String,
    private val onModifiedChanged: (Boolean) -> Unit,
) {
    private val log = Logger.getInstance(BlockbenchBridge::class.java)

    // Pre-allocate ALL JS queries before the browser loads any page
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

    private val fpsQuery = JBCefJSQuery.create(browser).apply {
        addHandler { fpsStr ->
            val fps = fpsStr.toIntOrNull()?.coerceIn(1, 360) ?: 60
            log.info("Syncing JCEF frame rate to Blockbench FPS Limit: $fps")
            browser.cefBrowser.setWindowlessFrameRate(fps)
            JBCefJSQuery.Response(null, 0, null)
        }
    }

    private val pluginReadyQuery = JBCefJSQuery.create(browser).apply {
        addHandler { _ ->
            log.info("Hytale plugin loaded, injecting bridge and loading model")
            injectBridge()
            loadModel()
            JBCefJSQuery.Response(null, 0, null)
        }
    }

    private val readyQuery = JBCefJSQuery.create(browser).apply {
        addHandler { _ ->
            log.info("Blockbench ready, injecting Hytale plugin")
            injectHytalePlugin()
            JBCefJSQuery.Response(null, 0, null)
        }
    }

    init {
        // Pipe JS console to IntelliJ log
        browser.jbCefClient.addDisplayHandler(object : CefDisplayHandlerAdapter() {
            override fun onConsoleMessage(
                cefBrowser: CefBrowser,
                level: org.cef.CefSettings.LogSeverity,
                message: String,
                source: String,
                line: Int,
            ): Boolean {
                val tag = "[JCEF:${source.substringAfterLast('/')}:$line]"
                when (level) {
                    org.cef.CefSettings.LogSeverity.LOGSEVERITY_ERROR -> log.warn("$tag $message")
                    org.cef.CefSettings.LogSeverity.LOGSEVERITY_WARNING -> log.warn("$tag $message")
                    else -> log.info("$tag $message")
                }
                return false
            }
        }, browser.cefBrowser)

        browser.jbCefClient.addLoadHandler(object : CefLoadHandlerAdapter() {
            override fun onLoadEnd(cefBrowser: CefBrowser, frame: CefFrame, httpStatusCode: Int) {
                log.info("onLoadEnd — url=${frame.url}, isMain=${frame.isMain}, status=$httpStatusCode")
                if (frame.isMain) {
                    waitForBlockbenchThenInit()
                }
            }

            override fun onLoadError(
                cefBrowser: CefBrowser,
                frame: CefFrame,
                errorCode: org.cef.handler.CefLoadHandler.ErrorCode,
                errorText: String,
                failedUrl: String,
            ) {
                log.error("onLoadError — url=$failedUrl, code=$errorCode, text=$errorText")
            }
        }, browser.cefBrowser)
    }

    private fun waitForBlockbenchThenInit() {
        log.info("Starting Blockbench readiness polling...")

        browser.cefBrowser.executeJavaScript(
            """
            (function() {
                console.log('[Hyve] Readiness polling started');
                var attempts = 0;
                var maxAttempts = 300;
                var timer = setInterval(function() {
                    attempts++;
                    var bbExists = typeof Blockbench !== 'undefined';
                    var bbReady = bbExists && Blockbench.setup_successful;
                    if (attempts % 10 === 0) {
                        console.log('[Hyve] Poll #' + attempts +
                            ' | Blockbench exists: ' + bbExists +
                            ' | setup_successful: ' + (bbExists ? Blockbench.setup_successful : 'N/A') +
                            ' | Formats: ' + (typeof Formats !== 'undefined') +
                            ' | Codecs: ' + (typeof Codecs !== 'undefined'));
                    }
                    if (bbReady) {
                        clearInterval(timer);
                        console.log('[Hyve] Blockbench ready after ' + attempts + ' polls');
                        ${readyQuery.inject("'ready'")}
                    } else if (attempts >= maxAttempts) {
                        clearInterval(timer);
                        console.error('[Hyve] TIMEOUT waiting for Blockbench after ' + attempts + ' polls');
                        console.error('[Hyve] Final state: Blockbench=' + (typeof Blockbench) +
                            ', setup_successful=' + (bbExists ? Blockbench.setup_successful : 'N/A'));
                    }
                }, 100);
            })();
            """.trimIndent(),
            "bridge://wait-ready",
            0,
        )
    }

    private fun injectHytalePlugin() {
        val pluginJs = BlockbenchBundle.hytalePluginJs()
        if (!pluginJs.exists()) {
            log.warn("Hytale Blockbench plugin JS not found at ${pluginJs.absolutePath}")
            return
        }
        log.info("Loading Hytale plugin via Blockbench plugin API from $baseUrl/hytale_plugin.js")
        // Use Blockbench's own plugin loading: create a Plugin instance with the correct ID
        // (via loadFromURL which calls pathToName), then the script's Plugin.register() matches it.
        // After loading, poll for Codecs.blockymodel to confirm the plugin's onload() has completed
        // (which registers all codecs, formats, and actions), then signal the bridge to load the model.
        // Blockbench's loadFromURL rejects http:// URLs (requires https://), but our
        // local server is plain HTTP. Bypass it entirely: register the Plugin instance,
        // fetch the JS via fetch() (no https restriction), then eval it the same way
        // Blockbench's internals do (new Function(code)). BBPlugin.register() inside
        // the plugin code will find our pre-registered instance and call onload().
        browser.cefBrowser.executeJavaScript(
            """
            (function() {
                console.log('[Hyve] Loading Hytale plugin via direct fetch from $baseUrl/hytale_plugin.js');
                try {
                    // Register plugin instance so BBPlugin.register('hytale_plugin', ...) finds it
                    var plugin = new Plugin('hytale_plugin', {});
                    Plugins.registered['hytale_plugin'] = plugin;
                    Plugins.all.safePush(plugin);
                    plugin.source = 'url';
                    plugin.tags.safePush('Remote');

                    fetch('$baseUrl/hytale_plugin.js')
                        .then(function(resp) {
                            if (!resp.ok) throw new Error('HTTP ' + resp.status);
                            return resp.text();
                        })
                        .then(function(code) {
                            console.log('[Hyve] Plugin JS fetched (' + code.length + ' chars), evaluating...');
                            // Eval the same way Blockbench does internally
                            var fn = new Function('requireNativeModule', 'require',
                                code + '\n//# sourceURL=PLUGINS/(Plugin):hytale_plugin.js');
                            fn(undefined, undefined);
                            plugin.installed = true;
                            console.log('[Hyve] Plugin evaluated, installed=' + plugin.installed);
                        })
                        .catch(function(err) {
                            console.error('[Hyve] Failed to fetch/eval Hytale plugin: ' + err);
                        });

                    // Poll for codec registration (happens when BBPlugin.register → onload fires)
                    var attempts = 0;
                    var maxAttempts = 300;
                    var timer = setInterval(function() {
                        attempts++;
                        if (typeof Codecs !== 'undefined' && Codecs.blockymodel) {
                            clearInterval(timer);
                            console.log('[Hyve] Hytale plugin ready after ' + attempts + ' polls');
                            ${pluginReadyQuery.inject("'ready'")}
                        } else if (attempts >= maxAttempts) {
                            clearInterval(timer);
                            console.error('[Hyve] TIMEOUT waiting for Hytale plugin codec after ' + attempts + ' polls');
                        } else if (attempts % 10 === 0) {
                            console.log('[Hyve] Waiting for codec, attempt ' + attempts);
                        }
                    }, 100);
                } catch(e) {
                    console.error('[Hyve] Failed to load Hytale plugin: ' + e.message, e);
                }
            })();
            """.trimIndent(),
            "bridge://hytale-plugin",
            0,
        )
    }

    private fun injectBridge() {
        log.info("Injecting bridge JS (save + change detection)")
        val bridgeJs = """
            (function() {
                console.log('[Hyve] Bridge injected');

                Blockbench.on('saved_state_changed', function(e) {
                    if (!e.saved) { ${changeQuery.inject("'changed'")} }
                });

                document.addEventListener('keydown', function(e) {
                    if ((e.ctrlKey || e.metaKey) && e.key === 's') {
                        e.preventDefault();
                        if (typeof Project === 'undefined' || typeof Codecs === 'undefined') return;
                        var codec = Codecs.blockymodel;
                        if (codec) {
                            var compiled = JSON.stringify(codec.compile({raw: true}), null, 2);
                            ${saveQuery.inject("compiled")}
                            Project.saved = true;
                        }
                    }
                });

                // Sync JCEF frame rate with Blockbench's FPS Limit setting
                function syncFpsLimit() {
                    var fps = settings.fps_limit ? settings.fps_limit.value : 60;
                    if (typeof fps === 'number' && fps > 0) {
                        ${fpsQuery.inject("'' + Math.round(fps)")}
                    }
                }
                syncFpsLimit();
                Blockbench.on('update_settings', syncFpsLimit);
            })();
        """.trimIndent()

        browser.cefBrowser.executeJavaScript(bridgeJs, "bridge://init", 0)
    }

    /** Trigger a save from the IDE side (Ctrl+S intercepted by IntelliJ, not JCEF). */
    fun save() {
        browser.cefBrowser.executeJavaScript(
            """
            (function() {
                if (typeof Project === 'undefined' || typeof Codecs === 'undefined') return;
                var codec = Codecs.blockymodel;
                if (codec) {
                    var compiled = JSON.stringify(codec.compile({raw: true}), null, 2);
                    ${saveQuery.inject("compiled")}
                    Project.saved = true;
                }
            })();
            """.trimIndent(),
            "bridge://save",
            0,
        )
    }

    fun loadModel() {
        val content = String(file.contentsToByteArray(), Charsets.UTF_8)
        val isAnim = file.extension.equals("blockyanim", ignoreCase = true)
        val escaped = content.escapeForJS()
        log.info("Loading ${if (isAnim) "animation" else "model"}: ${file.name} (${content.length} chars)")

        if (isAnim) {
            loadAnimation(escaped)
        } else {
            val textures = discoverTextures()
            log.info("Discovered ${textures.size} texture(s): ${textures.joinToString { "${it.name} (${it.width}x${it.height})" }}")
            loadBlockymodel(escaped, textures)
        }
    }

    private data class TextureInfo(val name: String, val base64: String, val width: Int, val height: Int)

    /**
     * Discover texture PNGs alongside the model file, mirroring the logic in
     * hytale_plugin.js `discoverTexturePaths()`:
     *  1. PNGs in same dir whose name starts with the model name, or is "Texture.png"
     *  2. PNGs in a `ModelName_Textures/` subfolder
     * Returns name, base64 data, and pixel dimensions for each texture.
     */
    private fun discoverTextures(): List<TextureInfo> {
        val dir = file.parent ?: return emptyList()
        val modelName = file.nameWithoutExtension
        val encoder = Base64.getEncoder()
        val result = mutableListOf<TextureInfo>()
        val seen = mutableSetOf<String>()

        fun addTexture(child: VirtualFile) {
            if (seen.add(child.name)) {
                val bytes = child.contentsToByteArray()
                val dims = pngDimensions(bytes)
                if (dims != null) {
                    result.add(TextureInfo(child.name, encoder.encodeToString(bytes), dims.first, dims.second))
                }
            }
        }

        for (child in dir.children) {
            if (!child.name.endsWith(".png", ignoreCase = true)) continue
            val childBase = child.nameWithoutExtension
            if (childBase.startsWith(modelName, ignoreCase = true) ||
                childBase.equals("Texture", ignoreCase = true)
            ) {
                addTexture(child)
            }
        }

        dir.findChild("${modelName}_Textures")?.let { textureDir ->
            if (textureDir.isDirectory) {
                for (child in textureDir.children) {
                    if (!child.name.endsWith(".png", ignoreCase = true)) continue
                    addTexture(child)
                }
            }
        }

        return result
    }

    /** Read width and height from a PNG file's IHDR chunk (bytes 16-23, big-endian). */
    private fun pngDimensions(data: ByteArray): Pair<Int, Int>? {
        if (data.size < 24) return null
        fun readInt(off: Int) =
            ((data[off].toInt() and 0xFF) shl 24) or
            ((data[off + 1].toInt() and 0xFF) shl 16) or
            ((data[off + 2].toInt() and 0xFF) shl 8) or
            (data[off + 3].toInt() and 0xFF)
        return readInt(16) to readInt(20)
    }

    private fun loadBlockymodel(escaped: String, textures: List<TextureInfo>) {
        // Build JS array of {name, data, w, h} for each discovered texture.
        // Passing pixel dimensions from Kotlin avoids async issues with fromDataURL.
        val textureArrayJs = textures.joinToString(",", "[", "]") { tex ->
            "{name:'${tex.name.escapeForJS()}',data:'data:image/png;base64,${tex.base64}',w:${tex.width},h:${tex.height}}"
        }

        // Plugin is already confirmed loaded (Codecs.blockymodel exists) by the time we get here.
        // Flow mirrors the desktop app: parse model, then add textures.
        // The Hytale format uses per_texture_uv_size, so we set uv_width/uv_height
        // on the Texture object (not on Project) to match the texture resolution.
        browser.cefBrowser.executeJavaScript(
            """
            (function() {
                try {
                    var codec = Codecs.blockymodel;
                    var fmt = Formats['hytale_character'] || Formats['free'];
                    console.log('[Hyve] Loading model with format: ' + (fmt ? fmt.id : 'null'));
                    if (fmt) { newProject(fmt); }

                    var data = JSON.parse('$escaped');
                    console.log('[Hyve] JSON parsed, keys: ' + Object.keys(data).join(', '));
                    codec.parse(data);

                    // Add textures after parse — same order as the desktop app.
                    // Set uv_width/uv_height to match texture resolution (per_texture_uv_size).
                    var texArr = $textureArrayJs;
                    if (texArr.length > 0) {
                        console.log('[Hyve] Adding ' + texArr.length + ' texture(s)');
                        texArr.forEach(function(t) {
                            var tex = new Texture({name: t.name}).fromDataURL(t.data);
                            tex.uv_width = t.w;
                            tex.uv_height = t.h;
                            tex.add(false);
                            console.log('[Hyve] Texture added: ' + t.name + ' (' + t.w + 'x' + t.h + '), uv_width=' + tex.uv_width);
                        });
                        if (!Texture.all.find(function(t) { return t.use_as_default; })) {
                            Texture.all[0].use_as_default = true;
                        }
                        Canvas.updateAll();
                    }

                    Project.saved = true;
                    console.log('[Hyve] Model loaded successfully');
                } catch(e) {
                    console.error('[Hyve] Failed to load model: ' + e.message, e);
                }
            })();
            """.trimIndent(),
            "bridge://load",
            0,
        )
    }

    private fun loadAnimation(escaped: String) {
        // Animations need a Hytale project context first, then we find the parseAnimationFile
        // function through the registered drag handler for .blockyanim files.
        browser.cefBrowser.executeJavaScript(
            """
            (function() {
                try {
                    // Set up a Hytale project if none exists
                    var fmt = Formats['hytale_character'] || Formats['free'];
                    if (!Project || !Format || Format.id !== fmt.id) {
                        console.log('[Hyve] Creating project for animation');
                        newProject(fmt);
                    }
                    // Switch to animate mode
                    Modes.options.animate.select();

                    var data = JSON.parse('$escaped');
                    console.log('[Hyve] Animation JSON parsed, keys: ' + Object.keys(data).join(', '));

                    // Find the blockyanim drag handler registered by the Hytale plugin
                    var handler = Filesystem.drag_handlers && Filesystem.drag_handlers.find(function(h) {
                        return h.id === 'blockyanim' || (h.extensions && h.extensions.includes('blockyanim'));
                    });
                    if (handler && handler.handler) {
                        console.log('[Hyve] Loading animation via drag handler');
                        handler.handler([{ name: '${file.name.escapeForJS()}', content: JSON.stringify(data) }]);
                    } else {
                        console.warn('[Hyve] No blockyanim drag handler found, attempting direct parse');
                        // Fallback: try the global parseAnimationFile if exposed
                        if (typeof parseAnimationFile === 'function') {
                            parseAnimationFile({ name: '${file.name.escapeForJS()}' }, data);
                        } else {
                            console.error('[Hyve] Cannot load animation: no handler available');
                        }
                    }
                    Project.saved = true;
                    console.log('[Hyve] Animation loaded successfully');
                } catch(e) {
                    console.error('[Hyve] Failed to load animation: ' + e.message, e);
                }
            })();
            """.trimIndent(),
            "bridge://load-anim",
            0,
        )
    }

    fun dispose() {
        saveQuery.dispose()
        changeQuery.dispose()
        readyQuery.dispose()
        pluginReadyQuery.dispose()
        fpsQuery.dispose()
    }
}

internal fun String.escapeForJS(): String {
    return this
        .replace("\\", "\\\\")
        .replace("'", "\\'")
        .replace("\n", "\\n")
        .replace("\r", "\\r")
        .replace("\t", "\\t")
}
