package com.hyve.blockbench.download

import com.intellij.openapi.diagnostic.Logger
import com.sun.net.httpserver.HttpServer
import java.io.File
import java.net.InetSocketAddress

/**
 * Lightweight local HTTP server that serves the Blockbench web bundle
 * from ~/.hyve/blockbench/ on a random localhost port.
 *
 * Required because JCEF blocks ES module imports from file:// origins (CORS).
 */
object BlockbenchServer {

    private val log = Logger.getInstance(BlockbenchServer::class.java)

    private var server: HttpServer? = null
    private var port: Int = 0

    @Synchronized
    fun ensureRunning(): Int {
        if (server != null) return port

        val httpServer = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        port = httpServer.address.port

        val cacheDir = BlockbenchBundle.cacheDir()

        httpServer.createContext("/") { exchange ->
            val path = exchange.requestURI.path.removePrefix("/").ifEmpty { "index.html" }
            val file = File(cacheDir, path)

            if (file.exists() && file.isFile && file.canonicalPath.startsWith(cacheDir.canonicalPath)) {
                val contentType = when (file.extension.lowercase()) {
                    "html" -> "text/html"
                    "js" -> "application/javascript"
                    "css" -> "text/css"
                    "json" -> "application/json"
                    "svg" -> "image/svg+xml"
                    "png" -> "image/png"
                    "jpg", "jpeg" -> "image/jpeg"
                    "gif" -> "image/gif"
                    "woff" -> "font/woff"
                    "woff2" -> "font/woff2"
                    "ttf" -> "font/ttf"
                    "webmanifest" -> "application/manifest+json"
                    else -> "application/octet-stream"
                }

                val bytes = file.readBytes()
                exchange.responseHeaders["Content-Type"] = listOf(contentType)
                exchange.responseHeaders["Access-Control-Allow-Origin"] = listOf("*")
                exchange.sendResponseHeaders(200, bytes.size.toLong())
                exchange.responseBody.use { it.write(bytes) }
            } else {
                exchange.sendResponseHeaders(404, -1)
            }
        }

        httpServer.executor = null
        httpServer.start()
        server = httpServer

        log.info("Blockbench server started on http://127.0.0.1:$port")
        return port
    }

    fun baseUrl(): String = "http://127.0.0.1:$port"

    @Synchronized
    fun stop() {
        server?.stop(0)
        server = null
        log.info("Blockbench server stopped")
    }
}
