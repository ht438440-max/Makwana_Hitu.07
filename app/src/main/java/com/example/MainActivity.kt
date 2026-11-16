package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.navigation.StudioNavHost
import com.example.ui.theme.MakwanaHituTheme
import com.example.ui.viewmodel.StudioViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val viewModel: StudioViewModel = viewModel()
            val prefs by viewModel.preferences.collectAsStateWithLifecycle()

            MakwanaHituTheme(darkTheme = prefs.darkThemeEnabled) {
                StudioNavHost(viewModel = viewModel)
            }
        }
    }
}
