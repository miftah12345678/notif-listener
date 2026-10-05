package com.veyra.notifmonitor

import android.app.Application
import com.veyra.notifmonitor.data.local.AppDatabase
import com.veyra.notifmonitor.data.repository.NotificationRepository
import com.veyra.notifmonitor.data.repository.DeviceConfigRepository
import com.veyra.notifmonitor.data.repository.SyncRepository
import com.veyra.notifmonitor.data.remote.ApiClient
import com.veyra.notifmonitor.data.security.DeviceCredentialStore

class VeyraApplication : Application() {

    lateinit var database: AppDatabase
    lateinit var notificationRepository: NotificationRepository
    lateinit var configRepository: DeviceConfigRepository
    lateinit var syncRepository: SyncRepository
    lateinit var deviceCredentialStore: DeviceCredentialStore
    lateinit var apiClient: ApiClient

    override fun onCreate() {
        super.onCreate()
        
        deviceCredentialStore = DeviceCredentialStore(this)
        database = androidx.room.Room.databaseBuilder(this, AppDatabase::class.java, "veyra_database").build()
        apiClient = object : ApiClient { override suspend fun postNotificationBatch(t: String, r: com.veyra.notifmonitor.data.remote.BatchSyncRequest) = com.veyra.notifmonitor.data.remote.ApiResponse.Success(com.veyra.notifmonitor.data.remote.BatchSyncResponse(emptyList(), emptyList(), emptyList())); override suspend fun fetchDeviceConfig(t: String) = com.veyra.notifmonitor.data.remote.ApiResponse.Success("{}") }
        
        notificationRepository = NotificationRepository(database.notificationDao())
        configRepository = DeviceConfigRepository(database.configDao(), database.appCatalogDao(), apiClient)
        syncRepository = SyncRepository(this, database)
    }
}

