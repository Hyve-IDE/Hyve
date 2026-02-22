package com.hyve.blockbench.download

import java.io.File

object BlockbenchBundle {

    private const val DOWNLOAD_URL =
        "https://github.com/Hyve-IDE/hyve-blockbench-web/releases/latest/download/blockbench-web.zip"

    fun cacheDir(): File = File(System.getProperty("user.home"), ".hyve/blockbench")

    fun isAvailable(): Boolean = File(cacheDir(), "index.html").exists()

    fun indexHtmlUrl(): String = File(cacheDir(), "index.html").toURI().toString()

    fun hytalePluginJs(): File = File(cacheDir(), "hytale_plugin.js")

    fun downloadUrl(): String = DOWNLOAD_URL
}
