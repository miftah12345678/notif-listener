package com.veyra.notifmonitor.data.local

import androidx.room.*

// ======================== ENTITIES ========================

@Entity(tableName = "device_profile")
data class DeviceProfile(
    @PrimaryKey val deviceId: String,
    val deviceName: String,
    val deviceModel: String,
    val androidVersion: String,
    val apiBaseUrl: String,
    val listenerStatus: String, // CONNECTED, DISCONNECTED (Last known state)
    val lastConfigSyncAt: Long
)

@Entity(tableName = "local_applications")
data class LocalApplication(
    @PrimaryKey val packageName: String,
    val appName: String,
    val firstSeenAt: Long,
    val lastSeenAt: Long
)

@Entity(tableName = "device_app_configs")
data class DeviceAppConfig(
    @PrimaryKey val packageName: String, // Primary key because config is single-device
    val enabled: Boolean,
    val captureEnabled: Boolean,
    val captureMode: String, // FULL, METADATA, DISABLED
    val parserId: String,
    val webhookEnabled: Boolean,
    val updatedAt: Long
)

@Entity(
    tableName = "notification_events",
    indices = [
        Index(value = ["fingerprint"], unique = true),
        Index(value = ["syncState"])
    ]
)
data class NotificationEvent(
    @PrimaryKey(autoGenerate = true) val localId: Long = 0,
    val packageName: String,
    val notificationKey: String,
    val title: String?,
    val text: String?,
    val rawExtras: String?, // Sanitized JSON string
    val postedAt: Long,
    val receivedAt: Long,
    val fingerprint: String, // Deterministic SHA-256
    val category: String?,
    val parsedData: String?, // JSON
    val syncState: String, // PENDING, SYNCING, SYNCED, FAILED
    val claimId: String? = null,
    val claimedAt: Long? = null,
    val syncAttempts: Int = 0,
    val lastSyncAttemptAt: Long = 0,
    val lastSyncError: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)

// ======================== DAOS ========================

@Dao
interface ConfigDao {
    @Query("SELECT * FROM device_app_configs WHERE packageName = :packageName LIMIT 1")
    suspend fun getConfig(packageName: String): DeviceAppConfig?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConfigs(configs: List<DeviceAppConfig>)
}

@Dao
interface NotificationDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertEvent(event: NotificationEvent): Long

    @Query("SELECT * FROM notification_events WHERE syncState = 'PENDING' OR syncState = 'FAILED' ORDER BY createdAt ASC LIMIT :limit")
    suspend fun getPendingEvents(limit: Int): List<NotificationEvent>

    @Query("UPDATE notification_events SET syncState = :newState WHERE localId IN (:ids)")
    suspend fun updateSyncState(ids: List<Long>, newState: String)
    
    // Atomically selects and claims a batch of pending events using an explicit claimId
    @Transaction
    suspend fun claimForSync(limit: Int, workerClaimId: String): List<NotificationEvent> {
        val eligibleIds = getEligibleIdsForClaim(limit)
        if (eligibleIds.isNotEmpty()) {
            val now = System.currentTimeMillis()
            applyClaim(eligibleIds, workerClaimId, now)
        }
        return getClaimedEvents(workerClaimId)
    }

    @Query("SELECT localId FROM notification_events WHERE syncState IN ('PENDING', 'FAILED') AND claimId IS NULL ORDER BY createdAt ASC LIMIT :limit")
    suspend fun getEligibleIdsForClaim(limit: Int): List<Long>

    @Query("UPDATE notification_events SET syncState = 'SYNCING', claimId = :workerClaimId, claimedAt = :now WHERE localId IN (:ids) AND syncState IN ('PENDING', 'FAILED') AND claimId IS NULL")
    suspend fun applyClaim(ids: List<Long>, workerClaimId: String, now: Long)

    @Query("SELECT * FROM notification_events WHERE claimId = :workerClaimId AND syncState = 'SYNCING'")
    suspend fun getClaimedEvents(workerClaimId: String): List<NotificationEvent>

    @Query("UPDATE notification_events SET syncState = :newState, claimId = null, claimedAt = null, syncAttempts = syncAttempts + 1, lastSyncAttemptAt = :now, lastSyncError = :error WHERE localId IN (:ids) AND claimId = :workerClaimId")
    suspend fun releaseClaim(ids: List<Long>, workerClaimId: String, newState: String, now: Long, error: String? = null)
    
    @Query("UPDATE notification_events SET syncState = 'PENDING', claimId = null, claimedAt = null WHERE syncState = 'SYNCING' AND claimedAt < :staleThreshold")
    suspend fun recoverStaleClaims(staleThreshold: Long): Int

    @Query("UPDATE notification_events SET syncState = 'PENDING', claimId = null, claimedAt = null WHERE localId = :id AND syncState = 'FAILED'")
    suspend fun manualRetry(id: Long)

    @Query("UPDATE notification_events SET syncState = 'PENDING', claimId = null, claimedAt = null WHERE syncState = 'FAILED'")
    suspend fun manualRetryAll()

    @Query("SELECT COUNT(*) FROM notification_events WHERE syncState = 'PENDING'")
    fun getPendingCount(): kotlinx.coroutines.flow.Flow<Int>

    @Query("SELECT COUNT(*) FROM notification_events WHERE syncState = 'SYNCING'")
    fun getSyncingCount(): kotlinx.coroutines.flow.Flow<Int>

    @Query("SELECT COUNT(*) FROM notification_events WHERE syncState = 'FAILED'")
    fun getFailedCount(): kotlinx.coroutines.flow.Flow<Int>

    @Query("SELECT COUNT(*) FROM notification_events WHERE syncState = 'SYNCED'")
    fun getSyncedCount(): kotlinx.coroutines.flow.Flow<Int>

    @Query("SELECT COUNT(*) FROM notification_events")
    fun getTotalCount(): kotlinx.coroutines.flow.Flow<Int>

    @Query("SELECT * FROM notification_events ORDER BY receivedAt DESC")
    fun getAllNotificationsDesc(): kotlinx.coroutines.flow.Flow<List<NotificationEvent>>
}

@Dao
interface AppCatalogDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertApp(app: LocalApplication)
    
    @Query("SELECT * FROM local_applications ORDER BY appName ASC")
    fun getAllApps(): kotlinx.coroutines.flow.Flow<List<LocalApplication>>
}

// ======================== DATABASE ========================

@Database(
    entities = [
        DeviceProfile::class,
        LocalApplication::class,
        DeviceAppConfig::class,
        NotificationEvent::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun configDao(): ConfigDao
    abstract fun notificationDao(): NotificationDao
    abstract fun appCatalogDao(): AppCatalogDao
}


