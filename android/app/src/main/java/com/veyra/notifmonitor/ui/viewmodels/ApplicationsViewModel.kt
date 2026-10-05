package com.veyra.notifmonitor.ui.viewmodels

import androidx.lifecycle.ViewModel
import com.veyra.notifmonitor.data.repository.DeviceConfigRepository

class ApplicationsViewModel(
    private val configRepository: DeviceConfigRepository
) : ViewModel() {
}
