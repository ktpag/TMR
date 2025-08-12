package com.example.tmr

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import com.example.tmr.ui.AppRoot
import com.example.tmr.ui.theme.TMRTheme
import com.example.tmr.viewmodel.AppViewModel

class MainActivity : ComponentActivity() {
    private val viewModel: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            TMRTheme {
                AppRoot(viewModel)
            }
        }
    }
}




