package com.veyra.notifmonitor.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.veyra.notifmonitor.ui.NotificationAccessNavigator
import com.veyra.notifmonitor.ui.viewmodels.AppViewModelProvider
import com.veyra.notifmonitor.ui.viewmodels.HomeViewModel
import com.veyra.notifmonitor.core.NotificationAccessChecker
// We should use NotificationAccessChecker from phase 3 here but it might not be properly imported, so using compat for now.

@Composable
fun HomeScreen(
    navController: NavController,
    viewModel: HomeViewModel = viewModel(factory = AppViewModelProvider.Factory)
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val granted = NotificationAccessChecker.isAccessGranted(context)
                viewModel.updateAccessState(granted)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    Column(modifier = Modifier.padding(16.dp).fillMaxSize()) {
        Text("Veyra Notification Monitor", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(16.dp))
        
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Status", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))
                Text("Notification Access: $")
                Text("Authentication: $")
                
                if (!uiState.listenerAccessGranted) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = { NotificationAccessNavigator.openNotificationAccessSettings(context) }) {
                        Text("Open Notification Access")
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Sync Queue", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))
                Text("Pending: $")
                Text("Failed: $")
                Text("Synced: $")
            }
        }
    }
}

