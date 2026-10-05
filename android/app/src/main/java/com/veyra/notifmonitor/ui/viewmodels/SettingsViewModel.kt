package com.veyra.notifmonitor.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.veyra.notifmonitor.data.repository.NotificationRepository
import com.veyra.notifmonitor.data.security.DeviceCredentialStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SettingsUiState(
    val isConnected: Boolean = false,
    val deviceToken: String? = null
)

class SettingsViewModel(
    private val deviceCredentialStore: DeviceCredentialStore,
    private val notificationRepository: NotificationRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState(
        isConnected = deviceCredentialStore.getToken() != null,
        deviceToken = deviceCredentialStore.getToken()
    ))
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    fun disconnectDevice() {
        // Simple disconnect logic for local UI
        deviceCredentialStore.clear()
        _uiState.value = _uiState.value.copy(isConnected = false, deviceToken = null)
    }
}

