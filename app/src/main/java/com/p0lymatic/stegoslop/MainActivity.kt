package com.p0lymatic.stegoslop

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.p0lymatic.stegoslop.ui.MainScreen
import com.p0lymatic.stegoslop.ui.theme.StegoSlopTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            StegoSlopTheme {
                MainScreen()
            }
        }
    }
}
