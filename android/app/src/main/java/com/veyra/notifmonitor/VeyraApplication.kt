package com.veyra.notifmonitor

import android.app.Application
import com.veyra.notifmonitor.data.local.VeyraDatabase
import com.veyra.notifmonitor.data.repository.NotificationRepository
import com.veyra.notifmonitor.data.repository.DeviceConfigRepository
import com.veyra.notifmonitor.data.remote.VeyraApiClient
import com.veyra.notifmonitor.data.security.SecureStorage

class VeyraApplication : Application() {

    lateinit var database: VeyraDatabase
    lateinit var notificationRepository: NotificationRepository
    lateinit var configRepository: DeviceConfigRepository
    lateinit var secureStorage: SecureStorage

    override fun onCreate() {
        super.onCreate()
        
        secureStorage = SecureStorage(this)
        database = VeyraDatabase.getDatabase(this)
        
        val apiClient = VeyraApiClient()
        notificationRepository = NotificationRepository(database.notificationDao(), apiClient, secureStorage)
        configRepository = DeviceConfigRepository(database.deviceAppConfigDao(), apiClient, secureStorage)
    }
}
