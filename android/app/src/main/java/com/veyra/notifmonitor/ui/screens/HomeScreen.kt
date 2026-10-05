package com.veyra.notifmonitor.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.veyra.notifmonitor.ui.NotificationAccessNavigator
import com.veyra.notifmonitor.ui.viewmodels.AppViewModelProvider
import com.veyra.notifmonitor.ui.viewmodels.HomeViewModel

@Composable
fun HomeScreen(
    navController: NavController,
    viewModel: HomeViewModel = viewModel(factory = AppViewModelProvider.Factory)
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    
    // Check listener permission
    LaunchedEffect(Unit) {
        val granted = NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)
        viewModel.updateAccessState(granted)
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
