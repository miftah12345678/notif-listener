package com.veyra.notifmonitor.ui.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.veyra.notifmonitor.data.local.LocalApplication
import com.veyra.notifmonitor.data.repository.DeviceConfigRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ApplicationsUiState(
    val apps: List<LocalApplication> = emptyList(),
    val loading: Boolean = false
)

class ApplicationsViewModel(
    private val configRepository: DeviceConfigRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(ApplicationsUiState(loading = true))
    val uiState: StateFlow<ApplicationsUiState> = _uiState.asStateFlow()
    
    init {
        viewModelScope.launch {
            configRepository.getAllApps().collect { list ->
                _uiState.value = ApplicationsUiState(apps = list, loading = false)
            }
        }
    }
}
