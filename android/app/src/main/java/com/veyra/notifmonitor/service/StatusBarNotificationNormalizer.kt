package com.veyra.notifmonitor.service

import android.app.Notification
import android.service.notification.StatusBarNotification
import com.veyra.notifmonitor.core.NormalizedNotificationEvent
import java.util.UUID

/**
 * Extracts raw data from Android framework StatusBarNotification safely.
 */
object StatusBarNotificationNormalizer {

    fun normalize(sbn: StatusBarNotification): NormalizedNotificationEvent {
        val notification = sbn.notification
        val extras = notification?.extras

        // Prioritize official system key. If missing, fallback deterministically.
        val key = try {
            if (!sbn.key.isNullOrEmpty()) sbn.key else NotificationIdentityResolver.resolveFallback(sbn)
        } catch (e: Exception) {
            NotificationIdentityResolver.resolveFallback(sbn)
        }

        val title = extras?.getCharSequence(Notification.EXTRA_TITLE)?.toString()
        val text = extras?.getCharSequence(Notification.EXTRA_TEXT)?.toString()

        return NormalizedNotificationEvent(
            packageName = sbn.packageName ?: "unknown",
            notificationKey = key,
            title = title,
            text = text,
            rawExtras = extras,
            postedAt = sbn.postTime,
            receivedAt = System.currentTimeMillis(),
            category = notification?.category
        )
    }
}

object NotificationIdentityResolver {
    /**
     * Highly specific fallback identifier if sbn.key is completely unavailable.
     * Note: This fallback has lower reliability than the system-provided key, 
     * but guarantees different notifications won't collapse into the same ID.
     */
    fun resolveFallback(sbn: StatusBarNotification): String {
        val user = try { sbn.user?.hashCode() ?: 0 } catch(e: Exception) { 0 }
        val tag = sbn.tag ?: "null"
        return "fallback|${sbn.packageName}|${sbn.id}|$tag|$user|${sbn.postTime}"
    }
}
