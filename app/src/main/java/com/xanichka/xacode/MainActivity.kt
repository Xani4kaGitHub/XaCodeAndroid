package com.xanichka.xacode

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.xanichka.xacode.ui.XaCodeApp
import com.xanichka.xacode.ui.theme.XaCodeTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            XaCodeTheme { XaCodeApp() }
        }
    }
}

