package com.veyra.notifmonitor

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.veyra.notifmonitor.ui.VeyraApp
import com.veyra.notifmonitor.ui.theme.VeyraTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val app = application as VeyraApplication
        val hasCredentials = app.secureStorage.getDeviceToken() != null
        
        setContent {
            VeyraTheme {
                VeyraApp(hasCredentials)
            }
        }
    }
}
