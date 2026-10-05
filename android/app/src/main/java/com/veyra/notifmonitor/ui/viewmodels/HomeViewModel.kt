package com.veyra.notifmonitor.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.veyra.notifmonitor.data.repository.NotificationRepository
import com.veyra.notifmonitor.data.security.SecureStorage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class HomeUiState(
    val listenerAccessGranted: Boolean = false,
    val pendingCount: Int = 0,
    val failedCount: Int = 0,
    val syncedCount: Int = 0,
    val lastSyncAt: Long = 0,
    val authState: String = "AUTHENTICATED"
)

class HomeViewModel(
    private val notificationRepository: NotificationRepository,
    private val secureStorage: SecureStorage
) : ViewModel() {
    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    fun updateAccessState(granted: Boolean) {
        _uiState.value = _uiState.value.copy(listenerAccessGranted = granted)
    }
    
    init {
        viewModelScope.launch {
            // Provide dummy data for now, since we need to observe Room later
            _uiState.value = _uiState.value.copy(
                pendingCount = 0,
                failedCount = 0,
                syncedCount = 0
            )
        }
    }
}
