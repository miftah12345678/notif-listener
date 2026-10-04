package com.veyra.notifmonitor.service

import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context

object NotificationAccessChecker {

    /**
     * The single source of truth for checking if the NotificationListenerService has been granted access.
     * Do NOT rely on Room status for this; always ask the Android System.
     */
    fun isAccessGranted(context: Context): Boolean {
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val componentName = ComponentName(context, VeyraNotificationListenerService::class.java)
        return notificationManager.isNotificationListenerAccessGranted(componentName)
    }
}
