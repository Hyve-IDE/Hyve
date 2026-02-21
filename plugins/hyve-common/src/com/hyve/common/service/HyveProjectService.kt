// Copyright 2026 Hyve. All rights reserved.
package com.hyve.common.service

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import kotlinx.coroutines.CoroutineScope

/**
 * Base class for Hyve project-level services.
 *
 * Provides:
 * - Access to the project
 * - Coroutine scope for async operations
 * - Proper disposal lifecycle
 *
 * Usage:
 * ```kotlin
 * @Service(Service.Level.PROJECT)
 * class MyProjectService(
 *     project: Project,
 *     coroutineScope: CoroutineScope,
 * ) : HyveProjectService(project, coroutineScope) {
 *
 *     fun doSomething() {
 *         scope.launch {
 *             // Async work that will be cancelled when project closes
 *         }
 *     }
 *
 *     companion object {
 *         fun getInstance(project: Project): MyProjectService =
 *             project.getService(MyProjectService::class.java)
 *     }
 * }
 * ```
 *
 * Then register in plugin.xml:
 * ```xml
 * <projectService serviceImplementation="com.example.MyProjectService"/>
 * ```
 */
abstract class HyveProjectService(
    protected val project: Project,
    protected val scope: CoroutineScope,
) : Disposable {

    /**
     * Called when the project is being closed.
     * Override to perform cleanup.
     * The coroutine scope is automatically cancelled.
     */
    override fun dispose() {
        // Subclasses can override for additional cleanup
    }
}

/**
 * Base class for Hyve application-level services.
 *
 * Usage:
 * ```kotlin
 * @Service(Service.Level.APP)
 * class MyAppService(
 *     coroutineScope: CoroutineScope,
 * ) : HyveApplicationService(coroutineScope) {
 *
 *     companion object {
 *         fun getInstance(): MyAppService =
 *             ApplicationManager.getApplication().getService(MyAppService::class.java)
 *     }
 * }
 * ```
 *
 * Then register in plugin.xml:
 * ```xml
 * <applicationService serviceImplementation="com.example.MyAppService"/>
 * ```
 */
abstract class HyveApplicationService(
    protected val scope: CoroutineScope,
) : Disposable {

    override fun dispose() {
        // Subclasses can override for additional cleanup
    }
}
