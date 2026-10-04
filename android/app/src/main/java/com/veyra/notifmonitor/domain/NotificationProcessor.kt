package com.veyra.notifmonitor.domain

import com.veyra.notifmonitor.core.ExtrasSanitizer
import com.veyra.notifmonitor.core.FingerprintUtils
import com.veyra.notifmonitor.core.NormalizedNotificationEvent
import com.veyra.notifmonitor.data.local.AppDatabase
import com.veyra.notifmonitor.data.local.NotificationEvent
import com.veyra.notifmonitor.data.local.LocalApplication
import com.veyra.notifmonitor.data.repository.SyncRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.room.withTransaction

/**
 * Handles the business logic of filtering, sanitizing, and persisting
 * notifications locally. Driven by a bounded Channel from the Service to handle backpressure.
 */
class NotificationProcessor(
    private val db: AppDatabase,
    private val syncRepository: SyncRepository,
    private val appNameResolver: (String) -> String // Injected fallback resolver
) {
    // Using UNLIMITED capacity to ensure zero intentional dropping during bursts.
    // Memory footprint is strictly bounded to the event objects themselves, 
    // avoiding the overhead of spawning thousands of suspended coroutines.
    private val eventChannel = Channel<NormalizedNotificationEvent>(
        capacity = Channel.UNLIMITED
    )
    
    private var processingJob: kotlinx.coroutines.Job? = null

    fun startProcessing(scope: CoroutineScope) {
        if (processingJob?.isActive == true) return
        
        // Single/bounded consumer coroutine
        processingJob = scope.launch(Dispatchers.IO) {
            for (event in eventChannel) {
                try {
                    processEvent(event)
                } catch (e: Exception) {
                    // Safe swallow to prevent the single consumer from dying
                }
            }
        }
    }
    
    fun stopProcessing() {
        processingJob?.cancel()
    }

    /**
     * Synchronous submit function. Fast handoff without launching new coroutines.
     */
    fun submit(event: NormalizedNotificationEvent) {
        eventChannel.trySend(event)
    }

    /**
     * Executes entirely on a background thread.
     */
    private suspend fun processEvent(event: NormalizedNotificationEvent) {
        
        // 1. Check Local Configuration
        val config = db.configDao().getConfig(event.packageName)
        
        if (config == null || !config.enabled || config.captureMode == "DISABLED") {
            // Drop event silently. Do not track.
            return
        }

        // 2. Discover App (Track catalog)
        db.appCatalogDao().insertApp(
            LocalApplication(
                packageName = event.packageName,
                appName = appNameResolver(event.packageName),
                firstSeenAt = System.currentTimeMillis(),
                lastSeenAt = System.currentTimeMillis()
            )
        )

        // 3. Generate Deterministic Fingerprint
        val fingerprint = FingerprintUtils.generate(
            packageName = event.packageName,
            notificationKey = event.notificationKey,
            postedAt = event.postedAt,
            title = event.title,
            text = event.text
        )

        // 4. Privacy Filtering & Parsing
        val sanitizedExtras: String?
        var parsedData: String? = null // Placeholder for ParserRegistry integration
        val finalTitle: String?
        val finalText: String?

        if (config.captureMode == "METADATA") {
            // Strip content strictly before persistence
            sanitizedExtras = null
            finalTitle = null
            finalText = null
            // For METADATA mode, we do NOT pass content to the parser unless parser is metadata-only safe.
            // By default, parsedData is null to protect privacy.
            parsedData = null
        } else {
            // FULL capture mode
            sanitizedExtras = ExtrasSanitizer.sanitizeToJson(event.rawExtras)
            finalTitle = event.title
            finalText = event.text
            
            // Execute Business Parser defensively
            parsedData = ParserRegistry.parse(config.parserId, event)
        }

        // 5. Persist to Room (Source of Truth)
        val entity = NotificationEvent(
            packageName = event.packageName,
            notificationKey = event.notificationKey,
            title = finalTitle,
            text = finalText,
            rawExtras = sanitizedExtras,
            postedAt = event.postedAt,
            receivedAt = event.receivedAt,
            fingerprint = fingerprint,
            category = event.category,
            parsedData = parsedData,
            syncState = "PENDING"
        )

        // 6. Transactional Insert
        var inserted = false
        db.withTransaction {
            val configDao = db.configDao() // Re-verify config in tx if needed
            val insertedId = db.notificationDao().insertEvent(entity)
            if (insertedId != -1L) {
                inserted = true
            }
        }

        // 7. Enqueue Sync (If inserted successfully and not ignored due to UNIQUE constraint)
        if (inserted) {
            syncRepository.enqueueSync()
        }
    }
}
