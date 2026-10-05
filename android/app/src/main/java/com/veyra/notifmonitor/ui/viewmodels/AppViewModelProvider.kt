package com.veyra.notifmonitor.ui.viewmodels

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.veyra.notifmonitor.VeyraApplication

object AppViewModelProvider {
    val Factory = viewModelFactory {
        initializer {
            val app = (this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as VeyraApplication)
            HomeViewModel(app.notificationRepository, app.deviceCredentialStore)
        }
        initializer {
            val app = (this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as VeyraApplication)
            NotificationsViewModel(app.notificationRepository)
        }
        initializer {
            val app = (this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as VeyraApplication)
            ApplicationsViewModel(app.configRepository)
        }
        initializer {
            val app = (this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as VeyraApplication)
            SettingsViewModel(app.deviceCredentialStore, app.notificationRepository)
        }
        initializer {
            val app = (this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as VeyraApplication)
            SetupViewModel(app.deviceCredentialStore, app.apiClient, app.configRepository)
        }
    }
}
