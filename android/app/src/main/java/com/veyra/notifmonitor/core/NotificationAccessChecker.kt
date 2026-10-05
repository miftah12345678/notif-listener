package com.veyra.notifmonitor.core

import android.content.ComponentName
import android.content.Context
import androidx.core.app.NotificationManagerCompat
import com.veyra.notifmonitor.service.VeyraNotificationListenerService

object NotificationAccessChecker {
    fun isAccessGranted(context: Context): Boolean {
        val componentName = ComponentName(context, VeyraNotificationListenerService::class.java)
        return NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)
    }
}
