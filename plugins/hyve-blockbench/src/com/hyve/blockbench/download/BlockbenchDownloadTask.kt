package com.hyve.blockbench.download

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import java.io.File
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.zip.ZipInputStream

class BlockbenchDownloadTask(
    project: Project,
    private val onComplete: () -> Unit = {},
) : Task.Backgroundable(project, "Downloading Blockbench Web...", true) {

    private val log = Logger.getInstance(BlockbenchDownloadTask::class.java)

    private val http: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(15))
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build()

    override fun run(indicator: ProgressIndicator) {
        indicator.isIndeterminate = false

        val cacheDir = BlockbenchBundle.cacheDir()
        cacheDir.mkdirs()

        // Phase 1: Download zip
        indicator.text = "Downloading Blockbench web bundle..."
        indicator.fraction = 0.0

        val request = HttpRequest.newBuilder()
            .uri(URI.create(BlockbenchBundle.downloadUrl()))
            .header("User-Agent", "HyveIDE-Blockbench")
            .timeout(Duration.ofMinutes(5))
            .GET()
            .build()

        val response = http.send(request, HttpResponse.BodyHandlers.ofByteArray())
        if (response.statusCode() != 200) {
            throw RuntimeException("Download failed with status ${response.statusCode()}")
        }

        val zipBytes = response.body()
        indicator.fraction = 0.5

        if (indicator.isCanceled) return

        // Phase 2: Extract zip
        indicator.text = "Extracting Blockbench bundle..."

        ZipInputStream(zipBytes.inputStream()).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                if (indicator.isCanceled) return

                // Strip the leading "dist/" prefix so files land directly in cacheDir
                val name = entry.name.removePrefix("dist/")
                if (name.isEmpty()) {
                    entry = zis.nextEntry
                    continue
                }

                val outFile = File(cacheDir, name)

                if (entry.isDirectory) {
                    outFile.mkdirs()
                } else {
                    outFile.parentFile?.mkdirs()
                    outFile.outputStream().use { out ->
                        zis.copyTo(out)
                    }
                }
                entry = zis.nextEntry
            }
        }

        indicator.fraction = 0.9

        // Phase 3: Verify
        if (!BlockbenchBundle.isAvailable()) {
            throw RuntimeException("Extraction completed but index.html not found in ${cacheDir.absolutePath}")
        }

        indicator.fraction = 1.0
        log.info("Blockbench web bundle installed to ${cacheDir.absolutePath}")
    }

    override fun onSuccess() {
        NotificationGroupManager.getInstance()
            .getNotificationGroup("Hyve Blockbench")
            .createNotification(
                "Blockbench Ready",
                "Blockbench web editor has been downloaded. Reopen your model file to use the visual editor.",
                NotificationType.INFORMATION,
            )
            .notify(project)
        onComplete()
    }

    override fun onThrowable(error: Throwable) {
        log.error("Blockbench download failed", error)
        NotificationGroupManager.getInstance()
            .getNotificationGroup("Hyve Blockbench")
            .createNotification(
                "Blockbench Download Failed",
                error.message ?: "Unknown error",
                NotificationType.ERROR,
            )
            .notify(project)
    }
}
