package com.veyra.notifmonitor.ui.viewmodels

import androidx.lifecycle.ViewModel
import com.veyra.notifmonitor.data.repository.NotificationRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class NotificationsUiState(
    val events: List<String> = emptyList(),
    val loading: Boolean = false
)

class NotificationsViewModel(
    private val notificationRepository: NotificationRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(NotificationsUiState())
    val uiState: StateFlow<NotificationsUiState> = _uiState.asStateFlow()
}
