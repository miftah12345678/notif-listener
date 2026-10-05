package com.veyra.notifmonitor.ui.viewmodels

import androidx.lifecycle.ViewModel
import com.veyra.notifmonitor.data.repository.NotificationRepository
import com.veyra.notifmonitor.data.security.SecureStorage

class SettingsViewModel(
    private val secureStorage: SecureStorage,
    private val notificationRepository: NotificationRepository
) : ViewModel() {
}
