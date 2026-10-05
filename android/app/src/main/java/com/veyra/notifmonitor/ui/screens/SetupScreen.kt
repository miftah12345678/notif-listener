package com.veyra.notifmonitor.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.veyra.notifmonitor.ui.viewmodels.AppViewModelProvider
import com.veyra.notifmonitor.ui.viewmodels.SetupViewModel

@Composable
fun SetupScreen(
    navController: NavController,
    viewModel: SetupViewModel = viewModel(factory = AppViewModelProvider.Factory)
) {
    Column(modifier = Modifier.padding(16.dp).fillMaxSize()) {
        Text("Setup Veyra", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(16.dp))
        Text("Provisioning UI will appear here.")
    }
}
