package com.androidlearning.sliide_challenge

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.androidlearning.sliide_challenge.splash.MainViewModel
import org.koin.androidx.viewmodel.ext.android.viewModel

class MainActivity : ComponentActivity() {

    private val mainViewModel: MainViewModel by viewModel()

    override fun onCreate(savedInstanceState: Bundle?) {
        // Must run before super.onCreate so the starting theme is swapped correctly.
        installSplashScreen().setKeepOnScreenCondition {
            mainViewModel.state.value.isLoading
        }
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            App(mainViewModel = mainViewModel)
        }
    }
}
