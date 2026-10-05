package com.veyra.notifmonitor.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.veyra.notifmonitor.ui.screens.*

@Composable
fun VeyraApp() {
    val navController = rememberNavController()
    
    val items = listOf(
        Pair("home", Icons.Filled.Home),
        Pair("notifications", Icons.Filled.Notifications),
        Pair("applications", Icons.Filled.List),
        Pair("settings", Icons.Filled.Settings)
    )

    Scaffold(
        bottomBar = {
            val navBackStackEntry by navController.currentBackStackEntryAsState()
            val currentRoute = navBackStackEntry?.destination?.route
            if (currentRoute != "setup") {
                NavigationBar {
                    items.forEach { (route, icon) ->
                        NavigationBarItem(
                            icon = { Icon(icon, contentDescription = route) },
                            label = { Text(route.replaceFirstChar { it.uppercase() }) },
                            selected = currentRoute == route,
                            onClick = {
                                navController.navigate(route) {
                                    popUpTo(navController.graph.startDestinationId) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = "home",
            modifier = Modifier.padding(innerPadding)
        ) {
            composable("setup") { SetupScreen(navController) }
            composable("home") { HomeScreen(navController) }
            composable("notifications") { NotificationsScreen(navController) }
            composable("applications") { ApplicationsScreen(navController) }
            composable("settings") { SettingsScreen(navController) }
        }
    }
}
