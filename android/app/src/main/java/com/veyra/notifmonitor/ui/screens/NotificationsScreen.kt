package com.veyra.notifmonitor.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.veyra.notifmonitor.ui.viewmodels.AppViewModelProvider
import com.veyra.notifmonitor.ui.viewmodels.NotificationsViewModel

@Composable
fun NotificationsScreen(
    navController: NavController,
    viewModel: NotificationsViewModel = viewModel(factory = AppViewModelProvider.Factory)
) {
    val uiState by viewModel.uiState.collectAsState()

    Column(modifier = Modifier.padding(16.dp).fillMaxSize()) {
        Text("Notifications", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(16.dp))
        if (uiState.events.isEmpty()) {
            Text("No notifications recorded yet.")
        } else {
            // List notifications here
        }
    }
}
