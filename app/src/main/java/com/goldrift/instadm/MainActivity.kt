package com.goldrift.instadm

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.goldrift.instadm.ui.InstaDmApp
import com.goldrift.instadm.ui.theme.InstaDMTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            InstaDMTheme {
                InstaDmApp()
            }
        }
    }
}
