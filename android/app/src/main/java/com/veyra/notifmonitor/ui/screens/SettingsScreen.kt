package com.veyra.notifmonitor.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.veyra.notifmonitor.ui.NotificationAccessNavigator
import com.veyra.notifmonitor.ui.viewmodels.AppViewModelProvider
import com.veyra.notifmonitor.ui.viewmodels.SettingsViewModel

@Composable
fun SettingsScreen(
    navController: NavController,
    viewModel: SettingsViewModel = viewModel(factory = AppViewModelProvider.Factory)
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var showDisconnectDialog by remember { mutableStateOf(false) }

    // Navigate to setup if disconnected
    LaunchedEffect(uiState.isConnected) {
        if (!uiState.isConnected) {
            navController.navigate("setup") {
                popUpTo(0) // Clear backstack completely
            }
        }
    }

    Column(modifier = Modifier.padding(16.dp).fillMaxSize()) {
        Text("Settings", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(16.dp))
        
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Device Identity", style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.height(8.dp))
                Text("Token Configured: $")
                Spacer(modifier = Modifier.height(16.dp))
                
                Button(
                    onClick = { showDisconnectDialog = true },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Disconnect Device")
                }
            }
        }
    }

    if (showDisconnectDialog) {
        AlertDialog(
            onDismissRequest = { showDisconnectDialog = false },
            title = { Text("Disconnect Device?") },
            text = { Text("This will clear your credentials and require you to provision the device again.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDisconnectDialog = false
                        viewModel.disconnectDevice()
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Disconnect")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDisconnectDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
