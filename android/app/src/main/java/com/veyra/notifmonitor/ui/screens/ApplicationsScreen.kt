package com.veyra.notifmonitor.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.veyra.notifmonitor.ui.viewmodels.AppViewModelProvider
import com.veyra.notifmonitor.ui.viewmodels.ApplicationsViewModel

@Composable
fun ApplicationsScreen(
    navController: NavController,
    viewModel: ApplicationsViewModel = viewModel(factory = AppViewModelProvider.Factory)
) {
    val uiState by viewModel.uiState.collectAsState()

    Column(modifier = Modifier.padding(16.dp).fillMaxSize()) {
        Text("Applications", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(16.dp))
        
        if (uiState.loading) {
            CircularProgressIndicator()
        } else if (uiState.apps.isEmpty()) {
            Text("No applications detected yet.")
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(uiState.apps) { app ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(app.appName, style = MaterialTheme.typography.titleMedium)
                            Text(app.packageName, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}
