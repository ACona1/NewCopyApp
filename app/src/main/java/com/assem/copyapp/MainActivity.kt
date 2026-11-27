package com.assem.copyapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.core.view.WindowCompat
import com.assem.copyapp.ui.AppRoot
import com.assem.copyapp.ui.NewCopyTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }

        val container = (applicationContext as App).container
        val vm: MainViewModel by viewModels {
            MainViewModel.Factory(this, container.repository, container.sessionController)
        }
        setContent {
            NewCopyTheme {
                AppRoot(viewModel = vm, container = container)
            }
        }
    }
}
