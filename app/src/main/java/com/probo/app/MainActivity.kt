package com.probo.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.probo.app.ui.ProboApp
import com.probo.app.ui.theme.ProboTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ProboTheme {
                ProboApp()
            }
        }
    }
}
