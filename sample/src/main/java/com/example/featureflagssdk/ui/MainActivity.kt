package com.example.featureflagssdk.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.lifecycle.ViewModelProvider
import com.example.featureflagssdk.client.SampleViewModel
import com.example.featureflagssdk.ui.theme.FeatureFlagsSDKTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val viewModel: SampleViewModel by viewModels()
        setContent {
            FeatureFlagsSDKTheme {
                MainScreen(viewModel)
            }
        }
    }
}
