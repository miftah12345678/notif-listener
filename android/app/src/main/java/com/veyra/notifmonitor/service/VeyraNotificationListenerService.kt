package com.veyra.notifmonitor.service

import android.content.ComponentName
import android.content.Context
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.veyra.notifmonitor.domain.NotificationProcessor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

/**
 * Thin Android framework adapter. 
 * Passes normalized events directly to NotificationProcessor's channel.
 */
class VeyraNotificationListenerService : NotificationListenerService() {

    private lateinit var processor: NotificationProcessor
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    // For keeping track of the state independently of Room
    enum class ListenerState {
        DISCONNECTED,
        CONNECTING,
        CONNECTED
    }

    private var currentState = ListenerState.DISCONNECTED

    override fun onCreate() {
        super.onCreate()
        // Inject dependencies (omitted Dagger/Hilt setup for brevity)
        // processor = DependencyContainer.getNotificationProcessor()
        
        // Start background channel consumption
        processor.startProcessing(serviceScope)
        currentState = ListenerState.CONNECTING
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        currentState = ListenerState.CONNECTED
        Log.d("VeyraNLS", "Listener Connected")
        // Any init logic that REQUIRES connected state can go here
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        currentState = ListenerState.DISCONNECTED
        Log.d("VeyraNLS", "Listener Disconnected")
        
        // Reconnect strategy recommended by Android:
        requestRebind(ComponentName(this, VeyraNotificationListenerService::class.java))
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null) return
        
        try {
            // Normalization is fast and defensive (runs on main thread quickly)
            val event = StatusBarNotificationNormalizer.normalize(sbn)
            
            // Synchronous handoff. No coroutines are launched per event.
            // Bounded concurrency is managed internally by the single processor coroutine.
            processor.submit(event)
        } catch (e: Exception) {
            // Structured diagnostic logging: Never expose sensitive data in error logs
            Log.e("VeyraNLS", "Failed to normalize/submit notification: ${e.message}")
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        // V1 requirement: DO NOT send deletion events. Ignore.
    }

    override fun onDestroy() {
        super.onDestroy()
        processor.stopProcessing()
        serviceScope.cancel()
    }
}
