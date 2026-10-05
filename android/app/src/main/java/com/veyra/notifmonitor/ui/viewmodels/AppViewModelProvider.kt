package com.veyra.notifmonitor.ui.viewmodels

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.veyra.notifmonitor.VeyraApplication

object AppViewModelProvider {
    val Factory = viewModelFactory {
        initializer {
            val application = (this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as VeyraApplication)
            HomeViewModel(
                notificationRepository = application.notificationRepository,
                secureStorage = application.secureStorage
            )
        }
        initializer {
            val application = (this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as VeyraApplication)
            NotificationsViewModel(
                notificationRepository = application.notificationRepository
            )
        }
        initializer {
            val application = (this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as VeyraApplication)
            ApplicationsViewModel(
                configRepository = application.configRepository
            )
        }
        initializer {
            val application = (this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as VeyraApplication)
            SettingsViewModel(
                secureStorage = application.secureStorage,
                notificationRepository = application.notificationRepository
            )
        }
        initializer {
            val application = (this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as VeyraApplication)
            SetupViewModel(
                secureStorage = application.secureStorage
            )
        }
    }
}
