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
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity

private const val HYVE_THEME_ID = "Hyve Dark"
private const val PROMPTED_KEY = "hyve.theme.prompted"

/**
 * On first project open, shows a balloon asking whether the user wants to
 * switch to the Hyve Dark theme.  If they click "Yes", the theme is applied
 * immediately.  "Not now" dismisses the balloon for this session.  "Don't
 * ask again" persists the choice so the prompt never reappears.
 *
 * The prompt is skipped when:
 *  - The user already has Hyve Dark active.
 *  - The user previously dismissed with "Don't ask again".
 */
class HyveThemeRecommendationActivity : ProjectActivity {

    override suspend fun execute(project: Project) {
        val properties = PropertiesComponent.getInstance()

        if (properties.getBoolean(PROMPTED_KEY, false)) return

        val lafManager = LafManager.getInstance()
        if (lafManager.currentUIThemeLookAndFeel?.id == HYVE_THEME_ID) {
            properties.setValue(PROMPTED_KEY, true)
            return
        }

        val themeManager = UiThemeProviderListManager.getInstance()
        val hyveTheme = themeManager.findThemeById(HYVE_THEME_ID) ?: return

        val notification = NotificationGroupManager.getInstance()
            .getNotificationGroup("Hyve Notifications")
            .createNotification(
                "Hyve Theme",
                "Would you like to use Hyve's recommended dark theme?",
                NotificationType.INFORMATION,
            )

        notification.addAction(object : NotificationAction("Yes, apply it") {
            override fun actionPerformed(e: AnActionEvent, n: Notification) {
                lafManager.setCurrentLookAndFeel(hyveTheme, true)
                lafManager.updateUI()
                properties.setValue(PROMPTED_KEY, true)
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
                properties.setValue(PROMPTED_KEY, true)
                n.expire()
            }
        })

        notification.notify(project)
    }
}
