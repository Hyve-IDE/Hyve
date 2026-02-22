// Copyright 2026 Hyve. All rights reserved.
package com.hyve.common.startup

import com.intellij.ide.ui.LafManager
import com.intellij.ide.ui.laf.UiThemeProviderListManager
import com.intellij.ide.util.PropertiesComponent
import com.intellij.notification.Notification
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.EDT
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val HYVE_THEME_ID = "Hyve Dark"
private const val CHOSEN_KEY = "hyve.theme.chosen"
private const val DECLINED_KEY = "hyve.theme.declined"

/**
 * Counteracts IntelliJ's built-in auto-apply behavior for [themeProvider] extensions.
 *
 * When a plugin with `<themeProvider>` is installed, the platform automatically switches
 * the user's theme (via LafAndEditorColorSchemeDynamicPluginListener). There is no flag
 * to suppress this. This activity detects the auto-switch and reverts it, then shows a
 * non-intrusive balloon so the user can opt in.
 *
 * State keys (persisted in ide-general.xml, survive plugin uninstall/reinstall):
 *  - [CHOSEN_KEY]: user explicitly applied Hyve Dark via the balloon → keep it.
 *  - [DECLINED_KEY]: user dismissed the prompt → revert silently, don't prompt again.
 */
class HyveThemeRecommendationActivity : ProjectActivity {

    override suspend fun execute(project: Project) {
        val props = PropertiesComponent.getInstance()
        val laf = LafManager.getInstance()

        // User previously clicked "Yes, apply it" → respect their choice
        if (props.getBoolean(CHOSEN_KEY, false)) return

        // If Hyve Dark is active but the user never chose it, the platform auto-applied it.
        // Revert to Darcula (our parentTheme).
        if (laf.currentUIThemeLookAndFeel?.id == HYVE_THEME_ID) {
            val darcula = UiThemeProviderListManager.getInstance().findThemeById("Darcula")
            if (darcula != null) {
                withContext(Dispatchers.EDT) {
                    laf.setCurrentLookAndFeel(darcula, true)
                    laf.updateUI()
                }
            }
        }

        // User previously clicked "Don't ask again" → done
        if (props.getBoolean(DECLINED_KEY, false)) return

        // Show a one-time recommendation balloon
        val hyveTheme = UiThemeProviderListManager.getInstance().findThemeById(HYVE_THEME_ID)
            ?: return

        val notification = NotificationGroupManager.getInstance()
            .getNotificationGroup("Hyve Notifications")
            .createNotification(
                "Hyve Theme",
                "Would you like to use Hyve's recommended dark theme?",
                NotificationType.INFORMATION,
            )

        notification.addAction(object : NotificationAction("Yes, apply it") {
            override fun actionPerformed(e: AnActionEvent, n: Notification) {
                laf.setCurrentLookAndFeel(hyveTheme, true)
                laf.updateUI()
                props.setValue(CHOSEN_KEY, true)
                n.expire()
            }
        })

        notification.addAction(object : NotificationAction("Not now") {
            override fun actionPerformed(e: AnActionEvent, n: Notification) {
                n.expire()
            }
        })

        notification.addAction(object : NotificationAction("Don't ask again") {
            override fun actionPerformed(e: AnActionEvent, n: Notification) {
                props.setValue(DECLINED_KEY, true)
                n.expire()
            }
        })

        notification.notify(project)
    }
}
