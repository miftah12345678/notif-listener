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
        database = AppDatabase.getDatabase(this)
        apiClient = ApiClient(deviceCredentialStore)
        
        notificationRepository = NotificationRepository(database.notificationDao())
        configRepository = DeviceConfigRepository(database.configDao(), database.appCatalogDao(), apiClient)
        syncRepository = SyncRepository(this, database)
    }
}
