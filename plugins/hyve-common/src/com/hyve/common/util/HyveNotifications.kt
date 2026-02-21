// Copyright 2026 Hyve. All rights reserved.
package com.hyve.common.util

import com.intellij.notification.Notification
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project

/**
 * Utility class for creating Hyve-branded notifications.
 *
 * All notifications use the "Hyve Notifications" group registered in plugin.xml.
 *
 * Usage:
 * ```kotlin
 * // Simple notification
 * HyveNotifications.info(project, "Operation completed")
 *
 * // With title
 * HyveNotifications.warning(project, "Caution", "This might take a while")
 *
 * // With action
 * HyveNotifications.error(project, "Build Failed", "Check the output for details") {
 *     action("Open Output") { e, notification ->
 *         // Open output window
 *         notification.expire()
 *     }
 * }
 * ```
 */
object HyveNotifications {

    private const val GROUP_ID = "Hyve Notifications"

    /**
     * Shows an information notification.
     */
    fun info(
        project: Project?,
        content: String,
        title: String = "Hyve",
        configure: NotificationBuilder.() -> Unit = {},
    ) {
        notify(project, NotificationType.INFORMATION, title, content, configure)
    }

    /**
     * Shows a warning notification.
     */
    fun warning(
        project: Project?,
        content: String,
        title: String = "Hyve",
        configure: NotificationBuilder.() -> Unit = {},
    ) {
        notify(project, NotificationType.WARNING, title, content, configure)
    }

    /**
     * Shows an error notification.
     */
    fun error(
        project: Project?,
        content: String,
        title: String = "Hyve",
        configure: NotificationBuilder.() -> Unit = {},
    ) {
        notify(project, NotificationType.ERROR, title, content, configure)
    }

    /**
     * Creates and shows a notification.
     */
    private fun notify(
        project: Project?,
        type: NotificationType,
        title: String,
        content: String,
        configure: NotificationBuilder.() -> Unit,
    ) {
        val notification = NotificationGroupManager.getInstance()
            .getNotificationGroup(GROUP_ID)
            .createNotification(title, content, type)

        val builder = NotificationBuilder(notification)
        builder.configure()

        notification.notify(project)
    }

    /**
     * Builder for configuring notifications with actions.
     */
    class NotificationBuilder(private val notification: Notification) {

        /**
         * Adds an action to the notification.
         */
        fun action(text: String, handler: (AnActionEvent, Notification) -> Unit) {
            notification.addAction(object : NotificationAction(text) {
                override fun actionPerformed(e: AnActionEvent, notification: Notification) {
                    handler(e, notification)
                }
            })
        }

        /**
         * Adds a "Don't show again" action that will disable this notification type.
         * @param project The project context, or null for application-wide suppression.
         */
        fun dontShowAgain(project: Project? = null) {
            notification.setDoNotAskFor(project)
        }

        /**
         * Sets whether the notification should be logged.
         */
        fun important() {
            notification.isImportant = true
        }

        /**
         * Adds a subtitle to the notification.
         */
        fun subtitle(text: String) {
            notification.subtitle = text
        }
    }
}
