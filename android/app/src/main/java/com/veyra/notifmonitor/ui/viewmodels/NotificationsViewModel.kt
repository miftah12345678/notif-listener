package com.veyra.notifmonitor.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.veyra.notifmonitor.data.local.NotificationEvent
import com.veyra.notifmonitor.data.repository.NotificationRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class NotificationsUiState(
    val events: List<NotificationEvent> = emptyList(),
    val loading: Boolean = false
)

class NotificationsViewModel(
    private val notificationRepository: NotificationRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(NotificationsUiState(loading = true))
    val uiState: StateFlow<NotificationsUiState> = _uiState.asStateFlow()
    
    init {
        viewModelScope.launch {
            notificationRepository.getAllNotificationsDesc().collect { list ->
                _uiState.value = NotificationsUiState(events = list, loading = false)
            }
        }
    }
}
