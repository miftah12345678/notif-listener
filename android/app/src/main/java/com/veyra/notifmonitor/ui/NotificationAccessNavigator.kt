package com.veyra.notifmonitor.ui

import android.content.Context
import android.content.Intent
import android.provider.Settings

object NotificationAccessNavigator {
    fun openNotificationAccessSettings(context: Context) {
        val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }
}
