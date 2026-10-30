package com.androidlearning.sliide_challenge

import androidx.compose.runtime.Composable
import com.androidlearning.sliide_challenge.core.designsystem.theme.SliideTheme
import com.androidlearning.sliide_challenge.core.presentation.splash.MainViewModel
import com.androidlearning.sliide_challenge.navigation.NavigationRoot
import org.koin.compose.viewmodel.koinViewModel

/**
 * @param mainViewModel Android passes the Activity's instance so the system splash and the feed observe the same
 * startup state.
 */
@Composable
fun App(mainViewModel: MainViewModel = koinViewModel()) {
    SliideTheme {
        NavigationRoot(startupState = mainViewModel.startupState)
    }
}
