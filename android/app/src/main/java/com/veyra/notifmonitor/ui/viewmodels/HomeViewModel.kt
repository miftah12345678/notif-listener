package com.veyra.notifmonitor.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.veyra.notifmonitor.data.repository.NotificationRepository
import com.veyra.notifmonitor.data.security.DeviceCredentialStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

data class HomeUiState(
    val listenerAccessGranted: Boolean = false,
    val pendingCount: Int = 0,
    val failedCount: Int = 0,
    val syncedCount: Int = 0,
    val authState: String = "AUTHENTICATED"
)

class HomeViewModel(
    private val notificationRepository: NotificationRepository,
    private val deviceCredentialStore: DeviceCredentialStore
) : ViewModel() {
    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    fun updateAccessState(granted: Boolean) {
        _uiState.value = _uiState.value.copy(listenerAccessGranted = granted)
    }
    
    init {
        viewModelScope.launch {
            combine(
                notificationRepository.getPendingCount(),
                notificationRepository.getFailedCount(),
                notificationRepository.getSyncedCount()
            ) { pending, failed, synced ->
                HomeUiState(
                    listenerAccessGranted = _uiState.value.listenerAccessGranted,
                    pendingCount = pending,
                    failedCount = failed,
                    syncedCount = synced,
                    authState = if (deviceCredentialStore.getDeviceToken() != null) "AUTHENTICATED" else "AUTH_ERROR"
                )
            }.collect { state ->
                _uiState.value = state
            }
        }
    }
}
