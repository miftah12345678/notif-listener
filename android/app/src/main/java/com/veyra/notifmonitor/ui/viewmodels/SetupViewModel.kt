package com.veyra.notifmonitor.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.veyra.notifmonitor.data.remote.ApiClient
import com.veyra.notifmonitor.data.repository.DeviceConfigRepository
import com.veyra.notifmonitor.data.security.DeviceCredentialStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class SetupState {
    object Idle : SetupState()
    object Loading : SetupState()
    object Success : SetupState()
    data class Error(val message: String) : SetupState()
}

class SetupViewModel(
    private val deviceCredentialStore: DeviceCredentialStore,
    private val apiClient: ApiClient,
    private val configRepository: DeviceConfigRepository
) : ViewModel() {

    private val _setupState = MutableStateFlow<SetupState>(SetupState.Idle)
    val setupState: StateFlow<SetupState> = _setupState.asStateFlow()

    fun provisionDevice(url: String, token: String) {
        viewModelScope.launch {
            _setupState.value = SetupState.Loading
            try {
                if (url.isBlank() || token.isBlank()) {
                    _setupState.value = SetupState.Error("URL and Token cannot be empty")
                    return@launch
                }
                val baseUrl = if (url.endsWith("/")) url else "/"
                // Just save the token and URL
                deviceCredentialStore.saveCredentials(baseUrl, token, "local-device")
                
                
                // Fetch config
                configRepository.fetchAndSaveConfig()
                _setupState.value = SetupState.Success
            } catch (e: Exception) {
                _setupState.value = SetupState.Error(e.message ?: "Failed to provision")
            }
        }
    }
}

