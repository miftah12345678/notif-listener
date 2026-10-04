package com.veyra.notifmonitor.data.repository

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.veyra.notifmonitor.data.local.AppDatabase
import com.veyra.notifmonitor.worker.ConfigSyncWorker
import com.veyra.notifmonitor.worker.SyncWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Handles enqueueing WorkManager tasks safely and manual retries.
 */
class SyncRepository(
    private val context: Context,
    private val db: AppDatabase
) {
    /**
     * Enqueues the SyncWorker. Uses KEEP policy to ensure a running sync
     * is not duplicated or cancelled unnecessarily. Requires network.
     */
    fun enqueueSync() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val request = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            "notification-sync",
            ExistingWorkPolicy.KEEP,
            request
        )
    }

    /**
     * Enqueues the ConfigSyncWorker. Uses REPLACE policy to prioritize the latest config fetch.
     */
    fun enqueueConfigSync() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val request = OneTimeWorkRequestBuilder<ConfigSyncWorker>()
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            "config-sync",
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    /**
     * Manually retries a specific failed event by reverting it to PENDING and triggering sync.
     */
    suspend fun manualRetryEvent(localId: Long) = withContext(Dispatchers.IO) {
        db.notificationDao().manualRetry(localId)
        enqueueSync()
    }

    /**
     * Manually retries all failed events.
     */
    suspend fun manualRetryAllFailed() = withContext(Dispatchers.IO) {
        db.notificationDao().manualRetryAll()
        enqueueSync()
    }
}
