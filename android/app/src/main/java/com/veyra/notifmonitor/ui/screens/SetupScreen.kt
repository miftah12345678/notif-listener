package com.veyra.notifmonitor.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.veyra.notifmonitor.ui.viewmodels.AppViewModelProvider
import com.veyra.notifmonitor.ui.viewmodels.SetupState
import com.veyra.notifmonitor.ui.viewmodels.SetupViewModel
import kotlinx.coroutines.launch

@Composable
fun SetupScreen(
    navController: NavController,
    viewModel: SetupViewModel = viewModel(factory = AppViewModelProvider.Factory)
) {
    val state by viewModel.setupState.collectAsState()
    var url by remember { mutableStateOf("") }
    var token by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    
    LaunchedEffect(state) {
        if (state is SetupState.Success) {
            navController.navigate("home") {
                popUpTo("setup") { inclusive = true }
            }
        }
    }

    Column(modifier = Modifier.padding(16.dp).fillMaxSize()) {
        Text("Setup Veyra", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(16.dp))
        
        OutlinedTextField(
            value = url,
            onValueChange = { url = it },
            label = { Text("Server URL") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = token,
            onValueChange = { token = it },
            label = { Text("Device Token") },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(16.dp))
        
        if (state is SetupState.Error) {
            Text(
                text = (state as SetupState.Error).message,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }
        
        Button(
            onClick = { viewModel.provisionDevice(url, token) },
            enabled = state !is SetupState.Loading,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (state is SetupState.Loading) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.onPrimary)
            } else {
                Text("Connect Device")
            }
        }
    }
}
