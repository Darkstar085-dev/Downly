package com.sipun.downly

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.sipun.downly.presentation.screens.DownlyApp
import com.sipun.downly.utils.DownlyContextRef

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        DownlyContextRef.context = applicationContext
        setContent { DownlyApp() }
    }
}
