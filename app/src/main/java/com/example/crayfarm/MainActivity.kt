package com.example.crayfarm

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.example.crayfarm.ui.theme.CrayFarmTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        CrayFarmNotificationHelper.createNotificationChannel(this)

        setContent {
            CrayFarmTheme {
                CrayFarmApp()
            }
        }
    }
}
