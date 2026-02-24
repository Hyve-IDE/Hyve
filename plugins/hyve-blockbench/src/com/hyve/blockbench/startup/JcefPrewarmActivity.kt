// Copyright 2026 Hyve. All rights reserved.
package com.hyve.blockbench.startup

import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.ui.jcef.JBCefApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Pre-warms JCEF by triggering [JBCefApp.isSupported] during startup.
 *
 * The first-ever access to [JBCefApp] runs `JBCefApp$Holder.<clinit>`, which
 * performs a service lookup that IntelliJ's diagnostics flag as an error
 * ("Must not request services during class initialization"). This is harmless
 * — it always succeeds — but the logged error confuses users.
 *
 * By triggering the one-time static initialization here (on a background
 * thread, during startup), the warning lands in the startup log noise instead
 * of appearing when a user opens a Blockbench file.
 */
class JcefPrewarmActivity : ProjectActivity {

    override suspend fun execute(project: Project) {
        // Run on IO dispatcher to keep the startup coroutine responsive.
        // JBCefApp$Holder.<clinit> is a one-shot static init — once done,
        // subsequent calls to isSupported() are instant.
        withContext(Dispatchers.IO) {
            try {
                JBCefApp.isSupported()
            } catch (_: Exception) {
                // Not available — nothing to pre-warm.
            }
        }
    }
}
