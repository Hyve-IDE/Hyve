// Copyright 2026 Hyve. All rights reserved.
package com.hyve.ui.service

import com.hyve.common.service.HyveProjectService
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Project-level service for managing HyveUI editor state across the project.
 *
 * This service:
 * - Tracks open .ui files in the project
 * - Manages shared resources (schema registry, asset loader)
 * - Provides project-wide settings for the UI editor
 */
@Service(Service.Level.PROJECT)
class HyveUIProjectService(
    project: Project,
    scope: CoroutineScope
) : HyveProjectService(project, scope) {

    // Currently open .ui files
    private val _openUIFiles = MutableStateFlow<Set<VirtualFile>>(emptySet())
    val openUIFiles: StateFlow<Set<VirtualFile>> = _openUIFiles.asStateFlow()

    // Recent files for quick access
    private val _recentFiles = MutableStateFlow<List<VirtualFile>>(emptyList())
    val recentFiles: StateFlow<List<VirtualFile>> = _recentFiles.asStateFlow()

    // Settings
    private val _settings = MutableStateFlow(HyveUISettings())
    val settings: StateFlow<HyveUISettings> = _settings.asStateFlow()

    /**
     * Register a file as open in the editor.
     */
    fun registerOpenFile(file: VirtualFile) {
        _openUIFiles.value = _openUIFiles.value + file
        addToRecentFiles(file)
    }

    /**
     * Unregister a file when closed.
     */
    fun unregisterOpenFile(file: VirtualFile) {
        _openUIFiles.value = _openUIFiles.value - file
    }

    /**
     * Add file to recent files list.
     */
    private fun addToRecentFiles(file: VirtualFile) {
        val current = _recentFiles.value.toMutableList()
        current.remove(file) // Remove if already present
        current.add(0, file) // Add to front
        // Keep only last 10 recent files
        _recentFiles.value = current.take(MAX_RECENT_FILES)
    }

    /**
     * Update settings.
     */
    fun updateSettings(settings: HyveUISettings) {
        _settings.value = settings
    }

    companion object {
        private const val MAX_RECENT_FILES = 10

        /**
         * Get the service instance for a project.
         */
        fun getInstance(project: Project): HyveUIProjectService {
            return project.getService(HyveUIProjectService::class.java)
        }
    }
}

/**
 * Settings for the HyveUI editor.
 */
data class HyveUISettings(
    /**
     * Whether to show grid in the canvas.
     */
    val showGrid: Boolean = true,

    /**
     * Grid size in pixels.
     */
    val gridSize: Int = 10,

    /**
     * Whether to snap elements to grid.
     */
    val snapToGrid: Boolean = true,

    /**
     * Default zoom level (1.0 = 100%).
     */
    val defaultZoom: Float = 1.0f,

    /**
     * Path to Hytale Assets.zip for texture loading.
     */
    val assetsZipPath: String? = null,

    /**
     * Whether to show element bounds outlines.
     */
    val showBounds: Boolean = false,

    /**
     * Whether to auto-save on focus loss.
     */
    val autoSave: Boolean = false
)
