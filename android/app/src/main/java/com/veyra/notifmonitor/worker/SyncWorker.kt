package com.veyra.notifmonitor.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.veyra.notifmonitor.data.local.AppDatabase
import com.veyra.notifmonitor.data.local.NotificationEvent
import com.veyra.notifmonitor.data.remote.ApiClient
import com.veyra.notifmonitor.data.remote.ApiResponse
import com.veyra.notifmonitor.data.remote.BatchSyncRequest
import com.veyra.notifmonitor.data.remote.SyncEventPayload
import com.veyra.notifmonitor.data.security.DeviceCredentialStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * WorkManager worker responsible for syncing notifications to the Veyra backend.
 * Uses atomic compare-and-set claim ownership, handles 413 batch splitting, and transient retries.
 */
class SyncWorker(
    context: Context,
    params: WorkerParameters,
    private val db: AppDatabase,
    private val apiClient: ApiClient,
    private val credentialStore: DeviceCredentialStore
) : CoroutineWorker(context, params) {

    private val workerClaimId = UUID.randomUUID().toString()
    
    // Configurable thresholds
    private val staleThresholdMs = 10 * 60 * 1000L // 10 minutes
    private val initialBatchSize = 50

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val authState = credentialStore.getAuthState()
        if (authState == DeviceCredentialStore.AuthState.AUTH_ERROR || 
            authState == DeviceCredentialStore.AuthState.DEVICE_REVOKED) {
            // Do not run sync if credentials are permanently revoked or invalid
            return@withContext Result.failure()
        }

        val token = credentialStore.getToken()
        if (token.isNullOrEmpty()) {
            return@withContext Result.failure() // Not provisioned
        }

        // 1. Recover stale claims from crashed workers
        val staleCount = db.notificationDao().recoverStaleClaims(System.currentTimeMillis() - staleThresholdMs)
        if (staleCount > 0) {
            // Log.d("SyncWorker", "Recovered $staleCount stale claims to PENDING")
        }

        // 2. Claim events atomically
        val claimedEvents = db.notificationDao().claimForSync(initialBatchSize, workerClaimId)
        if (claimedEvents.isEmpty()) {
            return@withContext Result.success() // Nothing to sync
        }

        // 3. Process Batch
        return@withContext processBatch(claimedEvents, token)
    }

    private suspend fun processBatch(events: List<NotificationEvent>, token: String): Result {
        val payload = events.map { 
            SyncEventPayload(
                localId = it.localId,
                packageName = it.packageName,
                notificationKey = it.notificationKey,
                postedAt = it.postedAt,
                title = it.title,
                text = it.text,
                rawExtras = it.rawExtras,
                fingerprint = it.fingerprint
            ) 
        }

        val request = BatchSyncRequest(payload)
        
        when (val response = apiClient.postNotificationBatch(token, request)) {
            is ApiResponse.Success -> {
                val body = response.data
                
                // Server Response Validation
                if (body.accepted.isEmpty() && body.duplicates.isEmpty() && body.failed.isEmpty() && events.isNotEmpty()) {
                    // Malformed protocol. We shouldn't mark as SYNCED. Safely release as PENDING to retry later.
                    db.notificationDao().releaseClaim(
                        ids = events.map { it.localId },
                        workerClaimId = workerClaimId,
                        newState = "PENDING",
                        now = System.currentTimeMillis(),
                        error = "Malformed empty server response"
                    )
                    return Result.retry()
                }

                // Handle Partial Success
                val acceptedIds = body.accepted + body.duplicates
                if (acceptedIds.isNotEmpty()) {
                    db.notificationDao().releaseClaim(
                        ids = acceptedIds,
                        workerClaimId = workerClaimId,
                        newState = "SYNCED",
                        now = System.currentTimeMillis()
                    )
                }

                if (body.failed.isNotEmpty()) {
                    val failedIds = body.failed.map { it.clientId }
                    db.notificationDao().releaseClaim(
                        ids = failedIds,
                        workerClaimId = workerClaimId,
                        newState = "FAILED",
                        now = System.currentTimeMillis(),
                        error = "Server rejected: 400 Bad Request"
                    )
                }

                return Result.success()
            }
            
            is ApiResponse.HttpError -> {
                val now = System.currentTimeMillis()
                val eventIds = events.map { it.localId }
                
                return when (response.code) {
                    401 -> {
                        credentialStore.setAuthState(DeviceCredentialStore.AuthState.AUTH_ERROR)
                        releaseToPending(eventIds, "401 Unauthorized")
                        Result.failure()
                    }
                    403 -> {
                        credentialStore.setAuthState(DeviceCredentialStore.AuthState.DEVICE_REVOKED)
                        releaseToPending(eventIds, "403 Forbidden")
                        Result.failure()
                    }
                    413 -> {
                        // Payload Too Large -> Split Batch
                        if (events.size > 1) {
                            val mid = events.size / 2
                            val batch1 = events.subList(0, mid)
                            val batch2 = events.subList(mid, events.size)
                            
                            val r1 = processBatch(batch1, token)
                            val r2 = processBatch(batch2, token)
                            
                            // Combine results
                            if (r1 is Result.Retry || r2 is Result.Retry) Result.retry() else Result.success()
                        } else {
                            // Split down to 1 event, still 413. Permanent failure.
                            db.notificationDao().releaseClaim(eventIds, workerClaimId, "FAILED", now, "413 Payload Too Large (Permanent)")
                            Result.success()
                        }
                    }
                    400 -> {
                        // Permanent failure for the whole batch
                        db.notificationDao().releaseClaim(eventIds, workerClaimId, "FAILED", now, "400 Bad Request")
                        Result.success()
                    }
                    408, 429, in 500..599 -> {
                        // Transient Http errors -> Backoff
                        releaseToPending(eventIds, "${response.code} Transient Error")
                        Result.retry()
                    }
                    else -> {
                        releaseToPending(eventIds, "Unknown Http Error ${response.code}")
                        Result.retry()
                    }
                }
            }
            
            is ApiResponse.NetworkError -> {
                releaseToPending(events.map { it.localId }, "Network Exception: ${response.exception.message}")
                return Result.retry()
            }
            
            is ApiResponse.ProtocolError -> {
                releaseToPending(events.map { it.localId }, "Protocol Error: ${response.message}")
                return Result.retry()
            }
        }
    }

    private suspend fun releaseToPending(ids: List<Long>, reason: String) {
        db.notificationDao().releaseClaim(
            ids = ids,
            workerClaimId = workerClaimId,
            newState = "PENDING", // Back to queue
            now = System.currentTimeMillis(),
            error = reason
        )
    }
}
